package studio.environment.server.observation;

import java.sql.SQLException;
import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.observation.ObservationResult.Code;

final class PostgresMetadata {
    private static final String REACHABLE = "(SELECT oid FROM pg_catalog.pg_roles WHERE oid=current_user::regrole OR pg_catalog.pg_has_role(current_user,oid,'MEMBER'))";
    record Verified(Map<String, String> identity, String version, String encoding) { }
    static Verified verify(SqlRead sql, Binding binding) throws SQLException {
        if (!"180006".equals(sql.scalar("SHOW server_version_num"))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        if (!"UTF8".equals(sql.scalar("SHOW server_encoding")) || !"UTF8".equals(sql.scalar("SHOW client_encoding"))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        if (!"repeatable read".equals(sql.scalar("SHOW transaction_isolation")) || !"on".equals(sql.scalar("SHOW transaction_read_only")) || !"off".equals(sql.scalar("SHOW row_security"))) throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
        var identity = sql.rows("SELECT s.system_identifier::text,d.oid::text,d.datname FROM pg_catalog.pg_control_system() s,pg_catalog.pg_database d WHERE d.datname=current_database()");
        if (identity.size() != 1) throw new ObservationFailure(Code.METADATA_UNAVAILABLE);
        var id = identity.getFirst();
        String relation = SqlRead.quoted(binding.schema()) + "." + SqlRead.quoted(binding.table());
        var tables = sql.rows("SELECT c.oid::text,c.relkind::text,c.relpersistence::text,c.relrowsecurity::text,c.relforcerowsecurity::text,(c.relowner=current_user::regrole)::text FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relname=?", binding.schema(), binding.table());
        if (tables.size() != 1) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        var table = tables.getFirst();
        if (!table.subList(1, 6).equals(List.of("r", "p", "false", "false", "false"))) throw new ObservationFailure(Code.VISIBILITY_UNQUALIFIED);
        long oid = Long.parseLong(table.getFirst());
        sql.empty("SELECT 1 FROM pg_catalog.pg_inherits WHERE inhrelid=?::oid OR inhparent=?::oid", Code.STORAGE_UNSUPPORTED, oid, oid);
        sql.empty("SELECT 1 FROM pg_catalog.pg_policy WHERE polrelid=?::oid", Code.VISIBILITY_UNQUALIFIED, oid);
        sql.empty("SELECT 1 FROM pg_catalog.pg_trigger WHERE tgrelid=?::oid AND tgenabled <> 'D'", Code.STORAGE_UNSUPPORTED, oid);
        sql.empty("SELECT 1 FROM pg_catalog.pg_roles r WHERE (r.oid=current_user::regrole OR pg_catalog.pg_has_role(current_user,r.oid,'MEMBER')) AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_roles WHERE oid IN " + REACHABLE + " AND rolname LIKE 'pg\\_%' ESCAPE '\\'", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_auth_members WHERE member IN " + REACHABLE + " AND admin_option", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_am a ON a.oid=c.relam WHERE c.oid=?::oid AND a.amname<>'heap'", Code.STORAGE_UNSUPPORTED, oid);
        verifySettings(sql);
        sql.empty("SELECT 1 FROM pg_catalog.pg_parameter_acl p CROSS JOIN LATERAL pg_catalog.aclexplode(p.paracl) a WHERE a.grantee=0 OR a.grantee IN " + REACHABLE, Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_namespace n CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND (n.nspowner=r.oid OR pg_catalog.has_schema_privilege(r.oid,n.oid,'CREATE'))", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_database d CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND d.datname=current_database() AND (d.datdba=r.oid OR pg_catalog.has_database_privilege(r.oid,d.oid,'CREATE,TEMP'))", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_class c CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND c.relkind IN ('r','p','v','m','f') AND (c.relowner=r.oid OR pg_catalog.has_table_privilege(r.oid,c.oid,'INSERT,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN') OR pg_catalog.has_any_column_privilege(r.oid,c.oid,'INSERT,REFERENCES') OR (c.oid<>'pg_catalog.pg_settings'::regclass AND (pg_catalog.has_table_privilege(r.oid,c.oid,'UPDATE') OR pg_catalog.has_any_column_privilege(r.oid,c.oid,'UPDATE'))))", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_class c CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND c.relkind='S' AND (c.relowner=r.oid OR pg_catalog.has_sequence_privilege(r.oid,c.oid,'USAGE,UPDATE'))", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_largeobject_metadata l CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND (l.lomowner=r.oid OR pg_catalog.has_largeobject_privilege(r.oid,l.oid,'UPDATE'))", Code.ACCOUNT_NOT_READ_ONLY);
        verifyNoDelegation(sql);
        sql.empty("SELECT 1 FROM pg_catalog.pg_shdepend WHERE deptype='o' AND refclassid='pg_catalog.pg_authid'::regclass AND refobjid IN " + REACHABLE, Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM (SELECT proowner AS owner FROM pg_catalog.pg_proc UNION ALL SELECT typowner FROM pg_catalog.pg_type UNION ALL SELECT lanowner FROM pg_catalog.pg_language UNION ALL SELECT spcowner FROM pg_catalog.pg_tablespace UNION ALL SELECT fdwowner FROM pg_catalog.pg_foreign_data_wrapper UNION ALL SELECT srvowner FROM pg_catalog.pg_foreign_server UNION ALL SELECT datdba FROM pg_catalog.pg_database) owned WHERE owner IN " + REACHABLE, Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_tablespace s CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND pg_catalog.has_tablespace_privilege(r.oid,s.oid,'CREATE')", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_foreign_data_wrapper f CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND pg_catalog.has_foreign_data_wrapper_privilege(r.oid,f.oid,'USAGE')", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_foreign_server s CROSS JOIN pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND pg_catalog.has_server_privilege(r.oid,s.oid,'USAGE')", Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND EXISTS (SELECT 1 FROM pg_catalog.pg_roles r WHERE r.oid IN " + REACHABLE + " AND pg_catalog.has_function_privilege(r.oid,p.oid,'EXECUTE'))", Code.ACCOUNT_NOT_READ_ONLY);
        if (!"true".equals(sql.scalar("SELECT pg_catalog.has_table_privilege(current_user,?,'SELECT')::text", relation))) throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        var columns = sql.rows("SELECT a.attname,t.typname,n.nspname,a.attnotnull::text,a.attgenerated::text,a.attidentity::text,t.typtype::text,a.atttypmod::text FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_type t ON t.oid=a.atttypid JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace WHERE a.attrelid=?::oid AND a.attnum>0 AND NOT a.attisdropped AND a.attname IN (?,?)", oid, binding.keyColumn(), binding.xmlColumn());
        if (columns.size() != 2) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        for (var column : columns) {
            String expected = column.getFirst().equals(binding.keyColumn()) && binding.keyType() == KeyType.INT64 ? "int8" : "text";
            if (!column.get(1).equals(expected) || !column.get(2).equals("pg_catalog") || (column.getFirst().equals(binding.keyColumn()) && !column.get(3).equals("true")) || !column.get(4).isEmpty() || !column.get(5).isEmpty() || !column.get(6).equals("b") || !column.get(7).equals("-1")) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        }
        if (!"1".equals(sql.scalar("SELECT count(*)::text FROM pg_catalog.pg_constraint c JOIN pg_catalog.pg_index i ON i.indexrelid=c.conindid AND i.indisvalid AND i.indisready AND i.indislive JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1] WHERE c.conrelid=?::oid AND c.contype IN ('p','u') AND c.convalidated AND NOT c.condeferrable AND cardinality(c.conkey)=1 AND a.attname=?", oid, binding.keyColumn()))) throw new ObservationFailure(Code.STORAGE_UNSUPPORTED);
        return new Verified(Map.of("systemIdentifier", id.get(0), "databaseOid", id.get(1), "databaseName", id.get(2)), "18.6", "UTF8");
    }

    private static void verifyNoDelegation(SqlRead sql) throws SQLException {
        // ACLs are database-owned metadata. Include column and non-table objects;
        // effective reads alone do not prove that the account cannot delegate them.
        String acls = "(SELECT relacl AS acl FROM pg_catalog.pg_class UNION ALL SELECT attacl FROM pg_catalog.pg_attribute UNION ALL SELECT datacl FROM pg_catalog.pg_database UNION ALL SELECT nspacl FROM pg_catalog.pg_namespace UNION ALL SELECT proacl FROM pg_catalog.pg_proc UNION ALL SELECT typacl FROM pg_catalog.pg_type UNION ALL SELECT lanacl FROM pg_catalog.pg_language UNION ALL SELECT spcacl FROM pg_catalog.pg_tablespace UNION ALL SELECT fdwacl FROM pg_catalog.pg_foreign_data_wrapper UNION ALL SELECT srvacl FROM pg_catalog.pg_foreign_server UNION ALL SELECT lomacl FROM pg_catalog.pg_largeobject_metadata UNION ALL SELECT paracl FROM pg_catalog.pg_parameter_acl UNION ALL SELECT defaclacl FROM pg_catalog.pg_default_acl)";
        sql.empty("SELECT 1 FROM " + acls + " privileges CROSS JOIN LATERAL pg_catalog.aclexplode(privileges.acl) a WHERE a.is_grantable AND (a.grantee=0 OR a.grantee IN " + REACHABLE + ")", Code.ACCOUNT_NOT_READ_ONLY);
    }

    private static void verifySettings(SqlRead sql) throws SQLException {
        var provenance=sql.rows("SELECT c.relkind::text,c.relowner::text,c.relnamespace::text,r.rolsuper::text,(c.oid<16384)::text FROM pg_catalog.pg_class c JOIN pg_catalog.pg_roles r ON r.oid=c.relowner WHERE c.oid='pg_catalog.pg_settings'::regclass");
        if (!provenance.equals(List.of(List.of("v","10","11","true","true")))) throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        var acl=sql.rows("SELECT a.grantee::text,a.privilege_type,a.is_grantable::text FROM pg_catalog.pg_class c CROSS JOIN LATERAL pg_catalog.aclexplode(c.relacl) a WHERE c.oid='pg_catalog.pg_settings'::regclass");
        var expected=new HashSet<List<String>>();
        for(String privilege:List.of("INSERT","SELECT","UPDATE","DELETE","TRUNCATE","REFERENCES","TRIGGER","MAINTAIN"))expected.add(List.of("10",privilege,"false"));
        expected.add(List.of("0","SELECT","false")); expected.add(List.of("0","UPDATE","false"));
        if(acl.size()!=expected.size() || !new HashSet<>(acl).equals(expected))throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        sql.empty("SELECT 1 FROM pg_catalog.pg_attribute WHERE attrelid='pg_catalog.pg_settings'::regclass AND attacl IS NOT NULL",Code.ACCOUNT_NOT_READ_ONLY);
        // Generic PostgreSQL REL_18_6 vendor view/rules, not application configuration.
        String fields="name, setting, unit, category, short_desc, extra_desc, context, vartype, source, min_val, max_val, enumvals, boot_val, reset_val, sourcefile, sourceline, pending_restart";
        String select="SELECT "+fields+" FROM pg_show_all_settings() a("+fields+");";
        if(!normalized(sql.scalar("SELECT pg_catalog.pg_get_viewdef('pg_catalog.pg_settings'::regclass,false)")).equals(select))throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        var rules=sql.rows("SELECT rulename,pg_catalog.pg_get_ruledef(oid,false) FROM pg_catalog.pg_rewrite WHERE ev_class='pg_catalog.pg_settings'::regclass");
        var expectedRules=Map.of("_RETURN","CREATE RULE \"_RETURN\" AS ON SELECT TO pg_catalog.pg_settings DO INSTEAD "+select,
                "pg_settings_n","CREATE RULE pg_settings_n AS ON UPDATE TO pg_catalog.pg_settings DO INSTEAD NOTHING;",
                "pg_settings_u","CREATE RULE pg_settings_u AS ON UPDATE TO pg_catalog.pg_settings WHERE (new.name = old.name) DO SELECT set_config(old.name, new.setting, false) AS set_config;");
        if(rules.size()!=3)throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        for(var rule:rules)if(!Objects.equals(expectedRules.get(rule.get(0)),normalized(rule.get(1))))throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
        var functions=sql.rows("SELECT oid::text,proname,prosrc,prolang::text,proowner::text,prosecdef::text,(proconfig IS NULL)::text,(proacl IS NULL)::text FROM pg_catalog.pg_proc WHERE oid IN ('pg_catalog.pg_show_all_settings()'::regprocedure,'pg_catalog.set_config(text,text,boolean)'::regprocedure)");
        if(!new HashSet<>(functions).equals(Set.of(List.of("2078","set_config","set_config_by_name","12","10","false","true","true"),List.of("2084","pg_show_all_settings","show_all_settings","12","10","false","true","true"))))throw new ObservationFailure(Code.ACCOUNT_NOT_READ_ONLY);
    }
    private static String normalized(String sql) { return sql.replaceAll("\\s+"," ").strip(); }
}
