package studio.environment.core.planning;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv2.NativeLexicalRules;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;
import static studio.environment.core.planning.TargetIntent.*;

/** Shared physical semantics; version-specific facades establish definition metadata. */
final class PhysicalTargetIntentCompiler {
    private final Comparator<Ref> references;
    PhysicalTargetIntentCompiler(Comparator<Ref> references) { this.references = references; }
    private record ChildRelation(Ref child, String relation) { }
    TargetCompilationResult compile(NativeDefinition.Logical logical, GraphValidationResult.Accepted observation, TargetIntent intent) {
        if (logical == null || observation == null || intent == null) return TargetCompilationResult.reject("INVALID_INPUT");
        try { return new TargetCompilationResult.Expected(resolve(logical, observation.graph(), intent)); }
        catch (Refusal refused) { return TargetCompilationResult.reject(refused.code); }
    }
    private ExpectedTarget resolve(NativeDefinition.Logical logical, ObservedGraph graph, TargetIntent intent) {
        budget(intent);
        Map<String, NativeDefinition.EntityType> types = new HashMap<>(); logical.entityTypes().forEach(t -> types.put(t.id(), t));
        Map<String, Relation> relations = new HashMap<>(); logical.relations().forEach(r -> relations.put(r.id(), r));
        Map<Ref.Existing, ObservedGraph.Entity> original = new LinkedHashMap<>();
        for (var entity : graph.entities()) if (original.putIfAbsent(new Ref.Existing(entity.key()), entity) != null) fail("INVALID_OBSERVATION");
        Map<Ref, EntityDecision> decisions = new HashMap<>(); Set<String> slots = new HashSet<>();
        Set<Ref.Existing> removed = new HashSet<>(); Set<Ref> affected = new HashSet<>();
        for (var decision : intent.entities()) {
            Ref ref = decision.entity();
            if (ref == null || decisions.putIfAbsent(ref, decision) != null) fail("CONFLICTING_DECISIONS");
            if (ref instanceof Ref.Existing old && !original.containsKey(old)) fail("UNKNOWN_ENTITY");
            if (decision instanceof EntityDecision.Create create) {
                if (!id(create.entity().slot()) || !slots.add(create.entity().slot()) || !types.containsKey(create.entity().type())) fail("INVALID_FRESH_SLOT");
                capability(logical, NativeDefinition.Operation.CREATE_ENTITY); affected.add(ref);
            } else if (decision instanceof EntityDecision.Remove remove) {
                capability(logical, NativeDefinition.Operation.REMOVE_ENTITY); removed.add(remove.entity()); affected.add(ref);
            }
        }
        long finalSize = (long)original.size() - removed.size() + slots.size();
        if (finalSize > 20_000) fail("RESOURCE_LIMIT");
        Map<Ref, ExpectedTarget.Entity> entities = new TreeMap<>(references);
        for (var entry : original.entrySet()) if (!removed.contains(entry.getKey())) {
            capability(logical, NativeDefinition.Operation.RETAIN_ENTITY);
            var type = types.get(entry.getKey().type()); if (type == null) fail("UNKNOWN_TYPE");
            var decision = decisions.get(entry.getKey());
            Map<String, String> fields = decision instanceof EntityDecision.Retain retain ? fields(type, entry.getValue().fields(), retain.fields(), false) : entry.getValue().fields();
            if (!fields.equals(entry.getValue().fields())) { capability(logical, NativeDefinition.Operation.BIND_FIELD); affected.add(entry.getKey()); }
            entities.put(entry.getKey(), entity(type, entry.getKey(), fields));
        }
        for (var decision : intent.entities()) if (decision instanceof EntityDecision.Create create) {
            var type = types.get(create.entity().type());
            entities.put(create.entity(), entity(type, create.entity(), fields(type, Map.of(), create.fields(), true)));
        }
        Set<ObservedGraph.Key> identities = new HashSet<>();
        for (var entity : entities.values()) if (!identities.add(entity.identity())) fail("DUPLICATE_IDENTITY");
        Map<Ref, Map<String, Ref.Existing>> originalReferences = new HashMap<>();
        Set<ExpectedTarget.Edge> originalEdges = new HashSet<>();
        for (var edge : graph.edges()) {
            Relation declaration = relations.get(edge.relation()); if (declaration == null) fail("UNKNOWN_RELATION");
            Ref.Existing from = new Ref.Existing(edge.source()), to = new Ref.Existing(edge.target());
            if (!originalEdges.add(new ExpectedTarget.Edge(edge.relation(), from, to))) fail("INVALID_OBSERVATION");
            if (declaration.kind() == RelationKind.REFERENCE && originalReferences.computeIfAbsent(from, ignored -> new HashMap<>()).putIfAbsent(edge.relation(), to) != null) fail("INVALID_OBSERVATION");
        }
        List<ExpectedTarget.Edge> edges = new ArrayList<>();
        for (var entry : entities.entrySet()) {
            Ref source = entry.getKey(); EntityDecision decision = decisions.get(source);
            Map<String, ReferenceValue> choices = decision instanceof EntityDecision.Retain retain ? retain.references() : decision instanceof EntityDecision.Create create ? create.references() : null;
            Set<String> declared = new HashSet<>();
            logical.relations().stream().filter(r -> r.kind() == RelationKind.REFERENCE && r.fromType().equals(source.type())).forEach(r -> declared.add(r.id()));
            if (choices != null && !choices.keySet().equals(declared)) fail("REFERENCE_DECISIONS_INCOMPLETE");
            for (var relation : logical.relations()) {
                if (relation.kind() != RelationKind.REFERENCE || !relation.fromType().equals(source.type())) continue;
                Ref.Existing before = originalReferences.getOrDefault(source, Map.of()).get(relation.id());
                ReferenceValue choice = choices == null ? new ReferenceValue.KeepObserved() : choices.get(relation.id());
                Ref target = reference(choice, source instanceof Ref.Fresh, before, entities);
                if (target != null) {
                    if (!relation.toType().equals(target.type())) fail("REFERENCE_TYPE_MISMATCH");
                    add(edges, new ExpectedTarget.Edge(relation.id(), source, target));
                }
                boolean changed = !java.util.Objects.equals(before, target)
                        || before != null && target != null && !before.key().identity().equals(entities.get(target).identity().identity());
                if (changed) { capability(logical, NativeDefinition.Operation.MOVE_RELATION); affected.add(source); if (before != null) affected.add(before); if (target != null) affected.add(target); }
            }
        }
        Map<ChildRelation, Containment> moves = new HashMap<>();
        for (var move : intent.containment()) {
            Relation relation = relations.get(move.relation());
            if (relation == null || relation.kind() != RelationKind.CONTAINMENT || move.parent() == null || move.child() == null || !entities.containsKey(move.parent()) || !entities.containsKey(move.child())
                    || !relation.fromType().equals(move.parent().type()) || !relation.toType().equals(move.child().type())) fail("INVALID_CONTAINMENT");
            if (!decisions.containsKey(move.child())) fail("MISSING_ENTITY_DISPOSITION");
            if (moves.putIfAbsent(new ChildRelation(move.child(), move.relation()), move) != null) fail("CONFLICTING_DECISIONS");
        }
        for (var edge : originalEdges) {
            if (relations.get(edge.relation()).kind() != RelationKind.CONTAINMENT || removed.contains(edge.target())) continue;
            if (!moves.containsKey(new ChildRelation(edge.target(), edge.relation()))) add(edges, edge);
        }
        for (var move : moves.values()) add(edges, new ExpectedTarget.Edge(move.relation(), move.parent(), move.child()));
        Set<ExpectedTarget.Edge> finalEdges = new HashSet<>(edges);
        if (finalEdges.size() != edges.size()) fail("DUPLICATE_RELATION");
        for (var edge : originalEdges) if (!finalEdges.contains(edge)) { capability(logical, NativeDefinition.Operation.MOVE_RELATION); affected.add(edge.source()); affected.add(edge.target()); }
        for (var edge : finalEdges) if (!originalEdges.contains(edge)) { capability(logical, NativeDefinition.Operation.MOVE_RELATION); affected.add(edge.source()); affected.add(edge.target()); }
        validateGraph(logical, entities, edges);
        edges.sort(Comparator.comparing(ExpectedTarget.Edge::relation).thenComparing(ExpectedTarget.Edge::source, references).thenComparing(ExpectedTarget.Edge::target, references));
        return new ExpectedTarget(new ArrayList<>(entities.values()), edges, removed, affected);
    }
    private static Map<String, String> fields(NativeDefinition.EntityType type, Map<String, String> original, Map<String, FieldValue> choices, boolean fresh) {
        Set<String> declared = new HashSet<>(); type.fields().forEach(f -> declared.add(f.id()));
        if (!declared.equals(choices.keySet())) fail("FIELD_DECISIONS_INCOMPLETE");
        Map<String, String> fields = new TreeMap<>();
        for (var field : type.fields()) {
            FieldValue choice = choices.get(field.id()); String value = null;
            if (!fresh && !field.editable() && !(choice instanceof FieldValue.KeepObserved)) fail("FIELD_NOT_EDITABLE");
            if (choice instanceof FieldValue.KeepObserved) {
                if (fresh || !field.readable()) fail("KEEP_OBSERVED_UNAVAILABLE"); value = original.get(field.id());
            } else if (choice instanceof FieldValue.Entered entered) value = entered.text();
            else if (!(choice instanceof FieldValue.ExplicitlyAbsent)) fail("UNRESOLVED_FIELD");
            if (value == null && field.required()) fail("REQUIRED_FIELD_MISSING");
            if (!fresh && original.containsKey(field.id()) != (value != null)) fail("ATTRIBUTE_PRESENCE_UNSUPPORTED");
            if (value != null) { if (!NativeLexicalRules.validValue(field.valueType(), value)) fail("INVALID_SCALAR"); fields.put(field.id(), value); }
        }
        return fields;
    }
    private static ExpectedTarget.Entity entity(NativeDefinition.EntityType type, Ref ref, Map<String, String> fields) {
        String identity = fields.get(type.identity().field()); if (!NativeLexicalRules.validIdentity(identity)) fail("INVALID_IDENTITY");
        for (var field : type.fields()) {
            String value = fields.get(field.id());
            if (value == null && field.required()) fail("REQUIRED_FIELD_MISSING");
            if (value != null && !NativeLexicalRules.validValue(field.valueType(), value)) fail("INVALID_SCALAR");
        }
        return new ExpectedTarget.Entity(ref, new ObservedGraph.Key(type.id(), identity), fields);
    }
    private static Ref reference(ReferenceValue choice, boolean fresh, Ref.Existing original, Map<Ref, ExpectedTarget.Entity> entities) {
        if (choice instanceof ReferenceValue.KeepObserved) {
            if (fresh) fail("KEEP_OBSERVED_UNAVAILABLE");
            if (original != null && (!entities.containsKey(original) || !entities.get(original).identity().identity().equals(original.key().identity()))) fail("RETAINED_REFERENCE_CHANGED");
            return original;
        }
        if (choice instanceof ReferenceValue.To to) {
            if (to.target() == null || !entities.containsKey(to.target())) fail("UNRESOLVED_REFERENCE");
            if (!fresh && original == null) fail("ATTRIBUTE_PRESENCE_UNSUPPORTED");
            return to.target();
        }
        if (choice instanceof ReferenceValue.ExplicitlyAbsent) {
            if (!fresh && original != null) fail("ATTRIBUTE_PRESENCE_UNSUPPORTED"); return null;
        }
        fail("UNRESOLVED_REFERENCE"); return null;
    }
    private static void validateGraph(NativeDefinition.Logical logical, Map<Ref, ExpectedTarget.Entity> entities, List<ExpectedTarget.Edge> edges) {
        Map<String, Relation> declarations = new HashMap<>(); logical.relations().forEach(r -> declarations.put(r.id(), r));
        Map<Ref, Map<String, Integer>> outgoing = new HashMap<>(); Map<Ref, Set<String>> incoming = new HashMap<>(); Map<Ref, Ref> parents = new HashMap<>();
        for (var edge : edges) {
            if (!entities.containsKey(edge.source()) || !entities.containsKey(edge.target())) fail("UNRESOLVED_RELATION");
            Relation relation = declarations.get(edge.relation());
            if (relation == null || !relation.fromType().equals(edge.source().type()) || !relation.toType().equals(edge.target().type())) fail("RELATION_TYPE_MISMATCH");
            outgoing.computeIfAbsent(edge.source(), ignored -> new HashMap<>()).merge(relation.id(), 1, Integer::sum);
            if (relation.kind() == RelationKind.CONTAINMENT) {
                Ref previous = parents.putIfAbsent(edge.target(), edge.source());
                if (previous != null && !previous.equals(edge.source())) fail("MULTIPLE_CONTAINMENT_PARENTS");
                incoming.computeIfAbsent(edge.target(), ignored -> new HashSet<>()).add(relation.id());
            }
        }
        for (Ref entity : entities.keySet()) for (var relation : logical.relations()) {
            if (relation.fromType().equals(entity.type())) {
                BigInteger count = BigInteger.valueOf(outgoing.getOrDefault(entity, Map.of()).getOrDefault(relation.id(), 0));
                if (count.compareTo(relation.minimum()) < 0 || count.compareTo(relation.maximum()) > 0) fail("RELATION_CARDINALITY");
            }
            if (relation.kind() == RelationKind.CONTAINMENT && relation.toType().equals(entity.type()) && !incoming.getOrDefault(entity, Set.of()).contains(relation.id())) fail("CONTAINMENT_PARENT_MISSING");
        }
        Set<Ref> complete = new HashSet<>();
        for (Ref start : parents.keySet()) {
            Set<Ref> path = new HashSet<>(); Ref next = start;
            while (next != null && !complete.contains(next)) { if (!path.add(next)) fail("CONTAINMENT_CYCLE"); next = parents.get(next); } complete.addAll(path);
        }
        Map<String, Integer> counts = new HashMap<>(); entities.keySet().forEach(ref -> counts.merge(ref.type(), 1, Integer::sum));
        for (var rule : logical.rules()) {
            BigInteger count = BigInteger.valueOf(counts.getOrDefault(rule.type(), 0));
            if (count.compareTo(rule.minimum()) < 0 || count.compareTo(rule.maximum()) > 0) fail("ENTITY_COUNT");
        }
    }
    private static void capability(NativeDefinition.Logical logical, NativeDefinition.Operation operation) { if (!logical.operationCapabilities().contains(operation)) fail("OPERATION_NOT_DECLARED"); }
    private static void add(List<ExpectedTarget.Edge> edges, ExpectedTarget.Edge edge) { if (edges.size() == 50_000) fail("RESOURCE_LIMIT"); edges.add(edge); }
    private static boolean id(String value) { return value != null && value.matches("[a-z][a-z0-9.-]{0,63}"); }
    private static void budget(TargetIntent intent) {
        long bytes = 0;
        for (var decision : intent.entities()) {
            if (decision == null) fail("INVALID_INPUT");
            Map<String, FieldValue> fields = decision instanceof EntityDecision.Retain retain ? retain.fields() : decision instanceof EntityDecision.Create create ? create.fields() : Map.of();
            for (var choice : fields.values()) if (choice instanceof FieldValue.Entered entered) {
                String text = entered.text(); if (text == null || text.length() > 1_048_576) fail("RESOURCE_LIMIT");
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (Character.isHighSurrogate(c)) { if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) fail("INVALID_UNICODE"); bytes += 4; }
                    else if (Character.isLowSurrogate(c)) fail("INVALID_UNICODE"); else bytes += c < 128 ? 1 : c < 2048 ? 2 : 3;
                    if (bytes > 16L * 1024 * 1024) fail("RESOURCE_LIMIT");
                }
            }
        }
    }
    private static void fail(String code) { throw new Refusal(code); }
    private static final class Refusal extends RuntimeException { final String code; Refusal(String code) { super(null, null, false, false); this.code = code; } }
}
