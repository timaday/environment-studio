package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanPorts.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.*;
import studio.environment.core.profile.ProfileCapture;

/** Independent mock adapter evidence; no real destination or database model. */
class PlanObservedDestinationTest {
    static Map<String,Object> evidence() {
        var identity=Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db");
        return Map.of("engine","postgresql","cleanup","complete",
                "destination",Map.of("id","destination","host","invented.invalid","port",5432,"database","invented_db","transportIdentity","c".repeat(64),"provisioningPolicyVersion","mock-v1","observedPhysicalIdentity",identity,"expectedPhysicalIdentity",identity),
                "metadata",Map.of("adapterVersion","jdbc-observation-v2","operationPolicyVersion","postgresql-read-operation-v1",
                        "visibility","complete","readOnlyOperation","verified","snapshot","repeatable-read-read-only"));
    }
    static final class Harness {
        final HostedPlanServiceTest base=new HostedPlanServiceTest();
        final AtomicInteger projections=new AtomicInteger();
        Map<String,Object> evidence=evidence();
        String fingerprint="b".repeat(64);
        boolean rejectProjection;
        final HostedPlanService service;
        final String plan;
        Harness() {
            ObservationPort port=new ObservationPort() {
                public ObservationResult observe(Selection selection,TransientCredentials credentials,Cancellation cancellation) { throw new AssertionError("RESERVATION_REQUIRED"); }
                public Reservation reserve(Selection selection) {
                    return new Reservation.Admitted(new Permit() {
                        public ObservationResult observe(TransientCredentials credentials,Cancellation cancellation) {
                            credentials.close();
                            return new ObservationResult.Complete(new ObservationResult.Observation(fingerprint,"logical","binding-digest",List.of(),evidence));
                        }
                        public void close() { }
                    });
                }
            };
            ContentAdapter adapter=new ContentAdapter() {
                public ContentResult project(PublishedDefinition definition,String binding,ObservationResult.Observation observed) {
                    projections.incrementAndGet();return rejectProjection?new ContentResult.Rejected(List.of("PROJECTION_REFUSED")):new ContentResult.Complete(new Content(List.of(),new ObservedGraph(List.of(),List.of()),Map.of()));
                }
                public ContentResult materialize(PublishedDefinition definition,String binding,Content current,Draft draft) { throw new AssertionError("UNEXPECTED_MATERIALIZE"); }
                public Capture capture(PublishedDefinition definition,String binding,Content current,ProfileCapture.Command command) { throw new AssertionError("UNEXPECTED_CAPTURE"); }
            };
            service=new HostedPlanService(base.authority::guard,base.workspace,Map.of("destination",new Destination("destination",Engine.POSTGRESQL,port)),adapter,System::nanoTime);
            plan=service.create(base.lease,UUID.randomUUID().toString(),base.ref,"invented-binding","destination").planId();
        }
        HostedPlanService.Status inspect() {
            var reserved=service.reserve(base.lease,plan,new HostedPlanService.Mutation(service.summary(base.lease,plan).revision(),UUID.randomUUID().toString()));
            return service.submit(base.lease,reserved.operationId().orElseThrow(),PlanLifecycleTest::credentials);
        }
    }
    @Test void completeAdapterResultWithoutIdentityCannotInstallOrReachProjection() {
        var h=new Harness();h.evidence=Map.of();
        assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());
        assertEquals(0,h.projections.get());assertFalse(h.service.summary(h.base.lease,h.plan).inspectionValid());
    }
    @Test void retiredAccountPurityPolicyCannotInstallAsCurrentReadOperationEvidence() {
        var h=new Harness();var frame=new HashMap<>(evidence());
        frame.put("metadata",Map.of("adapterVersion","jdbc-observation-v1","accountPolicyVersion","postgresql-readonly-v1","leastPrivilege","verified"));h.evidence=frame;
        assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());assertEquals(0,h.projections.get());
    }
    @Test void expectedIdentityAloneCannotSubstituteForObservation() {
        var h=new Harness();var frame=new HashMap<>(evidence());frame.put("destination",Map.of("id","destination","expectedPhysicalIdentity",Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db")));h.evidence=frame;
        assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());assertEquals(0,h.projections.get());
    }
    @Test void malformedFingerprintCannotBecomeObservedContext() {
        var h=new Harness();h.fingerprint="not-an-observation-digest";
        assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());assertEquals(0,h.projections.get());
    }
    static Map<String,Object> change(Map<String,Object> original,String section,String key,Object value) {
        var frame=new HashMap<>(original);var child=new HashMap<String,Object>();
        ((Map<?,?>)original.get(section)).forEach((k,v)->child.put((String)k,v));
        if(value==null)child.remove(key);else child.put(key,value);frame.put(section,child);return frame;
    }
    @Test void identityAppearsOnlyAfterCompleteProjectionAndIsRedactedAndImmutable() {
        var h=new Harness();assertTrue(h.service.view(h.base.lease,Optional.of(h.plan)).observedDestination().isEmpty());
        h.rejectProjection=true;assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());
        assertTrue(h.service.view(h.base.lease,Optional.of(h.plan)).observedDestination().isEmpty());
        h.rejectProjection=false;assertEquals(HostedPlanService.Phase.SUCCEEDED,h.inspect().phase());
        var observed=h.service.view(h.base.lease,Optional.empty()).observedDestination().orElseThrow();
        assertEquals("postgresql",observed.engine());assertEquals(Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db"),observed.identity());
        assertEquals("b".repeat(64),observed.observationFingerprint());assertTrue(observed.evidenceValid());
        assertEquals("ObservedDestination[redacted]",observed.toString());
        assertThrows(UnsupportedOperationException.class,()->observed.identity().put("databaseName","edited"));
    }
    @Test void failedReinspectionRetainsOnlyStaleIdentityUntilAtomicReplacement() {
        var h=new Harness();assertEquals(HostedPlanService.Phase.SUCCEEDED,h.inspect().phase());
        h.evidence=Map.of();assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());
        var stale=h.service.view(h.base.lease,Optional.of(h.plan)).observedDestination().orElseThrow();
        assertFalse(stale.evidenceValid());assertEquals("invented_db",stale.identity().get("databaseName"));assertEquals("b".repeat(64),stale.observationFingerprint());
        var replacement=Map.of("systemIdentifier","732","databaseOid","20","databaseName","invented_other");
        h.evidence=change(change(evidence(),"destination","observedPhysicalIdentity",replacement),"destination","expectedPhysicalIdentity",replacement);h.fingerprint="d".repeat(64);
        assertEquals(HostedPlanService.Phase.SUCCEEDED,h.inspect().phase());
        var fresh=h.service.view(h.base.lease,Optional.of(h.plan)).observedDestination().orElseThrow();
        assertTrue(fresh.evidenceValid());assertEquals(replacement,fresh.identity());assertEquals("d".repeat(64),fresh.observationFingerprint());assertFalse(stale.evidenceValid());
    }
    @Test void wrongOrMissingPolicyFactsRefuseBeforeProjection() {
        var fields=Map.of("adapterVersion","jdbc-observation-v2","operationPolicyVersion","postgresql-read-operation-v1","visibility","complete","readOnlyOperation","verified","snapshot","repeatable-read-read-only");
        for(String key:fields.keySet())for(Object invalid:Arrays.asList(null,"incorrect",true)) {
            var h=new Harness();h.evidence=change(evidence(),"metadata",key,invalid);
            assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase(),key);assertEquals(0,h.projections.get());
        }
        for(String key:List.of("engine","cleanup")) {
            var h=new Harness();var frame=new HashMap<>(evidence());frame.put(key,"incorrect");h.evidence=frame;
            assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());assertEquals(0,h.projections.get());
        }
        var h=new Harness();h.evidence=change(evidence(),"destination","id","other-destination");assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());
    }
    @Test void closedPhysicalIdentityRejectsAmbiguityNonScalarAndNonCanonicalValues() {
        var original=Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db");
        for(String key:original.keySet())for(Object invalid:Arrays.asList(null,17,"", "x\n", "\ud800", "0", "01", "1".repeat(129))) {
            if(key.equals("databaseName") && (Objects.equals(invalid,"0") || Objects.equals(invalid,"01")))continue;
            var identity=new HashMap<String,Object>(original);if(invalid==null)identity.remove(key);else identity.put(key,invalid);
            var h=new Harness();h.evidence=change(change(evidence(),"destination","observedPhysicalIdentity",identity),"destination","expectedPhysicalIdentity",identity);
            assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase(),key);assertEquals(0,h.projections.get());
        }
        var extra=new HashMap<String,Object>(original);extra.put("password","mock-private-field");
        var h=new Harness();h.evidence=change(evidence(),"destination","observedPhysicalIdentity",extra);assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());
        h=new Harness();h.evidence=change(evidence(),"destination","expectedPhysicalIdentity",Map.of("systemIdentifier","999","databaseOid","19","databaseName","invented_db"));assertEquals(HostedPlanService.Phase.REFUSED,h.inspect().phase());
    }
    @Test void oracleUsesItsClosedIdentityAndReadOnlySnapshotPolicy() {
        var identity=Map.of("dbid","711","dbUniqueName","invented_cdb","conId","3","conUid","812","conName","invented_pdb","pdbGuid","e".repeat(32));
        var frame=new HashMap<>(change(change(evidence(),"destination","observedPhysicalIdentity",identity),"destination","expectedPhysicalIdentity",identity));
        frame.put("engine","oracle");frame.put("metadata",Map.of("adapterVersion","jdbc-observation-v2","operationPolicyVersion","oracle-read-operation-v1","visibility","complete","readOnlyOperation","verified","snapshot","read-only"));
        var destination=new Destination("destination",Engine.ORACLE,(selection,credentials,cancel)->{throw new AssertionError("UNEXPECTED_CONNECT");});
        var observation=new ObservationResult.Observation("f".repeat(64),"logical","binding-digest",List.of(),frame);
        var observed=PlanObservedDestination.from(observation,destination);assertEquals(identity,observed.identity());assertEquals("oracle",observed.engine());
        for(Object invalid:List.of("E".repeat(32),"a".repeat(31),"a".repeat(33),"unknown")) {
            var broken=new HashMap<String,Object>(identity);broken.put("pdbGuid",invalid);
            var evidence=change(frame,"destination","observedPhysicalIdentity",broken);
            assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,()->PlanObservedDestination.from(new ObservationResult.Observation("f".repeat(64),"logical","binding-digest",List.of(),evidence),destination)).code());
        }
    }
    @Test void retainedContextCannotBeViewedByAnotherLeaseOrAfterRevocation() {
        var h=new Harness();h.inspect();
        var other=((studio.environment.core.session.SessionLedger.Accepted)h.base.authority.admit("other-lease",new studio.environment.core.session.Owner(h.base.lease.owner().issuer(),"other-owner"))).lease();
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->h.service.view(other,Optional.of(h.plan))).code());
        h.base.authority.close(h.base.lease.id());
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->h.service.view(h.base.lease,Optional.of(h.plan))).code());
    }
    @Test void summaryPublicationRejectsChangedValidityEvenWhenRevisionIsUnchanged() {
        var h=new Harness();h.inspect();var snapshot=h.service.view(h.base.lease,Optional.of(h.plan));
        h.service.verifySummary(h.base.lease,snapshot);
        h.evidence=Map.of();h.inspect();assertEquals(snapshot.revision(),h.service.summary(h.base.lease,h.plan).revision());
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->h.service.verifySummary(h.base.lease,snapshot)).code());
        h.base.authority.close(h.base.lease.id());
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->h.service.verifySummary(h.base.lease,snapshot)).code());
    }
}
