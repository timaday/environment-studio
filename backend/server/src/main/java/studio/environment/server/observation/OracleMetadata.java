package studio.environment.server.observation;

import java.sql.SQLException;
import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.ObservationResult.Code;

final class OracleMetadata {
    record Verified(Map<String, String> identity, String version, String encoding) { }
    static Verified verify(SqlRead sql, Binding binding) throws SQLException {
        if (!"23.26.3.0.0".equals(sql.scalar(ReadQuery.ORACLE_SERVER_VERSION))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        if (!"AL32UTF8".equals(sql.scalar(ReadQuery.ORACLE_ENCODING))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        sql.empty(ReadQuery.ORACLE_FGA, Code.VISIBILITY_UNQUALIFIED, binding.schema(), binding.table());
        var features = sql.rows(ReadQuery.ORACLE_FEATURES);
        if (features.size() != 2 || features.stream().anyMatch(row -> !"FALSE".equals(row.get(1)))) throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
        if (!"FALSE".equals(sql.scalar(ReadQuery.ORACLE_IS_DBA))) throw new ObservationFailure(Code.IDENTITY_UNSUPPORTED);
        sql.empty(ReadQuery.ORACLE_PROXY, Code.IDENTITY_UNSUPPORTED);
        if ("SYS".equals(sql.scalar(ReadQuery.ORACLE_SESSION_USER))) throw new ObservationFailure(Code.IDENTITY_UNSUPPORTED);
        var ids = sql.rows(ReadQuery.ORACLE_IDENTITY);
        if (ids.size() != 1) throw new ObservationFailure(Code.METADATA_UNAVAILABLE);
        var id = ids.getFirst();
        if (!sql.rows(ReadQuery.ORACLE_USER_KIND).equals(List.of(List.of("NO","N")))) throw new ObservationFailure(Code.IDENTITY_UNSUPPORTED);
        if (!"N".equals(sql.scalar(ReadQuery.ORACLE_SOURCE_OWNER, binding.schema()))) throw new ObservationFailure(Code.IDENTITY_UNSUPPORTED);
        var tables = sql.rows(ReadQuery.ORACLE_TABLE, binding.schema(), binding.table());
        if (tables.size() != 1) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        var table = tables.getFirst();
        if (!"NO".equals(table.get(0)) || table.get(1) != null || !"NO".equals(table.get(2)) || !"N".equals(table.get(3)) || !"N".equals(table.get(4)) || !"VALID".equals(table.get(5))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        sql.empty(ReadQuery.ORACLE_EXTERNAL_TABLE, Code.STORAGE_UNSUPPORTED, binding.schema(), binding.table());
        sql.empty(ReadQuery.ORACLE_SYNONYMS, Code.STORAGE_UNSUPPORTED, binding.schema(), binding.table());
        sql.empty(ReadQuery.ORACLE_TRIGGERS, Code.STORAGE_UNSUPPORTED, binding.schema(), binding.table());
        sql.empty(ReadQuery.ORACLE_VPD, Code.VISIBILITY_UNQUALIFIED, binding.schema(), binding.table());
        sql.empty(ReadQuery.ORACLE_REDACTION, Code.VISIBILITY_UNQUALIFIED, binding.schema(), binding.table());
        if (!"1".equals(sql.scalar(ReadQuery.ORACLE_READ_ACCESS, binding.schema(), binding.schema(), binding.table(), binding.schema()))) throw new ObservationFailure(Code.READ_ACCESS_DENIED);
        var columns = sql.rows(ReadQuery.ORACLE_COLUMNS, binding.schema(), binding.table(), binding.keyColumn(), binding.xmlColumn());
        if (columns.size() != 2) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        for (var column : columns) {
            boolean key = column.getFirst().equals(binding.keyColumn());
            if (column.get(2) != null || (key && !"N".equals(column.get(5))) || !"NO".equals(column.get(8)) || !"NO".equals(column.get(9)) || !"NO".equals(column.get(10))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            if (!key && !"CLOB".equals(column.get(1))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            if (key && binding.keyType() == KeyType.INT64 && (!"NUMBER".equals(column.get(1)) || !"19".equals(column.get(3)) || !"0".equals(column.get(4)))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            if (key && binding.keyType() == KeyType.TEXT && (!"VARCHAR2".equals(column.get(1)) || !"C".equals(column.get(7)) || column.get(6) == null || Integer.parseInt(column.get(6)) < 256)) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        }
        if (!"1".equals(sql.scalar(ReadQuery.ORACLE_UNIQUE_KEY, binding.schema(), binding.table(), binding.keyColumn()))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        return new Verified(Map.of("dbid", id.get(0), "dbUniqueName", id.get(1), "conId", id.get(2), "conUid", id.get(3), "conName", id.get(4), "pdbGuid", id.get(5)), "23.26.3.0.0", "AL32UTF8");
    }
}
