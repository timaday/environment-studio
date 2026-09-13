package studio.environment.server.observation;

import studio.environment.core.definitionv2.NativeDefinition.Engine;

/** Closed vendor metadata vocabulary. No caller-controlled SQL fragments. */
enum ReadQuery {
    PG_SERVER_VERSION(Engine.POSTGRESQL, "SHOW server_version_num"),
    PG_SERVER_ENCODING(Engine.POSTGRESQL, "SHOW server_encoding"),
    PG_CLIENT_ENCODING(Engine.POSTGRESQL, "SHOW client_encoding"),
    PG_ISOLATION(Engine.POSTGRESQL, "SHOW transaction_isolation"),
    PG_READ_ONLY(Engine.POSTGRESQL, "SHOW transaction_read_only"),
    PG_ROW_SECURITY(Engine.POSTGRESQL, "SHOW row_security"),
    PG_IDENTITY(Engine.POSTGRESQL, "SELECT s.system_identifier::text,d.oid::text,d.datname FROM pg_catalog.pg_control_system() s,pg_catalog.pg_database d WHERE d.datname=current_database()"),
    PG_TABLE(Engine.POSTGRESQL, "SELECT c.oid::text,c.relkind::text,c.relpersistence::text,c.relrowsecurity::text,c.relforcerowsecurity::text,(c.relowner=current_user::regrole)::text FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relname=?"),
    PG_INHERITANCE(Engine.POSTGRESQL, "SELECT 1 FROM pg_catalog.pg_inherits WHERE inhrelid=?::oid OR inhparent=?::oid"),
    PG_POLICIES(Engine.POSTGRESQL, "SELECT 1 FROM pg_catalog.pg_policy WHERE polrelid=?::oid"),
    PG_TRIGGERS(Engine.POSTGRESQL, "SELECT 1 FROM pg_catalog.pg_trigger WHERE tgrelid=?::oid AND tgenabled <> 'D'"),
    PG_ACCESS_METHOD(Engine.POSTGRESQL, "SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_am a ON a.oid=c.relam WHERE c.oid=?::oid AND a.amname<>'heap'"),
    PG_TABLE_ACCESS(Engine.POSTGRESQL, "SELECT pg_catalog.has_table_privilege(current_user,?,'SELECT')::text"),
    PG_SCHEMA_ACCESS(Engine.POSTGRESQL, "SELECT pg_catalog.has_schema_privilege(current_user,?,'USAGE')::text"),
    PG_COLUMNS(Engine.POSTGRESQL, "SELECT a.attname,t.typname,n.nspname,a.attnotnull::text,a.attgenerated::text,a.attidentity::text,t.typtype::text,a.atttypmod::text FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_type t ON t.oid=a.atttypid JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace WHERE a.attrelid=?::oid AND a.attnum>0 AND NOT a.attisdropped AND a.attname IN (?,?)"),
    PG_UNIQUE_KEY(Engine.POSTGRESQL, "SELECT count(*)::text FROM pg_catalog.pg_constraint c JOIN pg_catalog.pg_index i ON i.indexrelid=c.conindid AND i.indisvalid AND i.indisready AND i.indislive JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1] WHERE c.conrelid=?::oid AND c.contype IN ('p','u') AND c.convalidated AND NOT c.condeferrable AND cardinality(c.conkey)=1 AND a.attname=?"),
    ORACLE_SERVER_VERSION(Engine.ORACLE, "SELECT version_full FROM sys.v_$instance"),
    ORACLE_ENCODING(Engine.ORACLE, "SELECT value FROM SYS.NLS_DATABASE_PARAMETERS WHERE parameter='NLS_CHARACTERSET'"),
    ORACLE_FGA(Engine.ORACLE, "SELECT 1 FROM sys.dba_audit_policies WHERE object_schema=? AND object_name=? AND enabled='YES'"),
    ORACLE_FEATURES(Engine.ORACLE, "SELECT parameter,value FROM sys.v_$option WHERE parameter IN ('Oracle Label Security','Oracle Database Vault')"),
    ORACLE_IS_DBA(Engine.ORACLE, "SELECT SYS_CONTEXT('USERENV','ISDBA') FROM SYS.DUAL"),
    ORACLE_PROXY(Engine.ORACLE, "SELECT 1 FROM SYS.DUAL WHERE SYS_CONTEXT('USERENV','PROXY_USER') IS NOT NULL"),
    ORACLE_SESSION_USER(Engine.ORACLE, "SELECT SYS_CONTEXT('USERENV','SESSION_USER') FROM SYS.DUAL"),
    ORACLE_IDENTITY(Engine.ORACLE, "SELECT TO_CHAR(d.dbid),d.db_unique_name,TO_CHAR(c.con_id),TO_CHAR(c.con_uid),c.name,LOWER(RAWTOHEX(c.guid)) FROM sys.v_$database d CROSS JOIN sys.v_$containers c WHERE c.con_id=TO_NUMBER(SYS_CONTEXT('USERENV','CON_ID'))"),
    ORACLE_USER_KIND(Engine.ORACLE, "SELECT common,oracle_maintained FROM SYS.DBA_USERS WHERE username=SYS_CONTEXT('USERENV','SESSION_USER')"),
    ORACLE_SOURCE_OWNER(Engine.ORACLE, "SELECT oracle_maintained FROM SYS.DBA_USERS WHERE username=?"),
    ORACLE_TABLE(Engine.ORACLE, "SELECT partitioned,iot_type,nested,temporary,secondary,status FROM sys.dba_tables WHERE owner=? AND table_name=?"),
    ORACLE_EXTERNAL_TABLE(Engine.ORACLE, "SELECT 1 FROM sys.dba_external_tables WHERE owner=? AND table_name=?"),
    ORACLE_SYNONYMS(Engine.ORACLE, "SELECT 1 FROM sys.dba_synonyms WHERE owner=? AND synonym_name=?"),
    ORACLE_TRIGGERS(Engine.ORACLE, "SELECT 1 FROM sys.dba_triggers WHERE table_owner=? AND table_name=? AND status='ENABLED'"),
    ORACLE_VPD(Engine.ORACLE, "SELECT 1 FROM sys.dba_policies WHERE object_owner=? AND object_name=? AND enable='YES'"),
    ORACLE_REDACTION(Engine.ORACLE, "SELECT 1 FROM sys.redaction_policies WHERE object_owner=? AND object_name=? AND enable='YES'"),
    ORACLE_READ_ACCESS(Engine.ORACLE, "SELECT CASE WHEN SYS_CONTEXT('USERENV','SESSION_USER')=? OR EXISTS(SELECT 1 FROM SYS.DBA_TAB_PRIVS WHERE owner=? AND table_name=? AND grantee IN " + "(SELECT SYS_CONTEXT('USERENV','SESSION_USER') FROM SYS.DUAL UNION SELECT 'PUBLIC' FROM SYS.DUAL UNION SELECT role FROM SYS.SESSION_ROLES)" + " AND privilege IN ('SELECT','READ')) OR EXISTS(SELECT 1 FROM SYS.SESSION_PRIVS WHERE privilege IN ('SELECT ANY TABLE','READ ANY TABLE')) OR EXISTS(SELECT 1 FROM SYS.SESSION_SCHEMA_PRIVS WHERE schema=? AND privilege IN ('SELECT ANY TABLE','READ ANY TABLE')) THEN '1' ELSE '0' END FROM SYS.DUAL"),
    ORACLE_COLUMNS(Engine.ORACLE, "SELECT column_name,data_type,data_type_owner,TO_CHAR(data_precision),TO_CHAR(data_scale),nullable,TO_CHAR(char_length),char_used,virtual_column,hidden_column,identity_column FROM sys.dba_tab_cols WHERE owner=? AND table_name=? AND column_name IN (?,?)"),
    ORACLE_UNIQUE_KEY(Engine.ORACLE, "SELECT COUNT(*) FROM sys.dba_constraints c WHERE c.owner=? AND c.table_name=? AND c.constraint_type IN ('P','U') AND c.status='ENABLED' AND c.validated='VALIDATED' AND c.deferrable='NOT DEFERRABLE' AND (SELECT COUNT(*) FROM sys.dba_cons_columns cc WHERE cc.owner=c.owner AND cc.constraint_name=c.constraint_name)=1 AND EXISTS(SELECT 1 FROM sys.dba_cons_columns cc WHERE cc.owner=c.owner AND cc.constraint_name=c.constraint_name AND cc.column_name=?)");
    final Engine engine;
    final String text;
    ReadQuery(Engine engine, String text) { this.engine=engine; this.text=text; }
}
