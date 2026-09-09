import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
class VerifyDistribution {
    public static void main(String[] args)throws Exception {
        Path home=Path.of(args[0]),scratch=Files.createTempDirectory("es-supervisor-launch-"),fixture=scratch.resolve("configuration.json");
        Files.copy(Path.of(args[1]),fixture);Files.setPosixFilePermissions(fixture,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var command=List.of(home.resolve("environment-studio-guarded").toString(),"apply","--package","/invented/package.zip","--sha256","a".repeat(64),"--configuration",fixture.toString(),"--destination","invented-mock");
        var builder=new ProcessBuilder(command).directory(Path.of("/var/tmp").toFile()).redirectErrorStream(true);
        builder.environment().putAll(Map.of("CLASSPATH","/invented/external/payload.jar","JAVA_TOOL_OPTIONS","-Dcanary=UNTRUSTED_JAVA_OPTIONS","JDK_JAVA_OPTIONS","-Dcanary=UNTRUSTED_JDK_OPTIONS","PATH","/invented/hostile"));
        var process=builder.start();process.getOutputStream().close();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("ASSEMBLED_LAUNCH_TIMEOUT");}byte[] output=process.getInputStream().readNBytes(4097);
        String pattern;try(var input=Files.newInputStream(Path.of("/proc/sys/kernel/core_pattern"))){byte[] value=input.readNBytes(4097);if(value.length>4096)throw new IllegalStateException("CORE_POLICY_UNAVAILABLE");pattern=new String(value,java.nio.charset.StandardCharsets.US_ASCII);}
        boolean filePolicy=!pattern.isBlank()&&!pattern.startsWith("|")&&pattern.stripTrailing().chars().noneMatch(c->c<32);
        String code=filePolicy?"RUNTIME_UNQUALIFIED":"RUNTIME_PRIVACY_UNQUALIFIED";
        String expected="{\"outcome\":\"REFUSED\",\"code\":\""+code+"\",\"cleanup\":\"COMPLETE\"}\n";
        if(process.exitValue()!=2||!Arrays.equals(output,expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IllegalStateException("ASSEMBLED_LAUNCH_FAILED");
        Files.delete(fixture);Files.delete(scratch);
        System.out.println("Standalone unrelated-directory hostile-environment launch PASS; runtime remains unqualified");
    }
}
