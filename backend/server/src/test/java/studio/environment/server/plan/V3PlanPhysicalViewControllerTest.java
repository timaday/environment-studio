package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class V3PlanPhysicalViewControllerTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    @Test void originalInventoryAndPagedEntitiesExcludeComputedPartition() throws Exception {
        var f = new V3PlanMaterializationControllerTest.Fixture();
        var controller = new V3PlanPhysicalViewController(f.runtime, f.sessions);
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var response = V3PlanTransportTest.response(output);
        assertDoesNotThrow(() -> controller.documents(f.plan, f.body(context, "{\"revision\":\"2\"}"), response));
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
        assertEquals(200, response.getStatus()); var inventory = JSON.readTree(output.bytes.toByteArray());
        assertEquals("2", inventory.get("revision").asString()); assertEquals(1, inventory.get("documents").size());
        var document = inventory.get("documents").get(0);
        assertEquals("sheet", document.get("documentId").asString());
        assertEquals(f.core.snapshot(f.plan, "2").current().orElseThrow().sources().getFirst().digest(), document.get("currentDigest").asString());
        assertTrue(document.get("targetDigest").isNull()); assertTrue(document.get("changed").isNull());
        var page = page(f, controller, "2", "current", 0, 2);
        assertEquals(3, page.get("total").asInt()); assertEquals(2, page.get("items").size()); assertEquals(2, page.get("nextOffset").asInt());
        for (var entity : page.get("items")) assertEquals("item", entity.get("typeId").asString());
        var beyond = page(f, controller, "2", "current", 50_000, 100);
        assertEquals(3, beyond.get("total").asInt()); assertEquals(0, beyond.get("items").size()); assertTrue(beyond.get("nextOffset").isNull());
    }
    @Test void returnedOpaqueReferenceSurvivesIdentityAndPublicValueEditInTargetPage() throws Exception {
        var f = new V3PlanMaterializationControllerTest.Fixture();
        var controller = new V3PlanPhysicalViewController(f.runtime, f.sessions);
        var original = page(f, controller, "2", "current", 0, 100);
        JsonNode one = null;
        for (var item : original.get("items")) if ("one".equals(field(item, "id").get("value").asString())) one = item;
        assertNotNull(one); var ref = one.get("entity");
        var decision = Map.of("kind", "retain", "entity", ref, "fields", Map.of(
                "id", Map.of("kind", "entered", "text", "renamed"),
                "tone", Map.of("kind", "entered", "text", "violet & 𐀀"),
                "finish", Map.of("kind", "keep-observed")), "references", Map.of());
        String wire = JSON.writeValueAsString(Map.of("expectedRevision", "2", "requestId", UUID.randomUUID().toString(),
                "kind", "upsert-entity", "decision", decision, "placements", List.of()));
        try (var admission = f.core.service.reserveCommand(f.core.lease, f.plan, studio.environment.core.plan.PlanDefinition.Version.V3)) {
            assertEquals("3", admission.execute(new PlanCommandReader().read(new java.io.ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)))).revision());
        }
        var target = page(f, controller, "3", "target", 0, 100);
        assertEquals(3, target.get("total").asInt());
        JsonNode renamed = null;
        for (var item : target.get("items")) if ("renamed".equals(field(item, "id").get("value").asString())) renamed = item;
        assertNotNull(renamed); assertEquals(ref, renamed.get("entity"));
        assertEquals("violet & 𐀀", field(renamed, "tone").get("value").asString());
        assertFalse(field(renamed, "tone").get("masked").asBoolean());
        assertEquals(original.get("items"), page(f, controller, "3", "current", 0, 100).get("items"));
        assertEquals("<items><!-- mock -->\r\n<item id='renamed' tone='violet &amp; 𐀀' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",
                f.core.snapshot(f.plan, "3").target().orElseThrow().sources().getFirst().xml());
    }
    @Test void absentTargetAndInvalidPagingUseOwnedErrorsWithoutFabricatingEmptyGraph() throws Exception {
        var f = new V3PlanMaterializationControllerTest.Fixture();
        var controller = new V3PlanPhysicalViewController(f.runtime, f.sessions);
        for (String json : List.of("{\"revision\":\"2\",\"side\":\"target\",\"offset\":0,\"limit\":1}",
                "{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":101}",
                "{\"revision\":\"9\",\"side\":\"current\",\"offset\":0,\"limit\":1}")) {
            var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
            var response = V3PlanTransportTest.response(output);
            controller.entities(f.plan, f.body(context, json), response);
            assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
            assertEquals(json.contains("target") ? 422 : json.contains("101") ? 400 : 409, response.getStatus());
            var result = JSON.readTree(output.bytes.toByteArray()); assertTrue(result.has("code")); assertFalse(result.has("items"));
            assertTrue(f.core.snapshot(f.plan, "2").target().isEmpty());
        }
    }
    @Test void heldPageOutputRetainsScratchAndInspectionReplacementAbortsOriginalPin() throws Exception {
        var f = new V3PlanMaterializationControllerTest.Fixture();
        var controller = new V3PlanPhysicalViewController(f.runtime, f.sessions);
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output(); output.ready = false;
        String operation = null;
        try {
            controller.entities(f.plan, f.body(context, "{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":100}"), V3PlanTransportTest.response(output));
            assertTrue(output.checked.await(3, TimeUnit.SECONDS));
            assertThrows(studio.environment.core.plan.PlanRefusal.class, () -> f.core.service.reserveView(f.core.lease, f.plan, studio.environment.core.plan.PlanDefinition.Version.V3));
            operation = f.core.service.reserve(f.core.lease, f.plan, new studio.environment.core.plan.HostedPlanService.Mutation("2", UUID.randomUUID().toString()), studio.environment.core.plan.PlanDefinition.Version.V3).operationId().orElseThrow();
            assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
            assertEquals(0, output.bytes.size());
        } finally { output.ready = true; if (operation != null) f.core.service.cancel(f.core.lease, operation, studio.environment.core.plan.PlanDefinition.Version.V3); }
    }
    @Test void bothRouteAdmissionsRollbackBeforeBodyAndWrongVersionPrecedesCapacity() throws Exception {
        var f = new V3PlanMaterializationControllerTest.Fixture();
        var controller = new V3PlanPhysicalViewController(f.runtime, f.sessions);
        var request = new org.springframework.mock.web.MockHttpServletRequest() {
            @Override public jakarta.servlet.AsyncContext startAsync() { throw new AssertionError("UNADMITTED_ASYNC"); }
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("UNADMITTED_BODY"); }
        };
        request.setContentType("application/json"); request.setAttribute(studio.environment.server.session.HostedSessions.REQUEST_LEASE, f.core.lease);
        var retained = f.runtime.transfers().admitSemantic(f.core.lease);
        try {
            for (boolean documents : List.of(true, false)) {
                assertEquals(studio.environment.core.plan.PlanRefusal.Code.CAPACITY, assertThrows(studio.environment.core.plan.PlanRefusal.class, () -> {
                    if (documents) controller.documents(f.plan, request, new org.springframework.mock.web.MockHttpServletResponse());
                    else controller.entities(f.plan, request, new org.springframework.mock.web.MockHttpServletResponse());
                }).code());
                try (var recovered = assertDoesNotThrow(() -> f.core.service.reserveView(f.core.lease, f.plan, studio.environment.core.plan.PlanDefinition.Version.V3))) { assertTrue(recovered.live()); }
            }
        } finally { retained.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, f.sessions); }
        try (var busy = f.core.service.reserveView(f.core.lease, f.plan, studio.environment.core.plan.PlanDefinition.Version.V3)) {
            assertEquals(studio.environment.core.plan.PlanRefusal.Code.CAPACITY, assertThrows(studio.environment.core.plan.PlanRefusal.class,
                    () -> controller.documents(f.plan, request, new org.springframework.mock.web.MockHttpServletResponse())).code());
            assertFalse(f.runtime.transfers().awaitingWork(f.core.lease));
        }
        var legacy = new PlanV1VersionBoundaryTest.Fixture(); var plan = legacy.create(false);
        var runtime = new PlanRuntime(legacy.service, List.of(), (owner,id) -> true);
        var wrong = new V3PlanPhysicalViewController(runtime, f.sessions); var slot = runtime.transfers().admitSemantic(legacy.lease);
        try {
            assertEquals(studio.environment.core.plan.PlanRefusal.Code.NOT_FOUND, assertThrows(studio.environment.core.plan.PlanRefusal.class,
                    () -> wrong.entities(plan.planId(), PlanV1VersionBoundaryTest.unread(legacy), new org.springframework.mock.web.MockHttpServletResponse())).code());
        } finally { slot.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, f.sessions); }
    }
    @Test void materializedUnchangedInventoryReportsEqualDigestsAndFalseChange() throws Exception {
        var f = new V3PlanMaterializationControllerTest.Fixture();
        var materialize = new V3PlanTransportTest.Context();
        f.controller.materialize(f.plan, f.body(materialize, "{\"revision\":\"2\"}"), V3PlanTransportTest.response(new V3PlanTransportTest.Output()));
        assertTrue(materialize.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        new V3PlanPhysicalViewController(f.runtime, f.sessions).documents(f.plan, f.body(context, "{\"revision\":\"2\"}"), V3PlanTransportTest.response(output));
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
        var document = JSON.readTree(output.bytes.toByteArray()).get("documents").get(0);
        assertEquals(document.get("currentDigest"), document.get("targetDigest")); assertFalse(document.get("changed").asBoolean());
    }
    static JsonNode field(JsonNode item, String id) {
        for (var field : item.get("fields")) if (id.equals(field.get("fieldId").asString())) return field;
        throw new AssertionError("missing field " + id);
    }
    static JsonNode page(V3PlanMaterializationControllerTest.Fixture f, V3PlanPhysicalViewController controller,
            String revision, String side, int offset, int limit) throws Exception {
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var response = V3PlanTransportTest.response(output);
        controller.entities(f.plan, f.body(context, JSON.writeValueAsString(Map.of("revision", revision, "side", side, "offset", offset, "limit", limit))), response);
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
        assertEquals(200, response.getStatus()); return JSON.readTree(output.bytes.toByteArray());
    }
}
