package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import studio.environment.core.plan.*;
import studio.environment.server.session.HostedSessions;

class V3PlanControllerTest {
    @FunctionalInterface interface Call {void invoke();}
    static final class Fixture {
        final PlanV1VersionBoundaryTest.Fixture core;
        final HostedSessions sessions=new HostedSessions(Clock.systemUTC(),List.of());
        final PlanRuntime runtime;final V3PlanController controller;
        Fixture()throws Exception {core=new PlanV1VersionBoundaryTest.Fixture();runtime=new PlanRuntime(core.service,List.of(),(owner,id)->owner.equals(core.lease.owner())&&id.equals("mock-destination"));controller=new V3PlanController(runtime,sessions);}
        MockHttpServletRequest body(V3PlanTransportTest.Context context,String json){var request=V3PlanTransportTest.bodyRequest(context,json);request.setAttribute(HostedSessions.REQUEST_LEASE,core.lease);return request;}
    }
    @Test void allSixResourceRoutesRequireV3BeforeBusySlotsBodyOutputOrReservationExpiry()throws Exception {
        var f=new Fixture();var plan=f.core.create(false);
        String operation=f.core.service.reserve(f.core.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        f.core.clock.set(61_000_000_000L);
        var request=new MockHttpServletRequest(){
            @Override public AsyncContext startAsync(){throw new AssertionError("WRONG_VERSION_ASYNC");}
            @Override public ServletInputStream getInputStream(){throw new AssertionError("WRONG_VERSION_BODY");}
        };request.setAttribute(HostedSessions.REQUEST_LEASE,f.core.lease);request.setContentType("application/json");
        var response=new MockHttpServletResponse(){@Override public ServletOutputStream getOutputStream(){throw new AssertionError("WRONG_VERSION_OUTPUT");}};
        var slots=new ArrayList<PlanController.MetadataSlot>();
        try {
            for(int i=0;i<4;i++)slots.add(PlanController.metadata());
            for(Call call:List.<Call>of(()->f.controller.current(request,response),()->f.controller.summary(plan.planId(),request,response),
                    ()->f.controller.reserve(plan.planId(),request,response),()->f.controller.credentials(operation,request,response),
                    ()->f.controller.operation(operation,request,response),()->f.controller.cancel(operation,request,response)))
                assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,call::invoke).code());
            assertEquals(0,f.core.closes.get());assertFalse(f.runtime.transfers().awaitingWork(f.core.lease));
        } finally {slots.forEach(PlanController.MetadataSlot::close);f.core.service.cancel(f.core.lease,operation);}
    }
    @Test void ownedCreateAndReservationUseTheRealV3ServiceAndClosedReplies()throws Exception {
        var f=new Fixture();var createContext=new V3PlanTransportTest.Context();var created=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(created);
        String json="{\"expectedRevision\":\"0\",\"requestId\":\""+UUID.randomUUID()+"\",\"definition\":{\"objectId\":\""+f.core.reference.objectId()+"\",\"workspaceRevision\":\"2\"},\"bindingId\":\"mock-pg\",\"destinationId\":\"mock-destination\"}";
        f.controller.create(f.body(createContext,json),response);
        assertTrue(createContext.complete.await(2,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
        var plan=f.core.service.viewV3(f.core.lease,Optional.empty());assertEquals(201,response.getStatus());
        assertEquals("{\"planId\":\""+plan.summary().planId()+"\",\"revision\":\"1\"}",created.bytes.toString(StandardCharsets.UTF_8));
        var reserveContext=new V3PlanTransportTest.Context();var reserved=new V3PlanTransportTest.Output();var reservedResponse=V3PlanTransportTest.response(reserved);
        f.controller.reserve(plan.summary().planId(),f.body(reserveContext,"{\"expectedRevision\":\"1\",\"requestId\":\""+UUID.randomUUID()+"\",\"discardDraftOnSuccess\":true}"),reservedResponse);
        assertTrue(reserveContext.complete.await(2,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
        String operation=f.core.service.viewV3(f.core.lease,Optional.empty()).summary().activeOperationId().orElseThrow();
        try {assertEquals(202,reservedResponse.getStatus());assertEquals(1,f.core.reservations.get());assertEquals(0,f.core.closes.get());
            assertEquals("{\"planId\":\""+plan.summary().planId()+"\",\"revision\":\"1\",\"operationId\":\""+operation+"\"}",reserved.bytes.toString(StandardCharsets.UTF_8));
        } finally {f.core.service.cancel(f.core.lease,operation);}
    }
    @Test void fullCredentialHttpCapacityLeavesTheOriginalOneShotAttemptUsable()throws Exception {
        var f=new Fixture();var plan=f.core.create(true);String operation=f.core.service.reserve(f.core.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        var records=new ArrayList<V3PlanTransfers.Operation>();
        var unread=new MockHttpServletRequest(){@Override public AsyncContext startAsync(){throw new AssertionError("CAPACITY_STARTED_ASYNC");}@Override public ServletInputStream getInputStream(){throw new AssertionError("CAPACITY_READ_CREDENTIALS");}};
        unread.setContentType("application/json");unread.setAttribute(HostedSessions.REQUEST_LEASE,f.core.lease);
        try {
            for(int i=0;i<4;i++)records.add(f.runtime.transfers().admitCredentials(f.core.lease));
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->f.controller.credentials(operation,unread,new MockHttpServletResponse())).code());
            assertEquals(HostedPlanService.Phase.RESERVED,f.core.service.status(f.core.lease,operation).phase());assertEquals(0,f.core.closes.get());
            records.getFirst().settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);
            f.core.result=new studio.environment.core.observation.ObservationResult.Refused(studio.environment.core.observation.ObservationResult.Code.DATABASE_FAILURE,studio.environment.core.observation.ObservationResult.Cleanup.COMPLETE);
            var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);
            f.controller.credentials(operation,f.body(context,"{\"username\":\"invented-reader\",\"password\":\"invented-password\"}"),response);
            assertTrue(context.complete.await(2,TimeUnit.SECONDS));assertEquals(200,response.getStatus());
            assertTrue(output.bytes.toString(StandardCharsets.UTF_8).contains("\"phase\":\"refused\""));assertFalse(output.bytes.toString(StandardCharsets.UTF_8).contains("invented-password"));
            // A consumed observation reports its cleanup; Submission must not close that permit again.
            assertEquals(0,f.core.closes.get());assertEquals(1,f.core.reservations.get());
            assertEquals(HostedPlanService.Cleanup.COMPLETE,f.core.service.status(f.core.lease,operation).cleanup());
        } finally {records.forEach(record->record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions));f.core.service.cancel(f.core.lease,operation);}
        V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
    }
    @Test void malformedCredentialsConsumeOnlyTheirClaimAndCannotBeRetried()throws Exception {
        var f=new Fixture();var plan=f.core.create(true);String operation=f.core.service.reserve(f.core.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);
        f.controller.credentials(operation,f.body(context,"{\"username\":\"invented-reader\",\"password\":\"invented-password\",\"owner\":\"caller-cannot-select\"}"),response);
        assertTrue(context.complete.await(2,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
        assertEquals(400,response.getStatus());assertEquals("{\"code\":\"MALFORMED_BODY\"}",output.bytes.toString(StandardCharsets.UTF_8));assertEquals(1,f.core.closes.get());
        var retry=f.body(new V3PlanTransportTest.Context(),"{}");
        assertEquals(PlanRefusal.Code.CREDENTIALS_ALREADY_CONSUMED,assertThrows(PlanRefusal.class,()->f.controller.credentials(operation,retry,new MockHttpServletResponse())).code());
        assertFalse(f.runtime.transfers().awaitingWork(f.core.lease));assertEquals(1,f.core.closes.get());
    }
    @Test void summaryStatusAndCancellationExposeOnlyTheirOriginalBackendState()throws Exception {
        var f=new Fixture();var plan=f.core.create(true);
        for(boolean current:new boolean[]{true,false}) {
            var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);var request=f.body(context,"");
            if(current)f.controller.current(request,response);else f.controller.summary(plan.planId(),request,response);
            assertTrue(context.complete.await(2,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
            String text=output.bytes.toString(StandardCharsets.UTF_8);assertEquals(200,response.getStatus());
            assertTrue(text.contains("\"currentComputedCounts\":null"));assertTrue(text.contains("\"targetComputedCounts\":null"));assertTrue(text.contains("\"exportAvailable\":false"));
        }
        String operation=f.core.service.reserve(f.core.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString())).operationId().orElseThrow();
        var cancelContext=new V3PlanTransportTest.Context();var cancelled=new V3PlanTransportTest.Output();
        f.controller.cancel(operation,f.body(cancelContext,"{}"),V3PlanTransportTest.response(cancelled));
        assertTrue(cancelContext.complete.await(2,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
        assertTrue(cancelled.bytes.toString(StandardCharsets.UTF_8).contains("\"phase\":\"cancelled\""));assertEquals(1,f.core.closes.get());
        f.core.service.discard(f.core.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));f.core.create(false);
        var statusContext=new V3PlanTransportTest.Context();var status=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(status);
        f.controller.operation(operation,f.body(statusContext,""),response);
        assertTrue(statusContext.complete.await(2,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
        assertEquals(200,response.getStatus());assertTrue(status.bytes.toString(StandardCharsets.UTF_8).contains("\"planId\":\""+plan.planId()+"\""));assertEquals(1,f.core.closes.get());
    }
    @Test void callerCannotAddReadinessOrVersionAuthorityToCreateMetadata()throws Exception {
        for(String field:List.of("modelVersion","owner","exportAvailable")) {
            var f=new Fixture();var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);
            String json="{\"expectedRevision\":\"0\",\"requestId\":\""+UUID.randomUUID()+"\",\"definition\":{\"objectId\":\""+f.core.reference.objectId()+"\",\"workspaceRevision\":\"2\"},\"bindingId\":\"mock-pg\",\"destinationId\":\"mock-destination\",\""+field+"\":\"untrusted\"}";
            f.controller.create(f.body(context,json),response);assertTrue(context.complete.await(2,TimeUnit.SECONDS));V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
            assertEquals(400,response.getStatus());assertEquals("{\"code\":\"MALFORMED_BODY\"}",output.bytes.toString(StandardCharsets.UTF_8));
            assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->f.core.service.viewV3(f.core.lease,Optional.empty())).code());assertEquals(0,f.core.reservations.get());
        }
    }
}
