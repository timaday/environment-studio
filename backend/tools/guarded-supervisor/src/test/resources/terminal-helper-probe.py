# Independently invented pseudo-terminal inputs. No database or actual credentials.
import os, sys, pty, fcntl, termios, subprocess, select, struct, signal, time
MAGIC=b'ESTTY001'
def exact(stream,n):
    data=bytearray()
    end=time.monotonic()+3
    while len(data)<n:
        assert select.select([stream],[],[],max(0,end-time.monotonic()))[0], 'bounded helper response missing'
        part=os.read(stream.fileno(),n-len(data))
        assert part, 'helper response truncated'
        data.extend(part)
    return data

def frame(proc):
    header=exact(proc.stdout,13)
    assert header[:8]==MAGIC, 'unexpected helper magic'
    n=struct.unpack('>I',header[9:13])[0]
    assert n<=4608, 'unbounded helper response'
    return header[8],exact(proc.stdout,n)

def tty_until(master,marker):
    data=bytearray();end=time.monotonic()+3
    while marker not in data:
        assert select.select([master],[],[],max(0,end-time.monotonic()))[0], 'bounded prompt missing'
        data.extend(os.read(master,1024));assert len(data)<4096,'prompt overflow'
    return data

def checks(helper,master,slave):
    original=termios.tcgetattr(slave)
    chosen=list(original);chosen[6]=list(original[6]);chosen[3]|=termios.ECHO;chosen[6][termios.VERASE]=b'\x08';termios.tcsetattr(slave,termios.TCSANOW,chosen)
    captured=termios.tcgetattr(slave)
    proc=subprocess.Popen([helper],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env={})
    try:
        proc.stdin.write(b'\x01');proc.stdin.flush();kind,snapshot=frame(proc);assert kind==1 and 0<len(snapshot)<=512,'capture refused'
        assert termios.tcgetattr(slave)==captured,'terminal changed before ARM'
        proc.stdin.write(b'\x02');proc.stdin.flush();seen=tty_until(master,b'Account: ')
        assert not(termios.tcgetattr(slave)[3]&termios.ECHO),'echo remained enabled'
        os.write(master,b'mock_operator\r\nDISCARDED_SUFFIX')
        kind,account=frame(proc);assert kind==2 and account==b'mock_operator','account framing mismatch'
        seen.extend(tty_until(master,b'Password: '));secret=b'invented-\xce\xbb-\xf0\x9f\x98\x80'
        os.write(master,secret+b'\nDISCARDED_TAIL')
        kind,password=frame(proc);assert kind==3 and password==secret,'password framing mismatch'
        kind,payload=frame(proc);assert kind==4 and not payload,'restoration not acknowledged'
        assert proc.wait(timeout=3)==0,'helper nonzero success exit'
        assert termios.tcgetattr(slave)==captured,'nondefault terminal state not exactly restored'
        assert not proc.stderr.read(),'helper stderr was not empty'
        while select.select([master],[],[],0)[0]:seen.extend(os.read(master,1024))
        assert secret not in seen and account not in seen,'credential echoed'
        assert b'DISCARDED' not in seen,'pending suffix echoed'
        assert not select.select([slave],[],[],0)[0],'pending input escaped restoration'
        account[:]=b'\0'*len(account);password[:]=b'\0'*len(password)
    finally:
        if proc.poll() is None:proc.kill();proc.wait(timeout=3)
        termios.tcsetattr(slave,termios.TCSANOW,original)

