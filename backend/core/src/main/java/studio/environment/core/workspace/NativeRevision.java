package studio.environment.core.workspace;

import java.util.*;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.profile.ProfileResult;

/** Immutable stored result. Compiler readiness never substitutes for a publication. */
public record NativeRevision(String objectId, String workspaceRevision, DraftCommand.Format format, String source,
        String sourceDigest, String compilerVersion, String schemaVersion, Content content, Optional<Publication> publication) {
    public NativeRevision {
        Objects.requireNonNull(objectId); Objects.requireNonNull(workspaceRevision); Objects.requireNonNull(format);
        Objects.requireNonNull(source); Objects.requireNonNull(sourceDigest); Objects.requireNonNull(compilerVersion);
        Objects.requireNonNull(schemaVersion); Objects.requireNonNull(content); Objects.requireNonNull(publication);
    }
    public sealed interface Content { String nativeId(); String nativeRevision(); }
    public record Definition(NativeCompilationResult.Checked checked, List<DefinitionDiagnostic> diagnostics) implements Content {
        public Definition { Objects.requireNonNull(checked); diagnostics = List.copyOf(diagnostics); }
        public String nativeId() { return checked.definition().id(); }
        public String nativeRevision() { return checked.definition().revision().toString(); }
        public boolean ready() { return diagnostics.isEmpty(); }
        @Override public String toString() { return "DefinitionContent[redacted]"; }
    }
    public record Profile(ProfileResult.Checked checked, NativeCommand.Reference definition) implements Content {
        public Profile { Objects.requireNonNull(checked); Objects.requireNonNull(definition); }
        public String nativeId() { return checked.profile().id(); }
        public String nativeRevision() { return checked.profile().revision().toString(); }
        @Override public String toString() { return "ProfileContent[redacted]"; }
    }
    public record Publication(String digest, String sourceRevision, List<NativeCommand.Policy> exportPolicies) {
        public Publication { Objects.requireNonNull(digest); Objects.requireNonNull(sourceRevision); exportPolicies = NativeCommand.sorted(exportPolicies); }
        @Override public String toString() { return "Publication[redacted]"; }
    }
    public boolean profile() { return content instanceof Profile; }
    public String state() { return publication.isPresent() ? "published" : "draft"; }
    @Override public String toString() { return "NativeRevision[redacted]"; }
}
