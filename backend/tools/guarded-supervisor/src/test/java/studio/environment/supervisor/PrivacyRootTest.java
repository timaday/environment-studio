package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;

class PrivacyRootTest {
    static Path scratch, probe, library;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-privacy-root-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        assertEquals(0,PrivacyConnectionTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=close,--wrap=es_fork_read,--wrap=es_connection_read","-Isrc/main/c","src/main/c/privacy-peer.c","src/main/c/privacy-fork.c","src/main/c/privacy-listener.c","src/main/c/privacy-wire.c","src/main/c/privacy-connection.c","src/main/c/privacy-root.c","src/test/c/privacy-root-probe.c","-o",probe.toString())));
        compileJni();
    }
    @Test void capturedReturnedRootMatchesItsSeparatePreparedSocket() throws Exception {
        assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),"live")));
    }
    static void compileJni() throws Exception {
        library=scratch.resolve("librootprobe.so");
        String home=System.getProperty("java.home");
        assertEquals(0,PrivacyConnectionTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIC","-shared","-Wl,-z,relro,-z,now","-I"+home+"/include","-I"+home+"/include/linux","-Isrc/main/c","src/main/c/privacy-controls.c","src/main/c/privacy-peer.c","src/main/c/privacy-fork.c","src/main/c/privacy-listener.c","src/main/c/privacy-wire.c","src/main/c/privacy-connection.c","src/main/c/privacy-root.c","src/test/c/privacy-root-jni-probe.c","-o",library.toString())));
    }
    void javaProbe(String mechanism) throws Exception {
        String classpath=Path.of("target/test-classes").toAbsolutePath()+java.io.File.pathSeparator+Path.of("target/classes").toAbsolutePath();
        assertEquals(0,PrivacyConnectionTest.run(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djdk.lang.Process.launchMechanism="+(mechanism.equals("FAILED_EXEC")?"FORK":mechanism),"-cp",classpath,PrivacyRootProbe.class.getName(),library.toString(),probe.toString(),mechanism)),mechanism);
    }
    @Test void actualJavaOwnerCorrelatesForkAndPreservesMergedOutput() throws Exception {javaProbe("FORK");}
    @Test void actualJavaPosixSpawnCannotReplaceMissingForkCapture() throws Exception {javaProbe("POSIX_SPAWN");}
    void modes(String... modes) throws Exception {for(String mode:modes)assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),mode)),mode);}
    @Test void invalidOrderAndRepeatedCorrelationAreSticky() throws Exception {modes("before-capture","before-disarm","duplicate-register","duplicate-match","stale-generation","rearm");}
    @Test void exactReturnedPidAndLauncherAreRequired() throws Exception {modes("wrong-pid","zero-pid","wide-pid","wrong-thread","wrong-disarm");}
    @Test void unrelatedAndInheritedSocketCreatorsCannotMatch() throws Exception {modes("unrelated","inherited");}
    @Test void deadOrCancelledPinsCannotBeRegisteredOrMatched() throws Exception {modes("dead-register","dead-match","cancel-register","cancel-match","expired-arm","cancel-arm");}
    @Test void missingHookAndArmedCleanupNeverCreateAuthority() throws Exception {modes("missing-hook","close-armed","expired-close");}
    @Test void invalidOutputCannotOverwriteEitherOwner() throws Exception {modes("null","root-alias","connection-alias");}
    @Test void stalledCaptureUsesOriginalDeadlineAndBorrowedCancellation() throws Exception {modes("capture-cancel","capture-deadline");}
    @Test void bothPinsAndEveryIdentityComponentRemainRequired() throws Exception {modes("death-during-match","death-after-peer","wrong-start","wrong-uid","wrong-gid");}
    @Test void uncertainCloseNeverRetriesAReusedDescriptor() throws Exception {modes("uncertain-close");}
    @Test void failedExecCannotRegisterAnUnreturnedProcess() throws Exception {javaProbe("FAILED_EXEC");}
    @Test void freshOrBusyRejectionCannotPretendToEndAnOwnedWindow() throws Exception {modes("fresh","busy-arm");}
    @Test void borrowedConnectionMustUseTheSameOriginalLaunchControls() throws Exception {modes("foreign-cancel","foreign-deadline","peer-cancel","wire-cancel","peer-deadline","wire-deadline","cancel-during-match");}
    @Test void completedDisarmMayOverlapCaptureButCannotAllowLateRegistration() throws Exception {for(int i=0;i<8;i++)modes("disarm-overlap");}
    @AfterAll static void cleanup() throws Exception {if(library!=null)Files.deleteIfExists(library);if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);}
}
