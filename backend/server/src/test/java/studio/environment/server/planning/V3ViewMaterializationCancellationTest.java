package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.server.plan.PlanContentAdapter;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

class V3ViewMaterializationCancellationTest {
    static final class Held implements ContentAdapter {
        final PlanContentAdapter actual = new PlanContentAdapter();
        final CountDownLatch entered = new CountDownLatch(1), released = new CountDownLatch(1);
        final AtomicReference<ObservationPort.Cancellation> control = new AtomicReference<>();
        public ContentResult project(PublishedDefinition definition, String binding, ObservationResult.Observation observation) {
            return actual.project(definition, binding, observation);
        }
        public ContentResult project(PublishedDefinition definition, String binding, ObservationResult.Observation observation, ObservationPort.Cancellation cancellation) {
            return actual.project(definition, binding, observation, cancellation);
        }
        public ContentResult materialize(PublishedDefinition definition, String binding, Content current, Draft draft) {
            return actual.materialize(definition, binding, current, draft);
        }
        public Capture capture(PublishedDefinition definition, String binding, Content current, ProfileCapture.Command command) {
            return actual.capture(definition, binding, current, command);
        }
        public V3PlanContent.Result materializeV3(PublishedDefinition definition, DerivedInput.Pin original, Content current,
                DerivedInput.Pin target, Draft draft, ObservationPort.Cancellation cancellation) {
            // Independently computed actual XML/proof result is held immediately before return.
            var result = actual.materializeV3(definition, original, current, target, draft, cancellation);
            assertInstanceOf(V3PlanContent.Result.Complete.class, result);
            control.set(cancellation); entered.countDown();
            try { assertTrue(released.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            return result;
        }
    }
    @Test void directCloseSignalsTheActualAdapterControlWithoutReleasingExecutingScratch() throws Exception {
        var fixture = new SharedV3PlanXmlTest(); var held = new Held(); var service = fixture.service(held);
        String plan = fixture.inspected(service); var view = service.reserveView(fixture.lease, plan, V3);
        var worker = Executors.newSingleThreadExecutor();
        Future<?> result = worker.submit(() -> view.run(() -> { view.pin("2"); return view.materialize(); }));
        try {
            assertTrue(held.entered.await(3, TimeUnit.SECONDS)); view.close();
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> service.reserveCommand(fixture.lease, plan, V3)).code());
            assertTrue(held.control.get().cancelled(), "the exact adapter cancellation must be signalled by original view close");
        } finally {
            held.released.countDown(); view.close();
            try { result.get(3, TimeUnit.SECONDS); } catch (ExecutionException expected) { assertInstanceOf(PlanRefusal.class, expected.getCause()); }
            worker.shutdownNow(); assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
        }
        try (var independent = service.reserveCommand(fixture.lease, plan, V3)) { assertTrue(independent.live()); }
    }
    @Test void closedOriginalViewCannotInstallItsLateAlreadyComputedTarget() throws Exception {
        var fixture = new SharedV3PlanXmlTest(); var held = new Held(); var service = fixture.service(held);
        String plan = fixture.inspected(service); var view = service.reserveView(fixture.lease, plan, V3);
        var worker = Executors.newSingleThreadExecutor();
        Future<?> result = worker.submit(() -> view.run(() -> { view.pin("2"); return view.materialize(); }));
        try {
            assertTrue(held.entered.await(3, TimeUnit.SECONDS)); view.close(); held.released.countDown();
            var failure = assertThrows(ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS));
            assertEquals(PlanRefusal.Code.CONFLICT, assertInstanceOf(PlanRefusal.class, failure.getCause()).code());
            assertTrue(fixture.snapshot(service, plan, "2").target().isEmpty(), "late cancelled target must not be installed before outer verify refuses");
        } finally {
            held.released.countDown(); view.close(); worker.shutdownNow(); assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
    @Test void normalOriginalViewInstallsActualTargetAndReleasesOnlyAfterRunCloses() throws Exception {
        var fixture = new SharedV3PlanXmlTest(); var held = new Held(); var service = fixture.service(held);
        String plan = fixture.inspected(service); held.released.countDown();
        try (var view = service.reserveView(fixture.lease, plan, V3)) {
            assertTrue(view.run(() -> { view.pin("2"); return view.materialize().complete(); }));
            assertFalse(held.control.get().cancelled());
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                    () -> service.reserveCommand(fixture.lease, plan, V3)).code());
        }
        var target = fixture.snapshot(service, plan, "2").target().orElseThrow();
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>", target.sources().getFirst().xml());
        assertInstanceOf(PlanContentEvidence.V3Target.class, target.evidence());
        try (var independent = service.reserveCommand(fixture.lease, plan, V3)) { assertTrue(independent.live()); }
    }
}
