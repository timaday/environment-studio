package studio.environment.core.plan;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import static studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.definitionv2.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.NativeCommand;

/** Independently invented declared graph; mock ports isolate revision/handle authority. */
class PlanHandleIdentityTest {
    static final TargetIntent.Ref.Fresh FRESH=new TargetIntent.Ref.Fresh("new-slot","glyph");
    static final PlanCommand.Ref.Fresh WIRE=new PlanCommand.Ref.Fresh("new-slot","glyph");
    static final class Harness {
        final HostedPlanServiceTest base=new HostedPlanServiceTest();
        final AtomicBoolean incomplete=new AtomicBoolean();
        final PublishedDefinition definition;
        final Content observed;
        final HostedPlanService service;
        final String plan;
        Harness() {
            var field=new NativeDefinition.Field("tag",ValueType.TEXT,true,Classification.STRUCTURAL,Sensitivity.PUBLIC,true,true);
            var type=new EntityType("glyph","Invented glyph",List.of(field),new Identity("tag"));
            var mapping=new Projection("glyphs","glyph",List.of(new ExpandedName("urn:mock:handles","sheet"),new ExpandedName("urn:mock:handles","glyph")),List.of(new FieldMapping("tag",new ExpandedName("","id"))),List.of());
            var binding=new Binding("invented-binding",Engine.POSTGRESQL,Storage.TEXT,"mock_schema","mock_table","mock_key","mock_xml",KeyType.INT64,List.of(new Document("sheet","1",List.of(mapping))));
            var declared=new NativeDefinition("mock-handles",BigInteger.ONE,new Logical(List.of(type),List.of(),List.of(),List.of(Operation.RETAIN_ENTITY,Operation.CREATE_ENTITY,Operation.REMOVE_ENTITY,Operation.BIND_FIELD)),List.of(binding));
            var ready=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionCompiler().compile(declared));
            definition=new PublishedDefinition(base.ref,"mock-publication",ready,List.of());
            var key=new ObservedGraph.Key("glyph","alpha");
            observed=graph(Map.of(key,new TargetIntent.Ref.Existing(key)));
            Workspace workspace=new Workspace() {
                public PublishedDefinition definition(Owner owner,NativeCommand.Reference reference){return definition;}
                public PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PublishedDefinition ignored){throw new AssertionError("UNEXPECTED_PROFILE");}
            };
            ObservationPort observation=new ObservationPort(){
                public ObservationResult observe(Selection selection,TransientCredentials credentials,Cancellation cancel){throw new AssertionError("RESERVATION_REQUIRED");}
                public Reservation reserve(Selection selection){return new Reservation.Admitted(new Permit(){
                    final AtomicBoolean used=new AtomicBoolean();
                    public ObservationResult observe(TransientCredentials credentials,Cancellation cancel){
                        assertTrue(used.compareAndSet(false,true));credentials.close();return new ObservationResult.Complete(new ObservationResult.Observation("a".repeat(64),ready.checked().logicalDigest(),ready.checked().bindingDigests().get(binding.id()),List.of(),PlanObservedDestinationTest.evidence()));
                    }
                    public void close(){used.set(true);}
                });}
            };
            ContentAdapter adapter=new ContentAdapter() {
                public ContentResult project(PublishedDefinition ignored,String binding,ObservationResult.Observation observation){return new ContentResult.Complete(observed);}
                public ContentResult materialize(PublishedDefinition ignored,String binding,Content current,Draft draft){
                    if(incomplete.get())return new ContentResult.Rejected(List.of("UNRESOLVED_FIELDS"));
                    var provenance=new HashMap<>(current.provenance());
                    for(var item:draft.intent().entities())if(item instanceof TargetIntent.EntityDecision.Create create){
                        if(!(create.fields().get("tag") instanceof TargetIntent.FieldValue.Entered entered))return new ContentResult.Rejected(List.of("UNRESOLVED_FIELDS"));
                        provenance.put(new ObservedGraph.Key("glyph",entered.text()),create.entity());
                    }
                    return new ContentResult.Complete(graph(provenance));
                }
                public Capture capture(PublishedDefinition ignored,String binding,Content current,ProfileCapture.Command command){throw new AssertionError("UNEXPECTED_CAPTURE");}
            };
            service=new HostedPlanService(base.authority::guard,workspace,Map.of("destination",new Destination("destination",Engine.POSTGRESQL,observation)),adapter,System::nanoTime);
            plan=service.create(base.lease,UUID.randomUUID().toString(),base.ref,binding.id(),"destination").planId();
            inspect();
        }
        void inspect(){var reservation=service.reserve(base.lease,plan,mutation());assertEquals(HostedPlanService.Phase.SUCCEEDED,service.submit(base.lease,reservation.operationId().orElseThrow(),PlanLifecycleTest::credentials).phase());}
        HostedPlanService.Mutation mutation(){return new HostedPlanService.Mutation(revision(),UUID.randomUUID().toString());}
        String revision(){return service.summary(base.lease,plan).revision();}
        void create(String value){service.command(base.lease,plan,new PlanCommand(mutation(),new PlanCommand.Action.Upsert(new PlanCommand.Change(new PlanCommand.Entity.Create(WIRE,Map.of("tag",new TargetIntent.FieldValue.Entered(value)),Map.of()),List.of()))));}
        void bind(String value){service.command(base.lease,plan,new PlanCommand(mutation(),new PlanCommand.Action.BindField(WIRE,"tag",new TargetIntent.FieldValue.Entered(value))));}
        String handle(boolean target,String value){return service.entities(base.lease,plan,revision(),target,0,100).entities().stream().filter(e->e.fields().stream().anyMatch(f->f.value().orElse("").equals(value))).findFirst().orElseThrow().handle();}
        HostedPlanService.ViewSnapshot snapshot(){try(var view=service.reserveView(base.lease,plan)){return view.run(()->{view.pin(revision());return view.snapshot();});}}
    }
    static Content graph(Map<ObservedGraph.Key,TargetIntent.Ref> provenance){
        var keys=provenance.keySet().stream().sorted(Comparator.comparing(ObservedGraph.Key::identity)).toList();var entities=new ArrayList<ObservedGraph.Entity>();
        for(int i=0;i<keys.size();i++){var key=keys.get(i);entities.add(new ObservedGraph.Entity(key,Map.of("tag",key.identity()),new ObservedGraph.Origin("sheet","glyphs","mock-source",i+1,List.of(0))));}
        return new Content(List.of(new Source("sheet","<sheet xmlns=\"urn:mock:handles\"/>","mock-source")),new ObservedGraph(entities,List.of()),provenance);
    }
    @Test void freshDisplayHandleSurvivesIdentityEditsAndRepeatedMaterialization(){
        var h=new Harness();String existing=h.handle(false,"alpha");h.create("beta");String fresh=h.handle(true,"beta");
        h.bind("gamma");assertEquals(fresh,h.handle(true,"gamma"),"A field edit must not allocate another Fresh display identity");
        h.service.materialize(h.base.lease,h.plan,h.revision());assertEquals(fresh,h.handle(true,"gamma"));assertEquals(existing,h.handle(true,"alpha"));
    }
    @Test void failedMaterializationRetainsLiveFreshHandleAndRecreationRetiresIt(){
        var h=new Harness();h.create("beta");String fresh=h.handle(true,"beta");h.incomplete.set(true);h.bind("gamma");
        assertFalse(h.service.summary(h.base.lease,h.plan).targetComplete());h.incomplete.set(false);h.service.materialize(h.base.lease,h.plan,h.revision());
        assertEquals(fresh,h.handle(true,"gamma"),"Dropping target content must preserve a live draft creation's identity");
        h.service.command(h.base.lease,h.plan,new PlanCommand(h.mutation(),new PlanCommand.Action.Forget(WIRE)));h.create("gamma");assertNotEquals(fresh,h.handle(true,"gamma"));
    }
    @Test void unresolvedCreationHasAnAdmittedHandleBeforeAnyTargetOrPageExists(){
        var h=new Harness();h.incomplete.set(true);h.create("beta");
        assertFalse(h.service.summary(h.base.lease,h.plan).targetComplete());
        String handle=h.snapshot().displayHandle(FRESH);assertEquals(handle,UUID.fromString(handle).toString());
        h.bind("gamma");assertEquals(handle,h.snapshot().displayHandle(FRESH));
        h.incomplete.set(false);h.service.materialize(h.base.lease,h.plan,h.revision());assertEquals(handle,h.handle(true,"gamma"));
        assertThrows(PlanRefusal.class,()->h.snapshot().displayHandle(new TargetIntent.Ref.Fresh("not-admitted","glyph")));
    }
    @Test void forgettingAndReinspectionRemoveFreshViewIdentity(){
        var h=new Harness();h.create("beta");String handle=h.snapshot().displayHandle(FRESH);
        h.service.command(h.base.lease,h.plan,new PlanCommand(h.mutation(),new PlanCommand.Action.Forget(WIRE)));
        assertThrows(PlanRefusal.class,()->h.snapshot().displayHandle(FRESH));h.create("beta");assertNotEquals(handle,h.snapshot().displayHandle(FRESH));
        String observed=h.handle(false,"alpha");h.inspect();assertNotEquals(observed,h.handle(false,"alpha"));assertThrows(PlanRefusal.class,()->h.snapshot().displayHandle(FRESH));
    }
}
