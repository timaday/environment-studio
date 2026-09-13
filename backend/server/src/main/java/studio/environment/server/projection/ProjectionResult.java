package studio.environment.server.projection;

import java.util.List;
import studio.environment.core.graph.GraphDiagnostic;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;

public sealed interface ProjectionResult {
    record ExactDocument(String documentId, String source, String digest) {
        @Override public String toString() { return "ExactDocument[redacted]"; }
    }
    record TransientProjection(List<ExactDocument> documents) {
        public TransientProjection {
            if (documents.size() > 128) throw new IllegalArgumentException("Document limit exceeded.");
            documents = List.copyOf(documents);
        }
        @Override public String toString() { return "TransientProjection[redacted]"; }
    }
    record Accepted(ObservedGraph graph, TransientProjection projection, String logicalDigest, String bindingDigest)
            implements ProjectionResult {
        @Override public String toString() { return "Accepted[redacted]"; }
    }
    record Rejected(List<GraphDiagnostic> diagnostics) implements ProjectionResult {
        public Rejected { diagnostics = new GraphValidationResult.Rejected(diagnostics).diagnostics(); }
    }
}
