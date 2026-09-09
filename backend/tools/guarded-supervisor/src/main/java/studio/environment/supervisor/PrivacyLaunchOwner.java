package studio.environment.supervisor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Unconnected, one-shot Java root-capture prerequisite. REGISTERED is not image,
 * privacy, descendant or runtime admission; no Process, pipe or credential escapes.
 * The installed single supervisor class loader owns this process-wide launch gate.
 * Inputs must already be fixed, admitted, credential-free installation data. This
 * class does not establish installation, self-suppression or JDK FORK qualification.
 * No existing authenticated/native-client execution path calls this class.
 */
final class PrivacyLaunchOwner {
    enum Step { OK, REFUSED }
    enum State { IN_PROGRESS, REGISTERED, REFUSED }
    enum Failure { NONE, PLATFORM, RESOURCE, PROTOCOL, DEADLINE, CANCELLED, CLEANUP }
    enum Cleanup { COMPLETE, INCONCLUSIVE }
    record Result(State state, Failure failure) { }

    /**
     * One already-open, generation-bound native launch, exclusively transferred
     * to this owner. A future trusted JNI implementation must enforce the ABI;
     * an arbitrary implementation is not admission evidence. arm/register/disarm
     * run only on the dedicated launcher. register must validate its live kernel
     * capture, not acquire a numeric PID. cancel is concurrent, nonblocking and
     * wakes native polling; close must use its original remaining budget, retain
     * armed quarantine and establish all native/process-tree cleanup. No exception
     * or failure can be converted into success. Stalling test ports only exercise
     * Java scheduling and do not qualify actual blocked ProcessBuilder.start.
     */
    interface CapturePort {
        Step arm();
        Step register(long exactReturnedPid);
        Step disarm();
        void cancel();
        Cleanup close(long remainingCleanupNanos);
    }

    private static final long MAX_NANOS=TimeUnit.SECONDS.toNanos(10);
    private static final Semaphore WINDOW=new Semaphore(1);
    private final CapturePort port;
    private final long startupDeadline;
    private final CountDownLatch ended=new CountDownLatch(1),cancelEnded=new CountDownLatch(1),closed=new CountDownLatch(1);
    private final Object stateLock=new Object();
    private volatile Result result=new Result(State.IN_PROGRESS,Failure.NONE);
    private volatile Process process;
    private volatile boolean cancelled,closeRequested,uncertain;
    private volatile long cleanupDeadline;
    private volatile Cleanup cleanup=Cleanup.INCONCLUSIVE;
    private boolean cancelStarted,cleanupStarted;

    PrivacyLaunchOwner(List<String> command, Map<String,String> environment, Path directory,
                       CapturePort port, long startupDeadline) {
        this.port=Objects.requireNonNull(port,"capture port");
        this.startupDeadline=startupDeadline;
        List<String> copiedCommand;
        Map<String,String> copiedEnvironment;
        Path copiedDirectory;
        try {
            copiedCommand=List.copyOf(command);copiedEnvironment=Map.copyOf(environment);
            copiedDirectory=Objects.requireNonNull(directory);
            if(copiedCommand.isEmpty()||!copiedDirectory.isAbsolute())throw new IllegalArgumentException();
        } catch(Throwable failure) {refuse(Failure.PROTOCOL);ended.countDown();return;}
        long left=startupDeadline-System.nanoTime();
        if(left<=0||left>MAX_NANOS){refuse(Failure.DEADLINE);ended.countDown();return;}
        // Do not queue unbounded platform launchers, or wait inside an armed window.
        if(!WINDOW.tryAcquire()){refuse(Failure.RESOURCE);ended.countDown();return;}
        try {
            Thread.ofPlatform().name("privacy-launch").daemon(true)
                    .start(()->launch(copiedCommand,copiedEnvironment,copiedDirectory));
        } catch(Throwable failure) {WINDOW.release();refuse(Failure.RESOURCE);ended.countDown();}
    }

    private void launch(List<String> command,Map<String,String> environment,Path directory) {
        boolean armEntered=false,disarmed=true,registered=false;
        try {
            if(!live())return;
            var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
            builder.environment().clear();builder.environment().putAll(environment);
            // No user callback or unrelated launch is allowed between arm and start.
            armEntered=true;disarmed=false;
            if(port.arm()!=Step.OK){refuse(Failure.PROTOCOL);return;}
            if(!live())return;
            process=builder.start(); // Sole creation site; no supplied Process or PID seam.
            if(!live())return;
            long exactPid=process.pid();
            if(exactPid<=0||!process.isAlive()){refuse(Failure.PROTOCOL);return;}
            if(port.register(exactPid)!=Step.OK){refuse(Failure.PROTOCOL);return;}
            registered=live();
        } catch(Throwable failure) {refuse(Failure.RESOURCE);}
        finally {
            if(armEntered)try {disarmed=port.disarm()==Step.OK;}catch(Throwable failure){disarmed=false;}
            if(!disarmed){uncertain=true;refuse(Failure.CLEANUP);}
            // A failed/unreturned disarm retains the process-wide quarantine.
            if(disarmed)WINDOW.release();
            synchronized(stateLock){
                if(registered&&disarmed&&live()&&result.state()==State.IN_PROGRESS)
                    result=new Result(State.REGISTERED,Failure.NONE);
            }
            ended.countDown();
            // If start returned after the bounded closer left, retain uncertainty
            // and make a best-effort termination of this exact late Process.
            if(closeRequested&&closed.getCount()==0)terminateExact();
        }
    }

