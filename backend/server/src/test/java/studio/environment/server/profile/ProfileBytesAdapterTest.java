package studio.environment.server.profile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.profile.*;
import studio.environment.server.definition.*;
import studio.environment.server.projection.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileBytesAdapterTest {
    private final ProfileBytesAdapter adapter = new ProfileBytesAdapter();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private NativeCompilationResult.ReadyToPublish definition() throws Exception {
        return assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")), DefinitionBytesCompiler.Format.JSON));
    }
    private ObjectNode fixture() throws Exception { return (ObjectNode)JSON.readTree(Files.readAllBytes(Path.of("../../fixtures/profile-v2/profile.json"))); }
    private ProfileBytesAdapter.Result read(ObjectNode tree) throws Exception { return adapter.read(definition(), JSON.writeValueAsBytes(tree), BoundedDocumentParser.Format.JSON); }
    private ProfileResult.Checked checked() throws Exception { return assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, read(fixture())).checked(); }
    private static ObjectNode object(ObjectNode root, String path) { return (ObjectNode)root.at(path); }
    @Test void independentDigestOracleAndJsonYamlRoundTripPreserveUnicodeAndIgnoreOnlyRevision() throws Exception {
        var profile = checked();
        String expected = JSON.readTree(Files.readString(Path.of("../../fixtures/profile-v2/expected-digest.json"))).get("contentDigest").asString();
        assertEquals(expected, profile.contentDigest());
        byte[] bytes = assertInstanceOf(ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition(), profile)).bytes();
        for (var format : BoundedDocumentParser.Format.values()) assertEquals(profile, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition(), bytes, format)).checked());
        var revision = fixture(); revision.put("revision", 999);
        assertEquals(expected, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, read(revision)).checked().contentDigest());
        for (String property : List.of("id", "label")) {
            var changed = fixture(); if (property.equals("id")) changed.put("id", "different-profile"); else object(changed, "/entities/0").put("label", "Changed neutral label");
            assertNotEquals(expected, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, read(changed)).checked().contentDigest());
        }
        var reordered = fixture(); var array = (tools.jackson.databind.node.ArrayNode)reordered.get("entities"); var first = array.remove(0); array.add(first);
        var inputs = (tools.jackson.databind.node.ArrayNode)array.get(0).get("requiredInputs"); var input = inputs.remove(0); inputs.add(input);
        assertEquals(profile, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, read(reordered)).checked());
    }
    @Test void nestedUnknownValueBagsAreRejectedWithoutCanaries() throws Exception {
        for (String path : List.of("", "/entities/0", "/relations/0")) for (String key : List.of("values", "rawXml", "identity", "locator", "metadata", "credential", "canary-key")) {
            var tree = fixture(); object(tree, path).putObject(key).put("canary-value-key", "donor-secret-canary");
            var rejected = assertInstanceOf(ProfileBytesAdapter.Result.Rejected.class, read(tree));
            assertEquals("SCHEMA_VIOLATION", rejected.diagnostics().getFirst().code());
            assertFalse(rejected.toString().contains("canary"));
        }
    }
    @Test void requiredFieldsDigestEndpointsAndDuplicateEdgesAreSemanticallyChecked() throws Exception {
        var changed = fixture(); changed.put("logicalDefinitionDigest", "0".repeat(64)); assertCode(read(changed), "INCOMPATIBLE_DEFINITION");
        changed = fixture(); object(changed, "/entities/0").putArray("requiredInputs").add("tag"); assertCode(read(changed), "REQUIRED_INPUTS_MISMATCH");
        changed = fixture(); object(changed, "/relations/0").put("to", "first"); assertCode(read(changed), "INVALID_RELATION");
        changed = fixture(); ((tools.jackson.databind.node.ArrayNode)changed.get("relations")).add(changed.get("relations").get(0)); assertCode(read(changed), "DUPLICATE_RELATION");
        changed = fixture(); ((tools.jackson.databind.node.ArrayNode)changed.get("relations")).remove(0); assertCode(read(changed), "RELATION_CARDINALITY");
        changed = fixture(); object(changed, "/entities/1").put("id", "first"); assertCode(read(changed), "DUPLICATE_SLOT");
    }
    @Test void existingParserBudgetsAndStrictYamlRulesApplyThroughProfileBoundary() throws Exception {
        var definition = definition();
        String valid = JSON.writeValueAsString(fixture());
        for (String invalid : List.of("{\"id\":\"one\",\"id\":\"two\"}", "{\"bad\":\"\\ud800\"}", "{\"n\":1e1000000000}", "{\"n\":" + "1".repeat(257) + "}", "{\"n\":" + "[".repeat(33) + "0" + "]".repeat(33) + "}", "{\"canary\":\"" + "x".repeat(16385) + "\"}", "{\"n\":[" + "0,".repeat(20000) + "0]}")) {
            assertInstanceOf(ProfileBytesAdapter.Result.Rejected.class, adapter.read(definition, invalid.getBytes(StandardCharsets.UTF_8), BoundedDocumentParser.Format.JSON));
        }
        assertCode(adapter.read(definition, new byte[1_048_577], BoundedDocumentParser.Format.JSON), "RESOURCE_LIMIT");
        assertCode(adapter.read(definition, new byte[]{(byte)0xc0,(byte)0xaf}, BoundedDocumentParser.Format.JSON), "INVALID_UTF8");
        for (String yaml : List.of("x: &a 1\ny: *a", "x: !!str value", "x: 1\nx: 2", "x: 1\n---\nx: 2", "<<: {x: 1}")) assertCode(adapter.read(definition, yaml.getBytes(StandardCharsets.UTF_8), BoundedDocumentParser.Format.YAML), "INVALID_SYNTAX");
        for (String number : List.of("1.0", "1e0", "1e1023")) {
            String source = valid.replace("\"revision\":1", "\"revision\":" + number);
            var profile = assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, source.getBytes(StandardCharsets.UTF_8), BoundedDocumentParser.Format.JSON)).checked();
            byte[] encoded = assertInstanceOf(ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition, profile)).bytes();
            assertEquals(profile, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, encoded, BoundedDocumentParser.Format.JSON)).checked());
        }
        for (String number : List.of("1e1024", "1e-1024", "1.5", "0")) assertInstanceOf(ProfileBytesAdapter.Result.Rejected.class, adapter.read(definition, valid.replace("\"revision\":1", "\"revision\":" + number).getBytes(StandardCharsets.UTF_8), BoundedDocumentParser.Format.JSON));
        String yaml = "schemaVersion: '2'\nid: mock-profile\nrevision: 1\nlogicalDefinitionDigest: '" + definition.checked().logicalDigest() + "'\nentities:\n- id: neutral\n  type: palette\n  label: yes\n  requiredInputs: [shade, tag]\nrelations: []\n";
        assertEquals("yes", assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, yaml.getBytes(StandardCharsets.UTF_8), BoundedDocumentParser.Format.YAML)).checked().profile().entities().getFirst().label());
        for (String typed : List.of("true", "false", "null")) assertCode(adapter.read(definition, yaml.replace("label: yes", "label: " + typed).getBytes(StandardCharsets.UTF_8), BoundedDocumentParser.Format.YAML), "SCHEMA_VIOLATION");
        for (String literal : List.of("01", "2026-09-08")) assertEquals(literal, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, yaml.replace("label: yes", "label: " + literal).getBytes(StandardCharsets.UTF_8), BoundedDocumentParser.Format.YAML)).checked().profile().entities().getFirst().label());
    }
    @Test void actualProjectionCaptureSerializesNoDonorIdentityValuesOriginsOrXml() throws Exception {
        var definition = definition();
        String glyphs = "<tiles xmlns='urn:mock:tiles'><glyph id='donor-id-one-canary' tone='donor-environment-canary' palette='donor-id-target-canary'/><glyph id='donor-id-two-canary' tone='donor-secret-canary' palette='donor-id-target-canary'/></tiles>";
        String palettes = "<tiles xmlns='urn:mock:tiles'><palette id='donor-id-target-canary' shade='donor-structural-canary'/></tiles>";
        // Match only the independently invented fixture's lexical declarations.
        var graph = assertInstanceOf(ProjectionResult.Accepted.class, new GraphProjectionAdapter().project(definition, "mock-pg", List.of(new DocumentSource("glyph-sheet", glyphs), new DocumentSource("palette-sheet", palettes))));
        var mappings = new ArrayList<ProfileCapture.SlotMapping>();
        for (int i = 0; i < graph.graph().entities().size(); i++) mappings.add(new ProfileCapture.SlotMapping(graph.graph().entities().get(i).key(), "neutral-" + i, "Neutral slot " + i));
        var captured = assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.capture(definition, graph, new ProfileCapture.Command("mock-capture", BigInteger.ONE, mappings))).checked();
        var output = assertInstanceOf(ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition, captured));
        String text = new String(output.bytes(), StandardCharsets.UTF_8);
        for (String forbidden : List.of("donor-", "canary", "glyph-sheet", "palette-sheet", "<", "origin", "identity", "fields", "value")) {
            assertFalse(text.contains(forbidden), forbidden); assertFalse(captured.toString().contains(forbidden));
        }
        assertEquals(captured, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, output.bytes(), BoundedDocumentParser.Format.JSON)).checked());
        byte[] mutated = output.bytes(); mutated[0] = 0; assertEquals('{', output.bytes()[0]);
    }
    private static void assertCode(ProfileBytesAdapter.Result result, String code) { assertEquals(code, assertInstanceOf(ProfileBytesAdapter.Result.Rejected.class, result).diagnostics().getFirst().code()); }

    @Test void exactTwentyThousandNodeProfileRoundTripsAndOneMoreSlotRefuses() throws Exception {
        var tree = fixture(); tree.putArray("relations"); var entities = tree.putArray("entities");
        for (int i = 0; i < 1817; i++) { var entity = entities.addObject(); entity.put("id", "slot-" + i); entity.put("type", "palette"); entity.put("label", "Neutral"); entity.putArray("requiredInputs").add("shade").add("tag"); }
        var accepted = assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, read(tree)).checked();
        byte[] encoded = assertInstanceOf(ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition(), accepted)).bytes();
        assertEquals(accepted, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition(), encoded, BoundedDocumentParser.Format.JSON)).checked());
        var last = entities.addObject(); last.put("id", "one-more"); last.put("type", "palette"); last.put("label", "Neutral"); last.putArray("requiredInputs").add("shade").add("tag");
        assertCode(read(tree), "RESOURCE_LIMIT");
    }
    @Test void exportAndTargetCompositionRejectEditedDigestAndIncompatibleObservation() throws Exception {
        var definition = definition(); var profile = checked();
        assertInstanceOf(ProfileBytesAdapter.ExportResult.Rejected.class, adapter.write(definition, new ProfileResult.Checked(profile.profile(), "0".repeat(64))));
        var preview = assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class, new ProfileComposer().preview(definition, profile, Set.of("first"))).preview();
        var observation = new ProjectionResult.Accepted(new studio.environment.core.graph.ObservedGraph(List.of(), List.of()), new ProjectionResult.TransientProjection(List.of()), "0".repeat(64), "0".repeat(64));
        assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class, adapter.compose(definition, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, read(fixture())), preview, observation, List.of()));
        assertCode(adapter.capture(definition, observation, new ProfileCapture.Command("mock", BigInteger.ONE, List.of())), "INCOMPATIBLE_DEFINITION");
    }

    @Test void serverCaptureRequiresPortableOutputAfterStructuralValidation() throws Exception {
        ObjectNode declaration = (ObjectNode)JSON.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
        for (var rule : declaration.at("/logical/rules")) { ((ObjectNode)rule).put("minimum", 0); ((ObjectNode)rule).put("maximum", 2000); }
        var definition = assertInstanceOf(NativeCompilationResult.ReadyToPublish.class, new NativeDefinitionBytesCompiler().compile(JSON.writeValueAsBytes(declaration), DefinitionBytesCompiler.Format.JSON));
        for (int count : List.of(1, 1817, 1818, 1800)) {
            StringBuilder palettes = new StringBuilder("<tiles xmlns='urn:mock:tiles'>");
            for (int i = 0; i < count; i++) palettes.append("<palette id='donor-canary-").append(i).append("' shade='donor-canary'/>");
            palettes.append("</tiles>");
            var observation = assertInstanceOf(ProjectionResult.Accepted.class, new GraphProjectionAdapter().project(definition, "mock-pg", List.of(
                new DocumentSource("glyph-sheet", "<tiles xmlns='urn:mock:tiles'/>"), new DocumentSource("palette-sheet", palettes.toString()))));
            var mappings = new ArrayList<ProfileCapture.SlotMapping>();
            for (int i = 0; i < count; i++) mappings.add(new ProfileCapture.SlotMapping(observation.graph().entities().get(i).key(), "neutral-" + i, count == 1800 ? "\u0000".repeat(128) : "Neutral"));
            BigInteger revision = count == 1 ? BigInteger.TEN.pow(1023) : BigInteger.ONE;
            var command = new ProfileCapture.Command("mock", revision, mappings);
            var structural = assertInstanceOf(ProfileResult.StructurallyValid.class, new ProfileCapture().capture(definition,
                new studio.environment.core.graph.GraphValidationResult.Accepted(observation.graph()), command));
            var portable = adapter.capture(definition, observation, command);
            if (count == 1818 || count == 1800) {
                assertCode(portable, "RESOURCE_LIMIT");
                assertEquals("RESOURCE_LIMIT", assertInstanceOf(ProfileBytesAdapter.ExportResult.Rejected.class, adapter.write(definition, structural.checked())).diagnostics().getFirst().code());
            } else {
                var checked = assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, portable).checked();
                byte[] bytes = assertInstanceOf(ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition, checked)).bytes();
                assertEquals(checked, assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, bytes, BoundedDocumentParser.Format.JSON)).checked());
                if (count == 1) assertCode(adapter.capture(definition, observation, new ProfileCapture.Command("mock", revision.add(BigInteger.ONE), mappings)), "RESOURCE_LIMIT");
            }
        }
    }
}
