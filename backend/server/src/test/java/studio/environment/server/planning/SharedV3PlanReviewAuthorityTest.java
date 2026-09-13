package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.SharedV3PlanReviewTest.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.RequiredCheck;
import studio.environment.core.Outcome;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.observation.ObservationPort;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.session.*;
import studio.environment.core.profile.*;
import studio.environment.core.derived.DerivedInput;
import studio.environment.server.plan.PlanContentAdapter;

class SharedV3PlanReviewAuthorityTest {
    @Test void abortSignalDuringProofPreventsCommitWithoutClosingTheWorkerOwner() throws Exception {
        var fixture=allowed();var content=new SharedV3PlanCompositionAuthorityTest.ControlledContent();
        var service=fixture.service(content);String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
        var request=command("2",service.validateV3(fixture.lease,plan,"2").inputFingerprint());content.hold=true;
        var view=service.reserveView(fixture.lease,plan,PlanDefinition.Version.V3);
        var result=new AtomicReference<Object>();var failure=new AtomicReference<Throwable>();
        var worker=new Thread(()->{try{result.set(view.run(()->view.reviewV3(request)));}catch(Throwable refused){failure.set(refused);}});
        worker.start();
        try {
            assertTrue(content.entered.await(5,TimeUnit.SECONDS));view.abortReview();view.abortReview();
            assertTrue(view.live());refused(PlanRefusal.Code.CAPACITY,()->service.reserveView(fixture.lease,plan));
        } finally {content.released.countDown();worker.join(5000);view.close();}
        assertFalse(worker.isAlive());assertNull(result.get(),"abort before commit must not produce a receipt");
        assertInstanceOf(PlanRefusal.class,failure.get());content.hold=false;
        assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
        service.reviewV3(fixture.lease,plan,request);
        assertEquals(Outcome.PASS,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    @Test void abortAfterCommitPreservesTheReceiptAndCurrentReview() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
        var request=command("2",service.validateV3(fixture.lease,plan,"2").inputFingerprint());HostedPlanService.Ack receipt;
        try(var view=service.reserveView(fixture.lease,plan,PlanDefinition.Version.V3)) {
            receipt=view.run(()->{var ack=view.reviewV3(request);view.abortReview();view.abortReview();view.verify();return ack;});
        }
        assertEquals(receipt,service.reviewV3(fixture.lease,plan,request));
        assertEquals(Outcome.PASS,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    @Test void closingOriginalAdmissionDuringLookupOrProofCannotCommitAReviewOrReceipt() throws Exception {
        for(boolean lookup:List.of(false,true)) {
            var fixture=allowed();var content=new SharedV3PlanCompositionAuthorityTest.ControlledContent();
            var service=fixture.service(content);String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
            var request=command("2",service.validateV3(fixture.lease,plan,"2").inputFingerprint());
            var entered=new CountDownLatch(1);var released=new CountDownLatch(1);var original=fixture.definitionLookup;
            if(lookup)fixture.definitionLookup=p->{entered.countDown();await(released);return original.apply(p);};else content.hold=true;
            var view=service.reserveView(fixture.lease,plan,PlanDefinition.Version.V3);
            var result=new AtomicReference<Object>();var failure=new AtomicReference<Throwable>();
            var worker=new Thread(()->{try{result.set(view.run(()->view.reviewV3(request)));}catch(Throwable refused){failure.set(refused);}});
            worker.start();
            try {
                assertTrue((lookup?entered:content.entered).await(5,TimeUnit.SECONDS));view.close();
                if(!lookup)assertTrue(content.control.get().cancelled());
                refused(PlanRefusal.Code.CAPACITY,()->service.reserveView(fixture.lease,plan));
            } finally {released.countDown();content.released.countDown();worker.join(5000);view.close();}
            assertFalse(worker.isAlive());assertNull(result.get());assertInstanceOf(PlanRefusal.class,failure.get());
            fixture.definitionLookup=original;content.hold=false;
            assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
            // Same request can commit now; it was not stored as a misleading successful replay.
            service.reviewV3(fixture.lease,plan,request);
            assertEquals(Outcome.PASS,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
        }
    }
    @Test void anotherOwnerOrNewLeaseCannotRecoverReviewReceipts() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
        var request=command("2",service.validateV3(fixture.lease,plan,"2").inputFingerprint());service.reviewV3(fixture.lease,plan,request);
        var foreign=((SessionLedger.Accepted)fixture.ledger.admit("mock-other-lease",new Owner("https://mock.invalid","other-operator"))).lease();
        refused(PlanRefusal.Code.NOT_FOUND,()->service.reviewV3(foreign,plan,request));
        fixture.ledger.close(fixture.lease.id());service.invalidate(fixture.lease);
        var replacement=((SessionLedger.Accepted)fixture.ledger.admit("mock-replacement-lease",fixture.lease.owner())).lease();
        refused(PlanRefusal.Code.SESSION_REQUIRED,()->service.reviewV3(fixture.lease,plan,request));
        refused(PlanRefusal.Code.NOT_FOUND,()->service.reviewV3(replacement,plan,request));
    }
    @Test void failedReinspectionAndOldReplayCannotRestoreReviewAfterRecovery() {
        var fixture=allowed();var service=fixture.service();String plan=fixture.inspected(service);service.materialize(fixture.lease,plan,"2");
        var request=command("2",service.validateV3(fixture.lease,plan,"2").inputFingerprint());var ack=service.reviewV3(fixture.lease,plan,request);
        String original=fixture.xml;fixture.xml="<items><item id='one' tone='' finish='x'/></items>";
        var operation=service.reserve(fixture.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));
        var failed=service.submit(fixture.lease,operation.operationId().orElseThrow(),(user,password)->{user[0]='a';password[0]='X';return new CredentialLengths(1,1);});
        assertEquals(HostedPlanService.Phase.REFUSED,failed.phase());assertEquals("2",service.summary(fixture.lease,plan).revision());
        assertEquals(ack,service.reviewV3(fixture.lease,plan,request));
        refused(PlanRefusal.Code.INSPECTION_REQUIRED,()->service.validateV3(fixture.lease,plan,"2"));
        fixture.xml=original;operation=service.reserve(fixture.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()));
        assertEquals(HostedPlanService.Phase.SUCCEEDED,service.submit(fixture.lease,operation.operationId().orElseThrow(),(user,password)->{user[0]='a';password[0]='X';return new CredentialLengths(1,1);}).phase());
        service.materialize(fixture.lease,plan,"3");assertEquals(ack,service.reviewV3(fixture.lease,plan,request));
        assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(fixture.lease,plan,"3"),RequiredCheck.REVIEW));
    }
    @Test void sameRevisionTargetLossDoesNotReviveReviewWhenIdenticalTargetReturns() {
        var fixture=allowed();var content=new RefusingContent();var service=fixture.service(content);String plan=fixture.inspected(service);
        service.materialize(fixture.lease,plan,"2");var before=service.validateV3(fixture.lease,plan,"2");var request=command("2",before.inputFingerprint());
        var ack=service.reviewV3(fixture.lease,plan,request);content.refuse=true;
        assertFalse(service.materialize(fixture.lease,plan,"2").complete());assertFalse(service.summary(fixture.lease,plan).targetComplete());
        content.refuse=false;assertTrue(service.materialize(fixture.lease,plan,"2").complete());
        var after=service.validateV3(fixture.lease,plan,"2");assertEquals(before.inputFingerprint(),after.inputFingerprint());
        assertEquals(ack,service.reviewV3(fixture.lease,plan,request));
        assertEquals(Outcome.UNKNOWN,outcome(after,RequiredCheck.REVIEW));
        assertEquals(Outcome.UNKNOWN,outcome(service.validateV3(fixture.lease,plan,"2"),RequiredCheck.REVIEW));
    }
    private static final class RefusingContent implements ContentAdapter {
        final PlanContentAdapter actual=new PlanContentAdapter();boolean refuse;
        public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o){return actual.project(d,b,o);}
        public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o,ObservationPort.Cancellation c){return actual.project(d,b,o,c);}
        public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft){return actual.materialize(d,b,c,draft);}
        public V3PlanContent.Result materializeV3(PublishedDefinition d,DerivedInput.Pin before,Content current,DerivedInput.Pin next,Draft draft,ObservationPort.Cancellation c){
            return refuse?new V3PlanContent.Result.Refused("MOCK_MATERIALIZATION_REFUSED"):actual.materializeV3(d,before,current,next,draft,c);
        }
        public Capture capture(PublishedDefinition d,String b,Content c,ProfileCapture.Command command){return actual.capture(d,b,c,command);}
        public void verifyV3(HostedPlanService.ViewSnapshot snapshot,boolean target,ObservationPort.Cancellation c){actual.verifyV3(snapshot,target,c);}
    }
    private static void await(CountDownLatch latch){try{assertTrue(latch.await(5,TimeUnit.SECONDS));}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}}
    private static void refused(PlanRefusal.Code code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(PlanRefusal.class,action).code());}
}
