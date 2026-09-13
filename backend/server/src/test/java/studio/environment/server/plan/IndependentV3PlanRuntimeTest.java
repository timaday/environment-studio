package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.workspace.*;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.*;

class IndependentV3PlanRuntimeTest {
    @TempDir Path directory;
    @Test void sameComposedRuntimeDoesNotCacheMissingHistoryOrBypassCurrentQualification() throws Exception {
        SqliteDraftStore.initializeV3(directory);
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var lease = V3PlanRuntimeTest.lease(sessions, "MockOwner");
        var runtime = assertDoesNotThrow(() -> V3PlanRuntimeTest.runtime(directory, sessions));
        var reference = new NativeCommand.Reference(UUID.randomUUID().toString(), "1");
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class,
                () -> runtime.service().createV3(lease, UUID.randomUUID().toString(), reference, "mock-pg", "mock-reader")).code());
        V3PlanRuntimeFixtures.draft(directory, lease.owner(), reference.objectId(), "0");
        var counts = V3PlanRuntimeFixtures.counts(directory, true);
        assertEquals(PlanRefusal.Code.PUBLICATION_REQUIRED, assertThrows(PlanRefusal.class,
                () -> runtime.service().createV3(lease, UUID.randomUUID().toString(), reference, "mock-pg", "mock-reader")).code());
        assertEquals(counts, V3PlanRuntimeFixtures.counts(directory, true));
        var publication = V3ProfileHttpFixtures.definition(directory, lease.owner(), false);
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION, assertThrows(PlanRefusal.class,
                () -> runtime.service().createV3(lease, UUID.randomUUID().toString(), new NativeCommand.Reference(publication.objectId(), "2"), "mock-pg", "mock-reader")).code());
        assertEquals(PlanRefusal.Code.NOT_FOUND, assertThrows(PlanRefusal.class,
                () -> runtime.service().view(lease, Optional.empty())).code());
    }
    @Test void retiredOriginalLeaseRefusesBeforeDeferredUnavailableStoreAcquisition() {
        SqliteDraftStore.initialize(directory);
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var lease = V3PlanRuntimeTest.lease(sessions, "MockOwner");
        var runtime = assertDoesNotThrow(() -> V3PlanRuntimeTest.runtime(directory, sessions));
        sessions.quarantine(lease);
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED, assertThrows(PlanRefusal.class,
                () -> runtime.service().createV3(lease, UUID.randomUUID().toString(), new NativeCommand.Reference(UUID.randomUUID().toString(), "1"), "mock-pg", "mock-reader")).code());
    }
}
