package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;
import studio.environment.server.definition.*;

/** Invented JDBC/definition controls; no real engine or projection qualification. */
class IndependentV3ObservationTest {
    @Test void expiredStartupCannotOpenEitherVersionAndReleasesTheSharedSlot() throws Exception {
        var database = new V3ObservationTest.Database(Engine.POSTGRESQL);
        var adapter = new JdbcObservation(database.destination(), c -> database.open(), () -> {}, 1,
                TimeUnit.SECONDS.toNanos(1));
        var v3 = V3ObservationTest.selection(Engine.POSTGRESQL);
        var v2 = ReadOperationPolicyTest.selection(ReadOperationPolicyTest.PG);
        for (int i = 0; i < 6; i++) {
            var credentials = V3ObservationTest.credentials();
            var result = assertInstanceOf(Refused.class, i % 2 == 0
                    ? adapter.observeV3(v3, credentials, new ObservationPort.Cancellation())
                    : adapter.observe(v2, credentials, new ObservationPort.Cancellation()));
            assertEquals(Code.DEADLINE_EXCEEDED, result.code());
            assertEquals(Cleanup.COMPLETE, result.cleanup());
            assertTrue(credentials.closed());
        }
        assertEquals(0, database.opens);
        assertInstanceOf(Complete.class, database.adapter().observeV3(v3,
                V3ObservationTest.credentials(), new ObservationPort.Cancellation()));
    }
    @Test void freshConsistentCheckedDigestsCannotBypassAnotherPublicationBlocker() throws Exception {
        var tree = V3ObservationTest.JSON.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v3/definition.json")));
        var fields = (ArrayNode) tree.path("bindings").get(0).path("documents").get(0)
                .path("entities").get(0).path("fields");
        fields.remove(1);
        var incomplete = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeV3DefinitionBytesCompiler().compile(V3ObservationTest.JSON.writeValueAsBytes(tree),
                        DefinitionBytesCompiler.Format.JSON));
        assertTrue(incomplete.diagnostics().stream().anyMatch(d -> d.code().equals("FIELD_MAPPING_MISSING")));
        var selection = new ObservationPort.V3Selection(incomplete.checked(), "mock-pg");
        var database = new V3ObservationTest.Database(Engine.POSTGRESQL);
        for (int i = 0; i < 6; i++) {
            var credentials = V3ObservationTest.credentials();
            assertEquals(Code.INVALID_SELECTION, assertInstanceOf(Refused.class,
                    database.adapter().observeV3(selection, credentials, new ObservationPort.Cancellation())).code());
            assertTrue(credentials.closed());
        }
        assertEquals(0, database.opens);
        assertInstanceOf(Complete.class, database.adapter().observeV3(V3ObservationTest.selection(Engine.POSTGRESQL),
                V3ObservationTest.credentials(), new ObservationPort.Cancellation()));
    }
    @Test void closedUnusedPermitCannotStartLaterOrReleaseAnAdditionalSlot() throws Exception {
        var database = new V3ObservationTest.Database(Engine.POSTGRESQL);
        var adapter = database.adapter(); var selection = V3ObservationTest.selection(Engine.POSTGRESQL);
        var closed = assertInstanceOf(ObservationPort.Reservation.Admitted.class, adapter.reserveV3(selection)).permit();
        closed.close();
        var held = new ArrayList<ObservationPort.Permit>();
        try {
            for (int i = 0; i < 4; i++) held.add(assertInstanceOf(ObservationPort.Reservation.Admitted.class,
                    adapter.reserveV3(selection)).permit());
            var credentials = V3ObservationTest.credentials();
            assertEquals(Code.INVALID_SELECTION, assertInstanceOf(Refused.class,
                    closed.observe(credentials, new ObservationPort.Cancellation())).code());
            assertTrue(credentials.closed()); closed.close();
            assertEquals(Code.CAPACITY, assertInstanceOf(ObservationPort.Reservation.Refused.class,
                    adapter.reserveV3(selection)).code());
            assertEquals(0, database.opens);
        } finally { held.forEach(ObservationPort.Permit::close); }
    }
    @Test void expiredStartupAllocatesNoWorkerInAnIsolatedWarmedJvm() throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                ExpiredThreadProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Isolated startup control timed out");
            String diagnostic = new String(process.getInputStream().readNBytes(128), java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(Set.of("", "EXPIRED_WORKER_ALLOCATED\n", "EXPIRED_STARTUP_CONTROL_FAILED\n").contains(diagnostic),
                    "Unexpected isolated control diagnostic");
            assertEquals(0, process.exitValue(), diagnostic);
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            process.getInputStream().close(); process.getOutputStream().close(); process.getErrorStream().close();
        }
    }
    public static final class ExpiredThreadProbe {
        public static void main(String[] arguments) {
            try {
                var threads = java.lang.management.ManagementFactory.getThreadMXBean();
                var database = new V3ObservationTest.Database(Engine.POSTGRESQL);
                var selection = V3ObservationTest.selection(Engine.POSTGRESQL);
                var adapter = new JdbcObservation(database.destination(), c -> database.open(), () -> {},
                        1, TimeUnit.SECONDS.toNanos(1));
                // Initialize management, current compiler, logging policy and failure/cleanup classes first.
                for (int i = 0; i < 8; i++) {
                    var result = adapter.observeV3(selection, V3ObservationTest.credentials(), new ObservationPort.Cancellation());
                    if (!(result instanceof Refused refused) || refused.code() != Code.DEADLINE_EXCEEDED)
                        throw new AssertionError("WARMUP_REFUSED");
                }
                var credentials = V3ObservationTest.credentials();
                var cancellation = new ObservationPort.Cancellation();
                long before = threads.getTotalStartedThreadCount();
                var result = adapter.observeV3(selection, credentials, cancellation);
                long after = threads.getTotalStartedThreadCount();
                if (after != before) throw new AssertionError("EXPIRED_WORKER_ALLOCATED");
                if (!(result instanceof Refused refused) || refused.code() != Code.DEADLINE_EXCEEDED
                        || refused.cleanup() != Cleanup.COMPLETE || !credentials.closed() || database.opens != 0)
                    throw new AssertionError("EXPIRED_RESULT_INVALID");
            } catch (Throwable failed) {
                System.out.println(failed instanceof AssertionError && "EXPIRED_WORKER_ALLOCATED".equals(failed.getMessage())
                        ? "EXPIRED_WORKER_ALLOCATED" : "EXPIRED_STARTUP_CONTROL_FAILED");
                System.exit(40);
            }
        }
    }

}
