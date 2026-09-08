package studio.environment.core.definitionv2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import static studio.environment.core.definition.DefinitionDiagnostic.Phase.*;
import static studio.environment.core.definitionv2.NativeDefinition.*;

/** Compiles shape-checked declarations, never observed instances or workspace authority. */
public final class NativeDefinitionCompiler {
    public NativeCompilationResult compile(NativeDefinition definition) {
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        Logical logical = definition.logical();
        unique(logical.entityTypes(), EntityType::id, "/logical/entityTypes", diagnostics);
        unique(logical.relations(), Relation::id, "/logical/relations", diagnostics);
        unique(logical.rules(), CountRule::id, "/logical/rules", diagnostics);
        unique(definition.bindings(), Binding::id, "/bindings", diagnostics);
        Map<String, EntityType> types = new LinkedHashMap<>();
        for (int i = 0; i < logical.entityTypes().size(); i++) {
            EntityType type = logical.entityTypes().get(i);
            types.putIfAbsent(type.id(), type);
            String path = "/logical/entityTypes/" + i;
            unique(type.fields(), Field::id, path + "/fields", diagnostics);
            Field identity = type.fields().stream().filter(field -> field.id().equals(type.identity().field())).findFirst().orElse(null);
            if (identity == null || !identity.required() || !identity.readable() || identity.valueType() != ValueType.TEXT || identity.sensitivity() == Sensitivity.SECRET)
                error("INVALID_IDENTITY", path + "/identity", "Select a required readable non-secret text field as identity.", diagnostics);
            for (int j = 0; j < type.fields().size(); j++) {
                Field field = type.fields().get(j);
                if (!field.readable()) incomplete("FIELD_UNREADABLE", path + "/fields/" + j + "/readable", "Declare a supported readable field strategy before publication.", diagnostics);
                if (field.sensitivity() == Sensitivity.UNKNOWN) incomplete("SENSITIVITY_UNKNOWN", path + "/fields/" + j + "/sensitivity", "Declare field sensitivity before publication.", diagnostics);
            }
        }
        Map<String, Relation> relations = new LinkedHashMap<>();
        for (int i = 0; i < logical.relations().size(); i++) {
            Relation relation = logical.relations().get(i); relations.putIfAbsent(relation.id(), relation);
            String path = "/logical/relations/" + i;
            if (!types.containsKey(relation.fromType())) unknownType(path + "/fromType", diagnostics);
            if (!types.containsKey(relation.toType())) unknownType(path + "/toType", diagnostics);
            if (relation.minimum().compareTo(relation.maximum()) > 0) inverted(path + "/minimum", diagnostics);
            if (relation.kind() == RelationKind.REFERENCE && !relation.maximum().equals(BigInteger.ONE))
                incomplete("REFERENCE_CODEC_UNSUPPORTED", path + "/maximum", "Select a qualified single-target reference mechanism before publication.", diagnostics);
        }
        for (int i = 0; i < logical.rules().size(); i++) {
            CountRule rule = logical.rules().get(i);
            if (!types.containsKey(rule.type())) unknownType("/logical/rules/" + i + "/type", diagnostics);
            if (rule.minimum().compareTo(rule.maximum()) > 0) inverted("/logical/rules/" + i + "/minimum", diagnostics);
        }
        for (int i = 0; i < definition.bindings().size(); i++)
            binding(definition.bindings().get(i), "/bindings/" + i, types, relations, logical.operationCapabilities(), diagnostics);
        List<DefinitionDiagnostic> errors = diagnostics.stream().filter(diagnostic -> diagnostic.phase() != PUBLICATION).toList();
        if (!errors.isEmpty()) return new NativeCompilationResult.Rejected(errors);
        NativeCompilationResult.Checked checked = NativeDigests.checked(definition);
        return diagnostics.isEmpty() ? new NativeCompilationResult.ReadyToPublish(checked) : new NativeCompilationResult.Incomplete(checked, diagnostics);
    }
    private record Located(Projection projection, int documentIndex, String path) { }
    private static void binding(Binding binding, String path, Map<String, EntityType> types,
            Map<String, Relation> relations, List<Operation> operations, List<DefinitionDiagnostic> diagnostics) {
        if (binding.engine() == Engine.POSTGRESQL && binding.storage() != Storage.TEXT || binding.engine() == Engine.ORACLE && binding.storage() != Storage.CLOB)
            error("ENGINE_STORAGE_MISMATCH", path + "/storage", "Select the storage kind paired with the declared engine.", diagnostics);
        if (binding.keyColumn().equals(binding.xmlColumn())) error("COLUMN_COLLISION", path + "/xmlColumn", "Declare distinct key and XML columns.", diagnostics);
        unique(binding.documents(), Document::id, path + "/documents", diagnostics);
        Set<String> keys = new HashSet<>();
        Set<String> projectionIds = new HashSet<>();
        Map<String, List<Located>> projectionsByType = new HashMap<>();
        for (int i = 0; i < binding.documents().size(); i++) {
            Document document = binding.documents().get(i);
            String documentPath = path + "/documents/" + i;
            if (!NativeLexicalRules.validKey(binding.keyType(), document.key()))
                error("INVALID_DOCUMENT_KEY", documentPath + "/key", "Supply an exact key matching the declared key type and bounds.", diagnostics);
            if (!keys.add(document.key())) error("DUPLICATE_DOCUMENT_KEY", documentPath + "/key", "Use one unique typed key per document in this binding.", diagnostics);
            Set<List<ExpandedName>> paths = new HashSet<>();
            for (int j = 0; j < document.entities().size(); j++) {
                Projection projection = document.entities().get(j);
                String projectionPath = documentPath + "/entities/" + j;
                if (!projectionIds.add(projection.id())) error("DUPLICATE_ID", projectionPath + "/id", "Use a unique identifier within this declaration collection.", diagnostics);
                if (!paths.add(projection.path())) error("OVERLAPPING_PROJECTIONS", projectionPath + "/path", "Select disjoint entity occurrences for each projection.", diagnostics);
                projectionsByType.computeIfAbsent(projection.type(), ignored -> new ArrayList<>()).add(new Located(projection, i, projectionPath));
                projection(projection, projectionPath, types, relations, operations.contains(Operation.CREATE_ENTITY), diagnostics);
                if (projection.path().size() == 1 && (operations.contains(Operation.CREATE_ENTITY) || operations.contains(Operation.REMOVE_ENTITY)))
                    incomplete("ROOT_OPERATION_UNSUPPORTED", projectionPath + "/path", "Use non-root projections for declared creation and removal capabilities.", diagnostics);
            }
        }
        for (String type : types.keySet()) if (!projectionsByType.containsKey(type))
            incomplete("TYPE_PROJECTION_MISSING", path + "/documents", "Declare a canonical projection for every logical entity type.", diagnostics);
        for (Relation relation : relations.values()) {
            if (relation.kind() != RelationKind.CONTAINMENT) continue;
            List<Located> sources = projectionsByType.getOrDefault(relation.fromType(), List.of());
            List<Located> targets = projectionsByType.getOrDefault(relation.toType(), List.of());
            for (Located target : targets) {
                long compatible = sources.stream().filter(source -> source.documentIndex == target.documentIndex
                        && properPrefix(source.projection.path(), target.projection.path())).count();
                if (compatible != 1) incomplete("CONTAINMENT_BINDING_INCOMPLETE", target.path + "/path",
                        "Declare exactly one compatible source projection ancestor in this document.", diagnostics);
            }
        }
    }
    private static void projection(Projection projection, String path, Map<String, EntityType> types,
            Map<String, Relation> relations, boolean create, List<DefinitionDiagnostic> diagnostics) {
        if (projection.path().size() > 128)
            incomplete("XML_DEPTH_UNSUPPORTED", path + "/path", "Select a path within the XML mechanism depth limit of 128.", diagnostics);
        for (int i = 0; i < projection.path().size(); i++)
            name(projection.path().get(i), false, path + "/path/" + i, diagnostics);
        EntityType type = types.get(projection.type());
        if (type == null) unknownType(path + "/type", diagnostics);
        Map<String, Field> fields = new HashMap<>();
        if (type != null) type.fields().forEach(field -> fields.putIfAbsent(field.id(), field));
        Set<String> mappedFields = new HashSet<>();
        Set<ExpandedName> attributes = new HashSet<>();
        Set<ExpandedName> requiredAttributes = new HashSet<>();
        for (int i = 0; i < projection.fields().size(); i++) {
            FieldMapping mapping = projection.fields().get(i);
            String mappingPath = path + "/fields/" + i;
            if (!mappedFields.add(mapping.field())) error("DUPLICATE_FIELD_MAPPING", mappingPath + "/field", "Map each field once within its entity projection.", diagnostics);
            if (type != null && !fields.containsKey(mapping.field())) error("UNKNOWN_FIELD", mappingPath + "/field", "Reference a field declared by the projection entity type.", diagnostics);
            attribute(mapping.attribute(), attributes, mappingPath + "/attribute", diagnostics);
            Field field = fields.get(mapping.field());
            if (field != null && field.required()) requiredAttributes.add(mapping.attribute());
        }
        for (Field field : fields.values()) if (field.readable() && !mappedFields.contains(field.id()))
            incomplete("FIELD_MAPPING_MISSING", path + "/fields", "Map every declared readable field before publication.", diagnostics);
        Set<String> mappedReferences = new HashSet<>();
        for (int i = 0; i < projection.references().size(); i++) {
            ReferenceMapping mapping = projection.references().get(i);
            String mappingPath = path + "/references/" + i;
            if (!mappedReferences.add(mapping.relation())) error("DUPLICATE_REFERENCE_MAPPING", mappingPath + "/relation", "Map each reference relation once within its source projection.", diagnostics);
            Relation relation = relations.get(mapping.relation());
            if (relation == null) error("UNKNOWN_RELATION", mappingPath + "/relation", "Reference a declared logical relation.", diagnostics);
            else if (relation.kind() != RelationKind.REFERENCE || !relation.fromType().equals(projection.type()))
                error("INVALID_REFERENCE_BINDING", mappingPath + "/relation", "Map a reference relation on its declared source entity type.", diagnostics);
            attribute(mapping.attribute(), attributes, mappingPath + "/attribute", diagnostics);
            if (relation != null && relation.kind() == RelationKind.REFERENCE && relation.fromType().equals(projection.type())
                    && relation.minimum().signum() > 0) requiredAttributes.add(mapping.attribute());
        }
        xmlAttributeBudget(projection, requiredAttributes, create, path, diagnostics);
        for (Relation relation : relations.values()) if (relation.kind() == RelationKind.REFERENCE && relation.fromType().equals(projection.type())
                && !mappedReferences.contains(relation.id()))
            incomplete("REFERENCE_MAPPING_MISSING", path + "/references", "Map every logical reference relation on each source projection.", diagnostics);
    }
    private static void xmlAttributeBudget(Projection projection, Set<ExpandedName> requiredAttributes,
            boolean create, String path, List<DefinitionDiagnostic> diagnostics) {
        // Namespace declarations count as attributes in xml-span-v1. Observed non-root
        // elements can inherit them; self-contained creation fragments and document roots cannot.
        Set<String> declarations = new HashSet<>();
        if (create || projection.path().size() == 1) {
            String elementNamespace = projection.path().getLast().namespaceUri();
            if (create || !elementNamespace.isEmpty()) declarations.add(elementNamespace);
            for (ExpandedName attribute : requiredAttributes)
                if (!attribute.namespaceUri().isEmpty()) declarations.add(attribute.namespaceUri());
            declarations.remove("http://www.w3.org/XML/1998/namespace"); // implicit xml prefix
        }
        if (requiredAttributes.size() + declarations.size() > 256)
            incomplete("XML_ATTRIBUTES_UNSUPPORTED", path + "/fields",
                    "Required attributes and namespace declarations exceed the XML mechanism limit of 256.", diagnostics);
    }
    private static boolean properPrefix(List<ExpandedName> prefix, List<ExpandedName> path) {
        return prefix.size() < path.size() && path.subList(0, prefix.size()).equals(prefix);
    }
    private static void attribute(ExpandedName name, Set<ExpandedName> attributes, String path, List<DefinitionDiagnostic> diagnostics) {
        name(name, true, path, diagnostics);
        if (!attributes.add(name)) error("ATTRIBUTE_COLLISION", path, "Use distinct attributes for field and reference mappings.", diagnostics);
    }
    private static void name(ExpandedName name, boolean attribute, String path, List<DefinitionDiagnostic> diagnostics) {
        if (!NativeLexicalRules.validName(name, attribute)) error("INVALID_XML_NAME", path, "Use a supported expanded XML name without namespace declaration mappings.", diagnostics);
    }
    private static <T> void unique(List<T> values, Function<T, String> id, String path, List<DefinitionDiagnostic> diagnostics) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.size(); i++) if (!ids.add(id.apply(values.get(i))))
            error("DUPLICATE_ID", path + "/" + i + "/id", "Use a unique identifier within this declaration collection.", diagnostics);
    }
    private static void unknownType(String path, List<DefinitionDiagnostic> diagnostics) { error("UNKNOWN_TYPE", path, "Reference a declared entity type.", diagnostics); }
    private static void inverted(String path, List<DefinitionDiagnostic> diagnostics) { error("CARDINALITY_INVERTED", path, "Set minimum cardinality no greater than maximum cardinality.", diagnostics); }
    private static void error(String code, String path, String message, List<DefinitionDiagnostic> diagnostics) { diagnostics.add(new DefinitionDiagnostic(SEMANTIC, code, path, message)); }
    private static void incomplete(String code, String path, String message, List<DefinitionDiagnostic> diagnostics) { diagnostics.add(new DefinitionDiagnostic(PUBLICATION, code, path, message)); }
}
