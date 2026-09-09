package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.session.*;
import static studio.environment.core.plan.PlanPorts.*;

class PlanLifecycleTest {
    static final class Harness {
        final HostedPlanServiceTest base=new HostedPlanServiceTest();
        final AtomicLong nanos=new AtomicLong();
        final AtomicInteger opens=new AtomicInteger(), closes=new AtomicInteger(), projections=new AtomicInteger(), renders=new AtomicInteger();
        final AtomicBoolean reject=new AtomicBoolean(), incompleteTarget=new AtomicBoolean();
        volatile CountDownLatch entered,release,renderEntered,renderRelease;
        volatile ObservationResult.CleanupHandle handle;
        Content content=new Content(List.of(new Source("sample","<sample>MiXeD-Canary</sample>","source-digest")),new ObservedGraph(List.of(),List.of()),Map.of());
        final ObservationPort port=new ObservationPort() {
            public ObservationResult observe(Selection selection,TransientCredentials credentials,Cancellation cancel) { throw new AssertionError("RESERVATION_REQUIRED"); }
            public Reservation reserve(Selection selection) {
                return new Reservation.Admitted(new Permit() {
                    boolean used;
                    public ObservationResult observe(TransientCredentials credentials,Cancellation cancel) {
                        synchronized(this) { if(used) throw new AssertionError("DOUBLE_USE"); used=true; }
                        opens.incrementAndGet(); credentials.close();
                        if(entered!=null) { entered.countDown(); await(release); }
                        if(handle!=null) return new ObservationResult.Refused(ObservationResult.Code.CLEANUP_INCONCLUSIVE,ObservationResult.Cleanup.INCONCLUSIVE,Optional.of(handle));
                        if(reject.get()) return new ObservationResult.Refused(ObservationResult.Code.DATABASE_FAILURE,ObservationResult.Cleanup.COMPLETE);
                        return new ObservationResult.Complete(new ObservationResult.Observation("a".repeat(64),"logical","binding-digest",List.of(),PlanObservedDestinationTest.evidence()));
                    }
                    public synchronized void close() { if(!used) { used=true; closes.incrementAndGet(); } }
                });
            }
        };
        final ContentAdapter adapter=new ContentAdapter() {
            public ContentResult project(PublishedDefinition definition,String binding,ObservationResult.Observation observed) { projections.incrementAndGet(); return new ContentResult.Complete(content); }
            public ContentResult materialize(PublishedDefinition definition,String binding,Content current,Draft draft) {
                renders.incrementAndGet(); if(renderEntered!=null) { renderEntered.countDown(); await(renderRelease); } return incompleteTarget.get()?new ContentResult.Rejected(List.of("UNRESOLVED_FIELDS")):new ContentResult.Complete(content);
            }
            public Capture capture(PublishedDefinition definition,String binding,Content current,ProfileCapture.Command command) { throw new AssertionError("SHOULD_REFUSE_BEFORE_CAPTURE"); }
            public DocumentView compare(HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode) { var source=snapshot.selected(target).sources().stream().filter(s->s.documentId().equals(documentId)).findFirst().orElseThrow();return new DocumentView(source.documentId(),mode,source.xml(),true,false,true,List.of()); }
        };
        final HostedPlanService service=new HostedPlanService(base.authority::guard,base.workspace,Map.of("destination",new Destination("destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,port)),adapter,nanos::get);
        final HostedPlanService.Ack created=service.create(base.lease,UUID.randomUUID().toString(),base.ref,"invented-binding","destination");
        HostedPlanService.Mutation mutation() { return new HostedPlanService.Mutation(service.summary(base.lease,created.planId()).revision(),UUID.randomUUID().toString()); }
        String reserve() { return service.reserve(base.lease,created.planId(),mutation()).operationId().orElseThrow(); }
        HostedPlanService.Status inspect() { return service.submit(base.lease,reserve(),PlanLifecycleTest::credentials); }
    }
    static CredentialLengths credentials(char[] user,char[] password) { user[0]='u'; password[0]='P'; return new CredentialLengths(1,1); }
    static void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new AssertionError("TEST_TIMEOUT"); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError("TEST_INTERRUPTED"); }
    }
    @Test void failedReinspectionMakesPreviousComparisonDisplayOnlyUntilNewSuccess() {
        var h=new Harness(); h.inspect(); h.reject.set(true); h.inspect();
        var summary=h.service.summary(h.base.lease,h.created.planId());
        assertEquals("2",summary.revision()); assertFalse(summary.inspectionValid()); assertTrue(summary.targetComplete());
        assertEquals("<sample>MiXeD-Canary</sample>",h.service.comparison(h.base.lease,h.created.planId(),"2",false,"sample",ViewMode.RAW,true).text());
        assertThrows(PlanRefusal.class,()->h.service.replaceDraft(h.base.lease,h.created.planId(),h.mutation(),Draft.empty()));
        assertThrows(PlanRefusal.class,()->h.service.materialize(h.base.lease,h.created.planId(),"2"));
        assertThrows(PlanRefusal.class,()->h.service.capture(h.base.lease,h.created.planId(),"2",new ProfileCapture.Command("neutral",java.math.BigInteger.ONE,List.of())));
        h.reject.set(false); h.inspect(); assertTrue(h.service.summary(h.base.lease,h.created.planId()).inspectionValid());
        assertEquals("3",h.service.summary(h.base.lease,h.created.planId()).revision());
    }
    @Test void oneShotClaimPrecedesWorkerAllocationAndClosePreventsAnyLaterRead() {
        var h=new Harness(); var id=h.reserve();
        var claim=h.service.claimCredentials(h.base.lease,id);
        assertEquals(PlanRefusal.Code.CREDENTIALS_ALREADY_CONSUMED,assertThrows(PlanRefusal.class,()->h.service.claimCredentials(h.base.lease,id)).code());
        claim.close(); claim.close();
        assertEquals(1,h.closes.get());
        assertThrows(PlanRefusal.class,()->claim.process((u,p)->{throw new AssertionError("CLOSED_CLAIM_READ");}));
        assertEquals(HostedPlanService.Phase.REFUSED,h.service.status(h.base.lease,id).phase());
        assertEquals(0,h.opens.get());
    }
    @Test void cancellationBeforeClaimedWorkerStartsNeverReadsCredentials() {
        var h=new Harness(); var id=h.reserve();
        try(var claim=h.service.claimCredentials(h.base.lease,id)) {
            h.service.cancel(h.base.lease,id);
            assertEquals(HostedPlanService.Phase.CANCELLED,claim.process((u,p)->{throw new AssertionError("CANCELLED_CLAIM_READ");}).phase());
        }
        assertEquals(1,h.closes.get()); assertEquals(0,h.opens.get());
    }
    @Test void expiryDuringCredentialReadCannotStartDatabaseAuthentication() {
        var h=new Harness(); var id=h.reserve();
        assertThrows(PlanRefusal.class,()->h.service.submit(h.base.lease,id,(user,password)->{
            h.base.time.now=h.base.time.now.plusSeconds(1800);
            return credentials(user,password);
        }));
        assertEquals(0,h.opens.get(),"Expired credential decoder must not start database authentication");
        assertEquals(1,h.closes.get());
    }
    @Test void cancellationDuringCredentialReadCannotStartDatabaseAuthentication() {
        var h=new Harness(); var id=h.reserve();
        var status=h.service.submit(h.base.lease,id,(user,password)->{
            h.service.cancel(h.base.lease,id);
            return credentials(user,password);
        });
        assertEquals(HostedPlanService.Phase.CANCELLED,status.phase());
        assertEquals(0,h.opens.get(),"Cancelled credential decoder must not start database authentication");
        assertEquals(1,h.closes.get());
    }
    @Test void expiryBeforeSubmissionCannotReadAnyCredentialBody() {
        var h=new Harness(); var id=h.reserve(); h.base.time.now=h.base.time.now.plusSeconds(1800);
        assertThrows(PlanRefusal.class,()->h.service.submit(h.base.lease,id,(user,password)->{throw new AssertionError("EXPIRED_BODY_READ");}));
        assertEquals(0,h.opens.get());
    }
    @Test void malformedCredentialsConsumeBeforeReadAndClearOwnedBuffersWithoutConnecting() {
        var h=new Harness(); h.inspect(); var id=h.reserve(); var buffers=new ArrayList<char[]>();
        var status=h.service.submit(h.base.lease,id,(user,password)->{
            buffers.add(user); buffers.add(password); user[0]='u'; password[0]='\ud800'; return new CredentialLengths(1,1);
        });
        assertEquals(HostedPlanService.Phase.REFUSED,status.phase()); assertEquals(1,h.opens.get()); assertEquals(1,h.closes.get());
        for(var buffer:buffers) for(char ch:buffer) assertEquals(0,ch);
        assertThrows(PlanRefusal.class,()->h.service.submit(h.base.lease,id,(u,p)->{throw new AssertionError("READ_TWICE");}));
        assertFalse(h.service.summary(h.base.lease,h.created.planId()).inspectionValid());
    }
    @Test void reservationExpiryUsesMonotonicBoundaryAndRetiresInspectionAuthority() {
        var h=new Harness(); h.inspect(); var id=h.reserve(); h.nanos.set(59_999_999_999L);
        assertEquals(HostedPlanService.Phase.RESERVED,h.service.status(h.base.lease,id).phase());
        h.nanos.incrementAndGet(); assertEquals(HostedPlanService.Phase.EXPIRED,h.service.status(h.base.lease,id).phase());
        assertEquals(1,h.closes.get()); assertFalse(h.service.summary(h.base.lease,h.created.planId()).inspectionValid());
        assertEquals(1,h.opens.get());
    }
    @Test void cancellationWinsOverLateSuccessfulObservationAndDoesNotReauthorizeOldData() throws Exception {
        var h=new Harness(); h.inspect(); h.entered=new CountDownLatch(1); h.release=new CountDownLatch(1); var id=h.reserve();
        try(var executor=Executors.newSingleThreadExecutor()) {
            var pending=executor.submit(()->h.service.submit(h.base.lease,id,PlanLifecycleTest::credentials));
            try { await(h.entered); assertEquals(HostedPlanService.Phase.CANCELLED,h.service.cancel(h.base.lease,id).phase()); }
            finally { h.release.countDown(); }
            assertEquals(HostedPlanService.Phase.CANCELLED,pending.get(5,TimeUnit.SECONDS).phase());
        }
        assertEquals("2",h.service.summary(h.base.lease,h.created.planId()).revision());
        assertFalse(h.service.summary(h.base.lease,h.created.planId()).inspectionValid()); assertEquals(1,h.projections.get());
    }
    @Test void expiredLeaseCannotInstallDespiteCleanupHookNeverRunning() throws Exception {
        var h=new Harness(); h.entered=new CountDownLatch(1); h.release=new CountDownLatch(1); var id=h.reserve();
        try(var executor=Executors.newSingleThreadExecutor()) {
            var pending=executor.submit(()->h.service.submit(h.base.lease,id,PlanLifecycleTest::credentials));
            try { await(h.entered); h.base.time.now=h.base.time.now.plusSeconds(1800); }
            finally { h.release.countDown(); }
            var failure=assertThrows(ExecutionException.class,()->pending.get(5,TimeUnit.SECONDS));
            assertEquals(PlanRefusal.Code.SESSION_REQUIRED,((PlanRefusal)failure.getCause()).code());
        }
        assertThrows(PlanRefusal.class,()->h.service.summary(h.base.lease,h.created.planId()));
    }
    @Test void concurrentSubmissionsHaveExactlyOneBodyReaderAndOnePhysicalOperation() throws Exception {
        var h=new Harness(); var id=h.reserve(); var reads=new AtomicInteger();
        try(var executor=Executors.newFixedThreadPool(8)) {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<8;i++) tasks.add(()->{
                try { h.service.submit(h.base.lease,id,(u,p)->{reads.incrementAndGet();return credentials(u,p);}); return true; }
                catch(PlanRefusal refused) { assertEquals(PlanRefusal.Code.CREDENTIALS_ALREADY_CONSUMED,refused.code());return false; }
            });
            int accepted=0; for(var result:executor.invokeAll(tasks)) if(result.get()) accepted++;
            assertEquals(1,accepted);
        }
        assertEquals(1,reads.get()); assertEquals(1,h.opens.get());
    }
    @Test void pendingCleanupRetainsBusyCapacityAndOnlyOriginalHandleCanFinish() {
        var h=new Harness(); var complete=new AtomicBoolean(); var retries=new AtomicInteger();
        h.handle=new ObservationResult.CleanupHandle() {
            public ObservationResult.Cleanup status() { return complete.get()?ObservationResult.Cleanup.COMPLETE:ObservationResult.Cleanup.INCONCLUSIVE; }
            public ObservationResult.Cleanup retry() { retries.incrementAndGet(); return status(); }
            public void cancel() { }
        };
        assertEquals(HostedPlanService.Cleanup.INCONCLUSIVE,h.inspect().cleanup());
        assertEquals(PlanRefusal.Code.PLAN_BUSY,assertThrows(PlanRefusal.class,h::reserve).code());
        assertThrows(PlanRefusal.class,()->h.service.invalidate(h.base.lease));
        complete.set(true); h.service.invalidate(h.base.lease);
        assertEquals(2,retries.get()); assertEquals(1,h.opens.get());
    }
    @Test void incompleteAcceptedDraftAdvancesRevisionAndClearsOldTargetAndReplayDoesNotRenderAgain() {
        var h=new Harness(); h.inspect(); h.incompleteTarget.set(true); var mutation=h.mutation();
        var ack=h.service.replaceDraft(h.base.lease,h.created.planId(),mutation,Draft.empty());
        assertEquals("3",ack.revision()); assertFalse(h.service.summary(h.base.lease,h.created.planId()).targetComplete());
        assertEquals(1,h.renders.get()); assertEquals(ack,h.service.replaceDraft(h.base.lease,h.created.planId(),mutation,Draft.empty()));
        assertEquals(1,h.renders.get());
        assertThrows(PlanRefusal.class,()->h.service.comparison(h.base.lease,h.created.planId(),"3",true,"sample",ViewMode.RAW,true));
        h.incompleteTarget.set(false); assertTrue(h.service.materialize(h.base.lease,h.created.planId(),"3").complete());
        assertEquals("3",h.service.summary(h.base.lease,h.created.planId()).revision());
    }
    @Test void publicComparisonRequiresExplicitDisclosureAndExportStaysUnavailable() {
        var h=new Harness(); h.inspect();
        assertThrows(PlanRefusal.class,()->h.service.comparison(h.base.lease,h.created.planId(),"2",false,"sample",ViewMode.RAW,false));
        var report=h.service.validate(h.base.lease,h.created.planId(),"2");
        assertEquals(studio.environment.core.RequiredCheck.values().length,report.checks().size());
        assertEquals(studio.environment.core.Outcome.UNKNOWN,report.checks().stream().filter(check->check.check()==studio.environment.core.RequiredCheck.CLIENT_CAPABILITY).findFirst().orElseThrow().outcome());
        assertFalse(report.exportAvailable()); assertThrows(PlanRefusal.class,()->h.service.requestExport(h.base.lease,h.created.planId(),"2"));
        assertFalse(report.toString().contains("MiXeD-Canary"));
    }
    @Test void fullRetainedCapacityStillAllowsBoundedReplacementAndFifthPlanRefuses() {
        var h=new Harness(); String source="x".repeat(1_048_576);
        var documents=new ArrayList<Source>(); for(int index=0;index<16;index++) documents.add(new Source("invented-"+index,source,"digest-"+index));
        h.content=new Content(documents,new ObservedGraph(List.of(),List.of()),Map.of());
        h.inspect();
        for(int index=0;index<3;index++) {
            var lease=((SessionLedger.Accepted)h.base.authority.admit("other-"+index,new Owner(h.base.lease.owner().issuer(),"owner-"+index))).lease();
            var plan=h.service.create(lease,UUID.randomUUID().toString(),h.base.ref,"invented-binding","destination");
            var operation=h.service.reserve(lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
            assertEquals(HostedPlanService.Phase.SUCCEEDED,h.service.submit(lease,operation.operationId().orElseThrow(),PlanLifecycleTest::credentials).phase());
        }
        assertEquals("3",h.service.replaceDraft(h.base.lease,h.created.planId(),h.mutation(),Draft.empty()).revision());
        assertTrue(h.service.summary(h.base.lease,h.created.planId()).targetComplete());
        var fifth=((SessionLedger.Accepted)h.base.authority.admit("fifth",new Owner(h.base.lease.owner().issuer(),"fifth"))).lease();
        assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->h.service.create(fifth,UUID.randomUUID().toString(),h.base.ref,"invented-binding","destination")).code());
        assertEquals(4,h.opens.get());
    }
    @Test void oversizedEnteredValuesRefuseBeforeAdapterAllocationAndKeepOldRevision() {
        var h=new Harness(); h.inspect();
        var fresh=new studio.environment.core.planning.TargetIntent.Ref.Fresh("oversized","invented");
        var draft=new Draft(new studio.environment.core.planning.TargetIntent(List.of(new studio.environment.core.planning.TargetIntent.EntityDecision.Create(fresh,Map.of("value",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("X".repeat(16*1_048_576+1))),Map.of())),List.of()),List.of());
        assertEquals(PlanRefusal.Code.RESOURCE_LIMIT,assertThrows(PlanRefusal.class,()->h.service.replaceDraft(h.base.lease,h.created.planId(),h.mutation(),draft)).code());
        assertEquals(0,h.renders.get()); assertEquals("2",h.service.summary(h.base.lease,h.created.planId()).revision());
    }
    @Test void replayAndOperationHistoryAreBoundedWithoutEvictionOrCredentialRead() {
        var h=new Harness();
        for(int index=0;index<255;index++) { var id=h.reserve(); h.service.cancel(h.base.lease,id); }
        assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,h::reserve).code());
        assertEquals(0,h.opens.get()); assertEquals(255,h.closes.get());
    }
    @Test void originalCleanupCompletionReleasesCapacityWithoutRestoringInspection() {
        var h=new Harness(); var complete=new AtomicBoolean();
        h.handle=new ObservationResult.CleanupHandle() {
            public ObservationResult.Cleanup status() { return complete.get()?ObservationResult.Cleanup.COMPLETE:ObservationResult.Cleanup.INCONCLUSIVE; }
            public ObservationResult.Cleanup retry() { throw new AssertionError("POLL_MUST_NOT_RETRY"); }
            public void cancel() { }
        };
        var status=h.inspect(); complete.set(true);
        assertEquals(HostedPlanService.Cleanup.COMPLETE,h.service.status(h.base.lease,status.operationId()).cleanup());
        assertFalse(h.service.summary(h.base.lease,h.created.planId()).inspectionValid());
        assertDoesNotThrow(h::reserve); assertEquals(1,h.opens.get());
    }

    @Test void acceptedEditNeverDisplaysOldTargetWhileNewMaterializationIsStillRunning() throws Exception {
        var h=new Harness(); h.inspect(); h.renderEntered=new CountDownLatch(1); h.renderRelease=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var edit=executor.submit(()->h.service.replaceDraft(h.base.lease,h.created.planId(),h.mutation(),Draft.empty()));
            try {
                await(h.renderEntered);
                var summary=h.service.summary(h.base.lease,h.created.planId());
                assertEquals("3",summary.revision()); assertFalse(summary.targetComplete());
                assertThrows(PlanRefusal.class,()->h.service.comparison(h.base.lease,h.created.planId(),"3",true,"sample",ViewMode.RAW,true));
            } finally { h.renderRelease.countDown(); }
            assertEquals("3",edit.get(5,TimeUnit.SECONDS).revision());
        }
        assertTrue(h.service.summary(h.base.lease,h.created.planId()).targetComplete());
    }

    @Test void logoutCleanupStaysInconclusiveUntilOwnedMaterializationActuallyReturns() throws Exception {
        var h=new Harness(); h.inspect(); h.renderEntered=new CountDownLatch(1); h.renderRelease=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var edit=executor.submit(()->h.service.replaceDraft(h.base.lease,h.created.planId(),h.mutation(),Draft.empty()));
            try {
                await(h.renderEntered); h.base.authority.close(h.base.lease.id());
                assertEquals(PlanRefusal.Code.CLEANUP_INCONCLUSIVE,assertThrows(PlanRefusal.class,()->h.service.invalidate(h.base.lease)).code());
            } finally { h.renderRelease.countDown(); }
            assertThrows(ExecutionException.class,()->edit.get(5,TimeUnit.SECONDS));
        }
        assertDoesNotThrow(()->h.service.invalidate(h.base.lease));
    }

    @Test void exactCredentialFreeReplayPrecedesRetirementButNeverRestoresOldPlan() {
        var h=new Harness(); h.inspect(); var mutation=h.mutation();
        var edited=h.service.replaceDraft(h.base.lease,h.created.planId(),mutation,Draft.empty());
        var reserveCommand=h.mutation(); var reserved=h.service.reserve(h.base.lease,h.created.planId(),reserveCommand);
        h.service.cancel(h.base.lease,reserved.operationId().orElseThrow());
        var discarded=h.service.discard(h.base.lease,h.created.planId(),h.mutation());
        assertEquals(edited,h.service.replaceDraft(h.base.lease,h.created.planId(),mutation,Draft.empty()));
        assertEquals(reserved,h.service.reserve(h.base.lease,h.created.planId(),reserveCommand));
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->h.service.summary(h.base.lease,h.created.planId())).code());
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->h.service.reserve(h.base.lease,h.created.planId(),new HostedPlanService.Mutation(discarded.revision(),UUID.randomUUID().toString()))).code());
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->h.service.reserve(h.base.lease,h.created.planId(),mutation)).code());
        assertEquals(HostedPlanService.Phase.CANCELLED,h.service.status(h.base.lease,reserved.operationId().orElseThrow()).phase());
    }

}
