package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.*;

class PrivacyMapsTest {
    static Path scratch,probe;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-native-maps-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        assertEquals(0,PrivacyConnectionTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=open,--wrap=__open_2,--wrap=read,--wrap=__read_chk,--wrap=close,--wrap=fstatfs,--wrap=es_peer_read","-Isrc/main/c","src/main/c/privacy-peer.c","src/main/c/privacy-fork.c","src/main/c/privacy-listener.c","src/main/c/privacy-wire.c","src/main/c/privacy-connection.c","src/main/c/privacy-root.c","src/main/c/privacy-launch.c","src/main/c/privacy-maps.c","src/main/c/privacy-file.c","src/main/c/privacy-hash.c","src/main/c/privacy-image.c","src/main/c/privacy-elf.c","src/test/c/privacy-maps-probe.c","-lcrypto","-o",probe.toString())));
    }
    @Test void actualOwnedSplitFileMappingHasIndependentExactKernelMetadata() throws Exception {
        assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString())));
    }
    @Test void existingLaunchReceiverOwnsSamplingAfterCorrelation() throws Exception {
        assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),"launch")));
    }
    @Test void opaqueLabelsAndCompleteFragmentedSamplesRemainExact() throws Exception { modes("synthetic","fragment","high"); }
    @Test void malformedAndOverlappingRecordsRefuseWholeOutput() throws Exception { modes("format-upper","format-overflow","format-device","format-permissions","format-overlap","format-truncated","format-label","format-nul"); }
    @Test void exactAndOneOverRecordStreamAndLineBoundsAreDistinct() throws Exception { modes("records-exact","records-over","bytes-exact","bytes-over","line-over"); }
    @Test void changedSecondSamplesAndSyscallFailuresNeverPublish() throws Exception { modes("second-change","second-short","open-fault","second-open-fault","read-fault","filesystem-fault","wrong-filesystem","interrupts"); }
    @Test void closeUncertaintyDominatesAndFinalCancellationWipes() throws Exception { modes("close-uncertain","close-cancel","final-cancel"); }
    @Test void actualOpaquePathsAndChangedLiveMappingAreNotTrustedNamesOrAtomicSnapshots() throws Exception { modes("actual-newline","actual-nonutf8","actual-deleted","actual-change"); }
    @Test void deathCancellationAndExpiryDuringReadsAndFinalCloseWipeAllEvidence() throws Exception { modes("read-death","read-cancel","read-deadline","final-death","final-deadline","eof-cancel","eof-deadline"); }
    @Test void invalidAliasesAndOnceReceiverOwnershipPreserveBorrowedResources() throws Exception { modes("invalid","launch-double","launch-wrong-thread"); }
    @Test void closeDuringActualHeldReadRetainsOwnershipUntilReceiverStopsAndUncertaintyStaysSticky() throws Exception { modes("launch-held","launch-uncertain"); }
    static void modes(String... values) throws Exception { for(String value:values)assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),value)),value); }
    @AfterAll static void cleanup() throws Exception {
        if(probe!=null)Files.deleteIfExists(probe);
        if(scratch!=null)Files.deleteIfExists(scratch);
    }
}
