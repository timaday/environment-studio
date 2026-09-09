# Actual JVM/helper/PTTY interaction, using independently invented input only.
import os,sys,pty,fcntl,termios,subprocess,signal,time,select,importlib.util,glob
spec=importlib.util.spec_from_file_location('probe',os.path.join(os.path.dirname(__file__),'terminal-helper-probe.py'));p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
def one(java,classpath,helper,mode,master,slave):
 expected=termios.tcgetattr(slave);child=subprocess.Popen([java,'-XX:ErrorFile=/dev/null','-XX:-CreateCoredumpOnCrash','-XX:-HeapDumpOnOutOfMemoryError','-cp',classpath,'studio.environment.supervisor.TerminalConsolePtyProbe',helper,mode],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env={})
 try:
  seen=p.tty_until(master,b'Account: ');assert not(termios.tcgetattr(slave)[3]&termios.ECHO)
  owned=list({int(value) for path in glob.glob('/proc/'+str(child.pid)+'/task/*/children') for value in open(path).read().split()});assert len(owned)==1
  start=time.monotonic()
  if mode=='success':
   os.write(master,b'mock_reader\n');seen.extend(p.tty_until(master,b'Password: '));os.write(master,'mock-λ-😀\n'.encode());assert child.wait(timeout=4)==0
  elif mode=='shutdown':os.kill(child.pid,signal.SIGTERM);assert child.wait(timeout=12)==143
  else:os.kill(owned[0],signal.SIGKILL);assert child.wait(timeout=12)==0
  elapsed=time.monotonic()-start;output=child.stdout.read(8193);error=child.stderr.read(8193);assert len(output)<=8192 and not error
  if mode=='success':assert output==b'JAVA_TERMINAL_COMPLETE\n'
  elif mode=='kill':assert output==b'JAVA_TERMINAL_REFUSED_COMPLETE\n'
  elif mode=='stall':assert output==b'JAVA_TERMINAL_REFUSED_INCONCLUSIVE\n' and 9.8<=elapsed<=12
  if mode!='stall':assert termios.tcgetattr(slave)==expected
  else:assert not(termios.tcgetattr(slave)[3]&termios.ECHO),'stalled fallback falsely restored'
  for pid in owned:
   assert not os.path.exists('/proc/'+str(pid)),'owned helper remains'
  # The exact freshly compiled executable belongs only to this probe; include orphaned fallback children.
  for path in glob.glob('/proc/[0-9]*/exe'):
   try:target=os.readlink(path)
   except (FileNotFoundError,PermissionError,ProcessLookupError):continue
   assert target!=helper,'owned fallback remains'
  while select.select([master],[],[],0)[0]:seen.extend(os.read(master,1024))
  assert b'mock_reader' not in seen and 'mock-λ-😀'.encode() not in seen
  print('JAVA_PTY_'+mode.upper()+'_PASS',flush=True)
 finally:
  if child.poll() is None:child.kill();child.wait(timeout=3)
  termios.tcsetattr(slave,termios.TCSANOW,expected)
if __name__=='__main__':
 master,slave=pty.openpty();pid=os.fork()
 if pid==0:
  try:
   os.setsid();fcntl.ioctl(slave,termios.TIOCSCTTY,0)
   for mode in ['success','kill','shutdown','stall']:one(sys.argv[1],sys.argv[2],sys.argv[3],mode,master,slave)
   os._exit(0)
  except BaseException as failure:print('JAVA_PTY_FAILED_'+type(failure).__name__,flush=True);os._exit(1)
 _,status=os.waitpid(pid,0);os.close(master);os.close(slave);sys.exit(os.waitstatus_to_exitcode(status))
