package studio.environment.supervisor;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** Explicit mock PTY process, never a public credential-input mode. */
public final class TerminalConsolePtyProbe {
 public static void main(String[] args)throws Exception {
  Path helper=Path.of(args[0]);String mode=args[1];String digest=AdmittedFiles.sha256(Files.readAllBytes(helper));
  try{AdmittedFiles.read(helper,16_777_216);}catch(Refusal refused){System.out.println("MOCK_INSTALLATION_"+refused.code);return;}
  TerminalConsole console;
  if(mode.equals("stall")){
   var starts=new java.util.concurrent.atomic.AtomicInteger();
   console=new TerminalConsole(()->{
    var builder=new ProcessBuilder(helper.toString());builder.environment().clear();var process=builder.start();
    if(starts.incrementAndGet()==2){var stop=new ProcessBuilder("/usr/bin/kill","-STOP",Long.toString(process.pid())).start();if(!stop.waitFor(2,TimeUnit.SECONDS)||stop.exitValue()!=0)throw new IllegalStateException("MOCK_STOP_FAILED");}
    return process;
   },TimeUnit.SECONDS.toNanos(120),TimeUnit.SECONDS.toNanos(10));
  }else console=TerminalConsole.verified(helper,digest);
  try(var credentials=console.read(true)){
   if(!mode.equals("success")||!credentials.username().equals("mock_reader"))throw new IllegalStateException("MOCK_ACCOUNT_MISMATCH");
   byte[] bytes=credentials.password().line();try{if(!Arrays.equals(bytes,"mock-λ-😀\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new IllegalStateException("MOCK_PASSWORD_MISMATCH");}finally{Arrays.fill(bytes,(byte)0);}
   System.out.println("JAVA_TERMINAL_COMPLETE");
  }catch(Refusal refused){System.out.println("JAVA_TERMINAL_REFUSED_"+console.restore());}
 }
}
