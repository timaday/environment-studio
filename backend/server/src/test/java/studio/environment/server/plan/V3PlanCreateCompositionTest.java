package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanDefinition;
import studio.environment.server.workspace.V3PlanRuntimeFixtures;

class V3PlanCreateCompositionTest {
    @Test void explicitWrapperInstallsV3WhileLegacyWrapperStillInstallsV2() throws Exception {
        for (boolean version3 : List.of(true, false)) {
            var fixture = new PlanV1VersionBoundaryTest.Fixture();
            var checks = new AtomicInteger();
            var runtime = new PlanRuntime(fixture.service, List.of(), (owner, destination) -> {
                checks.incrementAndGet();
                return owner.equals(fixture.lease.owner()) && destination.equals("mock-destination");
            });
            var command = new PlanMetadataReader.Create(UUID.randomUUID().toString(), fixture.reference,
                    "mock-pg", "mock-destination");
            var ack = assertDoesNotThrow(() -> version3 ? runtime.createV3(fixture.lease, command)
                    : runtime.create(fixture.lease, command));
            assertDoesNotThrow(() -> fixture.service.requireOwned(fixture.lease, ack.planId(),
                    version3 ? PlanDefinition.Version.V3 : PlanDefinition.Version.V2));
            assertEquals(1, checks.get());
            assertEquals(0, fixture.reservations.get());
            assertEquals(ack, version3 ? runtime.createV3(fixture.lease, command)
                    : runtime.create(fixture.lease, command));
            assertEquals(2, checks.get());
            assertEquals(ack.planId(), fixture.service.view(fixture.lease, Optional.empty()).planId());
        }
    }
    @Test void deniedDestinationNeverReachesWorkspaceAndRevocationStillPrecedesLookup() throws Exception {
        var fixture = new PlanV1VersionBoundaryTest.Fixture();
        var lookups = new AtomicInteger();
        var workspace = new studio.environment.core.plan.PlanPorts.Workspace() {
            public studio.environment.core.plan.PlanPorts.PublishedDefinition definition(
                    studio.environment.core.session.Owner owner,
                    studio.environment.core.workspace.NativeCommand.Reference reference) {
                lookups.incrementAndGet(); throw new AssertionError("MOCK_UNEXPECTED_LOOKUP");
            }
            public studio.environment.core.plan.PlanPorts.PublishedDefinition definitionV3(
                    studio.environment.core.session.Owner owner,
                    studio.environment.core.workspace.NativeCommand.Reference reference) {
                lookups.incrementAndGet(); throw new AssertionError("MOCK_UNEXPECTED_LOOKUP");
            }
            public studio.environment.core.plan.PlanPorts.PublishedProfile profile(
                    studio.environment.core.session.Owner owner,
                    studio.environment.core.workspace.NativeCommand.Reference reference,
                    studio.environment.core.plan.PlanPorts.PublishedDefinition definition) {
                throw new AssertionError("MOCK_UNEXPECTED_PROFILE");
            }
        };
        var service = new studio.environment.core.plan.HostedPlanService(fixture.ledger::guard,
                workspace, java.util.Map.of(), new PlanContentAdapter(), System::nanoTime);
        var denied = new PlanRuntime(service, List.of(), (owner, destination) -> false);
        var command = new PlanMetadataReader.Create(UUID.randomUUID().toString(), fixture.reference,
                "mock-pg", "mock-destination");
        assertThrows(PlanRuntime.DestinationDenied.class, () -> denied.createV3(fixture.lease, command));
        assertThrows(PlanRuntime.DestinationDenied.class, () -> denied.create(fixture.lease, command));
        assertEquals(0, lookups.get());
        fixture.ledger.close(fixture.lease.id());
        var allowed = new PlanRuntime(service, List.of(), (owner, destination) -> true);
        assertEquals(studio.environment.core.plan.PlanRefusal.Code.SESSION_REQUIRED,
                assertThrows(studio.environment.core.plan.PlanRefusal.class,
                        () -> allowed.createV3(fixture.lease, command)).code());
        assertEquals(0, lookups.get());
    }

    @Test void actualRuntimePublicationRemainsUnqualifiedWithoutStorageChanges(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        studio.environment.server.workspace.SqliteDraftStore.initializeV3(directory);
        var sessions = new studio.environment.server.session.HostedSessions(java.time.Clock.systemUTC(), List.of());
        var lease = V3PlanRuntimeTest.lease(sessions, "MockOwner");
        var publication = studio.environment.server.workspace.V3ProfileHttpFixtures.definition(directory, lease.owner(), false);
        var before = V3PlanRuntimeFixtures.counts(directory, true);
        var runtime = V3PlanRuntimeTest.runtime(directory, sessions);
        var command = new PlanMetadataReader.Create(UUID.randomUUID().toString(),
                new studio.environment.core.workspace.NativeCommand.Reference(publication.objectId(), "2"),
                "mock-pg", "mock-reader");
        assertEquals(studio.environment.core.plan.PlanRefusal.Code.UNSUPPORTED_DEFINITION,
                assertThrows(studio.environment.core.plan.PlanRefusal.class,
                        () -> runtime.createV3(lease, command)).code());
        assertEquals(before, V3PlanRuntimeFixtures.counts(directory, true));
        assertEquals(studio.environment.core.plan.PlanRefusal.Code.NOT_FOUND,
                assertThrows(studio.environment.core.plan.PlanRefusal.class,
                        () -> runtime.service().view(lease, Optional.empty())).code());
    }

}
