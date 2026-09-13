package studio.environment.server.profile;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.profile.ProfileComposer;
import studio.environment.core.profile.ProfileResult;
import studio.environment.core.profile.V3ProfileComposer;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.definition.*;
import studio.environment.server.projection.DerivedGraphProjectionAdapter;
import studio.environment.server.projection.DocumentSource;
import studio.environment.server.planning.DerivedTargetInputAdapter;
import studio.environment.server.planning.DerivedTargetMaterializer;
import studio.environment.server.planning.TargetPlacement;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class V3ProfileBytesAdapterTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final V3ProfileBytesAdapter adapter = new V3ProfileBytesAdapter();
    static NativeCompilationResult.Checked definition() throws Exception {
        return assertInstanceOf(NativeCompilationResult.Incomplete.class, new NativeV3DefinitionBytesCompiler().compile(
                Files.readAllBytes(Path.of("../../fixtures/native-v3/definition.json")), DefinitionBytesCompiler.Format.JSON)).checked();
    }
    static byte[] fixture() throws Exception { return Files.readAllBytes(Path.of("../../fixtures/native-v3/profile.json")); }
    static final String GLYPHS = "<tiles xmlns='urn:mock:tiles' xmlns:p='urn:mock:properties' xmlns:k='urn:mock:keys'>"
            + "<glyph id='donor-a' palette='donor-p' finish='donor-finish'><p:entry k:key='tone' value='donor-tone'/></glyph>"
            + "<glyph id='donor-b' palette='donor-p'><p:entry k:key='tone' value='donor-tone'/></glyph></tiles>";
    static final String PALETTES = "<tiles xmlns='urn:mock:tiles'><palette id='donor-p' shade='donor-shade'/></tiles>";
    static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    static DerivedInput.Pin pin(NativeCompilationResult.Checked definition) throws Exception {
        return new DerivedInput.Pin("captured-revision", definition.logicalDigest(), "mock-pg", definition.bindingDigests().get("mock-pg"),
                Map.of("glyph-sheet", digest(GLYPHS), "palette-sheet", digest(PALETTES)));
    }
    static DerivedGraphProjectionAdapter.Snapshot snapshot(DerivedInput.Pin pin, String glyphs) {
        return new DerivedGraphProjectionAdapter.Snapshot(pin.revisionToken(), pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(),
                List.of(new DocumentSource("glyph-sheet", glyphs), new DocumentSource("palette-sheet", PALETTES)));
    }
    static ProfileCapture.Command command() {
        return new ProfileCapture.Command("neutral-capture", BigInteger.ONE, List.of(
                new ProfileCapture.SlotMapping(new ObservedGraph.Key("glyph", "donor-a"), "first", "First neutral slot"),
                new ProfileCapture.SlotMapping(new ObservedGraph.Key("glyph", "donor-b"), "second", "Second neutral slot"),
                new ProfileCapture.SlotMapping(new ObservedGraph.Key("palette", "donor-p"), "dependency", "Neutral dependency")));
    }
    @Test void v3PortableRoundTripMatchesIndependentDigestAndKeepsItsVersion() throws Exception {
        var definition = definition();
        var checked = assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class,
                adapter.read(definition, fixture(), BoundedDocumentParser.Format.JSON)).checked();
        assertEquals("b661384049dfe987cfbaaffd305f34b7a7e1f287ef7686851aeae1cbcac321e0", checked.contentDigest());
        var output = assertInstanceOf(V3ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition, checked));
        assertEquals("3", JSON.readTree(output.bytes()).get("schemaVersion").asString());
        for (var format : BoundedDocumentParser.Format.values())
            assertEquals(checked, assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, output.bytes(), format)).checked());
        byte[] mutated = output.bytes(); mutated[0] = 0; assertEquals('{', output.bytes()[0]);
    }
    @Test void captureReprojectsRealChildXmlButReturnsOnlyPhysicalNeutralSlotsAndEdges() throws Exception {
        var definition = definition(); var pin = pin(definition);
        var result = assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class,
                adapter.capture(definition, pin, snapshot(pin, GLYPHS), command(), () -> false));
        var profile = result.checked().profile();
        assertEquals(List.of("dependency", "first", "second"), profile.entities().stream().map(e -> e.id()).toList());
        assertEquals(List.of("palette", "glyph", "glyph"), profile.entities().stream().map(e -> e.type()).toList());
        assertEquals(List.of("shade", "tag"), profile.entities().getFirst().requiredInputs());
        assertEquals(List.of("uses:first:dependency", "uses:second:dependency"), profile.relations().stream().map(e -> e.type()+":"+e.from()+":"+e.to()).toList());
        var output = assertInstanceOf(V3ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition, result.checked()));
        String text = new String(output.bytes(), StandardCharsets.UTF_8);
        for (String forbidden : List.of("donor-", "tone-group", "finish-group", "contributors", "glyph-sheet", "sourceDigest", "<tiles"))
            assertFalse(text.contains(forbidden), forbidden);
        assertEquals(result.checked(), assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class,
                adapter.read(definition, output.bytes(), BoundedDocumentParser.Format.JSON)).checked());
    }
    @Test void sameDecodedReplacementXmlCannotReplaceExpectedCaptureAuthority() throws Exception {
        var definition = definition(); var pin = pin(definition);
        var result = assertInstanceOf(V3ProfileBytesAdapter.Result.Rejected.class,
                adapter.capture(definition, pin, snapshot(pin, GLYPHS.replace("donor-tone", "donor-&#116;one")), command(), () -> false));
        assertEquals("STALE_INPUT", result.diagnostics().getFirst().code());
    }
    @Test void closedVersionAndPhysicalPartitionRejectDerivedOrValueBearingImports() throws Exception {
        var definition = definition();
        for (String path : List.of("", "/entities/0", "/relations/0")) {
            var tree = (ObjectNode)JSON.readTree(fixture());
            ((ObjectNode)tree.at(path)).put("contributors", "donor-never-echo");
            var refused = assertInstanceOf(V3ProfileBytesAdapter.Result.Rejected.class, adapter.read(definition, JSON.writeValueAsBytes(tree), BoundedDocumentParser.Format.JSON));
            assertEquals("SCHEMA_VIOLATION", refused.diagnostics().getFirst().code());
            assertFalse(refused.toString().contains("donor-never-echo"));
        }
        var tree = (ObjectNode)JSON.readTree(fixture()); tree.put("schemaVersion", "2");
        assertCode(adapter.read(definition, JSON.writeValueAsBytes(tree), BoundedDocumentParser.Format.JSON), "SCHEMA_VIOLATION");
        tree = (ObjectNode)JSON.readTree(fixture()); ((ObjectNode)tree.at("/entities/0")).put("type", "tone-group");
        assertCode(adapter.read(definition, JSON.writeValueAsBytes(tree), BoundedDocumentParser.Format.JSON), "INVALID_ENTITY");
        tree = (ObjectNode)JSON.readTree(fixture()); ((ObjectNode)tree.at("/relations/0")).put("type", "tone-finish");
        assertCode(adapter.read(definition, JSON.writeValueAsBytes(tree), BoundedDocumentParser.Format.JSON), "INVALID_RELATION");
        assertCode(adapter.read(definition, new byte[]{(byte)0xc0, (byte)0xaf}, BoundedDocumentParser.Format.JSON), "INVALID_UTF8");
        assertCode(adapter.read(definition, new byte[1_048_577], BoundedDocumentParser.Format.JSON), "BYTE_LIMIT");
    }
    @Test void staleExportDigestAndFinalCaptureCancellationNeverYieldPortableBytes() throws Exception {
        var definition = definition(); var pin = pin(definition);
        var result = assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class, adapter.capture(definition, pin, snapshot(pin, GLYPHS), command(), () -> false));
        var refused = assertInstanceOf(V3ProfileBytesAdapter.ExportResult.Rejected.class,
                adapter.write(definition, new ProfileResult.Checked(result.checked().profile(), "0".repeat(64))));
        assertEquals("STALE_PROFILE", refused.diagnostics().getFirst().code());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class, adapter.capture(definition, pin, snapshot(pin, GLYPHS), command(), () -> { calls.incrementAndGet(); return false; }));
        var next = new java.util.concurrent.atomic.AtomicInteger();
        assertCode(adapter.capture(definition, pin, snapshot(pin, GLYPHS), command(), () -> next.incrementAndGet() >= calls.get()), "CANCELLED");
    }
    @Test void partialCompositionUsesPhysicalClosureAndFreshFieldsWhilePreservingAllCurrentEntities() throws Exception {
        var definition = definition(); var pin = pin(definition);
        var profile = assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class, adapter.read(definition, fixture(), BoundedDocumentParser.Format.JSON));
        var preview = assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class, adapter.preview(definition, profile, Set.of("first"))).preview();
        assertEquals(List.of("dependency", "first"), preview.physical().included().stream().map(e -> e.id()).toList());
        assertEquals(List.of("by-finish", "by-tone"), preview.affectedDerivations());
        List<ProfileComposer.Decision> decisions = List.of(new ProfileComposer.Decision.Create("first", "fresh-glyph"),
                new ProfileComposer.Decision.UseExisting("dependency", new ObservedGraph.Key("palette", "donor-p")));
        var prepared = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class,
                adapter.compose(definition, profile, preview, pin, snapshot(pin, GLYPHS), decisions, () -> false)).draft();
        assertEquals(3, prepared.retainedExisting().size());
        assertEquals(List.of(new ProfileComposer.Target.New("fresh-glyph", "glyph")), prepared.additions());
        assertEquals(2, prepared.retainedRelations().size());
        assertEquals(List.of("finish", "tag", "tone"), prepared.unresolvedFields().stream()
                .filter(f -> f.target() instanceof ProfileComposer.Target.New).findFirst().orElseThrow().fields());
        var stale = new DerivedInput.Pin("different", pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(), pin.documentDigests());
        var refused = assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class,
                adapter.compose(definition, profile, preview, stale, snapshot(pin, GLYPHS), decisions, () -> false));
        assertEquals("STALE_INPUT", refused.diagnostics().getFirst().code());
        var edited = new V3ProfileComposer.Preview(preview.physical(), List.of());
        assertEquals("STALE_PREVIEW", assertInstanceOf(ProfileComposer.CompositionResult.Rejected.class,
                adapter.compose(definition, profile, edited, pin, snapshot(pin, GLYPHS), decisions, () -> false)).diagnostics().getFirst().code());
    }
    private static void assertCode(V3ProfileBytesAdapter.Result result, String code) {
        assertEquals(code, assertInstanceOf(V3ProfileBytesAdapter.Result.Rejected.class, result).diagnostics().getFirst().code());
    }
    @Test void captureAppliesPortableNodeAndByteLimitsBeforeAcceptingOutput() throws Exception {
        var model = (ObjectNode)JSON.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v3/definition.json")));
        for (var rule : model.at("/logical/rules")) { ((ObjectNode)rule).put("minimum", 0); ((ObjectNode)rule).put("maximum", 2000); }
        var definition = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeV3DefinitionBytesCompiler().compile(JSON.writeValueAsBytes(model), DefinitionBytesCompiler.Format.JSON)).checked();
        String empty = "<tiles xmlns='urn:mock:tiles'/>";
        for (int count : List.of(1817, 1818, 1800)) {
            var xml = new StringBuilder("<tiles xmlns='urn:mock:tiles'>");
            var mappings = new ArrayList<ProfileCapture.SlotMapping>();
            for (int i = 0; i < count; i++) {
                xml.append("<palette id='donor-").append(i).append("' shade='donor-shade'/>");
                mappings.add(new ProfileCapture.SlotMapping(new ObservedGraph.Key("palette", "donor-" + i), "neutral-" + i,
                        count == 1800 ? "\u0000".repeat(128) : "Neutral"));
            }
            xml.append("</tiles>");
            var pin = new DerivedInput.Pin("capacity", definition.logicalDigest(), "mock-pg", definition.bindingDigests().get("mock-pg"),
                    Map.of("glyph-sheet", digest(empty), "palette-sheet", digest(xml.toString())));
            var snapshot = new DerivedGraphProjectionAdapter.Snapshot(pin.revisionToken(), pin.logicalDigest(), pin.bindingId(), pin.bindingDigest(),
                    List.of(new DocumentSource("glyph-sheet", empty), new DocumentSource("palette-sheet", xml.toString())));
            var result = adapter.capture(definition, pin, snapshot, new ProfileCapture.Command("neutral", BigInteger.ONE, mappings), () -> false);
            if (count == 1817) {
                var accepted = assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class, result);
                var output = assertInstanceOf(V3ProfileBytesAdapter.ExportResult.Encoded.class, adapter.write(definition, accepted.checked()));
                assertEquals(accepted.checked(), assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class,
                        adapter.read(definition, output.bytes(), BoundedDocumentParser.Format.JSON)).checked());
            } else assertCode(result, count == 1818 ? "RESOURCE_LIMIT" : "BYTE_LIMIT");
        }
    }
    @Test void wholeAndPartialReuseMaterializeFreshGroupsWithoutDonorMembershipOrSiblingDeletion() throws Exception {
        var definition = definition(); var donorPin = pin(definition);
        var profile = assertInstanceOf(V3ProfileBytesAdapter.Result.Accepted.class,
                adapter.capture(definition, donorPin, snapshot(donorPin, GLYPHS), command(), () -> false));
        String currentGlyphs = GLYPHS.replace("donor-tone", "target-tone").replace("donor-finish", "target-finish");
        String currentPalettes = PALETTES.replace("donor-shade", "target-shade");
        var currentPin = new DerivedInput.Pin("target-current", definition.logicalDigest(), "mock-pg", definition.bindingDigests().get("mock-pg"),
                Map.of("glyph-sheet", digest(currentGlyphs), "palette-sheet", digest(currentPalettes)));
        var current = new DerivedGraphProjectionAdapter.Snapshot(currentPin.revisionToken(), currentPin.logicalDigest(), currentPin.bindingId(), currentPin.bindingDigest(),
                List.of(new DocumentSource("glyph-sheet", currentGlyphs), new DocumentSource("palette-sheet", currentPalettes)));
        var palette = new TargetIntent.Ref.Existing(new ObservedGraph.Key("palette", "donor-p"));
        for (var selected : List.of(Set.of("first"), Set.of("first", "second", "dependency"))) {
            var preview = assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class, adapter.preview(definition, profile, selected)).preview();
            var mappings = new ArrayList<ProfileComposer.Decision>();
            mappings.add(new ProfileComposer.Decision.UseExisting("dependency", palette.key()));
            for (String slot : List.of("first", "second")) if (selected.contains(slot)) mappings.add(new ProfileComposer.Decision.Create(slot, "new-" + slot));
            var draft = assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class,
                    adapter.compose(definition, profile, preview, currentPin, current, mappings, () -> false)).draft();
            var entities = new ArrayList<TargetIntent.EntityDecision>();
            entities.add(new TargetIntent.EntityDecision.Retain(palette, Map.of("tag", new TargetIntent.FieldValue.KeepObserved(),
                    "shade", new TargetIntent.FieldValue.KeepObserved()), Map.of()));
            var placements = new ArrayList<TargetPlacement>();
            var expectedTones = new TreeSet<>(Set.of("target-tone"));
            for (var addition : draft.additions()) {
                var fresh = new TargetIntent.Ref.Fresh(addition.slot(), addition.type());
                expectedTones.add(addition.slot() + "-tone");
                entities.add(new TargetIntent.EntityDecision.Create(fresh, Map.of("tag", new TargetIntent.FieldValue.Entered(addition.slot()),
                        "tone", new TargetIntent.FieldValue.Entered(addition.slot() + "-tone"), "finish", new TargetIntent.FieldValue.ExplicitlyAbsent()),
                        Map.of("uses", new TargetIntent.ReferenceValue.To(palette))));
                placements.add(new TargetPlacement(fresh, "glyph-sheet", "glyphs", new TargetPlacement.Parent.Existing("glyph-sheet", digest(currentGlyphs), 0)));
            }
            var targetPin = new DerivedInput.Pin("composed-target", currentPin.logicalDigest(), currentPin.bindingId(), currentPin.bindingDigest(), currentPin.documentDigests());
            var materialized = assertInstanceOf(DerivedTargetMaterializer.Complete.class, new DerivedTargetMaterializer().materialize(definition,
                    currentPin, current, targetPin, new DerivedTargetInputAdapter.Decisions(targetPin, new TargetIntent(entities, List.of())),
                    Map.of("glyph-sheet", Optional.empty(), "palette-sheet", Optional.empty()), placements, () -> false));
            assertEquals(3 + draft.additions().size(), materialized.finalProjection().physical().entities().size());
            assertEquals(currentPalettes, materialized.physical().documents().stream().filter(d -> d.documentId().equals("palette-sheet")).findFirst().orElseThrow().source());
            assertEquals(expectedTones, new TreeSet<>(materialized.finalProjection().derived().graph().nodes().stream()
                    .filter(n -> n.key().derivation().equals("by-tone")).map(n -> n.key().value()).toList()));
            assertFalse(materialized.finalProjection().derived().graph().nodes().stream().anyMatch(n -> n.key().value().startsWith("donor-")));
            String finalGlyphs = materialized.physical().documents().stream().filter(d -> d.documentId().equals("glyph-sheet")).findFirst().orElseThrow().source();
            assertTrue(finalGlyphs.contains("<glyph id='donor-b' palette='donor-p'><p:entry k:key='tone' value='target-tone'/></glyph>"));
        }
    }
}
