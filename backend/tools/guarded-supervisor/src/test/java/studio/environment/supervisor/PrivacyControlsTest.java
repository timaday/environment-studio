package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class PrivacyControlsTest {
    static Path scratch;
    static Path probe;
    static Path library;
    static int run(List<String> command) throws Exception {
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().put("PATH", "/usr/bin:/bin");
        var process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "owned probe exceeded deadline");
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(output.length() < 8192, "unexpected probe output size");
            if (process.exitValue() != 0 && command.get(0).equals("/usr/bin/cc")) fail("compiler setup failed: " + output);
            return process.exitValue();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
    @BeforeAll static void compile() throws Exception {
        scratch = Files.createTempDirectory(Path.of("/tmp"), "es-privacy-controls-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe = scratch.resolve("probe"); library = scratch.resolve("probe.so");
        var common = List.of("/usr/bin/cc", "-std=c17", "-O2", "-Wall", "-Wextra", "-Werror",
            "-fstack-protector-strong", "-D_FORTIFY_SOURCE=3", "-Wl,-z,relro,-z,now",
            "-Isrc/main/c", "src/main/c/privacy-controls.c");
        var executable = new ArrayList<>(common);
        executable.addAll(List.of("-fPIE", "-pie", "-pthread", "src/test/c/privacy-controls-probe.c", "-o", probe.toString()));
        assertEquals(0, run(executable));
        var shared = new ArrayList<>(common);
        shared.addAll(List.of("-fPIC", "-shared", "-I" + System.getProperty("java.home") + "/include",
            "-I" + System.getProperty("java.home") + "/include/linux", "src/test/c/privacy-controls-jni-probe.c", "-o", library.toString()));
        assertEquals(0, run(shared));
        Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(library, PosixFilePermissions.fromString("rwx------"));
    }
    @Test void suppressesAndDeniesEveryNonzeroResetEncoding() throws Exception { assertEquals(0, run(List.of(probe.toString(),"healthy"))); }
    @Test void highOptionWordCannotHideDumpableReset() throws Exception { assertEquals(0, run(List.of(probe.toString(),"option-high-word"))); }
    @Test void forkInheritsSuppressionAndFilter() throws Exception { assertEquals(0, run(List.of(probe.toString(),"fork"))); }
    @Test void x32CannotBypassNativeSyscallFilter() throws Exception { assertEquals(0, run(List.of(probe.toString(),"x32"))); }
    @Test void incompatibleAuditArchitectureKillsOwnedProbe() throws Exception { assertEquals(159, run(List.of(probe.toString(),"compat"))); }
    @Test void divergentExistingFilterRefusesThreadSynchronization() throws Exception { assertEquals(0, run(List.of(probe.toString(),"diverged"))); }
    @Test void synchronizesAlreadyRunningJvmThreadAndFutureThread() throws Exception {
        assertEquals(0, run(List.of(System.getProperty("java.home") + "/bin/java",
            "-XX:ErrorFile=/dev/null", "-XX:-CreateCoredumpOnCrash", "-XX:-HeapDumpOnOutOfMemoryError",
            "-cp", Path.of("target/test-classes").toAbsolutePath().toString(),
            "studio.environment.supervisor.PrivacyControlsJvmProbe", library.toString())));
    }
    @AfterAll static void cleanup() throws Exception {
        if (scratch != null) {
            if (probe != null) Files.deleteIfExists(probe);
            if (library != null) Files.deleteIfExists(library);
            Files.deleteIfExists(scratch);
        }
    }
}

class PrivacyControlsJvmProbe {
    static native int establish();
    static native int check();
    public static void main(String[] arguments) throws Exception {
        System.load(arguments[0]);
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var result = new java.util.concurrent.atomic.AtomicInteger(99);
        var existing = new Thread(() -> {
            ready.countDown();
            try { release.await(); result.set(check()); }
            catch (InterruptedException interrupted) { result.set(98); }
        });
        existing.start(); ready.await();
        int admission = establish();
        release.countDown(); existing.join();
        if (admission != 0 || result.get() != 0 || check() != 0) System.exit(41);
        var future = new Thread(() -> result.set(check()));
        future.start(); future.join();
        if (result.get() != 0) System.exit(42);
    }
}
