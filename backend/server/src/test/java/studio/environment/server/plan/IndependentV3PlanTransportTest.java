package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import studio.environment.server.session.HostedSessions;

class IndependentV3PlanTransportTest {
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

}
