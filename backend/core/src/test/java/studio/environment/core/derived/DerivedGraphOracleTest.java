package studio.environment.core.derived;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.environment.core.Outcome;
import studio.environment.core.planning.TargetIntent;
import static studio.environment.core.derived.ComputedGraph.*;

/** Independent literal expected partition; no expected groups or edges computed from the input. */
class DerivedGraphOracleTest {
    private static Key key(String type, String derivation, String value) { return new Key(type, derivation, value); }
    private static DerivedInput.Ref ref(String slot) { return new DerivedInput.Ref.Target(new TargetIntent.Ref.Fresh(slot, "item")); }
    private static FieldRole role(String field, String value) {
        return new FieldRole(field, new DerivedInput.Proof.Target(new TargetIntent.FieldValue.Entered(value), Optional.empty()));
    }
    private static Contributor contributor(String slot, FieldRole... roles) { return new Contributor(ref(slot), List.of(roles)); }
    @Test void completePartitionIncludesEveryTupleMembershipPairAndProvenanceRole() {
        var definition = DerivedGraphEngineTest.definition(); var pin = DerivedGraphEngineTest.pin(definition);
        var input = DerivedGraphEngineTest.input(definition, new String[][] {{"alpha", "x"}, {"alpha", "y"}, {"beta", "x"}, {"alpha", "x"}});
        var alpha = key("tones", "by-tone", "alpha"); var beta = key("tones", "by-tone", "beta");
        var x = key("finishes", "by-finish", "x"); var y = key("finishes", "by-finish", "y");
        var a0 = contributor("slot-0", role("tone", "alpha")); var a1 = contributor("slot-1", role("tone", "alpha"));
        var b2 = contributor("slot-2", role("tone", "beta")); var a3 = contributor("slot-3", role("tone", "alpha"));
        var x0 = contributor("slot-0", role("finish", "x")); var y1 = contributor("slot-1", role("finish", "y"));
        var x2 = contributor("slot-2", role("finish", "x")); var x3 = contributor("slot-3", role("finish", "x"));
        var expected = new ComputedGraph(pin, List.of(new Node(x, List.of(x0, x2, x3)), new Node(y, List.of(y1)),
                new Node(alpha, List.of(a0, a1, a3)), new Node(beta, List.of(b2))),
                List.of(new Membership("has-finish", ref("slot-0"), x, List.of(x0)),
                        new Membership("has-finish", ref("slot-1"), y, List.of(y1)),
                        new Membership("has-finish", ref("slot-2"), x, List.of(x2)),
                        new Membership("has-finish", ref("slot-3"), x, List.of(x3)),
                        new Membership("has-tone", ref("slot-0"), alpha, List.of(a0)),
                        new Membership("has-tone", ref("slot-1"), alpha, List.of(a1)),
                        new Membership("has-tone", ref("slot-2"), beta, List.of(b2)),
                        new Membership("has-tone", ref("slot-3"), alpha, List.of(a3))),
                List.of(new Cooccurrence("pair", alpha, x, List.of(
                                contributor("slot-0", role("tone", "alpha"), role("finish", "x")),
                                contributor("slot-3", role("tone", "alpha"), role("finish", "x")))),
                        new Cooccurrence("pair", alpha, y, List.of(contributor("slot-1", role("tone", "alpha"), role("finish", "y")))),
                        new Cooccurrence("pair", beta, x, List.of(contributor("slot-2", role("tone", "beta"), role("finish", "x"))))));
        var expectedRules = List.of(new DerivedResult.RuleCheck(DerivedResult.RuleKind.COOCCURRENCE, "pair", Optional.of(alpha),
                        BigInteger.TWO, BigInteger.ZERO, BigInteger.TEN, Outcome.PASS),
                new DerivedResult.RuleCheck(DerivedResult.RuleKind.COOCCURRENCE, "pair", Optional.of(beta),
                        BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN, Outcome.PASS),
                new DerivedResult.RuleCheck(DerivedResult.RuleKind.ENTITY_COUNT, "tone-count", Optional.empty(),
                        BigInteger.TWO, BigInteger.ZERO, BigInteger.TEN, Outcome.PASS));
        assertEquals(new DerivedResult.Complete(expected, expectedRules), new DerivedGraphEngine().evaluate(definition, pin, input, () -> false));
    }
    @Test void equalValuesInDifferentDerivationsRemainDistinctTuples() {
        var definition = DerivedGraphEngineTest.definition(); var pin = DerivedGraphEngineTest.pin(definition);
        var input = DerivedGraphEngineTest.input(definition, new String[][] {{"same", "same"}});
        var result = assertInstanceOf(DerivedResult.Complete.class, new DerivedGraphEngine().evaluate(definition, pin, input, () -> false));
        assertEquals(List.of(key("finishes", "by-finish", "same"), key("tones", "by-tone", "same")),
                result.graph().nodes().stream().map(Node::key).toList());
        assertEquals(key("tones", "by-tone", "same"), result.graph().cooccurrences().getFirst().source());
        assertEquals(key("finishes", "by-finish", "same"), result.graph().cooccurrences().getFirst().target());
    }
    @Test void equalTextIsChargedAgainForEachDistinctComputedTuple() {
        var definition = DerivedGraphEngineTest.definition(); var pin = DerivedGraphEngineTest.pin(definition);
        var values = new String[4096][2];
        for (int i = 0; i < values.length; i++) {
            String value = "x".repeat(1020) + String.format(java.util.Locale.ROOT, "%04d", i);
            values[i][0] = value; values[i][1] = value;
        }
        var exact = assertInstanceOf(DerivedResult.Complete.class, new DerivedGraphEngine().evaluate(definition, pin,
                DerivedGraphEngineTest.input(definition, values), () -> false));
        assertEquals(8192, exact.graph().nodes().size()); // 4096 distinct values times two declared derivations.
        values[0][0] += "x";
        assertEquals(new DerivedResult.Refused("RESOURCE_LIMIT"), new DerivedGraphEngine().evaluate(definition, pin,
                DerivedGraphEngineTest.input(definition, values), () -> false));
    }

}
