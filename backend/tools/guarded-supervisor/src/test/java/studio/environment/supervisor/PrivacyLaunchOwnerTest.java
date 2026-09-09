package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class PrivacyLaunchOwnerTest {
    static long deadline(){return System.nanoTime()+TimeUnit.SECONDS.toNanos(3);}
    static class Port implements PrivacyLaunchOwner.CapturePort {
        final List<String> calls=new CopyOnWriteArrayList<>();
        volatile Thread armed,registered,disarmed;
        volatile long pid;
        public PrivacyLaunchOwner.Step arm(){calls.add("arm");armed=Thread.currentThread();return PrivacyLaunchOwner.Step.OK;}
        public PrivacyLaunchOwner.Step register(long value){calls.add("register");registered=Thread.currentThread();pid=value;return PrivacyLaunchOwner.Step.OK;}
        public PrivacyLaunchOwner.Step disarm(){calls.add("disarm");disarmed=Thread.currentThread();return PrivacyLaunchOwner.Step.OK;}
        public void cancel(){calls.add("cancel");}
        public PrivacyLaunchOwner.Cleanup close(long remaining){calls.add("close");return PrivacyLaunchOwner.Cleanup.COMPLETE;}
    }
    static PrivacyLaunchOwner owner(Port port){return new PrivacyLaunchOwner(List.of("/usr/bin/sleep","20"),Map.of(),Path.of("/tmp"),port,deadline());}
    @Test void ownsExactlyOneActualProcessOnTheDedicatedPlatformThread(){
        Port port=new Port();var owner=owner(port);
        try {
            assertEquals(new PrivacyLaunchOwner.Result(PrivacyLaunchOwner.State.REGISTERED,PrivacyLaunchOwner.Failure.NONE),owner.await());
            assertEquals(List.of("arm","register","disarm"),port.calls);
            assertSame(port.armed,port.registered);assertSame(port.armed,port.disarmed);
            assertNotSame(Thread.currentThread(),port.armed);assertFalse(port.armed.isVirtual());
            assertTrue(port.pid>0);assertTrue(ProcessHandle.of(port.pid).orElseThrow().isAlive());
            assertEquals(owner.await(),owner.await());assertEquals(3,port.calls.size());
        } finally {owner.close(deadline());}
        assertEquals(PrivacyLaunchOwner.Cleanup.COMPLETE,owner.close(deadline()));
        assertFalse(ProcessHandle.of(port.pid).map(ProcessHandle::isAlive).orElse(false));
    }
    @Test void failedActualExecDisarmsWithoutInventingAProcess(){
        Port port=new Port();var owner=new PrivacyLaunchOwner(List.of("/es-invented-missing-executable"),Map.of(),Path.of("/tmp"),port,deadline());
        try {assertEquals(PrivacyLaunchOwner.State.REFUSED,owner.await().state());assertEquals(List.of("arm","disarm"),port.calls);assertEquals(0,port.pid);}
        finally {owner.close(deadline());}
        assertEquals(PrivacyLaunchOwner.Cleanup.COMPLETE,owner.close(deadline()));
    }

    static void released(CountDownLatch latch){try {assertTrue(latch.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError();}}
    static class BlockedPort extends Port {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        final boolean blockRegister;
        BlockedPort(boolean register){blockRegister=register;}
        @Override public PrivacyLaunchOwner.Step arm(){var step=super.arm();if(!blockRegister){entered.countDown();released(release);}return step;}
        @Override public PrivacyLaunchOwner.Step register(long pid){var step=super.register(pid);if(blockRegister){entered.countDown();released(release);}return step;}
        @Override public PrivacyLaunchOwner.Step disarm(){try{return super.disarm();}finally{done.countDown();}}
    }
    @Test void concurrentArmedWindowRefusesWithoutStartingOrTouchingAnotherPort(){
        var first=new BlockedPort(false);var owned=owner(first);released(first.entered);
        Port second=new Port();var refused=owner(second);
        try {
            assertEquals(PrivacyLaunchOwner.Failure.RESOURCE,refused.await().failure());
            assertEquals(List.of(),second.calls);
        }finally{first.release.countDown();owned.await();owned.close(deadline());refused.close(deadline());}
    }
    @Test void cancellationDuringArmNeverStartsAndFinallyDisarmsOnItsThread(){
        var port=new BlockedPort(false);var owned=owner(port);released(port.entered);
        owned.cancel();port.release.countDown();
        assertEquals(PrivacyLaunchOwner.Failure.CANCELLED,owned.await().failure());
        assertEquals(0,port.pid);assertSame(port.armed,port.disarmed);
        assertEquals(PrivacyLaunchOwner.Cleanup.COMPLETE,owned.close(deadline()));
    }
    @Test void startupExpiryDoesNotRenewAStalledPortOrCreateAChild(){
        var port=new BlockedPort(false);var owned=new PrivacyLaunchOwner(List.of("/usr/bin/sleep","20"),Map.of(),Path.of("/tmp"),port,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(60));
        released(port.entered);
        assertEquals(PrivacyLaunchOwner.Failure.DEADLINE,owned.await().failure());
        port.release.countDown();released(port.done);
        assertEquals(PrivacyLaunchOwner.Cleanup.COMPLETE,owned.close(deadline()));assertEquals(0,port.pid);
    }
    @Test void stalledRegisterRetainsWindowAndCleanupUncertaintyUntilDisarmActuallyEnds(){
        var port=new BlockedPort(true);var owned=owner(port);released(port.entered);
        try {
            assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(40)));
            Port other=new Port();var rejected=owner(other);
            assertEquals(PrivacyLaunchOwner.Failure.RESOURCE,rejected.await().failure());rejected.close(deadline());
            assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(deadline()));
        }finally{port.release.countDown();released(port.done);}
        assertEquals(PrivacyLaunchOwner.State.REFUSED,owned.await().state());
        assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(deadline()));
        assertFalse(ProcessHandle.of(port.pid).map(ProcessHandle::isAlive).orElse(false));
    }
    @Test void copiedInputsAndClearedEnvironmentAreUsedByTheActualChild() throws Exception {
        var dir=java.nio.file.Files.createTempDirectory("es-launch-mock-");
        try {
            var command=new ArrayList<>(List.of("/bin/sh","-c","test \"$INVENTED\" = fixed && test -z \"$HOME\" && : > result; exec /usr/bin/sleep 20"));
            var env=new HashMap<String,String>();env.put("INVENTED","fixed");
            var port=new BlockedPort(false);var owned=new PrivacyLaunchOwner(command,env,dir,port,deadline());released(port.entered);
            command.clear();env.put("INVENTED","changed");port.release.countDown();
            try {
                assertEquals(PrivacyLaunchOwner.State.REGISTERED,owned.await().state());
                long end=deadline();while(!java.nio.file.Files.exists(dir.resolve("result"))&&System.nanoTime()<end)Thread.sleep(1);
                assertTrue(java.nio.file.Files.exists(dir.resolve("result")));
            }finally{owned.close(deadline());}
        }finally{java.nio.file.Files.deleteIfExists(dir.resolve("result"));java.nio.file.Files.deleteIfExists(dir);}
    }
    @Test void expiredAndExcessiveStartupBudgetsRefuseBeforeArming(){
        for(long end:new long[]{System.nanoTime()-1,System.nanoTime()+TimeUnit.SECONDS.toNanos(11)}){
            Port port=new Port();var owned=new PrivacyLaunchOwner(List.of("/usr/bin/sleep","20"),Map.of(),Path.of("/tmp"),port,end);
            assertEquals(PrivacyLaunchOwner.Failure.DEADLINE,owned.await().failure());assertTrue(port.calls.isEmpty());owned.close(deadline());
        }
    }
    @Test void refusalAndThrownArmStillDisarmWithoutLeakingCause(){
        for(boolean throwing:new boolean[]{false,true}){
            Port port=new Port(){@Override public PrivacyLaunchOwner.Step arm(){super.arm();if(throwing)throw new AssertionError("invented-private-diagnostic");return PrivacyLaunchOwner.Step.REFUSED;}};
            var owned=owner(port);assertEquals(PrivacyLaunchOwner.State.REFUSED,owned.await().state());
            assertEquals(List.of("arm","disarm"),port.calls);assertFalse(owned.toString().contains("invented"));
            assertEquals(PrivacyLaunchOwner.Cleanup.COMPLETE,owned.close(deadline()));
        }
    }
    @Test void nativeCleanupRefusalAndExpiredCleanupAreSticky(){
        for(boolean expired:new boolean[]{false,true}){
            Port port=new Port(){@Override public PrivacyLaunchOwner.Cleanup close(long remaining){super.close(remaining);return PrivacyLaunchOwner.Cleanup.INCONCLUSIVE;}};
            var owned=owner(port);owned.await();
            assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(expired?System.nanoTime()-1:deadline()));
            assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(deadline()));
        }
    }

    @Test void disarmMustFinishInsideTheOriginalStartupBudget() throws Exception {
        CountDownLatch disarmEntered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        Port port=new Port(){@Override public PrivacyLaunchOwner.Step disarm(){disarmEntered.countDown();released(release);try{return super.disarm();}finally{done.countDown();}}};
        var owned=new PrivacyLaunchOwner(List.of("/usr/bin/sleep","20"),Map.of(),Path.of("/tmp"),port,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(60));
        released(disarmEntered);Thread.sleep(90);release.countDown();released(done);Thread.sleep(10);
        try {assertEquals(PrivacyLaunchOwner.Failure.DEADLINE,owned.await().failure());}
        finally{owned.close(deadline());}
    }

    @Test void nativeRefusalNeverPublishesRegistrationAndCleanupRunsOnce(){
        Port port=new Port(){@Override public PrivacyLaunchOwner.Step register(long pid){super.register(pid);return PrivacyLaunchOwner.Step.REFUSED;}};
        var owned=owner(port);assertEquals(PrivacyLaunchOwner.State.REFUSED,owned.await().state());
        assertEquals(PrivacyLaunchOwner.Cleanup.COMPLETE,owned.close(deadline()));
        assertEquals(PrivacyLaunchOwner.Cleanup.COMPLETE,owned.close(System.nanoTime()-1));
        assertEquals(1,Collections.frequency(port.calls,"close"));
        assertEquals(1,Collections.frequency(port.calls,"cancel"));
    }
    @Test void cancellationFailureCannotSkipNativeCleanupOrBecomeComplete(){
        Port port=new Port(){@Override public void cancel(){super.cancel();throw new IllegalStateException("invented-private-cause");}};
        var owned=owner(port);owned.await();
        assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(deadline()));
        assertEquals(1,Collections.frequency(port.calls,"close"));
        assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(deadline()));
    }
    @Test void repeatedCleanupShortensTheAlreadyRunningBudget() throws Exception {
        CountDownLatch nativeClosed=new CountDownLatch(1);
        var port=new BlockedPort(false){@Override public PrivacyLaunchOwner.Cleanup close(long left){nativeClosed.countDown();return super.close(left);}};
        var owned=owner(port);released(port.entered);
        Thread first=Thread.ofPlatform().start(()->owned.close(deadline()));
        try {
            long until=deadline();while(!port.calls.contains("cancel")&&System.nanoTime()<until)Thread.sleep(1);
            owned.close(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(30));
            assertTrue(nativeClosed.await(400,TimeUnit.MILLISECONDS),"cleanup wait must observe shortened budget");
            assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owned.close(deadline()));
        }finally{port.release.countDown();released(port.done);first.join(1000);}
    }
    @Test void virtualCallerStillUsesDedicatedPlatformLauncher() throws Exception {
        Port port=new Port();var future=new CompletableFuture<PrivacyLaunchOwner>();
        Thread.ofVirtual().start(()->future.complete(owner(port)));
        var owned=future.get(3,TimeUnit.SECONDS);
        try{assertEquals(PrivacyLaunchOwner.State.REGISTERED,owned.await().state());assertFalse(port.armed.isVirtual());}
        finally{owned.close(deadline());}
    }
    @Test void failedDisarmKeepsTheProcessWideQuarantineInAnOwnedJvm() throws Exception {
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Djdk.lang.Process.launchMechanism=FORK","-cp",System.getProperty("java.class.path"),getClass().getName(),"disarm-refusal").redirectErrorStream(true);
        builder.environment().clear();Process child=builder.start();
        try {assertTrue(child.waitFor(8,TimeUnit.SECONDS));assertEquals(0,child.exitValue());assertEquals(0,child.getInputStream().readNBytes(1024).length);}
        finally{if(child.isAlive()){child.destroyForcibly();child.waitFor(3,TimeUnit.SECONDS);}}
    }
    public static void main(String[] args){
        Port port=new Port(){@Override public PrivacyLaunchOwner.Step disarm(){super.disarm();return PrivacyLaunchOwner.Step.REFUSED;}};
        var owned=owner(port);
        if(owned.await().state()!=PrivacyLaunchOwner.State.REFUSED)System.exit(41);
        if(owned.close(deadline())!=PrivacyLaunchOwner.Cleanup.INCONCLUSIVE)System.exit(42);
        var other=owner(new Port());
        if(other.await().failure()!=PrivacyLaunchOwner.Failure.RESOURCE)System.exit(43);
        other.close(deadline());
    }
}
