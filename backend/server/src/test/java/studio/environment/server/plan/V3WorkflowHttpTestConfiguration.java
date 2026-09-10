package studio.environment.server.plan;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.planning.V3WorkflowHttpWitnesses;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.*;

/** Explicit @Import only. No runtime property can create these mock publication/observation witnesses. */
@TestConfiguration
public class V3WorkflowHttpTestConfiguration {
    public static final String OBJECT = "00000000-0000-4000-8000-000000000919";
    public static final AtomicInteger observations = new AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicBoolean exactCredentials = new java.util.concurrent.atomic.AtomicBoolean();
    public static final Map<String, SessionLedger.Lease> leases = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<Owner,Boolean> largeOwners=new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<Owner,PlanPorts.PublishedProfile> profiles=new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile HostedPlanService service;
    public static volatile PlanRuntime runtime;

    /** Test synchronization only; externally observed HTTP capacity is asserted separately. */
    public static void awaitRecords(int count) throws Exception {
        var registry = runtime.transfers();
        var field = V3PlanTransfers.class.getDeclaredField("active"); field.setAccessible(true);
        long deadline = System.nanoTime()+3_000_000_000L;
        while (System.nanoTime()<deadline) {
            synchronized (registry) { if (((Set<?>)field.get(registry)).size()==count) return; }
            Thread.sleep(5);
        }
        throw new AssertionError("MOCK_TRANSFER_COUNT_TIMEOUT");
    }

    @Bean @Primary PlanRuntime mockV3WorkflowHttpRuntime(HostedSessions sessions, WorkspaceRuntime storage) {
        var real = new VersionedPlanWorkspace(new PlanWorkspaceBridge(storage), new V3PlanWorkspaceBridge(storage));
        var witness = new PlanPorts.PublishedDefinition(new NativeCommand.Reference(OBJECT, "2"),
                "explicit-http-mock-publication", new PlanDefinition.V3(V3WorkflowHttpWitnesses.definition()), List.of());
        var workspace = new PlanPorts.Workspace() {
            public PlanPorts.PublishedDefinition definition(Owner owner, NativeCommand.Reference reference) {
                return real.definition(owner, reference);
            }
            public PlanPorts.PublishedDefinition definitionV3(Owner owner, NativeCommand.Reference reference) {
                if(reference.equals(witness.reference()) && largeOwners.containsKey(owner))return new PlanPorts.PublishedDefinition(reference,"explicit-large-mock-publication",new PlanDefinition.V3(studio.environment.server.planning.V3WorkflowLargeHttpWitnesses.definition(largeOwners.get(owner))),List.of());
                return reference.equals(witness.reference()) ? witness : real.definitionV3(owner, reference);
            }
            public PlanPorts.PublishedProfile profileV3(Owner owner, NativeCommand.Reference reference, PlanPorts.PublishedDefinition definition) {
                var found=profiles.get(owner);if(found!=null && found.reference().equals(reference))return found;
                return real.profileV3(owner,reference,definition);
            }
            public PlanPorts.PublishedProfile profile(Owner owner, NativeCommand.Reference reference, PlanPorts.PublishedDefinition definition) {
                return real.profile(owner, reference, definition);
            }
        };
        var port = new ObservationPort() {
            public ObservationResult observe(Selection selected, TransientCredentials credentials, Cancellation cancellation) {
                throw new AssertionError("MOCK_RESERVATION_REQUIRED");
            }
            public Reservation reserveV3(V3Selection selected) {
                boolean large=selected.compiled().definition().id().equals("mock-tail") || selected.compiled().definition().id().equals("mock-wide");
                boolean wide=selected.compiled().definition().id().equals("mock-wide");
                if (!selected.compiled().equals(large?studio.environment.server.planning.V3WorkflowLargeHttpWitnesses.definition(wide):V3WorkflowHttpWitnesses.definition())) throw new AssertionError("MOCK_FOREIGN_DEFINITION");
                return new Reservation.Admitted(new Permit() {
                    public ObservationResult observe(TransientCredentials credentials, Cancellation cancelled) {
                        observations.incrementAndGet();
                        char[] user = credentials.copyUser(), password = credentials.copyPassword();
                        try {
                            exactCredentials.set(Arrays.equals(user, "MockV3Reader".toCharArray())
                                    && Arrays.equals(password, "MockV3-Password-𐀀".toCharArray()));
                        } finally { Arrays.fill(user, '\0'); Arrays.fill(password, '\0'); credentials.close(); }
                        return large?studio.environment.server.planning.V3WorkflowLargeHttpWitnesses.observation(wide):V3WorkflowHttpWitnesses.observation();
                    }
                    public void close() {}
                });
            }
        };
        service = new HostedPlanService(new PlanPorts.Authority() {
            public <T> Optional<T> guard(SessionLedger.Lease lease, java.util.function.Supplier<T> transition) {
                return sessions.guard(lease, () -> { leases.put(lease.owner().subject(), lease); return transition.get(); });
            }
        }, workspace, Map.of("mock-destination", new PlanPorts.Destination("mock-destination", Engine.POSTGRESQL, port)),
                new PlanContentAdapter(), System::nanoTime);
        runtime = new PlanRuntime(service, List.of(new PlanDestinations.Display("mock-destination", "postgresql", "invented.invalid", 5432, "invented_db")),
                (owner, id) -> id.equals("mock-destination"));
        return runtime;
    }
}
