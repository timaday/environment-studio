package studio.environment.server.observation;

import java.sql.*;
import java.util.*;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult.Code;

final class SqlRead {
    final Connection connection;
    final Cancellation cancellation;
    final long deadline;
    volatile Statement active;
    SqlRead(Connection connection, Cancellation cancellation, long deadline) { this.connection = connection; this.cancellation = cancellation; this.deadline = deadline; }
    void check() {
        if (cancellation.cancelled()) throw new ObservationFailure(Code.CANCELLED);
        if (System.nanoTime() >= deadline) throw new ObservationFailure(Code.DEADLINE_EXCEEDED);
    }
    PreparedStatement statement(String sql, Object... values) throws SQLException {
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

    List<List<String>> rows(String sql, Object... values) throws SQLException {
        try (var statement = statement(sql, values); var rows = statement.executeQuery()) {
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
    String scalar(String sql, Object... values) throws SQLException {
        var rows = rows(sql, values);
        if (rows.size() != 1 || rows.getFirst().size() != 1 || rows.getFirst().getFirst() == null) throw new ObservationFailure(Code.METADATA_UNAVAILABLE);
        return rows.getFirst().getFirst();
    }
    void empty(String sql, Code code, Object... values) throws SQLException { if (!rows(sql, values).isEmpty()) throw new ObservationFailure(code); }
    void execute(String sql) throws SQLException { try (var statement = statement(sql)) { statement.execute(); } finally { active = null; } }
    static String quoted(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) throw new ObservationFailure(Code.INVALID_SELECTION);
        return '"' + identifier + '"';
    }
}