def adverse(helper,master,slave):
    original=termios.tcgetattr(slave)
    def launch():return subprocess.Popen([helper],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env={})
    def armed():
        p=launch();p.stdin.write(b'\x01');p.stdin.flush();kind,snapshot=frame(p);assert kind==1
        p.stdin.write(b'\x02');p.stdin.flush();tty_until(master,b'Account: ');return p,snapshot
    def finished(p):
        try:assert p.wait(timeout=3) in (0,77,78),'unexpected helper status'
        finally:
            if p.poll() is None:p.kill();p.wait(timeout=3)
        assert termios.tcgetattr(slave)==original,'adverse path did not restore exactly'
        assert not p.stderr.read(),'adverse helper emitted stderr'
    for payload in [b'bad-name\n',b'a'*64+b'\n',b'\x00\n']:
        p,snapshot=armed();os.write(master,payload);kind,code=frame(p);assert kind==127 and code==b'M';finished(p)
    for payload in [b'\xc0\x80\n',b'x'*4097+b'\n',b'\xf0\x9f\x98\x80'*1025+b'\n',b'abc\x00suffix\n']:
        p,snapshot=armed();os.write(master,b'mock_user\n');assert frame(p)[0]==2;tty_until(master,b'Password: ');os.write(master,payload);kind,code=frame(p);assert kind==127 and code==b'M';finished(p)
    for sig in [signal.SIGINT,signal.SIGTERM,signal.SIGHUP,signal.SIGTSTP]:
        p,snapshot=armed();os.kill(p.pid,sig);finished(p)
    p,snapshot=armed();p.stdin.close();finished(p)
    p=launch();p.stdin.write(b'\x01');p.stdin.flush();snapshot=frame(p)[1];p.stdin.write(b'\x7f');p.stdin.flush();assert frame(p)[0]==127;finished(p)
    p,snapshot=armed();p.stdin.write(b'\x04');p.stdin.flush();assert frame(p)[0]==4;finished(p)
    p,snapshot=armed();p.kill();p.wait(timeout=3);assert not(termios.tcgetattr(slave)[3]&termios.ECHO),'kill accidentally restored state'
    fallback=launch();fallback.stdin.write(b'\x05'+struct.pack('>I',len(snapshot))+snapshot);fallback.stdin.flush();assert frame(fallback)[0]==4;finished(fallback)
    # A corrupted identity must not change this terminal, even with a correctly sized snapshot.
    bad=bytearray(snapshot);bad[12]^=1;p=launch();p.stdin.write(b'\x05'+struct.pack('>I',len(bad))+bad);p.stdin.flush();assert frame(p)[0]==127;finished(p)
    # Parent loss is distinct from helper loss: keep the session leader alive.
    import ctypes
    assert ctypes.CDLL(None).prctl(36,1,0,0,0)==0,'test subreaper setup failed'
    r,w=os.pipe();parent=os.fork()
    if parent==0:
        os.close(r);p,snapshot=armed();os.write(w,str(p.pid).encode()+b'\n');os.close(w);signal.pause();os._exit(9)
    os.close(w);child=int(os.read(r,32));os.close(r);os.kill(parent,signal.SIGKILL);os.waitpid(parent,0)
    until=time.monotonic()+3
    while time.monotonic()<until:
        waited,status=os.waitpid(child,os.WNOHANG)
        if waited:break
        time.sleep(.01)
    else:os.kill(child,signal.SIGKILL);os.waitpid(child,0);raise AssertionError('parent-death child cleanup timeout')
    assert termios.tcgetattr(slave)==original,'parent-death did not restore exactly'
    print('PTY adverse bounds/UTF8/signals/parent EOF+death/ARM/helper-kill fallback/identity PASS',flush=True)

if __name__=='__main__':
    master,slave=pty.openpty();pid=os.fork()
    if pid==0:
        try:
            os.setsid();fcntl.ioctl(slave,termios.TIOCSCTTY,0);checks(sys.argv[1],master,slave);adverse(sys.argv[1],master,slave);os._exit(0)
        except BaseException as failure:
            print('PTY_ASSERTION_FAILED:'+type(failure).__name__,file=sys.stderr,flush=True);os._exit(1)
    _,status=os.waitpid(pid,0);os.close(master);os.close(slave)
    assert os.waitstatus_to_exitcode(status)==0,'independent PTY qualification failed'
    print('PTY bounded capture/ARM/noecho/CRLF/Unicode/flush/exact restore PASS')
