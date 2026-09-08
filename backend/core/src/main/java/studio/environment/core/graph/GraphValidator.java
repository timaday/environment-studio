package studio.environment.core.graph;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definitionv2.NativeDefinition.EntityType;
import studio.environment.core.definitionv2.NativeDefinition.Logical;
import studio.environment.core.definitionv2.NativeLexicalRules;
import static studio.environment.core.graph.ObservedGraph.*;

/** Required graph checks over a complete, bounded lexical projection; no database authority. */
public final class GraphValidator {
    private static final Comparator<Key> KEYS = Comparator.comparing(Key::type).thenComparing(Key::identity);
    private record Position(String document, int element) { }
    public GraphValidationResult validate(Logical logical, List<Occurrence> occurrences) {
        if (occurrences.size() > MAX_ENTITIES) return rejected("RESOURCE_LIMIT");
        Map<String, EntityType> types = new HashMap<>();
        logical.entityTypes().forEach(type -> types.put(type.id(), type));
        Map<String, Relation> relations = new HashMap<>();
        logical.relations().forEach(relation -> relations.put(relation.id(), relation));
        Map<Key, Entity> entities = new HashMap<>();
        Map<Position, Entity> positions = new HashMap<>();
        Map<Key, Occurrence> inputs = new HashMap<>();
        List<GraphDiagnostic> diagnostics = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            EntityType type = types.get(occurrence.type());
            if (type == null) { issue(diagnostics, "UNKNOWN_TYPE", occurrence.origin(), ""); continue; }
            Set<String> fields = new HashSet<>();
            for (var field : type.fields()) {
                fields.add(field.id());
                String value = occurrence.fields().get(field.id());
                if (value == null && field.required()) issue(diagnostics, "REQUIRED_FIELD_MISSING", occurrence.origin(), field.id());
                if (value != null && !NativeLexicalRules.validValue(field.valueType(), value))
                    issue(diagnostics, "INVALID_SCALAR", occurrence.origin(), field.id());
            }
            if (!fields.containsAll(occurrence.fields().keySet())) issue(diagnostics, "UNKNOWN_FIELD", occurrence.origin(), "");
            String identity = occurrence.fields().get(type.identity().field());
            if (!NativeLexicalRules.validIdentity(identity)) {
                issue(diagnostics, "INVALID_IDENTITY", occurrence.origin(), type.identity().field()); continue;
            }
            Key key = new Key(type.id(), identity);
            Entity entity = new Entity(key, occurrence.fields(), occurrence.origin());
            if (entities.putIfAbsent(key, entity) != null) issue(diagnostics, "DUPLICATE_IDENTITY", occurrence.origin(), type.identity().field());
            if (positions.putIfAbsent(new Position(occurrence.origin().documentId(), occurrence.origin().elementIndex()), entity) != null)
                issue(diagnostics, "AMBIGUOUS_PROJECTION", occurrence.origin(), "");
            inputs.putIfAbsent(key, occurrence);
        }
        // No first-match identity or location is allowed to participate in a successful graph.
        if (!diagnostics.isEmpty()) return new GraphValidationResult.Rejected(diagnostics);
        List<Edge> edges = new ArrayList<>();
        Map<Key, Key> parents = new HashMap<>();
        for (Entity entity : entities.values()) {
            Occurrence occurrence = inputs.get(entity.key());
            for (var reference : occurrence.references().entrySet()) {
                Relation relation = relations.get(reference.getKey());
                if (relation == null || relation.kind() != RelationKind.REFERENCE || !relation.fromType().equals(entity.key().type())) {
                    issue(diagnostics, "INVALID_REFERENCE_MAPPING", entity.origin(), ""); continue;
                }
                Key target = new Key(relation.toType(), reference.getValue());
                if (!NativeLexicalRules.validIdentity(reference.getValue()) || !entities.containsKey(target)) {
                    issue(diagnostics, "UNRESOLVED_REFERENCE", entity.origin(), relation.id()); continue;
                }
                if (edges.size() == MAX_EDGES) return rejected("RESOURCE_LIMIT");
                edges.add(new Edge(relation.id(), entity.key(), target));
            }
        }
        for (Relation relation : logical.relations()) {
            if (relation.kind() != RelationKind.CONTAINMENT) continue;
            for (Entity target : entities.values()) {
                if (!target.key().type().equals(relation.toType())) continue;
                Entity parent = null;
                List<Integer> ancestors = target.origin().ancestry();
                for (int i = ancestors.size() - 1; i >= 0; i--) {
                    Entity candidate = positions.get(new Position(target.origin().documentId(), ancestors.get(i)));
                    if (candidate != null && candidate.key().type().equals(relation.fromType())) { parent = candidate; break; }
                }
                if (parent == null) { issue(diagnostics, "CONTAINMENT_PARENT_MISSING", target.origin(), relation.id()); continue; }
                Key previous = parents.putIfAbsent(target.key(), parent.key());
                if (previous != null && !previous.equals(parent.key())) issue(diagnostics, "MULTIPLE_CONTAINMENT_PARENTS", target.origin(), relation.id());
                if (edges.size() == MAX_EDGES) return rejected("RESOURCE_LIMIT");
                edges.add(new Edge(relation.id(), parent.key(), target.key()));
            }
        }
        Map<Key, Map<String, Integer>> outgoing = new HashMap<>();
        for (Edge edge : edges) outgoing.computeIfAbsent(edge.source(), ignored -> new HashMap<>()).merge(edge.relation(), 1, Integer::sum);
        for (Relation relation : logical.relations()) for (Entity entity : entities.values()) {
            if (!entity.key().type().equals(relation.fromType())) continue;
            BigInteger count = BigInteger.valueOf(outgoing.getOrDefault(entity.key(), Map.of()).getOrDefault(relation.id(), 0));
            if (count.compareTo(relation.minimum()) < 0 || count.compareTo(relation.maximum()) > 0)
                issue(diagnostics, "RELATION_CARDINALITY", entity.origin(), relation.id());
        }
        if (cyclic(parents)) globalIssue(diagnostics, "CONTAINMENT_CYCLE", "");
        Map<String, Integer> counts = new HashMap<>();
        entities.keySet().forEach(key -> counts.merge(key.type(), 1, Integer::sum));
        for (var rule : logical.rules()) {
            BigInteger count = BigInteger.valueOf(counts.getOrDefault(rule.type(), 0));
            if (count.compareTo(rule.minimum()) < 0 || count.compareTo(rule.maximum()) > 0)
                globalIssue(diagnostics, "ENTITY_COUNT", rule.id());
        }
        if (!diagnostics.isEmpty()) return new GraphValidationResult.Rejected(diagnostics);
        return new GraphValidationResult.Accepted(new ObservedGraph(entities.values().stream().sorted(Comparator.comparing(Entity::key, KEYS)).toList(),
                edges.stream().sorted(Comparator.comparing(Edge::relation).thenComparing(Edge::source, KEYS).thenComparing(Edge::target, KEYS)).toList()));
    }
    private static boolean cyclic(Map<Key, Key> parents) {
        Set<Key> complete = new HashSet<>();
        for (Key start : parents.keySet()) {
            Set<Key> path = new HashSet<>();
            Key next = start;
            while (next != null && !complete.contains(next)) {
                if (!path.add(next)) return true;
                next = parents.get(next);
            }
            complete.addAll(path);
        }
        return false;
    }
    private static void issue(List<GraphDiagnostic> diagnostics, String code, Origin origin, String declaration) {
        if (diagnostics.size() < 256) diagnostics.add(new GraphDiagnostic(code, origin.documentId(), origin.projectionId(), declaration));
    }
    private static void globalIssue(List<GraphDiagnostic> diagnostics, String code, String declaration) {
        if (diagnostics.size() < 256) diagnostics.add(new GraphDiagnostic(code, "", "", declaration));
    }
    private static GraphValidationResult rejected(String code) {
        return new GraphValidationResult.Rejected(List.of(new GraphDiagnostic(code, "", "", "")));
    }
}
