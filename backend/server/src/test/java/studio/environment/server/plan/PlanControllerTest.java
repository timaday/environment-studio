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
    @Test void destinationsExposeOnlyExactOwnedSafeFields() throws Exception {
        var ledger=new SessionLedger(Clock.systemUTC(),ignored->{});
        var lease=((SessionLedger.Accepted)ledger.admit("mock-http-session",new Owner("https://mock.invalid","owner"))).lease();
        PlanPorts.Workspace workspace=new PlanPorts.Workspace() {
            public PlanPorts.PublishedDefinition definition(Owner owner,NativeCommand.Reference reference){throw new AssertionError("UNEXPECTED_WORKSPACE_READ");}
            public PlanPorts.PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PlanPorts.PublishedDefinition definition){throw new AssertionError("UNEXPECTED_WORKSPACE_READ");}
        };
        var service=new HostedPlanService(ledger::guard,workspace,Map.of(),new PlanContentAdapter(),System::nanoTime);
        var runtime=new PlanRuntime(service,List.of(new PlanDestinations.Display("mock","postgresql","mock.invalid",5432,"mock_database")),(owner,id)->owner.equals(lease.owner()));
        var mvc=MockMvcBuilders.standaloneSetup(new PlanController(runtime)).build();
        mvc.perform(get("/api/v1/destinations").requestAttr(HostedSessions.REQUEST_LEASE,lease))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(content().json("{\"destinations\":[{\"id\":\"mock\",\"engine\":\"postgresql\",\"host\":\"mock.invalid\",\"port\":5432,\"database\":\"mock_database\"}]}"));
        mvc.perform(get("/api/v1/destinations")).andExpect(status().isUnauthorized());
    }
}
