package studio.environment.server.plan;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import studio.environment.core.plan.*;
import studio.environment.core.observation.*;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.*;

/** Independently invented adapter composition for HTTP tests; genuine OIDC remains the security boundary. */
@TestConfiguration
public class PlanHttpTestConfiguration {
    public static final TestClock clock=new TestClock();
    public static final class TestClock extends java.time.Clock {
        private volatile long offsetSeconds;
        public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
        public java.time.Clock withZone(java.time.ZoneId zone){return this;}
        public java.time.Instant instant(){return java.time.Instant.now().plusSeconds(offsetSeconds);}
        public void advance(long seconds){offsetSeconds+=seconds;}
        public void reset(){offsetSeconds=0;}
    }
    @Bean @Primary java.time.Clock mockPlanClock(){return clock;}
    public static volatile HostedPlanService installed;
    public static final java.util.concurrent.ConcurrentHashMap<String,studio.environment.core.session.SessionLedger.Lease> leases=new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.concurrent.atomic.AtomicBoolean exactCredentials=new java.util.concurrent.atomic.AtomicBoolean();
    public static final AtomicInteger connections=new AtomicInteger();
    /** Synchronization only; observable capacity/deadline assertions remain actual HTTP requests. */
    public static void awaitViewScratch(boolean reading)throws Exception {
        var lockField=HostedPlanService.class.getDeclaredField("lock");lockField.setAccessible(true);var scratchField=HostedPlanService.class.getDeclaredField("viewScratch");scratchField.setAccessible(true);
        var pinnedField=HostedPlanService.ViewAdmission.class.getDeclaredField("pinned");pinnedField.setAccessible(true);var executingField=HostedPlanService.ViewAdmission.class.getDeclaredField("executing");executingField.setAccessible(true);
        long end=System.nanoTime()+2_000_000_000L;
        while(System.nanoTime()<end){synchronized(lockField.get(installed)){Object admission=scratchField.get(installed);if(!reading && admission==null || reading && admission!=null && pinnedField.get(admission)==null && executingField.getBoolean(admission))return;}Thread.sleep(5);}
        throw new AssertionError("MOCK_VIEW_READER_STATE_TIMEOUT");
    }
    public static void awaitCommandScratch(boolean running)throws Exception {
        var lockField=HostedPlanService.class.getDeclaredField("lock");lockField.setAccessible(true);var scratchField=HostedPlanService.class.getDeclaredField("commandScratch");scratchField.setAccessible(true);var materializationField=HostedPlanService.class.getDeclaredField("materializationScratch");materializationField.setAccessible(true);
        var executingField=HostedPlanService.CommandAdmission.class.getDeclaredField("executing");executingField.setAccessible(true);
        long end=System.nanoTime()+2_000_000_000L;
        while(System.nanoTime()<end){synchronized(lockField.get(installed)){Object admission=scratchField.get(installed);boolean materializing=materializationField.getBoolean(installed);if(!running && admission==null && !materializing)return;if(running && admission!=null && executingField.getBoolean(admission))return;}Thread.sleep(5);}
        throw new AssertionError(running?"MOCK_COMMAND_READER_STATE_TIMEOUT":"MOCK_COMMAND_SETTLEMENT_TIMEOUT");
    }
    public static Map<String,Object> mockObservationEvidence() {
        var identity=Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db");
        return Map.of("engine","postgresql","cleanup","complete",
                "destination",Map.of("id","mock-destination","host","invented.invalid","port",5432,"database","invented_db","transportIdentity","c".repeat(64),"provisioningPolicyVersion","mock-v1","observedPhysicalIdentity",identity,"expectedPhysicalIdentity",identity),
                "metadata",Map.of("adapterVersion","jdbc-observation-v2","operationPolicyVersion","postgresql-read-operation-v1","visibility","complete","readOnlyOperation","verified","snapshot","repeatable-read-read-only"));
    }
    @Bean @Primary PlanRuntime mockPlanRuntime(HostedSessions sessions,WorkspaceRuntime workspace) {
        ObservationPort port=new ObservationPort() {
            public ObservationResult observe(Selection selection,TransientCredentials credentials,Cancellation cancellation) {throw new AssertionError("RESERVATION_REQUIRED");}
            public Reservation reserve(Selection selection) {
                return new Reservation.Admitted(new Permit() {
                    public ObservationResult observe(TransientCredentials credentials,Cancellation cancellation) {
                        connections.incrementAndGet();
                        char[] user=credentials.copyUser(),password=credentials.copyPassword();
                        try {exactCredentials.set(Arrays.equals(user,"MockReader".toCharArray()) && Arrays.equals(password,"Db-Password-Canary-𐀀".toCharArray()));}
                        finally {Arrays.fill(user,'\0');Arrays.fill(password,'\0');credentials.close();}
                        try {
                            var definition=new PlanPorts.PublishedDefinition(new studio.environment.core.workspace.NativeCommand.Reference("00000000-0000-4000-8000-000000000099","2"),"mock",selection.compiled(),List.of());
                            var observed=new PlanContentAdapterTest().observation(definition);
                            return new ObservationResult.Complete(new ObservationResult.Observation("a".repeat(64),observed.logicalDigest(),observed.bindingDigest(),observed.documents(),mockObservationEvidence()));
                        } catch(Exception failure) {throw new AssertionError("MOCK_OBSERVATION_UNAVAILABLE");}
                    }
                    public void close() { }
                });
            }
        };
        var service=new HostedPlanService(new PlanPorts.Authority() {
            public <T> Optional<T> guard(studio.environment.core.session.SessionLedger.Lease lease,java.util.function.Supplier<T> transition) {
                return sessions.guard(lease,()->{leases.put(lease.owner().subject(),lease);return transition.get();});
            }
        },new PlanWorkspaceBridge(workspace),Map.of("mock-destination",new PlanPorts.Destination("mock-destination",Engine.POSTGRESQL,port)),new PlanContentAdapter(),System::nanoTime);
        installed=service;
        return new PlanRuntime(service,List.of(new PlanDestinations.Display("mock-destination","postgresql","invented.invalid",5432,"mock_database")),(owner,id)->id.equals("mock-destination"));
    }
}
