package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;
class IndependentPrivacyFileTest {
    static Path scratch, probe;
    @BeforeAll static void compile() throws Exception {
        scratch = Files.createTempDirectory("es-independent-file-probe-"); probe = scratch.resolve("probe");
        assertEquals(0, PrivacyPeerTest.run(List.of("/usr/bin/cc", "-std=c17", "-O2", "-Wall", "-Wextra", "-Werror",
                "-fstack-protector-strong", "-D_FORTIFY_SOURCE=3", "-fPIE", "-pie", "-Wl,-z,relro,-z,now",
                "-Isrc/main/c", "src/main/c/privacy-file.c", "src/main/c/privacy-hash.c", "src/test/c/independent-file-probe.c", "-lcrypto", "-o", probe.toString())));
    }
    @Test void actualMaximum4095BytePathAndOneOverRefusal() throws Exception { check("maximum"); }
    @Test void actualTmpfsOwnedFileFeedsExactHash() throws Exception { check("tmpfs"); }
    @Test void invalidNontruncatedContinuationRefusesWithoutAcquisition() throws Exception { check("invalid"); }
    void check(String mode) throws Exception {
        var builder = new ProcessBuilder(probe.toString(), mode).redirectErrorStream(true);
        builder.environment().clear(); builder.environment().put("PATH", "/usr/bin:/bin");
        var child = builder.start();
        try {
            assertTrue(child.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
            String output = new String(child.getInputStream().readNBytes(8193), java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(output.isEmpty() || output.matches("(?:INDEPENDENT_FILE_[0-9]+\n)*"));
            assertEquals(0, child.exitValue(), output);
        } finally { if (child.isAlive()) { child.destroyForcibly(); assertTrue(child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)); } }
    }
    @AfterAll static void cleanup() throws Exception { Files.deleteIfExists(probe); Files.deleteIfExists(scratch); }
}
