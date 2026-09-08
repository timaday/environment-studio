package studio.environment.core.workspace;

import studio.environment.core.definition.DefinitionResult;

public record SavedDraft(String objectId, String workspaceRevision, String sourceDigest, DraftCommand.Format format,
        String source, DefinitionResult.Incomplete projection, String compilerVersion, String schemaVersion) {
    public SavedDraft {
        java.util.Objects.requireNonNull(objectId); java.util.Objects.requireNonNull(workspaceRevision);
        java.util.Objects.requireNonNull(sourceDigest); java.util.Objects.requireNonNull(format);
        java.util.Objects.requireNonNull(source); java.util.Objects.requireNonNull(projection);
        java.util.Objects.requireNonNull(compilerVersion); java.util.Objects.requireNonNull(schemaVersion);
    }
    @Override public String toString() { return "SavedDraft[content=REDACTED]"; }
}
