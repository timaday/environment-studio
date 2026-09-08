package studio.environment.server.observation;

import java.sql.SQLException;
import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.ObservationResult.Code;

final class OracleMetadata {
    private static final String GRANTEES = "(SELECT SYS_CONTEXT('USERENV','SESSION_USER') FROM dual UNION SELECT 'PUBLIC' FROM dual UNION SELECT granted_role FROM sys.dba_role_privs START WITH grantee IN (SYS_CONTEXT('USERENV','SESSION_USER'),'PUBLIC') CONNECT BY NOCYCLE PRIOR granted_role=grantee UNION SELECT role FROM session_roles)";
    record Verified(Map<String, String> identity, String version, String encoding) { }
    static Verified verify(SqlRead sql, Binding binding, ObservationDestination destination) throws SQLException {
        if (!"23.26.3.0.0".equals(sql.scalar("SELECT version_full FROM sys.v_$instance"))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        if (!"AL32UTF8".equals(sql.scalar("SELECT value FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET'"))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        var features = sql.rows("SELECT parameter,value FROM sys.v_$option WHERE parameter IN ('Oracle Label Security','Oracle Database Vault')");
        if (features.size() != 2 || features.stream().anyMatch(row -> !"FALSE".equals(row.get(1)))) throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
        if (!"FALSE".equals(sql.scalar("SELECT SYS_CONTEXT('USERENV','ISDBA') FROM dual"))) throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM dual WHERE SYS_CONTEXT('USERENV','PROXY_USER') IS NOT NULL", Code.ACCOUNT_NOT_READ_ONLY);
        var ids = sql.rows("SELECT TO_CHAR(d.dbid),d.db_unique_name,TO_CHAR(c.con_id),TO_CHAR(c.con_uid),c.name,LOWER(RAWTOHEX(c.guid)) FROM sys.v_$database d CROSS JOIN sys.v_$containers c WHERE c.con_id=TO_NUMBER(SYS_CONTEXT('USERENV','CON_ID'))");
        if (ids.size() != 1) throw new ObservationFailure(Code.METADATA_UNAVAILABLE);
        var id = ids.getFirst();
        if (!sql.rows("SELECT read_only,common FROM sys.dba_users WHERE username=SYS_CONTEXT('USERENV','SESSION_USER')").equals(List.of(List.of("YES","NO")))) throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        String special = "p.owner='SYS' AND p.table_name='PUBLIC' AND p.type='USER' AND p.privilege='INHERIT PRIVILEGES'";
        String targets = " FROM sys.dba_tab_privs p LEFT JOIN sys.dba_objects o ON p.type<>'USER' AND o.owner=p.owner AND o.object_name=p.table_name AND (o.object_type=p.type OR p.type='UNKNOWN') AND o.subobject_name IS NULL AND o.object_type NOT IN ('PACKAGE BODY','TYPE BODY') AND o.status='VALID' LEFT JOIN sys.dba_users u ON p.type='USER' AND u.username=p.table_name WHERE p.grantee='PUBLIC'";
        sql.empty("SELECT 1" + targets + " GROUP BY p.owner,p.table_name,p.type,p.privilege HAVING (p.type<>'USER' AND COUNT(DISTINCT o.object_id)<>1) OR (p.type='USER' AND COUNT(DISTINCT u.user_id)<>1 AND NOT (" + special + "))", Code.VISIBILITY_UNQUALIFIED);
        var publicGrants = sql.rows("SELECT DISTINCT p.owner,p.table_name,CASE WHEN p.type='USER' THEN 'USER' ELSE o.object_type END,p.privilege,p.grantable,p.common,p.inherited,CASE WHEN " + special + " THEN 'PUBLIC_SPECIAL_ROLE' WHEN p.type='USER' THEN u.oracle_maintained ELSE o.oracle_maintained END" + targets);
        if (publicGrants.isEmpty() || publicGrants.stream().anyMatch(row -> row.stream().anyMatch(Objects::isNull) || !Set.of("Y","PUBLIC_SPECIAL_ROLE").contains(row.get(7)))) throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        publicGrants = publicGrants.stream().sorted((left,right) -> {
            for (int i=0; i<8; i++) { int compared=Arrays.compareUnsigned(ObservationFingerprint.utf8(left.get(i)),ObservationFingerprint.utf8(right.get(i))); if(compared!=0)return compared; } return 0;
        }).toList();
        var grants = publicGrants.stream().map(row -> Map.of("owner",row.get(0),"objectName",row.get(1),"objectType",row.get(2),"privilege",row.get(3),"grantable",row.get(4),"common",row.get(5),"inherited",row.get(6),"oracleMaintained",row.get(7))).toList();
        String policy = "oracle-account-read-only-v2:" + ObservationFingerprint.hash("ES-ORACLE-PUBLIC-GRANTS-2", grants);
        if (!policy.equals(destination.accountPolicyVersion())) throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM sys.dba_objects WHERE owner=SYS_CONTEXT('USERENV','SESSION_USER')", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM sys.dba_sys_privs WHERE grantee IN " + GRANTEES + " AND (privilege <> 'CREATE SESSION' OR admin_option <> 'NO')", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM session_privs WHERE privilege <> 'CREATE SESSION'", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM sys.dba_role_privs WHERE grantee IN " + GRANTEES + " AND admin_option <> 'NO'", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM sys.dba_schema_privs WHERE grantee IN " + GRANTEES, Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM sys.dba_tab_privs WHERE grantee<>'PUBLIC' AND grantee IN " + GRANTEES + " AND (grantable <> 'NO' OR privilege NOT IN ('SELECT','READ','EXECUTE') OR (privilege='EXECUTE' AND grantee <> 'PUBLIC'))", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM sys.dba_col_privs WHERE grantee IN " + GRANTEES + " AND (privilege NOT IN ('SELECT','READ') OR grantable <> 'NO')", Code.ACCOUNT_NOT_READ_ONLY);
        var tables = sql.rows("SELECT partitioned,iot_type,nested,temporary,secondary,status FROM sys.dba_tables WHERE owner=? AND table_name=?", binding.schema(), binding.table());
        if (tables.size() != 1) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        var table = tables.getFirst();
        if (!"NO".equals(table.get(0)) || table.get(1) != null || !"NO".equals(table.get(2)) || !"N".equals(table.get(3)) || !"N".equals(table.get(4)) || !"VALID".equals(table.get(5))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        sql.empty("SELECT 1 FROM sys.dba_external_tables WHERE owner=? AND table_name=?", Code.STORAGE_UNSUPPORTED, binding.schema(), binding.table());
        sql.empty("SELECT 1 FROM sys.dba_synonyms WHERE owner=? AND synonym_name=?", Code.STORAGE_UNSUPPORTED, binding.schema(), binding.table());
        sql.empty("SELECT 1 FROM sys.dba_triggers WHERE table_owner=? AND table_name=? AND status='ENABLED'", Code.STORAGE_UNSUPPORTED, binding.schema(), binding.table());
        sql.empty("SELECT 1 FROM sys.dba_policies WHERE object_owner=? AND object_name=? AND enable='YES'", Code.VISIBILITY_UNQUALIFIED, binding.schema(), binding.table());
        sql.empty("SELECT 1 FROM sys.redaction_policies WHERE object_owner=? AND object_name=? AND enable='YES'", Code.VISIBILITY_UNQUALIFIED, binding.schema(), binding.table());
        if (!"1".equals(sql.scalar("SELECT COUNT(*) FROM sys.dba_tab_privs WHERE owner=? AND table_name=? AND grantee IN " + GRANTEES + " AND privilege IN ('SELECT','READ')", binding.schema(), binding.table()))) throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        var columns = sql.rows("SELECT column_name,data_type,data_type_owner,TO_CHAR(data_precision),TO_CHAR(data_scale),nullable,TO_CHAR(char_length),char_used,virtual_column,hidden_column,identity_column FROM sys.dba_tab_cols WHERE owner=? AND table_name=? AND column_name IN (?,?)", binding.schema(), binding.table(), binding.keyColumn(), binding.xmlColumn());
        if (columns.size() != 2) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        for (var column : columns) {
            boolean key = column.getFirst().equals(binding.keyColumn());
            if (column.get(2) != null || (key && !"N".equals(column.get(5))) || !"NO".equals(column.get(8)) || !"NO".equals(column.get(9)) || !"NO".equals(column.get(10))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            if (!key && !"CLOB".equals(column.get(1))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            if (key && binding.keyType() == KeyType.INT64 && (!"NUMBER".equals(column.get(1)) || !"19".equals(column.get(3)) || !"0".equals(column.get(4)))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
            if (key && binding.keyType() == KeyType.TEXT && (!"VARCHAR2".equals(column.get(1)) || !"C".equals(column.get(7)) || column.get(6) == null || Integer.parseInt(column.get(6)) < 256)) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        }
        if (!"1".equals(sql.scalar("SELECT COUNT(*) FROM sys.dba_constraints c WHERE c.owner=? AND c.table_name=? AND c.constraint_type IN ('P','U') AND c.status='ENABLED' AND c.validated='VALIDATED' AND c.deferrable='NOT DEFERRABLE' AND (SELECT COUNT(*) FROM sys.dba_cons_columns cc WHERE cc.owner=c.owner AND cc.constraint_name=c.constraint_name)=1 AND EXISTS(SELECT 1 FROM sys.dba_cons_columns cc WHERE cc.owner=c.owner AND cc.constraint_name=c.constraint_name AND cc.column_name=?)", binding.schema(), binding.table(), binding.keyColumn()))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        return new Verified(Map.of("dbid", id.get(0), "dbUniqueName", id.get(1), "conId", id.get(2), "conUid", id.get(3), "conName", id.get(4), "pdbGuid", id.get(5)), "23.26.3.0.0", "AL32UTF8");
    }
}
