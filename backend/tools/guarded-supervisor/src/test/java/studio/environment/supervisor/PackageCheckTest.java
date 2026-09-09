package studio.environment.supervisor;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.server.export.*;
import static org.junit.jupiter.api.Assertions.*;
class PackageCheckTest {
    static byte[] archive(boolean alteredProgram)throws Exception{return archive(alteredProgram,false);}
    static byte[] archive(boolean alteredProgram,boolean oracle)throws Exception{return archive(alteredProgram,oracle,false);}
    static byte[] archive(boolean alteredProgram,boolean oracle,boolean tls)throws Exception{
        var mapper=JsonMapper.builder().build();var manifest=(ObjectNode)mapper.readTree(Files.readAllBytes(Path.of("../../../fixtures/guarded-package-v1/manifest.json")));byte[] payload=Files.readAllBytes(Path.of("../../../fixtures/guarded-package-v1/payload.json"));
        if(tls)((ObjectNode)manifest.get("execution").get("destination")).put("transport","verified-tls").put("transportIdentity","a".repeat(64));
        if(oracle){var e=(ObjectNode)manifest.get("execution");e.put("engine","oracle").put("storage","clob").put("serverVersion","23.26.3.0.0").put("templateVersion","oracle-clob-v1");((ObjectNode)e.get("client")).put("family","sqlplus").put("version","23.26.3.0.0");var identity=((ObjectNode)e.get("destination")).putObject("expectedPhysicalIdentity");identity.put("dbid","1").put("dbUniqueName","MOCK").put("conId","3").put("conUid","4").put("conName","MOCKPDB").put("pdbGuid","a".repeat(32));var p=(ObjectNode)mapper.readTree(payload);p.put("engine","oracle").put("storage","clob");payload=mapper.writeValueAsBytes(p);}
        var inputs=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(manifest.get("execution")),payload));var candidate=assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(inputs));byte[] sql=candidate.bytes();if(alteredProgram)sql=(new String(sql,StandardCharsets.US_ASCII)+"COMMIT;\n").getBytes(StandardCharsets.US_ASCII);
        manifest.put("programDigest",inputs.programDigest());var members=(ObjectNode)manifest.get("members");for(var entry:Map.of("payload.json",payload,"transaction.sql",sql,"instructions.txt",PackageInstructions.bytes()).entrySet()){var m=members.putObject(entry.getKey());m.put("bytes",entry.getValue().length);m.put("sha256",AdmittedFiles.sha256(entry.getValue()));}
        var out=new ByteArrayOutputStream();assertInstanceOf(StrictPackageZip.WriteResult.Written.class,new StrictPackageZip().write(List.of(new StrictPackageZip.Member("manifest.json",mapper.writeValueAsBytes(manifest)),new StrictPackageZip.Member("payload.json",payload),new StrictPackageZip.Member("transaction.sql",sql),new StrictPackageZip.Member("instructions.txt",PackageInstructions.bytes())),out));return out.toByteArray();
    }
    @Test void completeArchiveAndExactRegenerationAreBothRequired()throws Exception{
        byte[] correct=archive(false);var regenerated=PackageCheck.read(correct,AdmittedFiles.sha256(correct));assertEquals("dabb86943f35e26b373e11c1711da368937241ce43688c43ba952ae654999aa8",regenerated.inputs().programDigest());assertFalse(regenerated.program().qualified());
        assertEquals("ARCHIVE_HASH_MISMATCH",assertThrows(Refusal.class,()->PackageCheck.read(correct,"0".repeat(64))).code);
        byte[] altered=archive(true);assertEquals("PROGRAM_MISMATCH",assertThrows(Refusal.class,()->PackageCheck.read(altered,AdmittedFiles.sha256(altered))).code);
        byte[] truncated=Arrays.copyOf(correct,correct.length-1);assertThrows(Refusal.class,()->PackageCheck.read(truncated,AdmittedFiles.sha256(truncated)));
    }
}
