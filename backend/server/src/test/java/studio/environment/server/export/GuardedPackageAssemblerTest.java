package studio.environment.server.export;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.ObservationPort.Cancellation;
import tools.jackson.databind.json.JsonMapper;
class GuardedPackageAssemblerTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static PackageAdmission.Result.Accepted input() throws Exception {
        var root=Path.of("../../fixtures/guarded-package-v1");
        var manifest=JSON.readTree(Files.readAllBytes(root.resolve("manifest.json")));
        return assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(
                JSON.writeValueAsBytes(manifest.get("execution")),Files.readAllBytes(root.resolve("payload.json"))));
    }
    @Test void derivesCompleteDeterministicArchiveFromCanonicalInputsOnly() throws Exception {
        var input=input(); var first=new ByteArrayOutputStream(); var assembler=new GuardedPackageAssembler();
        var candidate=assertInstanceOf(GuardedPackageAssembler.Result.Candidate.class,assembler.write(input,first,new Cancellation()));
        assertFalse(candidate.qualified());assertEquals(first.size(),candidate.bytes());assertEquals(hash(first.toByteArray()),candidate.sha256());
        var members=new LinkedHashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(first.toByteArray()))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())members.put(entry.getName(),zip.readAllBytes());
        }
        assertEquals(List.of("manifest.json","payload.json","transaction.sql","instructions.txt"),new ArrayList<>(members.keySet()));
        assertArrayEquals(input.canonicalPayload(),members.get("payload.json"));
        assertArrayEquals(PackageInstructions.bytes(),members.get("instructions.txt"));
        var manifest=JSON.readTree(members.get("manifest.json"));
        for(String name:List.of("payload.json","transaction.sql","instructions.txt")) {
            assertEquals(members.get(name).length,manifest.get("members").get(name).get("bytes").intValue());
            assertEquals(hash(members.get(name)),manifest.get("members").get(name).get("sha256").asString());
        }
        var canonical=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(input.canonicalExecution(),input.canonicalPayload()));
        assertNotEquals(input.programDigest(),canonical.programDigest());
        assertEquals(canonical.programDigest(),manifest.get("programDigest").asString());
        var oracle=JSON.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-writer-v1/canonical-digest.json")));
        assertEquals(oracle.get("programDigest").asString(),manifest.get("programDigest").asString());
        assertArrayEquals(assertInstanceOf(TransactionTemplates.Result.Candidate.class,new TransactionTemplates().generate(canonical)).bytes(),members.get("transaction.sql"));
        assertInstanceOf(GuardedPackageInspector.Result.Unqualified.class,new GuardedPackageInspector().inspect(first.toByteArray()));
        var second=new ByteArrayOutputStream();assertInstanceOf(GuardedPackageAssembler.Result.Candidate.class,assembler.write(canonical,second,new Cancellation()));
        assertArrayEquals(first.toByteArray(),second.toByteArray());
    }
    @Test void cancellationAndInputRefusedBeforeAssemblyEmitNothing() throws Exception {
        var out=new ByteArrayOutputStream();var cancelled=new Cancellation();cancelled.cancel();
        assertEquals(new GuardedPackageAssembler.Result.Rejected("CANCELLED"),new GuardedPackageAssembler().write(input(),out,cancelled));assertEquals(0,out.size());
        var execution=(tools.jackson.databind.node.ObjectNode)JSON.readTree(input().canonicalExecution());
        ((tools.jackson.databind.node.ObjectNode)execution.get("exportPolicies").get(0)).put("content","deny");
        assertEquals(new PackageAdmission.Result.Rejected("SCHEMA_VIOLATION"),new PackageAdmission().read(JSON.writeValueAsBytes(execution),input().canonicalPayload()));
        assertEquals(new GuardedPackageAssembler.Result.Rejected("INVALID_INPUT"),new GuardedPackageAssembler().write(null,out,new Cancellation()));assertEquals(0,out.size());
    }
    @Test void partialOutputFailureAndCancellationNeverReturnCandidateOrCloseCaller() throws Exception {
        for(boolean cancel:List.of(false,true)) {
            var cancellation=new Cancellation();var bytes=new ByteArrayOutputStream();
            var output=new OutputStream() {
                @Override public void write(int value)throws IOException {bytes.write(value);if(cancel)cancellation.cancel();else throw new IOException("Invented failure");}
                @Override public void close(){fail("Caller stream closed");}
                @Override public void flush(){fail("Caller stream flushed");}
            };
            assertEquals(new GuardedPackageAssembler.Result.Rejected(cancel?"CANCELLED":"OUTPUT_FAILURE"),new GuardedPackageAssembler().write(input(),output,cancellation));
            assertTrue(bytes.size()>0);
        }
    }
    private static String hash(byte[] bytes)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
