package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Production JNI/registry and real owned FORK; only fixture records are substituted. */
class PrivacyBridgeTest {
    static Path directory, parent, fixture, production, child;
    static final List<String> COMPONENTS=List.of("controls","peer","fork","listener","wire","connection","root","launch","maps","file","hash","image","elf");
    @BeforeAll static void compile() throws Exception {
        var permissions=PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        directory=Files.createTempDirectory(Path.of("/tmp"),"es-jni-build-",permissions);
        parent=Files.createTempDirectory(Path.of("/tmp"),"es-jni-🧪-",permissions);
        fixture=directory.resolve("fixture.so");production=directory.resolve("production.so");child=directory.resolve("child");
        for(boolean mock:List.of(false,true)){
            var command=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIC","-shared","-Wl,-z,relro,-z,now,-z,defs","-Isrc/main/c","-I"+System.getProperty("java.home")+"/include","-I"+System.getProperty("java.home")+"/include/linux"));
            for(String name:COMPONENTS)command.add("src/main/c/privacy-"+name+".c");
            command.add("src/main/c/privacy-jni.c");
            if(mock)command.addAll(List.of("-DES_FIXTURE_PARENT=\""+parent+"\"","-DES_FIXTURE_JAVA=\""+System.getProperty("java.runtime.version")+"\"","src/test/c/privacy-compiled-fixture.c","src/test/c/privacy-registry-fixture.c","-Wl,--wrap=es_root_disarm,--wrap=es_root_arm,--wrap=es_compiled_find_chain,--wrap=es_registry_event,--wrap=close,--wrap=es_listener_path,--wrap=es_listener_open,--wrap=getrandom,--wrap=eventfd,--wrap=es_launch_close"));
            else command.addAll(List.of("src/main/c/privacy-compiled.c","src/main/c/privacy-registry.c"));
            command.addAll(List.of("-lcrypto","-o",(mock?fixture:production).toString()));
            assertEquals(0,PrivacyConnectionTest.run(command));
        }
        var command=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fPIE","-pie","-Isrc/main/c","-Wl,--wrap=eventfd,--wrap=getrandom,--wrap=write,--wrap=close,--wrap=es_root_capture,--wrap=es_root_arm,--wrap=es_root_match,--wrap=es_root_register,--wrap=es_root_disarm,--wrap=es_root_close,--wrap=es_listener_open,--wrap=es_listener_accept,--wrap=es_connection_open"));
        for(String name:COMPONENTS)command.add("src/main/c/privacy-"+name+".c");
        command.addAll(List.of("src/test/c/privacy-launch-probe.c","-lcrypto","-o",child.toString()));
        assertEquals(0,PrivacyConnectionTest.run(command));
    }
    static void mode(String mode) throws Exception {
        String classes=Path.of("target/test-classes").toAbsolutePath()+java.io.File.pathSeparator+Path.of("target/classes").toAbsolutePath();
        var command=List.of("/usr/bin/env","LANG=C.UTF-8","LC_ALL=C.UTF-8",Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djdk.lang.Process.launchMechanism=FORK","-XX:ErrorFile=/dev/null","-XX:-CreateCoredumpOnCrash","-XX:-HeapDumpOnOutOfMemoryError","-cp",classes,PrivacyBridgeProbe.class.getName(),(mode.equals("empty")?production:fixture).toString(),mode,child.toString(),production.toString());
        assertEquals(0,mode.startsWith("native-")?nativeMode(command):PrivacyConnectionTest.run(command),mode);
        // Actual subprocess exit releases invocation-owned base FD; successful
        // launch modes must have removed their own endpoints before that exit.
        if(!mode.equals("disarm-fault")&&!mode.equals("expired-publication")&&!mode.equals("startup-expired-publication")&&!mode.equals("expired-refusal-allocation")&&!Set.of("native-path-expired","native-startup-expired","native-listener-expired","native-path-refusal-allocation").contains(mode))try(var entries=Files.list(parent)){assertEquals(0,entries.count(),"launch namespace leaked");}
    }
    static int nativeMode(List<String> command)throws Exception{
        var builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");
        var process=builder.start();
        try{
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"native construction fault bound");
            byte[] output=process.getInputStream().readNBytes(8193);assertTrue(output.length<=8192,"bounded invented diagnostic");
            System.out.print(new String(output,java.nio.charset.StandardCharsets.US_ASCII));return process.exitValue();
        }finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}
    }
    @Test void failedNativeConstructionConsumesOnlyOriginalCleanupAllowance()throws Exception{
        for(String name:List.of("native-path-expired","native-listener-expired"))expiredMode(name);
        mode("native-entropy-expired");mode("native-event-expired");
    }
    @Test void unexpiredNativeConstructionFailureUsesOnlyTimeStillRemaining()throws Exception{
        for(String name:List.of("native-entropy-unexpired","native-listener-unexpired","native-event-unexpired"))mode(name);
    }
    @Test void failedStartupCannotBorrowFromLongerOperationClock()throws Exception{expiredMode("native-startup-expired");}
    @Test void nativeFailureResultAllocationRetainsOriginalRefusalAndCleanup()throws Exception{expiredMode("native-path-refusal-allocation");}
    @Test void expiryBeforeNativeInitializationDoesNotInventResourceOwnership()throws Exception{mode("native-preinit-expired");}
    @Test void malformedLinkageCannotLeavePartiallyRegisteredMethods() throws Exception {
        Path scratch=Files.createDirectory(directory.resolve("linkage"));
        try {
            String source=Files.readString(Path.of("src/main/java/studio/environment/supervisor/PrivacyBridge.java"));
            Files.writeString(scratch.resolve("PrivacyBridge.java"),source.replace("static native void cancel(long launch);","static native void invalidCancel(long launch);"));
            assertEquals(0,PrivacyConnectionTest.run(List.of(Path.of(System.getProperty("java.home"),"bin","javac").toString(),"-d",scratch.toString(),scratch.resolve("PrivacyBridge.java").toString())));
            String classes=scratch+java.io.File.pathSeparator+Path.of("target/test-classes").toAbsolutePath();
            assertEquals(0,PrivacyConnectionTest.run(List.of("/usr/bin/env","LANG=C.UTF-8","LC_ALL=C.UTF-8",Path.of(System.getProperty("java.home"),"bin","java").toString(),"-XX:ErrorFile=/dev/null","-XX:-CreateCoredumpOnCrash","-XX:-HeapDumpOnOutOfMemoryError","-cp",classes,PrivacyBridgeLinkageProbe.class.getName(),production.toString())));
        } finally {try(var files=Files.walk(scratch)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
    }
    @Test void parentTrustAndNativeCloseUncertaintyNeverFallBack() throws Exception {
        Files.setPosixFilePermissions(parent,PosixFilePermissions.fromString("rwxrwx---"));
        try{mode("parent-refused");}finally{Files.setPosixFilePermissions(parent,PosixFilePermissions.fromString("rwx------"));}
        mode("parent-close-fault");mode("close-error");
    }
    @Test void anotherLibraryOrClassloaderCannotResetInvocation() throws Exception {mode("duplicate-library");mode("wrong-loader");}
    @Test void productionRecordsRemainEmpty(){assertDoesNotThrow(()->mode("empty"));}
    @Test void actualForkReachesMandatoryMissingIdentityWithoutChallenge() throws Exception {mode("lifecycle");}
    @Test void failedExecStillClosesOriginalOwnership() throws Exception {mode("failed-exec");}
    @Test void fourLiveAnd256LifetimeLimitsRetainOldTombstones() throws Exception {mode("capacity");mode("tokens");}
    @Test void referenceDrainAndShortenedCleanupKeepOriginalOwnership() throws Exception {mode("held-reference");mode("shortened-close");}
    @Test void lateTokensCannotTouchReusedDescriptorsOrStealFinally() throws Exception {mode("descriptor-reuse");mode("wrong-thread");}
    @Test void secondCoordinatorRefusesAndCancellationWakesOriginalCapture() throws Exception {mode("coordinator-cancel");}
    @Test void invalidTokensNeverTouchOtherOwners() throws Exception {mode("invalid");}
    @Test void nativeContentionRefusesWithoutQuarantiningJavaWindow() throws Exception {mode("contention");}
    @Test void allocationFailuresAndUnpublishedTokensRetainOriginalCleanup() throws Exception {mode("open-allocation");mode("arm-allocation");mode("late-publication");}
    @Test void originalStartupAndOperationClocksIncludeRegistryWork() throws Exception {mode("clocks");}
    @Test void resultConstructionCannotPublishAfterOriginalDeadline() throws Exception {
        mode("unexpired-publication");
        for(String expired:List.of("expired-publication","startup-expired-publication"))expiredMode(expired);
    }
    @Test void refusalConstructionFailurePreservesExpiredOwnershipAndExactException() throws Exception {
        expiredMode("expired-refusal-allocation");
    }
    @Test void coordinatorConstructionExceptionRetainsJavaUncertaintyAfterNativeCleanup() throws Exception {
        mode("event-allocation");
    }
    static void expiredMode(String expired) throws Exception {
        try {
            mode(expired);
            // Expired cleanup cannot prove namespace removal. Observe the
            // retained endpoint only after its JVM has actually terminated.
            try(var entries=Files.list(parent)){
                var remaining=entries.toList();assertEquals(1,remaining.size(),"inconclusive namespace retained");
                assertTrue(Files.exists(remaining.getFirst().resolve("control.sock")));
            }
        } finally {
            // External fixture teardown never upgrades native INCONCLUSIVE.
            try(var entries=Files.list(parent)){
                for(Path entry:entries.toList()){Files.deleteIfExists(entry.resolve("control.sock"));Files.delete(entry);}
            }
        }
    }
    @Test void selfControlsCoverExistingAndFutureJavaThreads() throws Exception {mode("threads");}
    @Test void unpublishedDisarmProofCannotReleaseUnknownJavaWindow() throws Exception {mode("disarm-allocation");}
    @Test void trueDisarmUncertaintyQuarantinesOriginalJavaWindow() throws Exception {
        try{mode("disarm-fault");}finally{
            // This mode asserted INCONCLUSIVE and exited. Exclusive fixture
            // teardown removes its residual names; never upgrades its outcome.
            try(var entries=Files.list(parent)){for(Path entry:entries.toList()){Files.deleteIfExists(entry.resolve("control.sock"));Files.delete(entry);}}
        }
    }
    @AfterAll static void cleanup() throws Exception {
        for(Path file:List.of(fixture,production,child))Files.deleteIfExists(file);
        Files.delete(directory);Files.delete(parent);
    }
}
