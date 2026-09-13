package studio.environment.server.observation;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.observation.ObservationPort;
import studio.environment.core.observation.ObservationResult.*;

/** Isolated JVM: four deliberately inconclusive JDBC doubles retain four original quarantines. */
public final class ObservationCleanupFailureProbe {
    private ObservationCleanupFailureProbe() { }
    public static void main(String[] arguments) {
        for (var engine : Engine.values()) for (String point : new String[] {"rollback", "close"}) {
            var database = new ReadOperationPolicyTest.Database(engine);
            var cancellation = new ObservationPort.Cancellation();
            var credentials = ObservationCancellationPublicationTest.credentials();
            var adapter = new JdbcObservation(database.destination(), ignored -> {
                Connection original = database.open();
                return ReadOperationPolicyTest.proxy(Connection.class, (proxy, method, args) -> {
                    Object result;
                    try { result = method.invoke(original, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                    if (method.getName().equals(point)) {
                        cancellation.cancel();
                        throw new SQLException("mock-cleanup-refusal");
                    }
                    return result;
                });
            }, () -> {}, TimeUnit.SECONDS.toNanos(5), TimeUnit.SECONDS.toNanos(1));
            var result = adapter.observe(ReadOperationPolicyTest.selection(ReadOperationPolicyTest.binding(engine)), credentials, cancellation);
            if (!(result instanceof Refused refused) || refused.code() != Code.CANCELLED || refused.cleanup() != Cleanup.INCONCLUSIVE)
                throw new AssertionError("Cancelled failed cleanup was promoted or lost its refusal");
            var handle = refused.cleanupHandle().orElseThrow();
            for (int i = 0; i < 3; i++) {
                handle.cancel();
                if (handle.status() != Cleanup.INCONCLUSIVE || handle.retry() != Cleanup.INCONCLUSIVE)
                    throw new AssertionError("Unproven cleanup became conclusive");
            }
            if (!credentials.closed() || database.opens != 1 || database.rollbacks != 1 || database.closes != 1)
                throw new AssertionError("Original credentials/connection cleanup was not preserved");
        }
        var database = new ReadOperationPolicyTest.Database(Engine.POSTGRESQL);
        var adapter = new JdbcObservation(database.destination(), ignored -> database.open(), () -> {},
            TimeUnit.SECONDS.toNanos(5), TimeUnit.SECONDS.toNanos(1));
        var selection = ReadOperationPolicyTest.selection(ReadOperationPolicyTest.PG);
        if (!new ObservationPort.Reservation.Refused(Code.CAPACITY).equals(adapter.reserve(selection)))
            throw new AssertionError("Inconclusive cleanup released original capacity");
        if (database.opens != 0) throw new AssertionError("Capacity refusal opened another connection");
        System.out.println("FOUR_CANCELLED_CLEANUP_REFUSALS_RETAINED");
    }
}
