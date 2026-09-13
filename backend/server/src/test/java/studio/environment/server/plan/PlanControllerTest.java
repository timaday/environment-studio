package studio.environment.server.plan;

import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import studio.environment.core.plan.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.session.HostedSessions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlanControllerTest {
    @Test void completionRegistrationFailureKeepsContainerTimeoutAndClosesApplicationAdmission()throws Exception {
        var ledger=new SessionLedger(Clock.systemUTC(),ignored->{});
        var lease=((SessionLedger.Accepted)ledger.admit("mock-failed-registration",new Owner("https://mock.invalid","registration-owner"))).lease();
        PlanPorts.Workspace workspace=new PlanPorts.Workspace(){
            public PlanPorts.PublishedDefinition definition(Owner owner,NativeCommand.Reference reference){throw new AssertionError("UNEXPECTED_WORKSPACE_READ");}
            public PlanPorts.PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PlanPorts.PublishedDefinition definition){throw new AssertionError("UNEXPECTED_WORKSPACE_READ");}
        };
        var service=new HostedPlanService(ledger::guard,workspace,Map.of(),new PlanContentAdapter(),System::nanoTime);
        var controller=new PlanController(new PlanRuntime(service,List.of(),(owner,id)->false),new HostedSessions(Clock.systemUTC(),List.of()));
        var timeout=new java.util.concurrent.atomic.AtomicLong(-1);var closed=new java.util.concurrent.atomic.AtomicInteger();
        var context=(jakarta.servlet.AsyncContext)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{jakarta.servlet.AsyncContext.class},(proxy,method,args)->{
            if(method.getName().equals("setTimeout")){timeout.set((Long)args[0]);return null;}
            if(method.getName().equals("addListener"))throw new IllegalStateException("PRIVATE_REGISTRATION_CANARY");
            throw new AssertionError("UNEXPECTED_CONTEXT_USE");
        });
        var request=new org.springframework.mock.web.MockHttpServletRequest(){@Override public jakarta.servlet.AsyncContext startAsync(){return context;}};
        request.setContentType("application/json");
        var failure=org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->controller.start(lease,request,new org.springframework.mock.web.MockHttpServletResponse(),closed::incrementAndGet,()->false,1024,10,200,body->{throw new AssertionError("UNADMITTED_BODY_ACTION");}));
        org.junit.jupiter.api.Assertions.assertEquals("ASYNC_COMPLETION_REGISTRATION_REFUSED",failure.getMessage());org.junit.jupiter.api.Assertions.assertNull(failure.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(1,closed.get());org.junit.jupiter.api.Assertions.assertTrue(timeout.get()>0&&timeout.get()<=30_000,"Registration failure cannot leave an unbounded async cycle");
    }
    @Test void destinationsExposeOnlyExactOwnedSafeFields() throws Exception {
        var ledger=new SessionLedger(Clock.systemUTC(),ignored->{});
        var lease=((SessionLedger.Accepted)ledger.admit("mock-http-session",new Owner("https://mock.invalid","owner"))).lease();
        PlanPorts.Workspace workspace=new PlanPorts.Workspace() {
            public PlanPorts.PublishedDefinition definition(Owner owner,NativeCommand.Reference reference){throw new AssertionError("UNEXPECTED_WORKSPACE_READ");}
            public PlanPorts.PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PlanPorts.PublishedDefinition definition){throw new AssertionError("UNEXPECTED_WORKSPACE_READ");}
        };
        var service=new HostedPlanService(ledger::guard,workspace,Map.of(),new PlanContentAdapter(),System::nanoTime);
        var runtime=new PlanRuntime(service,List.of(new PlanDestinations.Display("mock","postgresql","mock.invalid",5432,"mock_database")),(owner,id)->owner.equals(lease.owner()));
        var mvc=MockMvcBuilders.standaloneSetup(new PlanController(runtime,new HostedSessions(Clock.systemUTC(),List.of()))).build();
        mvc.perform(get("/api/v1/destinations").requestAttr(HostedSessions.REQUEST_LEASE,lease))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(content().json("{\"destinations\":[{\"id\":\"mock\",\"engine\":\"postgresql\",\"host\":\"mock.invalid\",\"port\":5432,\"database\":\"mock_database\"}]}"));
        mvc.perform(get("/api/v1/destinations")).andExpect(status().isUnauthorized());
    }
}
