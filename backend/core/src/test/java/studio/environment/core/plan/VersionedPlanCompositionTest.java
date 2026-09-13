package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanPorts.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.profile.*;

/** Independently invented physical entities and computed tones, never donor configuration. */
class VersionedPlanCompositionTest {
    static final ObservedGraph.Key FIRST = new ObservedGraph.Key("unit", "first-source");
    static final ObservedGraph.Key SECOND = new ObservedGraph.Key("unit", "second-source");
    static final ObservedGraph.Key HUB = new ObservedGraph.Key("hub", "shared-source");
    static NativeCompilationResult.Checked definition() {
        var fields = List.of(new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, true),
                new Field("tone", ValueType.TEXT, true, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true));
        var types = List.of(new EntityType("unit", "Mock unit", fields, new Identity("id")), new EntityType("hub", "Mock hub", fields, new Identity("id")));
        var links = new Relation("links", "unit", "hub", RelationKind.REFERENCE, BigInteger.ONE, BigInteger.ONE, true);
        var projections = types.stream().map(t -> new Projection(t.id()+"s", t.id(), List.of(new ExpandedName("", "root"),new ExpandedName("",t.id())),
                List.of(new FieldMapping("id",new ExpandedName("","id")),new FieldMapping("tone",new ExpandedName("","tone"))),
                t.id().equals("unit")?List.of(new ReferenceMapping("links",new ExpandedName("","link"))):List.<ReferenceMapping>of())).toList();
        var binding = new Binding("mock-binding",Engine.POSTGRESQL,Storage.TEXT,"mock","records","id","xml",KeyType.INT64,List.of(new Document("sheet","1",projections)));
        var logical = new NativeDefinition.Logical(types,List.of(links),List.of(),List.of(Operation.values()),
                List.of(new NativeDefinition.ComputedType("tones","Mock tones")),
                List.of(new NativeDefinition.Derivation("by-tone","unit","tone","tones","has-tone")),List.of(),
                List.of(new CountRule("tone-count","tones",BigInteger.ZERO,BigInteger.TEN)));
        var result = assertInstanceOf(NativeCompilationResult.Incomplete.class,new NativeDefinitionCompiler().compile(new NativeDefinition("mock-plan",BigInteger.ONE,logical,List.of(binding))));
        assertTrue(result.diagnostics().stream().allMatch(d -> d.code().equals("MECHANISM_UNQUALIFIED")),result.diagnostics().toString());
        return result.checked();
    }
    static ObservedGraph graph() {
        var entities = new ArrayList<ObservedGraph.Entity>();int index=1;
        for(var key:List.of(FIRST,SECOND,HUB)) entities.add(new ObservedGraph.Entity(key,Map.of("id",key.identity(),"tone","donor-canary-"+index),
                new ObservedGraph.Origin("sheet",key.type()+"s","a".repeat(64),index++,List.of(0))));
        return new ObservedGraph(entities,List.of(new ObservedGraph.Edge("links",FIRST,HUB),new ObservedGraph.Edge("links",SECOND,HUB)));
    }
    static Content content() {
        return new Content(List.of(),graph(),Map.of(FIRST,new TargetIntent.Ref.Existing(FIRST),SECOND,new TargetIntent.Ref.Existing(SECOND),HUB,new TargetIntent.Ref.Existing(HUB)));
    }
    static ProfileResult.Checked profile(NativeCompilationResult.Checked definition) {
        var command = new ProfileCapture.Command("neutral",BigInteger.ONE,List.of(
                new ProfileCapture.SlotMapping(FIRST,"first","First"),new ProfileCapture.SlotMapping(SECOND,"second","Second"),new ProfileCapture.SlotMapping(HUB,"dependency","Dependency")));
        return assertInstanceOf(ProfileResult.StructurallyValid.class,new V3ProfileCapture().capture(definition,graph(),command)).checked();
    }
    @Test void wholeAndPartialReuseMergeOnlyPhysicalShapeAndRequireFreshValues() {
        for(var selected:List.of(Set.of("first"),Set.of("first","second","dependency"))) checkReuse(selected);
    }
    private static void checkReuse(Set<String> selected) {
        var d=definition();var p=profile(d);var composer=new V3ProfileComposer();
        var preview=assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class,composer.preview(d,p,selected)).preview();
        assertEquals(selected.size()==1?List.of("dependency","first"):List.of("dependency","first","second"),preview.physical().included().stream().map(Profile.Entity::id).toList());
        assertEquals(List.of("by-tone"),preview.affectedDerivations());
        var choices=new ArrayList<ProfileComposer.Decision>();choices.add(new ProfileComposer.Decision.UseExisting("dependency",HUB));
        var expectedAdditions=new ArrayList<ProfileComposer.Target.New>();
        for(String slot:List.of("first","second")) if(selected.contains(slot)) {
            choices.add(new ProfileComposer.Decision.Create(slot,"new-"+slot));expectedAdditions.add(new ProfileComposer.Target.New("new-"+slot,"unit"));
        }
        var proposal=assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class,composer.compose(d,p,preview,graph(),choices)).draft();
        assertEquals(expectedAdditions,proposal.additions());
        var merged=assertDoesNotThrow(()->PlanComposition.merge(new PlanDefinition.V3(d),Draft.empty(),content(),proposal));
        var expected=new HashMap<TargetIntent.Ref,TargetIntent.EntityDecision>();
        for(var requested:expectedAdditions) {
            var fresh=new TargetIntent.Ref.Fresh(requested.slot(),"unit");
            expected.put(fresh,new TargetIntent.EntityDecision.Create(fresh,Map.of("id",new TargetIntent.FieldValue.Unresolved(),"tone",new TargetIntent.FieldValue.Unresolved()),Map.of("links",new TargetIntent.ReferenceValue.To(new TargetIntent.Ref.Existing(HUB)))));
        }
        expected.put(new TargetIntent.Ref.Existing(HUB),new TargetIntent.EntityDecision.Retain(new TargetIntent.Ref.Existing(HUB),Map.of("id",new TargetIntent.FieldValue.Unresolved(),"tone",new TargetIntent.FieldValue.Unresolved()),Map.of()));
        var actual=new HashMap<TargetIntent.Ref,TargetIntent.EntityDecision>();
        merged.intent().entities().forEach(e->assertNull(actual.put(e.entity(),e)));
        assertEquals(expected,actual);assertTrue(merged.intent().containment().isEmpty());assertTrue(merged.placements().isEmpty());
        assertEquals(merged,PlanComposition.merge(new PlanDefinition.V3(d),Draft.empty(),content(),proposal));
    }
    @Test void originalReferencesAndEnteredValuesSurviveReuseAfterIdentityChanges() {
        var d=definition();var p=profile(d);var composer=new V3ProfileComposer();
        var changed=new ObservedGraph.Key("unit","Edited-Identity");var createdKey=new ObservedGraph.Key("unit","Created-Identity");
        var original=new TargetIntent.Ref.Existing(FIRST);var fresh=new TargetIntent.Ref.Fresh("prior-slot","unit");var dependency=new TargetIntent.Ref.Existing(HUB);
        var beforeFirst=new TargetIntent.EntityDecision.Retain(original,Map.of("id",new TargetIntent.FieldValue.Entered("Edited-Identity"),"tone",new TargetIntent.FieldValue.KeepObserved()),Map.of("links",new TargetIntent.ReferenceValue.To(dependency)));
        var beforeSecond=new TargetIntent.EntityDecision.Create(fresh,Map.of("id",new TargetIntent.FieldValue.Entered("Created-Identity"),"tone",new TargetIntent.FieldValue.Entered("MiXeD-Entered")),Map.of("links",new TargetIntent.ReferenceValue.To(dependency)));
        var placement=new Placement(fresh,"sheet","units",new Parent.Existing("sheet","a".repeat(64),0));
        var prior=new Draft(new TargetIntent(List.of(beforeFirst,beforeSecond),List.of()),List.of(placement));
        var targetGraph=new ObservedGraph(List.of(
                new ObservedGraph.Entity(changed,Map.of("id",changed.identity(),"tone","retained"),graph().entities().getFirst().origin()),
                new ObservedGraph.Entity(createdKey,Map.of("id",createdKey.identity(),"tone","MiXeD-Entered"),graph().entities().get(1).origin()),graph().entities().getLast()),
                List.of(new ObservedGraph.Edge("links",changed,HUB),new ObservedGraph.Edge("links",createdKey,HUB)));
        var target=new Content(List.of(),targetGraph,Map.of(changed,original,createdKey,fresh,HUB,dependency));
        var preview=assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class,composer.preview(d,p,Set.of("first","second","dependency"))).preview();
        var proposal=assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class,composer.compose(d,p,preview,targetGraph,List.of(
                new ProfileComposer.Decision.UseExisting("first",changed),new ProfileComposer.Decision.UseExisting("second",createdKey),new ProfileComposer.Decision.UseExisting("dependency",HUB)))).draft();
        var merged=PlanComposition.merge(new PlanDefinition.V3(d),prior,target,proposal);
        assertEquals(List.of(beforeFirst,beforeSecond),merged.intent().entities().subList(0,2));
        assertEquals(3,merged.intent().entities().size());assertEquals(List.of(placement),merged.placements());
        assertTrue(merged.intent().entities().stream().noneMatch(e->e.entity().equals(new TargetIntent.Ref.Existing(changed))||e.entity().equals(new TargetIntent.Ref.Existing(createdKey))));
    }
    @Test void repeatedFreshSlotMissingOriginAndComputedRelationRefuse() {
        var d=definition();var p=profile(d);var composer=new V3ProfileComposer();
        var preview=assertInstanceOf(V3ProfileComposer.PreviewResult.Proposed.class,composer.preview(d,p,Set.of("first"))).preview();
        var proposal=assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class,composer.compose(d,p,preview,graph(),List.of(
                new ProfileComposer.Decision.Create("first","new-first"),new ProfileComposer.Decision.UseExisting("dependency",HUB)))).draft();
        var ref=new TargetIntent.Ref.Fresh("new-first","unit");
        var existing=new Draft(new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(ref,Map.of(),Map.of())),List.of()),List.of());
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->PlanComposition.merge(new PlanDefinition.V3(d),existing,content(),proposal)).code());
        var missing=new Content(List.of(),graph(),Map.of(FIRST,new TargetIntent.Ref.Existing(FIRST),SECOND,new TargetIntent.Ref.Existing(SECOND)));
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->PlanComposition.merge(new PlanDefinition.V3(d),Draft.empty(),missing,proposal)).code());
        var invalidRelation=new ProfileComposer.Draft(List.of(),List.of(HUB),List.of(),List.of(new ProfileComposer.RelationProposal("has-tone",new ProfileComposer.Target.Existing(HUB),new ProfileComposer.Target.Existing(HUB))),List.of());
        assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->PlanComposition.merge(new PlanDefinition.V3(d),Draft.empty(),content(),invalidRelation)).code());
    }
    @Test void completeV3ModelRetainsDerivedDeclarationsAndCannotBecomeV2Readiness() {
        var fixture=new HostedPlanServiceTest();var v2=fixture.definition;
        assertSame(v2.compiled(),assertInstanceOf(PlanDefinition.V2.class,v2.model()).ready());
        assertSame(v2.compiled().checked().definition().logical(),v2.model().physical());
        var checked=definition();var model=new PlanDefinition.V3(checked);
        var v3=new PublishedDefinition(fixture.ref,"mock-v3-publication",model,List.of());
        assertSame(checked,assertInstanceOf(PlanDefinition.V3.class,v3.model()).checked());
        assertEquals(List.of("unit","hub"),model.physical().entityTypes().stream().map(EntityType::id).toList());
        assertEquals(List.of("links"),model.physical().relations().stream().map(Relation::id).toList());
        assertTrue(model.physical().rules().isEmpty());assertEquals(1,model.checked().definition().logical().computedRules().size());
        assertThrows(UnsupportedOperationException.class,()->model.physical().entityTypes().clear());
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,v3::compiled).code());
        assertEquals("PlanDefinitionV3[redacted]",model.toString());assertFalse(v3.toString().contains("mock-v3-publication"));
    }
    @Test void currentHostedCreationRefusesV3BeforeReservationWithoutConsumingPlanCapacity() {
        var fixture=new HostedPlanServiceTest();var selected=new java.util.concurrent.atomic.AtomicReference<>(
                new PublishedDefinition(fixture.ref,"mock-v3-publication",new PlanDefinition.V3(definition()),List.of()));
        var workspace=new Workspace() {
            public PublishedDefinition definition(studio.environment.core.session.Owner owner,studio.environment.core.workspace.NativeCommand.Reference reference){return selected.get();}
            public PublishedProfile profile(studio.environment.core.session.Owner owner,studio.environment.core.workspace.NativeCommand.Reference reference,PublishedDefinition definition){throw new AssertionError("UNEXPECTED_PROFILE_LOOKUP");}
        };
        var reservations=new java.util.concurrent.atomic.AtomicInteger();
        var port=new studio.environment.core.observation.ObservationPort() {
            public studio.environment.core.observation.ObservationResult observe(Selection selection,studio.environment.core.observation.TransientCredentials credentials,Cancellation cancellation){throw new AssertionError("UNEXPECTED_OBSERVATION");}
            public Reservation reserve(Selection selection){reservations.incrementAndGet();throw new AssertionError("UNEXPECTED_RESERVATION");}
        };
        var service=new HostedPlanService(fixture.authority::guard,workspace,Map.of("destination",new Destination("destination",Engine.POSTGRESQL,port)),fixture.content,System::nanoTime);
        for(int i=0;i<2;i++) assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->service.create(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"mock-binding","destination")).code());
        assertEquals(0,reservations.get());selected.set(fixture.definition);
        assertEquals("1",service.create(fixture.lease,UUID.randomUUID().toString(),fixture.ref,"invented-binding","destination").revision());
    }
}
