package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import studio.environment.core.observation.ObservationPort;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.observation.TransientCredentials;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanDefinition;
import studio.environment.core.plan.PlanPorts;
import studio.environment.core.session.Owner;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.export.V3GuardedPackageCandidate;
import studio.environment.server.planning.V3WorkflowHttpWitnesses;
import studio.environment.server.session.HostedSessions;

class IndependentV3PlanTransportTest {
    static final class PackageFixture implements AutoCloseable {
        final SessionLedger ledger = new SessionLedger(Clock.systemUTC(), ignored -> {});
        final SessionLedger.Lease lease = ((SessionLedger.Accepted) ledger.admit("mock-package-session", new Owner("https://mock.invalid", "package-owner"))).lease();
        final HostedPlanService service;
        final HostedSessions sessions = new HostedSessions(Clock.systemUTC(), List.of());
        final String planId;
        final String inputFingerprint;
        final V3GuardedPackageCandidate.Target target;
        PackageFixture() throws Exception {
            var reference = new NativeCommand.Reference("00000000-0000-4000-8000-000000000919", "2");
            var checked = V3WorkflowHttpWitnesses.definition();
            var published = new PlanPorts.PublishedDefinition(reference, "d".repeat(64), new PlanDefinition.V3(checked), List.of(
                    new NativeCommand.Policy("mock-pg", "sheet", "protected-self-contained"),
                    new NativeCommand.Policy("mock-pg", "tail", "protected-self-contained")));
            var workspace = new PlanPorts.Workspace() {
                public PlanPorts.PublishedDefinition definition(Owner owner, NativeCommand.Reference ref) { throw new AssertionError("UNEXPECTED_V2_DEFINITION"); }
                public PlanPorts.PublishedDefinition definitionV3(Owner owner, NativeCommand.Reference ref) { return published; }
                public PlanPorts.PublishedProfile profile(Owner owner, NativeCommand.Reference ref, PlanPorts.PublishedDefinition definition) { throw new AssertionError("UNEXPECTED_PROFILE"); }
            };
            var observation = new ObservationPort() {
                public ObservationResult observe(ObservationPort.Selection selection, TransientCredentials credentials, ObservationPort.Cancellation cancellation) {
                    throw new AssertionError("UNEXPECTED_V2_OBSERVATION");
                }
                public ObservationPort.Reservation reserveV3(ObservationPort.V3Selection selected) {
                    return new ObservationPort.Reservation.Admitted(new ObservationPort.Permit() {
                        public ObservationResult observe(TransientCredentials credentials, ObservationPort.Cancellation cancellation) {
                            credentials.close(); return V3WorkflowHttpWitnesses.observation();
                        }
                        public void close() {}
                    });
                }
            };
            service = new HostedPlanService(ledger::guard, workspace,
                    Map.of("mock-destination", new PlanPorts.Destination("mock-destination", studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL, observation)),
                    new PlanContentAdapter(), System::nanoTime);
            planId = service.createV3(lease, java.util.UUID.randomUUID().toString(), reference, "mock-pg", "mock-destination").planId();
            var operation = service.reserve(lease, planId, new HostedPlanService.Mutation("1", java.util.UUID.randomUUID().toString())).operationId().orElseThrow();
            service.submit(lease, operation, (user, password) -> {
                user[0] = 'm'; password[0] = 'p'; return new PlanPorts.CredentialLengths(1, 1);
            });
            try (var admission = service.reserveView(lease, planId, PlanDefinition.Version.V3)) {
                admission.run(() -> { admission.pin("2"); admission.materialize(); return true; });
            }
            try (var admission = service.reserveView(lease, planId, PlanDefinition.Version.V3)) {
                inputFingerprint = admission.run(() -> {
                    admission.pin("2"); return admission.validationV3().inputFingerprint();
                });
            }
            var identity = Map.of("systemIdentifier", "731", "databaseOid", "19", "databaseName", "invented_db");
            target = new V3GuardedPackageCandidate.Target("mock-destination", "postgresql", "invented.invalid", 5432,
                    "invented_db", "verified-tls", "c".repeat(64), "mock-v1", identity, "16.11", "psql", "16.11",
                    "linux-amd64", "postgresql16-text-v1");
        }
        String request() {
            return "{\"revision\":\"2\",\"inputFingerprint\":\"" + inputFingerprint + "\"}";
        }
        void invalidateInspection() {
            var operation = service.reserve(lease, planId, new HostedPlanService.Mutation("2", java.util.UUID.randomUUID().toString())).operationId().orElseThrow();
            service.cancel(lease, operation);
        }
        @Override public void close() { service.invalidate(lease); ledger.close(lease.id()); }
    }

