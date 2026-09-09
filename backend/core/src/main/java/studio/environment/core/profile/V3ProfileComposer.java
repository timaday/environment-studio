package studio.environment.core.profile;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.graph.ObservedGraph;

/** Versioned physical composition proposals; derived results require fresh target decisions. */
public final class V3ProfileComposer {
    public record Preview(ProfileComposer.Preview physical, List<String> affectedDerivations) {
        public Preview {
            Objects.requireNonNull(physical);
            Profile.bound(affectedDerivations.size(), 32);
            affectedDerivations = List.copyOf(affectedDerivations);
        }
        @Override public String toString() { return "V3ProfilePreview[redacted]"; }
    }
    public sealed interface PreviewResult {
        record Proposed(Preview preview) implements PreviewResult {
            public Proposed { Objects.requireNonNull(preview); }
        }
        record Rejected(List<ProfileResult.Diagnostic> diagnostics) implements PreviewResult {
            public Rejected { Profile.bound(diagnostics.size(), 256); diagnostics = List.copyOf(diagnostics); }
        }
    }
    public PreviewResult preview(Checked definition, ProfileResult.Checked profile, Set<String> selectedSlots) {
        if (profile == null || profile.profile() == null || selectedSlots == null) return previewRejected("INVALID_INPUT");
        if (!V3ProfileDefinition.eligible(definition)) return previewRejected("INVALID_DEFINITION");
        if (selectedSlots.isEmpty() || selectedSlots.size() > Profile.MAX_ENTITIES || selectedSlots.stream().anyMatch(Objects::isNull)) return previewRejected("INVALID_SELECTION");
        var physical = new PhysicalProfileComposer().preview(V3ProfileDefinition.physical(definition), profile, selectedSlots,
                value -> new V3ProfileValidator().validate(definition, value));
        if (physical instanceof ProfileComposer.PreviewResult.Rejected rejected) return new PreviewResult.Rejected(rejected.diagnostics());
        var proposed = ((ProfileComposer.PreviewResult.Proposed)physical).preview();
        Set<String> types = new HashSet<>(); proposed.included().forEach(e -> types.add(e.type()));
        var affected = definition.definition().logical().derivations().stream().filter(d -> types.contains(d.sourceType()))
                .map(d -> d.id()).distinct().sorted().toList();
        return new PreviewResult.Proposed(new Preview(proposed, affected));
    }
    public ProfileComposer.CompositionResult compose(Checked definition, ProfileResult.Checked profile, Preview preview,
            ObservedGraph current, List<ProfileComposer.Decision> decisions) {
        if (preview == null || current == null || decisions == null) return rejected("INVALID_INPUT");
        if (!V3ProfileDefinition.eligible(definition)) return rejected("INVALID_DEFINITION");
        if (decisions.size() > Profile.MAX_ENTITIES) return rejected("RESOURCE_LIMIT");
        if (decisions.stream().anyMatch(Objects::isNull)) return rejected("INVALID_DECISIONS");
        var fresh = preview(definition, profile, new HashSet<>(preview.physical().selected()));
        if (!(fresh instanceof PreviewResult.Proposed proposed) || !proposed.preview().equals(preview)) return rejected("STALE_PREVIEW");
        if (!physicalCurrent(definition, current)) return rejected("INVALID_TARGET");
        Comparator<ObservedGraph.Key> keys = Comparator.comparing(ObservedGraph.Key::type, V3ProfileDefinition::scalar)
                .thenComparing(ObservedGraph.Key::identity, V3ProfileDefinition::scalar);
        return new PhysicalProfileComposer().compose(V3ProfileDefinition.physical(definition), profile, preview.physical(), current, decisions,
                value -> new V3ProfileValidator().validate(definition, value), keys);
    }
    private static boolean physicalCurrent(Checked definition, ObservedGraph graph) {
        Set<String> types = new HashSet<>(); definition.definition().logical().entityTypes().forEach(t -> types.add(t.id()));
        Set<ObservedGraph.Key> keys = new HashSet<>();
        for (var entity : graph.entities()) if (!types.contains(entity.key().type()) || !V3ProfileDefinition.identity(entity.key().identity()) || !keys.add(entity.key())) return false;
        Map<String, studio.environment.core.definition.DefinitionDraft.Relation> relations = new HashMap<>();
        definition.definition().logical().relations().forEach(r -> relations.put(r.id(), r));
        Set<ObservedGraph.Edge> unique = new HashSet<>();
        for (var edge : graph.edges()) {
            var relation = relations.get(edge.relation());
            if (relation == null || !keys.contains(edge.source()) || !keys.contains(edge.target()) || !unique.add(edge)
                    || !relation.fromType().equals(edge.source().type()) || !relation.toType().equals(edge.target().type())) return false;
        }
        return true;
    }
    private static PreviewResult.Rejected previewRejected(String code) {
        return new PreviewResult.Rejected(List.of(new ProfileResult.Diagnostic(code, "")));
    }
    private static ProfileComposer.CompositionResult.Rejected rejected(String code) {
        return new ProfileComposer.CompositionResult.Rejected(List.of(new ProfileResult.Diagnostic(code, "")));
    }
}
