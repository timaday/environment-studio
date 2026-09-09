package studio.environment.core.profile;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition.EntityType;

final class PhysicalProfileValidator {
    static final Comparator<Profile.Relation> RELATIONS = Comparator.comparing(Profile.Relation::type).thenComparing(Profile.Relation::from).thenComparing(Profile.Relation::to);
    ProfileResult validate(studio.environment.core.definitionv2.NativeDefinition.Logical logical, String logicalDigest, Profile profile, java.util.function.Function<Profile, String> digest) {
        if (!logicalDigest.equals(profile.logicalDefinitionDigest())) return reject("INCOMPATIBLE_DEFINITION");
        if (!id(profile.id()) || profile.revision() == null || profile.revision().signum() <= 0 || profile.entities().isEmpty()) return reject("INVALID_PROFILE");
        Map<String, EntityType> types = new HashMap<>(); logical.entityTypes().forEach(t -> types.put(t.id(), t));
        Map<String, Profile.Entity> entities = new HashMap<>();
        List<Profile.Entity> normalized = new ArrayList<>();
        for (var entity : profile.entities()) {
            EntityType type = types.get(entity.type());
            if (!id(entity.id()) || type == null || !label(entity.label())) return reject("INVALID_ENTITY");
            if (entities.putIfAbsent(entity.id(), entity) != null) return reject("DUPLICATE_SLOT");
            List<String> expected = required(type);
            if (entity.requiredInputs().size() != expected.size() || !new HashSet<>(entity.requiredInputs()).equals(new HashSet<>(expected))) return reject("REQUIRED_INPUTS_MISMATCH");
            normalized.add(new Profile.Entity(entity.id(), entity.type(), entity.label(), expected));
        }
        Map<String, studio.environment.core.definition.DefinitionDraft.Relation> declarations = new HashMap<>();
        logical.relations().forEach(r -> declarations.put(r.id(), r));
        Set<Profile.Relation> unique = new HashSet<>(); Map<String, String> parents = new HashMap<>();
        Map<String, Map<String, Integer>> outgoing = new HashMap<>();
        Map<String, Set<String>> incoming = new HashMap<>();
        for (var edge : profile.relations()) {
            var relation = declarations.get(edge.type()); var from = entities.get(edge.from()); var to = entities.get(edge.to());
            if (relation == null || from == null || to == null || !relation.fromType().equals(from.type()) || !relation.toType().equals(to.type())) return reject("INVALID_RELATION");
            if (!unique.add(edge)) return reject("DUPLICATE_RELATION");
            outgoing.computeIfAbsent(edge.from(), ignored -> new HashMap<>()).merge(edge.type(), 1, Integer::sum);
            if (relation.kind() == RelationKind.CONTAINMENT) {
                String previous = parents.putIfAbsent(edge.to(), edge.from());
                if (previous != null && !previous.equals(edge.from())) return reject("MULTIPLE_CONTAINMENT_PARENTS");
                incoming.computeIfAbsent(edge.to(), ignored -> new HashSet<>()).add(edge.type());
            }
        }
        for (var relation : declarations.values()) for (var entity : entities.values()) {
            if (relation.fromType().equals(entity.type())) {
                BigInteger count = BigInteger.valueOf(outgoing.getOrDefault(entity.id(), Map.of()).getOrDefault(relation.id(), 0));
                if (count.compareTo(relation.minimum()) < 0 || count.compareTo(relation.maximum()) > 0) return reject("RELATION_CARDINALITY");
            }
            if (relation.kind() == RelationKind.CONTAINMENT && relation.toType().equals(entity.type()) && !incoming.getOrDefault(entity.id(), Set.of()).contains(relation.id())) return reject("CONTAINMENT_PARENT_MISSING");
        }
        if (cyclic(parents)) return reject("CONTAINMENT_CYCLE");
        normalized.sort(Comparator.comparing(Profile.Entity::id));
        Profile result = new Profile(profile.id(), profile.revision(), profile.logicalDefinitionDigest(), normalized, unique.stream().sorted(RELATIONS).toList());
        return new ProfileResult.StructurallyValid(new ProfileResult.Checked(result, digest.apply(result)));
    }
    static boolean id(String value) { return value != null && value.matches("[a-z][a-z0-9.-]{0,63}"); }
    private static boolean label(String value) {
        if (value == null || value.isEmpty() || value.codePointCount(0, value.length()) > 128) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isSurrogate(value.charAt(i))) {
            if (!Character.isHighSurrogate(value.charAt(i)) || i + 1 == value.length() || !Character.isLowSurrogate(value.charAt(++i))) return false;
        }
        return true;
    }
    static List<String> required(EntityType type) { return type.fields().stream().filter(f -> f.required()).map(f -> f.id()).sorted().toList(); }
    static <T> boolean cyclic(Map<T, T> parents) {
        Set<T> complete = new HashSet<>();
        for (T start : parents.keySet()) { Set<T> path = new HashSet<>(); T next = start;
            while (next != null && !complete.contains(next)) { if (!path.add(next)) return true; next = parents.get(next); } complete.addAll(path); }
        return false;
    }
    private static ProfileResult.Rejected reject(String code) { return ProfileResult.rejected(code, ""); }
}
