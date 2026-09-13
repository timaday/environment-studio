package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.*;

class IndependentPrivacyMapsTest {
    static Path scratch, probe;
    @BeforeAll static void compile() throws Exception {
        scratch = Files.createTempDirectory(Path.of("/tmp"), "es-independent-maps-test-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe = scratch.resolve("probe");
        assertEquals(0, PrivacyConnectionTest.run(List.of("/usr/bin/cc", "-std=c17", "-O2", "-Wall", "-Wextra", "-Werror",
                "-fstack-protector-strong", "-D_FORTIFY_SOURCE=3", "-fPIE", "-pie", "-Wl,-z,relro,-z,now",
                "-Wl,--wrap=open,--wrap=__open_2,--wrap=read,--wrap=__read_chk,--wrap=close,--wrap=es_peer_read",
                "-Isrc/main/c", "src/main/c/privacy-peer.c", "src/main/c/privacy-maps.c",
                "src/test/c/independent-privacy-maps-probe.c", "-o", probe.toString())));
    }
    @Test void actualSharedAndInaccessibleMappingsRemainCompleteEvidence() throws Exception { modes("actual-shared"); }
    @Test void allPermissionCombinationsAndNumericExtremaRemainExact() throws Exception { modes("grammar", "empty", "second-extra"); }
    @Test void interruptAllowanceIsSharedAcrossBothStreams() throws Exception { modes("retry-exact", "retry-over"); }
    @Test void secondCloseUncertaintyDominatesCancellationWithoutReclosingAReusedDescriptor() throws Exception { modes("second-close", "second-close-cancel"); }
    static void modes(String... modes) throws Exception {
        for (String mode : modes) assertEquals(0, PrivacyConnectionTest.run(List.of(probe.toString(), mode)), mode);
    }
    @AfterAll static void cleanup() throws Exception {
        if (probe != null) Files.deleteIfExists(probe);
        if (scratch != null) Files.deleteIfExists(scratch);
    }
}
