package studio.environment.core.workspace;

import java.util.List;
import java.util.Optional;
import studio.environment.core.definition.DefinitionResult;
import studio.environment.core.session.Owner;

public interface DraftStore {
    Optional<SavedDraft> replay(Owner owner, DraftCommand command);
    SavedDraft save(Owner owner, DraftCommand command, DefinitionResult.Incomplete projection);
    SavedDraft read(Owner owner, String objectId, Optional<String> revision);
    List<Summary> list(Owner owner);
    record Summary(String objectId, String workspaceRevision, String nativeId, String nativeRevision, String sourceDigest) {
        @Override public String toString() { return "DefinitionSummary[content=REDACTED]"; }
    }
}
