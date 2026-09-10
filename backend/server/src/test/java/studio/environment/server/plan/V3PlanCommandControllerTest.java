package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import studio.environment.core.plan.*;
import studio.environment.server.planning.V3CommandWireFixtures;
import studio.environment.server.session.HostedSessions;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

class V3PlanCommandControllerTest {
    static final class Fixture {
        final V3CommandWireFixtures core = new V3CommandWireFixtures();
        final String plan = core.inspected();
        final HostedSessions sessions = new HostedSessions(Clock.systemUTC(), List.of());
        final PlanRuntime runtime = new PlanRuntime(core.service, List.of(), (owner, id) -> true);
        final V3PlanCommandController controller = new V3PlanCommandController(runtime, sessions);
        MockHttpServletRequest body(V3PlanTransportTest.Context context, String json) {
            var request = V3PlanTransportTest.bodyRequest(context, json);
            request.setAttribute(HostedSessions.REQUEST_LEASE, core.lease); return request;
        }
        String edit() {
            return "{\"expectedRevision\":\"2\",\"requestId\":\"" + UUID.randomUUID() + "\",\"kind\":\"upsert-entity\",\"decision\":{\"kind\":\"retain\",\"entity\":{\"kind\":\"existing\",\"handle\":\""
                    + core.handle(plan, "2", "one") + "\"},\"fields\":{\"id\":{\"kind\":\"keep-observed\"},\"tone\":{\"kind\":\"entered\",\"text\":\"beta\"},\"finish\":{\"kind\":\"keep-observed\"}},\"references\":{}},\"placements\":[]}";
        }
        MockHttpServletRequest unread() {
            var request = new MockHttpServletRequest() {
                @Override public AsyncContext startAsync() { throw new AssertionError("UNADMITTED_ASYNC"); }
                @Override public ServletInputStream getInputStream() { throw new AssertionError("UNADMITTED_BODY"); }
            };
            request.setContentType("application/json"); request.setAttribute(HostedSessions.REQUEST_LEASE, core.lease); return request;
        }
    }
    @Test void actualPublicCommandProducesExactAckXmlAndReplay() throws Exception {
        var f = new Fixture(); String json = f.edit();
        for (int i = 0; i < 2; i++) {
            var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
            var response = V3PlanTransportTest.response(output);
            assertDoesNotThrow(() -> f.controller.command(f.plan, f.body(context, json), response));
            assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
            assertEquals(200, response.getStatus());
            assertEquals("{\"planId\":\"" + f.plan + "\",\"revision\":\"3\"}", output.bytes.toString(StandardCharsets.UTF_8));
        }
        var target = f.core.snapshot(f.plan, "3").target().orElseThrow();
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>", target.sources().getFirst().xml());
        assertEquals(List.of("alpha:y", "beta:x"), ((PlanContentEvidence.V3Target) target.evidence()).derived().graph().cooccurrences().stream()
                .map(edge -> edge.source().value() + ":" + edge.target().value()).toList());
    }
    @Test void busySemanticRollsBackExactScratchAndBusyScratchDoesNotInstallARecord() throws Exception {
        var f = new Fixture();
        var retained = f.runtime.transfers().admitSemantic(f.core.lease);
        try {
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.controller.command(f.plan, f.unread(), new MockHttpServletResponse())).code());
            try (var available = assertDoesNotThrow(() -> f.core.service.reserveCommand(f.core.lease, f.plan, V3))) {
                assertTrue(available.live());
            }
        } finally { retained.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, f.sessions); }
        try (var scratch = f.core.service.reserveCommand(f.core.lease, f.plan, V3)) {
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.controller.command(f.plan, f.unread(), new MockHttpServletResponse())).code());
            assertFalse(f.runtime.transfers().awaitingWork(f.core.lease));
        }
        assertEquals("2", f.core.service.viewV3(f.core.lease, Optional.of(f.plan)).summary().revision());
    }

    @Test void wrongVersionWinsBeforeBusyAdmissionsOrBodyAndSafeHandlerUsesNoOutput() throws Exception {
        var core = new PlanV1VersionBoundaryTest.Fixture(); var legacy = core.create(false);
        var runtime = new PlanRuntime(core.service, List.of(), (owner, id) -> true);
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var controller = new V3PlanCommandController(runtime, sessions);
        var request = new MockHttpServletRequest() {
            @Override public AsyncContext startAsync() { throw new AssertionError("WRONG_VERSION_ASYNC"); }
            @Override public ServletInputStream getInputStream() { throw new AssertionError("WRONG_VERSION_BODY"); }
        };
        request.setAttribute(HostedSessions.REQUEST_LEASE, core.lease); request.setContentType("application/json");
        var response = new MockHttpServletResponse() {
            @Override public ServletOutputStream getOutputStream() { throw new AssertionError("EARLY_OUTPUT"); }
        };
        var retained = runtime.transfers().admitSemantic(core.lease);
        try (var scratch = core.service.reserveCommand(core.lease, legacy.planId(), PlanDefinition.Version.V2)) {
            var refusal = assertThrows(PlanRefusal.class, () -> controller.command(legacy.planId(), request, response));
            assertEquals(PlanRefusal.Code.NOT_FOUND, refusal.code());
            controller.failure(refusal, response);
            assertEquals(404, response.getStatus()); assertEquals("NOT_FOUND", response.getHeader("X-Environment-Studio-Code"));
            assertEquals("0", response.getHeader("Content-Length")); assertEquals(0, response.getContentAsByteArray().length);
        } finally { retained.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, sessions); }
    }

    @Test void malformedAndTrailingCommandsCannotMutateAndBothBudgetsRecover() throws Exception {
        var f = new Fixture();
        for (String json : List.of("{", f.edit() + "{}")) {
            var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
            var response = V3PlanTransportTest.response(output);
            f.controller.command(f.plan, f.body(context, json), response);
            assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
            assertEquals(400, response.getStatus());
            assertEquals("2", f.core.service.viewV3(f.core.lease, Optional.of(f.plan)).summary().revision());
            assertTrue(f.core.snapshot(f.plan, "2").target().isEmpty());
            try (var available = assertDoesNotThrow(() -> f.core.service.reserveCommand(f.core.lease, f.plan, V3))) { assertTrue(available.live()); }
        }
    }

    @Test void heldAckRetainsBothBudgetsAndOutputErrorClosesOriginalScratchWithoutClaimingRollback() throws Exception {
        var f = new Fixture(); var context = new V3PlanTransportTest.Context();
        var output = new V3PlanTransportTest.Output(); output.ready = false;
        var response = V3PlanTransportTest.response(output);
        try {
            f.controller.command(f.plan, f.body(context, f.edit()), response);
            assertTrue(output.checked.await(3, TimeUnit.SECONDS));
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.core.service.reserveCommand(f.core.lease, f.plan, V3)).code());
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.runtime.transfers().admitSemantic(f.core.lease)).code());
            assertEquals("3", f.core.service.viewV3(f.core.lease, Optional.of(f.plan)).summary().revision());
            context.listener.onError(new AsyncEvent(context.value));
            assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.runtime.transfers(), f.core.lease);
            assertEquals(0, output.bytes.size());
            try (var available = assertDoesNotThrow(() -> f.core.service.reserveCommand(f.core.lease, f.plan, V3))) { assertTrue(available.live()); }
        } finally { output.ready = true; }
    }

}
