package studio.environment.supervisor;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Isolated, credential-free process. Library pathname is a test harness input only. */
public final class PrivacyBridgeProbe {
    static native int hold(boolean start);
    static native void disarmFault();
    static native int metrics();
    static native int awaitCoordinator(long token);
    static native int reuseDescriptor(long token,int stage);
    static native void closeFault();
    static native void holdReference(long token);
    static native void referenceControl(boolean release);
    static native int controls();
    static native void delayRecord();
    static native int clocks(long token);
    static native void allocationFault(int mode);
    static native void refusalAllocationFault(Throwable failure);
    static native int eventAllocationFault();
    static native int eventAllocationObserved(long token);
    static native long heldToken();
    static native void releaseAllocation();
    static native int unpublishedOutcome(long token);
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    static long deadline(){return System.nanoTime()+TimeUnit.SECONDS.toNanos(3);}
    static PrivacyBridge.Opened opened(){var result=PrivacyBridge.openLaunch(1,3_000_000_000L);check(result instanceof PrivacyBridge.Opened,"OPEN_LIFECYCLE");return (PrivacyBridge.Opened)result;}
    static void complete(PrivacyBridge.Opened opened)throws Exception{
        check(PrivacyBridge.closeLaunch(opened.launch(),1_000_000_000L)==PrivacyBridge.CloseResult.COMPLETE,"CLOSE");
        check(!Files.exists(Path.of(opened.socketPath())),"ENDPOINT_GONE");
        check(PrivacyBridge.status(opened.launch())==PrivacyBridge.Status.CLOSED,"TOMBSTONE_STATUS");
        check(PrivacyBridge.closeLaunch(opened.launch(),1_000_000_000L)==PrivacyBridge.CloseResult.COMPLETE,"REPEAT_CLOSE");
        PrivacyBridge.cancel(opened.launch());
        check(PrivacyBridge.nextEvent(opened.launch())==PrivacyBridge.EventClosed.CLOSED,"TOMBSTONE_EVENT");
        check(PrivacyBridge.armFork(opened.launch()) instanceof PrivacyBridge.ForkFailed,"NO_REARM");
    }
    static void lifecycle(String child,boolean expectedRegistration)throws Exception{
        var open=opened();var coordinator=new PrivacyLaunchCoordinator(open);
        var owner=new PrivacyLaunchOwner(List.of(child,"owned-child"),Map.of("ES_LAUNCH_TEST_ENDPOINT",open.socketPath()),Path.of("/tmp"),coordinator,deadline());
        try{
            var result=owner.await();
            check(result.state()==(expectedRegistration?PrivacyLaunchOwner.State.REGISTERED:PrivacyLaunchOwner.State.REFUSED),"ROOT_REGISTRATION");
            var event=coordinator.await(deadline());check(event instanceof PrivacyBridge.EventFailed,"IDENTITY_GATE");
            if(expectedRegistration){check(((PrivacyBridge.EventFailed)event).failure()==PrivacyBridge.Failure.INSTALLATION,"MISSING_IDENTITY");
                var field=PrivacyLaunchOwner.class.getDeclaredField("process");field.setAccessible(true);Process exact=(Process)field.get(owner);
                String output=new String(exact.getInputStream().readNBytes(26),StandardCharsets.US_ASCII);
                check(output.equals("INVENTED-OUT\nINVENTED-ERR\n"),"OUTPUT");}
        }finally{check(owner.close(deadline())==PrivacyLaunchOwner.Cleanup.COMPLETE,"JAVA_NATIVE_CLEANUP");}
        complete(open);
    }
    static void delayedOpen(long allowance,long holdMillis,boolean expired)throws Exception{
        delayedOpen(allowance,holdMillis,expired,false);
    }
    static void coordinatorAllocation(String child)throws Exception{
        var open=opened();check(eventAllocationFault()==0,"EVENT_FAULT_SETUP");
        var coordinator=new PrivacyLaunchCoordinator(open);
        var owner=new PrivacyLaunchOwner(List.of(child,"owned-child"),Map.of("ES_LAUNCH_TEST_ENDPOINT",open.socketPath()),Path.of("/tmp"),coordinator,deadline());
        Process exact=null;
        try {
            check(owner.await().state()==PrivacyLaunchOwner.State.REGISTERED,"EVENT_ROOT_REGISTRATION");
            check(coordinator.await(deadline()).equals(new PrivacyBridge.EventFailed(PrivacyBridge.Failure.RESOURCE)),"EVENT_EXCEPTION_REFUSAL");
            check(eventAllocationObserved(open.launch())==0,"CORRELATED_EXCEPTION_RELEASED");
            var field=PrivacyLaunchOwner.class.getDeclaredField("process");field.setAccessible(true);exact=(Process)field.get(owner);
            check(exact!=null&&exact.isAlive(),"EXACT_CHILD_STILL_BLOCKED");
            check(new String(exact.getInputStream().readNBytes(26),StandardCharsets.US_ASCII).equals("INVENTED-OUT\nINVENTED-ERR\n"),"EVENT_OUTPUT_PRESERVED");
        } finally {check(owner.close(deadline())==PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,"JAVA_EVENT_UNCERTAINTY");}
        check(exact!=null&&!exact.isAlive()&&exact.waitFor(1,TimeUnit.SECONDS),"EXACT_CHILD_REAPED");
        complete(open); // Native COMPLETE is separate from the Java exception.
        check(coordinator.close(1_000_000_000L)==PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,"COORDINATOR_UNCERTAINTY_STICKY");
        check(owner.close(deadline())==PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,"OWNER_UNCERTAINTY_STICKY");
        lifecycle(child,true); // Original finally-disarm released the Java window.
    }
    static void delayedOpen(long allowance,long holdMillis,boolean expired,boolean failRefusal)throws Exception{
        var result=new java.util.concurrent.atomic.AtomicReference<PrivacyBridge.OpenResult>();
        var thrown=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var injected=new OutOfMemoryError("invented refusal allocation");
        Thread caller=Thread.ofPlatform().start(()->{
            try {
                if(failRefusal)refusalAllocationFault(injected);else allocationFault(3);
                result.set(PrivacyBridge.openLaunch(1,allowance));
            } catch(Throwable failure){thrown.set(failure);}
        });
        long token=heldToken();
        try {
            check(PrivacyBridge.status(token)==PrivacyBridge.Status.FAILED,"UNPUBLISHED_STATUS");
            check(PrivacyBridge.closeLaunch(token,1_000_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"UNPUBLISHED_CLOSE");
            Thread.sleep(holdMillis);
        } finally {releaseAllocation();caller.join(3000);}
        check(!caller.isAlive(),"PUBLICATION_JOIN");
        if(failRefusal)check(thrown.get()==injected&&result.get()==null,"EXACT_REFUSAL_EXCEPTION");
        else check(thrown.get()==null,"NO_UNEXPECTED_EXCEPTION");
        if(expired){
            if(!failRefusal)check(new PrivacyBridge.OpenFailed(PrivacyBridge.Failure.DEADLINE).equals(result.get()),"EXPIRED_OPEN_REFUSED");
            check(unpublishedOutcome(token)==0,"EXPIRED_OWNER_SETTLED_INCONCLUSIVE");
            for(int i=0;i<2;i++){
                check(PrivacyBridge.status(token)==PrivacyBridge.Status.FAILED,"NEVER_PUBLISHED");
                check(PrivacyBridge.closeLaunch(token,1_000_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"EXPIRED_CLOSE_STICKY");
                PrivacyBridge.cancel(token);
            }
            check(unpublishedOutcome(token)==0,"LATE_CALLS_PRESERVE_UNPUBLISHED_OWNER");
        }else{
            check(result.get() instanceof PrivacyBridge.Opened,"UNEXPIRED_OPEN_PUBLISHED");
            complete((PrivacyBridge.Opened)result.get());
        }
        // All four slots must recover, without resetting consumed issuance.
        var recovered=new ArrayList<PrivacyBridge.Opened>();
        for(int i=0;i<4;i++){var open=opened();check(open.launch()!=token,"ISSUANCE_NOT_REUSED");recovered.add(open);}
        check(PrivacyBridge.openLaunch(1,3_000_000_000L).equals(new PrivacyBridge.OpenFailed(PrivacyBridge.Failure.RESOURCE)),"RECOVERED_LIMIT");
        for(var open:recovered)complete(open);
    }
    public static void main(String[] args) {
        try{run(args);}catch(Throwable failure){System.err.println("PROBE_ASSERT_LINE_"+Math.max(0,failure.getStackTrace()[failure instanceof AssertionError&&failure.getStackTrace().length>1?1:0].getLineNumber()));System.exit(40);}
    }
    static void run(String[] args) throws Exception {
        System.load(args[0]);String mode=args[1];
        if(mode.equals("parent-refused")){check(PrivacyBridge.establishSelf(1).equals(new PrivacyBridge.SelfFailed(PrivacyBridge.Failure.INSTALLATION)),"PARENT_TRUST");return;}
        if(mode.equals("parent-close-fault")){closeFault();check(PrivacyBridge.establishSelf(1).equals(new PrivacyBridge.SelfFailed(PrivacyBridge.Failure.CLEANUP)),"PARENT_CLOSE_UNCERTAINTY");return;}
        if(mode.equals("empty")){check(PrivacyBridge.establishSelf(1) instanceof PrivacyBridge.SelfFailed,"EMPTY_SELF");check(PrivacyBridge.openLaunch(1,1_000_000L) instanceof PrivacyBridge.OpenFailed,"EMPTY_OPEN");return;}
        var priorReady=new CountDownLatch(1);var priorRelease=new CountDownLatch(1);var controlResult=new java.util.concurrent.atomic.AtomicInteger(-1);
        Thread prior=null;if(mode.equals("threads")){prior=Thread.ofPlatform().start(()->{priorReady.countDown();try{priorRelease.await();controlResult.set(controls());}catch(InterruptedException failure){Thread.currentThread().interrupt();}});priorReady.await();}
        check(PrivacyBridge.establishSelf(1)==PrivacyBridge.SelfEstablished.ESTABLISHED,"SELF");
        if(prior!=null){priorRelease.countDown();prior.join(3000);check(!prior.isAlive()&&controlResult.get()==0,"EXISTING_THREAD");}
        check(PrivacyBridge.establishSelf(1)==PrivacyBridge.SelfEstablished.ESTABLISHED,"SELF_REPEAT");
        switch(mode){
            case "lifecycle" -> lifecycle(args[2],true);
            case "failed-exec" -> lifecycle(args[2]+".missing",false);
            case "open-allocation" -> {allocationFault(1);boolean failed=false;try{PrivacyBridge.openLaunch(1,3_000_000_000L);}catch(OutOfMemoryError expected){failed=true;}check(failed,"OPEN_ALLOCATION");complete(opened());}
            case "late-publication" -> {var result=new java.util.concurrent.atomic.AtomicReference<PrivacyBridge.OpenResult>();Thread caller=Thread.ofPlatform().start(()->{allocationFault(3);result.set(PrivacyBridge.openLaunch(1,3_000_000_000L));});long token=heldToken();check(PrivacyBridge.closeLaunch(token,1_000_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"UNPUBLISHED_REFUSAL");releaseAllocation();caller.join(3000);check(!caller.isAlive()&&result.get() instanceof PrivacyBridge.Opened,"PUBLISHED_AFTER_RESULT");complete((PrivacyBridge.Opened)result.get());complete(opened());}
            case "expired-publication" -> delayedOpen(100_000_000L,250,true);
            case "unexpired-publication" -> delayedOpen(3_000_000_000L,250,false);
            case "startup-expired-publication" -> delayedOpen(12_000_000_000L,10_250,true);
            case "expired-refusal-allocation" -> delayedOpen(100_000_000L,250,true,true);
            case "event-allocation" -> coordinatorAllocation(args[2]);
            case "arm-allocation" -> {var open=opened();var delegate=new PrivacyLaunchCoordinator(open);var port=new PrivacyLaunchOwner.CapturePort(){
                public PrivacyBridge.ForkResult arm(){allocationFault(2);return delegate.arm();}
                public PrivacyLaunchOwner.Step register(long pid){return delegate.register(pid);}
                public PrivacyBridge.DisarmResult disarm(){return delegate.disarm();}
                public void cancel(){delegate.cancel();}
                public PrivacyLaunchOwner.Cleanup close(long left){return delegate.close(left);}
            };var owner=new PrivacyLaunchOwner(List.of(args[2]),Map.of(),Path.of("/tmp"),port,deadline());check(owner.await().state()==PrivacyLaunchOwner.State.REFUSED,"ARM_ALLOCATION");check(owner.close(deadline())==PrivacyLaunchOwner.Cleanup.COMPLETE,"ARM_ALLOCATION_CLEANUP");complete(open);lifecycle(args[2],true);}
            case "threads" -> {check(controls()==0,"SELF_CONTROLS");Thread future=Thread.ofPlatform().start(()->controlResult.set(controls()));future.join(3000);check(!future.isAlive()&&controlResult.get()==0,"FUTURE_THREAD");}
            case "clocks" -> {delayRecord();var result=PrivacyBridge.openLaunch(1,12_000_000_000L);check(result instanceof PrivacyBridge.Opened,"CLOCK_OPEN");var open=(PrivacyBridge.Opened)result;check(clocks(open.launch())==0,"ORIGINAL_BOTH_CLOCKS");complete(open);}
            case "held-reference" -> {var open=opened();Thread ref=Thread.ofPlatform().start(()->holdReference(open.launch()));referenceControl(false);check(PrivacyBridge.closeLaunch(open.launch(),1_000_000_000L)==PrivacyBridge.CloseResult.COMPLETE,"HELD_REF_CLOSE");var others=new ArrayList<PrivacyBridge.Opened>();for(int i=0;i<3;i++)others.add(opened());check(PrivacyBridge.openLaunch(1,3_000_000_000L) instanceof PrivacyBridge.OpenFailed,"REF_DRAIN_CAPACITY");referenceControl(true);ref.join(3000);check(!ref.isAlive(),"REF_JOIN");complete(opened());for(var other:others)complete(other);complete(open);}
            case "shortened-close" -> {var open=opened();check(PrivacyBridge.armFork(open.launch())==PrivacyBridge.ForkArmed.ARMED,"ACQUIRED_ARM");var first=new java.util.concurrent.atomic.AtomicReference<PrivacyBridge.CloseResult>();Thread closer=Thread.ofPlatform().start(()->first.set(PrivacyBridge.closeLaunch(open.launch(),3_000_000_000L)));Thread.sleep(20);check(PrivacyBridge.closeLaunch(open.launch(),20_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"SHORT_CLOSE");closer.join(500);check(!closer.isAlive()&&first.get()==PrivacyBridge.CloseResult.INCONCLUSIVE,"SHARED_CLOCK");check(PrivacyBridge.disarmFork(open.launch())==PrivacyBridge.ForkDisarmed.DISARMED,"LATE_DISARM");check(PrivacyBridge.closeLaunch(open.launch(),1_000_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"STICKY_CLOSE");Files.deleteIfExists(Path.of(open.socketPath()));Files.deleteIfExists(Path.of(open.socketPath()).getParent());complete(opened());}
            case "close-error" -> {var open=opened();closeFault();check(PrivacyBridge.closeLaunch(open.launch(),1_000_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"CLOSE_UNCERTAINTY");check(PrivacyBridge.closeLaunch(open.launch(),1_000_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"CLOSE_ERROR_STICKY");Files.deleteIfExists(Path.of(open.socketPath()));Files.deleteIfExists(Path.of(open.socketPath()).getParent());complete(opened());}
            case "duplicate-library" -> {var first=opened();boolean refused=false;try{System.load(args[3]);}catch(LinkageError expected){refused=true;}check(refused,"DUPLICATE_LIBRARY");var second=opened();check(second.launch()!=first.launch(),"NO_INVOCATION_RESET");complete(first);complete(second);}
            case "wrong-loader" -> {var paths=System.getProperty("java.class.path").split(java.io.File.pathSeparator);var urls=new java.net.URL[paths.length];for(int i=0;i<paths.length;i++)urls[i]=Path.of(paths[i]).toUri().toURL();boolean refused=false;
                try(var loader=new java.net.URLClassLoader(urls,ClassLoader.getPlatformClassLoader())){var helper=loader.loadClass(PrivacyBridgeLinkageProbe.class.getName());try{helper.getMethod("load",String.class).invoke(null,args[3]);}catch(java.lang.reflect.InvocationTargetException expected){refused=expected.getCause() instanceof LinkageError;}}
                check(refused,"WRONG_CLASSLOADER");complete(opened());}
            case "descriptor-reuse" -> {var open=opened();check(reuseDescriptor(open.launch(),0)==0,"REMEMBER_FD");complete(open);check(reuseDescriptor(open.launch(),1)==0,"EXACT_REUSED_FD");complete(open);check(PrivacyBridge.disarmFork(open.launch()) instanceof PrivacyBridge.DisarmFailed,"LATE_DISARM");check(PrivacyBridge.registerRoot(open.launch(),ProcessHandle.current().pid()) instanceof PrivacyBridge.RootFailed,"LATE_REGISTER");check(reuseDescriptor(open.launch(),2)==0,"UNTOUCHED_REUSED_FD");complete(opened());}
            case "wrong-thread" -> {var open=opened();check(PrivacyBridge.armFork(open.launch())==PrivacyBridge.ForkArmed.ARMED,"ARM");var wrong=new java.util.concurrent.atomic.AtomicReference<PrivacyBridge.DisarmResult>();Thread other=Thread.ofPlatform().start(()->wrong.set(PrivacyBridge.disarmFork(open.launch())));other.join(3000);check(!other.isAlive()&&wrong.get().equals(new PrivacyBridge.DisarmFailed(PrivacyBridge.Failure.PROTOCOL,PrivacyBridge.ArmOwnership.DISARM_REQUIRED_OR_UNKNOWN)),"WRONG_LAUNCHER");check(PrivacyBridge.disarmFork(open.launch())==PrivacyBridge.ForkDisarmed.DISARMED,"ORIGINAL_FINALLY");complete(open);}
            case "coordinator-cancel" -> {var open=opened();var result=new java.util.concurrent.atomic.AtomicReference<PrivacyBridge.Event>();Thread first=Thread.ofPlatform().start(()->result.set(PrivacyBridge.nextEvent(open.launch())));check(awaitCoordinator(open.launch())==0,"CAPTURE_ACTIVE");check(PrivacyBridge.nextEvent(open.launch()).equals(new PrivacyBridge.EventFailed(PrivacyBridge.Failure.PROTOCOL)),"SECOND_COORDINATOR");PrivacyBridge.cancel(open.launch());first.join(1000);check(!first.isAlive()&&result.get().equals(new PrivacyBridge.EventFailed(PrivacyBridge.Failure.PROTOCOL)),"CANCEL_WAKE_ORIGINAL_FAILURE");complete(open);lifecycle(args[2],true);}
            case "capacity" -> {var opens=new ArrayList<PrivacyBridge.Opened>();for(int i=0;i<4;i++)opens.add(opened());check(PrivacyBridge.openLaunch(1,3_000_000_000L).equals(new PrivacyBridge.OpenFailed(PrivacyBridge.Failure.RESOURCE)),"FIFTH");for(var open:opens)complete(open);complete(opened());}
            case "tokens" -> {long first=0;for(int i=0;i<256;i++){var open=opened();if(i==0)first=open.launch();complete(open);}check(PrivacyBridge.openLaunch(1,3_000_000_000L).equals(new PrivacyBridge.OpenFailed(PrivacyBridge.Failure.RESOURCE)),"TOKEN_EXHAUSTION");check(PrivacyBridge.closeLaunch(first,1_000_000_000L)==PrivacyBridge.CloseResult.COMPLETE,"OLDEST_TOMBSTONE");}
            case "invalid" -> {var open=opened();for(long token:new long[]{0,-1,Long.MAX_VALUE,open.launch()^512}){check(PrivacyBridge.closeLaunch(token,1_000_000L)==PrivacyBridge.CloseResult.INCONCLUSIVE,"INVALID_CLOSE");PrivacyBridge.cancel(token);check(PrivacyBridge.status(token)==PrivacyBridge.Status.FAILED,"INVALID_STATUS");}check(PrivacyBridge.status(open.launch())==PrivacyBridge.Status.STARTING,"OTHER_UNCHANGED");complete(open);}
            case "contention" -> {check(hold(true)==0,"HOLDER");try{var open=opened();var coordinator=new PrivacyLaunchCoordinator(open);var owner=new PrivacyLaunchOwner(List.of(args[2]),Map.of(),Path.of("/tmp"),coordinator,deadline());check(owner.await().state()==PrivacyLaunchOwner.State.REFUSED,"REFUSED_ARM");check(owner.close(deadline())==PrivacyLaunchOwner.Cleanup.COMPLETE,"NO_WINDOW_CLEANUP");complete(open);}finally{check(hold(false)==0,"RELEASE_HOLDER");}lifecycle(args[2],true);}
            case "disarm-allocation" -> {check(hold(true)==0,"HOLDER");try{var open=opened();var delegate=new PrivacyLaunchCoordinator(open);var port=new PrivacyLaunchOwner.CapturePort(){
                public PrivacyBridge.ForkResult arm(){return delegate.arm();}
                public PrivacyLaunchOwner.Step register(long pid){return delegate.register(pid);}
                public PrivacyBridge.DisarmResult disarm(){allocationFault(1);return delegate.disarm();}
                public void cancel(){delegate.cancel();}
                public PrivacyLaunchOwner.Cleanup close(long left){return delegate.close(left);}
            };var owner=new PrivacyLaunchOwner(List.of(args[2]),Map.of(),Path.of("/tmp"),port,deadline());check(owner.await().state()==PrivacyLaunchOwner.State.REFUSED,"DISARM_ALLOCATION");check(owner.close(System.nanoTime()+20_000_000L)==PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,"DISARM_RESULT_QUARANTINE");}finally{check(hold(false)==0,"HOLDER_CLEANUP");}var next=opened();var rejected=new PrivacyLaunchOwner(List.of(args[2]),Map.of(),Path.of("/tmp"),new PrivacyLaunchCoordinator(next),deadline());check(rejected.await().failure()==PrivacyLaunchOwner.Failure.RESOURCE,"JAVA_WINDOW_UNCERTAIN");rejected.close(deadline());return;}
            case "disarm-fault" -> {disarmFault();var open=opened();var coordinator=new PrivacyLaunchCoordinator(open);var owner=new PrivacyLaunchOwner(List.of(args[2]),Map.of("ES_LAUNCH_TEST_ENDPOINT",open.socketPath()),Path.of("/tmp"),coordinator,deadline());check(owner.await().state()==PrivacyLaunchOwner.State.REFUSED,"DISARM_REFUSED");check(owner.close(System.nanoTime()+20_000_000L)==PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,"QUARANTINE");var another=opened();var rejected=new PrivacyLaunchOwner(List.of(args[2]),Map.of(),Path.of("/tmp"),new PrivacyLaunchCoordinator(another),deadline());check(rejected.await().failure()==PrivacyLaunchOwner.Failure.RESOURCE,"WINDOW_QUARANTINE");rejected.close(deadline());return;}
            default -> throw new AssertionError("MODE");
        }
        check(metrics()==0,"RETAINED_OWNERS");
    }
}
