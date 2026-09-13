package studio.environment.server.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import static studio.environment.server.export.TransactionTemplates.*;

final class OracleTransaction {
    private OracleTransaction() { }
    private record Stream(byte[] bytes, List<Integer> offsets, List<Integer> lengths) { }
    static TransactionTemplates.Result.Candidate generate(PackageAdmission.Result.Accepted input) {
        Stream original=aggregate(input,false),target=aggregate(input,true);
        var blocks=new ArrayList<byte[]>();
        blocks.add(initialization());loaders(blocks,original.bytes(),":es_original_blob");loaders(blocks,target.bytes(),":es_target_blob");
        blocks.add(guard(input,original,target));
        var bytes=new ByteArrayOutputStream();var spans=new ArrayList<TransactionTemplates.Block>();
        for(byte[] rawBlock:blocks) {
            byte[] block=boundedLines(rawBlock);
            if(bytes.size()>0)bytes.write('\n');
            if(block.length>PackageJson.LARGE-bytes.size())PackageJson.fail("RESOURCE_LIMIT");
            spans.add(new TransactionTemplates.Block(bytes.size(),block.length));bytes.writeBytes(block);
        }
        return new TransactionTemplates.Result.Candidate(bytes.toByteArray(),spans);
    }
    private static byte[] boundedLines(byte[] bytes) {
        String source=new String(bytes,StandardCharsets.US_ASCII);var out=new Source();
        for(String line:source.split("\n",-1)) {
            if(line.isEmpty())continue;
            while(line.length()>2499) {
                boolean single=false,doubled=false;int split=-1;
                for(int i=0;i<Math.min(2400,line.length());i++) {char c=line.charAt(i);if(c=='\''&&!doubled)single=!single;else if(c=='"'&&!single)doubled=!doubled;else if(c==' '&&!single&&!doubled)split=i;}
                if(split<1)PackageJson.fail("SQL_SOURCE_LINE_LIMIT");
                out.line(line.substring(0,split));line="  "+line.substring(split+1);
            }
            out.line(line);
        }
        return out.bytes();
    }
    private static Stream aggregate(PackageAdmission.Result.Accepted input,boolean target) {
        var bytes=new ByteArrayOutputStream();var offsets=new ArrayList<Integer>();var lengths=new ArrayList<Integer>();
        for(var d:input.payload().records()) {
            String hex=target?d.targetHex():d.originalHex();int length=hex.length()/2;
            if(length<1 || length>16*1024*1024-bytes.size())PackageJson.fail("RESOURCE_LIMIT");
            offsets.add(bytes.size()+1);lengths.add(length);bytes.writeBytes(HexFormat.of().parseHex(hex));
        }
        if(bytes.size()==0)PackageJson.fail("RESOURCE_LIMIT");
        return new Stream(bytes.toByteArray(),List.copyOf(offsets),List.copyOf(lengths));
    }
    private static byte[] initialization() {
        var s=new Source();s.line("DECLARE");s.add(cleanup(false));s.line("BEGIN\n  :es_program_digest := NULL;\n  SYS.DBMS_LOB.CREATETEMPORARY(:es_original_blob,TRUE,SYS.DBMS_LOB.SESSION);\n  SYS.DBMS_LOB.CREATETEMPORARY(:es_target_blob,TRUE,SYS.DBMS_LOB.SESSION);");exception(s);return s.bytes();
    }
    private static void loaders(List<byte[]> blocks,byte[] bytes,String bind) {
        for(int group=0;group<bytes.length;group+=32768) {
            var s=new Source();s.line("DECLARE\n  es_raw RAW(16384);");s.add(cleanup(false));s.line("BEGIN");
            for(int offset=group;offset<Math.min(bytes.length,group+32768);offset+=16384) {
                int length=Math.min(16384,bytes.length-offset);
                String text=Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(bytes,offset,offset+length));
                s.line("  es_raw := SYS.UTL_ENCODE.BASE64_DECODE(SYS.UTL_RAW.CAST_TO_RAW(");
                for(int i=0;i<text.length();i+=2048)s.line("    '"+text.substring(i,Math.min(i+2048,text.length()))+"'"+(i+2048<text.length()?" ||":"));"));
                s.line("  SYS.DBMS_LOB.WRITEAPPEND("+bind+","+length+",es_raw);");
            }
            exception(s);blocks.add(s.bytes());
        }
    }
    private static String cleanup(boolean documents) {
        return """
  PROCEDURE es_free_blob(p IN OUT NOCOPY BLOB) IS BEGIN
    IF p IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(p)=1 THEN SYS.DBMS_LOB.FREETEMPORARY(p); END IF;
    IF p IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(p)=1 THEN RAISE_APPLICATION_ERROR(-20001,'ES_CLEANUP_FAILED'); END IF;
    p := NULL;
  END;
"""+(documents?"""
  PROCEDURE es_free_clob(p IN OUT NOCOPY CLOB) IS BEGIN
    IF p IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(p)=1 THEN SYS.DBMS_LOB.FREETEMPORARY(p); END IF;
    IF p IS NOT NULL AND SYS.DBMS_LOB.ISTEMPORARY(p)=1 THEN RAISE_APPLICATION_ERROR(-20001,'ES_CLEANUP_FAILED'); END IF;
    p := NULL;
  END;
  PROCEDURE es_free_document IS
    bad BOOLEAN := FALSE;
  BEGIN
    BEGIN es_free_blob(es_document_blob); EXCEPTION WHEN OTHERS THEN bad:=TRUE; END;
    BEGIN es_free_blob(es_roundtrip_blob); EXCEPTION WHEN OTHERS THEN bad:=TRUE; END;
    BEGIN es_free_clob(es_document_clob); EXCEPTION WHEN OTHERS THEN bad:=TRUE; END;
    IF bad THEN RAISE_APPLICATION_ERROR(-20001,'ES_CLEANUP_FAILED'); END IF;
  END;
""":"")+"""
  PROCEDURE es_cleanup IS
    bad BOOLEAN := FALSE;
  BEGIN
"""+(documents?"    BEGIN es_free_document; EXCEPTION WHEN OTHERS THEN bad:=TRUE; END;\n":"")+"""
    BEGIN es_free_blob(:es_original_blob); EXCEPTION WHEN OTHERS THEN bad:=TRUE; END;
    BEGIN es_free_blob(:es_target_blob); EXCEPTION WHEN OTHERS THEN bad:=TRUE; END;
    IF bad THEN RAISE_APPLICATION_ERROR(-20001,'ES_CLEANUP_FAILED'); END IF;
  END;
""";
    }
    private static void exception(Source s) {
        s.line("EXCEPTION WHEN OTHERS THEN\n  :es_program_digest := NULL;\n  BEGIN es_cleanup; EXCEPTION WHEN OTHERS THEN RAISE_APPLICATION_ERROR(-20001,'ES_CLEANUP_FAILED'); END;\n  RAISE_APPLICATION_ERROR(-20001,'ES_GUARDED_TRANSACTION_FAILED');\nEND;");
    }
    private static String text(String value){return "SYS.UTL_I18N.RAW_TO_CHAR(HEXTORAW('"+hex(value)+"'),'AL32UTF8')";}
    private static byte[] guard(PackageAdmission.Result.Accepted input,Stream original,Stream target) {
        var s=new Source();var t=input.payload().table();String table=id(t.schema())+"."+id(t.name());String where="owner="+text(t.schema())+" AND table_name="+text(t.name());
        var physical=(PackageData.PhysicalIdentity.Oracle)input.execution().destination().expectedPhysicalIdentity();
        s.line("DECLARE\n  es_count NUMBER;\n  es_document_blob BLOB;\n  es_roundtrip_blob BLOB;\n  es_document_clob CLOB;\n  es_observed CLOB;");s.add(cleanup(true));
        s.add("""
  PROCEDURE es_load(p_source BLOB,p_offset PLS_INTEGER,p_bytes PLS_INTEGER) IS
    d INTEGER := 1; b INTEGER := 1; language_context INTEGER := SYS.DBMS_LOB.DEFAULT_LANG_CTX; warning INTEGER;
  BEGIN
    es_free_document;
    SYS.DBMS_LOB.CREATETEMPORARY(es_document_blob,TRUE,SYS.DBMS_LOB.SESSION);
    SYS.DBMS_LOB.CREATETEMPORARY(es_document_clob,TRUE,SYS.DBMS_LOB.SESSION);
    SYS.DBMS_LOB.CREATETEMPORARY(es_roundtrip_blob,TRUE,SYS.DBMS_LOB.SESSION);
    SYS.DBMS_LOB.COPY(es_document_blob,p_source,p_bytes,1,p_offset);
    IF SYS.DBMS_LOB.GETLENGTH(es_document_blob)<>p_bytes THEN RAISE_APPLICATION_ERROR(-20001,'ES_SLICE_MISMATCH'); END IF;
    SYS.DBMS_LOB.CONVERTTOCLOB(es_document_clob,es_document_blob,p_bytes,d,b,NLS_CHARSET_ID('AL32UTF8'),language_context,warning);
    IF warning<>0 OR b<>p_bytes+1 OR d<>SYS.DBMS_LOB.GETLENGTH(es_document_clob)+1 THEN RAISE_APPLICATION_ERROR(-20001,'ES_CONVERSION_MISMATCH'); END IF;
    d:=1; b:=1; language_context:=SYS.DBMS_LOB.DEFAULT_LANG_CTX;
    SYS.DBMS_LOB.CONVERTTOBLOB(es_roundtrip_blob,es_document_clob,SYS.DBMS_LOB.LOBMAXSIZE,d,b,NLS_CHARSET_ID('AL32UTF8'),language_context,warning);
    IF warning<>0 OR b<>SYS.DBMS_LOB.GETLENGTH(es_document_clob)+1 OR d<>p_bytes+1 OR SYS.DBMS_LOB.GETLENGTH(es_roundtrip_blob)<>p_bytes OR SYS.DBMS_LOB.COMPARE(es_roundtrip_blob,es_document_blob)<>0 THEN RAISE_APPLICATION_ERROR(-20001,'ES_ROUNDTRIP_MISMATCH'); END IF;
  END;
BEGIN
  :es_program_digest := NULL;
""");
        check(s,":es_original_blob IS NULL OR :es_target_blob IS NULL OR SYS.DBMS_LOB.GETLENGTH(:es_original_blob)<>"+original.bytes().length+" OR SYS.DBMS_LOB.GETLENGTH(:es_target_blob)<>"+target.bytes().length+" OR LOWER(RAWTOHEX(SYS.DBMS_CRYPTO.HASH(:es_original_blob,SYS.DBMS_CRYPTO.HASH_SH256)))<>'"+PackageJson.sha256(original.bytes())+"' OR LOWER(RAWTOHEX(SYS.DBMS_CRYPTO.HASH(:es_target_blob,SYS.DBMS_CRYPTO.HASH_SH256)))<>'"+PackageJson.sha256(target.bytes())+"'","ES_AGGREGATE_MISMATCH");
        s.line("  LOCK TABLE "+table+" IN EXCLUSIVE MODE WAIT 30;");
        count(s,"SELECT COUNT(*) FROM sys.v_$instance WHERE version_full='23.26.3.0.0'",1,"ES_VERSION_MISMATCH");
        count(s,"SELECT COUNT(*) FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET' AND value='AL32UTF8'",1,"ES_ENCODING_MISMATCH");
        count(s,"SELECT COUNT(*) FROM nls_session_parameters WHERE parameter IN ('NLS_COMP','NLS_SORT') AND value='BINARY'",2,"ES_COLLATION_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.v_$database d CROSS JOIN sys.v_$containers c WHERE c.con_id=TO_NUMBER(SYS_CONTEXT('USERENV','CON_ID')) AND d.dbid="+physical.dbid()+" AND d.db_unique_name="+text(physical.dbUniqueName())+" AND c.con_id="+physical.conId()+" AND c.con_uid="+physical.conUid()+" AND c.name="+text(physical.conName())+" AND LOWER(RAWTOHEX(c.guid))='"+physical.pdbGuid().toLowerCase(java.util.Locale.ROOT)+"'",1,"ES_DESTINATION_MISMATCH");
        count(s,"SELECT COUNT(*) FROM sys.v_$option WHERE parameter IN ('Oracle Label Security','Oracle Database Vault') AND value='FALSE'",2,"ES_VISIBILITY_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_tables WHERE "+where+" AND partitioned='NO' AND iot_type IS NULL AND nested='NO' AND temporary='N' AND secondary='N' AND status='VALID' AND cluster_name IS NULL",1,"ES_TABLE_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_external_tables WHERE "+where,0,"ES_EXTERNAL_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_triggers WHERE table_owner="+text(t.schema())+" AND table_name="+text(t.name()),0,"ES_TRIGGER_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_policies WHERE object_owner="+text(t.schema())+" AND object_name="+text(t.name()),0,"ES_POLICY_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.redaction_policies WHERE object_owner="+text(t.schema())+" AND object_name="+text(t.name()),0,"ES_REDACTION_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_audit_policies WHERE object_schema="+text(t.schema())+" AND object_name="+text(t.name()),0,"ES_AUDIT_POLICY_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_mview_logs WHERE log_owner="+text(t.schema())+" AND master="+text(t.name()),0,"ES_MVIEW_LOG_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_flashback_archive_tables WHERE owner_name="+text(t.schema())+" AND table_name="+text(t.name()),0,"ES_FLASHBACK_ARCHIVE_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_tab_cols WHERE "+where+" AND (data_type_owner IS NOT NULL OR virtual_column<>'NO' OR identity_column<>'NO' OR hidden_column<>'NO' OR (data_type IN ('VARCHAR2','CHAR','CLOB') AND (collation IS NULL OR collation NOT IN ('USING_NLS_COMP','BINARY'))) OR data_type NOT IN ('VARCHAR2','CHAR','NUMBER','CLOB','BLOB','RAW','DATE','TIMESTAMP(6)','TIMESTAMP(6) WITH TIME ZONE','BINARY_FLOAT','BINARY_DOUBLE'))",0,"ES_COLUMN_EFFECT_UNSUPPORTED");
        String keyType=t.keyType()==PackageData.KeyType.TEXT?"data_type='VARCHAR2' AND char_used='C' AND char_length>=256":"data_type='NUMBER' AND data_precision=19 AND data_scale=0";
        count(s,"SELECT COUNT(*) FROM sys.dba_tab_cols WHERE "+where+" AND ((column_name="+text(t.keyColumn())+" AND nullable='N' AND "+keyType+") OR (column_name="+text(t.xmlColumn())+" AND data_type='CLOB'))",2,"ES_COLUMNS_MISMATCH");
        count(s,"SELECT COUNT(*) FROM sys.dba_constraints c WHERE c.owner="+text(t.schema())+" AND c.table_name="+text(t.name())+" AND (c.status<>'ENABLED' OR c.validated<>'VALIDATED' OR c.deferrable<>'NOT DEFERRABLE' OR (c.constraint_type NOT IN ('P','U') AND NOT(c.constraint_type='C' AND EXISTS(SELECT 1 FROM sys.dba_tab_cols a WHERE a.owner=c.owner AND a.table_name=c.table_name AND a.nullable='N' AND c.search_condition_vc='\"'||a.column_name||'\" IS NOT NULL'))))",0,"ES_CONSTRAINT_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_constraints c WHERE c.constraint_type='R' AND (c.r_owner,c.r_constraint_name) IN (SELECT owner,constraint_name FROM sys.dba_constraints WHERE "+where+")",0,"ES_REFERENCING_CONSTRAINT_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_constraints c WHERE c.owner="+text(t.schema())+" AND c.table_name="+text(t.name())+" AND c.constraint_type IN ('P','U') AND (SELECT COUNT(*) FROM sys.dba_cons_columns cc WHERE cc.owner=c.owner AND cc.constraint_name=c.constraint_name)=1 AND EXISTS(SELECT 1 FROM sys.dba_cons_columns cc WHERE cc.owner=c.owner AND cc.constraint_name=c.constraint_name AND cc.column_name="+text(t.keyColumn())+")",1,"ES_KEY_CONSTRAINT_MISMATCH");
        count(s,"SELECT COUNT(*) FROM sys.dba_indexes WHERE table_owner="+text(t.schema())+" AND table_name="+text(t.name())+" AND (index_type NOT IN ('NORMAL','LOB') OR status NOT IN ('VALID','N/A') OR partitioned<>'NO' OR temporary<>'N')",0,"ES_INDEX_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM sys.dba_ind_expressions WHERE table_owner="+text(t.schema())+" AND table_name="+text(t.name()),0,"ES_INDEX_EXPRESSION_UNSUPPORTED");
        count(s,"SELECT COUNT(*) FROM "+table,input.payload().records().size(),"ES_MEMBERSHIP_MISMATCH");
        for(int i=0;i<input.payload().records().size();i++)compare(s,t,input.payload().records().get(i),original,i,":es_original_blob","ES_ORIGINAL_MISMATCH");
        for(int i=0;i<input.payload().records().size();i++) {
            var d=input.payload().records().get(i);if(d.originalHex().equals(d.targetHex()))continue;
            load(s,target,i,":es_target_blob");s.line("  UPDATE "+table+" SET "+id(t.xmlColumn())+"=es_document_clob WHERE "+key(t,d.key())+";");check(s,"SQL%ROWCOUNT<>1","ES_ROW_COUNT_MISMATCH");s.line("  es_free_document;");
        }
        count(s,"SELECT COUNT(*) FROM "+table,input.payload().records().size(),"ES_MEMBERSHIP_MISMATCH");
        for(int i=0;i<input.payload().records().size();i++)compare(s,t,input.payload().records().get(i),target,i,":es_target_blob","ES_TARGET_MISMATCH");
        s.line("  es_cleanup;\n  :es_program_digest := '"+input.programDigest()+"';");exception(s);return s.bytes();
    }
    private static void check(Source s,String predicate,String code){s.line("  IF "+predicate+" THEN RAISE_APPLICATION_ERROR(-20001,'"+code+"'); END IF;");}
    private static void count(Source s,String query,int expected,String code){s.line("  "+query.replaceFirst("SELECT COUNT\\(\\*\\)","SELECT COUNT(*) INTO es_count")+";");check(s,"es_count<>"+expected,code);}
    private static String key(PackageData.Table table,PackageData.Key key){return key.type()==PackageData.KeyType.INT64?id(table.keyColumn())+"="+key.value():"SYS.UTL_RAW.CAST_TO_RAW("+id(table.keyColumn())+")=HEXTORAW('"+hex(key.value())+"')";}
    private static void load(Source s,Stream stream,int i,String bind){s.line("  es_load("+bind+","+stream.offsets().get(i)+","+stream.lengths().get(i)+");");}
    private static void compare(Source s,PackageData.Table t,PackageData.Document d,Stream stream,int i,String bind,String code){
        String table=id(t.schema())+"."+id(t.name());load(s,stream,i,bind);count(s,"SELECT COUNT(*) FROM "+table+" WHERE "+key(t,d.key()),1,code);
        s.line("  SELECT "+id(t.xmlColumn())+" INTO es_observed FROM "+table+" WHERE "+key(t,d.key())+";");
        check(s,"es_observed IS NULL OR SYS.DBMS_LOB.GETLENGTH(es_observed)<>SYS.DBMS_LOB.GETLENGTH(es_document_clob) OR SYS.DBMS_LOB.COMPARE(es_observed,es_document_clob)<>0",code);s.line("  es_free_document;");
    }
}
