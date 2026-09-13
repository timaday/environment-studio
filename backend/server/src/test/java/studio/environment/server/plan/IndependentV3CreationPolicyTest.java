package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanDefinition;

class IndependentV3CreationPolicyTest {
    @Test void retainedCreateReplayCannotBypassTheCurrentDestinationOwnerPredicate() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var allowed=new AtomicBoolean(true);
        var runtime=new PlanRuntime(f.service,List.of(),(owner,destination)->allowed.get()&&owner.equals(f.lease.owner())&&destination.equals("mock-destination"));
        var request=new PlanMetadataReader.Create(UUID.randomUUID().toString(),f.reference,"mock-pg","mock-destination");
        var original=runtime.createV3(f.lease,request);allowed.set(false);
        assertThrows(PlanRuntime.DestinationDenied.class,()->runtime.createV3(f.lease,request));
        assertEquals(original.planId(),f.service.viewV3(f.lease,Optional.empty()).summary().planId());
        assertEquals("1",f.service.viewV3(f.lease,Optional.empty()).summary().revision());assertEquals(0,f.reservations.get());
        allowed.set(true);assertEquals(original,runtime.createV3(f.lease,request));
        assertDoesNotThrow(()->f.service.requireOwned(f.lease,original.planId(),PlanDefinition.Version.V3));
    }
}
