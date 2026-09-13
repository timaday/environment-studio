package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.observation.ObservationPort;
import studio.environment.core.observation.ObservationResult.*;
import studio.environment.core.observation.TransientCredentials;

/** Original mock catalog plumbing; cancellation oracles are independent of adapter result logic. */
class ObservationCancellationPublicationTest {
    enum At { ROLLBACK, CLOSE, AFTER_CLOSE }
    static Stream<Arguments> cleanupPoints() {
        return Arrays.stream(Engine.values()).flatMap(engine ->
            Arrays.stream(At.values()).map(at -> Arguments.of(engine, at)));
    }
    static Stream<Engine> engines() { return Arrays.stream(Engine.values()); }
    static TransientCredentials credentials() {
        return new TransientCredentials("mock_owner".toCharArray(), "mock-cancel-canary".toCharArray());
    }
    @ParameterizedTest @MethodSource("cleanupPoints")
    void cancellationDuringCompletedCleanupCannotPublishObservation(Engine engine, At at) {
        var database = new ReadOperationPolicyTest.Database(engine);
        var cancellation = new ObservationPort.Cancellation();
        var credentials = credentials();
        var adapter = new JdbcObservation(database.destination(), ignored -> {
            Connection original = database.open();
            return ReadOperationPolicyTest.proxy(Connection.class, (proxy, method, args) -> {
                if (method.getName().equals(at == At.ROLLBACK ? "rollback" : "close") && at != At.AFTER_CLOSE) cancellation.cancel();
                Object result;
                try { result = method.invoke(original, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
                if (method.getName().equals("close") && at == At.AFTER_CLOSE) cancellation.cancel();
                return result;
            });
        }, () -> {}, TimeUnit.SECONDS.toNanos(5), TimeUnit.SECONDS.toNanos(1));
        var result = adapter.observe(ReadOperationPolicyTest.selection(ReadOperationPolicyTest.binding(engine)), credentials, cancellation);
        assertAll(() -> assertTrue(cancellation.cancelled()),
            () -> assertTrue(credentials.closed()),
            () -> assertEquals(1, database.opens), () -> assertEquals(1, database.rollbacks),
            () -> assertEquals(1, database.closes), () -> assertTrue(database.sourceRead()),
            () -> assertEquals(Cleanup.COMPLETE, result.cleanup()),
            () -> assertEquals(Code.CANCELLED, assertInstanceOf(Refused.class, result).code()));
    }
    @ParameterizedTest @MethodSource("engines")
    void uncancelledCompleteCleanupStillPublishesExactSource(Engine engine) {
        var database = new ReadOperationPolicyTest.Database(engine);
        var credentials = credentials();
        var adapter = new JdbcObservation(database.destination(), ignored -> database.open(), () -> {},
            TimeUnit.SECONDS.toNanos(5), TimeUnit.SECONDS.toNanos(1));
        var result = assertInstanceOf(Complete.class, adapter.observe(
            ReadOperationPolicyTest.selection(ReadOperationPolicyTest.binding(engine)), credentials, new ObservationPort.Cancellation()));
        assertEquals(ReadOperationPolicyTest.XML, result.observation().documents().getFirst().xml());
        assertEquals(Cleanup.COMPLETE, result.cleanup());
        assertTrue(credentials.closed());
        assertEquals(1, database.opens); assertEquals(1, database.rollbacks); assertEquals(1, database.closes);
    }
    @ParameterizedTest @MethodSource("engines")
    void v3CancellationAfterRealCloseCannotPublishItsSeparateFingerprint(Engine engine) throws Exception {
        var database = new V3ObservationTest.Database(engine);
        var cancellation = new ObservationPort.Cancellation();
        var credentials = credentials();
        var adapter = new JdbcObservation(database.destination(), ignored -> {
            Connection original = database.open();
            return ReadOperationPolicyTest.proxy(Connection.class, (proxy, method, args) -> {
                Object result;
                try { result = method.invoke(original, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
                if (method.getName().equals("close")) cancellation.cancel();
                return result;
            });
        }, () -> {}, TimeUnit.SECONDS.toNanos(5), TimeUnit.SECONDS.toNanos(1));
        var result = assertInstanceOf(Refused.class, adapter.observeV3(V3ObservationTest.selection(engine), credentials, cancellation));
        assertEquals(Code.CANCELLED, result.code()); assertEquals(Cleanup.COMPLETE, result.cleanup());
        assertTrue(credentials.closed());
        assertEquals(1, database.opens); assertEquals(1, database.rollbacks); assertEquals(1, database.closes);
    }
    @ParameterizedTest @MethodSource("engines")
    void cancelledHeldCloseRetainsExactlyOneSlotUntilOriginalCleanupCompletes(Engine engine) throws Exception {
        var database = new ReadOperationPolicyTest.Database(engine);
        var cancellation = new ObservationPort.Cancellation();
        var credentials = credentials();
        var closing = new CountDownLatch(1); var release = new CountDownLatch(1);
        var adapter = new JdbcObservation(database.destination(), ignored -> {
            Connection original = database.open();
            return ReadOperationPolicyTest.proxy(Connection.class, (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                    cancellation.cancel(); closing.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("mock-close-barrier-expired");
                }
                try { return method.invoke(original, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        }, () -> {}, TimeUnit.SECONDS.toNanos(5), TimeUnit.MILLISECONDS.toNanos(80));
        var selection = ReadOperationPolicyTest.selection(ReadOperationPolicyTest.binding(engine));
        var permits = new ArrayList<ObservationPort.Permit>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                for (int i = 0; i < 4; i++) permits.add(assertInstanceOf(ObservationPort.Reservation.Admitted.class, adapter.reserve(selection)).permit());
                var consumed = permits.getLast();
                var pending = executor.submit(() -> consumed.observe(credentials, cancellation));
                assertTrue(closing.await(2, TimeUnit.SECONDS));
                var result = assertInstanceOf(Refused.class, pending.get(2, TimeUnit.SECONDS));
                assertEquals(Cleanup.INCONCLUSIVE, result.cleanup());
                var handle = result.cleanupHandle().orElseThrow();
                assertTrue(credentials.closed()); assertEquals(0, database.closes);
                consumed.close(); consumed.close(); handle.cancel(); handle.cancel();
                assertEquals(Cleanup.INCONCLUSIVE, handle.retry());
                assertEquals(new ObservationPort.Reservation.Refused(Code.CAPACITY), adapter.reserve(selection));
                release.countDown();
                awaitCleanup(handle);
                assertEquals(1, database.closes);
                var recovered = assertInstanceOf(ObservationPort.Reservation.Admitted.class, adapter.reserve(selection)).permit();
                permits.add(recovered);
                handle.cancel(); assertEquals(Cleanup.COMPLETE, handle.retry()); consumed.close();
                assertEquals(new ObservationPort.Reservation.Refused(Code.CAPACITY), adapter.reserve(selection));
                assertEquals(1, database.opens); assertEquals(1, database.rollbacks);
            } finally { release.countDown(); permits.forEach(ObservationPort.Permit::close); }
        }
    }
    @ParameterizedTest @MethodSource("engines")
    void originalSelectedDeadlineIsNotRenamedByItsOwnCancellation(Engine engine) throws Exception {
        var database = new ReadOperationPolicyTest.Database(engine);
        var cancellation = new ObservationPort.Cancellation();
        var credentials = credentials();
        var closing = new CountDownLatch(1);
        var adapter = new JdbcObservation(database.destination(), ignored -> {
            Connection original = database.open();
            return ReadOperationPolicyTest.proxy(Connection.class, (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                    closing.countDown();
                    long bound = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                    // Only the adapter's original deadline path can cancel this operation.
                    while (!cancellation.cancelled() && System.nanoTime() < bound) Thread.sleep(1);
                    if (!cancellation.cancelled()) throw new IllegalStateException("mock-deadline-not-observed");
                }
                try { return method.invoke(original, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        }, () -> {}, TimeUnit.MILLISECONDS.toNanos(300), TimeUnit.SECONDS.toNanos(2));
        var result = assertInstanceOf(Refused.class, adapter.observe(
            ReadOperationPolicyTest.selection(ReadOperationPolicyTest.binding(engine)), credentials, cancellation));
        assertEquals(0, closing.getCount()); assertTrue(database.sourceRead());
        assertTrue(cancellation.cancelled()); assertTrue(credentials.closed());
        assertEquals(Code.DEADLINE_EXCEEDED, result.code()); assertEquals(Cleanup.COMPLETE, result.cleanup());
        assertEquals(1, database.opens); assertEquals(1, database.rollbacks); assertEquals(1, database.closes);
    }
    private static void awaitCleanup(CleanupHandle handle) throws InterruptedException {
        long bound = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (handle.status() != Cleanup.COMPLETE && System.nanoTime() < bound) Thread.sleep(5);
        assertEquals(Cleanup.COMPLETE, handle.status());
    }

    @Test void failedRollbackAndCloseNeverPromoteCleanupOrReleaseQuarantines() throws Exception {
        var process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            ObservationCleanupFailureProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Isolated cleanup control must finish");
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("FOUR_CANCELLED_CLEANUP_REFUSALS_RETAINED"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

}
