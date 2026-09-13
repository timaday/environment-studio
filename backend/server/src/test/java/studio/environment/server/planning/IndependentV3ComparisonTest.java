package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.XML;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;

/** Actual reprojection refusal while a previously computed comparison is held. */
class IndependentV3ComparisonTest {
    @Test void directComparisonCannotSurviveFailedReinspectionAtSameRevision() throws Exception {
        rejectedReinspection(false);
    }
    @Test void viewAdmissionAlsoRejectsFailedReinspection() throws Exception {
        rejectedReinspection(true);
    }
    @Test void unchangedCompletedComparisonStillReturnsExactSource() {
        var fixture = new SharedV3PlanXmlTest();
        var service = fixture.service();
        String id = fixture.inspected(service);
        assertEquals(XML, service.comparison(fixture.lease, id, "2", false,
                "sheet", ViewMode.RAW, true).text());
    }
    private static void rejectedReinspection(boolean admitted) throws Exception {
        var fixture = new SharedV3PlanXmlTest();
        var adapter = new SharedV3PlanComparisonTest.HeldComparison();
        var service = fixture.service(adapter);
        String id = fixture.inspected(service);
        var output = new AtomicReference<DocumentView>();
        var failure = new AtomicReference<Throwable>();
        var view = admitted ? service.reserveView(fixture.lease, id) : null;
        var worker = new Thread(() -> {
            try {
                output.set(view == null
                        ? service.comparison(fixture.lease, id, "2", false, "sheet", ViewMode.RAW, true)
                        : view.run(() -> { view.pin("2"); return view.document(false, "sheet", ViewMode.RAW, true); }));
            } catch (Throwable refused) { failure.set(refused); }
        });
        worker.start();
        try {
            assertTrue(adapter.entered.await(5, TimeUnit.SECONDS));
            // Legal mock XML but present empty derived identity: actual projection must refuse.
            fixture.xml = "<items><item id='one' tone='' finish='x'/></items>";
            var reserved = service.reserve(fixture.lease, id,
                    new HostedPlanService.Mutation("2", UUID.randomUUID().toString()));
            var status = service.submit(fixture.lease, reserved.operationId().orElseThrow(), (user, password) -> {
                user[0] = 'a'; password[0] = 'X'; return new CredentialLengths(1, 1);
            });
            assertEquals(HostedPlanService.Phase.REFUSED, status.phase());
            assertEquals("2", service.summary(fixture.lease, id).revision());
            assertTrue(service.live(fixture.lease));
        } finally {
            adapter.released.countDown();
            worker.join(5000);
            if (view != null) view.close();
        }
        assertFalse(worker.isAlive());
        assertAll(
                () -> assertTrue(adapter.control.get().cancelled(), "original comparison control was not cancelled"),
                () -> assertNull(output.get(), "invalidated comparison escaped"),
                () -> assertEquals(PlanRefusal.Code.CONFLICT,
                        assertInstanceOf(PlanRefusal.class, failure.get()).code()));
    }
}
