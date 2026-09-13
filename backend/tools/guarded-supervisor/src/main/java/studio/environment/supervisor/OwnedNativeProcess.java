package studio.environment.supervisor;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Process creation belongs only to this separately installed tool. */
final class OwnedNativeProcess implements NativeProcess {
    private final Process process;
    private final java.time.Instant wrapperStart;
    private final BlockingQueue<Integer> output=new ArrayBlockingQueue<>(1_048_577);
    private final Thread reader;
    private Thread writer;
    private final Thread[] streamClosers=new Thread[2];
    private volatile boolean overflow,outputEnded,outputFailed,inputFailed,cleanupUncertain;
    private long total,lastTrack;
    private final Map<ProcessHandle,java.time.Instant> descendants=new HashMap<>();
    OwnedNativeProcess(Path sessionLauncher,List<String> fixedClient,Map<String,String> fixedEnvironment,Path ownedDirectory){
        this(sessionLauncher,fixedClient,fixedEnvironment,ownedDirectory,java.util.function.UnaryOperator.identity());
    }
    OwnedNativeProcess(Path sessionLauncher,List<String> fixedClient,Map<String,String> fixedEnvironment,Path ownedDirectory,java.util.function.UnaryOperator<java.io.InputStream> outputStream){
        var fixedCommand=new ArrayList<String>();fixedCommand.add(sessionLauncher.toString());fixedCommand.addAll(List.of("--fork","--wait","--"));fixedCommand.addAll(fixedClient);
        try{var builder=new ProcessBuilder(List.copyOf(fixedCommand)).directory(ownedDirectory.toFile()).redirectErrorStream(true);builder.environment().clear();builder.environment().putAll(fixedEnvironment);process=builder.start();}
        catch(Throwable e){throw new Refusal("CLIENT_LAUNCH_FAILED");}
        wrapperStart=process.info().startInstant().orElse(null);if(wrapperStart==null){process.destroyForcibly();throw new Refusal("PROCESS_OWNERSHIP_UNAVAILABLE");}
        reader=Thread.ofVirtual().start(()->{try(var stream=outputStream.apply(process.getInputStream())){int b;while((b=stream.read())!=-1){if(++total>1_048_576||!output.offer(b)){overflow=true;break;}}}catch(Throwable ignored){outputFailed=true;cleanupUncertain=true;}finally{outputEnded=true;if(!output.offer(-1))overflow=true;}});
    }
    static long remaining(long deadline){long remaining=deadline-System.nanoTime();Refusal.require(remaining>0,"DEADLINE_EXCEEDED");return remaining;}
    @Override public void write(byte[] bytes,long deadline){
        FutureTask<Void> task;Thread owned;
        synchronized(this){
            Refusal.require(writer==null||!writer.isAlive(),"CONCURRENT_CLIENT_INPUT");
            Refusal.require(!inputFailed,"CLIENT_INPUT_FAILED");Refusal.require(!outputFailed,"CLIENT_OUTPUT_FAILED");track();Refusal.require(!overflow,"TRANSCRIPT_LIMIT");remaining(deadline);
            // Construct/capture the supplied bytes only after exclusive admission. No executor or input queue.
            task=new FutureTask<>(()->{process.getOutputStream().write(bytes);process.getOutputStream().flush();return null;});
            owned=Thread.ofVirtual().unstarted(task);writer=owned;
            try{owned.start();}catch(Throwable failure){inputFailed=true;throw new Refusal("CLIENT_INPUT_FAILED");}
        }
        try{task.get(remaining(deadline),TimeUnit.NANOSECONDS);TimeUnit.NANOSECONDS.timedJoin(owned,remaining(deadline));Refusal.require(!owned.isAlive(),"CLIENT_INPUT_FAILED");}
        catch(Throwable failure){inputFailed=true;task.cancel(true);try{terminate();}catch(Throwable ignored){cleanupUncertain=true;}throw new Refusal("CLIENT_INPUT_FAILED");}
    }
    @Override public int read(long deadline){Refusal.require(!outputFailed,"CLIENT_OUTPUT_FAILED");track();Refusal.require(!overflow,"TRANSCRIPT_LIMIT");try{Integer b=output.poll(remaining(deadline),TimeUnit.NANOSECONDS);Refusal.require(b!=null,"DEADLINE_EXCEEDED");Refusal.require(!outputFailed,"CLIENT_OUTPUT_FAILED");Refusal.require(!overflow,"TRANSCRIPT_LIMIT");return b;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new Refusal("INTERRUPTED");}}
    @Override public boolean alive(){Refusal.require(!outputFailed,"CLIENT_OUTPUT_FAILED");track();return process.isAlive()&&process.info().startInstant().filter(wrapperStart::equals).isPresent()&&!outputEnded&&(descendants.isEmpty()||descendants.keySet().stream().anyMatch(ProcessHandle::isAlive));}
    @Override public int exit(long deadline){try{Refusal.require(!outputFailed,"CLIENT_OUTPUT_FAILED");Refusal.require(process.waitFor(remaining(deadline),TimeUnit.NANOSECONDS),"DEADLINE_EXCEEDED");TimeUnit.NANOSECONDS.timedJoin(reader,remaining(deadline));Refusal.require(!reader.isAlive(),"DEADLINE_EXCEEDED");Refusal.require(!outputFailed,"CLIENT_OUTPUT_FAILED");track();Refusal.require(!cleanupUncertain&&descendants.keySet().stream().noneMatch(ProcessHandle::isAlive),"CLIENT_DESCENDANTS_REMAIN");return process.exitValue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new Refusal("INTERRUPTED");}}
    private synchronized void track(){
        long now=System.nanoTime();if(now-lastTrack<10_000_000)return;lastTrack=now;
        try{process.descendants().forEach(handle->{Refusal.require(descendants.size()<16||descendants.containsKey(handle),"PROCESS_LIMIT");var start=handle.info().startInstant();if(start.isPresent())descendants.putIfAbsent(handle,start.get());else Refusal.require(!handle.isAlive(),"PROCESS_OWNERSHIP_UNAVAILABLE");});}
        catch(Throwable failure){cleanupUncertain=true;throw failure;}
    }
    @Override public synchronized void terminate(){
        try{lastTrack=0;track();}catch(Throwable ignored){cleanupUncertain=true;}
        for(var entry:descendants.entrySet())try{var handle=entry.getKey();if(handle.isAlive()){Refusal.require(handle.info().startInstant().filter(entry.getValue()::equals).isPresent(),"PROCESS_OWNERSHIP_UNAVAILABLE");handle.destroyForcibly();}}catch(Throwable ignored){cleanupUncertain=true;}
        try{process.waitFor(200,TimeUnit.MILLISECONDS);}catch(Throwable ignored){cleanupUncertain=true;}
        try{if(process.isAlive())process.destroyForcibly();}catch(Throwable ignored){cleanupUncertain=true;}
        if(cleanupUncertain)throw new Refusal("PROCESS_CLEANUP_INCONCLUSIVE");
    }
    private void closeStream(int index,AutoCloseable stream){
        try{if(streamClosers[index]==null)streamClosers[index]=Thread.ofVirtual().start(()->{try{stream.close();}catch(Throwable ignored){cleanupUncertain=true;}});streamClosers[index].join(1000);if(streamClosers[index].isAlive())cleanupUncertain=true;}
        catch(Throwable ignored){cleanupUncertain=true;}
    }
    @Override public void close(){
        inputFailed=true;try{terminate();}catch(Throwable ignored){cleanupUncertain=true;}
        try{if(!process.waitFor(2,TimeUnit.SECONDS))cleanupUncertain=true;}catch(Throwable ignored){cleanupUncertain=true;}
        try{closeStream(0,process.getOutputStream());}catch(Throwable ignored){cleanupUncertain=true;}
        try{closeStream(1,process.getInputStream());}catch(Throwable ignored){cleanupUncertain=true;}
        try{reader.join(1000);if(reader.isAlive())cleanupUncertain=true;}catch(Throwable ignored){cleanupUncertain=true;}
        Thread owned; synchronized(this){owned=writer;}
        try{if(owned!=null){owned.interrupt();owned.join(1000);if(owned.isAlive())cleanupUncertain=true;}}catch(Throwable ignored){cleanupUncertain=true;}
        try{if(descendants.keySet().stream().anyMatch(ProcessHandle::isAlive))cleanupUncertain=true;}catch(Throwable ignored){cleanupUncertain=true;}
        if(cleanupUncertain)throw new Refusal("PROCESS_CLEANUP_INCONCLUSIVE");
    }
    @Override public String toString(){return "OwnedNativeProcess[redacted]";}
}
