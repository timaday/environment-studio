package studio.environment.server.plan;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.environment.server.session.SessionCleanup;

@Configuration
class PlanLifecycleConfiguration {
    @Bean SessionCleanup planCleanup(ObjectProvider<PlanRuntime> runtime) { return lease->runtime.getObject().cleanup(lease); }
}
