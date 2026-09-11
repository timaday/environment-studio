package studio.environment.server.observation;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import studio.environment.core.definitionv2.NativeDefinition.Binding;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.definitionv2.NativeDefinition.KeyType;
import studio.environment.core.observation.*;
import studio.environment.core.observation.ObservationResult.*;
import studio.environment.server.xml.*;

/** No pool, reconnect or retry. Four operations/quarantines maximum across adapter instances. */
public final class JdbcObservation implements ObservationPort {
    private static final Semaphore CAPACITY = new Semaphore(4);
    private static final long OPERATION_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long CLEANUP_NANOS = TimeUnit.SECONDS.toNanos(5);
    @FunctionalInterface interface ConnectionFactory { Connection open(TransientCredentials credentials) throws SQLException; }
    private final ObservationDestination destination;
    private final ConnectionFactory connections;
    private final Runnable beforeSources;
    private final long operationNanos;
    private final long cleanupNanos;
    public JdbcObservation(ObservationDestination destination) {
        this.destination = Objects.requireNonNull(destination); this.connections = this::connect;
        this.beforeSources = () -> { }; this.operationNanos = OPERATION_NANOS; this.cleanupNanos = CLEANUP_NANOS;
    }
    JdbcObservation(ObservationDestination destination, ConnectionFactory connections, Runnable beforeSources, long operationNanos, long cleanupNanos) {
        this.destination = Objects.requireNonNull(destination); this.connections = Objects.requireNonNull(connections);
        this.beforeSources = Objects.requireNonNull(beforeSources); this.operationNanos = operationNanos; this.cleanupNanos = cleanupNanos;
    }
    JdbcObservation(ObservationDestination destination, Runnable beforeSources) {
        this.destination = Objects.requireNonNull(destination); this.connections = this::connect;
        this.beforeSources = Objects.requireNonNull(beforeSources); this.operationNanos = OPERATION_NANOS; this.cleanupNanos = CLEANUP_NANOS;
    }
    @Override public Reservation reserve(Selection selection) {
        return reserveRead(() -> select(selection));
    }
    @Override public Reservation reserveV3(V3Selection selection) {
        return reserveRead(() -> selectV3(selection));
    }
    private Reservation reserveRead(Supplier<ReadSelection> selection) {
        if (!CAPACITY.tryAcquire()) return new Reservation.Refused(Code.CAPACITY);
        return new Reservation.Admitted(new Permit() {
            private final AtomicBoolean consumed = new AtomicBoolean();
            @Override public ObservationResult observe(TransientCredentials credentials, Cancellation cancellation) {
                Objects.requireNonNull(credentials);
                if (!consumed.compareAndSet(false, true)) {
                    credentials.close(); return new Refused(Code.INVALID_SELECTION, Cleanup.COMPLETE);
                }
                return observeReserved(selection, credentials, cancellation);
            }
            @Override public void close() { if (consumed.compareAndSet(false, true)) CAPACITY.release(); }
        });
    }
    @Override public ObservationResult observe(Selection selection, TransientCredentials credentials, Cancellation cancellation) {
        Objects.requireNonNull(credentials); Objects.requireNonNull(cancellation);
        var reservation = reserve(selection);
        if (reservation instanceof Reservation.Refused refused) {
            credentials.close(); return new Refused(refused.code(), Cleanup.COMPLETE);
        }
        try (var permit = ((Reservation.Admitted)reservation).permit()) { return permit.observe(credentials, cancellation); }
    }
    @Override public ObservationResult observeV3(V3Selection selection, TransientCredentials credentials, Cancellation cancellation) {
        Objects.requireNonNull(credentials);
        var reservation = reserveV3(selection);
        if (reservation instanceof Reservation.Refused refused) {
            credentials.close(); return new Refused(refused.code(), Cleanup.COMPLETE);
        }
        try (var permit = ((Reservation.Admitted)reservation).permit()) { return permit.observe(credentials, cancellation); }
    }
    private record ReadSelection(Binding binding, String logicalDigest, String bindingDigest, String version) { }
    private static ReadSelection select(Selection selection) {
        if (selection == null || selection.compiled() == null || selection.bindingId() == null) throw new ObservationFailure(Code.INVALID_SELECTION);
        var checked = selection.compiled().checked();
        var binding = checked.definition().bindings().stream().filter(b -> b.id().equals(selection.bindingId())).findFirst().orElseThrow(() -> new ObservationFailure(Code.INVALID_SELECTION));
        return new ReadSelection(binding, checked.logicalDigest(), checked.bindingDigests().get(binding.id()), "2");
    }
    private static ReadSelection selectV3(V3Selection selection) {
        if (selection == null || selection.compiled() == null || selection.bindingId() == null) throw new ObservationFailure(Code.INVALID_SELECTION);
        var checked = selection.compiled();
        studio.environment.core.definitionv3.NativeCompilationResult result;
        try { result = new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(checked.definition()); }
        catch (RuntimeException invalid) { throw new ObservationFailure(Code.INVALID_SELECTION); }
        if (!result.isCompatibleWith(checked)) throw new ObservationFailure(Code.INVALID_SELECTION);
        var binding = checked.definition().bindings().stream().filter(b -> b.id().equals(selection.bindingId())).findFirst().orElseThrow(() -> new ObservationFailure(Code.INVALID_SELECTION));
        return new ReadSelection(binding, checked.logicalDigest(), checked.bindingDigests().get(binding.id()), "3");
    }
    private ObservationResult observeReserved(Supplier<ReadSelection> selected, TransientCredentials credentials, Cancellation cancellation) {
        if (cancellation == null) { credentials.close(); CAPACITY.release(); return new Refused(Code.INVALID_SELECTION, Cleanup.COMPLETE); }
        long deadline = System.nanoTime() + operationNanos;
        Binding binding; ReadSelection selection;
        try {
            selection = selected.get(); binding = selection.binding();
            if (binding.engine() != destination.engine() || binding.documents().isEmpty() || binding.documents().size() > 128) throw new ObservationFailure(Code.INVALID_SELECTION);
            String expectedPolicy = binding.engine() == Engine.POSTGRESQL ? "postgresql-read-operation-v1" : "oracle-read-operation-v1";
            if (!expectedPolicy.equals(destination.operationPolicyVersion())) throw new ObservationFailure(Code.DESTINATION_UNQUALIFIED);
            if (binding.engine() == Engine.POSTGRESQL && (binding.schema().startsWith("pg_") || binding.schema().equals("information_schema"))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            SqlRead.quoted(binding.schema()); SqlRead.quoted(binding.table()); SqlRead.quoted(binding.keyColumn()); SqlRead.quoted(binding.xmlColumn());
            if (cancellation.cancelled()) throw new ObservationFailure(Code.CANCELLED);
            if (DriverLoggingPolicy.verboseDriverLogging()) throw new ObservationFailure(Code.DESTINATION_UNQUALIFIED);
            if (System.nanoTime() >= deadline) throw new ObservationFailure(Code.DEADLINE_EXCEEDED);
        } catch (ObservationFailure refused) { credentials.close(); CAPACITY.release(); return new Refused(refused.code(), Cleanup.COMPLETE); }
        var work = new Work(selection, binding, credentials, cancellation, deadline);
        work.thread = new Thread(work, "studio-read-operation");
        work.thread.setDaemon(true);
        work.thread.start();
        long cleanupDeadline = Long.MAX_VALUE;
        Code terminal = null;
        while (true) {
            if (work.finished()) return work.result(terminal);
            long now = System.nanoTime();
            if (terminal == null && (cancellation.cancelled() || now >= work.deadline)) {
                terminal = cancellation.cancelled() ? Code.CANCELLED : Code.DEADLINE_EXCEEDED;
                cleanupDeadline = now + cleanupNanos;
                work.cancel();
            }
            if (work.cleanupStarted != Long.MAX_VALUE && now - work.cleanupStarted >= cleanupNanos) {
                work.cancel(); return new Refused(Code.CLEANUP_INCONCLUSIVE, Cleanup.INCONCLUSIVE, Optional.of(work));
            }
            if (now >= cleanupDeadline) return new Refused(terminal, Cleanup.INCONCLUSIVE, Optional.of(work));
            try { work.done.await(20, TimeUnit.MILLISECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); work.cancel(); return new Refused(Code.CANCELLED, Cleanup.INCONCLUSIVE, Optional.of(work)); }
        }
    }
    private final class Work implements Runnable, CleanupHandle {
        final ReadSelection selection;
        final Binding binding;
        final TransientCredentials credentials;
        final Cancellation cancellation;
        final long deadline;
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicBoolean cancelStarted = new AtomicBoolean();
        final AtomicBoolean released = new AtomicBoolean();
        volatile boolean cancelFinished = true;
        volatile boolean cancelConfirmed = true;
        volatile boolean cleanupConfirmed;
        volatile long cleanupStarted = Long.MAX_VALUE;
        volatile Thread thread;
        volatile SqlRead sql;
        volatile Connection connection;
        volatile Observation observation;
        volatile Code failure;
        Work(ReadSelection selection, Binding binding, TransientCredentials credentials, Cancellation cancellation, long deadline) {
            this.selection = selection; this.binding = binding; this.credentials = credentials; this.cancellation = cancellation;
            this.deadline = deadline;
        }
        @Override public void run() {
            boolean attempted = false;
            try {
                if (cancellation.cancelled()) throw new ObservationFailure(Code.CANCELLED);
                attempted = true;
                connection = connections.open(credentials);
                credentials.close();
                sql = new SqlRead(connection, cancellation, deadline);
                sql.begin(binding);
                Map<String,String> identity;
                String version, encoding;
                try {
                    if (binding.engine() == Engine.POSTGRESQL) {
                        var metadata = PostgresMetadata.verify(sql, binding); identity = metadata.identity(); version = metadata.version(); encoding = metadata.encoding();
                    } else {
                        var metadata = OracleMetadata.verify(sql, binding); identity = metadata.identity(); version = metadata.version(); encoding = metadata.encoding();
                    }
                } catch (SQLException denied) { throw new ObservationFailure(Code.METADATA_UNAVAILABLE); }
                if (!destination.expectedPhysicalIdentity().equals(identity)) throw new ObservationFailure(Code.DESTINATION_MISMATCH);
                String driver = connection.getMetaData().getDriverVersion();
                if (!(binding.engine() == Engine.POSTGRESQL ? "42.7.13" : "23.26.3.0.0").equals(driver)) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
                beforeSources.run();
                sql.verifySnapshot();
                var documents = readDocuments(sql, binding);
                sql.check();
                observation = observation(selection, binding, documents, identity, version, driver, encoding);
                sql.check();
            } catch (ObservationFailure refused) { failure = refused.code(); }
            catch (SQLException | IOException | RuntimeException failed) { failure = cancellation.cancelled() ? Code.CANCELLED : Code.DATABASE_FAILURE; }
            finally {
                cleanupStarted = System.nanoTime();
                boolean clean = !attempted;
                if (connection != null) {
                    clean = true;
                    try { connection.rollback(); } catch (SQLException | RuntimeException failed) { clean = false; }
                    try { connection.close(); } catch (SQLException | RuntimeException failed) { clean = false; }
                    if (!clean) try { connection.abort(Runnable::run); } catch (SQLException | RuntimeException failed) { clean = false; }
                }
                credentials.close();
                cleanupConfirmed = clean;
                done.countDown(); releaseIfConfirmed();
            }
        }
        boolean finished() { return done.getCount() == 0 && cancelFinished; }
        private void releaseIfConfirmed() { if (finished() && cleanupConfirmed && cancelConfirmed && released.compareAndSet(false, true)) CAPACITY.release(); }
        ObservationResult result(Code terminal) {
            releaseIfConfirmed();
            Cleanup cleanup = status();
            if (terminal == null && cancellation.cancelled()) terminal = Code.CANCELLED;
            if (cleanup != Cleanup.COMPLETE) return new Refused(terminal == null ? Code.CLEANUP_INCONCLUSIVE : terminal, Cleanup.INCONCLUSIVE, Optional.of(this));
            if (terminal == null && System.nanoTime() >= deadline) terminal = Code.DEADLINE_EXCEEDED;
            if (terminal != null || failure != null) return new Refused(terminal == null ? failure : terminal, Cleanup.COMPLETE);
            return new Complete(Objects.requireNonNull(observation));
        }
        @Override public Cleanup status() { releaseIfConfirmed(); return finished() && cleanupConfirmed && cancelConfirmed ? Cleanup.COMPLETE : Cleanup.INCONCLUSIVE; }
        @Override public Cleanup retry() { return status(); } // No reconnect or repeated JDBC work; original cleanup retains ownership.
        @Override public void cancel() {
            cancellation.cancel();
            if (finished()) return;
            if (!cancelStarted.compareAndSet(false, true)) return;
            cancelFinished = false;
            var cancelThread = new Thread(() -> {
                try { var current = sql; var statement = current == null ? null : current.active; if (statement != null) statement.cancel(); }
                catch (SQLException | RuntimeException failed) { cancelConfirmed = false; }
                finally { cancelFinished = true; releaseIfConfirmed(); }
            }, "studio-read-cancel");
            cancelThread.setDaemon(true); cancelThread.start();
        }
        @Override public String toString() { return "CleanupHandle[REDACTED]"; }
    }
    private Connection connect(TransientCredentials credentials) throws SQLException {
        var properties = new Properties();
        char[] user = credentials.copyUser(), password = credentials.copyPassword();
        try {
            properties.setProperty("user", new String(user)); properties.setProperty("password", new String(password));
            if (destination.engine() == Engine.POSTGRESQL) {
                properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "2"); properties.setProperty("cancelSignalTimeout", "2");
                properties.setProperty("defaultRowFetchSize", "1"); properties.setProperty("autosave", "never"); properties.setProperty("reWriteBatchedInserts", "false");
                properties.setProperty("ApplicationName", "environment-studio-observation");
                properties.setProperty("sslmode", destination.transport() == ObservationDestination.Transport.VERIFIED_TLS ? "verify-full" : "disable");
                if (destination.transport() == ObservationDestination.Transport.VERIFIED_TLS) properties.setProperty("sslrootcert", destination.trustMaterial());
                return new org.postgresql.Driver().connect("jdbc:postgresql://" + destination.host() + ":" + destination.port() + "/" + destination.database(), properties);
            }
            properties.setProperty(oracle.jdbc.OracleConnection.CONNECTION_PROPERTY_THIN_NET_CONNECT_TIMEOUT, "5000");
            properties.setProperty(oracle.jdbc.OracleConnection.CONNECTION_PROPERTY_THIN_OUTBOUND_CONNECT_TIMEOUT, "5000");
            properties.setProperty(oracle.jdbc.OracleConnection.CONNECTION_PROPERTY_THIN_READ_TIMEOUT, "2000");
            properties.setProperty("oracle.jdbc.defaultLobPrefetchSize", "0"); properties.setProperty("oracle.jdbc.retainLobPrefetchData", "false"); properties.setProperty("defaultRowPrefetch", "1");
            properties.setProperty("oracle.jdbc.fanEnabled", "false");
            if (destination.transport() == ObservationDestination.Transport.VERIFIED_TLS) {
                properties.setProperty(oracle.jdbc.OracleConnection.CONNECTION_PROPERTY_THIN_SSL_SERVER_DN_MATCH, "true");
                properties.setProperty(oracle.jdbc.OracleConnection.CONNECTION_PROPERTY_THIN_JAVAX_NET_SSL_TRUSTSTORE, destination.trustMaterial());
            }
            String protocol = destination.transport() == ObservationDestination.Transport.VERIFIED_TLS ? "tcps" : "tcp";
            String url = "jdbc:oracle:thin:@(DESCRIPTION=(RETRY_COUNT=0)(ADDRESS=(PROTOCOL=" + protocol + ")(HOST=" + destination.host() + ")(PORT=" + destination.port() + "))(CONNECT_DATA=(SERVICE_NAME=" + destination.database() + ")))";
            return new oracle.jdbc.OracleDriver().connect(url, properties);
        } finally { Arrays.fill(user, '\0'); Arrays.fill(password, '\0'); properties.clear(); }
    }
    private static List<Document> readDocuments(SqlRead sql, Binding binding) throws SQLException, IOException {
        if (!Integer.toString(binding.documents().size()).equals(sql.sourceScalar(SqlRead.Source.COUNT))) throw new ObservationFailure(Code.INVENTORY_MISMATCH);
        var lengths = sql.sourceRows(SqlRead.Source.LENGTHS);
        if (lengths.size() != binding.documents().size() || lengths.size() > 128) throw new ObservationFailure(Code.INVENTORY_MISMATCH);
        long totalLowerBound = 0;
        for (var row : lengths) {
            if (row.get(0) == null) throw new ObservationFailure(Code.INVENTORY_MISMATCH);
            if (row.get(1) == null) throw new ObservationFailure(Code.NULL_SOURCE);
            long characters = Long.parseLong(row.get(1)), byteBound = Long.parseLong(row.get(2));
            if (characters == 0) throw new ObservationFailure(Code.EMPTY_SOURCE);
            if (characters > 1_048_576 || byteBound > 4L * 1_048_576) throw new ObservationFailure(Code.RESOURCE_LIMIT);
            totalLowerBound += byteBound;
        }
        if (totalLowerBound > 16L * 1_048_576) throw new ObservationFailure(Code.RESOURCE_LIMIT);
        var ids = new HashMap<String,String>();
        for (var document : binding.documents()) if (ids.put(document.key(), document.id()) != null) throw new ObservationFailure(Code.INVALID_SELECTION);
        var result = new ArrayList<Document>();
        long totalBytes = 0;
        try (var statement = sql.sourceStatement(SqlRead.Source.DOCUMENTS); var rows = statement.executeQuery()) {
            while (rows.next()) {
                sql.check(); if (result.size() >= 128) throw new ObservationFailure(Code.RESOURCE_LIMIT);
                String key;
                if (binding.keyType() == KeyType.INT64) {
                    var number = rows.getBigDecimal(1);
                    if (number == null) throw new ObservationFailure(Code.INVENTORY_MISMATCH);
                    try { key = Long.toString(number.longValueExact()); } catch (ArithmeticException invalid) { throw new ObservationFailure(Code.INVENTORY_MISMATCH); }
                } else {
                    key = rows.getString(1);
                    if (key == null || key.isEmpty() || key.codePointCount(0,key.length()) > 256) throw new ObservationFailure(Code.INVENTORY_MISMATCH);
                    ObservationFingerprint.utf8(key);
                }
                String id = ids.remove(key);
                if (id == null) throw new ObservationFailure(Code.INVENTORY_MISMATCH);
                String source;
                if (binding.engine() == Engine.ORACLE) {
                    Clob lob = rows.getClob(2);
                    if (lob == null) throw new ObservationFailure(Code.NULL_SOURCE);
                    try { try (Reader stream = lob.getCharacterStream()) { source = stream(sql, stream, 16L * 1_048_576 - totalBytes); } }
                    finally { lob.free(); }
                } else { try (Reader stream = rows.getCharacterStream(2)) { source = stream(sql, stream, 16L * 1_048_576 - totalBytes); } }
                if (source.isEmpty()) throw new ObservationFailure(Code.EMPTY_SOURCE);
                long count = ObservationFingerprint.utf8(source).length;
                totalBytes += count;
                if (totalBytes > 16L * 1_048_576) throw new ObservationFailure(Code.RESOURCE_LIMIT);
                if (new LosslessXmlAdapter().project(source) instanceof XmlResult.Rejected) throw new ObservationFailure(Code.INVALID_SOURCE);
                result.add(new Document(id, new Key(binding.keyType().name().toLowerCase(Locale.ROOT), key), source, count, source.length(), ObservationFingerprint.source(source)));
            }
        } finally { sql.active = null; }
        result.sort(Comparator.comparing(Document::documentId));
        if (!ObservationInventory.complete(binding, result)) throw new ObservationFailure(Code.INVENTORY_MISMATCH);
        return List.copyOf(result);
    }
    static String stream(SqlRead sql, Reader reader, long remainingBytes) throws IOException {
        if (reader == null) throw new ObservationFailure(Code.NULL_SOURCE);
        var text = new StringBuilder(); char[] chunk = new char[4096];
        long bytes = 0; char high = 0;
        for (int count; (count = reader.read(chunk)) != -1;) {
            sql.check();
            if (text.length() + count > 1_048_576) throw new ObservationFailure(Code.RESOURCE_LIMIT);
            for (int index=0; index<count; index++) {
                char character = chunk[index];
                if (high != 0) { if (!Character.isLowSurrogate(character)) throw new ObservationFailure(Code.INVALID_SOURCE); bytes += 4; high = 0; }
                else if (Character.isHighSurrogate(character)) high = character;
                else if (Character.isLowSurrogate(character)) throw new ObservationFailure(Code.INVALID_SOURCE);
                else bytes += character < 128 ? 1 : character < 2048 ? 2 : 3;
                if (bytes > remainingBytes) throw new ObservationFailure(Code.RESOURCE_LIMIT);
            }
            text.append(chunk, 0, count);
        }
        if (high != 0) throw new ObservationFailure(Code.INVALID_SOURCE);
        return text.toString();
    }
    private Observation observation(ReadSelection selection, Binding binding, List<Document> documents, Map<String,String> identity, String version, String driver, String encoding) {
        Map<String,Object> endpoint = Map.of("id", destination.id(), "host", destination.host(), "port", destination.port(), "database", destination.database(), "transportIdentity", destination.transportIdentity(),
                "expectedPhysicalIdentity", destination.expectedPhysicalIdentity(), "observedPhysicalIdentity", identity, "provisioningPolicyVersion", destination.provisioningPolicyVersion());
        Map<String,Object> metadata = Map.of("adapterVersion", "jdbc-observation-v" + selection.version(), "operationPolicyVersion", destination.operationPolicyVersion(), "visibility", "complete", "readOnlyOperation", "verified", "snapshot", binding.engine() == Engine.POSTGRESQL ? "repeatable-read-read-only" : "read-only");
        var frame = new TreeMap<String,Object>();
        frame.put("logicalDigest",selection.logicalDigest()); frame.put("bindingDigest",selection.bindingDigest());
        frame.put("engine",binding.engine().name().toLowerCase(Locale.ROOT)); frame.put("engineVersion",version); frame.put("driverVersion",driver); frame.put("storage",binding.storage().name().toLowerCase(Locale.ROOT));
        frame.put("storageVersion",binding.engine() == Engine.POSTGRESQL ? "postgresql-text-v1" : "oracle-clob-v1"); frame.put("encoding",encoding); frame.put("destination",endpoint); frame.put("metadata",metadata); frame.put("cleanup","complete");
        frame.put("documents",documents.stream().map(d -> Map.of("documentId",d.documentId(),"key",Map.of("type",d.key().type(),"value",d.key().value()),"xml",d.xml(),"utf8Bytes",d.utf8Bytes(),"characters",d.characters(),"sourceDigest",d.sourceDigest())).toList());
        return new Observation(ObservationFingerprint.hash("ES-OBSERVATION-" + selection.version(), frame), selection.logicalDigest(), selection.bindingDigest(), documents, frame);
    }
}
