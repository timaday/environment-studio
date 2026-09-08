package studio.environment.server.export;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class PackageAdmissionTest {
    @Test void independentlyMatchesFrozenExecutionFramingOracle() throws Exception {
        var mapper = JsonMapper.builder().build();
        var manifest = mapper.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json")));
        byte[] payload = Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json"));
        var result = assertInstanceOf(PackageAdmission.Result.Accepted.class, new PackageAdmission().read(mapper.writeValueAsBytes(manifest.get("execution")), payload));
        assertEquals("9d5d0598978013c117393121f97ae687e429602fc46f25a5ad51a501dd2c90b8", result.payloadDigest());
        assertEquals("5b521d695676f345a9e8ec7eceeb09ea5c99cf83b8134179aa89af4b11b21fcc", result.programDigest());
    }
    private static byte[] payload() throws Exception {return Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json"));}
    private static tools.jackson.databind.node.ObjectNode execution() throws Exception {return (tools.jackson.databind.node.ObjectNode)JsonMapper.builder().build().readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json"))).get("execution").deepCopy();}
    @Test void rejectsDuplicateKeysNoncanonicalNumbersMalformedUnicodeAndCrossEngine() throws Exception {
        var mapper=JsonMapper.builder().build();byte[] good=mapper.writeValueAsBytes(execution());
        for(String text:java.util.List.of("{\"engine\":\"postgresql\",\"engine\":\"postgresql\"}","{\"port\":1.0}","{\"port\":1e3}","{\"port\":-0}","{\"x\":\"\\ud800\"}","{} {}"))assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),payload()));
        var changed=execution();changed.put("engine","oracle");assertEquals("SCHEMA_VIOLATION",assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(mapper.writeValueAsBytes(changed),payload())).code());
        assertEquals("INVALID_UTF8",assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(new byte[]{(byte)0xc0,(byte)0xaf},payload())).code());
        var node=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload());node.put("extra","value-canary");assertEquals("SCHEMA_VIOLATION",assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(good,mapper.writeValueAsBytes(node))).code());
    }
    @Test void rejectsInvalidHexUtf8XmlKeysDuplicateInventoryAndPolicies() throws Exception {
        var mapper=JsonMapper.builder().build();byte[] good=mapper.writeValueAsBytes(execution());
        for(String hex:java.util.List.of("a","c0af","eda080","3c78","00")){
            var node=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload());((tools.jackson.databind.node.ObjectNode)node.get("records").get(0)).put("targetHex",hex);
            assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(good,mapper.writeValueAsBytes(node)));
        }
        for(String number:java.util.List.of("9223372036854775808","-9223372036854775809","01","-0")){
            var node=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload());((tools.jackson.databind.node.ObjectNode)node.get("table")).put("keyType","int64");var key=(tools.jackson.databind.node.ObjectNode)node.get("records").get(0).get("key");key.put("type","int64");key.put("value",number);
            assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(good,mapper.writeValueAsBytes(node)));
        }
        var node=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload());((tools.jackson.databind.node.ArrayNode)node.get("records")).add(node.get("records").get(0).deepCopy());
        assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(good,mapper.writeValueAsBytes(node)));
        var wrong=execution();((tools.jackson.databind.node.ObjectNode)wrong.get("exportPolicies").get(0)).put("documentId","missing");assertEquals("POLICY_INVENTORY_MISMATCH",assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(mapper.writeValueAsBytes(wrong),payload())).code());
    }
    @Test void canonicalJsonIsStableExactUtf8AndAcceptedCollectionsAreImmutable() throws Exception {
        var mapper=JsonMapper.builder().build();var execution=execution();var result=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(execution),payload()));
        String json=new String(result.canonicalExecution(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(json.contains("independently-invented-π"));assertFalse(json.contains("\n"));
        var again=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(result.canonicalExecution(),result.canonicalPayload()));assertArrayEquals(result.canonicalPayload(),again.canonicalPayload());assertArrayEquals(result.canonicalExecution(),again.canonicalExecution());
        assertThrows(UnsupportedOperationException.class,()->result.payload().records().clear());assertThrows(UnsupportedOperationException.class,()->result.execution().versions().mechanisms().clear());
        assertFalse(result.toString().contains("MockDb"));assertFalse(result.payload().records().getFirst().toString().contains("3c7469"));
    }
    @Test void profileDigestOrderAndWrappedMalformedUtf8Refuse()throws Exception {
        var mapper=JsonMapper.builder().build();var execution=execution();execution.putArray("profilePublicationDigests").add("b".repeat(64)).add("a".repeat(64));
        assertEquals("NONCANONICAL_ORDER",assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(mapper.writeValueAsBytes(execution),payload())).code());
        var node=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload());((tools.jackson.databind.node.ObjectNode)node.get("records").get(0)).put("targetHex","3c7820613d22c0af222f3e");
        assertEquals("INVALID_UTF8",assertInstanceOf(PackageAdmission.Result.Rejected.class,new PackageAdmission().read(mapper.writeValueAsBytes(execution()),mapper.writeValueAsBytes(node))).code());
    }
    @Test void acceptsFullIndependentSixteenMiBOriginalAndTargetScopes()throws Exception {
        var mapper=JsonMapper.builder().build();var execution=execution();var policies=execution.putArray("exportPolicies");var payload=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload());var records=payload.putArray("records");
        String source="<x>"+"a".repeat(1_048_569)+"</x>";assertEquals(1_048_576,source.length());String hex=java.util.HexFormat.of().formatHex(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for(int i=0;i<16;i++){String id=String.format(java.util.Locale.ROOT,"doc-%02d",i);policies.addObject().put("documentId",id).put("content","protected-self-contained");var record=records.addObject();record.put("documentId",id);record.putObject("key").put("type","text").put("value",Integer.toString(i));record.put("originalHex",hex);record.put("targetHex",hex);}
        var result=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(execution),mapper.writeValueAsBytes(payload)));
        assertEquals(16_777_216,result.counts().originalBytes());assertEquals(16_777_216,result.counts().targetBytes());assertEquals(0,result.counts().changedRecords());
    }
    @Test void canonicalBytesAndDerivedDigestMatchIndependentPythonOracle()throws Exception {
        var mapper=JsonMapper.builder().build();var result=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(mapper.writeValueAsBytes(execution()),payload()));
        var root=Path.of("../../fixtures/guarded-writer-v1");assertArrayEquals(Files.readAllBytes(root.resolve("canonical-execution.json")),result.canonicalExecution());assertArrayEquals(Files.readAllBytes(root.resolve("canonical-payload.json")),result.canonicalPayload());
        var canonical=assertInstanceOf(PackageAdmission.Result.Accepted.class,new PackageAdmission().read(result.canonicalExecution(),result.canonicalPayload()));var expected=mapper.readTree(Files.readAllBytes(root.resolve("canonical-digest.json")));
        assertEquals(expected.get("payloadDigest").asString(),canonical.payloadDigest());assertEquals(expected.get("programDigest").asString(),canonical.programDigest());
    }
}
