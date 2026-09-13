package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.*;

class PrivacyArgumentsTest {
    static Path scratch, probe;
    @BeforeAll static void compile() throws Exception {
        scratch = Files.createTempDirectory(Path.of("/tmp"), "es-privacy-arguments-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe = scratch.resolve("probe");
        assertEquals(0, PrivacyPeerTest.run(List.of("/usr/bin/cc", "-std=c17", "-O2", "-Wall", "-Wextra", "-Werror",
                "-fstack-protector-strong", "-D_FORTIFY_SOURCE=3", "-fPIE", "-pie", "-Wl,-z,relro,-z,now",
                "-Wl,--wrap=open,--wrap=read,--wrap=__read_chk,--wrap=close,--wrap=fstatfs,--wrap=clock_gettime",
                "-Isrc/main/c", "src/main/c/privacy-peer.c", "src/main/c/privacy-arguments.c",
                "src/test/c/privacy-arguments-probe.c", "-o", probe.toString())));
    }
    void check(String mode) throws Exception { assertEquals(0, PrivacyPeerTest.run(List.of(probe.toString(), mode)), mode); }
    @Test void actualExecArgumentsPreserveExactUnicodeSpacesAndEmptyArgument() throws Exception { check("live"); }
    @Test void partialReadsAndEintrPreserveExactBytes() throws Exception { check("partial"); check("eintr"); }
    @Test void allDeclaredArgumentBoundsAreAcceptedExactly() throws Exception { check("max-count"); check("max-width"); check("max-bytes"); }
    @Test void differentOrAdditionalArgumentsCannotMatch() throws Exception { check("wrong-order"); check("reordered"); check("extra-argument"); }
    @Test void invalidExpectedVectorsRefuseBeforeInspection() throws Exception {
        for (String mode : List.of("unterminated", "too-wide", "too-many", "zero", "too-large", "null", "null-bytes", "overlap")) check(mode);
    }
    @Test void actualChildArgumentMemoryChangeBetweenReadsRefuses() throws Exception { check("actual-change"); }
    @Test void bothCompleteReadingsMustMatch() throws Exception { check("changed"); check("second-change"); }
    @Test void emptyMalformedTruncatedAndOverflowingEvidenceRefuses() throws Exception {
        for (String mode : List.of("empty", "oversized", "missing-nul", "truncate", "actual-overflow")) check(mode);
    }
    @Test void originalPeerMustRemainAliveAndKeepItsStartIdentity() throws Exception { check("dead"); check("changed-start"); check("final-death"); }
    @Test void originalCancellationAndDeadlineApplyThroughFinalEof() throws Exception {
        for (String mode : List.of("cancel", "deadline", "final-cancel", "final-deadline")) check(mode);
    }
    @Test void unavailableOrNonProcfsArgumentFilesRefuse() throws Exception { check("open-fault"); check("wrong-fs"); check("read-fault"); }
    @Test void closeUncertaintyOverridesReadFailureWithoutRetryingReusedNumber() throws Exception { check("close-fault"); check("read-close-fault"); }
    @AfterAll static void cleanup() throws Exception {
        if (probe != null) Files.deleteIfExists(probe);
        if (scratch != null) Files.deleteIfExists(scratch);
    }
}
