package studio.environment.core.derived;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import studio.environment.core.Outcome;

public sealed interface DerivedResult permits DerivedResult.Complete, DerivedResult.Incomplete, DerivedResult.Refused {
    record Complete(ComputedGraph graph, List<RuleCheck> rules) implements DerivedResult {
        public Complete { Objects.requireNonNull(graph); rules = List.copyOf(rules); }
        @Override public String toString() { return "DerivedComplete[redacted]"; }
    }
    record Incomplete(List<String> derivations) implements DerivedResult {
        public Incomplete { derivations = derivations.stream().distinct().sorted().toList(); }
    }
    record Refused(String code) implements DerivedResult { public Refused { Objects.requireNonNull(code); } }
    enum RuleKind { ENTITY_COUNT, COOCCURRENCE }
    record RuleCheck(RuleKind kind, String declaration, Optional<ComputedGraph.Key> source,
            BigInteger actual, BigInteger minimum, BigInteger maximum, Outcome outcome) {
        public RuleCheck {
            Objects.requireNonNull(kind); Objects.requireNonNull(declaration); Objects.requireNonNull(source);
            Objects.requireNonNull(actual); Objects.requireNonNull(minimum); Objects.requireNonNull(maximum);
            if (outcome != Outcome.PASS && outcome != Outcome.FAIL) throw new IllegalArgumentException("A complete rule requires an outcome.");
        }
        @Override public String toString() { return "DerivedRuleCheck[redacted]"; }
    }
}
