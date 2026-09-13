package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.planning.TargetIntent.*;

/** Literal XML outcomes and actual final locations for independent mock items. */
class DerivedTargetMaterializerTest {
    static DerivedTargetMaterializer.Result materialize(String source, studio.environment.core.planning.TargetIntent intent, List<TargetPlacement> placements) {
        var definition = definition(false); var current = pin(definition, source, "current-1"); var target = pin(definition, source, "target-2");
        return new DerivedTargetMaterializer().materialize(definition, current, snapshot(current, source), target,
                new DerivedTargetInputAdapter.Decisions(target, intent), Map.of("sheet", Optional.empty()), placements, () -> false);
    }
    @Test void noOpRetainsExactSourcesAndMapsEveryPhysicalReferenceToActualFinalOrigin() {
        var result = assertInstanceOf(DerivedTargetMaterializer.Complete.class, materialize(XML, intent(), List.of()));
        assertEquals(XML, result.physical().documents().getFirst().source());
        assertEquals(3, result.provenance().size());
        assertEquals(1, result.provenance().get(old("one")).origin().elementIndex());
        assertEquals(2, result.provenance().get(old("two")).origin().elementIndex());
        assertEquals(3, result.provenance().get(old("three")).origin().elementIndex());
        assertEquals("target-2", result.finalProjection().input().pin().revisionToken());
        assertEquals(List.of("alpha:x", "alpha:y", "beta:x"), result.finalProjection().derived().graph().cooccurrences().stream().map(e -> e.source().value()+":"+e.target().value()).toList());
    }
    @Test void scalarEditReprojectsExactEscapingAndRetainsSeparateOriginalAndFinalPins() {
        var result = assertInstanceOf(DerivedTargetMaterializer.Complete.class, materialize(XML,
                intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Entered("beta & \t"))), List.of()));
        String expected = "<items><!-- mock -->\r\n<item id='one' tone='beta &amp; &#9;' finish='x'/>"
                + "<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";
        assertEquals(expected, result.physical().documents().getFirst().source());
        assertEquals(XML, result.preliminary().current().sources().documents().getFirst().source());
        assertEquals(digest(XML), result.preliminary().input().pin().documentDigests().get("sheet"));
        assertEquals(digest(expected), result.finalProjection().input().pin().documentDigests().get("sheet"));
        var ref = result.provenance().get(old("one"));
        var entity = result.finalProjection().input().entities().stream().filter(e -> e.reference().equals(ref)).findFirst().orElseThrow();
        var proof = assertInstanceOf(DerivedInput.Proof.Observed.class, assertInstanceOf(DerivedInput.FieldState.Present.class, entity.fields().get("tone")).proof());
        assertEquals("beta &amp; &#9;", expected.substring(proof.location().value().valueStart(), proof.location().value().valueEnd()));
    }
    @Test void freshContributorHasAnActualFinalOriginAndIdentityEditsNeverRebindOriginal() {
        var fresh = new Ref.Fresh("new-item", "item");
        var definition = definition(false); var current = pin(definition, XML, "current-1");
        var decisions = intent(edit("one", new FieldValue.Entered("renamed"), new FieldValue.KeepObserved()),
                new EntityDecision.Create(fresh, Map.of("id", new FieldValue.Entered("one"), "tone", new FieldValue.Entered("alpha"), "finish", new FieldValue.Entered("x")), Map.of()));
        var placement = new TargetPlacement(fresh, "sheet", "items", new TargetPlacement.Parent.Existing("sheet", current.documentDigests().get("sheet"), 0));
        var result = assertInstanceOf(DerivedTargetMaterializer.Complete.class, materialize(XML, decisions, List.of(placement)));
        assertEquals("renamed", result.provenance().get(old("one")).key().identity());
        assertEquals("one", result.provenance().get(fresh).key().identity());
        assertEquals(1, result.provenance().get(old("one")).origin().elementIndex()); assertEquals(4, result.provenance().get(fresh).origin().elementIndex());
        String expected = "<items><!-- mock -->\r\n<item id='renamed' tone='al&#112;ha' finish='x'/>"
                + "<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/>"
                + "<item xmlns=\"\" finish=\"x\" id=\"one\" tone=\"alpha\"/></items>";
        assertEquals(expected, result.physical().documents().getFirst().source());
        var alpha = result.finalProjection().derived().graph().nodes().stream().filter(n -> n.key().value().equals("alpha")).findFirst().orElseThrow();
        assertEquals(List.of("renamed", "two", "one"), alpha.contributors().stream().map(c -> ((DerivedInput.Ref.Observed)c.physical()).key().identity()).toList());
    }
}
