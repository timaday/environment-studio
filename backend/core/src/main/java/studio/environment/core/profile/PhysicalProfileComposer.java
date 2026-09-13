package studio.environment.core.profile;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import java.util.Set;
import studio.environment.core.graph.ObservedGraph;

import static studio.environment.core.profile.ProfileComposer.*;

/** Shared physical closure and explicit mapping; never derivation closure. */
final class PhysicalProfileComposer {
    PreviewResult preview(studio.environment.core.definitionv2.NativeDefinition.Logical logical, ProfileResult.Checked profile, Set<String> selected, java.util.function.Function<Profile, ProfileResult> validator) {
        if (selected.isEmpty() || selected.size() > Profile.MAX_ENTITIES) return previewRejected("INVALID_SELECTION");
        ProfileResult checked = validator.apply(profile.profile());
        if (!(checked instanceof ProfileResult.StructurallyValid accepted)) return new PreviewResult.Rejected(((ProfileResult.Rejected)checked).diagnostics());
        if (!accepted.checked().equals(profile)) return previewRejected("STALE_PROFILE");
        Map<String, Profile.Entity> entities = new TreeMap<>(); profile.profile().entities().forEach(e -> entities.put(e.id(), e));
        if (!entities.keySet().containsAll(selected)) return previewRejected("UNKNOWN_SLOT");
        Map<String, studio.environment.core.definition.DefinitionDraft.Relation> declarations = new HashMap<>();
        logical.relations().forEach(r -> declarations.put(r.id(), r));
        Set<String> included = new TreeSet<>(selected); Set<String> pending = new TreeSet<>(selected);
        Map<String, List<Profile.Relation>> outgoing = new HashMap<>(), incoming = new HashMap<>();
        for (var edge : profile.profile().relations()) {
            outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge);
        }
        List<Dependency> dependencies = new ArrayList<>();
        while (!pending.isEmpty()) {
            String slot = pending.iterator().next(); pending.remove(slot);
            for (var edge : outgoing.getOrDefault(slot, List.of())) {
                var declaration = declarations.get(edge.type());
                Reason reason = declaration.kind() == RelationKind.REFERENCE && declaration.minimum().signum() > 0 ? Reason.REQUIRED_REFERENCE
                    : declaration.includeTargetOnReuse() ? Reason.DECLARED_REUSE_TARGET : null;
                if (reason != null && included.add(edge.to())) { pending.add(edge.to()); dependencies.add(new Dependency(edge.to(), slot, edge.type(), reason)); }
            }
            for (var edge : incoming.getOrDefault(slot, List.of())) if (declarations.get(edge.type()).kind() == RelationKind.CONTAINMENT && included.add(edge.from())) {
                pending.add(edge.from()); dependencies.add(new Dependency(edge.from(), slot, edge.type(), Reason.CONTAINMENT_PARENT));
            }
        }
        dependencies.sort(Comparator.comparing(Dependency::slot).thenComparing(Dependency::causedBy).thenComparing(Dependency::relation).thenComparing(d -> d.reason().name()));
        var edges = profile.profile().relations().stream().filter(e -> included.contains(e.from()) && included.contains(e.to())).toList();
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        for (var edge : edges) counts.computeIfAbsent(edge.from(), ignored -> new HashMap<>()).merge(edge.type(), 1, Integer::sum);
        List<Conflict> conflicts = new ArrayList<>();
        for (String slot : included) for (var relation : logical.relations()) {
            if (!relation.fromType().equals(entities.get(slot).type())) continue;
            long count = counts.getOrDefault(slot, Map.of()).getOrDefault(relation.id(), 0);
            if (BigInteger.valueOf(count).compareTo(relation.minimum()) < 0) conflict(conflicts, "RELATION_CARDINALITY", slot, relation.id());
        }
        return new PreviewResult.Proposed(new Preview(profile.profile().id(), profile.profile().revision(), profile.contentDigest(), profile.profile().logicalDefinitionDigest(),
            selected.stream().sorted().toList(), included.stream().map(entities::get).toList(), dependencies, edges, conflicts.stream().sorted(Comparator.comparing(Conflict::code).thenComparing(Conflict::slot).thenComparing(Conflict::relation)).toList()));
    }
    CompositionResult compose(studio.environment.core.definitionv2.NativeDefinition.Logical logical, ProfileResult.Checked profile, Preview preview,
            ObservedGraph target, List<Decision> decisions, java.util.function.Function<Profile, ProfileResult> validator, Comparator<ObservedGraph.Key> keys) {
        if (decisions.size() > Profile.MAX_ENTITIES) return rejected("RESOURCE_LIMIT");
        var fresh = preview(logical, profile, new HashSet<>(preview.selected()), validator);
        if (!(fresh instanceof PreviewResult.Proposed proposed) || !proposed.preview().equals(preview)) return rejected("STALE_PREVIEW");
        Map<String, Profile.Entity> included = new TreeMap<>(); preview.included().forEach(e -> included.put(e.id(), e));
        Map<String, Decision> chosen = new HashMap<>();
        for (var decision : decisions) if (!included.containsKey(decision.slot()) || chosen.putIfAbsent(decision.slot(), decision) != null) return rejected("INVALID_DECISIONS");
        if (!chosen.keySet().equals(included.keySet())) return rejected("MISSING_DECISION");
        if (chosen.values().stream().anyMatch(d -> d instanceof Decision.Cancel)) return new CompositionResult.Cancelled();
        Set<ObservedGraph.Key> existing = new HashSet<>();
        for (var entity : target.entities()) if (!existing.add(entity.key())) return rejected("INVALID_TARGET");
        Map<String, Target> assignments = new TreeMap<>(); Set<Target> unique = new HashSet<>(); Set<String> newIds = new HashSet<>();
        List<Target.New> additions = new ArrayList<>();
        for (var entry : included.entrySet()) {
            Decision decision = chosen.get(entry.getKey()); Target mapped;
            if (decision instanceof Decision.Create create) {
                if (!ProfileValidator.id(create.targetSlot()) || !newIds.add(create.targetSlot())) return rejected("TARGET_COLLISION");
                var created = new Target.New(create.targetSlot(), entry.getValue().type()); additions.add(created); mapped = created;
            } else if (decision instanceof Decision.UseExisting reuse) {
                if (!existing.contains(reuse.target()) || !entry.getValue().type().equals(reuse.target().type())) return rejected("INCOMPATIBLE_TARGET");
                mapped = new Target.Existing(reuse.target());
            } else return rejected("INVALID_DECISIONS");
            if (!unique.add(mapped)) return rejected("TARGET_COLLISION");
            assignments.put(entry.getKey(), mapped);
        }
        if ((long) existing.size() + additions.size() > Profile.MAX_ENTITIES) return rejected("RESOURCE_LIMIT");
        var proposals = preview.relations().stream().map(e -> new RelationProposal(e.type(), assignments.get(e.from()), assignments.get(e.to()))).toList();
        Set<RelationProposal> combinedEdges = new LinkedHashSet<>();
        for (var edge : target.edges()) combinedEdges.add(new RelationProposal(edge.relation(), new Target.Existing(edge.source()), new Target.Existing(edge.target())));
        for (var proposal : proposals) {
            if (combinedEdges.size() == Profile.MAX_RELATIONS && !combinedEdges.contains(proposal)) return rejected("RESOURCE_LIMIT");
            combinedEdges.add(proposal);
        }
        Map<String, List<String>> fields = new HashMap<>();
        logical.entityTypes().forEach(t -> fields.put(t.id(), t.fields().stream().map(f -> f.id()).sorted().toList()));
        List<UnresolvedFields> unresolved = new ArrayList<>();
        assignments.forEach((slot, destination) -> unresolved.add(new UnresolvedFields(destination, fields.get(included.get(slot).type()))));
        Draft draft = new Draft(additions, existing.stream().sorted(keys).toList(), target.edges().stream().sorted(Comparator.comparing(ObservedGraph.Edge::relation).thenComparing(ObservedGraph.Edge::source, keys).thenComparing(ObservedGraph.Edge::target, keys)).toList(), proposals, unresolved);
        Map<Target, String> all = new LinkedHashMap<>();
        existing.stream().sorted(keys).forEach(k -> all.put(new Target.Existing(k), k.type())); additions.forEach(n -> all.put(n, n.type()));
        List<Conflict> conflicts = structure(logical, all, combinedEdges, assignments);
        return conflicts.isEmpty() ? new CompositionResult.Prepared(draft) : new CompositionResult.NeedsResolution(draft, conflicts);
    }

    private static List<Conflict> structure(studio.environment.core.definitionv2.NativeDefinition.Logical logical, Map<Target, String> all, Set<RelationProposal> edges, Map<String, Target> assignments) {
        Map<String, studio.environment.core.definition.DefinitionDraft.Relation> declarations = new HashMap<>(); logical.relations().forEach(r -> declarations.put(r.id(), r));
        Map<Target, Map<String, Integer>> outgoing = new HashMap<>(); Map<Target, Set<String>> incoming = new HashMap<>(); Map<Target, Target> parents = new HashMap<>();
        List<Conflict> conflicts = new ArrayList<>(); Map<Target, String> slots = new HashMap<>(); assignments.forEach((s, t) -> slots.put(t, s));
        for (var edge : edges) {
            var relation = declarations.get(edge.relation());
            if (relation == null || !relation.fromType().equals(all.get(edge.from())) || !relation.toType().equals(all.get(edge.to()))) {
                conflict(conflicts, "INVALID_TARGET_RELATION", "", ""); continue;
            }
            outgoing.computeIfAbsent(edge.from(), ignored -> new HashMap<>()).merge(relation.id(), 1, Integer::sum);
            if (relation.kind() == RelationKind.CONTAINMENT) {
                Target previous = parents.putIfAbsent(edge.to(), edge.from());
                if (previous != null && !previous.equals(edge.from())) conflict(conflicts, "MULTIPLE_CONTAINMENT_PARENTS", slots.getOrDefault(edge.to(), ""), relation.id());
                incoming.computeIfAbsent(edge.to(), ignored -> new HashSet<>()).add(relation.id());
            }
        }
        for (var entity : all.entrySet()) for (var relation : logical.relations()) {
            if (relation.fromType().equals(entity.getValue())) {
                BigInteger count = BigInteger.valueOf(outgoing.getOrDefault(entity.getKey(), Map.of()).getOrDefault(relation.id(), 0));
                if (count.compareTo(relation.minimum()) < 0 || count.compareTo(relation.maximum()) > 0) conflict(conflicts, "RELATION_CARDINALITY", slots.getOrDefault(entity.getKey(), ""), relation.id());
            }
            if (relation.kind() == RelationKind.CONTAINMENT && relation.toType().equals(entity.getValue()) && !incoming.getOrDefault(entity.getKey(), Set.of()).contains(relation.id()))
                conflict(conflicts, "CONTAINMENT_PARENT_MISSING", slots.getOrDefault(entity.getKey(), ""), relation.id());
        }
        if (ProfileValidator.cyclic(parents)) conflict(conflicts, "CONTAINMENT_CYCLE", "", "");
        Map<String, Integer> counts = new HashMap<>(); all.values().forEach(t -> counts.merge(t, 1, Integer::sum));
        for (var rule : logical.rules()) {
            BigInteger count = BigInteger.valueOf(counts.getOrDefault(rule.type(), 0));
            if (count.compareTo(rule.minimum()) < 0 || count.compareTo(rule.maximum()) > 0) conflict(conflicts, "ENTITY_COUNT", "", rule.id());
        }
        return conflicts.stream().distinct().sorted(Comparator.comparing(Conflict::code).thenComparing(Conflict::slot).thenComparing(Conflict::relation)).toList();
    }
    private static void conflict(List<Conflict> conflicts, String code, String slot, String relation) { if (conflicts.size() < 256) conflicts.add(new Conflict(code, slot, relation)); }
    private static PreviewResult.Rejected previewRejected(String code) { return new PreviewResult.Rejected(List.of(new ProfileResult.Diagnostic(code, ""))); }
    private static CompositionResult.Rejected rejected(String code) { return new CompositionResult.Rejected(List.of(new ProfileResult.Diagnostic(code, ""))); }
}
