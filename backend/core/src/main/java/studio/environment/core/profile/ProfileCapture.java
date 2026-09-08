package studio.environment.core.profile;

import java.math.BigInteger;
import java.util.List;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;

public final class ProfileCapture {
    public record SlotMapping(ObservedGraph.Key observed, String slotId, String label) {
        @Override public String toString() { return "SlotMapping[redacted]"; }
    }
    public record Command(String id, BigInteger revision, List<SlotMapping> mappings) {
        public Command { Profile.bound(mappings.size(), Profile.MAX_ENTITIES); mappings = List.copyOf(mappings); }
        @Override public String toString() { return "CaptureCommand[redacted]"; }
    }
    public ProfileResult capture(ReadyToPublish definition, GraphValidationResult.Accepted observation, Command command) {
        var graph = observation.graph();
        if (command.mappings().size() != graph.entities().size()) return ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", "");
        var mappings = new java.util.HashMap<ObservedGraph.Key, SlotMapping>();
        var slots = new java.util.HashSet<String>();
        for (var mapping : command.mappings()) {
            if (mappings.putIfAbsent(mapping.observed(), mapping) != null || !slots.add(mapping.slotId()))
                return ProfileResult.rejected("MAPPING_NOT_BIJECTIVE", "");
        }
        var types = new java.util.HashMap<String, studio.environment.core.definitionv2.NativeDefinition.EntityType>();
        definition.checked().definition().logical().entityTypes().forEach(t -> types.put(t.id(), t));
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
        return new ProfileValidator().validate(definition, new Profile(command.id(), command.revision(), definition.checked().logicalDigest(), entities, edges));
    }
}
