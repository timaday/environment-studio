package studio.environment.server.export;

import static studio.environment.server.export.TransactionTemplates.*;

final class PostgresTransaction {
    private PostgresTransaction() { }
    static TransactionTemplates.Result.Candidate generate(PackageAdmission.Result.Accepted input) {
        var s=new Source();var table=input.payload().table();String target=id(table.schema())+"."+id(table.name());String xml=id(table.xmlColumn());
        var identity=(PackageData.PhysicalIdentity.Postgres)input.execution().destination().expectedPhysicalIdentity();
        s.line("DO $es$\nDECLARE\n  es_oid oid;\n  es_count bigint;");
        values(s,input,false);values(s,input,true);s.line("BEGIN");
        s.line("  PERFORM pg_catalog.set_config('environment_studio.program_digest','',true);");
        s.line("  PERFORM pg_catalog.set_config('lock_timeout','30s',true);");
        s.line("  LOCK TABLE "+target+" IN ACCESS EXCLUSIVE MODE;");
        guard(s,"current_setting('server_version_num') <> '180006' OR current_setting('server_encoding') <> 'UTF8' OR current_setting('client_encoding') <> 'UTF8' OR current_setting('transaction_read_only') <> 'off'","ES_SETTINGS_MISMATCH");
        guard(s,"(SELECT system_identifier::text FROM pg_catalog.pg_control_system()) <> '"+identity.systemIdentifier()+"' OR (SELECT oid::text FROM pg_catalog.pg_database WHERE datname=current_database()) <> '"+identity.databaseOid()+"' OR pg_catalog.convert_to(current_database(),'UTF8') <> pg_catalog.decode('"+hex(identity.databaseName())+"','hex')","ES_DESTINATION_MISMATCH");
        s.line("  SELECT c.oid INTO STRICT es_oid FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname="+pgText(table.schema())+" AND c.relname="+pgText(table.name())+";");
        guard(s,"NOT EXISTS(SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_am a ON a.oid=c.relam WHERE c.oid=es_oid AND c.relkind='r' AND c.relpersistence='p' AND NOT c.relispartition AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity AND a.amname='heap' AND a.oid<16384)","ES_TABLE_UNSUPPORTED");
        guard(s,"EXISTS(SELECT 1 FROM pg_catalog.pg_inherits WHERE inhrelid=es_oid OR inhparent=es_oid) OR EXISTS(SELECT 1 FROM pg_catalog.pg_policy WHERE polrelid=es_oid) OR EXISTS(SELECT 1 FROM pg_catalog.pg_trigger WHERE tgrelid=es_oid) OR EXISTS(SELECT 1 FROM pg_catalog.pg_rewrite WHERE ev_class=es_oid)","ES_WRITE_EFFECT_UNSUPPORTED");
        guard(s,"EXISTS(SELECT 1 FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_type t ON t.oid=a.atttypid JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace WHERE a.attrelid=es_oid AND a.attnum>0 AND NOT a.attisdropped AND (a.attgenerated<>'' OR a.attidentity<>'' OR n.nspname<>'pg_catalog' OR t.oid>=16384 OR t.typtype<>'b' OR t.typname NOT IN ('text','varchar','bpchar','int2','int4','int8','bool','numeric','float4','float8','bytea','date','timestamp','timestamptz','uuid')))","ES_COLUMN_EFFECT_UNSUPPORTED");
        guard(s,"(SELECT count(*) FROM pg_catalog.pg_attribute a WHERE a.attrelid=es_oid AND a.attnum>0 AND NOT a.attisdropped AND ((a.attname="+pgText(table.keyColumn())+" AND a.attnotnull AND a.atttypid='pg_catalog."+(table.keyType()==PackageData.KeyType.TEXT?"text":"int8")+"'::regtype) OR (a.attname="+pgText(table.xmlColumn())+" AND a.atttypid='pg_catalog.text'::regtype))) <> 2","ES_COLUMNS_MISMATCH");
        guard(s,"EXISTS(SELECT 1 FROM pg_catalog.pg_constraint c WHERE (c.conrelid=es_oid OR c.confrelid=es_oid) AND (c.contype NOT IN ('p','u','n') OR c.condeferrable OR NOT c.convalidated OR NOT c.conenforced))","ES_CONSTRAINT_UNSUPPORTED");
        guard(s,"(SELECT count(*) FROM pg_catalog.pg_constraint c JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1] WHERE c.conrelid=es_oid AND c.contype IN ('p','u') AND cardinality(c.conkey)=1 AND a.attname="+pgText(table.keyColumn())+")<>1","ES_KEY_CONSTRAINT_MISMATCH");
        guard(s,"EXISTS(SELECT 1 FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class c ON c.oid=i.indexrelid JOIN pg_catalog.pg_am a ON a.oid=c.relam WHERE i.indrelid=es_oid AND (NOT i.indisvalid OR NOT i.indisready OR NOT i.indislive OR NOT i.indimmediate OR i.indpred IS NOT NULL OR i.indexprs IS NOT NULL OR a.amname<>'btree' OR a.oid>=16384))","ES_INDEX_UNSUPPORTED");
        guard(s,"EXISTS(SELECT 1 FROM pg_catalog.pg_index i CROSS JOIN LATERAL unnest(i.indclass) x(oid) JOIN pg_catalog.pg_opclass o ON o.oid=x.oid JOIN pg_catalog.pg_namespace n ON n.oid=o.opcnamespace WHERE i.indrelid=es_oid AND (n.nspname<>'pg_catalog' OR o.oid>=16384 OR o.opcname NOT IN ('text_ops','int2_ops','int4_ops','int8_ops','bool_ops','numeric_ops','float4_ops','float8_ops','bytea_ops','date_ops','timestamp_ops','timestamptz_ops','uuid_ops','bpchar_ops')))","ES_INDEX_OPERATOR_UNSUPPORTED");
        guard(s,"EXISTS(SELECT 1 FROM pg_catalog.pg_index i CROSS JOIN LATERAL unnest(i.indcollation) x(oid) LEFT JOIN pg_catalog.pg_collation c ON c.oid=x.oid WHERE i.indrelid=es_oid AND x.oid<>0 AND (c.oid IS NULL OR c.oid>=16384 OR c.collname NOT IN ('C','POSIX','default') OR NOT c.collisdeterministic)) OR (EXISTS(SELECT 1 FROM pg_catalog.pg_index i CROSS JOIN LATERAL unnest(i.indcollation) x(oid) JOIN pg_catalog.pg_collation c ON c.oid=x.oid WHERE i.indrelid=es_oid AND c.collname='default') AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_database WHERE datname=current_database() AND datlocprovider='c' AND datcollate IN ('C','C.UTF-8','C.UTF8','POSIX') AND datctype IN ('C','C.UTF-8','C.UTF8','POSIX')))","ES_COLLATION_UNSUPPORTED");
        membership(s,target,input.payload().records().size());
        for(int i=0;i<input.payload().records().size();i++){var d=input.payload().records().get(i);compare(s,target,xml,key(table,d.key()),"es_original["+(i+1)+"]","ES_ORIGINAL_MISMATCH");}
        for(int i=0;i<input.payload().records().size();i++){var d=input.payload().records().get(i);if(!d.originalHex().equals(d.targetHex())) {
            s.line("  UPDATE "+target+" SET "+xml+"=pg_catalog.convert_from(es_target["+(i+1)+"],'UTF8') WHERE "+key(table,d.key())+";");
            s.line("  GET DIAGNOSTICS es_count = ROW_COUNT;");guard(s,"es_count<>1","ES_ROW_COUNT_MISMATCH");
        }}
        membership(s,target,input.payload().records().size());
        for(int i=0;i<input.payload().records().size();i++){var d=input.payload().records().get(i);compare(s,target,xml,key(table,d.key()),"es_target["+(i+1)+"]","ES_TARGET_MISMATCH");}
        s.line("  PERFORM pg_catalog.set_config('environment_studio.program_digest','"+input.programDigest()+"',true);");
        s.line("EXCEPTION WHEN OTHERS THEN\n  RAISE EXCEPTION 'ES_GUARDED_TRANSACTION_FAILED';\nEND;\n$es$;");
        byte[]bytes=s.bytes();return new TransactionTemplates.Result.Candidate(bytes,java.util.List.of(new TransactionTemplates.Block(0,bytes.length)));
    }
    private static void guard(Source s,String condition,String code){s.line("  IF "+condition+" THEN RAISE EXCEPTION '"+code+"'; END IF;");}
    private static String key(PackageData.Table t,PackageData.Key k){return k.type()==PackageData.KeyType.INT64?id(t.keyColumn())+"='"+k.value()+"'::bigint":"pg_catalog.convert_to("+id(t.keyColumn())+",'UTF8')=pg_catalog.decode('"+hex(k.value())+"','hex')";}
    private static void membership(Source s,String target,int count){guard(s,"(SELECT count(*) FROM "+target+")<>"+count,"ES_MEMBERSHIP_MISMATCH");}
    private static void compare(Source s,String table,String xml,String key,String value,String code){guard(s,"(SELECT count(*) FROM "+table+" WHERE "+key+" AND "+xml+" IS NOT NULL AND pg_catalog.convert_to("+xml+",'UTF8')="+value+")<>1",code);}
    private static void values(Source s,PackageAdmission.Result.Accepted input,boolean target) {
        s.line("  "+(target?"es_target":"es_original")+" bytea[] := ARRAY[");
        for(int i=0;i<input.payload().records().size();i++){var d=input.payload().records().get(i);s.line("    pg_catalog.decode('"+(target?d.targetHex():d.originalHex())+"','hex')"+(i+1<input.payload().records().size()?",":"];") );}
    }
}
