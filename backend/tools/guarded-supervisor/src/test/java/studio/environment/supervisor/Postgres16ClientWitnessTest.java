package studio.environment.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.server.export.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ES_POSTGRES16_CLIENT_WITNESS", matches = "true")
class Postgres16ClientWitnessTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String IMAGE = System.getenv().getOrDefault("ES_POSTGRES16_IMAGE", "postgres:16.11-bookworm");
    private static final String PASSWORD = "invented-secret";
    private static final String DATABASE = "MockDb";
    private static final String ORIGINAL = "<tile value=\"old\"/>";
    private static final String TARGET = "<tile value=\"new\"/>";
    private String container;
    private final Set<String> volumes = new TreeSet<>();
    @TempDir Path directory;

    @AfterEach void cleanup() throws Exception {
        if (container != null) {
            recordVolumes();
            run(Duration.ofSeconds(20), "docker", "rm", "-f", "-v", container);
            var inspectedContainer = run(Duration.ofSeconds(5), false, "docker", "container", "inspect", container);
            requireMissingDockerResource("container", container, inspectedContainer.code, inspectedContainer.stdout);
            for (String volume : volumes) {
                var inspectedVolume = run(Duration.ofSeconds(5), false, "docker", "volume", "inspect", volume);
                requireMissingDockerResource("volume", volume, inspectedVolume.code, inspectedVolume.stdout);
            }
        }
    }

    @Test void postgres16PsqlAppliesGeneratedProgramOnlyAfterSupervisorCommitAcknowledgement() throws Exception {
        startContainer();
        setupTable(ORIGINAL);
        var input = packageForLiveDatabase();
        var outcome = new SessionEngine().execute(protocol(input, null));
        assertEquals(new SessionEngine.Outcome(SessionEngine.Status.APPLIED, SessionEngine.Cleanup.COMPLETE), outcome);
        assertEquals(TARGET, scalar("SELECT xml_data FROM \"MockSchema\".\"CanvasRows\" WHERE slot='01';"));
    }

    @Test void postgres16PsqlAcknowledgesRollbackWhenSupervisorRefusesBeforeProgramWrite() throws Exception {
        startContainer();
        setupTable(ORIGINAL);
        var input = packageForLiveDatabase();
        var outcome = new SessionEngine().execute(protocol(input, Fault.PROGRAM_WRITE));
        assertEquals(new SessionEngine.Outcome(SessionEngine.Status.NOT_APPLIED, SessionEngine.Cleanup.COMPLETE), outcome);
        assertEquals(ORIGINAL, scalar("SELECT xml_data FROM \"MockSchema\".\"CanvasRows\" WHERE slot='01';"));
    }

    @Test void postgres16PsqlPostCommitAcknowledgementLossStaysUnknownWithoutRollbackClaim() throws Exception {
        startContainer();
        setupTable(ORIGINAL);
        var input = packageForLiveDatabase();
        var outcome = new SessionEngine().execute(protocol(input, Fault.COMMITTED_FRAME_READ));
        assertEquals(new SessionEngine.Outcome(SessionEngine.Status.UNKNOWN, SessionEngine.Cleanup.INCONCLUSIVE), outcome);
        assertEquals(TARGET, scalar("SELECT xml_data FROM \"MockSchema\".\"CanvasRows\" WHERE slot='01';"));
    }

    private enum Fault { PROGRAM_WRITE, COMMITTED_FRAME_READ }

    private SessionEngine.Wire protocol(PackageCheck.Regenerated input, Fault fault) throws Exception {
        var command = List.of("/usr/bin/docker", "exec", "-i", container,
                "psql", "-X", "-W", "-A", "-t", "-q", "-v", "ON_ERROR_STOP=on", "-v", "ON_ERROR_ROLLBACK=off", "-P", "pager=off",
                "-U", "postgres", "-d", DATABASE);
        NativeProcess child = new OwnedNativeProcess(Path.of("/usr/bin/setsid"), command, Map.of("LANG", "C.UTF-8", "LC_ALL", "C.UTF-8"), directory);
        if (fault == Fault.PROGRAM_WRITE) child = failOnProgramWrite(child);
        if (fault == Fault.COMMITTED_FRAME_READ) child = loseCommittedFrame(child);
        var secret = BoundedSecret.read(new ByteArrayInputStream((PASSWORD + "\n").getBytes(StandardCharsets.UTF_8)));
        return new ClientProtocol(child, input, "0123456789abcdef0123456789abcdef", "postgres", secret, "");
    }

    private NativeProcess failOnProgramWrite(NativeProcess owned) {
        return new NativeProcess() {
            boolean failed;
            @Override public void write(byte[] bytes, long deadline) {
                var prefix = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
                if (!failed && prefix.startsWith("DO ")) { failed = true; throw new Refusal("INJECTED_PROGRAM_WRITE_FAILURE"); }
                owned.write(bytes, deadline);
            }
            @Override public int read(long deadline) { return owned.read(deadline); }
            @Override public int exit(long deadline) { return owned.exit(deadline); }
            @Override public boolean alive() { return owned.alive(); }
            @Override public void terminate() { owned.terminate(); }
            @Override public void close() { owned.close(); }
        };
    }


    private NativeProcess loseCommittedFrame(NativeProcess owned) {
        return new NativeProcess() {
            final StringBuilder seen = new StringBuilder();
            @Override public void write(byte[] bytes, long deadline) { owned.write(bytes, deadline); }
            @Override public int read(long deadline) {
                int b = owned.read(deadline);
                if (b >= 0 && b < 128) {
                    seen.append((char) b);
                    if (seen.length() > 128) seen.delete(0, seen.length() - 128);
                    if (seen.toString().contains("ES_COMMITTED|")) throw new Refusal("INJECTED_COMMITTED_FRAME_LOSS");
                }
                return b;
            }
            @Override public int exit(long deadline) { return owned.exit(deadline); }
            @Override public boolean alive() { return owned.alive(); }
            @Override public void terminate() { owned.terminate(); }
            @Override public void close() { owned.close(); }
        };
    }

    private PackageCheck.Regenerated packageForLiveDatabase() throws Exception {
        var manifest = (ObjectNode) JSON.readTree(Files.readAllBytes(Path.of("../../../fixtures/guarded-package-v1/manifest.json")));
        var execution = (ObjectNode) manifest.get("execution");
        execution.put("serverVersion", "16.11").put("templateVersion", "postgresql16-text-v1");
        ((ObjectNode) execution.get("client")).put("version", "16.11");
        var destination = (ObjectNode) execution.get("destination");
        destination.put("database", DATABASE);
        var identity = (ObjectNode) destination.get("expectedPhysicalIdentity");
        identity.put("systemIdentifier", scalar("SELECT system_identifier::text FROM pg_catalog.pg_control_system();"));
        identity.put("databaseOid", scalar("SELECT oid::text FROM pg_catalog.pg_database WHERE datname=current_database();"));
        identity.put("databaseName", DATABASE);
        byte[] payload = Files.readAllBytes(Path.of("../../../fixtures/guarded-package-v1/payload.json"));
        var accepted = assertInstanceOf(PackageAdmission.Result.Accepted.class,
                new PackageAdmission().read(JSON.writeValueAsBytes(execution), payload));
        var candidate = assertInstanceOf(TransactionTemplates.Result.Candidate.class, new TransactionTemplates().generate(accepted));
        manifest.put("programDigest", accepted.programDigest());
        var members = (ObjectNode) manifest.get("members");
        for (var entry : Map.of("payload.json", payload, "transaction.sql", candidate.bytes(), "instructions.txt", PackageInstructions.bytes()).entrySet()) {
            var node = members.putObject(entry.getKey());
            node.put("bytes", entry.getValue().length);
            node.put("sha256", AdmittedFiles.sha256(entry.getValue()));
        }
        var out = new ByteArrayOutputStream();
        assertInstanceOf(StrictPackageZip.WriteResult.Written.class, new StrictPackageZip().write(List.of(
                new StrictPackageZip.Member("manifest.json", JSON.writeValueAsBytes(manifest)),
                new StrictPackageZip.Member("payload.json", payload),
                new StrictPackageZip.Member("transaction.sql", candidate.bytes()),
                new StrictPackageZip.Member("instructions.txt", PackageInstructions.bytes())), out));
        return PackageCheck.read(out.toByteArray(), AdmittedFiles.sha256(out.toByteArray()));
    }

    private void startContainer() throws Exception {
        container = "es-pg16-client-witness-" + Long.toHexString(new SecureRandom().nextLong()).replace("-", "0");
        run(Duration.ofSeconds(30), "docker", "run", "-d", "--name", container,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD, "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_INITDB_ARGS=--locale=C --encoding=UTF8", IMAGE);
        recordVolumes();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            var ready = run(Duration.ofSeconds(5), false, "docker", "exec", container, "pg_isready", "-U", "postgres", "-d", DATABASE);
            if (ready.code == 0) return;
            Thread.sleep(1000);
        }
        fail("POSTGRES16_WITNESS_NOT_READY");
    }

    private void recordVolumes() throws Exception {
        if (container == null) return;
        var inspected = run(Duration.ofSeconds(5), false, "docker", "inspect", "--format",
                "{{range .Mounts}}{{if eq .Type \"volume\"}}{{.Name}}{{\"\\n\"}}{{end}}{{end}}", container);
        if (inspected.code != 0) fail("DOCKER_VOLUME_DISCOVERY_UNCERTAIN: " + inspected.stdout);
        for (String line : inspected.stdout.lines().toList()) {
            var name = line.strip();
            if (!name.isEmpty()) volumes.add(name);
        }
    }

    static void requireMissingDockerResource(String kind, String name, int code, String output) {
        if (code == 0) fail("DOCKER_" + kind.toUpperCase(Locale.ROOT) + "_STILL_PRESENT: " + name);
        var text = output == null ? "" : output;
        if (code == 1 && exactMissingDockerResource(kind, name, text)) return;
        fail("DOCKER_" + kind.toUpperCase(Locale.ROOT) + "_ABSENCE_UNCERTAIN: " + text);
    }

    private static boolean exactMissingDockerResource(String kind, String name, String output) {
        var quoted = java.util.regex.Pattern.quote(name);
        if ("container".equals(kind))
            return java.util.regex.Pattern.compile("(?im)^.*no such container:\\s*" + quoted + "\\s*$").matcher(output).find();
        if ("volume".equals(kind)) {
            var direct = java.util.regex.Pattern.compile("(?im)^.*no such volume:\\s*" + quoted + "\\s*$").matcher(output).find();
            var get = java.util.regex.Pattern.compile("(?im)^.*get\\s+" + quoted + ":\\s*no such volume\\s*$").matcher(output).find();
            return direct || get;
        }
        return false;
    }

    private void setupTable(String value) throws Exception {
        execSql("CREATE SCHEMA \"MockSchema\";\n" +
                "CREATE TABLE \"MockSchema\".\"CanvasRows\" (slot text PRIMARY KEY, xml_data text NOT NULL);\n" +
                "INSERT INTO \"MockSchema\".\"CanvasRows\" VALUES ('01', '" + value.replace("'", "''") + "');\n");
    }

    private String scalar(String sql) throws Exception {
        return run(Duration.ofSeconds(10), "docker", "exec", "-e", "PGPASSWORD=" + PASSWORD, container,
                "psql", "-X", "-A", "-t", "-q", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", DATABASE, "-c", sql).stdout.strip();
    }

    private void execSql(String sql) throws Exception {
        var process = new ProcessBuilder("docker", "exec", "-i", "-e", "PGPASSWORD=" + PASSWORD, container,
                "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", DATABASE).redirectErrorStream(true).start();
        try (var stdin = process.outputWriter(StandardCharsets.UTF_8)) { stdin.write(sql); }
        var result = new Result(process, Duration.ofSeconds(10));
        result.finish();
        if (result.code != 0) throw new AssertionError("setup SQL failed: " + result.stdout);
    }

    private static Result run(Duration timeout, String... command) throws Exception { return run(timeout, true, command); }
    private static Result run(Duration timeout, boolean requireSuccess, String... command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var result = new Result(process, timeout);
        result.finish();
        if (requireSuccess && result.code != 0) throw new AssertionError("command failed: " + command[0] + " output=" + result.stdout);
        return result;
    }
    private static final class Result {
        final Process process; final Duration timeout; int code = Integer.MIN_VALUE; String stdout = "";
        Result(Process process, Duration timeout) { this.process = process; this.timeout = timeout; }
        void finish() throws Exception {
            if (code != Integer.MIN_VALUE) return;
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) { process.destroyForcibly(); throw new AssertionError("command timed out"); }
            stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            code = process.exitValue();
        }
    }
}
