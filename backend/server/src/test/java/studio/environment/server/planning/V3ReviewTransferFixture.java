package studio.environment.server.planning;

import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.Outcome;
import studio.environment.core.RequiredCheck;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;

/** Actual independently invented XML with explicit test-only publication/observation ports. */
public final class V3ReviewTransferFixture {
    private final SharedV3PlanXmlTest fixture=SharedV3PlanReviewTest.allowed();
    public final HostedPlanService service=fixture.service();
    public final SessionLedger.Lease lease=fixture.lease;
    public final String plan=fixture.inspected(service);
    public final HostedPlanService.ReviewCommand command;
    public final CountDownLatch lookupEntered=new CountDownLatch(1),lookupRelease=new CountDownLatch(1);
    public volatile boolean holdLookup;
    public int lookups;
    public V3ReviewTransferFixture() {
        assertTrue(service.materialize(lease,plan,"2").complete());
        command=SharedV3PlanReviewTest.command("2",service.validateV3(lease,plan,"2").inputFingerprint());
        var lookup=fixture.definitionLookup;
        fixture.definitionLookup=p->{lookups++;if(holdLookup){lookupEntered.countDown();try{assertTrue(lookupRelease.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}return lookup.apply(p);};
    }
    public String body() {
        return "{\"expectedRevision\":\"2\",\"requestId\":\""+command.mutation().requestId()+"\",\"inputFingerprint\":\""+command.inputFingerprint()+"\",\"destinationId\":\"destination\",\"artifactIntent\":\"protected-self-contained\"}";
    }
    public Outcome reviewOutcome(String revision) {return SharedV3PlanReviewTest.outcome(service.validateV3(lease,plan,revision),RequiredCheck.REVIEW);}
    public void revokeLease(){fixture.ledger.close(lease.id());}
    public void holdLeaseGuard(CountDownLatch entered,CountDownLatch release) {
        fixture.ledger.guard(lease,()->{entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return true;});
    }
}
