package studio.environment.core.graph;

import java.util.Comparator;
import java.util.List;

public sealed interface GraphValidationResult {
    record Accepted(ObservedGraph graph) implements GraphValidationResult {
        @Override public String toString() { return "Accepted[redacted]"; }
    }
    record Rejected(List<GraphDiagnostic> diagnostics) implements GraphValidationResult {
        public Rejected { diagnostics = diagnostics.stream().distinct().sorted(Comparator.comparing(GraphDiagnostic::documentId)
                .thenComparing(GraphDiagnostic::projectionId).thenComparing(GraphDiagnostic::declarationId)
                .thenComparing(GraphDiagnostic::code)).toList();
            if (diagnostics.isEmpty()) throw new IllegalArgumentException("A refusal requires diagnostics."); }
    }
}
