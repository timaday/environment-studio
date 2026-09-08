package studio.environment.server.export;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

class GuardedPackageInspectorTest {
    private static final byte[] SQL="Unqualified invented shape text; never execute.\n".getBytes(StandardCharsets.UTF_8);
    static byte[] archive(java.util.function.Consumer<ObjectNode> mutation)throws Exception {
        var mapper=JsonMapper.builder().build();var manifest=(ObjectNode)mapper.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json")));
        byte[] payload=Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json"));
        var members=(ObjectNode)manifest.get("members");
        for(var entry:java.util.Map.of("transaction.sql",SQL,"instructions.txt",PackageInstructions.bytes()).entrySet()){
            var member=members.putObject(entry.getKey());member.put("bytes",entry.getValue().length);member.put("sha256",PackageJson.sha256(entry.getValue()));
        }
        mutation.accept(manifest);var output=new ByteArrayOutputStream();
        assertInstanceOf(StrictPackageZip.WriteResult.Written.class,new StrictPackageZip().write(List.of(new StrictPackageZip.Member("manifest.json",mapper.writeValueAsBytes(manifest)),new StrictPackageZip.Member("payload.json",payload),new StrictPackageZip.Member("transaction.sql",SQL),new StrictPackageZip.Member("instructions.txt",PackageInstructions.bytes())),output));return output.toByteArray();
    }
    @Test void verifiesWholeContainerMetadataButNeverClaimsTemplateOrGenerationAuthority()throws Exception {
        var inspector=new GuardedPackageInspector();var result=assertInstanceOf(GuardedPackageInspector.Result.Unqualified.class,inspector.inspect(archive(m->{})));
        assertEquals("TEMPLATE_QUALIFICATION_UNAVAILABLE",result.code());assertFalse(inspector.generationAvailable());assertEquals(1,result.counts().changedRecords());assertEquals(19,result.counts().originalBytes());
        assertEquals("5b521d695676f345a9e8ec7eceeb09ea5c99cf83b8134179aa89af4b11b21fcc",result.programDigest());assertFalse(result.toString().contains("MockDb"));
    }
    @Test void manifestMemberProgramAndCountTamperingRefusesDespiteValidZipCrc()throws Exception {
        var inspector=new GuardedPackageInspector();
        assertEquals("PROGRAM_DIGEST_MISMATCH",assertInstanceOf(GuardedPackageInspector.Result.Rejected.class,inspector.inspect(archive(m->m.put("programDigest","0".repeat(64))))).code());
        assertEquals("COUNT_MISMATCH",assertInstanceOf(GuardedPackageInspector.Result.Rejected.class,inspector.inspect(archive(m->((ObjectNode)m.get("counts")).put("changedRecords",0)))).code());
        assertEquals("MEMBER_INTEGRITY_MISMATCH",assertInstanceOf(GuardedPackageInspector.Result.Rejected.class,inspector.inspect(archive(m->((ObjectNode)m.get("members").get("payload.json")).put("sha256","0".repeat(64))))).code());
        assertEquals("MEMBER_INTEGRITY_MISMATCH",assertInstanceOf(GuardedPackageInspector.Result.Rejected.class,inspector.inspect(archive(m->((ObjectNode)m.get("members").get("transaction.sql")).put("bytes",1)))).code());
    }
}
