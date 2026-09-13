package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.*;

class PrivacyParentTest {
    static Path scratch, probe;
    @BeforeAll static void compile() throws Exception {
        scratch = Files.createTempDirectory(Path.of("/tmp"), "es-privacy-parent-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe = scratch.resolve("probe");
        assertEquals(0, PrivacyPeerTest.run(List.of("/usr/bin/cc", "-std=c17", "-O2", "-Wall", "-Wextra", "-Werror",
                "-fstack-protector-strong", "-D_FORTIFY_SOURCE=3", "-fPIE", "-pie", "-Wl,-z,relro,-z,now",
                "-Wl,--wrap=es_peer_read,--wrap=open,--wrap=read,--wrap=__read_chk,--wrap=close,--wrap=fstatfs",
                "-Isrc/main/c", "src/main/c/privacy-peer.c", "src/main/c/privacy-parent.c",
                "src/test/c/privacy-parent-probe.c", "-o", probe.toString())));
    }
    void check(String mode) throws Exception { assertEquals(0, PrivacyPeerTest.run(List.of(probe.toString(), mode)), mode); }
    @Test void actualTwoGenerationParentEdgeMatchesBothPins() throws Exception { check("live"); }
    @Test void legalCommAndPartialReadsPreserveExactParentage() throws Exception { check("comm"); check("partial"); }
    @Test void reversedOrSameProcessCannotSupplyAParentEdge() throws Exception { check("reverse"); check("same"); }
    @Test void actualGrandparentCannotStandInForDirectParent() throws Exception { check("grandparent"); }
    @Test void invalidOverlappingOwnersAreNotMutated() throws Exception { check("null"); check("overlap"); }
    @Test void foreignLaunchControlsRefuse() throws Exception { check("foreign-cancel"); check("foreign-deadline"); }
    @Test void cancellationAndOriginalDeadlineRefuse() throws Exception { check("cancel"); check("deadline"); }
    @Test void eitherExitedProcessRefuses() throws Exception { check("parent-exit"); check("child-exit"); }
    @Test void deathDuringRecheckCannotAcceptStaleParentage() throws Exception { check("second-parent-exit"); check("after-read-exit"); }
    @Test void deathAfterTheFinalParentReadStillRefuses() throws Exception { check("final-read-exit"); }
    @Test void parentMustRemainLiveAfterTheFinalEdgeRead() throws Exception { check("final-parent-exit"); }
    @Test void pinnedStartAndReadStartMustBothMatch() throws Exception { check("changed-start"); check("wrong-start"); }
    @Test void bothActualParentRecordsMustMatch() throws Exception { check("wrong-parent"); check("second-change"); }
    @Test void malformedOversizedAndNonProcfsEvidenceRefuses() throws Exception {
        for (String mode : List.of("malformed", "embedded-nul", "overflow", "oversized", "wrong-fs")) check(mode);
    }
    @Test void fileFailuresRefuseAndClosedNumbersAreNotRetried() throws Exception {
        for (String mode : List.of("open-fault", "read-fault", "close-fault")) check(mode);
    }
    @AfterAll static void cleanup() throws Exception {
        if (probe != null) Files.deleteIfExists(probe);
        if (scratch != null) Files.deleteIfExists(scratch);
    }
}
