package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.server.plan.PlanContentAdapter;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

class IndependentV3MaterializationCancellationTest {
    static final class HeldRefusal implements ContentAdapter {
        final PlanContentAdapter actual = new PlanContentAdapter();
        final AtomicBoolean hold = new AtomicBoolean();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        public ContentResult project(PublishedDefinition d, String binding, ObservationResult.Observation observation) { return actual.project(d,binding,observation); }
        public ContentResult project(PublishedDefinition d, String binding, ObservationResult.Observation observation, ObservationPort.Cancellation control) { return actual.project(d,binding,observation,control); }
        public ContentResult materialize(PublishedDefinition d, String binding, Content current, Draft draft) { return actual.materialize(d,binding,current,draft); }
        public Capture capture(PublishedDefinition d, String binding, Content current, ProfileCapture.Command command) { return actual.capture(d,binding,current,command); }
        public V3PlanContent.Result materializeV3(PublishedDefinition d, DerivedInput.Pin original, Content current,
                DerivedInput.Pin target, Draft draft, ObservationPort.Cancellation control) {
            if (!hold.get()) return actual.materializeV3(d,original,current,target,draft,control);
            entered.countDown();
            try { assertTrue(release.await(5,TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            // An explicit controlled resource refusal, not a manufactured valid target proof.
            return new V3PlanContent.Result.Refused("RESOURCE_LIMIT");
        }
    }
    @Test void cancelledLateRefusalCannotDropAnAlreadyRetainedCheckedTarget() throws Exception {
        var fixture = new SharedV3PlanXmlTest(); var adapter = new HeldRefusal(); var service = fixture.service(adapter);
        String plan = fixture.inspected(service);
        try (var initial = service.reserveView(fixture.lease,plan,V3)) {
            assertTrue(initial.run(() -> { initial.pin("2"); return initial.materialize().complete(); }));
        }
        var before = fixture.snapshot(service,plan,"2").target().orElseThrow();
        assertInstanceOf(PlanContentEvidence.V3Target.class,before.evidence());
        adapter.hold.set(true);
        var view = service.reserveView(fixture.lease,plan,V3); var executor = Executors.newSingleThreadExecutor();
        var result = executor.submit(() -> view.run(() -> { view.pin("2"); return view.materialize(); }));
        try {
            assertTrue(adapter.entered.await(3,TimeUnit.SECONDS)); view.close();
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,() -> service.reserveView(fixture.lease,plan,V3)).code());
            adapter.release.countDown();
            var failure = assertThrows(ExecutionException.class,() -> result.get(3,TimeUnit.SECONDS));
            assertEquals(PlanRefusal.Code.CONFLICT,assertInstanceOf(PlanRefusal.class,failure.getCause()).code());
            var retained = fixture.snapshot(service,plan,"2").target();
            assertTrue(retained.isPresent(),"cancelled refusal cannot erase the original retained target");
            assertEquals(before,retained.orElseThrow());
            assertEquals("2",service.viewV3(fixture.lease,java.util.Optional.of(plan)).summary().revision());
        } finally {
            adapter.release.countDown(); view.close(); executor.shutdownNow(); assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));
        }
    }
}
