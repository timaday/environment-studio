package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;

class PrivacyNativeLaunchTest {
    static Path scratch, probe, library;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-native-launch-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        assertEquals(0,PrivacyConnectionTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=eventfd,--wrap=getrandom,--wrap=write,--wrap=close,--wrap=es_root_capture,--wrap=es_root_arm,--wrap=es_root_match,--wrap=es_root_register,--wrap=es_root_disarm,--wrap=es_root_close,--wrap=es_listener_open,--wrap=es_listener_accept,--wrap=es_connection_open","-Isrc/main/c","src/main/c/privacy-peer.c","src/main/c/privacy-fork.c","src/main/c/privacy-listener.c","src/main/c/privacy-wire.c","src/main/c/privacy-connection.c","src/main/c/privacy-root.c","src/main/c/privacy-launch.c","src/main/c/privacy-maps.c","src/test/c/privacy-launch-probe.c","-o",probe.toString())));
    }
    static void compileJni() throws Exception {
        library=scratch.resolve("liblaunch.so");
        var command=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIC","-shared","-Wl,-z,relro,-z,now","-Isrc/main/c",
                "-I"+System.getProperty("java.home")+"/include","-I"+System.getProperty("java.home")+"/include/linux"));
        for(String name:List.of("controls","peer","fork","listener","wire","connection","root","launch","maps"))command.add("src/main/c/privacy-"+name+".c");
        command.addAll(List.of("src/test/c/privacy-launch-jni-probe.c","-o",library.toString()));
        assertEquals(0,PrivacyConnectionTest.run(command));
    }
    @Test void actualForkUsesExistingJavaOwnerAndPrivateReceiver() throws Exception { javaProbe("FORK"); }
    @Test void posixSpawnAndFailedStartNeverCorrelate() throws Exception { javaProbe("POSIX_SPAWN"); javaProbe("FAILED_EXEC"); }
    static void javaProbe(String mode) throws Exception {
        if(library==null)compileJni();
        String classes=Path.of("target/test-classes").toAbsolutePath()+java.io.File.pathSeparator+Path.of("target/classes").toAbsolutePath();
        assertEquals(0,PrivacyConnectionTest.run(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Djdk.lang.Process.launchMechanism="+(mode.equals("POSIX_SPAWN")?mode:"FORK"),"-cp",classes,PrivacyNativeLaunchProbe.class.getName(),library.toString(),probe.toString(),mode)),mode);
    }
    @Test void ownsActualEndpointAndOriginalCleanup() throws Exception {
        assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString())));
    }
    @Test void freshStatusAndDurationArgumentsNeverGrantAuthority() throws Exception { modes("fresh","status-alias","duration"); }
    @Test void wrongThreadAndDuplicateWindowOperationsRemainRefused() throws Exception { modes("rearm","wrong-thread","double-disarm"); }
    @Test void completedFailedArmEndsOnlyItsOriginalWindow() throws Exception { modes("failed-arm"); }
    @Test void deathAfterCaptureCannotRegisterRoot() throws Exception { modes("dead-captured"); }
    @Test void interruptedWakeIsBoundedAndCannotRestoreAdmission() throws Exception { modes("signal-interrupted"); }
    @Test void sameRootAndExactReturnedProcessAreRequired() throws Exception { modes("live","foreign","wrong-pid"); }
    @Test void cancellationAfterAcceptPreventsConnectionWork() throws Exception { modes("cancel-accepted","cancel-match","close-correlate"); }
    @Test void receiverMayArriveBeforeArmAndStopsOnOriginalControls() throws Exception { modes("early-cancel","early-deadline"); }
    @Test void concurrentCloseShortensOriginalWaitersAndKeepsUncertainty() throws Exception { modes("short-close"); }
    @Test void closeDuringRegisterOrDisarmRetainsLateCleanupUncertainty() throws Exception { modes("close-register","close-disarm"); }
    @Test void signalReferencePreventsEventfdCloseAndReuseRace() throws Exception { modes("signal-close"); }
    @Test void partialOpenAndClosedDescriptorTombstonesRemainOwned() throws Exception { modes("entropy","entropy-interrupted","listener-fault","event-fault","partial-listener","root-uncertain","reuse","uncertain"); }
    @Test void overlongPathInvalidatesSafeOutputBeforeAllocation() throws Exception { modes("long-path","null-open"); }
    static void modes(String... modes) throws Exception {
        for(String mode:modes)assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),mode)),mode);
    }
    @AfterAll static void cleanup() throws Exception {
        if(library!=null)Files.deleteIfExists(library);
        if(probe!=null)Files.deleteIfExists(probe);
        if(scratch!=null)Files.deleteIfExists(scratch);
    }
}
