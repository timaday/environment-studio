package studio.environment.server.export;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class TransactionTemplatesTest {
    static PackageAdmission.Result.Accepted input() throws Exception {
        var mapper=JsonMapper.builder().build();
        var execution=mapper.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json"))).get("execution");
        return assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(execution),Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json"))));
    }
    @Test void generatesSinglePostgresBlockWithIndependentBaselineAndNoTransactionControl() throws Exception {
        var candidate=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(input()));
        String sql=new String(candidate.bytes(),StandardCharsets.UTF_8);
        assertTrue(sql.startsWith("DO $es$\nDECLARE\n"));
        assertTrue(sql.contains("LOCK TABLE \"MockSchema\".\"CanvasRows\" IN ACCESS EXCLUSIVE MODE;"));
        assertTrue(sql.contains("ES_ORIGINAL_MISMATCH"));
        assertTrue(sql.contains("ES_TARGET_MISMATCH"));
        assertFalse(sql.contains("COMMIT"));
        assertFalse(sql.contains("ROLLBACK"));
        var fragments=JsonMapper.builder().build().readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-transaction-v1/expected-sql-fragments.json"))).get("postgresql");for(var fragment:fragments)assertTrue(sql.contains(fragment.asString()));
    }
    @Test void oracleCandidateHasKnownBoundedLoadersAndCompleteRoundTripGuards() throws Exception {
        var mapper=JsonMapper.builder().build();var e=(tools.jackson.databind.node.ObjectNode)mapper.readTree(input().canonicalExecution());var p=(tools.jackson.databind.node.ObjectNode)mapper.readTree(input().canonicalPayload());
        e.put("engine","oracle").put("storage","clob").put("serverVersion","23.26.3.0.0").put("templateVersion","oracle-clob-v1");((tools.jackson.databind.node.ObjectNode)e.get("client")).put("family","sqlplus").put("version","23.26.3.0.0");
        var identity=((tools.jackson.databind.node.ObjectNode)e.get("destination")).putObject("expectedPhysicalIdentity");identity.put("dbid","42").put("dbUniqueName","MOCK").put("conId","3").put("conUid","43").put("conName","MOCKPDB").put("pdbGuid","a".repeat(32));p.put("engine","oracle").put("storage","clob");
        var accepted=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(e),mapper.writeValueAsBytes(p)));
        var candidate=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(accepted));
        assertEquals(4,candidate.blocks().size());String sql=new String(candidate.bytes(),StandardCharsets.US_ASCII);
        assertTrue(sql.contains("UTL_ENCODE.BASE64_DECODE"));assertTrue(sql.contains("DBMS_LOB.CONVERTTOCLOB"));assertTrue(sql.contains("DBMS_LOB.CONVERTTOBLOB"));assertTrue(sql.contains("ES_ROUNDTRIP_MISMATCH"));
        assertFalse(sql.lines().anyMatch(l->l.equals("/")));assertFalse(candidate.qualified());
        var fragments=mapper.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-transaction-v1/expected-sql-fragments.json"))).get("oracle");for(var fragment:fragments)assertTrue(sql.contains(fragment.asString()));
        int end=0;for(var block:candidate.blocks()){assertEquals(end==0?0:end+1,block.offset());String text=new String(candidate.bytes(),block.offset(),block.length(),StandardCharsets.US_ASCII);assertTrue(text.endsWith("END;\n"));assertTrue(text.lines().allMatch(l->l.length()<=2499));end=block.offset()+block.length();}assertEquals(candidate.bytes().length,end);
        var table=(tools.jackson.databind.node.ObjectNode)p.get("table");table.put("schema","S".repeat(128)).put("name","T".repeat(128)).put("keyColumn","K".repeat(128)).put("xmlColumn","X".repeat(128));((tools.jackson.databind.node.ObjectNode)p.get("records").get(0).get("key")).put("value","𐀀".repeat(256));
        var longInput=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(e),mapper.writeValueAsBytes(p)));var longCandidate=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(longInput));assertTrue(new String(longCandidate.bytes(),StandardCharsets.US_ASCII).lines().allMatch(l->l.length()<=2499));

    }
    @Test void candidatesHaveExactImmutableBoundariesAndRefuseDeniedDocuments() throws Exception {
        var input=input();var result=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(input));byte[] original=result.bytes();byte[] altered=result.bytes();altered[0]=0;assertArrayEquals(original,result.bytes());assertThrows(UnsupportedOperationException.class,()->result.blocks().clear());assertFalse(result.qualified());assertFalse(result.toString().contains("MockSchema"));
        var mapper=JsonMapper.builder().build();var e=(tools.jackson.databind.node.ObjectNode)mapper.readTree(input.canonicalExecution());((tools.jackson.databind.node.ObjectNode)e.get("exportPolicies").get(0)).put("content","deny");
        assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(mapper.writeValueAsBytes(e),input.canonicalPayload()));assertEquals("INVALID_INPUT",assertInstanceOf(TransactionTemplates.Result.Rejected.class,new TransactionTemplates().generate(null)).code());
    }
    @Test void fullPostgresScopeUsesEachOriginalAndTargetLiteralOnceWithinSqlBudget() throws Exception {
        var mapper=JsonMapper.builder().build();var e=(tools.jackson.databind.node.ObjectNode)mapper.readTree(input().canonicalExecution());var p=(tools.jackson.databind.node.ObjectNode)mapper.readTree(input().canonicalPayload());var policies=e.putArray("exportPolicies");var records=p.putArray("records");
        String source="<x>"+"a".repeat(1_048_569)+"</x>";String target="<x>"+"b".repeat(1_048_569)+"</x>";var hex=java.util.HexFormat.of();String before=hex.formatHex(source.getBytes(StandardCharsets.UTF_8)),after=hex.formatHex(target.getBytes(StandardCharsets.UTF_8));
        for(int i=0;i<16;i++){String id=String.format(java.util.Locale.ROOT,"d%02d",i);policies.addObject().put("documentId",id).put("content","protected-self-contained");var d=records.addObject().put("documentId",id).put("originalHex",before).put("targetHex",after);d.putObject("key").put("type","text").put("value",id);}
        var admitted=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(e),mapper.writeValueAsBytes(p)));var candidate=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(admitted));assertTrue(candidate.bytes().length<80*1024*1024);assertTrue(candidate.bytes().length>64*1024*1024);assertEquals(1,candidate.blocks().size());
    }
    @Test void noopIncludesFullGuardsAndInt64MinimumNeverInterpolatesText() throws Exception {
        var mapper=JsonMapper.builder().build();var input=input();var p=(tools.jackson.databind.node.ObjectNode)mapper.readTree(input.canonicalPayload());var record=(tools.jackson.databind.node.ObjectNode)p.get("records").get(0);record.put("targetHex",record.get("originalHex").asString());((tools.jackson.databind.node.ObjectNode)p.get("table")).put("keyType","int64");((tools.jackson.databind.node.ObjectNode)record.get("key")).put("type","int64").put("value","-9223372036854775808");
        var accepted=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(input.canonicalExecution(),mapper.writeValueAsBytes(p)));var a=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(accepted));var b=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(accepted));assertArrayEquals(a.bytes(),b.bytes());String sql=new String(a.bytes(),StandardCharsets.US_ASCII);assertTrue(sql.contains("='-9223372036854775808'::bigint"));assertFalse(sql.contains("\n  UPDATE "));assertTrue(sql.contains("ES_ORIGINAL_MISMATCH"));assertTrue(sql.contains("ES_TARGET_MISMATCH"));
    }
}
