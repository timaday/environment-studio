package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.definition.*;
import studio.environment.server.session.HostedSessions;

/** Actual shared service/controllers; publication and observation are explicitly invented witnesses. */
class PlanV1VersionBoundaryTest {
    static final class Fixture {
        final SessionLedger ledger=new SessionLedger(Clock.systemUTC(),ignored->{});
        final SessionLedger.Lease lease=((SessionLedger.Accepted)ledger.admit("mock-http-version",new Owner("https://mock.invalid","version-owner"))).lease();
        final NativeCommand.Reference reference=new NativeCommand.Reference("00000000-0000-4000-8000-000000000061","2");
        final AtomicLong clock=new AtomicLong();
        final AtomicInteger reservations=new AtomicInteger(), closes=new AtomicInteger();
        ObservationResult result;
        final HostedPlanService service;
        final PlanController controller;
        final PlanViewController views;
        Fixture() throws Exception {
            var v3=assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,
                    new NativeV3DefinitionBytesCompiler().compile(Files.readAllBytes(Path.of("../../fixtures/native-v3/definition.json")),DefinitionBytesCompiler.Format.JSON)).checked();
            var v2=assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,
                    new NativeDefinitionBytesCompiler().compile(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")),DefinitionBytesCompiler.Format.JSON));
            var workspace=new Workspace(){
                public PublishedDefinition definition(Owner owner,NativeCommand.Reference ref){return new PublishedDefinition(ref,"invented-v2-publication",v2,List.of());}
                public PublishedDefinition definitionV3(Owner owner,NativeCommand.Reference ref){return new PublishedDefinition(ref,"invented-v3-publication-witness",new PlanDefinition.V3(v3),List.of());}
                public PublishedProfile profile(Owner owner,NativeCommand.Reference ref,PublishedDefinition definition){throw new AssertionError("UNEXPECTED_PROFILE_LOOKUP");}
            };
            var port=new ObservationPort(){
                public ObservationResult observe(Selection selected,TransientCredentials credentials,Cancellation flag){throw new AssertionError("RESERVATION_REQUIRED");}
                public Reservation reserve(Selection selected){return admitted();}
                public Reservation reserveV3(V3Selection selected){return admitted();}
                Reservation admitted(){reservations.incrementAndGet();return new Reservation.Admitted(new Permit(){
                    public ObservationResult observe(TransientCredentials credentials,Cancellation flag){credentials.close();if(result==null)throw new AssertionError("UNEXPECTED_DATABASE_WORK");return result;}
                    public void close(){closes.incrementAndGet();}
                });}
            };
            service=new HostedPlanService(ledger::guard,workspace,Map.of("mock-destination",new Destination("mock-destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,port)),new PlanContentAdapter(),clock::get);
            var runtime=new PlanRuntime(service,List.of(),(owner,id)->id.equals("mock-destination"));
            controller=new PlanController(runtime,new HostedSessions(Clock.systemUTC(),List.of()));
            views=new PlanViewController(runtime,controller);
        }
        HostedPlanService.Ack create(boolean v3){return v3?service.createV3(lease,UUID.randomUUID().toString(),reference,"mock-pg","mock-destination"):
                service.create(lease,UUID.randomUUID().toString(),reference,"mock-pg","mock-destination");}
    }
    @Test void legacyCurrentAndIdSummariesCannotExposeAnOwnedV3Plan()throws Exception {
        var fixture=new Fixture();var plan=fixture.create(true);
        assertEquals(plan.planId(),fixture.service.view(fixture.lease,Optional.empty()).planId());
        var mvc=MockMvcBuilders.standaloneSetup(fixture.controller,fixture.views).build();
        for(String path:List.of("/api/v1/plans/current","/api/v1/plans/"+plan.planId()))
            mvc.perform(get(path).requestAttr(HostedSessions.REQUEST_LEASE,fixture.lease))
                    .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(content().json("{\"code\":\"NOT_FOUND\"}"));
        assertEquals(0,fixture.reservations.get());
    }
    @FunctionalInterface interface Call {void run()throws Exception;}
    static void missing(Call call){assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,call::run).code());}
    static MockHttpServletRequest unread(Fixture fixture){
        var request=new MockHttpServletRequest(){
            @Override public AsyncContext startAsync(){throw new AssertionError("WRONG_VERSION_STARTED_ASYNC");}
            @Override public ServletInputStream getInputStream(){throw new AssertionError("WRONG_VERSION_OPENED_BODY");}
        };
        request.setContentType("application/json");request.setAttribute(HostedSessions.REQUEST_LEASE,fixture.lease);return request;
    }
    static MockHttpServletResponse unwritten(){return new MockHttpServletResponse(){
        @Override public ServletOutputStream getOutputStream(){throw new AssertionError("WRONG_VERSION_OPENED_OUTPUT");}
    };}
    @Test void wrongVersionSummariesAndMutationBodiesRefuseBeforeOccupiedMetadataSlots()throws Exception {
        var f=new Fixture();var plan=f.create(true);var request=unread(f);var response=unwritten();
        var slots=new ArrayList<PlanController.MetadataSlot>();
        try {
            for(int index=0;index<4;index++)slots.add(PlanController.metadata());
            missing(()->f.controller.current(request,response));
            missing(()->f.controller.summary(plan.planId(),request,response));
            missing(()->f.controller.reserve(plan.planId(),request,response));
        }finally{slots.forEach(PlanController.MetadataSlot::close);}
        assertEquals(0,f.reservations.get());
        try(var slot=PlanController.metadata()){assertNotNull(slot);}
        var operation=f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        assertEquals(1,f.reservations.get());f.service.cancel(f.lease,operation.operationId().orElseThrow());assertEquals(1,f.closes.get());
    }
    @Test void allThirteenViewRoutesAndCommandsRefuseBeforeOccupiedScratchOrBodyAccess()throws Exception {
        var f=new Fixture();var plan=f.create(true);var request=unread(f);var response=unwritten();
        try(var held=f.service.reserveView(f.lease,plan.planId())) {
            List<Call> routes=List.of(
                    ()->f.views.materialization(plan.planId(),request,response),()->f.views.documents(plan.planId(),request,response),
                    ()->f.views.entities(plan.planId(),request,response),()->f.views.relations(plan.planId(),request,response),
                    ()->f.views.draft(plan.planId(),request,response),()->f.views.containment(plan.planId(),request,response),
                    ()->f.views.placements(plan.planId(),request,response),()->f.views.document(plan.planId(),request,response),
                    ()->f.views.bindings(plan.planId(),request,response),()->f.views.bindingLocations(plan.planId(),request,response),
                    ()->f.views.capture(plan.planId(),request,response),()->f.views.preview(plan.planId(),request,response),
                    ()->f.views.validation(plan.planId(),request,response),()->f.controller.command(plan.planId(),request,response));
            for(var route:routes)missing(route);
            assertTrue(held.live());
        }
        try(var recovered=f.service.reserveView(f.lease,plan.planId())){assertTrue(recovered.live());}
        assertEquals(0,f.reservations.get());
    }
    @Test void wrongVersionCredentialAndCancelRequestsLeaveTheOneShotReservationUsable()throws Exception {
        var f=new Fixture();var plan=f.create(true);
        String operation=f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        var request=unread(f);var response=unwritten();
        missing(()->f.controller.credentials(operation,request,response));
        missing(()->f.controller.operation(operation,request,response));
        missing(()->f.controller.cancel(operation,request,response));
        assertEquals(HostedPlanService.Phase.RESERVED,f.service.status(f.lease,operation).phase());assertEquals(0,f.closes.get());
        try(var submission=f.service.claimCredentials(f.lease,operation)){assertFalse(submission.cancelled());}
        assertEquals(1,f.closes.get());
    }
    @Test void wrongVersionPollingDoesNotExpireAnOldReservation()throws Exception {
        var f=new Fixture();var plan=f.create(true);
        String operation=f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        f.clock.set(61_000_000_000L);
        var request=unread(f);var response=unwritten();
        missing(()->f.controller.operation(operation,request,response));missing(()->f.controller.cancel(operation,request,response));
        missing(()->f.controller.credentials(operation,request,response));assertEquals(0,f.closes.get());
        assertEquals(HostedPlanService.Phase.EXPIRED,f.service.status(f.lease,operation).phase());assertEquals(1,f.closes.get());
    }
    @Test void wrongVersionOperationNeverInvokesItsPendingCleanupHandle()throws Exception {
        var f=new Fixture();var plan=f.create(true);var polls=new AtomicInteger();var cancels=new AtomicInteger();
        var complete=new AtomicBoolean();
        f.result=new ObservationResult.Refused(ObservationResult.Code.DATABASE_FAILURE,ObservationResult.Cleanup.INCONCLUSIVE,Optional.of(new ObservationResult.CleanupHandle(){
            public ObservationResult.Cleanup status(){polls.incrementAndGet();return complete.get()?ObservationResult.Cleanup.COMPLETE:ObservationResult.Cleanup.INCONCLUSIVE;}
            public ObservationResult.Cleanup retry(){throw new AssertionError("UNEXPECTED_RETRY");}
            public void cancel(){cancels.incrementAndGet();}
        }));
        String operation=f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        f.service.submit(f.lease,operation,(user,password)->{user[0]='m';password[0]='p';return new CredentialLengths(1,1);});
        int before=polls.get();var request=unread(f);var response=unwritten();
        missing(()->f.controller.operation(operation,request,response));missing(()->f.controller.cancel(operation,request,response));
        assertEquals(before,polls.get());assertEquals(0,cancels.get());
        complete.set(true);assertEquals(HostedPlanService.Cleanup.COMPLETE,f.service.status(f.lease,operation).cleanup());assertEquals(before+1,polls.get());
    }
    @Test void originalV2TerminalStatusAndSummaryRulesSurviveANewCurrentV3Plan()throws Exception {
        var f=new Fixture();var old=f.create(false);
        String operation=f.service.reserve(f.lease,old.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        f.service.cancel(f.lease,operation);
        var discard=new HostedPlanService.Mutation("1",UUID.randomUUID().toString());var ack=f.service.discard(f.lease,old.planId(),discard);
        var newer=f.create(true);assertNotEquals(old.planId(),newer.planId());
        var mvc=MockMvcBuilders.standaloneSetup(f.controller,f.views).build();
        mvc.perform(get("/api/v1/operations/"+operation).requestAttr(HostedSessions.REQUEST_LEASE,f.lease)).andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(old.planId())).andExpect(jsonPath("$.phase").value("cancelled"));
        assertEquals(ack,f.service.discard(f.lease,old.planId(),discard));
        mvc.perform(get("/api/v1/plans/"+old.planId()).requestAttr(HostedSessions.REQUEST_LEASE,f.lease)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/plans/current").requestAttr(HostedSessions.REQUEST_LEASE,f.lease)).andExpect(status().isNotFound());
        f.service.discard(f.lease,newer.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        var current=f.create(false);
        mvc.perform(get("/api/v1/plans/current").requestAttr(HostedSessions.REQUEST_LEASE,f.lease)).andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(current.planId())).andExpect(jsonPath("$.exportAvailable").value(false));
    }
    @Test void currentSummaryCannotSubstituteANewV3PlanDuringOutputAcquisition()throws Exception {
        var f=new Fixture();var original=f.create(false);var request=unread(f);
        var replacement=new AtomicReference<String>();
        var response=new MockHttpServletResponse(){
            @Override public ServletOutputStream getOutputStream(){
                f.service.discard(f.lease,original.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
                replacement.set(f.create(true).planId());return super.getOutputStream();
            }
        };
        missing(()->f.controller.current(request,response));assertNotNull(replacement.get());
        assertEquals(0,response.getContentAsByteArray().length);
        assertEquals(replacement.get(),f.service.view(f.lease,Optional.empty()).planId());
        try(var slot=PlanController.metadata()){assertNotNull(slot);}
    }
    @Test void foreignOrRevokedLeaseCannotUseVersionedPlanOrRetainedMetadata()throws Exception {
        var f=new Fixture();var created=f.create(false);
        String operation=f.service.reserve(f.lease,created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        f.service.cancel(f.lease,operation);
        var foreign=((SessionLedger.Accepted)f.ledger.admit("mock-other-lease",new Owner("https://mock.invalid","other"))).lease();
        var request=unread(f);request.setAttribute(HostedSessions.REQUEST_LEASE,foreign);var response=unwritten();
        missing(()->f.controller.summary(created.planId(),request,response));missing(()->f.controller.operation(operation,request,response));
        f.ledger.close(f.lease.id());request.setAttribute(HostedSessions.REQUEST_LEASE,f.lease);
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->f.controller.operation(operation,request,response)).code());
        var again=((SessionLedger.Accepted)f.ledger.admit("mock-new-login",f.lease.owner())).lease();request.setAttribute(HostedSessions.REQUEST_LEASE,again);
        missing(()->f.controller.summary(created.planId(),request,response));missing(()->f.controller.operation(operation,request,response));
    }
}
