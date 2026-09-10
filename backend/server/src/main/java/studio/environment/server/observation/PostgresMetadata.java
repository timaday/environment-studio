package studio.environment.server.observation;

import java.sql.SQLException;
import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.ObservationResult.Code;

final class PostgresMetadata {
    record Verified(Map<String, String> identity, String version, String encoding) { }
    static Verified verify(SqlRead sql, Binding binding) throws SQLException {
        if (!"180006".equals(sql.scalar(ReadQuery.PG_SERVER_VERSION))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        if (!"UTF8".equals(sql.scalar(ReadQuery.PG_SERVER_ENCODING)) || !"UTF8".equals(sql.scalar(ReadQuery.PG_CLIENT_ENCODING))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        if (!"repeatable read".equals(sql.scalar(ReadQuery.PG_ISOLATION)) || !"on".equals(sql.scalar(ReadQuery.PG_READ_ONLY)) || !"off".equals(sql.scalar(ReadQuery.PG_ROW_SECURITY))) throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
        var identity = sql.rows(ReadQuery.PG_IDENTITY);
        if (identity.size() != 1) throw new ObservationFailure(Code.METADATA_UNAVAILABLE);
        var id = identity.getFirst();
        String relation = SqlRead.quoted(binding.schema()) + "." + SqlRead.quoted(binding.table());
        var tables = sql.rows(ReadQuery.PG_TABLE, binding.schema(), binding.table());
        if (tables.size() != 1) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        var table = tables.getFirst();
        if (!table.subList(1, 5).equals(List.of("r", "p", "false", "false"))) throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
        long oid = Long.parseLong(table.getFirst());
        sql.empty(ReadQuery.PG_INHERITANCE, Code.STORAGE_UNSUPPORTED, oid, oid);
        sql.empty(ReadQuery.PG_POLICIES, Code.VISIBILITY_UNQUALIFIED, oid);
        sql.empty(ReadQuery.PG_TRIGGERS, Code.STORAGE_UNSUPPORTED, oid);
        sql.empty(ReadQuery.PG_ACCESS_METHOD, Code.STORAGE_UNSUPPORTED, oid);
        if (!"true".equals(sql.scalar(ReadQuery.PG_TABLE_ACCESS, relation))
                || !"true".equals(sql.scalar(ReadQuery.PG_SCHEMA_ACCESS, binding.schema()))) throw new ObservationFailure(Code.READ_ACCESS_DENIED);
        var columns = sql.rows(ReadQuery.PG_COLUMNS, oid, binding.keyColumn(), binding.xmlColumn());
        if (columns.size() != 2) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        for (var column : columns) {
            String expected = column.getFirst().equals(binding.keyColumn()) && binding.keyType() == KeyType.INT64 ? "int8" : "text";
            if (!column.get(1).equals(expected) || !column.get(2).equals("pg_catalog") || (column.getFirst().equals(binding.keyColumn()) && !column.get(3).equals("true")) || !column.get(4).isEmpty() || !column.get(5).isEmpty() || !column.get(6).equals("b") || !column.get(7).equals("-1")) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        }
        String uniqueKeys = sql.scalar(ReadQuery.PG_UNIQUE_KEY, oid, binding.keyColumn());
        if (uniqueKeys == null || !uniqueKeys.matches("[1-9][0-9]{0,18}")
                || new java.math.BigInteger(uniqueKeys).compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0)
            throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        return new Verified(Map.of("systemIdentifier", id.get(0), "databaseOid", id.get(1), "databaseName", id.get(2)), "18.6", "UTF8");
    }

}
