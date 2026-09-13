package studio.environment.core.workspace;

import studio.environment.core.definition.DefinitionResult;
import studio.environment.core.session.Owner;

public final class DraftWorkspace {
    @FunctionalInterface public interface Compiler { DefinitionResult compile(DraftCommand command); }
    public sealed interface PutResult permits Saved, Rejected { }
    public record Saved(SavedDraft draft) implements PutResult {
        @Override public String toString() { return "Saved[content=REDACTED]"; }
    }
    public record Rejected(DefinitionResult.Rejected rejection) implements PutResult { }
    private final DraftStore store;
    private final Compiler compiler;
    public DraftWorkspace(DraftStore store, Compiler compiler) {
        this.store = java.util.Objects.requireNonNull(store);
        this.compiler = java.util.Objects.requireNonNull(compiler);
    }
    public PutResult put(Owner owner, DraftCommand command) {
        var replay = store.replay(owner, command);
        if (replay.isPresent()) return new Saved(replay.orElseThrow());
        return switch (compiler.compile(command)) {
            case DefinitionResult.Rejected rejected -> new Rejected(rejected);
            case DefinitionResult.Incomplete incomplete -> new Saved(store.save(owner, command, incomplete));
        };
    }
}
