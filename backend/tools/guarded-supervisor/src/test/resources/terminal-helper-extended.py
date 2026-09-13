# Independent PTY qualification; mock terminal bytes only, no DB authentication.
import os,sys,pty,fcntl,termios,subprocess,signal,time,select,struct,importlib.util
spec=importlib.util.spec_from_file_location('probe',os.path.join(os.path.dirname(__file__),'terminal-helper-probe.py'));p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
def launch(helper):return subprocess.Popen([helper],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env={})
def arm(helper,master):
 child=launch(helper);child.stdin.write(b'\x01');child.stdin.flush();kind,snapshot=p.frame(child);assert kind==1
 child.stdin.write(b'\x02');child.stdin.flush();p.tty_until(master,b'Account: ');return child,snapshot
def wide(helper,master,slave):
 original=termios.tcgetattr(slave);chosen=list(original);chosen[6]=list(original[6]);chosen[0]|=termios.IXON|termios.IXOFF|termios.ISTRIP|termios.ICRNL|termios.INLCR|termios.IGNCR|termios.PARMRK;chosen[1]|=termios.OPOST|termios.ONLCR;chosen[3]|=termios.ECHO|termios.ISIG|termios.ICANON|termios.IEXTEN
 termios.tcsetattr(slave,termios.TCSANOW,chosen);expected=termios.tcgetattr(slave);child,snapshot=arm(helper,master)
 try:
  os.write(master,b'mock_reader\r');assert p.frame(child)==(2,bytearray(b'mock_reader'));p.tty_until(master,b'Password: ')
  secret=b'\x03\x04\x08\x11\x13\x16\x1a-\xce\xbb-\xf0\x9f\x98\x80';os.write(master,secret+b'\r');kind,value=p.frame(child);assert kind==3 and value==secret;value[:]=bytes(len(value));assert p.frame(child)==(4,bytearray());assert child.wait(timeout=3)==0
  assert termios.tcgetattr(slave)==expected;assert not child.stderr.read()
 finally:
  if child.poll() is None:child.kill();child.wait(timeout=3)
  termios.tcsetattr(slave,termios.TCSANOW,original)
 print('WIDE_TERMIOS_CONTROL_AND_UTF8_PASS',flush=True)
def deadline(helper,master,slave):
 expected=termios.tcgetattr(slave);start=time.monotonic();child,snapshot=arm(helper,master)
 try:
  os.write(master,b'mock_reader\n');assert p.frame(child)[0]==2;p.tty_until(master,b'Password: ')
  while child.poll() is None and time.monotonic()-start<124:
   os.write(master,b'x');time.sleep(.2)
  elapsed=time.monotonic()-start;assert child.poll() is not None and 119<=elapsed<=124
  assert p.frame(child)==(127,bytearray(b'M'));assert child.wait(timeout=3)==77;assert termios.tcgetattr(slave)==expected;assert not child.stderr.read()
  print('ACTUAL_120_SECOND_ABSOLUTE_TRICKLE_DEADLINE_PASS',flush=True)
 finally:
  if child.poll() is None:child.kill();child.wait(timeout=3)
  termios.tcsetattr(slave,termios.TCSANOW,expected)
def foreign(helper,master,slave):
 child=launch(helper);child.stdin.write(b'\x01');child.stdin.flush();kind,snapshot=p.frame(child);assert kind==1;child.stdin.write(b'\x04');child.stdin.flush();assert p.frame(child)[0]==4;assert child.wait(timeout=3)==0
 other_master,other_slave=pty.openpty();signal.signal(signal.SIGHUP,signal.SIG_IGN);fcntl.ioctl(slave,termios.TIOCNOTTY,0);fcntl.ioctl(other_slave,termios.TIOCSCTTY,0)
 original=termios.tcgetattr(other_slave);chosen=list(original);chosen[6]=list(original[6]);chosen[3]&=~termios.ECHO;chosen[6][termios.VERASE]=b'\x07';termios.tcsetattr(other_slave,termios.TCSANOW,chosen);expected=termios.tcgetattr(other_slave)
 child=launch(helper);child.stdin.write(b'\x05'+struct.pack('>I',len(snapshot))+snapshot);child.stdin.flush();assert p.frame(child)==(127,bytearray(b'M'));assert child.wait(timeout=3)==77;assert termios.tcgetattr(other_slave)==expected
 os.close(other_master);os.close(other_slave);print('ACTUAL_FOREIGN_CONTROLLING_TTY_SAME_PARENT_SESSION_REFUSED_PASS',flush=True)
if __name__=='__main__':
 master,slave=pty.openpty();pid=os.fork()
 if pid==0:
  try:
   os.setsid();fcntl.ioctl(slave,termios.TIOCSCTTY,0)
   if len(sys.argv)>2 and sys.argv[2]=='--deadline':deadline(sys.argv[1],master,slave)
   else:wide(sys.argv[1],master,slave);foreign(sys.argv[1],master,slave)
   os._exit(0)
  except BaseException as failure:print('EXTENDED_PTY_FAILED_'+type(failure).__name__,flush=True);os._exit(1)
 _,status=os.waitpid(pid,0);os.close(master);os.close(slave);sys.exit(os.waitstatus_to_exitcode(status))
