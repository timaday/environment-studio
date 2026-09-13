package studio.environment.server.definition;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.definition.DefinitionBytesCompiler.Format.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.definition.DefinitionDiagnostic.Phase;
import studio.environment.core.definitionv2.NativeDefinition.ChildProperty;
import studio.environment.core.definitionv2.NativeDefinition.DirectAttribute;
import studio.environment.core.definitionv3.NativeCompilationResult;

class NativeV3DefinitionBytesCompilerTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final NativeV3DefinitionBytesCompiler compiler = new NativeV3DefinitionBytesCompiler();

    private ObjectNode fixture() throws Exception {
        return (ObjectNode) MAPPER.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v3/definition.json")));
    }

    private NativeCompilationResult compile(JsonNode node) {
        return compiler.compile(MAPPER.writeValueAsBytes(node), JSON);
    }

    private void refusal(NativeCompilationResult result, Phase phase, String code) {
        var rejected = assertInstanceOf(NativeCompilationResult.Rejected.class, result);
        assertTrue(rejected.diagnostics().stream().anyMatch(d -> d.phase() == phase && d.code().equals(code)),
                () -> rejected.diagnostics().toString());
    }

    private static ObjectNode at(JsonNode node, String pointer) {
        return (ObjectNode) node.at(pointer);
    }

    @Test void independentGoldenIsInspectableButCannotBecomeReady() throws Exception {
        var result = assertInstanceOf(NativeCompilationResult.Incomplete.class, compile(fixture()));
        var expected = MAPPER.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v3/expected-digests.json")));
        assertEquals(expected.get("logicalDigest").asString(), result.checked().logicalDigest());
        for (var entry : expected.get("bindingDigests").properties()) {
            assertEquals(entry.getValue().asString(), result.checked().bindingDigests().get(entry.getKey()));
        }
        assertTrue(result.diagnostics().stream().allMatch(d -> d.phase() == Phase.PUBLICATION));
    }

    @Test void unknownVocabularyAndClaimedAuthorityNeverReachSemantics() throws Exception {
        for (String location : List.of("", "/logical", "/logical/derivations/0",
                "/logical/computedTypes/0", "/logical/cooccurrences/0")) {
            var input = fixture();
            at(input, location).put("invented-private-canary", "do-not-copy");
            var result = compile(input);
            refusal(result, Phase.SHAPE, "SCHEMA_VIOLATION");
            assertFalse(result.toString().contains("invented-private-canary"));
            assertFalse(result.toString().contains("do-not-copy"));
        }
        var input = fixture();
        input.putObject("mechanisms").put("derived-graph-v1", 1);
        refusal(compile(input), Phase.SHAPE, "SCHEMA_VIOLATION");
    }

    @Test void directAndChildFormsAreClosedAndMutuallyExclusive() throws Exception {
        for (String fault : List.of("both", "neither", "extra", "missing")) {
            var input = fixture();
            var field = at(input, "/bindings/0/documents/0/entities/0/fields/1");
            switch (fault) {
                case "both" -> field.putObject("attribute").put("namespaceUri", "").put("localName", "tone");
                case "neither" -> field.remove("childProperty");
                case "extra" -> at(field, "/childProperty").put("descendants", true);
                case "missing" -> at(field, "/childProperty").remove("discriminatorValue");
            }
            refusal(compile(input), Phase.SHAPE, "SCHEMA_VIOLATION");
        }
    }

    @Test void declarationArraysHaveActualShapeBounds() throws Exception {
        for (String key : List.of("computedTypes", "derivations", "cooccurrences")) {
            var input = fixture();
            var values = (ArrayNode) input.at("/logical/" + key);
            var first = values.get(0).deepCopy();
            values.removeAll();
            for (int i = 0; i < 32; i++) {
                values.add(first.deepCopy());
            }
            assertTrue(compile(input).diagnostics().stream()
                    .noneMatch(d -> d.phase() == Phase.PARSE || d.phase() == Phase.SHAPE));
            values.add(first.deepCopy());
            refusal(compile(input), Phase.SHAPE, "SCHEMA_VIOLATION");
        }
    }

    @Test void malformedUtf8SurrogatesDuplicateKeysAndMultipleDocumentsRefuse() {
        refusal(compiler.compile(new byte[] {(byte) 0xc3, 0x28}, JSON), Phase.PARSE, "INVALID_UTF8");
        for (String source : List.of("{\"x\":\"\\ud800\"}", "{\"x\":1,\"x\":2}", "{} {}")) {
            refusal(compiler.compile(source.getBytes(StandardCharsets.UTF_8), JSON), Phase.PARSE, "INVALID_SYNTAX");
        }
        for (String source : List.of("x: &a [1]\ny: *a", "x: !!str hello", "---\nx: 1\n---\nx: 2", "x: 1\nx: 2")) {
            refusal(compiler.compile(source.getBytes(StandardCharsets.UTF_8), YAML), Phase.PARSE, "INVALID_SYNTAX");
        }
    }

    @Test void sourceAndParserBudgetsRemainClosed() {
        refusal(compiler.compile(new byte[1048577], JSON), Phase.PARSE, "RESOURCE_LIMIT");
        for (String source : List.of("[".repeat(33) + "0" + "]".repeat(33),
                "{\"x\":\"" + "x".repeat(16385) + "\"}", "{\"x\":" + "9".repeat(257) + "}")) {
            refusal(compiler.compile(source.getBytes(StandardCharsets.UTF_8), JSON), Phase.PARSE, "RESOURCE_LIMIT");
        }
    }

    @Test void v2ReaderAndV3ReaderDoNotRelabelEachOther() throws Exception {
        assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.Rejected.class,
                new NativeDefinitionBytesCompiler().compile(MAPPER.writeValueAsBytes(fixture()), JSON));
        byte[] old = Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json"));
        assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,
                new NativeDefinitionBytesCompiler().compile(old, JSON));
        refusal(compiler.compile(old, JSON), Phase.SHAPE, "SCHEMA_VIOLATION");
    }

    @Test void jsonAndActualYamlPreserveIntegersAndEveryDerivedDeclaration() throws Exception {
        var input = fixture();
        var large = new BigInteger("123456789012345678901234567890");
        input.put("revision", large);
        at(input, "/logical/cooccurrences/0").put("minimum", BigInteger.ZERO).put("maximum", large);
        var yaml = new StringBuilder();
        for (var entry : input.properties()) {
            yaml.append(entry.getKey()).append(": ").append(MAPPER.writeValueAsString(entry.getValue())).append('\n');
        }
        byte[] json = MAPPER.writeValueAsBytes(input);
        byte[] other = yaml.toString().getBytes(StandardCharsets.UTF_8);
        var parser = new BoundedDefinitionParser();
        var tree = parser.parse(other, YAML);
        assertTrue(new DefinitionShapeValidator(DefinitionShapeValidator.Version.V3).validate(tree).isEmpty());
        var read = NativeV3DefinitionReader.read(tree);
        assertEquals(large, read.revision());
        assertEquals(large, read.logical().cooccurrences().getFirst().maximum());
        assertEquals(input.at("/logical/derivations/0/membershipRelation").asString(),
                read.logical().derivations().getFirst().membershipRelation());
        assertEquals(input.at("/logical/computedTypes/0/label").asString(),
                read.logical().computedTypes().getFirst().label());
        assertEquals(input.at("/logical/computedRules/0/type").asString(),
                read.logical().computedRules().getFirst().type());
        assertInstanceOf(ChildProperty.class,
                read.bindings().getFirst().documents().getFirst().entities().getFirst().fields().get(1).locator());
        assertInstanceOf(DirectAttribute.class,
                read.bindings().get(1).documents().getFirst().entities().getFirst().fields().get(1).locator());
        assertEquals(NativeV3DefinitionReader.read(parser.parse(json, JSON)), read);
        assertEquals(compiler.compile(json, JSON), compiler.compile(other, YAML));
        assertInstanceOf(NativeCompilationResult.Incomplete.class, compiler.compile(other, YAML));
    }

    @Test void numericTokensRetainMathematicalIntegerMeaningWithoutCoercion() throws Exception {
        String source = MAPPER.writeValueAsString(fixture());
        var seed = fixture();
        seed.put("revision", 1000);
        var expected = compile(seed);
        for (var format : List.of(JSON, YAML)) {
            for (String number : List.of("1000", "1e3", "1000.0")) {
                String changed = source.replaceFirst("\"revision\":1", "\"revision\":" + number);
                assertEquals(expected, compiler.compile(changed.getBytes(StandardCharsets.UTF_8), format));
            }
        }
        for (String number : List.of("1.5", "\"1\"", "true", "null")) {
            String changed = source.replaceFirst("\"revision\":1", "\"revision\":" + number);
            refusal(compiler.compile(changed.getBytes(StandardCharsets.UTF_8), JSON), Phase.SHAPE, "SCHEMA_VIOLATION");
        }
    }

    @Test void shapeValidIneligibleSourcesNeverAcquireCheckedValues() throws Exception {
        for (String sensitivity : List.of("internal", "secret", "unknown")) {
            var input = fixture();
            at(input, "/logical/entityTypes/0/fields/1").put("sensitivity", sensitivity);
            var result = compile(input);
            refusal(result, Phase.SEMANTIC, "DERIVATION_INPUT_INELIGIBLE");
            assertFalse(result.toString().contains("urn:mock"));
        }
    }
}
