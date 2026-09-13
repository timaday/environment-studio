package studio.environment.core.planning;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.TreeMap;
import studio.environment.core.graph.ObservedGraph;

/** Independently resolved semantic expectation; no XML positions or serialization. */
public record ExpectedTarget(List<Entity> entities, List<Edge> edges, Set<TargetIntent.Ref.Existing> removed, Set<TargetIntent.Ref> affected) {
    public ExpectedTarget { TargetIntent.bound(entities.size(), 20_000); TargetIntent.bound(edges.size(), 50_000); TargetIntent.bound(removed.size(), 20_000); TargetIntent.bound(affected.size(), 40_000);
        entities = List.copyOf(entities); edges = List.copyOf(edges); removed = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(removed.stream().sorted(TargetIntentCompiler.REFERENCES).toList())); affected = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(affected.stream().sorted(TargetIntentCompiler.REFERENCES).toList())); }
    public record Entity(TargetIntent.Ref reference, ObservedGraph.Key identity, Map<String, String> fields) {
        public Entity { TargetIntent.bound(fields.size(), 256); fields = Collections.unmodifiableMap(new TreeMap<>(fields)); }
        @Override public String toString() { return "ExpectedEntity[redacted]"; }
    }
    public record Edge(String relation, TargetIntent.Ref source, TargetIntent.Ref target) { @Override public String toString() { return "ExpectedEdge[redacted]"; } }
    @Override public String toString() { return "ExpectedTarget[redacted]"; }
}
