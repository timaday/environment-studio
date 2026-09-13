package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.planning.TargetIntent.*;

/** Independent invented XML witnesses; no final writer authority is claimed. */
class IndependentDerivedTargetInputTest {
    @Test void sameDecodedValueRetainsDistinctEscapedSourceProofsAfterRename() {
        String xml = "<items><!-- \uD800\uDC00 -->\r\n<item id='one' tone='a&#108;pha' finish='x'/>"
                + "<item id='two' tone='alpha' finish='x'/></items>";
        var complete = assertInstanceOf(DerivedTargetInputAdapter.Complete.class,
                prepare(definition(false), xml, intent(edit("one", new FieldValue.Entered("renamed"), new FieldValue.KeepObserved()))));
        var node = complete.derived().graph().nodes().stream().filter(n -> n.key().value().equals("alpha")).findFirst().orElseThrow();
        assertEquals(List.of(old("one"), old("two")), node.contributors().stream()
                .map(c -> ((DerivedInput.Ref.Target)c.physical()).reference()).toList());
        var first = (DerivedInput.Proof.Target)node.contributors().getFirst().roles().getFirst().proof();
        var second = (DerivedInput.Proof.Target)node.contributors().getLast().roles().getFirst().proof();
        var a = first.kept().orElseThrow(); var b = second.kept().orElseThrow();
        assertEquals(old("one").key(), a.source().key());
        assertEquals(old("two").key(), b.source().key());
        assertEquals("a&#108;pha", xml.substring(a.location().value().valueStart(), a.location().value().valueEnd()));
        assertEquals("alpha", xml.substring(b.location().value().valueStart(), b.location().value().valueEnd()));
        assertNotEquals(a.location(), b.location());
        assertThrows(UnsupportedOperationException.class, () -> complete.input().entities().clear());
        assertThrows(UnsupportedOperationException.class, () -> complete.input().entities().getFirst().fields().clear());
    }
    @Test void matchingDecodedReplacementCannotSubstituteForPinnedActualXml() {
        var d = definition(false); var current = pin(d, XML, "current-1"); var target = pin(d, XML, "target-2");
        String replacement = XML.replace("al&#112;ha", "alpha");
        assertEquals(new DerivedTargetInputAdapter.Refused("STALE_INPUT"), new DerivedTargetInputAdapter().prepare(
                d, current, snapshot(current, replacement), target,
                new DerivedTargetInputAdapter.Decisions(target, intent()), () -> false));
    }
    @Test void freshEnteredValueMatchingOldTextNeverAcquiresOldProof() {
        var fresh = new Ref.Fresh("fresh", "item");
        var complete = assertInstanceOf(DerivedTargetInputAdapter.Complete.class, prepare(definition(false), XML,
                intent(new EntityDecision.Create(fresh, Map.of("id", new FieldValue.Entered("fresh"),
                        "tone", new FieldValue.Entered("alpha"), "finish", new FieldValue.Entered("x")), Map.of()))));
        var entity = complete.input().entities().stream().filter(e -> e.reference().equals(new DerivedInput.Ref.Target(fresh))).findFirst().orElseThrow();
        var present = (DerivedInput.FieldState.Present)entity.fields().get("tone");
        var proof = (DerivedInput.Proof.Target)present.proof();
        assertEquals(new FieldValue.Entered("alpha"), proof.decision());
        assertTrue(proof.kept().isEmpty());
    }
}
