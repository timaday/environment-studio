package studio.environment.server.plan;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.BiPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import studio.environment.core.plan.*;
import studio.environment.core.session.Owner;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.observation.JdbcObservation;
import studio.environment.server.export.V3GuardedPackageCandidate;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.*;

/** Composition-owned services. No request constructs an adapter or selects arbitrary driver settings. */
@Component
public final class PlanRuntime {
    private final Optional<HostedPlanService> service;
    private final V3PlanTransfers transfers=new V3PlanTransfers();
    V3PlanTransfers transfers(){return transfers;}
    private final Map<String,PlanDestinations.Display> destinations=new LinkedHashMap<>();
    private final Map<String,V3GuardedPackageCandidate.Target> packageTargets=new LinkedHashMap<>();
    private final Map<String,Set<Owner>> destinationOwners=new LinkedHashMap<>();
    private final BiPredicate<Owner,String> allowed;
    @Autowired
    public PlanRuntime(Environment environment,RuntimeMode mode,WorkspaceRuntime workspace,ObjectProvider<HostedSessions> sessions) {
        var configuration=new PlanDestinations(environment);
        var ports=new LinkedHashMap<String,PlanPorts.Destination>();
        for(var entry:configuration.configured()) if(entry.exportClient()!=null) {
            var d=entry.destination(); var c=entry.exportClient();
            packageTargets.put(d.id(),new V3GuardedPackageCandidate.Target(d.id(),d.engine().name().toLowerCase(Locale.ROOT),d.host(),d.port(),d.database(),
                    d.transport().name().toLowerCase(Locale.ROOT).replace('_','-'),d.transportIdentity(),d.provisioningPolicyVersion(),d.expectedPhysicalIdentity(),
                    c.serverVersion(),c.family(),c.version(),c.platform(),c.templateVersion()));
        }
        for(var entry:configuration.configured()) {
            var destination=entry.destination();
            destinations.put(destination.id(),new PlanDestinations.Display(destination.id(),destination.engine().name().toLowerCase(Locale.ROOT),destination.host(),destination.port(),destination.database()));
            destinationOwners.put(destination.id(),entry.owners());
            ports.put(destination.id(),new PlanPorts.Destination(destination.id(),destination.engine(),new JdbcObservation(destination)));
        }
        allowed=this::allows;
        if(mode!=RuntimeMode.HOSTED || !workspace.enabled()) {service=Optional.empty();return;}
        var publications=new VersionedPlanWorkspace(new PlanWorkspaceBridge(workspace),new V3PlanWorkspaceBridge(workspace));
        service=Optional.of(new HostedPlanService(sessions.getObject()::guard,publications,ports,new PlanContentAdapter(),System::nanoTime));
    }
    /** Explicit independently invented test composition; never selected by a runtime property. */
    PlanRuntime(HostedPlanService service,List<PlanDestinations.Display> destinations,BiPredicate<Owner,String> allowed) {
        this(service,destinations,Map.of(),allowed);
    }
    PlanRuntime(HostedPlanService service,List<PlanDestinations.Display> destinations,Map<String,V3GuardedPackageCandidate.Target> packageTargets,BiPredicate<Owner,String> allowed) {
        this.service=Optional.of(service); destinations.forEach(destination->this.destinations.put(destination.id(),destination));
        this.packageTargets.putAll(packageTargets); this.allowed=allowed;
    }
    HostedPlanService service() { return service.orElseThrow(Unavailable::new); }
    V3GuardedPackageCandidate.Target packageTarget(Owner owner,String id,studio.environment.core.plan.PlanObservedDestination observed) {
        if(!allowed.test(owner,id)) throw new DestinationDenied();
        var target=packageTargets.get(id);
        if(target==null && observed!=null) {
            PlanDestinations.Display display;
            synchronized(destinations) { display=destinations.get(id); }
            if(display!=null && "postgresql".equals(display.engine()) && "postgresql".equals(observed.engine()))
                target=new V3GuardedPackageCandidate.Target(display.id(),display.engine(),display.host(),display.port(),display.database(),
                        "operator-supplied-plaintext",observed.observationFingerprint(),"postgresql16-operator-supplied-v1",observed.identity(),
                        "16.11","psql","16.11","linux-amd64","postgresql16-text-v1");
        }
        if(target==null) throw new PlanRefusal(PlanRefusal.Code.EXPORT_UNAVAILABLE);
        return target;
    }
    /** Configuration diagnostic only; every operation still enforces its own admission. */
    public boolean inspectionApiConfigured() { return service.isPresent(); }
    public boolean postgresql16PilotConfigured() { return service.isPresent(); }
    public boolean exportConfigured() { return postgresql16PilotConfigured(); }
    public List<String> qualifiedDatabaseAdapters() {
        if(service.isEmpty()) return List.of();
        var configured=packageTargets.values().stream()
                .filter(target -> target.engine().equals("postgresql") && target.serverVersion().equals("16.11")
                        && target.clientFamily().equals("psql") && target.clientVersion().equals("16.11")
                        && target.clientPlatform().equals("linux-amd64") && target.templateVersion().equals("postgresql16-text-v1"))
                .map(target -> "postgresql:16.11/psql:16.11/postgresql16-text-v1");
        return java.util.stream.Stream.concat(configured,java.util.stream.Stream.of("postgresql:16.11/psql:16.11/postgresql16-text-v1")).distinct().sorted().toList();
    }
    List<PlanDestinations.Display> visible(Owner owner) {service();synchronized(destinations){return destinations.values().stream().filter(d->allowed.test(owner,d.id())).toList();}}
    PlanDestinations.Display register(SessionLedger.Lease lease,PlanMetadataReader.Destination command) {
        var parsed=jdbc(command.jdbcUrl());
        String id="pg-"+digest(lease.owner().issuer()+"\n"+lease.owner().subject()+"\n"+command.jdbcUrl()).substring(0,20);
        var destination=new studio.environment.server.observation.ObservationDestination(id,studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,
                parsed.host(),parsed.port(),parsed.database(),studio.environment.server.observation.ObservationDestination.Transport.OPERATOR_SUPPLIED_PLAINTEXT,
                "",digest(command.jdbcUrl()),Map.of(),"postgresql16-operator-supplied-v1","postgresql-read-operation-v1");
        var display=new PlanDestinations.Display(id,"postgresql",parsed.host(),parsed.port(),parsed.database());
        synchronized(destinations) {
            var existing=destinations.get(id);
            if(existing!=null) {
                if(!allowed.test(lease.owner(),id)) throw new DestinationDenied();
                return existing;
            }
            if(destinations.size()>=32) throw new PlanRefusal(PlanRefusal.Code.CAPACITY);
            service().registerDestination(new PlanPorts.Destination(id,studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,new JdbcObservation(destination)));
            destinations.put(id,display); destinationOwners.put(id,Set.of(lease.owner()));
            return display;
        }
    }
    HostedPlanService.Ack create(SessionLedger.Lease lease,PlanMetadataReader.Create command) {
        if(!allowed.test(lease.owner(),command.destinationId())) throw new DestinationDenied();
        return service().create(lease,command.requestId(),command.definition(),command.bindingId(),command.destinationId());
    }
    HostedPlanService.Ack createV3(SessionLedger.Lease lease,PlanMetadataReader.Create command) {
        if(!allowed.test(lease.owner(),command.destinationId())) throw new DestinationDenied();
        return service().createV3(lease,command.requestId(),command.definition(),command.bindingId(),command.destinationId());
    }
    public void cleanup(SessionLedger.Lease lease) {
        boolean incomplete=false;
        try {service.ifPresent(value->value.invalidate(lease));}catch(RuntimeException refusal){incomplete=true;}
        try {transfers.invalidate(lease);}catch(RuntimeException refusal){incomplete=true;}
        if(incomplete)throw new PlanRefusal(PlanRefusal.Code.CLEANUP_INCONCLUSIVE);
    }
    public boolean awaitingCleanupWork(SessionLedger.Lease lease) { return service.map(value->value.awaitingCleanupWork(lease)).orElse(false) || transfers.awaitingWork(lease); }
    private boolean allows(Owner owner,String id) {
        var owners=destinationOwners.get(id);
        return owners!=null && owners.contains(owner);
    }
    private record JdbcParts(String host,int port,String database) { }
    private static JdbcParts jdbc(String value) {
        var uri=java.net.URI.create(value.substring(5));
        return new JdbcParts(uri.getHost(),uri.getPort(),uri.getPath().substring(1));
    }
    private static String digest(String value) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA_256_UNAVAILABLE"); }
    }
    static final class Unavailable extends RuntimeException { Unavailable(){super("PLAN_SERVICES_UNAVAILABLE",null,false,false);} }
    static final class DestinationDenied extends RuntimeException { DestinationDenied(){super("DESTINATION_DENIED",null,false,false);} }
}
