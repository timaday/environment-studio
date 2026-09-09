package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import static studio.environment.server.planning.V3PlanContentAdapterTest.FINGERPRINT;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.Outcome;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.plan.V3PlanContentAdapter;

/** Independently invented XML operations, with literal whole-document expectations. */
class V3PlanContentOperationsTest {
    static NativeCompilationResult.Checked compile(NativeDefinition definition) {
        return assertInstanceOf(NativeCompilationResult.Incomplete.class,new NativeDefinitionCompiler().compile(definition)).checked();
    }
    static ObservationResult.Observation observed(PlanDefinition.V3 model,Map<String,String> documents) {
        var binding=model.checked().definition().bindings().getFirst();
        return new ObservationResult.Observation(FINGERPRINT,model.checked().logicalDigest(),model.checked().bindingDigests().get(binding.id()),
                binding.documents().reversed().stream().map(d->{String xml=documents.get(d.id());return new ObservationResult.Document(d.id(),new ObservationResult.Key("int64",d.key()),xml,xml.getBytes(StandardCharsets.UTF_8).length,xml.length(),digest(xml));}).toList(),Map.of());
    }
    static PlanPorts.Content project(PlanDefinition.V3 model,Map<String,String> documents) {
        return assertInstanceOf(V3PlanContent.Result.Complete.class,new V3PlanContentAdapter().project(model,"mock-pg",observed(model,documents),new Cancellation())).content();
    }
    static V3PlanContent.Result target(PlanDefinition.V3 model,PlanPorts.Content original,PlanPorts.Draft draft) {
        var pin=((PlanContentEvidence.V3Observed)original.evidence()).input().pin();
        var next=new DerivedInput.Pin("decision-2",pin.logicalDigest(),pin.bindingId(),pin.bindingDigest(),pin.documentDigests());
        return new V3PlanContentAdapter().materialize(model,pin,original,next,draft,new Cancellation());
    }
    @Test void failedObservedRulesRemainInspectableAndExplicitRepairRecomputesCompleteTarget() {
        var d=definition(false).definition();var l=d.logical();
        var logical=new NativeDefinition.Logical(l.entityTypes(),l.relations(),l.rules(),l.operationCapabilities(),l.computedTypes(),l.derivations(),
                List.of(new NativeDefinition.Cooccurrence("pair","by-tone","by-finish",BigInteger.ZERO,BigInteger.ONE)),l.computedRules());
        var model=new PlanDefinition.V3(compile(new NativeDefinition(d.id(),d.revision(),logical,d.bindings())));
        var original=project(model,Map.of("sheet",XML));var proof=(PlanContentEvidence.V3Observed)original.evidence();
        var failure=proof.derived().rules().stream().filter(r->r.outcome()==Outcome.FAIL).toList();
        assertEquals(1,failure.size());assertEquals("alpha",failure.getFirst().source().orElseThrow().value());assertEquals(BigInteger.TWO,failure.getFirst().actual());
        assertEquals(new V3PlanContent.Result.Refused("DERIVED_RULE_FAILED"),target(model,original,PlanPorts.Draft.empty()));
        var repaired=assertInstanceOf(V3PlanContent.Result.Complete.class,target(model,original,new PlanPorts.Draft(intent(new TargetIntent.EntityDecision.Remove(old("two"))),List.of()))).content();
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='three' tone='beta' finish='x'/></items>",repaired.sources().getFirst().xml());
        assertTrue(((PlanContentEvidence.V3Target)repaired.evidence()).derived().rules().stream().allMatch(r->r.outcome()==Outcome.PASS));
        assertEquals(Outcome.FAIL,failure.getFirst().outcome());assertEquals(XML,original.sources().getFirst().xml());
    }
    @Test void unicodeBytesAndUtf16CharactersRemainDistinctAndInvalidSurrogatesRefuse() {
        var model=new PlanDefinition.V3(definition(false));String xml="<items><item id='one' tone='é𐀀' finish='x'/></items>";
        var observation=observed(model,Map.of("sheet",xml));var document=observation.documents().getFirst();
        assertEquals(document.characters()+3,document.utf8Bytes());
        var content=project(model,Map.of("sheet",xml));
        assertEquals(Set.of("x","é𐀀"),new HashSet<>(((PlanContentEvidence.V3Observed)content.evidence()).derived().graph().nodes().stream().map(n->n.key().value()).toList()));
        for(String invalid:List.of("<items>\ud800</items>","<items>\udc00</items>"))
            assertEquals(new V3PlanContent.Result.Refused("INVALID_UNICODE"),new V3PlanContentAdapter().project(model,"mock-pg",observed(model,Map.of("sheet",invalid)),new Cancellation()));
        var result=assertInstanceOf(V3PlanContent.Result.Complete.class,target(model,content,new PlanPorts.Draft(intent(edit("one",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.Entered("é𐀀 &\t"))),List.of()))).content();
        assertEquals("<items><item id='one' tone='é𐀀 &amp;&#9;' finish='x'/></items>",result.sources().getFirst().xml());
    }
    static PlanDefinition.V3 twoDocuments(boolean nested) {
        var d=definition(false).definition();var l=d.logical();var b=d.bindings().getFirst();var p=b.documents().getFirst().entities().getFirst();
        var types=new ArrayList<>(l.entityTypes());var operations=new ArrayList<>(l.operationCapabilities());var relations=new ArrayList<>(l.relations());
        if(nested) {
            var id=l.entityTypes().getFirst().fields().getFirst();types.add(new EntityType("box","Invented box",List.of(id),new Identity("id")));
            relations.add(new studio.environment.core.definition.DefinitionDraft.Relation("contains","box","item",studio.environment.core.definition.DefinitionDraft.RelationKind.CONTAINMENT,BigInteger.ZERO,BigInteger.TEN,false));
            operations.add(Operation.MOVE_RELATION);
        }
        var documents=new ArrayList<Document>();
        for(String side:List.of("left","right")) {
            var projections=new ArrayList<Projection>();var path=nested?List.of(new ExpandedName("","items"),new ExpandedName("","box"),new ExpandedName("","item")):p.path();
            projections.add(new Projection("items-"+side,p.type(),path,p.fields(),p.references()));
            if(nested) projections.add(new Projection("boxes-"+side,"box",List.of(new ExpandedName("","items"),new ExpandedName("","box")),List.of(new FieldMapping("id",new ExpandedName("","id"))),List.of()));
            documents.add(new Document(side,side.equals("left")?"1":"2",projections));
        }
        var binding=new Binding(b.id(),b.engine(),b.storage(),b.schema(),b.table(),b.keyColumn(),b.xmlColumn(),b.keyType(),documents);
        var logical=new NativeDefinition.Logical(types,relations,l.rules(),operations,l.computedTypes(),l.derivations(),l.cooccurrences(),l.computedRules());
        return new PlanDefinition.V3(compile(new NativeDefinition(d.id(),d.revision(),logical,List.of(binding))));
    }
    @Test void multiDocumentContributorDeduplicationKeepsEveryOriginAndExactChangedSource() {
        var model=twoDocuments(false);
        String left="<items><!-- left --><item id='one' tone='alpha' finish='x'/></items>";
        String right="<items>\r\n<item id='two' tone='alpha' finish='x'/></items>";
        var original=project(model,Map.of("left",left,"right",right));
        assertEquals(List.of("left","right"),original.sources().stream().map(PlanPorts.Source::documentId).toList());
        var observed=(PlanContentEvidence.V3Observed)original.evidence();var pair=observed.derived().graph().cooccurrences();
        assertEquals(1,pair.size());assertEquals(2,pair.getFirst().contributors().size());
        assertEquals(List.of("left","right"),pair.getFirst().contributors().stream().map(c->((DerivedInput.Ref.Observed)c.physical()).origin().documentId()).toList());
        var result=assertInstanceOf(V3PlanContent.Result.Complete.class,target(model,original,new PlanPorts.Draft(intent(edit("two",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.Entered("beta"))),List.of()))).content();
        String expectedRight="<items>\r\n<item id='two' tone='beta' finish='x'/></items>";
        assertEquals(List.of(new PlanPorts.Source("left",left,digest(left)),new PlanPorts.Source("right",expectedRight,digest(expectedRight))),result.sources());
        var finalProof=(PlanContentEvidence.V3Target)result.evidence();
        assertEquals(List.of("alpha:x","beta:x"),finalProof.derived().graph().cooccurrences().stream().map(e->e.source().value()+":"+e.target().value()).toList());
        assertEquals(Map.of("left",digest(left),"right",digest(expectedRight)),finalProof.input().pin().documentDigests());
        assertEquals(original.provenance(),result.provenance());
    }
    @Test void crossDocumentMovePreservesOriginalKeepProofAndUsesActualDestinationForFinalProof() {
        var model=twoDocuments(true);
        String left="<items><box id='a'><!-- left --><item id='one' tone='al&#112;ha' finish='x'/></box></items>";
        String right="<items><box id='b'><item id='two' tone='alpha' finish='x'/></box></items>";
        var original=project(model,Map.of("left",left,"right",right));var parent=new TargetIntent.Ref.Existing(new ObservedGraph.Key("box","b"));
        var choices=new TargetIntent(List.of(edit("one",new TargetIntent.FieldValue.KeepObserved(),new TargetIntent.FieldValue.KeepObserved())),List.of(new TargetIntent.Containment("contains",parent,old("one"))));
        var placement=new PlanPorts.Placement(old("one"),"right","items-right",new PlanPorts.Parent.Existing("right",digest(right),1));
        var raw=target(model,original,new PlanPorts.Draft(choices,List.of(placement)));
        var result=assertInstanceOf(V3PlanContent.Result.Complete.class,raw,raw.toString()).content();
        String expectedLeft="<items><box id='a'><!-- left --></box></items>";
        String expectedRight="<items><box id='b'><item id='two' tone='alpha' finish='x'/><item id='one' tone='al&#112;ha' finish='x' xmlns=\"\"/></box></items>";
        assertEquals(expectedLeft,result.sources().getFirst().xml());assertEquals(expectedRight,result.sources().getLast().xml());
        assertEquals(List.of(new PlanPorts.Source("left",expectedLeft,digest(expectedLeft)),new PlanPorts.Source("right",expectedRight,digest(expectedRight))),result.sources());
        var proof=(PlanContentEvidence.V3Target)result.evidence();
        var typed=proof.preliminary().entities().stream().filter(e->e.reference().equals(new DerivedInput.Ref.Target(old("one")))).findFirst().orElseThrow();
        var kept=((DerivedInput.Proof.Target)((DerivedInput.FieldState.Present)typed.fields().get("tone")).proof()).kept().orElseThrow();
        assertEquals("left",kept.source().origin().documentId());assertEquals(2,kept.source().origin().elementIndex());assertEquals(digest(left),kept.location().value().sourceDigest());
        var actual=proof.input().entities().stream().filter(e->((DerivedInput.Ref.Observed)e.reference()).key().equals(old("one").key())).findFirst().orElseThrow();
        var finalRef=(DerivedInput.Ref.Observed)actual.reference();
        assertEquals("right",finalRef.origin().documentId());assertEquals(3,finalRef.origin().elementIndex());assertEquals("items-right",finalRef.origin().projectionId());
        var location=((DerivedInput.Proof.Observed)((DerivedInput.FieldState.Present)actual.fields().get("tone")).proof()).location();
        assertEquals(digest(expectedRight),location.value().sourceDigest());assertEquals("al&#112;ha",expectedRight.substring(location.value().valueStart(),location.value().valueEnd()));
        assertEquals(original.provenance(),result.provenance());assertEquals(1,proof.derived().graph().cooccurrences().size());assertEquals(2,proof.derived().graph().cooccurrences().getFirst().contributors().size());
    }
    static String manyItems(int count) {
        var xml=new StringBuilder("<items>");for(int i=0;i<count;i++)xml.append("<item id='").append(i).append("' tone='a' finish='b'/>");return xml.append("</items>").toString();
    }
    @Test void physicalAndComputedNodesConsumeOneLimitWithoutTruncatedProofs() {
        var model=new PlanDefinition.V3(definition(false));String within=manyItems(19_998);
        var content=project(model,Map.of("sheet",within));var proof=(PlanContentEvidence.V3Observed)content.evidence();
        assertEquals(19_998,content.graph().entities().size());assertEquals(2,proof.derived().graph().nodes().size());
        assertEquals(39_996,proof.derived().graph().memberships().size());assertEquals(19_998,proof.derived().graph().cooccurrences().getFirst().contributors().size());
        assertEquals(new V3PlanContent.Result.Refused("RESOURCE_LIMIT"),new V3PlanContentAdapter().project(model,"mock-pg",observed(model,Map.of("sheet",manyItems(19_999))),new Cancellation()));
        assertEquals(new V3PlanContent.Result.Refused("RESOURCE_LIMIT"),new V3PlanContentAdapter().project(model,"mock-pg",observed(model,Map.of("sheet"," ".repeat(1_048_577))),new Cancellation()));
    }
    @Test void cancellingOriginalControlDuringActualMaterializationCannotReturnTarget() throws Exception {
        var model=new PlanDefinition.V3(definition(false));var original=project(model,Map.of("sheet",manyItems(8_000)));
        var pin=((PlanContentEvidence.V3Observed)original.evidence()).input().pin();var next=new DerivedInput.Pin("cancel-target",pin.logicalDigest(),pin.bindingId(),pin.bindingDigest(),pin.documentDigests());
        var cancellation=new Cancellation();var outcome=new java.util.concurrent.atomic.AtomicReference<V3PlanContent.Result>();var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var worker=new Thread(()->{try{outcome.set(new V3PlanContentAdapter().materialize(model,pin,original,next,PlanPorts.Draft.empty(),cancellation));}catch(Throwable failure){error.set(failure);}},"invented-v3-materialization");
        boolean entered=false;worker.start();long end=System.nanoTime()+10_000_000_000L;
        try {
            while(worker.isAlive()&&System.nanoTime()<end) {
                if(Arrays.stream(worker.getStackTrace()).anyMatch(f->f.getClassName().equals(DerivedTargetMaterializer.class.getName()))){entered=true;break;}
                java.util.concurrent.locks.LockSupport.parkNanos(100_000);
            }
        }finally{cancellation.cancel();worker.join(10_000);}
        assertTrue(entered,"Actual materializer must be observed running before cancellation");assertFalse(worker.isAlive());assertNull(error.get());
        assertEquals(new V3PlanContent.Result.Refused("CANCELLED"),outcome.get());assertEquals(8_000,original.graph().entities().size());
    }
}
