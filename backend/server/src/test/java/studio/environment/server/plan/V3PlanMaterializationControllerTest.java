package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import studio.environment.core.plan.*;
import studio.environment.server.planning.V3CommandWireFixtures;
import studio.environment.server.session.HostedSessions;

class V3PlanMaterializationControllerTest {
    static final class Fixture {
        final V3CommandWireFixtures core = new V3CommandWireFixtures();
        final String plan = core.inspected();
        final HostedSessions sessions = new HostedSessions(Clock.systemUTC(), List.of());
        final PlanRuntime runtime = new PlanRuntime(core.service, List.of(), (owner, id) -> true);
        final V3PlanMaterializationController controller = new V3PlanMaterializationController(runtime, sessions);
        MockHttpServletRequest body(V3PlanTransportTest.Context context, String json) {
            var request = V3PlanTransportTest.bodyRequest(context, json);
            request.setAttribute(HostedSessions.REQUEST_LEASE, core.lease); return request;
        }
    }
    @Test void unchangedCurrentMaterializesAtItsOriginalRevisionWithExactXmlAndCompleteState() throws Exception {
        var f = new Fixture(); assertTrue(f.core.snapshot(f.plan, "2").target().isEmpty());
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var response = V3PlanTransportTest.response(output);
        assertDoesNotThrow(() -> f.controller.materialize(f.plan, f.body(context, "{\"revision\":\"2\"}"), response));
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
        assertEquals(200, response.getStatus());
        assertEquals("{\"revision\":\"2\",\"state\":\"COMPLETE\",\"complete\":true,\"diagnostics\":[]}", output.bytes.toString(StandardCharsets.UTF_8));
        var snapshot = f.core.snapshot(f.plan, "2");
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>", snapshot.target().orElseThrow().sources().getFirst().xml());
        assertInstanceOf(PlanContentEvidence.V3Target.class, snapshot.target().orElseThrow().evidence());
    }
    @Test void malformedAndStalePrePinRequestsUseOwnedErrorsAndReleaseBothAdmissions() throws Exception {
        var f = new Fixture();
        for (String json : List.of("{", "{\"revision\":\"2\"}{}", "{\"revision\":\"9\"}")) {
            var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
            var response = V3PlanTransportTest.response(output);
            f.controller.materialize(f.plan, f.body(context, json), response);
            assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
            assertEquals(json.contains("9") ? 409 : 400, response.getStatus());
            assertFalse(output.bytes.toString(StandardCharsets.UTF_8).isEmpty());
            assertTrue(f.core.snapshot(f.plan, "2").target().isEmpty());
        }
    }
    @Test void bothBusyBudgetsRefuseBeforeBodyAndRollbackOnlyAcquiredView() throws Exception {
        var f = new Fixture();
        var request = new MockHttpServletRequest() {
            @Override public jakarta.servlet.AsyncContext startAsync() { throw new AssertionError("UNADMITTED_ASYNC"); }
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("UNADMITTED_BODY"); }
        };
        request.setContentType("application/json"); request.setAttribute(HostedSessions.REQUEST_LEASE, f.core.lease);
        var retained = f.runtime.transfers().admitSemantic(f.core.lease);
        try {
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.controller.materialize(f.plan, request, new org.springframework.mock.web.MockHttpServletResponse())).code());
            try (var recovered = assertDoesNotThrow(() -> f.core.service.reserveView(f.core.lease, f.plan, PlanDefinition.Version.V3))) {
                assertTrue(recovered.live());
            }
        } finally { retained.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, f.sessions); }
        try (var busy = f.core.service.reserveView(f.core.lease, f.plan, PlanDefinition.Version.V3)) {
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.controller.materialize(f.plan, request, new org.springframework.mock.web.MockHttpServletResponse())).code());
            assertFalse(f.runtime.transfers().awaitingWork(f.core.lease));
        }
    }
    @Test void outputRemainsInsidePinnedRunAndInspectionChangeAbortsWithoutErrorFallback() throws Exception {
        var f = new Fixture(); var context = new V3PlanTransportTest.Context();
        var output = new V3PlanTransportTest.Output(); output.ready = false;
        var response = V3PlanTransportTest.response(output); String operation = null;
        try {
            f.controller.materialize(f.plan, f.body(context, "{\"revision\":\"2\"}"), response);
            assertTrue(output.checked.await(3, TimeUnit.SECONDS));
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.core.service.reserveView(f.core.lease, f.plan, PlanDefinition.Version.V3)).code());
            operation = f.core.service.reserve(f.core.lease, f.plan,
                    new HostedPlanService.Mutation("2", UUID.randomUUID().toString()), PlanDefinition.Version.V3).operationId().orElseThrow();
            assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
            assertEquals(0, output.bytes.size(), "lost pinned context must not yield a replacement error body");
        } finally {
            output.ready = true;
            if (operation != null) f.core.service.cancel(f.core.lease, operation, PlanDefinition.Version.V3);
        }
    }

    @Test void actualUnresolvedDraftReturnsIncompleteReferencesAtItsUnchangedRevision() throws Exception {
        var f = new Fixture();
        var existing = new PlanCommand.Ref.Existing(f.core.handle(f.plan, "2", "one"));
        var fields = Map.<String,studio.environment.core.planning.TargetIntent.FieldValue>of(
                "id", new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),
                "tone", new studio.environment.core.planning.TargetIntent.FieldValue.Unresolved(),
                "finish", new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved());
        var command = new PlanCommand(new HostedPlanService.Mutation("2", UUID.randomUUID().toString()),
                new PlanCommand.Action.Upsert(new PlanCommand.Change(new PlanCommand.Entity.Retain(existing, fields, Map.of()), List.of())));
        try (var admission = f.core.service.reserveCommand(f.core.lease, f.plan, PlanDefinition.Version.V3)) {
            assertEquals("3", admission.execute(command).revision());
        }
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var response = V3PlanTransportTest.response(output);
        f.controller.materialize(f.plan, f.body(context, "{\"revision\":\"3\"}"), response);
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
        assertEquals(200, response.getStatus());
        assertEquals("{\"revision\":\"3\",\"state\":\"INCOMPLETE\",\"complete\":false,\"diagnostics\":[\"by-tone\"]}", output.bytes.toString(StandardCharsets.UTF_8));
        assertTrue(f.core.snapshot(f.plan, "3").target().isEmpty());
    }
    @Test void wrongVersionAndMissingInspectionRemainDistinctRefusals() throws Exception {
        var core = new PlanV1VersionBoundaryTest.Fixture(); var legacy = core.create(false);
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var runtime = new PlanRuntime(core.service, List.of(), (owner, id) -> true);
        var controller = new V3PlanMaterializationController(runtime, sessions);
        var request = PlanV1VersionBoundaryTest.unread(core);
        var held = runtime.transfers().admitSemantic(core.lease);
        try {
            assertEquals(PlanRefusal.Code.NOT_FOUND, assertThrows(PlanRefusal.class,
                    () -> controller.materialize(legacy.planId(), request, new org.springframework.mock.web.MockHttpServletResponse())).code());
        } finally { held.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, sessions); }
        core.service.discard(core.lease, legacy.planId(), new HostedPlanService.Mutation("1", UUID.randomUUID().toString()));
        var v3 = core.create(true); var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var body = V3PlanTransportTest.bodyRequest(context, "{\"revision\":\"1\"}");
        body.setAttribute(HostedSessions.REQUEST_LEASE, core.lease); var response = V3PlanTransportTest.response(output);
        controller.materialize(v3.planId(), body, response);
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(runtime.transfers(), core.lease);
        assertEquals(422, response.getStatus());
        assertEquals("{\"code\":\"INSPECTION_REQUIRED\"}", output.bytes.toString(StandardCharsets.UTF_8));
    }

}
