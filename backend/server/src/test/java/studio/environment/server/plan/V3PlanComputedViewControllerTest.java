package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.planning.V3ComputedWireFixtures;
import studio.environment.server.session.HostedSessions;

class V3PlanComputedViewControllerTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String XML = "<items><item id='one' tone='alpha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";
    record Fixture(V3ComputedWireFixtures core, PlanRuntime runtime, HostedSessions sessions, V3PlanComputedViewController controller) { }
    static Fixture fixture(boolean child, String xml, boolean failed) {
        var core = new V3ComputedWireFixtures(child, xml, failed);
        var runtime = new PlanRuntime(core.service, List.of(), (owner,id) -> true);
        var sessions = new HostedSessions(java.time.Clock.systemUTC(), List.of());
        return new Fixture(core, runtime, sessions, new V3PlanComputedViewController(runtime, sessions));
    }
    static void dispatch(V3PlanComputedViewController controller, V3PlanComputedReader.Route route, String plan,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        switch (route) {
            case NODES -> controller.nodes(plan, request, response);
            case MEMBERSHIPS -> controller.memberships(plan, request, response);
            case COOCCURRENCES -> controller.cooccurrences(plan, request, response);
            case RULES -> controller.rules(plan, request, response);
            case CONTRIBUTORS -> controller.contributors(plan, request, response);
        }
    }
    static tools.jackson.databind.JsonNode call(Fixture f, V3PlanComputedReader.Route route, Object body, int status) throws Exception {
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output(); var response = V3PlanTransportTest.response(output);
        var request = V3PlanTransportTest.bodyRequest(context, JSON.writeValueAsString(body));
        request.setAttribute(HostedSessions.REQUEST_LEASE, f.core.lease);
        dispatch(f.controller, route, f.core.plan, request, response);
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
        assertEquals(status, response.getStatus(), output.bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
        return JSON.readTree(output.bytes.toByteArray());
    }
    static Map<String,Object> page(String revision, String side) { return Map.of("revision", revision, "side", side, "offset", 0, "limit", 100); }
    static Map<String,Object> contributor(String revision, String side, Object selector) {
        return Map.of("revision", revision, "side", side, "selector", selector, "offset", 0, "limit", 100, "completeDocumentDisclosure", true);
    }
    @Test void returnedLargeKeyRoundTripsToCompleteChildSelectorAndFinalValueCoordinates() throws Exception {
        String value = "x".repeat(20_000) + "𐀀&";
        String xml = "<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='" + "x".repeat(20_000) + "𐀀&amp;'/></item></items>";
        var f = fixture(true, xml, false);
        var nodes = call(f, V3PlanComputedReader.Route.NODES, page("2", "current"), 200);
        assertEquals(2, nodes.get("total").asInt()); var key = nodes.get("items").get(1).get("key"); assertEquals(value, key.get("value").asString());
        var selector = Map.of("kind", "node", "key", key);
        var result = call(f, V3PlanComputedReader.Route.CONTRIBUTORS, contributor("2", "current", selector), 200);
        assertEquals(1, result.get("total").asInt()); var row = result.get("items").get(0);
        assertEquals(JSON.valueToTree(PlanViewProjection.reference(f.core.reference("one"))), row.get("physical"));
        var location = row.get("roles").get(0).get("location"); var pin = location.get("value"); var child = location.get("selector");
        assertEquals("2", pin.get("elementIndex").asString()); assertEquals("p:value", pin.get("qualifiedName").asString());
        assertEquals(value, pin.get("decodedValue").asString()); assertEquals("1", child.get("parentElementIndex").asString());
        assertEquals(JSON.valueToTree(Map.of("namespaceUri", "urn:props", "localName", "entry")), child.get("element"));
        var discriminator = child.get("discriminator"); assertEquals("p:key", discriminator.get("qualifiedName").asString());
        assertEquals("tone", xml.substring(discriminator.get("valueStart").asInt(), discriminator.get("valueEnd").asInt()));
        assertEquals("x".repeat(20_000) + "𐀀&amp;", xml.substring(pin.get("valueStart").asInt(), pin.get("valueEnd").asInt()));
        var reference = assertInstanceOf(PlanCommand.Ref.Existing.class, f.core.reference("one"));
        var fields = Map.<String,studio.environment.core.planning.TargetIntent.FieldValue>of("id", new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(), "finish", new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(), "tone", new studio.environment.core.planning.TargetIntent.FieldValue.Entered("beta & 𐀀"));
        var decision = new PlanCommand.Change(new PlanCommand.Entity.Retain(reference, fields, Map.of()), List.of());
        try (var command = f.core.service.reserveCommand(f.core.lease, f.core.plan, PlanDefinition.Version.V3)) {
            command.execute(new PlanCommand(new HostedPlanService.Mutation("2", UUID.randomUUID().toString()), new PlanCommand.Action.Upsert(decision)));
        }
        var finalKey = Map.of("kind", "node", "key", V3PlanComputedReaderTest.key("beta & 𐀀"));
        var target = call(f, V3PlanComputedReader.Route.CONTRIBUTORS, contributor("3", "target", finalKey), 200).get("items").get(0);
        var finalPin = target.get("roles").get(0).get("location").get("value");
        String expected = "<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='beta &amp; 𐀀'/></item></items>";
        assertEquals(expected, f.core.snapshot("3").target().orElseThrow().sources().getFirst().xml());
        assertEquals("beta &amp; 𐀀", expected.substring(finalPin.get("valueStart").asInt(), finalPin.get("valueEnd").asInt()));
        assertEquals(JSON.valueToTree(PlanViewProjection.reference(reference)), target.get("physical"));
    }
    @Test void unsignedUnicodeOrderOptionalAbsenceAndFailedCurrentRulesRemainInspectable() throws Exception {
        var f = fixture(false, "<items><item id='one' tone='𐀀'/><item id='two' tone='\ue000'/><item id='three' tone='é'/><item id='four' tone='é'/><item id='five'/></items>", false);
        var nodes = call(f, V3PlanComputedReader.Route.NODES, page("2", "current"), 200);
        var values = new ArrayList<String>(); for (var node : nodes.get("items")) values.add(node.get("key").get("value").asString());
        assertEquals(List.of("é", "é", "\ue000", "𐀀"), values); assertEquals(4, nodes.get("total").asInt());
        assertEquals(0, call(f, V3PlanComputedReader.Route.COOCCURRENCES, page("2", "current"), 200).get("total").asInt());
        var failed = fixture(false, XML, true); var rules = call(failed, V3PlanComputedReader.Route.RULES, page("2", "current"), 200);
        assertEquals("FAIL", rules.get("items").get(0).get("outcome").asString()); assertEquals("2", rules.get("items").get(0).get("actual").asString());
        assertEquals("1", rules.get("items").get(0).get("maximum").asString()); assertEquals("PASS", rules.get("items").get(1).get("outcome").asString());
        assertEquals("INCOMPLETE_TARGET", call(failed, V3PlanComputedReader.Route.NODES, page("2", "target"), 422).get("code").asString());
        assertTrue(failed.core.snapshot("2").target().isEmpty());
    }
    @Test void everyRouteRollsBackScratchAndRejectsV2BeforeUnadmittedBodyAccess() throws Exception {
        var f = fixture(false, XML, false); var request = new org.springframework.mock.web.MockHttpServletRequest() {
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("UNADMITTED_BODY"); }
            @Override public jakarta.servlet.AsyncContext startAsync() { throw new AssertionError("UNADMITTED_ASYNC"); }
        };
        request.setContentType("application/json"); request.setAttribute(HostedSessions.REQUEST_LEASE, f.core.lease);
        var held = f.runtime.transfers().admitSemantic(f.core.lease);
        try {
            for (var route : V3PlanComputedReader.Route.values()) {
                assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class, () -> dispatch(f.controller, route, f.core.plan, request, new org.springframework.mock.web.MockHttpServletResponse())).code());
                try (var recovered = assertDoesNotThrow(() -> f.core.service.reserveView(f.core.lease, f.core.plan, PlanDefinition.Version.V3))) { assertTrue(recovered.live()); }
            }
        } finally { held.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, f.sessions); }
        var legacy = new PlanV1VersionBoundaryTest.Fixture(); var old = legacy.create(false);
        var runtime = new PlanRuntime(legacy.service, List.of(), (owner,id) -> true); var controller = new V3PlanComputedViewController(runtime, f.sessions);
        var capacity = runtime.transfers().admitSemantic(legacy.lease);
        try { for (var route : V3PlanComputedReader.Route.values()) assertEquals(PlanRefusal.Code.NOT_FOUND, assertThrows(PlanRefusal.class,
                () -> dispatch(controller, route, old.planId(), PlanV1VersionBoundaryTest.unread(legacy), new org.springframework.mock.web.MockHttpServletResponse())).code()); }
        finally { capacity.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, f.sessions); }
    }
    @Test void heldComputedOutputCannotOutliveOriginalPinOrReleaseScratchEarly() throws Exception {
        for (var route : V3PlanComputedReader.Route.values()) {
            var f = fixture(false, XML, false); var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output(); output.ready = false;
            String operation = null;
            try {
                Object body = route == V3PlanComputedReader.Route.CONTRIBUTORS ? contributor("2", "current", Map.of("kind", "node", "key", V3PlanComputedReaderTest.key("alpha"))) : page("2", "current");
                var request = V3PlanTransportTest.bodyRequest(context, JSON.writeValueAsString(body)); request.setAttribute(HostedSessions.REQUEST_LEASE, f.core.lease);
                dispatch(f.controller, route, f.core.plan, request, V3PlanTransportTest.response(output));
                assertTrue(output.checked.await(3, TimeUnit.SECONDS));
                assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class, () -> f.core.service.reserveView(f.core.lease, f.core.plan, PlanDefinition.Version.V3)).code());
                operation = f.core.service.reserve(f.core.lease, f.core.plan, new HostedPlanService.Mutation("2", UUID.randomUUID().toString()), PlanDefinition.Version.V3).operationId().orElseThrow();
                assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease); assertEquals(0, output.bytes.size());
            } finally { output.ready = true; if (operation != null) f.core.service.cancel(f.core.lease, operation, PlanDefinition.Version.V3); }
            try (var recovered = f.core.service.reserveView(f.core.lease, f.core.plan, PlanDefinition.Version.V3)) { assertTrue(recovered.live()); }
        }
    }
    @Test void selfCooccurrenceRetainsBothRolesAndFreshReplacementKeepsItsOwnReference() throws Exception {
        var core = new V3ComputedWireFixtures(false, XML, false, true);
        var runtime = new PlanRuntime(core.service, List.of(), (owner,id) -> true); var sessions = new HostedSessions(java.time.Clock.systemUTC(), List.of());
        var f = new Fixture(core, runtime, sessions, new V3PlanComputedViewController(runtime, sessions));
        var key = V3PlanComputedReaderTest.key("alpha"); var selector = Map.of("kind", "cooccurrence", "relation", "pair", "source", key, "target", key);
        var pair = call(f, V3PlanComputedReader.Route.CONTRIBUTORS, contributor("2", "current", selector), 200);
        assertEquals(2, pair.get("total").asInt());
        for (var row : pair.get("items")) { assertEquals(2, row.get("roles").size()); assertEquals(row.get("roles").get(0), row.get("roles").get(1)); }
        var old = assertInstanceOf(PlanCommand.Ref.Existing.class, core.reference("one")); var fresh = new PlanCommand.Ref.Fresh("replacement", "item");
        var fields = Map.<String,studio.environment.core.planning.TargetIntent.FieldValue>of("id", new studio.environment.core.planning.TargetIntent.FieldValue.Entered("one"), "tone", new studio.environment.core.planning.TargetIntent.FieldValue.Entered("gamma"), "finish", new studio.environment.core.planning.TargetIntent.FieldValue.Entered("z"));
        var source = core.snapshot("2").current().orElseThrow().sources().getFirst();
        var create = new PlanCommand.Change(new PlanCommand.Entity.Create(fresh, fields, Map.of()), List.of(new PlanCommand.Placement(fresh, "sheet", "items", new PlanCommand.Parent.Existing("sheet", source.digest(), "0"))));
        var remove = new PlanCommand.Change(new PlanCommand.Entity.Remove(old), List.of());
        try (var command = core.service.reserveCommand(core.lease, core.plan, PlanDefinition.Version.V3)) {
            command.execute(new PlanCommand(new HostedPlanService.Mutation("2", UUID.randomUUID().toString()), new PlanCommand.Action.Batch(List.of(remove, create), List.of())));
        }
        var current = call(f, V3PlanComputedReader.Route.CONTRIBUTORS, contributor("3", "current", selector), 200); assertEquals(2, current.get("total").asInt());
        var gamma = Map.of("kind", "membership", "relation", "has-tone", "physical", PlanViewProjection.reference(fresh), "computed", V3PlanComputedReaderTest.key("gamma"));
        var target = call(f, V3PlanComputedReader.Route.CONTRIBUTORS, contributor("3", "target", gamma), 200);
        assertEquals(1, target.get("total").asInt()); assertEquals(JSON.valueToTree(PlanViewProjection.reference(fresh)), target.get("items").get(0).get("physical"));
        assertEquals("NOT_FOUND", call(f, V3PlanComputedReader.Route.CONTRIBUTORS, contributor("3", "current", gamma), 404).get("code").asString());
        var wrong = new LinkedHashMap<String,Object>(gamma); wrong.put("physical", PlanViewProjection.reference(old));
        assertEquals("NOT_FOUND", call(f, V3PlanComputedReader.Route.CONTRIBUTORS, contributor("3", "target", wrong), 404).get("code").asString());
    }
    @Test void closedRuleEncodingPreservesLargeDecimalStringsAndCancellation() {
        var maximum = new java.math.BigInteger("9007199254740993123456789");
        var rule = new studio.environment.core.derived.DerivedResult.RuleCheck(studio.environment.core.derived.DerivedResult.RuleKind.ENTITY_COUNT, "tone-count", Optional.empty(), java.math.BigInteger.TWO, java.math.BigInteger.ZERO, maximum, studio.environment.core.Outcome.PASS);
        var page = new V3PlanComputedViews.Page<>("2", 1, 0, java.util.OptionalInt.empty(), List.of(rule));
        var value = JSON.valueToTree(V3PlanComputedEncoding.page(page, () -> {})).get("items").get(0);
        assertTrue(value.get("actual").isString()); assertTrue(value.get("maximum").isString()); assertEquals("9007199254740993123456789", value.get("maximum").asString()); assertTrue(value.get("source").isNull());
        assertEquals(7, value.size()); assertEquals(PlanRefusal.Code.CANCELLED, assertThrows(PlanRefusal.class, () -> V3PlanComputedEncoding.page(page, () -> { throw new PlanRefusal(PlanRefusal.Code.CANCELLED); })).code());
    }
}
