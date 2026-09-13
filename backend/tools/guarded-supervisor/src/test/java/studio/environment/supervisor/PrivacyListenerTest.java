package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

class PrivacyListenerTest {
    static Path scratch;
    static Path probe;
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
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-privacy-listener-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        assertEquals(0,run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=syscall,--wrap=close","-Isrc/main/c","src/main/c/privacy-listener.c","src/test/c/privacy-listener-probe.c","-o",probe.toString())));
    }
    void check(String mode) throws Exception {assertEquals(0,run(List.of(probe.toString(),mode)),mode);}
    @Test void actualOwnedSocketHasExactPermissionsAndAcceptedOwnership() throws Exception {check("live");}
    @Test void expiredStartupStillPermitsOwnedCleanup() throws Exception {check("deadline");}
    @Test void restrictiveUmaskAndMissingPinnedChmodHaveExplicitResults() throws Exception {for(String mode:List.of("umask","unsupported"))check(mode);}
    @Test void cancellationDoesNotBlockOwnedCleanup() throws Exception {check("cancel");}
    @Test void substitutedSocketIsPreserved() throws Exception {check("substitute");}
    @Test void liveAcceptedOwnerCapacityRefusesBeforeAnotherAccept() throws Exception {check("capacity");}
    @Test void completedCleanupCannotBeReopenedByARejectedRead() throws Exception {check("tombstone");}
    @Test void uncertainOrExpiredCleanupCannotRetryDescriptorNumbers() throws Exception {for(String mode:List.of("close-uncertain","cleanup-expired"))check(mode);}
    @Test void actualChildConnectionPreservesInventedBytes() throws Exception {check("child");}
    @Test void fullPathBoundaryAndInvalidAdmissionsAreExplicit() throws Exception {for(String mode:List.of("maximum-path","invalid","bad-parent-mode","symlink-parent"))check(mode);}
    @Test void realQueueSaturationDoesNotIncreaseAcceptedOwnerBound() throws Exception {check("saturation");}
    @Test void closedTokensAndMaximumLaunchAcceptsRemainBounded() throws Exception {check("generations");}
    @Test void transferredSocketRetainsCapacityAndCannotBeClosedByListener() throws Exception {for(String mode:List.of("transfer","transfer-close"))check(mode);}
    @Test void transferredCapacityAndSingleActiveHandshakeCannotBeBypassed() throws Exception {for(String mode:List.of("transfer-capacity","transfer-one-active"))check(mode);}
    @Test void transferAliasesAndFalseSettlementsPreserveActualOwnership() throws Exception {for(String mode:List.of("transfer-alias","transfer-invalid-settlement","transfer-early-close"))check(mode);}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);}
}
