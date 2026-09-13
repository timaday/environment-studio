package studio.environment.server.definition;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.definition.DefinitionBytesCompiler.Format.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studio.environment.core.definition.*;

/** Independently invented declarations: no private application model or configuration provenance. */
class DefinitionBytesCompilerTest {
    private final DefinitionBytesCompiler compiler = new DefinitionBytesCompiler();
    private static final String JSON_SOURCE = """
        {"schemaVersion":"1","id":"mock-orbit","revision":1,"status":"published",
         "entityTypes":[{"id":"orb","label":"Orb","fields":[]}],"relations":[],
         "documents":[{"id":"sheet","logicalStore":"mock-store","recordKey":"mock-key","namespaces":{},"mappings":[]}],
         "requiredRules":["mock-rule"],"operationCapabilities":[]}
        """;
    private static final String YAML_SOURCE = """
        schemaVersion: "1"
        id: mock-orbit
        revision: 1.0
        status: published
        entityTypes:
          - id: orb
            label: Orb
            fields: []
        relations: []
        documents:
          - id: sheet
            logicalStore: mock-store
            recordKey: mock-key
            namespaces: {}
            mappings: []
        requiredRules: [mock-rule]
        operationCapabilities: []
        """;
    private DefinitionResult json(String input) { return compile(input, JSON); }
    private DefinitionResult compile(String input, DefinitionBytesCompiler.Format format) {
        return compiler.compile(input.getBytes(StandardCharsets.UTF_8), format);
    }
    private void rejected(DefinitionResult result, DefinitionDiagnostic.Phase phase, String code) {
        var rejection = assertInstanceOf(DefinitionResult.Rejected.class, result);
        assertTrue(rejection.diagnostics().stream().anyMatch(d -> d.phase() == phase && d.code().equals(code)),
                () -> "Expected safe diagnostic " + code + " but got " + rejection.diagnostics());
    }
    @Test void equivalentJsonYamlAndIntegralDecimalProduceEqualCheckedDrafts() {
        var result = assertInstanceOf(DefinitionResult.Incomplete.class, json(JSON_SOURCE));
        assertEquals(result, compile(YAML_SOURCE, YAML));
        assertEquals(result, json(JSON_SOURCE.replace("\"revision\":1", "\"revision\":1.0")));
        assertEquals("orb", result.draft().entityTypes().getFirst().id());
        assertEquals(result, json(JSON_SOURCE));
    }
    @Test void hugeIntegersRemainExact() {
        String huge = "1234567890123456789012345678901234567890";
        var result = assertInstanceOf(DefinitionResult.Incomplete.class,
                json(JSON_SOURCE.replace("\"revision\":1", "\"revision\":" + huge)));
        assertEquals(new BigInteger(huge), result.draft().revision());
    }
    @ParameterizedTest @ValueSource(strings = {"{} {}", "{", "[]", "null", "{\"id\":1,\"id\":2}", "{\"a\":NaN}"})
    void rejectsInvalidJson(String input) { rejected(json(input), DefinitionDiagnostic.Phase.PARSE, "INVALID_SYNTAX"); }
    @ParameterizedTest @ValueSource(strings = {"---\na: b\n---\na: c", "a: !!str secret-sentinel", "a: &mark value", "a: *mark", "a: {<<: {b: c}}", "? [a,b]\n: value", "true: value", "a: 1\na: 2", "a: .inf", "a: .NaN"})
    void rejectsUnsafeYaml(String input) { rejected(compile(input, YAML), DefinitionDiagnostic.Phase.PARSE, "INVALID_SYNTAX"); }
    @Test void invalidUtf8AndOversizeBytesAreSafelyRejected() {
        rejected(compiler.compile(new byte[] {(byte) 0xc3, 0x28}, JSON), DefinitionDiagnostic.Phase.PARSE, "INVALID_UTF8");
        rejected(compiler.compile(new byte[1048577], JSON), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
    }
    @Test void depthNodeScalarAndNumericBudgetsAreEnforced() {
        rejected(json("[".repeat(33) + "0" + "]".repeat(33)), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        rejected(json("[" + "0,".repeat(20000) + "0]"), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        rejected(json("{\"a\":\"" + "x".repeat(16385) + "\"}"), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        for (String number : List.of("1e1000000000", "1e-1000000000", "1e1024", "0." + "0".repeat(255) + "1")) {
            rejected(json("{\"a\":" + number + "}"), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
            rejected(compile("a: " + number, YAML), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        }
    }
    @Test void schemaRejectsUnknownFieldsEnumsFractionsAndMissingRequirementsWithoutLeakingValues() {
        for (String input : List.of(JSON_SOURCE.replace("\"id\":\"mock-orbit\"", "\"id\":\"mock-orbit\",\"secret-sentinel\":\"sensitive-value\""),
                JSON_SOURCE.replace("published", "secret-sentinel"), JSON_SOURCE.replace("\"revision\":1", "\"revision\":1.1"), "{}")) {
            var rejection = assertInstanceOf(DefinitionResult.Rejected.class, json(input));
            assertTrue(rejection.diagnostics().stream().allMatch(d -> d.phase() == DefinitionDiagnostic.Phase.SHAPE));
            assertFalse(rejection.toString().contains("secret-sentinel"));
            assertFalse(rejection.toString().contains("sensitive-value"));
        }
    }
    @Test void yamlDatesYesAndLeadingZeroStayStrings() {
        for (String label : List.of("yes", "2026-09-08", "01")) {
            var result = assertInstanceOf(DefinitionResult.Incomplete.class, compile(YAML_SOURCE.replace("label: Orb", "label: " + label), YAML));
            assertEquals(label, result.draft().entityTypes().getFirst().label());
        }
    }
    @Test void mappingAndRelationIntegersAndDeclarationsRemainExact() {
        String huge = "123456789012345678901234567890";
        String input = JSON_SOURCE.replace("\"fields\":[]", "\"fields\":[{\"id\":\"tone\",\"valueType\":\"boolean\",\"required\":false,\"classification\":\"structural\",\"sensitivity\":\"public\"}]")
                .replace("\"relations\":[]", "\"relations\":[{\"id\":\"echo\",\"fromType\":\"orb\",\"toType\":\"orb\",\"kind\":\"reference\",\"minimum\":0.0,\"maximum\":" + huge + ",\"includeTargetOnReuse\":false}]")
                .replace("\"namespaces\":{}", "\"namespaces\":{\"m\":\"urn:mock:orbit\"}")
                .replace("\"mappings\":[]", "\"mappings\":[{\"entityType\":\"orb\",\"field\":\"tone\",\"contextXPath\":\"/m:orb\",\"valueXPath\":\"@tone\",\"expectedMatches\":" + huge + ",\"writerCapability\":\"mock-writer\"}]");
        var draft = assertInstanceOf(DefinitionResult.Incomplete.class, json(input)).draft();
        assertEquals(new BigInteger(huge), draft.relations().getFirst().maximum());
        assertEquals(BigInteger.ZERO, draft.relations().getFirst().minimum());
        assertEquals(new BigInteger(huge), draft.documents().getFirst().mappings().getFirst().expectedMatches());
        assertEquals("urn:mock:orbit", draft.documents().getFirst().namespaces().get("m"));
        assertFalse(draft.entityTypes().getFirst().fields().getFirst().required());
        assertFalse(draft.relations().getFirst().includeTargetOnReuse());
        assertThrows(UnsupportedOperationException.class, () -> draft.documents().getFirst().namespaces().put("new", "urn:new"));
        rejected(json(input.replace("\"field\":\"tone\"", "\"field\":\"absent\"")), DefinitionDiagnostic.Phase.SEMANTIC, "UNKNOWN_FIELD");
    }
    @Test void schemaPathsNeverExposeUnknownPropertyOrNamespaceNames() {
        String input = JSON_SOURCE.replace("\"namespaces\":{}", "\"namespaces\":{\"secret/~sentinel\":false}");
        var result = assertInstanceOf(DefinitionResult.Rejected.class, json(input));
        assertEquals("/documents/0/namespaces", result.diagnostics().getFirst().pointer());
        assertFalse(result.toString().contains("sentinel"));
        input = JSON_SOURCE.replace("\"fields\":[]", "\"fields\":[],\"secret/~sentinel\":1");
        result = assertInstanceOf(DefinitionResult.Rejected.class, json(input));
        assertEquals("/entityTypes/0", result.diagnostics().getFirst().pointer());
        assertFalse(result.toString().contains("sentinel"));
    }
    @Test void exactResourceBoundariesAndUnicodeCodePointsAreRespected() {
        var parser = new BoundedDefinitionParser();
        String unicode = "\uD83D\uDE00".repeat(16384);
        assertEquals(unicode, parser.parse(("{\"x\":\"" + unicode + "\"}").getBytes(StandardCharsets.UTF_8), JSON).get("x").asString());
        assertEquals(19997, parser.parse(("{\"x\":[" + "0,".repeat(19996) + "0]}").getBytes(StandardCharsets.UTF_8), JSON).get("x").size());
        assertDoesNotThrow(() -> parser.parse(("{\"x\":" + "[".repeat(31) + "0" + "]".repeat(31) + "}").getBytes(StandardCharsets.UTF_8), JSON));
        assertDoesNotThrow(() -> parser.parse(("{\"x\":1e1023}").getBytes(StandardCharsets.UTF_8), JSON));
        assertDoesNotThrow(() -> parser.parse((JSON_SOURCE + " ".repeat(1048576 - JSON_SOURCE.getBytes(StandardCharsets.UTF_8).length)).getBytes(StandardCharsets.UTF_8), JSON));
        rejected(compile("x: " + "[".repeat(32) + "0" + "]".repeat(32), YAML), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        rejected(compile("x: [" + "0,".repeat(20000) + "0]", YAML), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        rejected(compile("x: " + "x".repeat(16385), YAML), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        rejected(json("{\"x\":\"" + "\uD83D\uDE00".repeat(16385) + "\"}"), DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
    }
    @Test void objectKeysConsumeTheNodeBudgetAndAllRefusalsAreDeterministic() {
        StringBuilder input = new StringBuilder("{");
        for (int i = 0; i < 10000; i++) {
            if (i > 0) input.append(',');
            input.append('"').append("invented-").append(i).append("\":0");
        }
        input.append('}');
        DefinitionResult first = json(input.toString());
        rejected(first, DefinitionDiagnostic.Phase.PARSE, "RESOURCE_LIMIT");
        assertEquals(first, json(input.toString()));
    }
    @Test void canonicalSchemaIsPackagedWithoutASecondMaintainedCopy() throws Exception {
        try (var source = getClass().getResourceAsStream("/schemas/definition.schema.json")) {
            assertNotNull(source);
            assertArrayEquals(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../../schemas/definition.schema.json")), source.readAllBytes());
        }
    }
    @Test void uploadedSchemaReferencesAreOnlyRejectedData() {
        var result = assertInstanceOf(DefinitionResult.Rejected.class,
                json(JSON_SOURCE.replace("\"id\":\"mock-orbit\"", "\"id\":\"mock-orbit\",\"$ref\":\"https://invalid.invalid/secret-sentinel\"")));
        assertEquals(DefinitionDiagnostic.Phase.SHAPE, result.diagnostics().getFirst().phase());
        assertFalse(result.toString().contains("invalid.invalid"));
    }
}
