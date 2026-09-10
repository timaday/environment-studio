package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import jakarta.servlet.*;
import studio.environment.core.plan.*;
import studio.environment.server.session.HostedSessions;

class V3PlanWorkflowControllerTest {
    @Test void fixedVersionPrecedesAnyRequestInputOrAsyncAdmission()throws Exception{
        var f=new V3PlanControllerTest.Fixture();var plan=f.core.create(false);var controller=new V3PlanWorkflowController(f.runtime,f.sessions);
        var request=new MockHttpServletRequest(){
            @Override public AsyncContext startAsync(){throw new AssertionError("WRONG_VERSION_ASYNC");}
            @Override public ServletInputStream getInputStream(){throw new AssertionError("WRONG_VERSION_INPUT");}
        };request.setContentType("application/json");request.setAttribute(HostedSessions.REQUEST_LEASE,f.core.lease);
        var response=new MockHttpServletResponse();
        for(Runnable call:List.<Runnable>of(()->controller.capture(plan.planId(),request,response),()->controller.preview(plan.planId(),request,response),()->controller.validation(plan.planId(),request,response)))
            assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,call::run).code());
        assertFalse(f.runtime.awaitingCleanupWork(f.core.lease));
    }
    @Test void semanticRecordRefusalRollsBackOnlyTheNewViewAdmission()throws Exception{
        var f=new V3PlanControllerTest.Fixture();var plan=f.core.create(true);var controller=new V3PlanWorkflowController(f.runtime,f.sessions);
        var held=f.runtime.transfers().admitSemantic(f.core.lease);
        var request=new MockHttpServletRequest(){@Override public ServletInputStream getInputStream(){throw new AssertionError("CAPACITY_INPUT");}@Override public AsyncContext startAsync(){throw new AssertionError("CAPACITY_ASYNC");}};
        request.setContentType("application/json");request.setAttribute(HostedSessions.REQUEST_LEASE,f.core.lease);
        try{
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->controller.validation(plan.planId(),request,new MockHttpServletResponse())).code());
            try(var next=f.core.service.reserveCommand(f.core.lease,plan.planId(),PlanDefinition.Version.V3)){assertTrue(next.live());}
            assertTrue(f.runtime.transfers().awaitingWork(f.core.lease));
        }finally{held.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,f.sessions);}
        V3PlanTransportTest.settled(f.runtime.transfers(),f.core.lease);
    }
}
