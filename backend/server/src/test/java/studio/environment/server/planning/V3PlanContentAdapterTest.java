package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.derived.ComputedGraph;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.plan.*;
import studio.environment.server.plan.V3PlanContentAdapter;

/** Actual invented XML flows, independent exact source and contributor expectations. */
class V3PlanContentAdapterTest {
    static final String FINGERPRINT="c".repeat(64);
    static ObservationResult.Observation observation(studio.environment.core.definitionv3.NativeCompilationResult.Checked definition,String xml) {
        return new ObservationResult.Observation(FINGERPRINT,definition.logicalDigest(),definition.bindingDigests().get("mock-pg"),
                List.of(new ObservationResult.Document("sheet",new ObservationResult.Key("int64","1"),xml,xml.getBytes(StandardCharsets.UTF_8).length,xml.length(),digest(xml))),Map.of());
    }
    @Test void projectionRetainsCompleteObservedProofsAndOriginalPhysicalReferences() {
        var checked=definition(false);
        var result=assertInstanceOf(V3PlanContent.Result.Complete.class,new V3PlanContentAdapter().project(new PlanDefinition.V3(checked),"mock-pg",observation(checked,XML),new Cancellation())).content();
        assertEquals(List.of(new PlanPorts.Source("sheet",XML,digest(XML))),result.sources());
        assertEquals(Set.of(old("one").key(),old("two").key(),old("three").key()),result.provenance().keySet());
        result.provenance().forEach((key,ref)->assertEquals(new studio.environment.core.planning.TargetIntent.Ref.Existing(key),ref));
        var evidence=assertInstanceOf(PlanContentEvidence.V3Observed.class,result.evidence());
        assertEquals(FINGERPRINT,evidence.observationFingerprint());assertEquals(pin(checked,XML,FINGERPRINT),evidence.input().pin());
        assertEquals(DerivedInput.Kind.OBSERVED,evidence.input().kind());
        assertEquals(List.of("alpha:x","alpha:y","beta:x"),evidence.derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
        var alpha=evidence.derived().graph().nodes().stream().filter(n->n.key().value().equals("alpha")).findFirst().orElseThrow();
        assertEquals(List.of("one","two"),alpha.contributors().stream().map(c->((DerivedInput.Ref.Observed)c.physical()).key().identity()).toList());
        var location=((DerivedInput.Proof.Observed)alpha.contributors().getFirst().roles().getFirst().proof()).location();
        assertEquals("al&#112;ha",XML.substring(location.value().valueStart(),location.value().valueEnd()));
        assertEquals(digest(XML),location.value().sourceDigest());assertEquals(1,location.value().elementIndex());
        assertEquals(evidence.input().pin(),evidence.derived().graph().pin());
    }
    @Test void scalarTargetRetainsOriginalTypedAndActualFinalProofsSeparately() {
        var checked=definition(false);var model=new PlanDefinition.V3(checked);var adapter=new V3PlanContentAdapter();
        var current=assertInstanceOf(V3PlanContent.Result.Complete.class,adapter.project(model,"mock-pg",observation(checked,XML),new Cancellation())).content();
        var before=pin(checked,XML,FINGERPRINT);var target=pin(checked,XML,"decision-2");
        var intent=intent(edit("one",new studio.environment.core.planning.TargetIntent.FieldValue.KeepObserved(),new studio.environment.core.planning.TargetIntent.FieldValue.Entered("beta")));
        var result=assertInstanceOf(V3PlanContent.Result.Complete.class,adapter.materialize(model,before,current,target,new PlanPorts.Draft(intent,List.of()),new Cancellation())).content();
        String expected="<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x'/>"
                +"<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";
        assertEquals(List.of(new PlanPorts.Source("sheet",expected,digest(expected))),result.sources());
        assertEquals(XML,current.sources().getFirst().xml());
        var evidence=assertInstanceOf(PlanContentEvidence.V3Target.class,result.evidence());
        assertEquals(FINGERPRINT,evidence.observationFingerprint());assertEquals(before,evidence.originalPin());
        assertEquals(DerivedInput.Kind.TYPED_TARGET,evidence.preliminary().kind());assertEquals(target,evidence.preliminary().pin());
        assertEquals(DerivedInput.Kind.OBSERVED,evidence.input().kind());assertEquals(pin(checked,expected,"decision-2"),evidence.input().pin());
        assertEquals(3,evidence.physicalExpected().entities().size());
        assertEquals(List.of("alpha:y","beta:x"),evidence.derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
        var beta=evidence.derived().graph().nodes().stream().filter(n->n.key().value().equals("beta")).findFirst().orElseThrow();
        assertEquals(List.of("one","three"),beta.contributors().stream().map(c->((DerivedInput.Ref.Observed)c.physical()).key().identity()).toList());
        var one=evidence.preliminary().entities().stream().filter(e->e.reference().equals(new DerivedInput.Ref.Target(old("one")))).findFirst().orElseThrow();
        var entered=(DerivedInput.Proof.Target)((DerivedInput.FieldState.Present)one.fields().get("tone")).proof();
        assertTrue(entered.kept().isEmpty());assertEquals(new studio.environment.core.planning.TargetIntent.FieldValue.Entered("beta"),entered.decision());
        var kept=(DerivedInput.Proof.Target)((DerivedInput.FieldState.Present)one.fields().get("finish")).proof();
        assertEquals(digest(XML),kept.kept().orElseThrow().location().value().sourceDigest());
        assertEquals(current.provenance(),result.provenance());
    }
    static PlanPorts.Content current(PlanDefinition.V3 model,String xml) {
        return assertInstanceOf(V3PlanContent.Result.Complete.class,new V3PlanContentAdapter().project(model,"mock-pg",observation(model.checked(),xml),new Cancellation())).content();
    }
    static V3PlanContent.Result target(PlanDefinition.V3 model,PlanPorts.Content original,TargetIntent choices,List<PlanPorts.Placement> placements) {
        return new V3PlanContentAdapter().materialize(model,pin(model.checked(),XML,FINGERPRINT),original,pin(model.checked(),XML,"decision-2"),new PlanPorts.Draft(choices,placements),new Cancellation());
    }
    @Test void observationMetadataMustAgreeWithActualSourcesAndConfiguredKeys() {
        var checked=definition(false);var model=new PlanDefinition.V3(checked);var adapter=new V3PlanContentAdapter();var good=observation(checked,XML);var doc=good.documents().getFirst();
        for(var changed:List.of(
                new ObservationResult.Document("sheet",doc.key(),XML,doc.utf8Bytes()+1,doc.characters(),doc.sourceDigest()),
                new ObservationResult.Document("sheet",doc.key(),XML,doc.utf8Bytes(),doc.characters()+1,doc.sourceDigest()),
                new ObservationResult.Document("sheet",new ObservationResult.Key("text","1"),XML,doc.utf8Bytes(),doc.characters(),doc.sourceDigest()),
                new ObservationResult.Document("sheet",new ObservationResult.Key("int64","2"),XML,doc.utf8Bytes(),doc.characters(),doc.sourceDigest()),
                new ObservationResult.Document("other",doc.key(),XML,doc.utf8Bytes(),doc.characters(),doc.sourceDigest()),
                new ObservationResult.Document("sheet",doc.key(),XML,doc.utf8Bytes(),doc.characters(),"f".repeat(64)))) {
            var observed=new ObservationResult.Observation(good.fingerprint(),good.logicalDigest(),good.bindingDigest(),List.of(changed),Map.of());
            assertInstanceOf(V3PlanContent.Result.Refused.class,adapter.project(model,"mock-pg",observed,new Cancellation()));
        }
        for(var documents:List.of(List.<ObservationResult.Document>of(),List.of(doc,doc))) {
            assertEquals(new V3PlanContent.Result.Refused("INVENTORY_MISMATCH"),adapter.project(model,"mock-pg",new ObservationResult.Observation(good.fingerprint(),good.logicalDigest(),good.bindingDigest(),documents,Map.of()),new Cancellation()));
        }
        for(var fingerprint:List.of("",FINGERPRINT.toUpperCase(Locale.ROOT),"not-a-fingerprint")) {
            assertEquals(new V3PlanContent.Result.Refused("INVALID_PIN"),adapter.project(model,"mock-pg",new ObservationResult.Observation(fingerprint,good.logicalDigest(),good.bindingDigest(),good.documents(),Map.of()),new Cancellation()));
        }
        var forged=new studio.environment.core.definitionv3.NativeCompilationResult.Checked(checked.definition(),checked.logicalDigest(),checked.bindingDigests(),Map.of());
        assertEquals(new V3PlanContent.Result.Refused("INVALID_DEFINITION"),adapter.project(new PlanDefinition.V3(forged),"mock-pg",good,new Cancellation()));
    }
    @Test void changedOriginalGraphInputComputedProofOrReferenceCannotBeIgnored() {
        var model=new PlanDefinition.V3(definition(false));var good=current(model,XML);var evidence=(PlanContentEvidence.V3Observed)good.evidence();
        var graph=new ObservedGraph(good.graph().entities().subList(1,good.graph().entities().size()),good.graph().edges());
        var missingInput=new DerivedInput(evidence.input().kind(),evidence.input().pin(),evidence.input().entities().subList(1,evidence.input().entities().size()),evidence.input().edges());
        var computed=evidence.derived().graph();
        var missingResult=new DerivedResult.Complete(new ComputedGraph(computed.pin(),computed.nodes().subList(1,computed.nodes().size()),computed.memberships(),computed.cooccurrences()),evidence.derived().rules());
        var provenance=new HashMap<>(good.provenance());provenance.put(old("one").key(),old("two"));
        for(var changed:List.of(
                new PlanPorts.Content(good.sources(),graph,good.provenance(),evidence),
                new PlanPorts.Content(good.sources(),good.graph(),good.provenance(),new PlanContentEvidence.V3Observed(FINGERPRINT,missingInput,evidence.derived())),
                new PlanPorts.Content(good.sources(),good.graph(),good.provenance(),new PlanContentEvidence.V3Observed(FINGERPRINT,evidence.input(),missingResult)),
                new PlanPorts.Content(good.sources(),good.graph(),provenance,evidence))) {
            assertEquals(new V3PlanContent.Result.Refused("STALE_CONTENT"),target(model,changed,intent(),List.of()));
        }
        var sources=List.of(new PlanPorts.Source("sheet",XML.replace("al&#112;ha","alpha"),digest(XML)));
        assertEquals(new V3PlanContent.Result.Refused("STALE_INPUT"),target(model,new PlanPorts.Content(sources,good.graph(),good.provenance(),evidence),intent(),List.of()));
    }
    @Test void legacyOrTargetEvidenceAndWrongOriginalDecisionPinsRefuse() {
        var model=new PlanDefinition.V3(definition(false));var good=current(model,XML);var adapter=new V3PlanContentAdapter();
        var legacy=new PlanPorts.Content(good.sources(),good.graph(),good.provenance());
        assertInstanceOf(PlanContentEvidence.V2.class,legacy.evidence());
        assertEquals(new V3PlanContent.Result.Refused("UNSUPPORTED_CONTENT"),target(model,legacy,intent(),List.of()));
        var complete=assertInstanceOf(V3PlanContent.Result.Complete.class,target(model,good,intent(),List.of())).content();
        assertEquals(new V3PlanContent.Result.Refused("UNSUPPORTED_CONTENT"),target(model,complete,intent(),List.of()));
        var before=pin(model.checked(),XML,FINGERPRINT);var decision=pin(model.checked(),XML,"decision-2");
        assertEquals(new V3PlanContent.Result.Refused("STALE_INPUT"),adapter.materialize(model,decision,good,decision,PlanPorts.Draft.empty(),new Cancellation()));
        assertEquals(new V3PlanContent.Result.Refused("STALE_INPUT"),adapter.materialize(model,before,good,before,PlanPorts.Draft.empty(),new Cancellation()));
        assertEquals(new V3PlanContent.Result.Refused("STALE_INPUT"),adapter.materialize(model,before,good,new DerivedInput.Pin("decision-2",before.logicalDigest(),before.bindingId(),before.bindingDigest(),Map.of("sheet","f".repeat(64))),PlanPorts.Draft.empty(),new Cancellation()));
    }
    @Test void unresolvedAbsentEmptyAndLastContributorRemovalStayDistinct() {
        var model=new PlanDefinition.V3(definition(false));var good=current(model,XML);
        assertEquals(new V3PlanContent.Result.Incomplete(List.of("by-tone")),target(model,good,intent(edit("one",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.Unresolved())),List.of()));
        assertEquals(new V3PlanContent.Result.Refused("INVALID_DERIVED_IDENTITY"),target(model,good,intent(edit("one",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.Entered(""))),List.of()));
        assertEquals(new V3PlanContent.Result.Refused("ATTRIBUTE_PRESENCE_UNSUPPORTED"),target(model,good,intent(edit("one",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.ExplicitlyAbsent())),List.of()));
        String missingXml=XML.replace(" tone='al&#112;ha'","");var missing=current(model,missingXml);
        var absent=assertInstanceOf(V3PlanContent.Result.Complete.class,new V3PlanContentAdapter().materialize(model,pin(model.checked(),missingXml,FINGERPRINT),missing,pin(model.checked(),missingXml,"absent-2"),
                new PlanPorts.Draft(intent(edit("one",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.ExplicitlyAbsent())),List.of()),new Cancellation())).content();
        assertEquals(missingXml,absent.sources().getFirst().xml());
        var input=((PlanContentEvidence.V3Target)absent.evidence()).input();
        var entity=input.entities().stream().filter(e->((DerivedInput.Ref.Observed)e.reference()).key().equals(old("one").key())).findFirst().orElseThrow();
        assertInstanceOf(DerivedInput.FieldState.Absent.class,entity.fields().get("tone"));
        var removed=assertInstanceOf(V3PlanContent.Result.Complete.class,target(model,good,intent(new TargetIntent.EntityDecision.Remove(old("one")),new TargetIntent.EntityDecision.Remove(old("two"))),List.of())).content();
        String expected="<items><!-- mock -->\r\n<item id='three' tone='beta' finish='x'/></items>";
        assertEquals(expected,removed.sources().getFirst().xml());
        var proof=(PlanContentEvidence.V3Target)removed.evidence();
        assertEquals(Set.of("beta","x"),new HashSet<>(proof.derived().graph().nodes().stream().map(n->n.key().value()).toList()));
        assertEquals(Map.of(old("three").key(),old("three")),removed.provenance());
    }
    @Test void childPropertyEditRetainsExactSelectorAndFinalValueEvidence() {
        String xml="<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='al&#112;ha'/></item></items>";
        var model=new PlanDefinition.V3(definition(true));var good=current(model,xml);var adapter=new V3PlanContentAdapter();
        var result=assertInstanceOf(V3PlanContent.Result.Complete.class,adapter.materialize(model,pin(model.checked(),xml,FINGERPRINT),good,pin(model.checked(),xml,"child-2"),
                new PlanPorts.Draft(intent(edit("one",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.Entered("beta &"))),List.of()),new Cancellation())).content();
        String expected="<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='beta &amp;'/></item></items>";
        assertEquals(expected,result.sources().getFirst().xml());
        var evidence=(PlanContentEvidence.V3Target)result.evidence();
        var location=((DerivedInput.Proof.Observed)((DerivedInput.FieldState.Present)evidence.input().entities().getFirst().fields().get("tone")).proof()).location();
        assertEquals(2,location.value().elementIndex());assertEquals(1,location.selector().orElseThrow().parentElementIndex());
        assertEquals("tone",expected.substring(location.selector().orElseThrow().discriminator().valueStart(),location.selector().orElseThrow().discriminator().valueEnd()));
        assertEquals("beta &amp;",expected.substring(location.value().valueStart(),location.value().valueEnd()));
        assertEquals(digest(expected),location.value().sourceDigest());
    }
    @Test void freshIdentityReuseKeepsOriginalAndCreatedReferencesDistinct() {
        var model=new PlanDefinition.V3(definition(false));var good=current(model,XML);var fresh=new TargetIntent.Ref.Fresh("new-item","item");
        var decisions=intent(edit("one",new TargetIntent.FieldValue.Entered("renamed"),new TargetIntent.FieldValue.KeepObserved()),
                new TargetIntent.EntityDecision.Create(fresh,Map.of("id",new TargetIntent.FieldValue.Entered("one"),"tone",new TargetIntent.FieldValue.Entered("alpha"),"finish",new TargetIntent.FieldValue.Entered("x")),Map.of()));
        var placement=new PlanPorts.Placement(fresh,"sheet","items",new PlanPorts.Parent.Existing("sheet",digest(XML),0));
        var complete=assertInstanceOf(V3PlanContent.Result.Complete.class,target(model,good,decisions,List.of(placement))).content();
        assertEquals(old("one"),complete.provenance().get(old("renamed").key()));assertEquals(fresh,complete.provenance().get(old("one").key()));
        assertEquals(4,complete.provenance().size());
        assertEquals(4,complete.graph().entities().stream().filter(e->e.key().equals(old("one").key())).findFirst().orElseThrow().origin().elementIndex());
        var evidence=(PlanContentEvidence.V3Target)complete.evidence();
        assertEquals("one",evidence.physicalExpected().entities().stream().filter(e->e.reference().equals(fresh)).findFirst().orElseThrow().identity().identity());
    }
    @Test void originalCancellationAndMissingControlNeverReturnSuccessfulContent() {
        var model=new PlanDefinition.V3(definition(false));var good=current(model,XML);var cancelled=new Cancellation();cancelled.cancel();var adapter=new V3PlanContentAdapter();
        assertEquals(new V3PlanContent.Result.Refused("CANCELLED"),adapter.project(model,"mock-pg",observation(model.checked(),XML),cancelled));
        assertEquals(new V3PlanContent.Result.Refused("CANCELLED"),adapter.materialize(model,pin(model.checked(),XML,FINGERPRINT),good,pin(model.checked(),XML,"decision-2"),PlanPorts.Draft.empty(),cancelled));
        assertEquals(new V3PlanContent.Result.Refused("INVALID_INPUT"),adapter.project(model,"mock-pg",observation(model.checked(),XML),null));
        assertEquals(new V3PlanContent.Result.Refused("INVALID_INPUT"),adapter.materialize(model,pin(model.checked(),XML,FINGERPRINT),good,pin(model.checked(),XML,"decision-2"),PlanPorts.Draft.empty(),null));
    }
}
