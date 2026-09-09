package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;

/** Independently authored original-owner controls. No production JNI/admission. */
class IndependentPrivacyNativeLaunchTest {
    static Path directory, probe;
    @BeforeAll static void compile() throws Exception {
        directory=Files.createTempDirectory(Path.of("/tmp"),"es-independent-launch-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=directory.resolve("probe");
        assertEquals(0,PrivacyConnectionTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=eventfd,--wrap=getrandom,--wrap=close,--wrap=es_listener_close","-Isrc/main/c","src/main/c/privacy-peer.c","src/main/c/privacy-fork.c","src/main/c/privacy-listener.c","src/main/c/privacy-wire.c","src/main/c/privacy-connection.c","src/main/c/privacy-root.c","src/main/c/privacy-launch.c","src/main/c/privacy-maps.c","src/test/c/independent-privacy-launch-probe.c","-o",probe.toString())));
    }
    void check(String mode) throws Exception {assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),mode)),mode);}
    @Test void invalidAliasesCannotAllocateOrOverwriteOwnersAndInputs() throws Exception {check("aliases");}
    @Test void partialEntropyExhaustionClosesOriginalDescriptorAndWipesIdentifier() throws Exception {check("entropy");}
    @Test void actualSaturatedEventfdCannotResetCancellationOrAffectAnotherOwner() throws Exception {check("saturated");}
    @Test void successfulSubordinateCleanupReturningLateIsStillInconclusive() throws Exception {check("late");}
    @Test void concurrentCloseCanShortenDuringSettlingAndNeverReuseClosedNumbers() throws Exception {check("shortened");}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(directory!=null)Files.deleteIfExists(directory);}
}
