package studio.environment.core.profile;

import java.util.Comparator;
import java.util.List;

/** Core structural validity only. Portable bytes require the server profile adapter. */
public sealed interface ProfileResult {
    record Diagnostic(String code, String path) { }
    record Checked(Profile profile, String contentDigest) {
        @Override public String toString() { return "CheckedProfile[redacted]"; }
    }
    record StructurallyValid(Checked checked) implements ProfileResult {
        @Override public String toString() { return "StructurallyValidProfile[redacted]"; }
    }
    record Rejected(List<Diagnostic> diagnostics) implements ProfileResult {
        public Rejected { Profile.bound(diagnostics.size(), 256); diagnostics = diagnostics.stream().distinct().sorted(Comparator.comparing(Diagnostic::path).thenComparing(Diagnostic::code)).toList();
            if (diagnostics.isEmpty()) throw new IllegalArgumentException("A refusal requires diagnostics."); }
    }
    static Rejected rejected(String code, String path) { return new Rejected(List.of(new Diagnostic(code, path))); }
}
