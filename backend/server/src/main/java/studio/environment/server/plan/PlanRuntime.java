package studio.environment.server.plan;

import java.util.*;
import java.util.function.BiPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import studio.environment.core.plan.*;
import studio.environment.core.session.Owner;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.observation.JdbcObservation;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.*;

/** Composition-owned services. No request constructs an adapter or selects arbitrary driver settings. */
@Component
public final class PlanRuntime {
    private final Optional<HostedPlanService> service;
    private final List<PlanDestinations.Display> destinations;
    private final BiPredicate<Owner,String> allowed;
    @Autowired
    public PlanRuntime(Environment environment,RuntimeMode mode,WorkspaceRuntime workspace,ObjectProvider<HostedSessions> sessions) {
        var configuration=new PlanDestinations(environment);
        destinations=configuration.configured().stream().map(entry->{var d=entry.destination(); return new PlanDestinations.Display(d.id(),d.engine().name().toLowerCase(Locale.ROOT),d.host(),d.port(),d.database());}).toList();
        allowed=configuration::allows;
        if(mode!=RuntimeMode.HOSTED || !workspace.enabled() || destinations.isEmpty()) {service=Optional.empty();return;}
        var ports=new LinkedHashMap<String,PlanPorts.Destination>();
        for(var entry:configuration.configured()) {var destination=entry.destination();ports.put(destination.id(),new PlanPorts.Destination(destination.id(),destination.engine(),new JdbcObservation(destination)));}
        var publications=new VersionedPlanWorkspace(new PlanWorkspaceBridge(workspace),new V3PlanWorkspaceBridge(workspace));
        service=Optional.of(new HostedPlanService(sessions.getObject()::guard,publications,ports,new PlanContentAdapter(),System::nanoTime));
    }
    /** Explicit independently invented test composition; never selected by a runtime property. */
    PlanRuntime(HostedPlanService service,List<PlanDestinations.Display> destinations,BiPredicate<Owner,String> allowed) {
        this.service=Optional.of(service); this.destinations=List.copyOf(destinations); this.allowed=allowed;
    }
    HostedPlanService service() { return service.orElseThrow(Unavailable::new); }
    /** Configuration diagnostic only; every operation still enforces its own admission. */
    public boolean inspectionApiConfigured() { return service.isPresent(); }
    List<PlanDestinations.Display> visible(Owner owner) {service();return destinations.stream().filter(d->allowed.test(owner,d.id())).toList();}
    HostedPlanService.Ack create(SessionLedger.Lease lease,PlanMetadataReader.Create command) {
        if(!allowed.test(lease.owner(),command.destinationId())) throw new DestinationDenied();
        return service().create(lease,command.requestId(),command.definition(),command.bindingId(),command.destinationId());
    }
    public void cleanup(SessionLedger.Lease lease) { service.ifPresent(value->value.invalidate(lease)); }
    public boolean awaitingCleanupWork(SessionLedger.Lease lease) { return service.map(value->value.awaitingCleanupWork(lease)).orElse(false); }
    static final class Unavailable extends RuntimeException { Unavailable(){super("PLAN_SERVICES_UNAVAILABLE",null,false,false);} }
    static final class DestinationDenied extends RuntimeException { DestinationDenied(){super("DESTINATION_DENIED",null,false,false);} }
}
