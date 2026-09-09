package studio.environment.core.profile;

import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.profile.ProfileCapture.Command;
import studio.environment.core.profile.ProfileCapture.SlotMapping;

/** Allowlisted physical copy only; no source graph retained in results. */
final class PhysicalProfileCapture {
    ProfileResult capture(studio.environment.core.definitionv2.NativeDefinition.Logical logical, String logicalDigest, ObservedGraph graph, Command command, java.util.function.Function<Profile, ProfileResult> validator) {
        if (command.mappings().size() != graph.entities().size()) return ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", "");
        var mappings = new java.util.HashMap<ObservedGraph.Key, SlotMapping>();
        var slots = new java.util.HashSet<String>();
        for (var mapping : command.mappings()) {
            if (mappings.putIfAbsent(mapping.observed(), mapping) != null || !slots.add(mapping.slotId()))
                return ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", "");
        }
        var types = new java.util.HashMap<String, studio.environment.core.definitionv2.NativeDefinition.EntityType>();
        logical.entityTypes().forEach(t -> types.put(t.id(), t));
        var entities = new java.util.ArrayList<Profile.Entity>();
        for (var entity : graph.entities()) {
            var mapping = mappings.remove(entity.key()); var type = types.get(entity.key().type());
            if (mapping == null || type == null) return ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", "");
            entities.add(new Profile.Entity(mapping.slotId(), type.id(), mapping.label(), ProfileValidator.required(type)));
        }
        if (!mappings.isEmpty()) return ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", "");
        command.mappings().forEach(m -> mappings.put(m.observed(), m));
        var edges = new java.util.ArrayList<Profile.Relation>();
        for (var edge : graph.edges()) {
            if (!mappings.containsKey(edge.source()) || !mappings.containsKey(edge.target())) return ProfileResult.rejected("INVALID_GRAPH", "");
            edges.add(new Profile.Relation(edge.relation(), mappings.get(edge.source()).slotId(), mappings.get(edge.target()).slotId()));
        }
        return validator.apply(new Profile(command.id(), command.revision(), logicalDigest, entities, edges));
    }
}
