package studio.environment.core.graph;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact transient values; absence is a missing map key, distinct from present empty text. */
public record ObservedGraph(List<Entity> entities, List<Edge> edges) {
    public static final int MAX_ENTITIES = 20_000;
    public static final int MAX_EDGES = 50_000;
    public ObservedGraph { bound(entities.size(), MAX_ENTITIES); bound(edges.size(), MAX_EDGES);
        entities = List.copyOf(entities); edges = List.copyOf(edges); }
    public List<Origin> origins() {
        return entities.stream().map(Entity::origin).sorted(java.util.Comparator.comparing(Origin::documentId)
                .thenComparing(Origin::projectionId).thenComparingInt(Origin::elementIndex)).toList();
    }
    @Override public String toString() { return "ObservedGraph[redacted]"; }
    public record Key(String type, String identity) {
        @Override public String toString() { return "Key[redacted]"; }
    }
    public record Origin(String documentId, String projectionId, String sourceDigest, int elementIndex,
            List<Integer> ancestry) {
        public Origin { bound(ancestry.size(), 128); ancestry = List.copyOf(ancestry); }
        @Override public String toString() { return "Origin[redacted]"; }
    }
    public record Entity(Key key, Map<String, String> fields, Origin origin) {
        public Entity { fields = values(fields); }
        @Override public String toString() { return "Entity[redacted]"; }
    }
    public record Edge(String relation, Key source, Key target) {
        @Override public String toString() { return "Edge[redacted]"; }
    }
    public record Occurrence(String type, Map<String, String> fields, Map<String, String> references, Origin origin) {
        public Occurrence { fields = values(fields); references = values(references); }
        @Override public String toString() { return "Occurrence[redacted]"; }
    }
    private static Map<String, String> values(Map<String, String> values) {
        bound(values.size(), 256);
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
    private static void bound(int size, int maximum) {
        if (size > maximum) throw new IllegalArgumentException("Graph collection exceeds its resource limit.");
    }
}
