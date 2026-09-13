package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent.FieldValue;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.export.V3PlanPackagePayload;
import studio.environment.server.plan.V3PlanContentAdapter;
import tools.jackson.databind.json.JsonMapper;

class V3PlanPackagePayloadTest {
    static HostedPlanService.ViewSnapshot view() {
        var model = new PlanDefinition.V3(definition(false));
        var current = V3PlanContentAdapterTest.current(model, XML);
        var draft = new PlanPorts.Draft(intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Entered("beta"))), List.of());
        var pins = new V3PlanPins(pin(model.checked(), XML, V3PlanContentAdapterTest.FINGERPRINT), pin(model.checked(), XML, "decision-2"));
        var target = assertInstanceOf(V3PlanContent.Result.Complete.class, new V3PlanContentAdapter().materialize(
                model, pins.original(), current, pins.decisions(), draft, new Cancellation())).content();
        var published = new PlanPorts.PublishedDefinition(new NativeCommand.Reference("10000000-0000-0000-0000-000000000001", "1"),
                "a".repeat(64), model, List.of(new NativeCommand.Policy("mock-pg", "sheet", "protected-self-contained")));
        return new HostedPlanService.ViewSnapshot("2", published, "mock-pg", Optional.of(current), Optional.of(target), draft,
                Map.of(), Map.of(), Optional.of(pins));
    }

    @Test void payloadUsesVerifiedMaterializedValuesAndExactDeclaredLocations() throws Exception {
        var candidate = assertInstanceOf(V3PlanPackagePayload.Result.Candidate.class,
                new V3PlanPackagePayload().prepare(view(), new Cancellation()));
        var body = JsonMapper.builder().build().readTree(candidate.bytes());
        assertEquals("1", body.get("schemaVersion").asString());
        assertEquals("mock-pg", body.get("bindingId").asString());
        assertEquals("postgresql", body.get("engine").asString());
        assertEquals("text", body.get("storage").asString());
        assertEquals("mock_schema", body.at("/table/schema").asString());
        assertEquals("mock_table", body.at("/table/name").asString());
        assertEquals("mock_key", body.at("/table/keyColumn").asString());
        assertEquals("mock_xml", body.at("/table/xmlColumn").asString());
        assertEquals("int64", body.at("/table/keyType").asString());
        assertEquals(1, body.get("records").size());
        assertEquals("sheet", body.at("/records/0/documentId").asString());
        assertEquals("1", body.at("/records/0/key/value").asString());
        assertEquals(XML, decode(body.at("/records/0/originalHex").asString()));
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",
                decode(body.at("/records/0/targetHex").asString()));
        assertFalse(candidate.qualified());
        var before = candidate.bytes(); var changed = candidate.bytes(); changed[0] = 0;
        assertArrayEquals(before, candidate.bytes());
        assertArrayEquals(before, assertInstanceOf(V3PlanPackagePayload.Result.Candidate.class,
                new V3PlanPackagePayload().prepare(view(), new Cancellation())).bytes());
        assertFalse(candidate.toString().contains("beta"));
    }
    @Test void refusesMissingDeniedDuplicateOrExtraneousPolicyEvenForPublicFields() {
        var good = view();
        var allowed = good.definition().policies().getFirst();
        for (var policies : List.of(List.<NativeCommand.Policy>of(),
                List.of(new NativeCommand.Policy("mock-pg", "sheet", "deny")),
                List.of(allowed, allowed),
                List.of(allowed, new NativeCommand.Policy("mock-pg", "other", "protected-self-contained")),
                List.of(new NativeCommand.Policy("other", "sheet", "protected-self-contained")))) {
            var definition = new PlanPorts.PublishedDefinition(good.definition().reference(), good.definition().publicationDigest(), good.definition().model(), policies);
            assertEquals(new V3PlanPackagePayload.Result.Rejected("POLICY_INVENTORY_MISMATCH"),
                    new V3PlanPackagePayload().prepare(copy(good, definition, good.current(), good.target(), good.draft(), good.v3Pins()), new Cancellation()));
        }
    }
    @Test void refusesMissingTargetAndCancelledWork() {
        var good = view();
        assertEquals(new V3PlanPackagePayload.Result.Rejected("INCOMPLETE_TARGET"), new V3PlanPackagePayload().prepare(
                copy(good, good.definition(), good.current(), Optional.empty(), good.draft(), good.v3Pins()), new Cancellation()));
        var cancellation = new Cancellation(); cancellation.cancel();
        assertEquals(new V3PlanPackagePayload.Result.Rejected("CANCELLED"), new V3PlanPackagePayload().prepare(good, cancellation));
    }
    @Test void refusesReplacedSourcesMissingPinsAndMismatchedDraftInsteadOfTrustingRetainedGraphs() {
        var good = view(); var current = good.current().orElseThrow(); var target = good.target().orElseThrow();
        var corrupted = new PlanPorts.Content(List.of(new PlanPorts.Source("sheet", XML.replace("three", "four"), digest(XML))), current.graph(), current.provenance(), current.evidence());
        var changedTarget = new PlanPorts.Content(List.of(new PlanPorts.Source("sheet", XML, digest(XML))), target.graph(), target.provenance(), target.evidence());
        for (var bad : List.of(copy(good, good.definition(), Optional.of(corrupted), good.target(), good.draft(), good.v3Pins()),
                copy(good, good.definition(), good.current(), Optional.of(changedTarget), good.draft(), good.v3Pins()),
                copy(good, good.definition(), good.current(), good.target(), PlanPorts.Draft.empty(), good.v3Pins()),
                copy(good, good.definition(), good.current(), good.target(), good.draft(), Optional.empty()))) {
            assertEquals(new V3PlanPackagePayload.Result.Rejected("PROJECTION_REFUSED"), new V3PlanPackagePayload().prepare(bad, new Cancellation()));
        }
    }
    @Test void includesUnmappedUnchangedDocumentsAndPreservesNoOpBytes() throws Exception {
        for (boolean change : List.of(false, true)) {
            var good = multiDocument(change);
            var candidate = assertInstanceOf(V3PlanPackagePayload.Result.Candidate.class, new V3PlanPackagePayload().prepare(good, new Cancellation()));
            var records = JsonMapper.builder().build().readTree(candidate.bytes()).get("records");
            assertEquals(2, records.size());
            assertEquals("other", records.get(0).get("documentId").asString());
            assertEquals("2", records.get(0).at("/key/value").asString());
            assertEquals(OTHER_XML, decode(records.get(0).get("originalHex").asString()));
            assertEquals(OTHER_XML, decode(records.get(0).get("targetHex").asString()));
            assertEquals("sheet", records.get(1).get("documentId").asString());
            assertEquals(XML, decode(records.get(1).get("originalHex").asString()));
            if (!change) assertEquals(XML, decode(records.get(1).get("targetHex").asString()));
            else assertEquals(XML.replace("tone='al&#112;ha'", "tone='beta'"), decode(records.get(1).get("targetHex").asString()));
            var policies = List.of(new NativeCommand.Policy("mock-pg", "sheet", "protected-self-contained"),
                    new NativeCommand.Policy("mock-pg", "other", "deny"));
            var denied = new PlanPorts.PublishedDefinition(good.definition().reference(), good.definition().publicationDigest(), good.definition().model(), policies);
            assertEquals(new V3PlanPackagePayload.Result.Rejected("POLICY_INVENTORY_MISMATCH"), new V3PlanPackagePayload().prepare(
                    copy(good, denied, good.current(), good.target(), good.draft(), good.v3Pins()), new Cancellation()));
        }
    }
    @Test void actualPlanPayloadPassesExistingMechanicalPinsAndPostgres16TemplateOnlyAsCandidate() throws Exception {
        var good = multiDocument(true);
        var mapper = JsonMapper.builder().build();
        var execution = (tools.jackson.databind.node.ObjectNode) mapper.readTree(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("../../fixtures/guarded-package-v1/manifest.json"))).get("execution").deepCopy();
        execution.put("bindingId", good.binding()).put("logicalDigest", good.definition().model().logicalDigest())
                .put("bindingDigest", good.definition().model().bindingDigests().get(good.binding()))
                .put("serverVersion", "16.11").put("templateVersion", "postgresql16-text-v1");
        ((tools.jackson.databind.node.ObjectNode) execution.get("client")).put("version", "16.11");
        var mechanisms = execution.putObject("mechanisms");
        for (String key : List.of("native-compiler-v3", "derived-graph-v1", "xml-path-v1", "xml-span-v1", "generic-graph-v1", "structural-target-v1", "plan-validation-v3")) mechanisms.put(key, "1");
        var policies = execution.putArray("exportPolicies");
        for (String id : List.of("other", "sheet")) policies.addObject().put("documentId", id).put("content", "protected-self-contained");
        var payload = assertInstanceOf(V3PlanPackagePayload.Result.Candidate.class, new V3PlanPackagePayload().prepare(good, new Cancellation()));
        // Explicit test witness for mechanical pin compatibility only; actual compiler is Incomplete.
        var witness = new studio.environment.core.definitionv3.NativeCompilationResult.ReadyToPublish(((PlanDefinition.V3) good.definition().model()).checked());
        var admitted = assertInstanceOf(studio.environment.server.export.PackageAdmission.Result.Accepted.class,
                new studio.environment.server.export.PackageAdmission().readPinnedV3(witness, good.binding(), mapper.writeValueAsBytes(execution), payload.bytes()));
        assertArrayEquals(payload.bytes(), admitted.canonicalPayload());
        assertEquals(2, admitted.counts().records()); assertEquals(1, admitted.counts().changedRecords());
        var sql = assertInstanceOf(studio.environment.server.export.TransactionTemplates.Result.Candidate.class,
                new studio.environment.server.export.TransactionTemplates().generate(admitted));
        assertFalse(sql.qualified());
        assertTrue(new String(sql.bytes(), StandardCharsets.US_ASCII).contains("160011"));
        assertFalse(new studio.environment.server.export.GuardedPackageInspector().generationAvailable());
    }
    private static final String OTHER_XML = "<?xml version='1.0'?><!-- untouched -->\r\n<unmapped><![CDATA[😀 é < &]]></unmapped>";
    private static HostedPlanService.ViewSnapshot multiDocument(boolean change) {
        var originalDefinition = definition(false).definition();
        var oldBinding = originalDefinition.bindings().getFirst();
        var binding = new studio.environment.core.definitionv2.NativeDefinition.Binding(oldBinding.id(), oldBinding.engine(), oldBinding.storage(),
                oldBinding.schema(), oldBinding.table(), oldBinding.keyColumn(), oldBinding.xmlColumn(), oldBinding.keyType(),
                List.of(oldBinding.documents().getFirst(), new studio.environment.core.definitionv2.NativeDefinition.Document("other", "2", List.of())));
        var definition = new studio.environment.core.definitionv3.NativeDefinition(originalDefinition.id(), originalDefinition.revision(), originalDefinition.logical(), List.of(binding));
        var checked = assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,
                new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(definition)).checked();
        var model = new PlanDefinition.V3(checked);
        var observedDocuments = new ArrayList<studio.environment.core.observation.ObservationResult.Document>();
        for (var doc : binding.documents()) {
            String xml = doc.id().equals("sheet") ? XML : OTHER_XML;
            observedDocuments.add(new studio.environment.core.observation.ObservationResult.Document(doc.id(),
                    new studio.environment.core.observation.ObservationResult.Key("int64", doc.key()), xml,
                    xml.getBytes(StandardCharsets.UTF_8).length, xml.length(), digest(xml)));
        }
        var observation = new studio.environment.core.observation.ObservationResult.Observation(V3PlanContentAdapterTest.FINGERPRINT,
                checked.logicalDigest(), checked.bindingDigests().get("mock-pg"), observedDocuments, Map.of());
        var adapter = new V3PlanContentAdapter();
        var current = assertInstanceOf(V3PlanContent.Result.Complete.class, adapter.project(model, "mock-pg", observation, new Cancellation())).content();
        var sourcePins = Map.of("sheet", digest(XML), "other", digest(OTHER_XML));
        var originalPin = new studio.environment.core.derived.DerivedInput.Pin(V3PlanContentAdapterTest.FINGERPRINT, checked.logicalDigest(), "mock-pg", checked.bindingDigests().get("mock-pg"), sourcePins);
        var decisionPin = new studio.environment.core.derived.DerivedInput.Pin("decision-2", checked.logicalDigest(), "mock-pg", checked.bindingDigests().get("mock-pg"), sourcePins);
        var draft = change ? new PlanPorts.Draft(intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Entered("beta"))), List.of()) : PlanPorts.Draft.empty();
        var target = assertInstanceOf(V3PlanContent.Result.Complete.class, adapter.materialize(model, originalPin, current, decisionPin, draft, new Cancellation())).content();
        var published = new PlanPorts.PublishedDefinition(view().definition().reference(), "a".repeat(64), model,
                List.of(new NativeCommand.Policy("mock-pg", "sheet", "protected-self-contained"),
                        new NativeCommand.Policy("mock-pg", "other", "protected-self-contained"),
                        new NativeCommand.Policy("another-binding", "other", "deny")));
        return new HostedPlanService.ViewSnapshot("2", published, "mock-pg", Optional.of(current), Optional.of(target), draft,
                Map.of(), Map.of(), Optional.of(new V3PlanPins(originalPin, decisionPin)));
    }
    private static HostedPlanService.ViewSnapshot copy(HostedPlanService.ViewSnapshot good, PlanPorts.PublishedDefinition definition,
            Optional<PlanPorts.Content> current, Optional<PlanPorts.Content> target, PlanPorts.Draft draft, Optional<V3PlanPins> pins) {
        return new HostedPlanService.ViewSnapshot(good.revision(), definition, good.binding(), current, target, draft, good.references(), good.displayHandles(), pins);
    }
    private static String decode(String hex) { return new String(HexFormat.of().parseHex(hex), StandardCharsets.UTF_8); }
}