    private boolean live(){
        if(cancelled||closeRequested){refuse(Failure.CANCELLED);return false;}
        if(startupDeadline-System.nanoTime()<=0){refuse(Failure.DEADLINE);return false;}
        return true;
    }
    private void refuse(Failure failure){
        synchronized(stateLock){if(result.state()!=State.REFUSED)result=new Result(State.REFUSED,failure);}
    }
    Result await(){
        if(!waitFor(ended,startupDeadline)){
            refuse(Thread.currentThread().isInterrupted()?Failure.CANCELLED:Failure.DEADLINE);cancel();
        }
        return result;
    }
    void cancel(){
        synchronized(stateLock){
            cancelled=true;
            if(result.state()!=State.REFUSED)result=new Result(State.REFUSED,Failure.CANCELLED);
            if(cancelStarted)return;
            cancelStarted=true;
            try {Thread.ofPlatform().name("privacy-cancel").daemon(true).start(()->{
                try {port.cancel();}catch(Throwable failure){uncertain=true;}
                finally {cancelEnded.countDown();}
            });}catch(Throwable failure){uncertain=true;cancelEnded.countDown();}
        }
    }

    Cleanup close(long originalCleanupDeadline){
        synchronized(stateLock){
            if(closed.getCount()==0)return cleanup;
            long now=System.nanoTime(),remaining=originalCleanupDeadline-now;
            boolean valid=remaining>0&&remaining<=MAX_NANOS;
            long proposed=valid?originalCleanupDeadline:now;
            if(!valid)uncertain=true;
            if(!cleanupStarted){
                cleanupStarted=true;closeRequested=true;cleanupDeadline=proposed;
                cancel();
                try {Thread.ofPlatform().name("privacy-close").daemon(true).start(this::cleanup);}
                catch(Throwable failure){uncertain=true;closed.countDown();}
            } else if(proposed-now<cleanupDeadline-now)cleanupDeadline=proposed;
        }
        if(!waitCleanup(closed))uncertain=true;
        return closed.getCount()==0?cleanup:Cleanup.INCONCLUSIVE;
    }
    private void cleanup(){
        try {
            // Native cancellation may own a captured child before start returns.
            if(!waitCleanup(cancelEnded))uncertain=true;
            if(!waitCleanup(ended))uncertain=true;
            terminateExact();
            Process exact=process;
            if(exact!=null)try {
                while(exact.isAlive()){
                    long left=cleanupDeadline-System.nanoTime();
                    if(left<=0){uncertain=true;break;}
                    exact.waitFor(Math.min(left,TimeUnit.MILLISECONDS.toNanos(1)),TimeUnit.NANOSECONDS);
                }
            }catch(Throwable failure){uncertain=true;}
            // Even a Java cleanup failure must not skip the native close attempt.
            try {if(port.close(Math.max(0,cleanupDeadline-System.nanoTime()))!=Cleanup.COMPLETE)uncertain=true;}
            catch(Throwable failure){uncertain=true;}
            if(exact!=null){
                try {exact.getOutputStream().close();}catch(Throwable failure){uncertain=true;}
                try {exact.getInputStream().close();}catch(Throwable failure){uncertain=true;}
                try {exact.getErrorStream().close();}catch(Throwable failure){uncertain=true;}
            }
        } catch(Throwable failure){uncertain=true;}
        finally {
            synchronized(stateLock){
                if(ended.getCount()!=0||cancelEnded.getCount()!=0||cleanupDeadline-System.nanoTime()<=0)uncertain=true;
                cleanup=uncertain?Cleanup.INCONCLUSIVE:Cleanup.COMPLETE;
                closed.countDown();
            }
        }
    }
    private boolean waitCleanup(CountDownLatch latch){
        while(latch.getCount()!=0){
            long left=cleanupDeadline-System.nanoTime();
            if(left<=0)return false;
            try {if(latch.await(Math.min(left,TimeUnit.MILLISECONDS.toNanos(1)),TimeUnit.NANOSECONDS))return true;}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}
        }
        return true;
    }
    private void terminateExact(){
        Process exact=process;
        if(exact!=null)try {if(exact.isAlive())exact.destroyForcibly();}catch(Throwable failure){uncertain=true;}
    }
    private static boolean waitFor(CountDownLatch latch,long deadline){
        long left=deadline-System.nanoTime();
        if(latch.getCount()==0)return true;
        if(left<=0)return false;
        try{return latch.await(left,TimeUnit.NANOSECONDS);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}
    }
    @Override public String toString(){return "PrivacyLaunchOwner[redacted]";}
}
