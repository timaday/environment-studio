package studio.environment.server.security;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IndependentV3PollingDeadlineTest {
    @Test void alternatingPlanAndOperationPollingCannotKeepTheOriginalIdleSessionAlive() throws Exception {
        var f=new V3PlanPollingBoundaryTest.Fixture();
        for(int seconds:new int[]{300,600,900,1200,1500,1799}) {
            f.instant.set(f.start.plusSeconds(seconds));
            assertEquals(200,f.call("GET",seconds%600==0?"/api/v3/operations/held-operation":"/api/v3/plans/current"));
        }
        f.instant.set(f.start.plusSeconds(1800));assertEquals(401,f.call("GET","/api/v3/operations/held-operation"));
        assertTrue(f.sessions.guard(f.lease,()->true).isEmpty());
    }
    @Test void validActivityCannotExtendTheOriginalAbsoluteLease() throws Exception {
        var f=new V3PlanPollingBoundaryTest.Fixture();
        for(int seconds=1700;seconds<28_800;seconds+=1700) {
            f.instant.set(f.start.plusSeconds(seconds));assertEquals(200,f.call("POST","/api/v3/plans/held-plan/inspections"));
            assertEquals(200,f.call("GET","/api/v3/plans/held-plan"));
        }
        f.instant.set(f.start.plusSeconds(28_800));assertEquals(401,f.call("GET","/api/v3/operations/held-operation"));
        assertTrue(f.sessions.guard(f.lease,()->true).isEmpty());
    }
}
