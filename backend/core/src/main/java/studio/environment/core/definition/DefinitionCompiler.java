package studio.environment.core.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import static studio.environment.core.definition.DefinitionDiagnostic.Phase.*;
import static studio.environment.core.definition.DefinitionDraft.*;

/** Checks declaration relationships; never grants publication or execution authority. */
public final class DefinitionCompiler {
    public DefinitionResult compile(DefinitionDraft draft) {
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        unique(draft.entityTypes(), EntityType::id, "/entityTypes", errors);
        unique(draft.relations(), Relation::id, "/relations", errors);
        unique(draft.documents(), Document::id, "/documents", errors);
        Map<String, Set<String>> fields = new HashMap<>();
        for (int i = 0; i < draft.entityTypes().size(); i++) {
            EntityType type = draft.entityTypes().get(i);
            unique(type.fields(), Field::id, "/entityTypes/" + i + "/fields", errors);
            fields.putIfAbsent(type.id(), new HashSet<>(type.fields().stream().map(Field::id).toList()));
        }
        for (int i = 0; i < draft.relations().size(); i++) {
            Relation relation = draft.relations().get(i);
            String path = "/relations/" + i;
            if (!fields.containsKey(relation.fromType())) unknownType(path + "/fromType", errors);
            if (!fields.containsKey(relation.toType())) unknownType(path + "/toType", errors);
            if (relation.minimum().compareTo(relation.maximum()) > 0)
                errors.add(new DefinitionDiagnostic(SEMANTIC, "CARDINALITY_INVERTED", path + "/minimum",
                        "Set minimum cardinality no greater than maximum cardinality."));
        }
        for (int i = 0; i < draft.documents().size(); i++) {
            List<Mapping> mappings = draft.documents().get(i).mappings();
            for (int j = 0; j < mappings.size(); j++) {
                Mapping mapping = mappings.get(j);
                String path = "/documents/" + i + "/mappings/" + j;
                if (!fields.containsKey(mapping.entityType())) unknownType(path + "/entityType", errors);
                else if (!fields.get(mapping.entityType()).contains(mapping.field()))
                    errors.add(new DefinitionDiagnostic(SEMANTIC, "UNKNOWN_FIELD", path + "/field",
                            "Reference a field declared by the mapping entity type."));
            }
        }
        if (!errors.isEmpty()) return new DefinitionResult.Rejected(errors);
        List<DefinitionDiagnostic> blockers = new ArrayList<>(List.of(
                blocker("IDENTITY_UNDECLARED", "", "Declare and qualify identity semantics before publication."),
                blocker("INVENTORY_UNDECLARED", "", "Declare and qualify complete inventory semantics before publication."),
                blocker("OPERATIONS_UNQUALIFIED", "", "Qualify the supported operations before publication."),
                blocker("SELECTORS_UNQUALIFIED", "", "Qualify selectors and record scope before publication."),
                blocker("WRITERS_UNQUALIFIED", "", "Qualify writer implementations before publication.")));
        for (int i = 0; i < draft.entityTypes().size(); i++) {
            List<Field> declaredFields = draft.entityTypes().get(i).fields();
            for (int j = 0; j < declaredFields.size(); j++)
                if (declaredFields.get(j).sensitivity() == Sensitivity.UNKNOWN)
                    blockers.add(blocker("SENSITIVITY_UNKNOWN", "/entityTypes/" + i + "/fields/" + j + "/sensitivity",
                            "Declare field sensitivity before publication."));
        }
        for (int i = 0; i < draft.requiredRules().size(); i++)
            blockers.add(blocker("RULE_UNBOUND", "/requiredRules/" + i,
                    "Bind and qualify the required rule implementation before publication."));
        return new DefinitionResult.Incomplete(draft, blockers);
    }
    private static DefinitionDiagnostic blocker(String code, String pointer, String message) {
        return new DefinitionDiagnostic(PUBLICATION, code, pointer, message);
    }
    private static void unknownType(String pointer, List<DefinitionDiagnostic> errors) {
        errors.add(new DefinitionDiagnostic(SEMANTIC, "UNKNOWN_TYPE", pointer, "Reference a declared entity type."));
    }
    private static <T> void unique(List<T> values, Function<T, String> id, String path,
            List<DefinitionDiagnostic> errors) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.size(); i++)
            if (!ids.add(id.apply(values.get(i))))
                errors.add(new DefinitionDiagnostic(SEMANTIC, "DUPLICATE_ID", path + "/" + i + "/id",
                        "Use a unique identifier within this declaration collection."));
    }
}
