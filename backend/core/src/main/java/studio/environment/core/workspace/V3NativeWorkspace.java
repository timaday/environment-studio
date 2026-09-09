package studio.environment.core.workspace;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import studio.environment.core.session.Owner;

/** Internal v3 definition drafts only; no publication or profile mutation authority. */
public final class V3NativeWorkspace {
    public interface Compiler {
        V3NativeRevision.Definition definition(NativeCommand.SaveDefinition command);
    }
    private final V3NativeStore store;
    private final Compiler compiler;

    public V3NativeWorkspace(V3NativeStore store, Compiler compiler) {
        this.store = Objects.requireNonNull(store);
        this.compiler = Objects.requireNonNull(compiler);
    }

    public V3NativeRevision saveDefinition(Owner owner, NativeCommand.SaveDefinition command) {
        if (owner == null || command == null) throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);
        var replay = store.replay(owner, command);
        if (replay.isPresent()) return replay.orElseThrow();
        String next = new BigInteger(command.expectedRevision()).add(BigInteger.ONE).toString();
        if (!NativeCommand.revision(next, false)) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
        var content = compiler.definition(command);
        if (content == null) throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);
        var revision = new V3NativeRevision(command.objectId(), next, command.format(), command.source(),
                V3NativeWorkspaceDigests.source(command.source()), "native-compiler-v3", "3", content, Optional.empty());
        return store.append(owner, command, revision);
    }
}
