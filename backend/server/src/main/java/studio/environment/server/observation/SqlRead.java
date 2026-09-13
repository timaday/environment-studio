package studio.environment.server.observation;

import java.sql.*;
import java.util.*;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.ObservationResult.Code;

final class SqlRead {
    final Connection connection;
    final Cancellation cancellation;
    final long deadline;
    volatile Statement active;
    private enum Phase { NEW, SETUP, OBSERVATION, REFUSED }
    enum Source { COUNT, LENGTHS, DOCUMENTS }
    private enum Control {
        PG_BEGIN("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY"),
        PG_SEARCH_PATH("SET LOCAL search_path=pg_catalog"), PG_ROW_SECURITY("SET LOCAL row_security=off"),
        PG_STATEMENT_TIMEOUT("SET LOCAL statement_timeout='2000ms'"), PG_LOCK_TIMEOUT("SET LOCAL lock_timeout='2000ms'"),
        ORACLE_BEGIN("SET TRANSACTION READ ONLY");
        final String text;
        Control(String text) { this.text=text; }
    }
    private Phase phase=Phase.NEW;
    private Binding binding;
    void begin(Binding selected) throws SQLException {
        if (phase!=Phase.NEW) { phase=Phase.REFUSED; throw new ObservationFailure(Code.INVALID_SELECTION); }
        phase=Phase.SETUP;
        try {
            Objects.requireNonNull(selected);
            quoted(selected.schema()); quoted(selected.table()); quoted(selected.keyColumn()); quoted(selected.xmlColumn());
            binding=selected;
            connection.setAutoCommit(false);
            if (connection.getAutoCommit()) throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
            if (binding.engine()==Engine.POSTGRESQL) {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                control(Control.PG_BEGIN);
                control(Control.PG_SEARCH_PATH); control(Control.PG_ROW_SECURITY);
                control(Control.PG_STATEMENT_TIMEOUT); control(Control.PG_LOCK_TIMEOUT);
                verifySnapshot();
                lock();
            } else control(Control.ORACLE_BEGIN);
            phase=Phase.OBSERVATION;
        } catch (SQLException | RuntimeException failure) { phase=Phase.REFUSED; throw failure; }
    }
    void verifySnapshot() throws SQLException {
        if (phase!=Phase.SETUP && phase!=Phase.OBSERVATION) throw new ObservationFailure(Code.INVALID_SELECTION);
        if (binding.engine()==Engine.POSTGRESQL && (!"repeatable read".equals(rawScalar("SHOW transaction_isolation"))
                || !"on".equals(rawScalar("SHOW transaction_read_only")) || !"pg_catalog".equals(rawScalar("SHOW search_path"))
                || !"off".equals(rawScalar("SHOW row_security")))) {
            phase=Phase.REFUSED; throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
        }
    }
    private void observing() { if(phase!=Phase.OBSERVATION) throw new ObservationFailure(Code.INVALID_SELECTION); }
    private String source(Source source) {
        observing(); Objects.requireNonNull(source);
        String table=quoted(binding.schema())+"."+quoted(binding.table());
        String key=quoted(binding.keyColumn()), xml=quoted(binding.xmlColumn());
        String length=binding.engine()==Engine.POSTGRESQL ? "pg_catalog.char_length("+xml+")" : "SYS.DBMS_LOB.GETLENGTH("+xml+")";
        String bytes=binding.engine()==Engine.POSTGRESQL ? "pg_catalog.octet_length("+xml+")" : length;
        String boundedKey=key;
        if(binding.keyType()==KeyType.TEXT) {
            String keyLength=binding.engine()==Engine.POSTGRESQL ? "pg_catalog.char_length("+key+")" : "LENGTHC("+key+")";
            boundedKey="CASE WHEN "+keyLength+" BETWEEN 1 AND 256 THEN "+key+" ELSE NULL END";
        }
        return switch(source) {
            case COUNT -> "SELECT COUNT(*) FROM "+table;
            case LENGTHS -> "SELECT "+boundedKey+","+length+","+bytes+" FROM "+table;
            case DOCUMENTS -> "SELECT "+key+","+xml+" FROM "+table;
        };
    }
    PreparedStatement sourceStatement(Source source) throws SQLException { return rawStatement(source(source)); }
    List<List<String>> sourceRows(Source source) throws SQLException { return rawRows(source(source)); }
    String sourceScalar(Source source) throws SQLException { return rawScalar(source(source)); }
    PreparedStatement statement(ReadQuery query, Object... values) throws SQLException {
        observing(); Objects.requireNonNull(query);
        if(query.engine!=binding.engine()) throw new ObservationFailure(Code.INVALID_SELECTION);
        return rawStatement(query.text,values);
    }
    SqlRead(Connection connection, Cancellation cancellation, long deadline) { this.connection = connection; this.cancellation = cancellation; this.deadline = deadline; }
    void check() {
        if (cancellation.cancelled()) throw new ObservationFailure(Code.CANCELLED);
        if (System.nanoTime() >= deadline) throw new ObservationFailure(Code.DEADLINE_EXCEEDED);
    }
    private PreparedStatement rawStatement(String sql, Object... values) throws SQLException {
        check();
        var statement = connection.prepareStatement(sql);
        try {
            statement.setFetchSize(1); statement.setQueryTimeout(2);
            if (statement.getFetchSize() != 1) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            if (statement.isWrapperFor(oracle.jdbc.OracleStatement.class)) {
                var oracle = statement.unwrap(oracle.jdbc.OracleStatement.class);
                oracle.setLobPrefetchSize(0);
                if (oracle.getLobPrefetchSize() != 0) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            }
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            active = statement;
            return statement;
        } catch (SQLException | RuntimeException failure) {
            try { statement.close(); } catch (SQLException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private List<List<String>> rawRows(String sql, Object... values) throws SQLException {
        try (var statement = rawStatement(sql, values); var rows = statement.executeQuery()) {
            var result = new ArrayList<List<String>>();
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                check(); if (result.size() >= 4096 || columns > 20) throw new ObservationFailure(Code.RESOURCE_LIMIT);
                var row = new ArrayList<String>();
                for (int index = 1; index <= columns; index++) {
                    String value = rows.getString(index);
                    if (value != null && value.length() > 4096) throw new ObservationFailure(Code.RESOURCE_LIMIT);
                    row.add(value);
                }
                result.add(Collections.unmodifiableList(row));
            }
            return List.copyOf(result);
        } finally { active = null; }
    }
    private String rawScalar(String sql, Object... values) throws SQLException {
        var rows = rawRows(sql, values);
        if (rows.size() != 1 || rows.getFirst().size() != 1 || rows.getFirst().getFirst() == null) throw new ObservationFailure(Code.METADATA_UNAVAILABLE);
        return rows.getFirst().getFirst();
    }
    List<List<String>> rows(ReadQuery query, Object... values) throws SQLException {
        observing(); Objects.requireNonNull(query);
        if(query.engine!=binding.engine()) throw new ObservationFailure(Code.INVALID_SELECTION);
        return rawRows(query.text,values);
    }
    String scalar(ReadQuery query, Object... values) throws SQLException {
        var rows=rows(query,values);
        if(rows.size()!=1 || rows.getFirst().size()!=1 || rows.getFirst().getFirst()==null) throw new ObservationFailure(Code.METADATA_UNAVAILABLE);
        return rows.getFirst().getFirst();
    }
    void empty(ReadQuery query, Code code, Object... values) throws SQLException { if (!rows(query, values).isEmpty()) throw new ObservationFailure(code); }
    private void control(Control control) throws SQLException { try (var statement = rawStatement(control.text)) { statement.execute(); } finally { active = null; } }
    private void lock() throws SQLException {
        try (var statement=rawStatement("LOCK TABLE "+quoted(binding.schema())+"."+quoted(binding.table())+" IN ACCESS SHARE MODE")) { statement.execute(); }
        finally { active=null; }
    }
    static String quoted(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) throw new ObservationFailure(Code.INVALID_SELECTION);
        return '"' + identifier + '"';
    }
}
