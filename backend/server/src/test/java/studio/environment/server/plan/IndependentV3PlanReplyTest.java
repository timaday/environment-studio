package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanObservedDestination;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.workspace.NativeCommand;

class IndependentV3PlanReplyTest {
    private static String bytes(V3PlanReply reply) throws Exception {
        try (var encoder = new PlanViewEncoding(32768)) {
            encoder.encode(reply.wire());
            var stream = new ByteArrayOutputStream();
            encoder.write(stream, () -> {});
            return stream.toString(StandardCharsets.UTF_8);
        }
    }

    @Test void explicitDisplayContextRemainsInvalidAndCannotEnableExport() throws Exception {
        var observed = new PlanObservedDestination("postgresql", Map.of(
                "systemIdentifier", "17", "databaseOid", "19", "databaseName", "mock-é"),
                "a".repeat(64), false);
        var physical = new HostedPlanService.View("mock-plan", "9",
                new NativeCommand.Reference("00000000-0000-4000-8000-000000000019", "7"),
                "mock-binding", "mock-destination", Optional.of(observed),
                new HostedPlanService.Counts(2, 3, 4), new HostedPlanService.Counts(5, 6, 7),
                false, true, true, List.of("INSPECTION_REQUIRED"), Optional.of("mock-operation"));
        var reply = new V3PlanReply.Summary(new HostedPlanService.V3View(physical,
                Optional.empty(), Optional.of(new HostedPlanService.ComputedCounts(8, 9, 10))));
        String expected = "{\"planId\":\"mock-plan\",\"revision\":\"9\",\"definition\":{\"objectId\":\"00000000-0000-4000-8000-000000000019\",\"workspaceRevision\":\"7\"},\"bindingId\":\"mock-binding\",\"destinationId\":\"mock-destination\",\"currentCounts\":{\"documents\":2,\"entities\":3,\"relations\":4},\"targetCounts\":{\"documents\":5,\"entities\":6,\"relations\":7},\"observedDestination\":{\"engine\":\"postgresql\",\"identity\":{\"databaseName\":\"mock-é\",\"databaseOid\":\"19\",\"systemIdentifier\":\"17\"},\"observationFingerprint\":\"" + "a".repeat(64) + "\",\"evidenceValid\":false},\"inspectionValid\":false,\"targetComplete\":true,\"exportAvailable\":false,\"blockers\":[\"INSPECTION_REQUIRED\"],\"activeOperationId\":\"mock-operation\",\"currentComputedCounts\":null,\"targetComputedCounts\":{\"nodes\":8,\"memberships\":9,\"cooccurrences\":10}}";
        assertEquals(expected, bytes(reply));
        assertEquals("V3PlanReply.Summary[redacted]", reply.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> definition = (Map<String, Object>) reply.wire().get("definition");
        assertThrows(UnsupportedOperationException.class, () -> definition.put("objectId", "other"));
    }

    @Test void retainedV3AcknowledgementAndStatusRemainOriginalAcrossV2Replacement() throws Exception {
        var fixture = new PlanV1VersionBoundaryTest.Fixture();
        var original = fixture.create(true);
        var acknowledged = fixture.service.reserve(fixture.lease, original.planId(),
                new HostedPlanService.Mutation("1", UUID.randomUUID().toString()));
        String operation = acknowledged.operationId().orElseThrow();
        fixture.service.cancel(fixture.lease, operation);
        var captured = fixture.service.status(fixture.lease, operation);
        var ack = new V3PlanReply.Acknowledgement(acknowledged);
        var status = new V3PlanReply.Status(captured);
        var summary = new V3PlanReply.Summary(fixture.service.viewV3(fixture.lease, Optional.empty()));
        fixture.service.discard(fixture.lease, original.planId(),
                new HostedPlanService.Mutation("1", UUID.randomUUID().toString()));
        var replacement = fixture.create(false);
        assertNotEquals(original.planId(), replacement.planId());
        assertDoesNotThrow(() -> ack.verify(fixture.service, fixture.lease));
        assertDoesNotThrow(() -> status.verify(fixture.service, fixture.lease));
        assertEquals(original.planId(), status.wire().get("planId"));
        assertFalse(status.wire().containsKey("installedRevision"));
        assertThrows(PlanRefusal.class, () -> summary.verify(fixture.service, fixture.lease));
        assertEquals(1, fixture.closes.get());
        fixture.ledger.close(fixture.lease.id());
        for (V3PlanReply reply : List.of(ack, status)) {
            assertEquals(PlanRefusal.Code.SESSION_REQUIRED,
                    assertThrows(PlanRefusal.class, () -> reply.verify(fixture.service, fixture.lease)).code());
        }
        fixture.service.invalidate(fixture.lease);
    }
}
