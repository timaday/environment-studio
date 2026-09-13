package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.profile.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.definition.BoundedDocumentParser;
import studio.environment.server.planning.*;
import studio.environment.server.profile.ProfileBytesAdapter;
import studio.environment.server.projection.*;

/** Lead-authored acceptance controls using invented XML and independent target strings. */
class ChildPropertyReuseTest {
    @ParameterizedTest @CsvSource({"mock-pg,false", "mock-oracle,false", "mock-pg,true", "mock-oracle,true"})
    void wholeAndPartialReuseRequireFreshValuesAndPreserveUnselectedSiblings(String binding, boolean whole) throws Exception {
        var definition=ChildPropertyProjectionTest.definition(true);
        var projection=new GraphProjectionAdapter();var profiles=new ProfileBytesAdapter();
        var donor=assertInstanceOf(ProjectionResult.Accepted.class,projection.project(definition,binding,
            ChildPropertyProjectionTest.sources("<p:entry k:key='tone' value='donor-tone-canary'/>")));
        var alpha=new ObservedGraph.Key("glyph","alpha");var beta=new ObservedGraph.Key("glyph","beta");var shared=new ObservedGraph.Key("palette","shared");
        var captured=assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class,profiles.capture(definition,donor,
            new ProfileCapture.Command("invented-child-reuse",BigInteger.ONE,List.of(
                new ProfileCapture.SlotMapping(alpha,"first","First neutral slot"),new ProfileCapture.SlotMapping(beta,"second","Second neutral slot"),
                new ProfileCapture.SlotMapping(shared,"dependency","Neutral dependency")))));
        byte[] portable=assertInstanceOf(ProfileBytesAdapter.ExportResult.Encoded.class,profiles.write(definition,captured.checked())).bytes();
        String profileText=new String(portable,StandardCharsets.UTF_8);
        for(String forbidden:List.of("donor-tone-canary","alpha","beta","shared","childProperty","glyph-sheet","value="))assertFalse(profileText.contains(forbidden),forbidden);
        assertEquals(captured.checked(),assertInstanceOf(ProfileBytesAdapter.Result.Accepted.class,profiles.read(definition,portable,BoundedDocumentParser.Format.JSON)).checked());

