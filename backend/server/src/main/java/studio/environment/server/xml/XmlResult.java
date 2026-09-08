package studio.environment.server.xml;

import java.util.List;
import java.util.Objects;

public sealed interface XmlResult permits XmlResult.Accepted, XmlResult.Rejected {
    record Diagnostic(String code, String message) { }
    record Accepted(XmlDocument document) implements XmlResult {
        public Accepted { Objects.requireNonNull(document); }
    }
    record Rejected(List<Diagnostic> diagnostics) implements XmlResult {
        public Rejected { diagnostics = List.copyOf(diagnostics);
            if (diagnostics.isEmpty()) throw new IllegalArgumentException("A refusal requires diagnostics."); }
    }
}
