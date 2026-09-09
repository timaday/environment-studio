package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.plan.PlanContentAdapter;

/** Actual XML adapter integration; publication/observation ports are explicit mock witnesses. */
class SharedV3PlanXmlTest {
    static final String FINGERPRINT="c".repeat(64);
    final SessionLedger ledger=new SessionLedger(Clock.fixed(Instant.parse("2026-09-09T21:00:00Z"),ZoneOffset.UTC),ignored->{});
    final SessionLedger.Lease lease=((SessionLedger.Accepted)ledger.admit("mock-lease",new Owner("https://mock.invalid","operator"))).lease();
    final NativeCommand.Reference reference=new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2");
    PlanDefinition.V3 model=new PlanDefinition.V3(definition(false));
    String xml=XML;
    static SharedV3PlanXmlTest with(studio.environment.core.definitionv3.NativeCompilationResult.Checked definition,String xml){var fixture=new SharedV3PlanXmlTest();fixture.model=new PlanDefinition.V3(definition);fixture.xml=xml;return fixture;}
    static Map<String,Object> evidence(){
        var identity=Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db");
        return Map.of("engine","postgresql","cleanup","complete",
                "destination",Map.of("id","destination","host","invented.invalid","port",5432,"database","invented_db","transportIdentity","c".repeat(64),"provisioningPolicyVersion","mock-v1","observedPhysicalIdentity",identity,"expectedPhysicalIdentity",identity),
                "metadata",Map.of("adapterVersion","jdbc-observation-v3","operationPolicyVersion","postgresql-read-operation-v1","visibility","complete","readOnlyOperation","verified","snapshot","repeatable-read-read-only"));
    }
    HostedPlanService service(){
        return service(new PlanContentAdapter());
    }
    HostedPlanService service(ContentAdapter adapter){return service(adapter,System::nanoTime);}
    HostedPlanService service(ContentAdapter adapter,java.util.function.LongSupplier monotonic){
        var published=new PublishedDefinition(reference,"mock-published",model,List.of());
        var workspace=new Workspace(){
            public PublishedDefinition definition(Owner o,NativeCommand.Reference r){throw new AssertionError("VERSION_MUST_BE_EXPLICIT");}
            public PublishedDefinition definitionV3(Owner o,NativeCommand.Reference r){assertEquals(lease.owner(),o);assertEquals(reference,r);return published;}
            public PublishedProfile profile(Owner o,NativeCommand.Reference r,PublishedDefinition d){throw new AssertionError("UNEXPECTED_PROFILE");}
        };
        var observations=new ObservationPort(){
            public ObservationResult observe(Selection s,TransientCredentials c,Cancellation flag){throw new AssertionError("RESERVATION_REQUIRED");}
            public Reservation reserveV3(V3Selection selected){assertEquals(model.checked(),selected.compiled());return new Reservation.Admitted(new Permit(){
                public ObservationResult observe(TransientCredentials credentials,Cancellation control){credentials.close();return new ObservationResult.Complete(new ObservationResult.Observation(FINGERPRINT,model.logicalDigest(),model.bindingDigests().get("mock-pg"),
                        List.of(new ObservationResult.Document("sheet",new ObservationResult.Key("int64","1"),xml,xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,xml.length(),digest(xml))),evidence()));}
                public void close(){}
            });}
        };
        return new HostedPlanService(ledger::guard,workspace,Map.of("destination",new Destination("destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,observations)),adapter,monotonic);
    }
    String inspected(HostedPlanService service){
        var created=service.createV3(lease,UUID.randomUUID().toString(),reference,"mock-pg","destination");
        var reserved=service.reserve(lease,created.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        var status=service.submit(lease,reserved.operationId().orElseThrow(),(user,password)->{user[0]='a';password[0]='X';return new CredentialLengths(1,1);});
        assertEquals(HostedPlanService.Phase.SUCCEEDED,status.phase());assertEquals(Optional.of("2"),status.installedRevision());return created.planId();
    }
    HostedPlanService.ViewSnapshot snapshot(HostedPlanService service,String id,String revision){
        try(var view=service.reserveView(lease,id)){return view.run(()->{view.pin(revision);return view.snapshot();});}
    }
    @Test void actualOriginalAndTargetXmlPassThroughOneHostedLeaseWithoutLegacyAdapterFallback(){
        var service=service();String id=inspected(service);var before=snapshot(service,id,"2");
        var observed=assertInstanceOf(PlanContentEvidence.V3Observed.class,before.current().orElseThrow().evidence());
        assertEquals(List.of("alpha:x","alpha:y","beta:x"),observed.derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
        var materialized=service.materialize(lease,id,"2");assertTrue(materialized.complete(),materialized.diagnostics().toString());
        var target=snapshot(service,id,"2").target().orElseThrow();assertEquals(List.of(new Source("sheet",XML,digest(XML))),target.sources());
        var proof=assertInstanceOf(PlanContentEvidence.V3Target.class,target.evidence());assertEquals(observed.input().pin(),proof.originalPin());
        assertNotEquals(FINGERPRINT,proof.input().pin().revisionToken());assertEquals(before.current().orElseThrow().provenance(),target.provenance());
        assertEquals(3,proof.physicalExpected().entities().size());
    }
    @Test void explicitValuesRecomputeActualTargetAndUnresolvedDraftRemainsInspectable(){
        var service=service();String id=inspected(service);
        var choices=intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),new studio.environment.core.planning.TargetIntent.FieldValue.Entered("beta")));
        var changed=assertDoesNotThrow(()->service.replaceDraft(lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new Draft(choices,List.of())));
        assertEquals("3",changed.revision());var view=snapshot(service,id,"3");
        assertEquals(XML,view.current().orElseThrow().sources().getFirst().xml());
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",view.target().orElseThrow().sources().getFirst().xml());
        var proof=(PlanContentEvidence.V3Target)view.target().orElseThrow().evidence();
        assertEquals(List.of("alpha:y","beta:x"),proof.derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
        var unresolved=new Draft(intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),new studio.environment.core.planning.TargetIntent.FieldValue.Unresolved())),List.of());
        var next=service.replaceDraft(lease,id,new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),unresolved);assertEquals("4",next.revision());
        var incomplete=snapshot(service,id,"4");assertTrue(incomplete.target().isEmpty());assertEquals(unresolved,incomplete.draft());
        var materialized=service.materialize(lease,id,"4");assertEquals(HostedPlanService.Materialization.State.INCOMPLETE,materialized.state());assertEquals(List.of("by-tone"),materialized.diagnostics());
    }
    @Test void opaqueCommandRecomputesTargetAndRejectsPartialOrComputedEntityDecisions(){
        var service=service();String id=inspected(service);var original=snapshot(service,id,"2");
        var handle=(PlanCommand.Ref.Existing)original.reference(old("one"));
        var fields=edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),new studio.environment.core.planning.TargetIntent.FieldValue.Entered("beta")).fields();
        var command=new PlanCommand(new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),
                new PlanCommand.Action.Upsert(new PlanCommand.Change(new PlanCommand.Entity.Retain(handle,fields,Map.of()),List.of())));
        var changed=assertDoesNotThrow(()->service.command(lease,id,command));assertEquals("3",changed.revision());
        assertEquals(changed,service.command(lease,id,command));
        var target=snapshot(service,id,"3").target().orElseThrow();
        assertEquals(List.of("alpha:y","beta:x"),((PlanContentEvidence.V3Target)target.evidence()).derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
        var partial=new PlanCommand(new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),
                new PlanCommand.Action.Upsert(new PlanCommand.Change(new PlanCommand.Entity.Retain(handle,Map.of("tone",fields.get("tone")),Map.of()),List.of())));
        assertEquals(PlanRefusal.Code.INVALID_REQUEST,assertThrows(PlanRefusal.class,()->service.command(lease,id,partial)).code());
        var computed=new PlanCommand(new HostedPlanService.Mutation("3",UUID.randomUUID().toString()),
                new PlanCommand.Action.Upsert(new PlanCommand.Change(new PlanCommand.Entity.Create(new PlanCommand.Ref.Fresh("invented-slot","by-tone"),Map.of(),Map.of()),List.of())));
        assertEquals(PlanRefusal.Code.INVALID_REQUEST,assertThrows(PlanRefusal.class,()->service.command(lease,id,computed)).code());
        assertEquals(target,snapshot(service,id,"3").target().orElseThrow());
    }
    @Test void physicalEntityPagesKeepOpaqueHandlesAcrossAnIdentityEdit(){
        var service=service();String id=inspected(service);var original=snapshot(service,id,"2");
        String handle=((PlanCommand.Ref.Existing)original.reference(old("one"))).handle();
        var page=assertDoesNotThrow(()->service.entities(lease,id,"2",false,0,100));assertEquals(3,page.total());
        var one=page.entities().stream().filter(e->e.handle().equals(handle)).findFirst().orElseThrow();
        assertEquals("item",one.type());assertTrue(one.fields().stream().allMatch(f->f.present()&&!f.masked()));
        assertEquals(Map.of("id","one","tone","alpha","finish","x"),one.fields().stream().collect(java.util.stream.Collectors.toMap(HostedPlanService.FieldView::field,f->f.value().orElseThrow())));
        service.replaceDraft(lease,id,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),
                new Draft(intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.Entered("renamed"),new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved())),List.of()));
        var changed=service.entities(lease,id,"3",true,0,100);assertEquals(3,changed.total());
        assertEquals(Optional.of("renamed"),changed.entities().stream().filter(e->e.handle().equals(handle)).findFirst().orElseThrow().fields().stream().filter(f->f.field().equals("id")).findFirst().orElseThrow().value());
        assertTrue(service.entities(lease,id,"3",true,3,100).entities().isEmpty());
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->service.entities(lease,id,"2",true,0,100)).code());
    }
    @Test void explicitRemovalAndFreshReplacementDoNotReuseOriginalContributorIdentity(){
        var service=service();String id=inspected(service);var original=snapshot(service,id,"2");
        var one=(PlanCommand.Ref.Existing)original.reference(old("one"));
        var fresh=new PlanCommand.Ref.Fresh("new-one","item");
        java.util.function.Function<String,studio.environment.core.planning.TargetIntent.FieldValue> enter=studio.environment.core.planning.TargetIntent.FieldValue.Entered::new;
        var source=original.current().orElseThrow().sources().getFirst();
        var parent=new PlanCommand.Parent.Existing("sheet",source.digest(),"0");
        var create=new PlanCommand.Change(new PlanCommand.Entity.Create(fresh,Map.of("id",enter.apply("one"),"tone",enter.apply("gamma"),"finish",enter.apply("z")),Map.of()),
                List.of(new PlanCommand.Placement(fresh,"sheet","items",parent)));
        var removal=new PlanCommand.Change(new PlanCommand.Entity.Remove(one),List.of());
        var command=new PlanCommand(new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),new PlanCommand.Action.Batch(List.of(removal,create),List.of()));
        var ack=service.command(lease,id,command);assertEquals("3",ack.revision());
        var target=snapshot(service,id,"3").target().orElseThrow();
        assertEquals("<items><!-- mock -->\r\n<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/><item xmlns=\"\" finish=\"z\" id=\"one\" tone=\"gamma\"/></items>",target.sources().getFirst().xml());
        assertEquals(new studio.environment.core.planning.TargetIntent.Ref.Fresh("new-one","item"),target.provenance().get(old("one").key()));
        assertEquals(List.of("alpha:y","beta:x","gamma:z"),((PlanContentEvidence.V3Target)target.evidence()).derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
    }
}
