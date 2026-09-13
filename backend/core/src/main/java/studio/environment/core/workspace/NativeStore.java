package studio.environment.core.workspace;

import java.util.*;
import studio.environment.core.session.Owner;

public interface NativeStore {
    Optional<NativeRevision> replay(Owner owner, NativeCommand command);
    NativeRevision append(Owner owner, NativeCommand command, NativeRevision revision);
    NativeRevision read(Owner owner, String objectId, Optional<String> revision, boolean profile);
    List<NativeRevision> list(Owner owner, boolean profile);
}
