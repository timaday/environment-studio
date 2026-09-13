package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

class PrivacyForkTest {
    static Path scratch;
    static Path probe;
    static Path library;
    static int run(List<String> command) throws Exception {
        var builder=new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");
        var process=builder.start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"owned peer probe deadline");
            var output=process.getInputStream().readNBytes(8193);
            assertTrue(output.length<=8192,"bounded diagnostic output");
            String diagnostic=new String(output,java.nio.charset.StandardCharsets.US_ASCII);
            if(command.getFirst().equals("/usr/bin/cc"))assertEquals(0,process.exitValue(),"compiler setup: "+diagnostic);
            else if(process.exitValue()!=0)assertTrue(diagnostic.matches("(?:PROBE_ASSERT_LINE_[0-9]+\\n)*"),"unexpected diagnostic format");
            return process.exitValue();
        } finally {
            if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
        }
    }
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-privacy-fork-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        library=scratch.resolve("libforkprobe.so");
        assertEquals(0,run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=socketpair,--wrap=setsockopt,--wrap=close,--wrap=recvmsg,--wrap=send","-Isrc/main/c","src/main/c/privacy-peer.c","src/main/c/privacy-fork.c","src/test/c/privacy-fork-probe.c","-o",probe.toString())));
        String javaHome=System.getProperty("java.home");
        assertEquals(0,run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIC","-shared","-Wl,-z,relro,-z,now","-I"+javaHome+"/include","-I"+javaHome+"/include/linux","-Isrc/main/c","src/main/c/privacy-controls.c","src/main/c/privacy-peer.c","src/main/c/privacy-fork.c","src/test/c/privacy-fork-jni-probe.c","-o",library.toString())));
    }
    void check(String mode) throws Exception {assertEquals(0,run(List.of(probe.toString(),mode)),mode);}
    @Test void actualForkSenderIsCapturedBeforeExec() throws Exception {check("live");}
    @Test void originalDeadlineAndCancellationBoundArming() throws Exception {for(String mode:List.of("deadline","excess-deadline","cancel"))check(mode);}
    @Test void setupCloseUncertaintyClosesBothOwnedDescriptors() throws Exception {check("setup-close");}
    @Test void missingHookCannotCreateOwnership() throws Exception {check("missing-hook");}
    @Test void activeWindowQuarantinesDescriptorsUntilDisarm() throws Exception {check("close-armed");}
    @Test void malformedAncillaryAndRecordRefuseWithoutRetainingPins() throws Exception {for(String mode:List.of("truncated","wrong-record","extra-control"))check(mode);}
    @Test void cancelledDeadAndParentCloseUncertainCannotCapture() throws Exception {for(String mode:List.of("post-cancel","dead","parent-close"))check(mode);}
    @Test void wrongThreadAndRearmingCannotTouchTheActiveGeneration() throws Exception {for(String mode:List.of("wrong-thread","reinit"))check(mode);}
    @Test void actualJavaForkHasCapturedSenderAndExactMergedOutput() throws Exception {javaProbe("FORK");}
    @Test void actualPosixSpawnCannotSubstituteMissingCapture() throws Exception {javaProbe("POSIX_SPAWN");}
    void javaProbe(String mechanism) throws Exception {
        assertEquals(0,run(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djdk.lang.Process.launchMechanism="+(mechanism.equals("FAILED_EXEC")?"FORK":mechanism),"-cp",Path.of("target/test-classes").toAbsolutePath().toString(),PrivacyForkProbe.class.getName(),library.toString(),probe.toString(),mechanism)),mechanism);
    }
    @Test void duplicateAncillaryPinCannotCloseAReusedDescriptor() throws Exception {check("duplicate-pin");}
    @Test void actualRightsAndExtraPacketsAreRefused() throws Exception {for(String mode:List.of("rights","extra-packet"))check(mode);}
    @Test void stalledCaptureUsesTheOriginalDeadline() throws Exception {check("stalled");}
    @Test void captureAndDisarmCanOverlapWithoutSharingDescriptorWriters() throws Exception {for(int i=0;i<16;i++){check("concurrent");check("cancel-concurrent");}}
    @Test void invalidCaptureOrderingIsSticky() throws Exception {check("invalid-order");}
    @Test void actualTruncatedRightsAndUnsupportedKernelRefuse() throws Exception {for(String mode:List.of("rights-truncated","unsupported"))check(mode);}
    @Test void failedJavaExecCannotCreateARegisteredRoot() throws Exception {javaProbe("FAILED_EXEC");}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(library!=null)Files.deleteIfExists(library);if(scratch!=null)Files.deleteIfExists(scratch);}
}
