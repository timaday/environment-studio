\if :{?schema_name}
\else
\echo 'Set schema_name, table_name, key_column, xml_column and key_type. Example:'
\echo 'psql "$JDBC_TARGET" -v schema_name=public -v table_name=app_config -v key_column=id -v xml_column=config_xml -v key_type=INT64 -f scripts/postgres16_storage_probe.sql'
\quit 2
\endif
\if :{?table_name}
\else
\echo 'Missing table_name'
\quit 2
\endif
\if :{?key_column}
\else
\echo 'Missing key_column'
\quit 2
\endif
\if :{?xml_column}
\else
\echo 'Missing xml_column'
\quit 2
\endif
\if :{?key_type}
\else
\echo 'Missing key_type: use INT64 or TEXT'
\quit 2
\endif

\pset pager off
\pset tuples_only off
\pset format aligned

with requested as (
  select
    :'schema_name'::text as schema_name,
    :'table_name'::text as table_name,
    :'key_column'::text as key_column,
    :'xml_column'::text as xml_column,
    upper(:'key_type'::text) as key_type
), relation as (
  select c.oid, n.nspname, c.relname, c.relkind, c.relpersistence, c.relrowsecurity,
         c.relforcerowsecurity, c.relam
  from requested r
  left join pg_catalog.pg_namespace n on n.nspname = r.schema_name
  left join pg_catalog.pg_class c on c.relnamespace = n.oid and c.relname = r.table_name
), columns as (
  select a.attname, t.typname, tn.nspname as type_schema, a.attnotnull::text as attnotnull,
         a.attgenerated, a.attidentity, t.typtype, a.atttypmod::text as atttypmod
  from relation rel
  join requested r on true
  join pg_catalog.pg_attribute a on a.attrelid = rel.oid
  join pg_catalog.pg_type t on t.oid = a.atttypid
  join pg_catalog.pg_namespace tn on tn.oid = t.typnamespace
  where a.attnum > 0 and not a.attisdropped and a.attname in (r.key_column, r.xml_column)
), checks as (
  select 'server_version_16_11' as check_name,
         current_setting('server_version_num') = '160011' as pass,
         current_setting('server_version_num') as observed,
         'PostgreSQL server_version_num must be 160011.' as requirement
  union all select 'server_encoding_utf8', current_setting('server_encoding') = 'UTF8', current_setting('server_encoding'), 'server_encoding must be UTF8.'
  union all select 'client_encoding_utf8', current_setting('client_encoding') = 'UTF8', current_setting('client_encoding'), 'client_encoding must be UTF8.'
  union all select 'schema_not_system', not (schema_name like 'pg_%' or schema_name = 'information_schema'), schema_name, 'Schema must not be pg_* or information_schema.' from requested
  union all select 'single_plain_table', count(*) = 1 and bool_and(relkind = 'r' and relpersistence = 'p'), coalesce(string_agg(coalesce(relkind::text,'missing') || '/' || coalesce(relpersistence::text,'missing'), ','), 'missing'), 'Exactly one permanent ordinary table is required.' from relation
  union all select 'no_row_security', count(*) = 1 and bool_and(not relrowsecurity and not relforcerowsecurity), coalesce(string_agg(relrowsecurity::text || '/' || relforcerowsecurity::text, ','), 'missing'), 'Row security must be disabled for this pilot.' from relation
  union all select 'no_inheritance_or_partitioning', count(i.*) = 0, count(i.*)::text, 'Table must not inherit from another table and must not be inherited/partitioned.' from relation rel left join pg_catalog.pg_inherits i on i.inhrelid = rel.oid or i.inhparent = rel.oid
  union all select 'enabled_triggers_allowed', true, count(t.*)::text, 'Enabled triggers are allowed in the PostgreSQL 16.11 pilot; target-side updates will fire them.' from relation rel left join pg_catalog.pg_trigger t on t.tgrelid = rel.oid and t.tgenabled <> 'D'
  union all select 'heap_access_method', count(a.*) = 0, coalesce(string_agg(a.amname, ','), 'heap or none'), 'Non-heap table access methods are unsupported.' from relation rel left join pg_catalog.pg_am a on a.oid = rel.relam and a.amname <> 'heap'
  union all select 'select_privilege', pg_catalog.has_table_privilege(current_user, pg_catalog.quote_ident(r.schema_name) || '.' || pg_catalog.quote_ident(r.table_name), 'SELECT'), current_user, 'Current user must have SELECT on the table.' from requested r
  union all select 'schema_usage', pg_catalog.has_schema_privilege(current_user, schema_name, 'USAGE'), current_user, 'Current user must have USAGE on the schema.' from requested
  union all select 'both_columns_found', count(*) = 2, count(*)::text, 'Both key and XML text columns must exist as real non-dropped columns.' from columns
  union all select 'xml_column_text', exists(select 1 from columns c join requested r on c.attname = r.xml_column where c.typname = 'text' and c.type_schema = 'pg_catalog' and c.typtype = 'b' and c.atttypmod = '-1' and c.attgenerated = '' and c.attidentity = ''), coalesce((select typname || '/' || type_schema || '/generated=' || attgenerated::text || '/identity=' || attidentity::text from columns c join requested r on c.attname = r.xml_column), 'missing'), 'XML storage column must be plain pg_catalog.text, not generated or identity.'
  union all select 'key_column_type', exists(select 1 from columns c join requested r on c.attname = r.key_column where c.typname = case when r.key_type = 'INT64' then 'int8' else 'text' end and c.type_schema = 'pg_catalog' and c.typtype = 'b' and c.atttypmod = '-1' and c.attgenerated = '' and c.attidentity = '' and (r.key_type <> 'INT64' or c.attnotnull = 'true')), coalesce((select typname || '/' || type_schema || '/notnull=' || attnotnull || '/generated=' || attgenerated::text::text || '/identity=' || attidentity::text from columns c join requested r on c.attname = r.key_column), 'missing'), 'Key column must match definition key type: INT64=bigint NOT NULL, TEXT=text.'
  union all select 'single_column_unique_key', exists(select 1 from relation rel join requested r on true join pg_catalog.pg_constraint con on con.conrelid = rel.oid join pg_catalog.pg_index idx on idx.indexrelid = con.conindid and idx.indisvalid and idx.indisready and idx.indislive join pg_catalog.pg_attribute a on a.attrelid = con.conrelid and a.attnum = con.conkey[1] where con.contype in ('p','u') and con.convalidated and not con.condeferrable and cardinality(con.conkey) = 1 and a.attname = r.key_column), coalesce((select string_agg(con.conname, ',') from relation rel join requested r on true join pg_catalog.pg_constraint con on con.conrelid = rel.oid join pg_catalog.pg_attribute a on a.attrelid = con.conrelid and a.attnum = con.conkey[1] where con.contype in ('p','u') and cardinality(con.conkey) = 1 and a.attname = r.key_column), 'none'), 'Key column must have a valid ready non-deferrable single-column primary or unique constraint.'
)
select check_name, case when pass then 'PASS' else 'FAIL' end as result, observed, requirement
from checks
order by check_name;
