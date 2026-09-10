package studio.environment.server.plan;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.environment.server.session.SessionCleanup;

@Configuration
class PlanLifecycleConfiguration {
    @Bean SessionCleanup planCleanup(ObjectProvider<PlanRuntime> runtime) {
        return new SessionCleanup() {
            public void invalidate(studio.environment.core.session.SessionLedger.Lease lease) { runtime.getObject().cleanup(lease); }
            public boolean awaitingWork(studio.environment.core.session.SessionLedger.Lease lease) { return runtime.getObject().awaitingCleanupWork(lease); }
        };
    }
}
