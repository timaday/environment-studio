package studio.environment.core.definitionv3;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definitionv2.NativeDefinition.Binding;
import studio.environment.core.definitionv2.NativeDefinition.CountRule;
import studio.environment.core.definitionv2.NativeDefinition.EntityType;
import studio.environment.core.definitionv2.NativeDefinition.Operation;

/** Explicit v3 declarations. Shared physical value types retain their frozen v2 meaning. */
public record NativeDefinition(String id, BigInteger revision, Logical logical, List<Binding> bindings) {
    public NativeDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(revision); Objects.requireNonNull(logical);
        bindings = List.copyOf(bindings);
    }
    @Override public String toString() { return "NativeDefinitionV3[redacted]"; }

    public record Logical(List<EntityType> entityTypes, List<Relation> relations, List<CountRule> rules,
            List<Operation> operationCapabilities, List<ComputedType> computedTypes,
            List<Derivation> derivations, List<Cooccurrence> cooccurrences, List<CountRule> computedRules) {
        public Logical {
            entityTypes = List.copyOf(entityTypes); relations = List.copyOf(relations); rules = List.copyOf(rules);
            operationCapabilities = List.copyOf(operationCapabilities); computedTypes = List.copyOf(computedTypes);
            derivations = List.copyOf(derivations); cooccurrences = List.copyOf(cooccurrences); computedRules = List.copyOf(computedRules);
        }
        @Override public String toString() { return "LogicalV3[redacted]"; }
    }
    public record ComputedType(String id, String label) {
        public ComputedType { Objects.requireNonNull(id); Objects.requireNonNull(label); }
        @Override public String toString() { return "ComputedType[redacted]"; }
    }
    public record Derivation(String id, String sourceType, String sourceField, String computedType, String membershipRelation) {
        public Derivation {
            Objects.requireNonNull(id); Objects.requireNonNull(sourceType); Objects.requireNonNull(sourceField);
            Objects.requireNonNull(computedType); Objects.requireNonNull(membershipRelation);
        }
        @Override public String toString() { return "Derivation[redacted]"; }
    }
    public record Cooccurrence(String id, String fromDerivation, String toDerivation, BigInteger minimum, BigInteger maximum) {
        public Cooccurrence {
            Objects.requireNonNull(id); Objects.requireNonNull(fromDerivation); Objects.requireNonNull(toDerivation);
            Objects.requireNonNull(minimum); Objects.requireNonNull(maximum);
        }
        @Override public String toString() { return "Cooccurrence[redacted]"; }
    }
}
