package studio.environment.core.workspace;

import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.session.Owner;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/** Owned value-free draft validation only; no publication authority. */
public final class V3ProfileWorkspace {
    public interface Compiler {
        V3NativeRevision.Profile profile(NativeCommand.SaveProfile command, NativeCompilationResult.Checked definition);
    }
    private final V3NativeStore store;
    private final Compiler compiler;
    public V3ProfileWorkspace(V3NativeStore store, Compiler compiler) {
        this.store = Objects.requireNonNull(store); this.compiler = Objects.requireNonNull(compiler);
    }
    public V3NativeRevision saveProfile(Owner owner, NativeCommand.SaveProfile command) {
        if (owner == null || command == null) throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);
        var replay = store.replay(owner, command);
        if (replay.isPresent()) return replay.orElseThrow();
        String next = new BigInteger(command.expectedRevision()).add(BigInteger.ONE).toString();
        if (!NativeCommand.revision(next, false)) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
        var reference = command.definition();
        var historical = store.read(owner, reference.objectId(), Optional.of(reference.workspaceRevision()), false);
        if (!historical.schemaVersion().equals("3") || !historical.compilerVersion().equals("native-compiler-v3"))
            throw WorkspaceRejection.publication("UNSUPPORTED_COMPILER");
        if (historical.publication().isEmpty()) throw WorkspaceRejection.publication("DEFINITION_NOT_PUBLISHED");
        if (!(historical.content() instanceof V3NativeRevision.Definition definition))
            throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);
        if (!definition.historicalReady()) throw WorkspaceRejection.publication("DEFINITION_INCOMPLETE");
        var content = compiler.profile(command, definition.checked());
        if (content == null || !content.definition().equals(reference)) throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);
        var revision = new V3NativeRevision(command.objectId(), next, command.format(), command.source(),
                V3NativeWorkspaceDigests.source(command.source()), "profile-compiler-v3", "3", content, Optional.empty());
        return store.append(owner, command, revision);
    }
}
