package studio.environment.server.export;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.server.definition.DefinitionBytesCompiler;
import studio.environment.server.definition.NativeV3DefinitionBytesCompiler;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Reviewed independently invented fixtures; explicit compiler witness is not publication. */
class V3PackageAdmissionTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final PackageAdmission admission = new PackageAdmission();

    @Test void exactV3PinsReachUnqualifiedTemplateWithoutChangingPayloadOrEnablingPublication() throws Exception {
        var seed = seed();
        var result = assertInstanceOf(PackageAdmission.Result.Accepted.class,
                admission.readPinnedV3(seed.definition(), "mock-pg", bytes(seed.execution()), bytes(seed.payload())));
        assertEquals(8, result.execution().versions().mechanisms().size());
        assertEquals(seed.payload(), json.readTree(result.canonicalPayload()));
        var sql = assertInstanceOf(TransactionTemplates.Result.Candidate.class, new TransactionTemplates().generate(result));
        assertFalse(sql.qualified());
        assertFalse(new GuardedPackageInspector().generationAvailable());
        assertArrayEquals(sql.bytes(), assertInstanceOf(TransactionTemplates.Result.Candidate.class,
                new TransactionTemplates().generate(result)).bytes());
        assertFalse(result.toString().contains("mock-pg"));
    }

    @Test void exactDefinitionAndBindingPinsCannotBeSubstituted() throws Exception {
        for (String field : java.util.List.of("logicalDigest", "bindingDigest", "bindingId")) {
            var seed = seed();
            seed.execution().put(field, field.equals("bindingId") ? "another-binding" : "0".repeat(64));
            if (field.equals("bindingId")) seed.payload().put("bindingId", "another-binding");
            assertEquals("DEFINITION_BINDING_MISMATCH", refused(seed), field);
        }
        var seed = seed();
        assertEquals("UNKNOWN_BINDING", assertInstanceOf(PackageAdmission.Result.Rejected.class,
                admission.readPinnedV3(seed.definition(), "missing", bytes(seed.execution()), bytes(seed.payload()))).code());
        assertEquals("UNSUPPORTED_MECHANISM", assertInstanceOf(PackageAdmission.Result.Rejected.class,
                admission.readPinnedV3(null, "mock-pg", bytes(seed.execution()), bytes(seed.payload()))).code());
    }

    @Test void mixedFamiliesAndMissingOrUnsupportedDependenciesRefuse() throws Exception {
        for (String key : java.util.List.of("native-compiler-v2", "plan-validation-v1", "unknown-mechanism")) {
            var seed = seed();
            ((ObjectNode) seed.execution().get("mechanisms")).put(key, key.equals("native-compiler-v2") ? "2" : "1");
            assertEquals("SCHEMA_VIOLATION", refused(seed), key);
        }
        for (String key : java.util.List.of("native-compiler-v3", "derived-graph-v1", "plan-validation-v3",
                "xml-path-v1", "xml-span-v1", "generic-graph-v1", "structural-target-v1")) {
            var seed = seed();
            ((ObjectNode) seed.execution().get("mechanisms")).remove(key);
            assertEquals("SCHEMA_VIOLATION", refused(seed), key);
        }
        var seed = seed();
        ((ObjectNode) seed.execution().get("mechanisms")).remove("xml-child-property-v1");
        assertEquals("MECHANISM_MISMATCH", refused(seed));
        seed = seed();
        ((ObjectNode) seed.execution().get("mechanisms")).put("native-compiler-v3", "2");
        assertEquals("SCHEMA_VIOLATION", refused(seed));
    }

    @Test void checkedDefinitionDependencyMismatchCannotBeSmuggledThroughTheWitness() throws Exception {
        var seed = seed(); var checked = seed.definition().checked();
        var wrong = new NativeCompilationResult.ReadyToPublish(new NativeCompilationResult.Checked(
                checked.definition(), checked.logicalDigest(), checked.bindingDigests(), java.util.Map.of()));
        assertEquals("UNSUPPORTED_MECHANISM", assertInstanceOf(PackageAdmission.Result.Rejected.class,
                admission.readPinnedV3(wrong, "mock-pg", bytes(seed.execution()), bytes(seed.payload()))).code());
    }

    @Test void directBindingDoesNotInheritAnotherBindingsChildDependency() throws Exception {
        var seed = seed();
        var oracle = json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v3/expected-digests.json")));
        seed.execution().put("bindingId", "mock-oracle")
                .put("bindingDigest", oracle.get("bindingDigests").get("mock-oracle").asString());
        seed.payload().put("bindingId", "mock-oracle");
        seed.execution().put("engine", "oracle").put("storage", "clob")
                .put("serverVersion", "23.26.3.0.0").put("templateVersion", "oracle-clob-v1");
        ((ObjectNode) seed.execution().get("client")).put("family", "sqlplus").put("version", "23.26.3.0.0");
        ((ObjectNode) seed.execution().get("destination")).putObject("expectedPhysicalIdentity")
                .put("dbid", "42").put("dbUniqueName", "MOCK").put("conId", "3").put("conUid", "43")
                .put("conName", "MOCKPDB").put("pdbGuid", "a".repeat(32));
        seed.payload().put("engine", "oracle").put("storage", "clob");
        var mechanisms = (ObjectNode) seed.execution().get("mechanisms");
        assertEquals("MECHANISM_MISMATCH", assertInstanceOf(PackageAdmission.Result.Rejected.class,
                admission.readPinnedV3(seed.definition(), "mock-oracle", bytes(seed.execution()), bytes(seed.payload()))).code());
        mechanisms.remove("xml-child-property-v1");
        assertInstanceOf(PackageAdmission.Result.Accepted.class,
                admission.readPinnedV3(seed.definition(), "mock-oracle", bytes(seed.execution()), bytes(seed.payload())));
    }

    @Test void matchingExecutionAndPayloadCannotMislabelTheSelectedBindingsEngine() throws Exception {
        var seed = seed();
        seed.execution().put("bindingId", "mock-oracle")
                .put("bindingDigest", seed.definition().checked().bindingDigests().get("mock-oracle"));
        seed.payload().put("bindingId", "mock-oracle");
        ((ObjectNode) seed.execution().get("mechanisms")).remove("xml-child-property-v1");
        assertEquals("DEFINITION_BINDING_MISMATCH", assertInstanceOf(PackageAdmission.Result.Rejected.class,
                admission.readPinnedV3(seed.definition(), "mock-oracle", bytes(seed.execution()), bytes(seed.payload()))).code());
    }

    @Test void v2MetadataCannotBeUsedForV3DefinitionAndConversely() throws Exception {
        var seed = seed();
        var manifest = json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json")));
        seed.execution().set("mechanisms", manifest.get("execution").get("mechanisms").deepCopy());
        assertEquals("MECHANISM_MISMATCH", refused(seed));
        var v2 = assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,
                new studio.environment.server.definition.NativeDefinitionBytesCompiler().compile(
                        Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")), DefinitionBytesCompiler.Format.JSON));
        seed = seed();
        seed.execution().put("logicalDigest", v2.checked().logicalDigest())
                .put("bindingDigest", v2.checked().bindingDigests().get("mock-pg"));
        assertEquals("MECHANISM_MISMATCH", assertInstanceOf(PackageAdmission.Result.Rejected.class,
                admission.readPinned(v2, "mock-pg", bytes(seed.execution()), bytes(seed.payload()))).code());
    }

    @Test void v3PinsDoNotBypassPayloadFidelityOrContentPermission() throws Exception {
        var seed = seed();
        ((ObjectNode) seed.payload().get("records").get(0)).put("targetHex", "3c783e"); // Incomplete invented XML.
        assertEquals("INVALID_XML", refused(seed));
        seed = seed();
        ((ObjectNode) seed.execution().get("exportPolicies").get(0)).put("content", "deny");
        assertEquals("SCHEMA_VIOLATION", refused(seed));
    }

    private String refused(Seed seed) {
        return assertInstanceOf(PackageAdmission.Result.Rejected.class,
                admission.readPinnedV3(seed.definition(), "mock-pg", bytes(seed.execution()), bytes(seed.payload()))).code();
    }

    private Seed seed() throws Exception {
        var source = Files.readAllBytes(Path.of("../../fixtures/native-v3/definition.json"));
        var compiled = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeV3DefinitionBytesCompiler().compile(source, DefinitionBytesCompiler.Format.JSON));
        assertTrue(compiled.diagnostics().stream().anyMatch(d -> d.code().equals("MECHANISM_UNQUALIFIED")));
        // This witness only lets the internal mechanical boundary be exercised; production stays Incomplete.
        var witness = new NativeCompilationResult.ReadyToPublish(compiled.checked());
        var oracle = json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v3/expected-digests.json")));
        var manifest = json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json")));
        var execution = (ObjectNode) manifest.get("execution").deepCopy();
        execution.put("bindingId", "mock-pg").put("logicalDigest", oracle.get("logicalDigest").asString())
                .put("bindingDigest", oracle.get("bindingDigests").get("mock-pg").asString());
        var mechanisms = execution.putObject("mechanisms");
        oracle.get("mechanisms").get("mock-pg").properties().forEach(e -> mechanisms.put(e.getKey(), e.getValue().asString()));
        mechanisms.put("structural-target-v1", "1").put("plan-validation-v3", "1");
        var payload = (ObjectNode) json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json")));
        payload.put("bindingId", "mock-pg");
        return new Seed(witness, execution, payload);
    }
    private byte[] bytes(ObjectNode value) { return json.writeValueAsBytes(value); }
    private record Seed(NativeCompilationResult.ReadyToPublish definition, ObjectNode execution, ObjectNode payload) { }
}
