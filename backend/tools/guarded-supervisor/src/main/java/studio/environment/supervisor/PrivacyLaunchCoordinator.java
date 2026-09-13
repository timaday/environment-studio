package studio.environment.supervisor;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One native token/coordinator; identity refusal is never root admission. */
final class PrivacyLaunchCoordinator implements PrivacyLaunchOwner.CapturePort {
    private static final PrivacyBridge.Event RESOURCE = new PrivacyBridge.EventFailed(PrivacyBridge.Failure.RESOURCE);
    private static final PrivacyBridge.Event DEADLINE = new PrivacyBridge.EventFailed(PrivacyBridge.Failure.DEADLINE);
    private static final PrivacyBridge.Event CANCELLED = new PrivacyBridge.EventFailed(PrivacyBridge.Failure.CANCELLED);
    private final long token;
    private final CountDownLatch ended=new CountDownLatch(1);
    private volatile PrivacyBridge.Event event = RESOURCE;
    private volatile boolean uncertain;
    PrivacyLaunchCoordinator(PrivacyBridge.Opened opened) {
        token=Objects.requireNonNull(opened).launch();
        try {Thread.ofPlatform().name("privacy-coordinator").daemon(true).start(()->{
            try {var result=PrivacyBridge.nextEvent(token);if(result==null)throw new IllegalStateException();event=result;}
            catch(Throwable failure){uncertain=true;event=RESOURCE;try{PrivacyBridge.cancel(token);}catch(Throwable ignored){uncertain=true;}}
            finally {ended.countDown();}
        });}catch(Throwable failure){uncertain=true;event=RESOURCE;ended.countDown();try{PrivacyBridge.cancel(token);}catch(Throwable ignored){uncertain=true;}}
    }
    public PrivacyBridge.ForkResult arm(){return PrivacyBridge.armFork(token);}
    public PrivacyLaunchOwner.Step register(long pid){return PrivacyBridge.registerRoot(token,pid)==PrivacyBridge.RootRegistered.REGISTERED?PrivacyLaunchOwner.Step.OK:PrivacyLaunchOwner.Step.REFUSED;}
    public PrivacyBridge.DisarmResult disarm(){return PrivacyBridge.disarmFork(token);}
    public void cancel(){PrivacyBridge.cancel(token);}
    PrivacyBridge.Event await(long deadline){
        while(ended.getCount()!=0){long left=deadline-System.nanoTime();if(left<=0){uncertain=true;cancel();return DEADLINE;}
            try{ended.await(Math.min(left,TimeUnit.MILLISECONDS.toNanos(1)),TimeUnit.NANOSECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();uncertain=true;cancel();return CANCELLED;}}
        return event;
    }
    public PrivacyLaunchOwner.Cleanup close(long remaining){
        long now=System.nanoTime(),deadline=now+Math.max(0,Math.min(remaining,TimeUnit.SECONDS.toNanos(10)));
        if(remaining<=0||remaining>TimeUnit.SECONDS.toNanos(10))uncertain=true;
        try{if(PrivacyBridge.closeLaunch(token,remaining)!=PrivacyBridge.CloseResult.COMPLETE)uncertain=true;}
        catch(Throwable failure){uncertain=true;}
        while(ended.getCount()!=0){long left=deadline-System.nanoTime();if(left<=0){uncertain=true;break;}
            try{ended.await(Math.min(left,TimeUnit.MILLISECONDS.toNanos(1)),TimeUnit.NANOSECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();uncertain=true;break;}}
        return uncertain?PrivacyLaunchOwner.Cleanup.INCONCLUSIVE:PrivacyLaunchOwner.Cleanup.COMPLETE;
    }
}