        var sources=ChildPropertyProjectionTest.sources("<p:entry k:key='tone' value='target-original'/><!--keep-target-->");
        var current=assertInstanceOf(ProjectionResult.Accepted.class,projection.project(definition,binding,sources));
        Set<String> selected=whole?Set.of("first","second","dependency"):Set.of("first");
        var preview=assertInstanceOf(ProfileComposer.PreviewResult.Proposed.class,new ProfileComposer().preview(definition,captured.checked(),selected)).preview();
        assertEquals(whole?3:2,preview.included().size());
        if(!whole)assertEquals(List.of("dependency"),preview.dependencies().stream().map(ProfileComposer.Dependency::slot).toList());
        var decisions=new ArrayList<ProfileComposer.Decision>();
        if(whole){decisions.add(new ProfileComposer.Decision.UseExisting("first",alpha));decisions.add(new ProfileComposer.Decision.Create("second","fresh-glyph"));}
        else decisions.add(new ProfileComposer.Decision.Create("first","fresh-glyph"));
        decisions.add(new ProfileComposer.Decision.Create("dependency","fresh-palette"));
        var proposal=profiles.compose(definition,captured,preview,current,decisions);
        ProfileComposer.Draft composed;
        if(whole) {
            var conflict=assertInstanceOf(ProfileComposer.CompositionResult.NeedsResolution.class,proposal);
            assertEquals(List.of(new ProfileComposer.Conflict("RELATION_CARDINALITY","first","uses")),conflict.conflicts());
            composed=conflict.draft();
        } else composed=assertInstanceOf(ProfileComposer.CompositionResult.Prepared.class,proposal).draft();
        assertEquals(Set.of(alpha,beta,shared),new HashSet<>(composed.retainedExisting()));
        var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();current.graph().entities().forEach(e->provenance.put(e.key(),new TargetIntent.Ref.Existing(e.key())));
        var content=new PlanPorts.Content(current.projection().documents().stream().map(d->new PlanPorts.Source(d.documentId(),d.source(),d.digest())).toList(),current.graph(),provenance);
        var unresolved=PlanComposition.merge(definition,PlanPorts.Draft.empty(),content,composed);
        for(var decision:unresolved.intent().entities()) {
            var fields=decision instanceof TargetIntent.EntityDecision.Create c?c.fields():((TargetIntent.EntityDecision.Retain)decision).fields();
            assertTrue(fields.values().stream().allMatch(TargetIntent.FieldValue.Unresolved.class::isInstance));
        }
        var adapter=new StructuralTargetAdapter();
        var inputs=sources.stream().map(d->new TargetSource(d.documentId(),d.source(),Optional.empty())).toList();
        assertInstanceOf(MaterializationResult.Rejected.class,adapter.materialize(definition,binding,inputs,unresolved.intent(),List.of()));
        var resolved=new ArrayList<TargetIntent.EntityDecision>();var placements=new ArrayList<TargetPlacement>();
        for(var decision:unresolved.intent().entities()) {
            if(decision instanceof TargetIntent.EntityDecision.Create create) {
                boolean glyph=create.entity().type().equals("glyph");
                resolved.add(new TargetIntent.EntityDecision.Create(create.entity(),Map.of("tag",new TargetIntent.FieldValue.Entered(glyph?"gamma":"second-palette"),
                    glyph?"tone":"shade",new TargetIntent.FieldValue.Entered(glyph?"fresh & 𐀀":"cool")),create.references()));
                var doc=content.sources().stream().filter(d->d.documentId().equals(glyph?"glyph-sheet":"palette-sheet")).findFirst().orElseThrow();
                placements.add(new TargetPlacement(create.entity(),doc.documentId(),glyph?"glyphs":"palettes",new TargetPlacement.Parent.Existing(doc.documentId(),doc.digest(),0)));
            } else {
                var retained=(TargetIntent.EntityDecision.Retain)decision;
                resolved.add(new TargetIntent.EntityDecision.Retain(retained.entity(),Map.of("tag",new TargetIntent.FieldValue.KeepObserved(),"tone",new TargetIntent.FieldValue.Entered("chosen-target")),retained.references()));
            }
        }
        var intent=new TargetIntent(resolved,unresolved.intent().containment());
        var target=assertInstanceOf(MaterializationResult.Complete.class,adapter.materialize(definition,binding,inputs,intent,placements));
        String glyphs=sources.get(1).source();
        if(whole)glyphs=glyphs.replace("id='alpha' palette='shared'","id='alpha' palette='second-palette'").replace("value='target-original'","value='chosen-target'");
        String addition="<ns2:glyph xmlns:ns0=\"urn:mock:keys\" xmlns:ns1=\"urn:mock:properties\" xmlns:ns2=\"urn:mock:tiles\" id=\"gamma\" palette=\"second-palette\"><ns1:entry value=\"fresh &amp; 𐀀\" ns0:key=\"tone\"/></ns2:glyph>";
        glyphs=glyphs.replace("</tiles>",addition+"</tiles>");
        String palettes=sources.getFirst().source().replace("</tiles>","<ns0:palette xmlns:ns0=\"urn:mock:tiles\" id=\"second-palette\" shade=\"cool\"/></tiles>");
        Map<String,String> expected=Map.of("glyph-sheet",glyphs,"palette-sheet",palettes);
        for(var document:target.documents())assertArrayEquals(expected.get(document.documentId()).getBytes(StandardCharsets.UTF_8),document.source().getBytes(StandardCharsets.UTF_8));
        assertEquals(5,target.graph().entities().size());assertEquals(3,target.graph().edges().size());
        assertTrue(target.graph().edges().contains(new ObservedGraph.Edge("uses",beta,shared)));
        assertFalse(target.documents().toString().contains("donor-tone-canary"));
        assertEquals("",target.graph().entities().stream().filter(e->e.key().equals(beta)).findFirst().orElseThrow().fields().get("tone"));
        var stale=new ArrayList<>(placements);var first=stale.getFirst();
        stale.set(0,new TargetPlacement(first.entity(),first.documentId(),first.projectionId(),new TargetPlacement.Parent.Existing(first.documentId(),"0".repeat(64),0)));
        assertInstanceOf(MaterializationResult.Rejected.class,adapter.materialize(definition,binding,inputs,intent,stale));
    }
}
