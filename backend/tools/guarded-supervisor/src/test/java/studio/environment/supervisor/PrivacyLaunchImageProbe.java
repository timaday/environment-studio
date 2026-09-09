package studio.environment.supervisor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only JNI and independently invented constructor; no installed authority. */
public final class PrivacyLaunchImageProbe {
    private static native String prepare(String path,String digest);
    private static native int passes();
    private static native int arm();
    private static native int captureAndCorrelate();
    private static native int register(long exactPid);
    private static native int disarm();
    private static native void cancel();
    private static native int finish(long remaining);
    private static native int releaseParent();
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        String endpoint=prepare(args[1],args[2]);
        if(endpoint==null)System.exit(41);
        boolean fork=args[3].equals("FORK");
        var receiverResult=new AtomicInteger(-1);
        var receiver=Thread.ofPlatform().start(()->receiverResult.set(captureAndCorrelate()));
        var port=new PrivacyLaunchOwner.CapturePort() {
            public PrivacyLaunchOwner.Step arm(){return PrivacyLaunchImageProbe.arm()==0?PrivacyLaunchOwner.Step.OK:PrivacyLaunchOwner.Step.REFUSED;}
            public PrivacyLaunchOwner.Step register(long pid){return PrivacyLaunchImageProbe.register(pid)==0?PrivacyLaunchOwner.Step.OK:PrivacyLaunchOwner.Step.REFUSED;}
            public PrivacyLaunchOwner.Step disarm(){return PrivacyLaunchImageProbe.disarm()==0?PrivacyLaunchOwner.Step.OK:PrivacyLaunchOwner.Step.REFUSED;}
            public void cancel(){PrivacyLaunchImageProbe.cancel();}
            public PrivacyLaunchOwner.Cleanup close(long left){return finish(left)==0?PrivacyLaunchOwner.Cleanup.COMPLETE:PrivacyLaunchOwner.Cleanup.INCONCLUSIVE;}
        };
        String executable=args[3].equals("FAILED_EXEC")?args[1]+".missing":args[1];
        var owner=new PrivacyLaunchOwner(List.of(executable,"owned-child"),Map.of("ES_IMAGE_TEST_ENDPOINT",endpoint),Path.of("/tmp"),port,System.nanoTime()+TimeUnit.SECONDS.toNanos(3));
        int failure=0;
        try {
            var result=owner.await();
            if(result.state()!=(fork?PrivacyLaunchOwner.State.REGISTERED:PrivacyLaunchOwner.State.REFUSED))failure=42;
            receiver.join(4000);
            if(receiver.isAlive())failure=43;
            if(fork&&failure==0) {
                if(receiverResult.get()!=0 || passes()!=5)failure=44;
                // Inspection-only read of the exact privately retained Process; never an input seam.
                var field=PrivacyLaunchOwner.class.getDeclaredField("process");field.setAccessible(true);
                Process process=(Process)field.get(owner);
                if(!process.isAlive())failure=45;
                if(finish(TimeUnit.SECONDS.toNanos(1))!=0)failure=46;
                if(!process.waitFor(3,TimeUnit.SECONDS)||process.exitValue()!=0)failure=47;
                String output=new String(process.getInputStream().readNBytes(128),StandardCharsets.US_ASCII);
                if(!output.equals("INVENTED-OUT\nINVENTED-ERR\n"))failure=48;
            } else if(receiverResult.get()==0)failure=49;
        } finally {
            if(owner.close(System.nanoTime()+TimeUnit.SECONDS.toNanos(3))!=PrivacyLaunchOwner.Cleanup.COMPLETE)failure=50;
            receiver.join(4000);
            if(receiver.isAlive()||releaseParent()!=0)failure=51;
        }
        System.exit(failure);
    }
}
