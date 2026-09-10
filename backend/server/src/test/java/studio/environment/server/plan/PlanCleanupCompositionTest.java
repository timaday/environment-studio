package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.session.SessionCleanup;
import studio.environment.server.workspace.WorkspaceRuntime;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;

class PlanCleanupCompositionTest {
    @Test void actualSpringPlanHookForwardsReadinessAndDisabledRuntimeHasNoWork() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var plan=f.create(false);
        var runtime=new PlanRuntime(f.service,List.of(),(owner,id)->true);
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(PlanRuntime.class,()->runtime);context.register(PlanLifecycleConfiguration.class);context.refresh();
            var hook=context.getBean(SessionCleanup.class);assertFalse(hook.awaitingWork(f.lease));
            var read=f.service.reserveView(f.lease,plan.planId());
            try{assertTrue(hook.awaitingWork(f.lease));assertThrows(RuntimeException.class,()->hook.invalidate(f.lease));}
            finally{read.close();}
            assertFalse(hook.awaitingWork(f.lease));hook.invalidate(f.lease);assertFalse(hook.awaitingWork(f.lease));
        }
        var environment=new MockEnvironment();var workspace=new WorkspaceRuntime(environment,RuntimeMode.DEMO);
        try(var context=new AnnotationConfigApplicationContext()) {
            var disabled=new PlanRuntime(environment,RuntimeMode.DEMO,workspace,new org.springframework.beans.factory.support.DefaultListableBeanFactory().getBeanProvider(HostedSessions.class));
            context.registerBean(PlanRuntime.class,()->disabled);context.register(PlanLifecycleConfiguration.class);context.refresh();
            var hook=context.getBean(SessionCleanup.class);assertFalse(hook.awaitingWork(f.lease));hook.invalidate(f.lease);
        }
    }
}
