package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanRefusal;

class IndependentV3PlanSummaryTest {
    @Test void repeatedContributorsCountPhysicalMembershipsButDistinctComputedPairs() {
        var f = new SharedV3PlanXmlTest();
        f.xml = "<items><item id='left' tone='same' finish='same'/><item id='right' tone='same' finish='same'/></items>";
        var service = f.service();
        String id = f.inspected(service);
        var current = service.viewV3(f.lease, Optional.of(id));
        // Equal text in separate derivations remains two nodes; repeated pair coalesces.
        var expected = Optional.of(new HostedPlanService.ComputedCounts(2, 4, 1));
        assertEquals(new HostedPlanService.Counts(1, 2, 0), current.summary().currentCounts());
        assertEquals(expected, current.currentComputedCounts());
        assertEquals(Optional.empty(), current.targetComputedCounts());
        assertTrue(service.materialize(f.lease, id, "2").complete());
        var completed = service.viewV3(f.lease, Optional.of(id));
        assertEquals(expected, completed.targetComputedCounts());
        service.verifySummaryV3(f.lease, completed);
        for (var forgedTarget : List.of(Optional.<HostedPlanService.ComputedCounts>empty(),
                Optional.of(new HostedPlanService.ComputedCounts(2, 4, 2)))) {
            var forged = new HostedPlanService.V3View(completed.summary(), completed.currentComputedCounts(), forgedTarget);
            assertEquals(PlanRefusal.Code.CONFLICT,
                    assertThrows(PlanRefusal.class, () -> service.verifySummaryV3(f.lease, forged)).code());
        }
        service.verifySummaryV3(f.lease, completed);
    }

    @Test void sameRevisionActiveReservationInvalidatesSummaryWithoutChangingComputedCounts() {
        var f = new SharedV3PlanXmlTest();
        f.xml = "<items><item id='sole' tone='one' finish='two'/></items>";
        var service = f.service();
        String id = f.inspected(service);
        var before = service.viewV3(f.lease, Optional.of(id));
        String operation = service.reserve(f.lease, id,
                new HostedPlanService.Mutation("2", UUID.randomUUID().toString())).operationId().orElseThrow();
        try {
            var during = service.viewV3(f.lease, Optional.of(id));
            assertEquals(before.summary().revision(), during.summary().revision());
            assertEquals(before.currentComputedCounts(), during.currentComputedCounts());
            assertEquals(Optional.of(operation), during.summary().activeOperationId());
            assertEquals(PlanRefusal.Code.CONFLICT,
                    assertThrows(PlanRefusal.class, () -> service.verifySummaryV3(f.lease, before)).code());
            service.verifySummaryV3(f.lease, during);
        } finally { service.cancel(f.lease, operation); }
    }
}
