package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

/** Independent constructor-held interpreter and refusal/cleanup combinations, all invented. */
class IndependentPrivacyScriptTest {
    static Path directory, child, probe;
    @BeforeAll static void compile() throws Exception {
        directory = Files.createTempDirectory(Path.of("/tmp"), "es-independent-script-");
        Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        child = directory.resolve("interpreter"); probe = directory.resolve("probe");
        var flags = List.of("/usr/bin/cc", "-std=c17", "-O2", "-Wall", "-Wextra", "-Werror", "-fstack-protector-strong", "-D_FORTIFY_SOURCE=3", "-fPIE", "-pie", "-Wl,-z,relro,-z,now");
        var command = new ArrayList<>(flags);
        command.addAll(List.of("-DINDEPENDENT_CHILD", "src/test/c/independent-script-probe.c", "-o", child.toString()));
        assertEquals(0, PrivacyPeerTest.run(command));
        Files.setPosixFilePermissions(child, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var header = new StringBuilder();
        array(header, "script_sha", ("#!" + child + "\n# independent source\n").getBytes(StandardCharsets.UTF_8));
        array(header, "interpreter_sha", Files.readAllBytes(child));
        Files.writeString(directory.resolve("independent-script-expected.h"), header);
        command = new ArrayList<>(flags);
        command.addAll(List.of("-Wl,--wrap=es_hash_file,--wrap=open,--wrap=__open_2,--wrap=close", "-Isrc/main/c", "-I" + directory,
                "src/main/c/privacy-peer.c", "src/main/c/privacy-file.c", "src/main/c/privacy-hash.c", "src/main/c/privacy-image.c",
                "src/main/c/privacy-arguments.c", "src/main/c/privacy-script.c", "src/test/c/independent-script-probe.c", "-lcrypto", "-o", probe.toString()));
        assertEquals(0, PrivacyPeerTest.run(command));
    }
    static void array(StringBuilder out, String name, byte[] bytes) throws Exception {
        out.append("static const unsigned char ").append(name).append("[32]={");
        for (byte value : java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(Byte.toUnsignedInt(value)).append(',');
        out.append("};\n");
    }
    @Test void overlappingReadOnlyInputsWorkWhileActualInterpreterMainIsBlocked() throws Exception { check("overlap"); }
    @Test void deviceMismatchAloneRefusesAnOtherwiseMatchingReopenedScript() throws Exception { check("device"); }
    @Test void hashResourceRefusalStillClosesWithUncertaintyDominatingActualCancellation() throws Exception { check("resource-cleanup"); }
    @Test void earlierIdentityRefusalCannotHideFinalPeerInspectionCleanupUncertainty() throws Exception { check("earlier-identity-cleanup"); }
    static void check(String mode) throws Exception {
        var builder = new ProcessBuilder(probe.toString(), child.toString(), mode).redirectErrorStream(true);
        builder.environment().clear(); builder.environment().put("PATH", "/usr/bin:/bin");
        var process = builder.start();
        try {
            assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readNBytes(8193), StandardCharsets.US_ASCII);
            assertTrue(output.isEmpty() || output.matches("(?:INDEPENDENT_SCRIPT_[0-9]+\n)*"), "Unexpected probe output");
            assertEquals(0, process.exitValue(), output);
        } finally { if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)); } }
    }
    @AfterAll static void cleanup() throws Exception {
        if (directory != null) { try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); } Files.delete(directory); }
    }
}
