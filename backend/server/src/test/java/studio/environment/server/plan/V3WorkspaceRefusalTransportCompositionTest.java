package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.SqliteDraftStore;
import studio.environment.server.workspace.V3PlanRuntimeFixtures;
import studio.environment.server.workspace.V3ProfileHttpFixtures;

/** Actual production composition and SQLite; existing independently invented mock fixtures only. */
class V3WorkspaceRefusalTransportCompositionTest {
    @TempDir Path directory;

    @Test void absentAndForeignHistoryHaveTheSameClosed404WhileOwnedDraftStillRequiresPublication() throws Exception {
        SqliteDraftStore.initializeV3(directory);
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var owner = V3PlanRuntimeTest.lease(sessions, "MockOwner");
        var foreign = V3PlanRuntimeTest.lease(sessions, "ForeignOwner");
        var objectId = UUID.randomUUID().toString();
        V3PlanRuntimeFixtures.draft(directory, foreign.owner(), objectId, "0");
        var owned = V3PlanRuntimeFixtures.draft(directory, owner.owner(), UUID.randomUUID().toString(), "0");
        var before = V3PlanRuntimeFixtures.counts(directory, true);
        var runtime = V3PlanRuntimeTest.runtime(directory, sessions);
        for (int retry = 0; retry < 2; retry++) {
            refusal(runtime, sessions, owner, UUID.randomUUID().toString(), "1", 404, "NOT_FOUND");
            refusal(runtime, sessions, owner, objectId, "1", 404, "NOT_FOUND");
            refusal(runtime, sessions, owner, owned.objectId(), "1", 422, "PUBLICATION_REQUIRED");
        }
        assertEquals(before, V3PlanRuntimeFixtures.counts(directory, true));
        assertNoPlan(runtime, foreign);
    }

    @Test void schema2WorkspaceReturnsClosed503WithoutMigrationOrAnInstalledPlan() throws Exception {
        SqliteDraftStore.initialize(directory);
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var owner = V3PlanRuntimeTest.lease(sessions, "MockOwner");
        var before = V3PlanRuntimeFixtures.counts(directory, false);
        var runtime = V3PlanRuntimeTest.runtime(directory, sessions);
        for (int retry = 0; retry < 2; retry++)
            refusal(runtime, sessions, owner, UUID.randomUUID().toString(), "1", 503, "PLAN_SERVICES_UNAVAILABLE");
        assertEquals(before, V3PlanRuntimeFixtures.counts(directory, false));
    }

    @Test void historicalPublicationStillRequiresActualCurrentCompilerQualification() throws Exception {
        SqliteDraftStore.initializeV3(directory);
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var owner = V3PlanRuntimeTest.lease(sessions, "MockOwner");
        // Test-produced historical readiness is never operational compiler qualification.
        var publication = V3ProfileHttpFixtures.definition(directory, owner.owner(), false);
        var before = V3PlanRuntimeFixtures.counts(directory, true);
        var runtime = V3PlanRuntimeTest.runtime(directory, sessions);
        refusal(runtime, sessions, owner, publication.objectId(), "2", 422, "UNSUPPORTED_DEFINITION");
        assertEquals(before, V3PlanRuntimeFixtures.counts(directory, true));
    }

    private static void refusal(PlanRuntime runtime, HostedSessions sessions, SessionLedger.Lease lease,
            String objectId, String revision, int status, String code) throws Exception {
        var context = new V3PlanTransportTest.Context();
        var output = new V3PlanTransportTest.Output();
        var response = V3PlanTransportTest.response(output);
        var body = "{\"expectedRevision\":\"0\",\"requestId\":\"" + UUID.randomUUID()
                + "\",\"definition\":{\"objectId\":\"" + objectId + "\",\"workspaceRevision\":\"" + revision
                + "\"},\"bindingId\":\"mock-pg\",\"destinationId\":\"mock-reader\"}";
        var request = V3PlanTransportTest.bodyRequest(context, body);
        request.setAttribute(HostedSessions.REQUEST_LEASE, lease);
        new V3PlanController(runtime, sessions).create(request, response);
        assertTrue(context.complete.await(3, TimeUnit.SECONDS));
        V3PlanTransportTest.settled(runtime.transfers(), lease);
        assertFalse(runtime.awaitingCleanupWork(lease));
        assertTrue(runtime.service().live(lease));
        assertEquals(1, context.calls.get());
        assertNoPlan(runtime, lease);
        assertEquals(status, response.getStatus());
        assertEquals("{\"code\":\"" + code + "\"}", output.bytes.toString(StandardCharsets.UTF_8));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals(output.bytes.size(), response.getContentLength());
    }

    private static void assertNoPlan(PlanRuntime runtime, SessionLedger.Lease lease) {
        assertEquals(PlanRefusal.Code.NOT_FOUND, assertThrows(PlanRefusal.class,
                () -> runtime.service().view(lease, Optional.empty())).code());
    }
}