    @Test void revocationDuringReadinessCannotStartANewWriteAfterTheCheckReturns() throws Exception {
        var fixture = new PlanV1VersionBoundaryTest.Fixture();
        var ack = fixture.create(true);
        var transfers = new V3PlanTransfers();
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var record = transfers.admitCredentials(fixture.lease);
        var context = new V3PlanTransportTest.Context();
        var bytes = new ByteArrayOutputStream();
        var revoked = new AtomicBoolean();
        var output = new ServletOutputStream() {
            public void setWriteListener(WriteListener listener) {}
            public boolean isReady() {
                if (revoked.compareAndSet(false, true)) fixture.ledger.close(fixture.lease.id());
                return true;
            }
            public void write(int value) { bytes.write(value); }
            public void write(byte[] source, int offset, int length) { bytes.write(source, offset, length); }
        };
        var response = new MockHttpServletResponse() {
            @Override public ServletOutputStream getOutputStream() { return output; }
        };
        try {
            new V3PlanTransport(fixture.service, sessions).read(fixture.lease,
                    V3PlanTransportTest.request(context), response, record, 200,
                    () -> new V3PlanReply.Acknowledgement(ack));
            assertTrue(context.complete.await(2, TimeUnit.SECONDS));
            V3PlanTransportTest.settled(transfers, fixture.lease);
            assertTrue(revoked.get());
            assertEquals(0, bytes.size(), "No write was in flight when readiness revoked the original lease");
        } finally {
            record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, sessions);
            fixture.service.invalidate(fixture.lease);
        }
    }
    @Test void callbackCompletionCannotReleaseResourcesHeldInsideOriginalClose() throws Exception {
        var fixture = new PlanV1VersionBoundaryTest.Fixture();
        var ack = fixture.create(true);
        var transfers = new V3PlanTransfers();
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var record = transfers.admitCredentials(fixture.lease);
        var context = new V3PlanTransportTest.Context();
        var closing = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var closed = new AtomicBoolean();
        try {
            new V3PlanTransport(fixture.service, sessions).body(fixture.lease,
                    V3PlanTransportTest.bodyRequest(context, "{}"),
                    V3PlanTransportTest.response(new V3PlanTransportTest.Output()), record, 200,
                    () -> {
                        closing.countDown();
                        if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("MOCK_CLOSE_TIMEOUT");
                        closed.set(true);
                    }, () -> false, body -> {
                        new PlanMetadataReader().empty(body);
                        return new V3PlanReply.Acknowledgement(ack);
                    });
            assertTrue(closing.await(1, TimeUnit.SECONDS));
            context.listener.onError(new jakarta.servlet.AsyncEvent(context.value));
            assertEquals(1, context.calls.get());
            assertFalse(closed.get());
            assertTrue(transfers.awaitingWork(fixture.lease));
            release.countDown();
            V3PlanTransportTest.settled(transfers, fixture.lease);
            assertTrue(closed.get());
            assertEquals(1, context.calls.get());
        } finally {
            release.countDown();
            record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, sessions);
        }
    }

    @Test void staleReplyCannotRenewExpiredEncodingBudgetForItsSafeError() throws Exception {
        var fixture = new PlanV1VersionBoundaryTest.Fixture();
        var plan = fixture.create(true);
        var captured = new V3PlanReply.Summary(fixture.service.viewV3(fixture.lease, java.util.Optional.empty()));
        var transfers = new V3PlanTransfers();
        var sessions = new HostedSessions(Clock.systemUTC(), List.of());
        var record = transfers.admitCredentials(fixture.lease);
        var context = new V3PlanTransportTest.Context();
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var acquired = new java.util.concurrent.atomic.AtomicInteger();
        var operation = new java.util.concurrent.atomic.AtomicReference<String>();
        var output = new V3PlanTransportTest.Output();
        var response = new MockHttpServletResponse() {
            @Override public boolean isCommitted() { clock.set(31_000_000_000L); return false; }
            @Override public ServletOutputStream getOutputStream() { acquired.incrementAndGet(); return output; }
        };
        try {
            new V3PlanTransport(fixture.service, sessions, clock::get).read(fixture.lease,
                    V3PlanTransportTest.request(context), response, record, 200, () -> {
                        operation.set(fixture.service.reserve(fixture.lease, plan.planId(),
                                new studio.environment.core.plan.HostedPlanService.Mutation("1", java.util.UUID.randomUUID().toString()))
                                .operationId().orElseThrow());
                        return captured;
                    });
            assertTrue(context.complete.await(2, TimeUnit.SECONDS));
            V3PlanTransportTest.settled(transfers, fixture.lease);
            assertEquals(31_000_000_000L, clock.get());
            assertEquals(0, acquired.get());
            assertEquals(0, output.bytes.size());
            assertEquals(1, context.calls.get());
        } finally {
            record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE, sessions);
            if (operation.get() != null) fixture.service.cancel(fixture.lease, operation.get());
        }
    }

    @Test void packageCandidateInvalidatedDuringOutputAcquisitionExposesNoArchiveBytes() throws Exception {
        try (var fixture = new PackageFixture(); var admission = fixture.service.reserveView(fixture.lease, fixture.planId, PlanDefinition.Version.V3)) {
            var transfers = new V3PlanTransfers();
            var record = transfers.admitSemantic(fixture.lease);
            var context = new V3PlanTransportTest.Context();
            var output = new V3PlanTransportTest.Output();
            var response = new MockHttpServletResponse() {
                @Override public ServletOutputStream getOutputStream() {
                    fixture.invalidateInspection();
                    return output;
                }
            };
            new V3PlanTransport(fixture.service, fixture.sessions).packageBody(fixture.lease, fixture.planId,
                    V3PlanTransportTest.bodyRequest(context, fixture.request()), response, record, admission, fixture.target);
            assertTrue(context.complete.await(3, TimeUnit.SECONDS));
            V3PlanTransportTest.settled(transfers, fixture.lease);
            assertEquals(0, output.bytes.size());
            assertEquals(0, output.flushes.get());
        }
    }

    @Test void packageCandidateInvalidatedAfterFirstWriteDoesNotCompleteUsableArchive() throws Exception {
        try (var fixture = new PackageFixture(); var admission = fixture.service.reserveView(fixture.lease, fixture.planId, PlanDefinition.Version.V3)) {
            var transfers = new V3PlanTransfers();
            var record = transfers.admitSemantic(fixture.lease);
            var context = new V3PlanTransportTest.Context();
            var bytes = new ByteArrayOutputStream();
            var invalidated = new AtomicBoolean();
            var output = new ServletOutputStream() {
                public void setWriteListener(WriteListener listener) {}
                public boolean isReady() { return true; }
                public void write(int value) {
                    bytes.write(value);
                    if (invalidated.compareAndSet(false, true)) fixture.invalidateInspection();
                }
                public void write(byte[] source, int offset, int length) {
                    bytes.write(source, offset, length);
                    if (invalidated.compareAndSet(false, true)) fixture.invalidateInspection();
                }
            };
            var response = new MockHttpServletResponse() {
                @Override public ServletOutputStream getOutputStream() { return output; }
            };
            new V3PlanTransport(fixture.service, fixture.sessions).packageBody(fixture.lease, fixture.planId,
                    V3PlanTransportTest.bodyRequest(context, fixture.request()), response, record, admission, fixture.target);
            assertTrue(context.complete.await(3, TimeUnit.SECONDS));
            V3PlanTransportTest.settled(transfers, fixture.lease);
            assertTrue(invalidated.get());
            assertTrue(bytes.size() > 0);
            var members = new java.util.ArrayList<String>();
            try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    members.add(entry.getName()); zip.readAllBytes();
                }
            }
            assertNotEquals(List.of("manifest.json", "payload.json", "transaction.sql", "instructions.txt"), members);
        }
    }

}
