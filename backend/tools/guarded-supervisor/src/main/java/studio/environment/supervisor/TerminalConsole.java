package studio.environment.supervisor;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Candidate private terminal adapter. Ordinary Main still has no qualified console. */
final class TerminalConsole implements Supervisor.ConsolePort {
    @FunctionalInterface interface Launcher { Process start() throws Exception; }
    private final Launcher launcher;
    private final long entryNanos,restoreNanos;
    private final AtomicBoolean used=new AtomicBoolean();
    private final AtomicBoolean forcedStop=new AtomicBoolean();
    private volatile boolean stopping,restored,armed,cleanupUncertain,delivering;
    private volatile Process process,fallback;
    private volatile Thread entryThread;
    private volatile byte[] snapshot;
    private volatile Credentials pending;
    private FutureTask<SessionEngine.Cleanup> cleanupTask;
    private long cleanupDeadline;
    private Thread hook;
    TerminalConsole(Launcher launcher,long entryNanos,long restoreNanos) {
        this.launcher=Objects.requireNonNull(launcher);
        if(entryNanos<=0||entryNanos>TimeUnit.SECONDS.toNanos(120)||restoreNanos<=0||restoreNanos>TimeUnit.SECONDS.toNanos(10))throw new IllegalArgumentException("TERMINAL_DEADLINE_INVALID");
        this.entryNanos=entryNanos;this.restoreNanos=restoreNanos;
    }
    static TerminalConsole verified(Path helper,String digest) {
        return new TerminalConsole(()->{
            Refusal.require("Linux".equals(System.getProperty("os.name"))&&Set.of("amd64","x86_64").contains(System.getProperty("os.arch")),"TERMINAL_PLATFORM_UNQUALIFIED");
            Refusal.require(helper.isAbsolute()&&helper.normalize().equals(helper)&&digest!=null&&digest.matches("[0-9a-f]{64}"),"TERMINAL_INSTALLATION_UNQUALIFIED");
            byte[] bytes=AdmittedFiles.read(helper,16_777_216);
            try {Refusal.require(AdmittedFiles.sha256(bytes).equals(digest)&&Files.isExecutable(helper),"TERMINAL_INSTALLATION_UNQUALIFIED");}finally{Arrays.fill(bytes,(byte)0);}
            var builder=new ProcessBuilder(helper.toString()).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();builder.environment().putAll(Map.of("LANG","C.UTF-8","LC_ALL","C.UTF-8"));return builder.start();
        },TimeUnit.SECONDS.toNanos(120),TimeUnit.SECONDS.toNanos(10));
    }
    @Override public Credentials read(boolean postgres) {
        Refusal.require(used.compareAndSet(false,true)&&!stopping,"TERMINAL_ENTRY_ALREADY_USED");
        long deadline=System.nanoTime()+entryNanos;
        hook=new Thread(this::restore,"studio-terminal-restore");Runtime.getRuntime().addShutdownHook(hook);
        var task=new FutureTask<Credentials>(()->entry(postgres,deadline));
        entryThread=Thread.ofVirtual().unstarted(task);entryThread.start();
        Credentials result=null;
        try {
            result=task.get(remaining(deadline),TimeUnit.NANOSECONDS);delivering=true;
            Refusal.require(restore()==SessionEngine.Cleanup.COMPLETE,"TERMINAL_RESTORATION_INCONCLUSIVE");
            return result;
        }catch(Throwable failure){
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            if(result!=null)result.close();delivering=false;restore();throw new Refusal("TERMINAL_ENTRY_REFUSED");
        }
    }
    private Credentials entry(boolean postgres,long deadline)throws Exception {
        byte[] account=null,password=null,line=null;char[] username=null;BoundedSecret bounded=null;
        try {
            process=launcher.start();Refusal.require(!stopping,"TERMINAL_STOPPED");
            write(process,new byte[]{1});var captured=readFrame(process,1,112);validateSnapshot(captured);snapshot=captured;
            Refusal.require(!stopping,"TERMINAL_STOPPED");armed=true;write(process,new byte[]{(byte)(postgres?2:3)});
            account=readFrame(process,2,postgres?63:128);Refusal.require(account.length>0,"TERMINAL_ACCOUNT_INVALID");
            username=new char[account.length];for(int i=0;i<account.length;i++){Refusal.require((account[i]&0x80)==0,"TERMINAL_ACCOUNT_INVALID");username[i]=(char)account[i];}
            password=readFrame(process,3,4096);for(byte value:password)Refusal.require(value!=0&&value!='\r'&&value!='\n',"TERMINAL_PASSWORD_INVALID");
            line=Arrays.copyOf(password,password.length+1);line[line.length-1]='\n';bounded=BoundedSecret.read(new ByteArrayInputStream(line));
            byte[] ack=readFrame(process,4,0);Refusal.require(ack.length==0,"TERMINAL_RESTORE_INVALID");restored=true;
            Refusal.require(process.getInputStream().read()==-1,"TERMINAL_TRAILING_FRAME");
            Refusal.require(process.waitFor(remaining(deadline),TimeUnit.NANOSECONDS)&&process.exitValue()==0,"TERMINAL_EXIT_UNCONFIRMED");
            Refusal.require(!stopping,"TERMINAL_STOPPED");pending=new Credentials(username,bounded,postgres);bounded=null;return pending;
        } finally {
            wipe(account);wipe(password);wipe(line);if(username!=null)Arrays.fill(username,'\0');if(bounded!=null)bounded.close();
            if(stopping&&!delivering&&pending!=null)pending.close();
            if(stopping&&process!=null&&process.isAlive())process.destroyForcibly();
        }
    }
    private static long remaining(long deadline){long value=deadline-System.nanoTime();Refusal.require(value>0,"TERMINAL_DEADLINE");return value;}
    private static void write(Process child,byte[] bytes)throws IOException {child.getOutputStream().write(bytes);child.getOutputStream().flush();}
    private static byte[] readFrame(Process child,int expected,int maximum)throws IOException {
        byte[] header=new byte[13],payload=null;
        try {
            exact(child.getInputStream(),header);
            for(int i=0;i<8;i++)Refusal.require(header[i]=="ESTTY001".charAt(i),"TERMINAL_FRAME_INVALID");
            long length=Integer.toUnsignedLong(ByteBuffer.wrap(header,9,4).getInt());
            Refusal.require((header[8]&255)==expected&&length<=maximum,"TERMINAL_FRAME_INVALID");
            payload=new byte[(int)length];exact(child.getInputStream(),payload);return payload;
        }catch(Throwable failure){wipe(payload);throw failure;}finally{wipe(header);}
    }
    private static void exact(InputStream input,byte[] target)throws IOException {
        int used=0;while(used<target.length){int n=input.read(target,used,target.length-used);Refusal.require(n>0,"TERMINAL_FRAME_TRUNCATED");used+=n;}
    }
    private static void validateSnapshot(byte[] bytes) {
        try {
            Refusal.require(bytes.length==112,"TERMINAL_SNAPSHOT_INVALID");
            var input=ByteBuffer.wrap(bytes);byte[] magic=new byte[8];input.get(magic);
            Refusal.require(Arrays.equals(magic,"LNXAMD64".getBytes(StandardCharsets.US_ASCII))&&input.getInt()==1,"TERMINAL_SNAPSHOT_INVALID");
            input.position(36);Refusal.require(input.getInt()>0&&input.getInt()>0&&input.getInt()>1&&input.getInt()==60,"TERMINAL_SNAPSHOT_INVALID");
        }catch(Throwable failure){wipe(bytes);throw failure;}
    }
    @Override public SessionEngine.Cleanup restore() {
        FutureTask<SessionEngine.Cleanup> task;long deadline;
        synchronized(this) {
            if(cleanupTask==null){stopping=true;cleanupDeadline=System.nanoTime()+restoreNanos;cleanupTask=new FutureTask<>(()->cleanup(cleanupDeadline));Thread.ofVirtual().start(cleanupTask);}
            task=cleanupTask;deadline=cleanupDeadline;
        }
        try {
            SessionEngine.Cleanup result=task.isDone()?task.get():task.get(remaining(deadline),TimeUnit.NANOSECONDS);
            return cleanupUncertain?SessionEngine.Cleanup.INCONCLUSIVE:result;
        }catch(Throwable failure){
            cleanupUncertain=true;
            if(forcedStop.compareAndSet(false,true))Thread.ofVirtual().start(()->{
                for(Process child:new Process[]{process,fallback})if(child!=null&&child.isAlive())try{child.destroyForcibly();}catch(Throwable ignored){/* Remains inconclusive. */}
            });
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();return SessionEngine.Cleanup.INCONCLUSIVE;
        }
    }
    private SessionEngine.Cleanup cleanup(long deadline) {
        boolean clean=true;
        try {
            Thread worker=entryThread;if(worker!=null&&worker.isAlive())worker.interrupt();
            Process child=process;
            if(child!=null)clean=stop(child,deadline)&&clean;
            if(worker!=null){TimeUnit.NANOSECONDS.timedJoin(worker,remaining(deadline));clean=!worker.isAlive()&&clean;}
            // A launch that has not returned a handle cannot be declared cleaned up.
            if(used.get()&&process==null)clean=false;
            if(armed&&!restored) {
                byte[] original=snapshot;Refusal.require(original!=null&&child!=null&&!child.isAlive(),"TERMINAL_FALLBACK_UNSAFE");
                remaining(deadline);fallback=launcher.start();remaining(deadline);byte[] command=ByteBuffer.allocate(5+original.length).put((byte)5).putInt(original.length).put(original).array();
                try{write(fallback,command);readFrame(fallback,4,0);Refusal.require(fallback.getInputStream().read()==-1,"TERMINAL_TRAILING_FRAME");Refusal.require(fallback.waitFor(remaining(deadline),TimeUnit.NANOSECONDS)&&fallback.exitValue()==0,"TERMINAL_FALLBACK_EXIT");restored=true;}
                finally{wipe(command);}
                clean=stop(fallback,deadline)&&clean;
            }
            clean=(!armed||restored)&&clean;
        }catch(Throwable failure){clean=false;}
        finally {
            if(!clean){cleanupUncertain=true;Process child=fallback;if(child!=null&&child.isAlive())child.destroyForcibly();}
            if(!delivering&&pending!=null)pending.close();wipe(snapshot);
            try{if(hook!=null&&Thread.currentThread()!=hook)Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shutdown){/* This same bounded restore remains owned by the shutdown hook. */}
        }
        return clean&&!cleanupUncertain?SessionEngine.Cleanup.COMPLETE:SessionEngine.Cleanup.INCONCLUSIVE;
    }
    private static boolean stop(Process child,long deadline)throws Exception {
        if(child.isAlive())child.destroyForcibly();boolean dead=child.waitFor(remaining(deadline),TimeUnit.NANOSECONDS);
        boolean output=close(child.getOutputStream(),deadline),input=close(child.getInputStream(),deadline),error=close(child.getErrorStream(),deadline);
        return dead&&output&&input&&error;
    }
    private static boolean close(AutoCloseable stream,long deadline)throws Exception {
        var task=new FutureTask<Void>(()->{stream.close();return null;});Thread.ofVirtual().start(task);task.get(remaining(deadline),TimeUnit.NANOSECONDS);return true;
    }
    private static void wipe(byte[] bytes){if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    @Override public String toString(){return "TerminalConsole[redacted]";}
}
