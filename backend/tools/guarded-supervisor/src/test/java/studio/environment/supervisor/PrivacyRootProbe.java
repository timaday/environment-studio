package studio.environment.supervisor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Test-only JNI wiring. No installed library, registry or native client admission. */
public final class PrivacyRootProbe {
    private static native String prepare();
    private static native int arm();
    private static native int register(long exactPid);
    private static native int disarm();
    private static native int match();
    private static native int releaseConnection();
    private static native void cancel();
    private static native int finish(long remaining);
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        String endpoint=prepare();
        if(endpoint==null){finish(TimeUnit.SECONDS.toNanos(1));System.exit(40);}
        boolean fork=args[2].equals("FORK");
        var port=new PrivacyLaunchOwner.CapturePort() {
            public PrivacyLaunchOwner.Step arm(){return PrivacyRootProbe.arm()==0?PrivacyLaunchOwner.Step.OK:PrivacyLaunchOwner.Step.REFUSED;}
            public PrivacyLaunchOwner.Step register(long pid){return PrivacyRootProbe.register(pid)==2?PrivacyLaunchOwner.Step.OK:PrivacyLaunchOwner.Step.REFUSED;}
            public PrivacyLaunchOwner.Step disarm(){return PrivacyRootProbe.disarm()==0?PrivacyLaunchOwner.Step.OK:PrivacyLaunchOwner.Step.REFUSED;}
            public void cancel(){PrivacyRootProbe.cancel();}
            public PrivacyLaunchOwner.Cleanup close(long remaining){return finish(remaining)==0?PrivacyLaunchOwner.Cleanup.COMPLETE:PrivacyLaunchOwner.Cleanup.INCONCLUSIVE;}
        };
        var owner=new PrivacyLaunchOwner(List.of(args[2].equals("FAILED_EXEC")?args[1]+".missing":args[1],"owned-child",endpoint),Map.of("ES_ROOT_TEST_ENDPOINT",endpoint),Path.of("/tmp"),port,System.nanoTime()+TimeUnit.SECONDS.toNanos(3));
        int failure=0;
        try {
            var result=owner.await();
            if(result.state()!=(fork?PrivacyLaunchOwner.State.REGISTERED:PrivacyLaunchOwner.State.REFUSED))failure=41;
            if(fork&&failure==0) {
                if(match()!=3)failure=42;
                // Inspection-only test access; never supplies/replaces the wrapper's Process.
                var field=PrivacyLaunchOwner.class.getDeclaredField("process");field.setAccessible(true);
                Process process=(Process)field.get(owner);
                if(process.getInputStream().available()!=0)failure=46;
                if(releaseConnection()!=0)failure=42;
                if(!process.waitFor(3,TimeUnit.SECONDS)||process.exitValue()!=0)failure=43;
                String output=new String(process.getInputStream().readNBytes(128),StandardCharsets.US_ASCII);
                if(!output.equals("INVENTED-OUT\nINVENTED-ERR\n"))failure=44;
            }
        } finally {if(owner.close(System.nanoTime()+TimeUnit.SECONDS.toNanos(3))!=PrivacyLaunchOwner.Cleanup.COMPLETE)failure=45;}
        System.exit(failure);
    }
}
