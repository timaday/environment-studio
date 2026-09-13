package studio.environment.server.planning;

import java.util.List;
import java.util.Set;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent.Ref;

public sealed interface MaterializationResult {
    record Complete(List<TargetSource> documents, ObservedGraph graph, Set<Ref> affectedEntities, Set<String> affectedDocuments) implements MaterializationResult {
        public Complete { if (documents.size() > 128 || affectedEntities.size() > 40_000 || affectedDocuments.size() > 128) throw new IllegalArgumentException("Target result limit exceeded.");
            documents = List.copyOf(documents); affectedEntities = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(affectedEntities.stream().sorted(studio.environment.core.planning.TargetIntentCompiler.REFERENCES).toList())); affectedDocuments = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(affectedDocuments)); }
        @Override public String toString() { return "CompleteTarget[redacted]"; }
    }
    record Rejected(List<String> codes) implements MaterializationResult {
        public Rejected { if (codes.size() > 256) throw new IllegalArgumentException("Diagnostic limit exceeded."); codes = codes.stream().distinct().sorted().toList(); }
    }
    static Rejected reject(String code) { return new Rejected(List.of(code)); }
}
