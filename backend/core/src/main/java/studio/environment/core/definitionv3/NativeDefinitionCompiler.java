package studio.environment.core.definitionv3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import static studio.environment.core.definitionv3.NativeDefinition.*;

/** Compiles shape-checked v3 declarations without publication or execution authority. */
public final class NativeDefinitionCompiler {
    public NativeCompilationResult compile(NativeDefinition definition) {
        var diagnostics = new ArrayList<DefinitionDiagnostic>();
        var logical = definition.logical();
        if (logical.computedTypes().size() > DerivedSemantics.MAX_DERIVATIONS
                || logical.derivations().size() > DerivedSemantics.MAX_DERIVATIONS
                || logical.cooccurrences().size() > DerivedSemantics.MAX_COOCCURRENCES) {
            error("RESOURCE_LIMIT", "/logical", "Use no more than the declared v3 computation limits.", diagnostics);
            return new NativeCompilationResult.Rejected(diagnostics);
        }
        var physicalTypes = new LinkedHashMap<String, studio.environment.core.definitionv2.NativeDefinition.EntityType>();
        logical.entityTypes().forEach(type -> physicalTypes.putIfAbsent(type.id(), type));
        var computedTypes = unique(logical.computedTypes(), ComputedType::id, "/logical/computedTypes", diagnostics);
        var derivations = unique(logical.derivations(), Derivation::id, "/logical/derivations", diagnostics);
        unique(logical.cooccurrences(), Cooccurrence::id, "/logical/cooccurrences", diagnostics);
        unique(logical.computedRules(), studio.environment.core.definitionv2.NativeDefinition.CountRule::id, "/logical/computedRules", diagnostics);
        for (int i = 0; i < logical.computedTypes().size(); i++)
            if (physicalTypes.containsKey(logical.computedTypes().get(i).id()))
                error("TYPE_ID_COLLISION", "/logical/computedTypes/" + i + "/id", "Use disjoint physical and computed type IDs.", diagnostics);
        Set<String> relationIds = new HashSet<>();
        logical.relations().forEach(relation -> relationIds.add(relation.id()));
        Set<String> assigned = new HashSet<>();
        for (int i = 0; i < logical.derivations().size(); i++) {
            var derivation = logical.derivations().get(i);
            String path = "/logical/derivations/" + i;
            if (!computedTypes.containsKey(derivation.computedType()))
                error("UNKNOWN_COMPUTED_TYPE", path + "/computedType", "Reference a declared computed type.", diagnostics);
            else if (!assigned.add(derivation.computedType()))
                error("COMPUTED_TYPE_MULTIPLE_DERIVATIONS", path + "/computedType", "Declare exactly one derivation for each computed type.", diagnostics);
            var source = physicalTypes.get(derivation.sourceType());
            if (source == null) error("UNKNOWN_TYPE", path + "/sourceType", "Reference a declared physical source type.", diagnostics);
            else {
                var field = source.fields().stream().filter(f -> f.id().equals(derivation.sourceField())).findFirst();
                if (field.isEmpty()) error("UNKNOWN_FIELD", path + "/sourceField", "Reference a field of the declared physical source type.", diagnostics);
                else if (!field.get().readable() || field.get().sensitivity() != Sensitivity.PUBLIC || field.get().valueType() != ValueType.TEXT)
                    error("DERIVATION_INPUT_INELIGIBLE", path + "/sourceField", "Use an explicitly public readable text field as the derivation input.", diagnostics);
            }
            relationId(relationIds, derivation.membershipRelation(), path + "/membershipRelation", diagnostics);
        }
        for (int i = 0; i < logical.computedTypes().size(); i++)
            if (!assigned.contains(logical.computedTypes().get(i).id()))
                error("DERIVATION_MISSING", "/logical/computedTypes/" + i, "Declare exactly one derivation for each computed type.", diagnostics);
        for (int i = 0; i < logical.cooccurrences().size(); i++) {
            var relation = logical.cooccurrences().get(i);
            String path = "/logical/cooccurrences/" + i;
            relationId(relationIds, relation.id(), path + "/id", diagnostics);
            var from = derivations.get(relation.fromDerivation()); var to = derivations.get(relation.toDerivation());
            if (from == null) error("UNKNOWN_DERIVATION", path + "/fromDerivation", "Reference a declared derivation.", diagnostics);
            if (to == null) error("UNKNOWN_DERIVATION", path + "/toDerivation", "Reference a declared derivation.", diagnostics);
            if (from != null && to != null && !from.sourceType().equals(to.sourceType()))
                error("COOCCURRENCE_SOURCE_MISMATCH", path, "Select derivations on the same physical source type.", diagnostics);
            cardinality(relation.minimum(), relation.maximum(), path, diagnostics);
        }
        Set<String> ruleIds = new HashSet<>();
        logical.rules().forEach(rule -> ruleIds.add(rule.id()));
        for (int i = 0; i < logical.computedRules().size(); i++) {
            var rule = logical.computedRules().get(i);
            String path = "/logical/computedRules/" + i;
            if (!ruleIds.add(rule.id())) error("RULE_ID_COLLISION", path + "/id", "Use unique rule IDs across physical and computed rules.", diagnostics);
            if (!computedTypes.containsKey(rule.type())) error("UNKNOWN_COMPUTED_TYPE", path + "/type", "Reference a declared computed type.", diagnostics);
            cardinality(rule.minimum(), rule.maximum(), path, diagnostics);
        }
        // Reuse the frozen physical checks, never the v2 digest or readiness result as v3 authority.
        var physical = new studio.environment.core.definitionv2.NativeDefinition(definition.id(), definition.revision(),
                new studio.environment.core.definitionv2.NativeDefinition.Logical(logical.entityTypes(), logical.relations(), logical.rules(), logical.operationCapabilities()),
                definition.bindings());
        var physicalResult = new studio.environment.core.definitionv2.NativeDefinitionCompiler().compile(physical);
        diagnostics.addAll(physicalResult.diagnostics());
        var errors = diagnostics.stream().filter(d -> d.phase() != DefinitionDiagnostic.Phase.PUBLICATION).toList();
        if (!errors.isEmpty()) return new NativeCompilationResult.Rejected(errors);
        diagnostics.add(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PUBLICATION, "MECHANISM_UNQUALIFIED", "",
                "V3 requires complete server qualification before publication."));
        return new NativeCompilationResult.Incomplete(NativeDigests.checked(definition), diagnostics);
    }
    private static <T> Map<String, T> unique(List<T> items, Function<T, String> identifier, String path, List<DefinitionDiagnostic> diagnostics) {
        Map<String, T> result = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) if (result.putIfAbsent(identifier.apply(items.get(i)), items.get(i)) != null)
            error("DUPLICATE_ID", path + "/" + i + "/id", "Use a unique identifier within this declaration collection.", diagnostics);
        return result;
    }
    private static void relationId(Set<String> ids, String id, String path, List<DefinitionDiagnostic> diagnostics) {
        if (!ids.add(id)) error("RELATION_ID_COLLISION", path, "Use unique relation IDs across physical, membership and co-occurrence declarations.", diagnostics);
    }
    private static void cardinality(java.math.BigInteger minimum, java.math.BigInteger maximum, String path, List<DefinitionDiagnostic> diagnostics) {
        if (minimum.signum() < 0 || maximum.signum() <= 0)
            error("INVALID_CARDINALITY", path, "Use a nonnegative minimum and positive maximum.", diagnostics);
        else if (minimum.compareTo(maximum) > 0)
            error("CARDINALITY_INVERTED", path + "/minimum", "Set minimum cardinality no greater than maximum cardinality.", diagnostics);
    }
    private static void error(String code, String path, String message, List<DefinitionDiagnostic> diagnostics) {
        diagnostics.add(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SEMANTIC, code, path, message));
    }
}
