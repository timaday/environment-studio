package studio.environment.supervisor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Credential-free test JVM; no runtime bridge or registry authorization. */
public final class PrivacyForkProbe {
    private static native int prepare();
    private static native int disarm();
    private static native int capture(long expectedPid);
    private static native int finish();
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        if(prepare()!=0)System.exit(40);
        Process child=null;
        int result=0;
        boolean failedExec=args[2].equals("FAILED_EXEC");
        try {
            try {
                var builder=new ProcessBuilder(failedExec?args[1]+".missing":args[1],"owned-child").redirectErrorStream(true);
                builder.environment().clear();
                try {child=builder.start();if(failedExec)result=47;}
                catch(java.io.IOException failure){if(!failedExec)result=48;}
            } finally {if(disarm()!=0)result=41;}
            if(result==0&&failedExec){if(capture(0)!=5)result=49;if(finish()!=5)result=50;}
            if(result==0&&!failedExec){
                int captured=capture(child.pid());
                boolean fork=args[2].equals("FORK");
                if(captured!=(fork?1:4))result=42;
                if(finish()!=(fork?0:4))result=43;
                child.getOutputStream().close();
                if(!child.waitFor(5,TimeUnit.SECONDS))result=44;
                else if(child.exitValue()!=0)result=45;
                String output=new String(child.getInputStream().readNBytes(256),StandardCharsets.US_ASCII);
                if(!output.equals("INVENTED-OUT\nINVENTED-ERR\n"))result=46;
            }
        } finally {
            if(child!=null&&child.isAlive()){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);}
        }
        System.exit(result);
    }
}
