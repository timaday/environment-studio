package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.planning.V3PlanHttpWitnesses;
import studio.environment.server.session.HostedSessions;

class V3PlanMaterializationTransportTest {
    static final class Fixture {
        final SessionLedger ledger = new SessionLedger(Clock.systemUTC(), ignored -> {});
        final SessionLedger.Lease lease = ((SessionLedger.Accepted) ledger.admit("invented-materialization", new Owner("https://mock.invalid", "materialization"))).lease();
        final AtomicReference<V3PlanContent.Result> result = new AtomicReference<>();
        volatile Runnable beforeReturn = () -> {};
        final HostedPlanService service;
        final HostedSessions sessions = new HostedSessions(Clock.systemUTC(), List.of());
        final V3PlanTransfers transfers = new V3PlanTransfers();
        final String plan;
        Fixture() {
            var definition = new PlanDefinition.V3(V3PlanHttpWitnesses.definition());
            var reference = new NativeCommand.Reference("00000000-0000-4000-8000-000000000292", "2");
            var workspace = new Workspace() {
                public PublishedDefinition definition(Owner owner, NativeCommand.Reference selected) { throw new AssertionError("V2_FALLBACK"); }
                public PublishedDefinition definitionV3(Owner owner, NativeCommand.Reference selected) {
                    assertEquals(lease.owner(), owner); assertEquals(reference, selected);
                    return new PublishedDefinition(reference, "invented-result-witness", definition, List.of());
                }
                public PublishedProfile profile(Owner owner, NativeCommand.Reference selected, PublishedDefinition model) { throw new AssertionError("PROFILE_LOOKUP"); }
            };
            var observation = new ObservationPort() {
                public ObservationResult observe(Selection selected, TransientCredentials credentials, Cancellation cancelled) { throw new AssertionError("RESERVATION_REQUIRED"); }
                public Reservation reserveV3(V3Selection selected) {
                    return new Reservation.Admitted(new Permit() {
                        public ObservationResult observe(TransientCredentials credentials, Cancellation cancelled) { credentials.close(); return V3PlanHttpWitnesses.observation(); }
                        public void close() {}
                    });
                }
            };
            var actual = new PlanContentAdapter();
            var content = new ContentAdapter() {
                public ContentResult project(PublishedDefinition d, String b, ObservationResult.Observation o) { return actual.project(d, b, o); }
                public ContentResult project(PublishedDefinition d, String b, ObservationResult.Observation o, ObservationPort.Cancellation c) { return actual.project(d, b, o, c); }
                public ContentResult materialize(PublishedDefinition d, String b, Content c, Draft draft) { return actual.materialize(d, b, c, draft); }
                public Capture capture(PublishedDefinition d, String b, Content c, ProfileCapture.Command command) { return actual.capture(d, b, c, command); }
                public V3PlanContent.Result materializeV3(PublishedDefinition d, DerivedInput.Pin original, Content c, DerivedInput.Pin target, Draft draft, ObservationPort.Cancellation cancellation) {
                    beforeReturn.run(); return Objects.requireNonNull(result.get());
                }
            };
            service = new HostedPlanService(ledger::guard, workspace, Map.of("mock-destination", new Destination("mock-destination",
                    studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL, observation)), content, System::nanoTime);
            plan = service.createV3(lease, UUID.randomUUID().toString(), reference, "mock-pg", "mock-destination").planId();
            String operation = service.reserve(lease, plan, new HostedPlanService.Mutation("1", UUID.randomUUID().toString()), PlanDefinition.Version.V3).operationId().orElseThrow();
            var status = service.submit(lease, operation, (user, password) -> { user[0] = 'a'; password[0] = 'B'; return new CredentialLengths(1, 1); });
            assertEquals(HostedPlanService.Phase.SUCCEEDED, status.phase());
        }
    }
    @Test void wholeLargeIncompleteReferenceSetUsesFullViewCeilingWithoutTruncation() throws Exception {
        var f = new Fixture();
        var references = java.util.stream.IntStream.range(0, 256)
                .mapToObj(index -> "t".repeat(64) + "/" + "f".repeat(61) + String.format(Locale.ROOT, "%03d", index)).toList();
        f.result.set(new V3PlanContent.Result.Incomplete(references));
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var response = V3PlanTransportTest.response(output);
        var view = f.service.reserveView(f.lease, f.plan, PlanDefinition.Version.V3);
        new V3PlanTransport(f.service, f.sessions).materializationBody(f.lease, f.plan,
                V3PlanTransportTest.bodyRequest(context, "{\"revision\":\"2\"}"), response, f.transfers.admitSemantic(f.lease), view);
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.transfers, f.lease);
        assertEquals(200, response.getStatus()); assertTrue(output.bytes.size() > 32_768);
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(output.bytes.toByteArray());
        assertEquals("INCOMPLETE", json.get("state").asString()); assertFalse(json.get("complete").asBoolean());
        assertEquals(references, json.get("diagnostics").valueStream().map(node -> node.asString()).toList());
        assertFalse(f.service.viewV3(f.lease, Optional.of(f.plan)).summary().targetComplete());
        try (var next = f.service.reserveView(f.lease, f.plan, PlanDefinition.Version.V3)) { assertTrue(next.live()); }
    }
    @Test void refusedEngineOutcomeIsExplicitDataAndDoesNotBecomeIncompleteOrSuccess() throws Exception {
        var f = new Fixture(); f.result.set(new V3PlanContent.Result.Refused("INVALID_DERIVED_IDENTITY"));
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var response = V3PlanTransportTest.response(output);
        var view = f.service.reserveView(f.lease, f.plan, PlanDefinition.Version.V3);
        new V3PlanTransport(f.service, f.sessions).materializationBody(f.lease, f.plan,
                V3PlanTransportTest.bodyRequest(context, "{\"revision\":\"2\"}"), response, f.transfers.admitSemantic(f.lease), view);
        assertTrue(context.complete.await(3, TimeUnit.SECONDS)); V3PlanTransportTest.settled(f.transfers, f.lease);
        assertEquals(200, response.getStatus());
        assertEquals("{\"revision\":\"2\",\"state\":\"REFUSED\",\"complete\":false,\"diagnostics\":[\"INVALID_DERIVED_IDENTITY\"]}", output.bytes.toString(StandardCharsets.UTF_8));
        assertFalse(f.service.viewV3(f.lease, Optional.of(f.plan)).summary().targetComplete());
    }
    @Test void lostPinDuringMaterializationCannotFallBackToLeaseOnlyOwnedError() throws Exception {
        var f = new Fixture(); f.result.set(new V3PlanContent.Result.Refused("RESOURCE_LIMIT"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var released = new java.util.concurrent.CountDownLatch(1);
        f.beforeReturn = () -> {
            entered.countDown();
            try { assertTrue(released.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        };
        var context = new V3PlanTransportTest.Context(); var output = new V3PlanTransportTest.Output();
        var acquired = new java.util.concurrent.atomic.AtomicInteger();
        var response = new org.springframework.mock.web.MockHttpServletResponse() {
            @Override public jakarta.servlet.ServletOutputStream getOutputStream() { acquired.incrementAndGet(); return output; }
        };
        var view = f.service.reserveView(f.lease, f.plan, PlanDefinition.Version.V3);
        try {
            new V3PlanTransport(f.service, f.sessions).materializationBody(f.lease, f.plan,
                    V3PlanTransportTest.bodyRequest(context, "{\"revision\":\"2\"}"), response, f.transfers.admitSemantic(f.lease), view);
            assertTrue(entered.await(3, TimeUnit.SECONDS)); view.close();
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> f.service.reserveView(f.lease, f.plan, PlanDefinition.Version.V3)).code());
            released.countDown(); assertTrue(context.complete.await(3, TimeUnit.SECONDS));
            V3PlanTransportTest.settled(f.transfers, f.lease);
            assertEquals(0, acquired.get()); assertEquals(0, output.bytes.size());
        } finally { released.countDown(); view.close(); }
    }

}
