package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanPorts.CredentialLengths;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.profile.ProfileCapture;

/** Independently invented mappings and actual XML; no runtime publication witness. */
class IndependentV3PlanCaptureTest {
    @Test void equalSizeMappingsCannotDuplicateOnePhysicalEntityAndOmitAnother() {
        var fixture = new SharedV3PlanXmlTest();
        var service = fixture.service();
        String plan = fixture.inspected(service);
        var original = service.capture(fixture.lease, plan, "2", SharedV3PlanCaptureTest.command());
        var mappings = SharedV3PlanCaptureTest.command().mappings();
        var repeated = new ProfileCapture.SlotMapping(mappings.getFirst().observed(), "independent-slot", "Independent");
        var malformed = new ProfileCapture.Command("invented-profile", BigInteger.ONE,
                List.of(mappings.get(0), mappings.get(1), repeated));
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED, assertThrows(PlanRefusal.class,
                () -> service.capture(fixture.lease, plan, "2", malformed)).code());
        assertEquals(original, service.capture(fixture.lease, plan, "2", SharedV3PlanCaptureTest.command()));
    }

    @Test void failedReinspectionCancelsAnAlreadyComputedViewCaptureBeforeClose() throws Exception {
        var fixture = new SharedV3PlanXmlTest();
        var adapter = new SharedV3PlanCaptureTest.HeldCapture();
        var service = fixture.service(adapter);
        String plan = fixture.inspected(service);
        var result = new AtomicReference<HostedPlanService.CapturedProfile>();
        var failure = new AtomicReference<Throwable>();
        try (var view = service.reserveView(fixture.lease, plan)) {
            var worker = new Thread(() -> {
                try {
                    result.set(view.run(() -> {
                        view.pin("2");
                        return view.capture(SharedV3PlanCaptureTest.command());
                    }));
                } catch (Throwable refused) { failure.set(refused); }
            });
            worker.start();
            try {
                assertTrue(adapter.entered.await(5, TimeUnit.SECONDS));
                fixture.xml = "<items><item id='one' tone='' finish='x'/></items>";
                String operation = service.reserve(fixture.lease, plan,
                        new HostedPlanService.Mutation("2", UUID.randomUUID().toString())).operationId().orElseThrow();
                var status = service.submit(fixture.lease, operation, (user, password) -> {
                    user[0] = 'a'; password[0] = 'X'; return new CredentialLengths(1, 1);
                });
                assertEquals(HostedPlanService.Phase.REFUSED, status.phase());
                assertTrue(adapter.control.get().cancelled());
            } finally {
                adapter.released.countDown();
                worker.join(5000);
            }
            assertFalse(worker.isAlive());
            assertNull(result.get());
            assertEquals(PlanRefusal.Code.CONFLICT, assertInstanceOf(PlanRefusal.class, failure.get()).code());
            assertTrue(service.live(fixture.lease));
            assertFalse(service.summary(fixture.lease, plan).inspectionValid());
        }
    }
}
