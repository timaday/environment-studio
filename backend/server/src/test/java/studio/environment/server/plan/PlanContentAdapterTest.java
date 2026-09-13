package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.definition.*;
import studio.environment.server.xml.*;

class PlanContentAdapterTest {
    final PlanContentAdapter adapter=new PlanContentAdapter();
    PublishedDefinition definition() throws Exception {
        var ready=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionBytesCompiler().compile(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")),DefinitionBytesCompiler.Format.JSON));
        return new PublishedDefinition(new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2"),"independent-publication",ready,List.of());
    }
    ObservationResult.Observation observation(PublishedDefinition definition) throws Exception {
        var result=new ArrayList<ObservationResult.Document>();
        for(var pair:List.of(List.of("glyph-sheet","glyphs.xml"),List.of("palette-sheet","palettes.xml"))) {
            String source=Files.readString(Path.of("../../fixtures/structural-target",pair.get(1)));
            var xml=assertInstanceOf(XmlResult.Accepted.class,new LosslessXmlAdapter().project(source)).document();
            result.add(new ObservationResult.Document(pair.getFirst(),new ObservationResult.Key("INT64",Integer.toString(result.size()+1)),source,source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,source.length(),xml.digest()));
        }
        return new ObservationResult.Observation("independent-observation",definition.compiled().checked().logicalDigest(),definition.compiled().checked().bindingDigests().get("mock-pg"),result,Map.of());
    }
    @Test void completeTargetReprojectsBothDocumentsAndRetainsOriginalFreshProvenance() throws Exception {
        var definition=definition(); var current=assertInstanceOf(ContentResult.Complete.class,adapter.project(definition,"mock-pg",observation(definition))).content();
        var fresh=new TargetIntent.Ref.Fresh("new-palette","palette");
        var glyph=new TargetIntent.Ref.Existing(new ObservedGraph.Key("glyph","alpha"));
        var draft=new Draft(new TargetIntent(List.of(
                new TargetIntent.EntityDecision.Retain(glyph,Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.KeepObserved()),Map.of("uses",new TargetIntent.ReferenceValue.To(fresh))),
                new TargetIntent.EntityDecision.Create(fresh,Map.of("tag",new TargetIntent.FieldValue.Entered("second-palette"),"shade",new TargetIntent.FieldValue.Entered("cool & \t𐀀")),Map.of())),List.of()),
                List.of(new Placement(fresh,"palette-sheet","palettes",new Parent.Existing("palette-sheet",current.sources().stream().filter(source->source.documentId().equals("palette-sheet")).findFirst().orElseThrow().digest(),0))));
        var target=assertInstanceOf(ContentResult.Complete.class,adapter.materialize(definition,"mock-pg",current,draft)).content();
        assertEquals(Files.readString(Path.of("../../fixtures/structural-target/expected-glyphs.xml")),target.sources().getFirst().xml());
        assertEquals(Files.readString(Path.of("../../fixtures/structural-target/expected-palettes.xml")),target.sources().get(1).xml());
        assertEquals(fresh,target.provenance().get(new ObservedGraph.Key("palette","second-palette")));
        assertEquals(glyph,target.provenance().get(new ObservedGraph.Key("glyph","alpha")));
        assertFalse(target.toString().contains("cool"));
    }
    @Test void captureUsesPortableAdapterAndCarriesNoDonorValues() throws Exception {
        var definition=definition(); var current=assertInstanceOf(ContentResult.Complete.class,adapter.project(definition,"mock-pg",observation(definition))).content();
        var mappings=new ArrayList<ProfileCapture.SlotMapping>();
        for(int index=0;index<current.graph().entities().size();index++) mappings.add(new ProfileCapture.SlotMapping(current.graph().entities().get(index).key(),"neutral-"+index,"Invented neutral slot "+index));
        var captured=adapter.capture(definition,"mock-pg",current,new ProfileCapture.Command("invented-capture",java.math.BigInteger.ONE,mappings));
        assertTrue(captured.source().contains("invented-capture"));
        assertFalse(captured.source().contains("alpha")); assertFalse(captured.source().contains("shared")); assertFalse(captured.source().contains("warm"));
        assertFalse(captured.toString().contains("neutral"));
    }
    @Test void rawPlaceholderAndFormattedViewsAreDistinctAndNeverReplaceUnmappedConcrete() throws Exception {
        var definition=definition(); var current=assertInstanceOf(ContentResult.Complete.class,adapter.project(definition,"mock-pg",observation(definition))).content();
        var source=current.sources().getFirst();var snapshot=new PlanViewProjectionTest().snapshot();
        var raw=adapter.compare(snapshot,false,source.documentId(),ViewMode.RAW);
        assertEquals(source.xml(),raw.text()); assertTrue(raw.exact());
        var placeholders=adapter.compare(snapshot,false,source.documentId(),ViewMode.PLACEHOLDERS);
        assertTrue(placeholders.text().contains("[[value:")); assertFalse(placeholders.text().contains("id='alpha'"));
        assertTrue(placeholders.unmappedConcreteMayRemain()); assertFalse(placeholders.exact());
        var formatted=adapter.compare(snapshot,false,source.documentId(),ViewMode.FORMATTED);
        assertTrue(formatted.text().contains("alpha")); assertFalse(formatted.exact());
        assertFalse(raw.toString().contains("alpha"));
    }
    @Test void staleObservationDigestAndIncompleteTargetRefuseWithoutPartialSources() throws Exception {
        var definition=definition(); var observed=observation(definition);
        var first=observed.documents().getFirst(); var documents=new ArrayList<>(observed.documents());
        documents.set(0,new ObservationResult.Document(first.documentId(),first.key(),first.xml(),first.utf8Bytes(),first.characters(),"0".repeat(64)));
        assertInstanceOf(ContentResult.Rejected.class,adapter.project(definition,"mock-pg",new ObservationResult.Observation(observed.fingerprint(),observed.logicalDigest(),observed.bindingDigest(),documents,Map.of())));
        var current=assertInstanceOf(ContentResult.Complete.class,adapter.project(definition,"mock-pg",observed)).content();
        var fresh=new TargetIntent.Ref.Fresh("missing-fields","palette");
        assertInstanceOf(ContentResult.Rejected.class,adapter.materialize(definition,"mock-pg",current,new Draft(new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(fresh,Map.of(),Map.of())),List.of()),List.of())));
    }
}
