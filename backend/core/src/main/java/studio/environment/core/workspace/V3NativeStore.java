package studio.environment.core.workspace;

import java.util.List;
import java.util.Optional;
import studio.environment.core.session.Owner;

/** Versioned immutable history in the shared owner/UUID catalog. */
public interface V3NativeStore {
    Optional<V3NativeRevision> replay(Owner owner, NativeCommand command);
    V3NativeRevision append(Owner owner, NativeCommand command, V3NativeRevision revision);
    V3NativeRevision read(Owner owner, String objectId, Optional<String> revision, boolean profile);
    List<V3NativeRevision> list(Owner owner, boolean profile);
}
