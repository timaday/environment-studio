package studio.environment.core.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.*;
import studio.environment.core.session.Owner;

class DraftWorkspaceTest {
    final Owner owner = new Owner("https://mock.invalid", "invented-owner");
    final DraftCommand command = new DraftCommand("00000000-0000-4000-8000-000000000001", "0",
            "00000000-0000-4000-8000-000000000002", DraftCommand.Format.JSON, "independent display-only source");
    static class Store implements DraftStore {
        SavedDraft replay;
        int saves;
        public Optional<SavedDraft> replay(Owner owner, DraftCommand command) { return Optional.ofNullable(replay); }
        public SavedDraft save(Owner owner, DraftCommand command, DefinitionResult.Incomplete projection) { saves++; throw new AssertionError("Unexpected save"); }
        public SavedDraft read(Owner owner, String id, Optional<String> revision) { throw new UnsupportedOperationException(); }
        public List<Summary> list(Owner owner) { return List.of(); }
    }
    @Test void replayReturnsOriginalResultWithoutInvokingTodaysCompiler() {
        var store = new Store();
        store.replay = new SavedDraft(command.objectId(), "1", "invented-digest", command.format(), command.source(), new DefinitionResult.Incomplete(
                new DefinitionDraft("1", "mote", java.math.BigInteger.ONE, DefinitionDraft.Status.DRAFT, List.of(), List.of(), List.of(), List.of(), List.of()),
                List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PUBLICATION, "MOCK_BLOCKER", "", "Declare mock semantics."))), "original", "1");
        var service = new DraftWorkspace(store, input -> { throw new AssertionError("Replay must never recompile"); });
        assertEquals(new DraftWorkspace.Saved(store.replay), service.put(owner, command));
    }
    @Test void rejectionCannotReachPersistence() {
        var store = new Store();
        var rejection = new DefinitionResult.Rejected(List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SHAPE,
                "MOCK_REJECTED", "", "Supply a declared shape.")));
        var service = new DraftWorkspace(store, input -> rejection);
        assertEquals(new DraftWorkspace.Rejected(rejection), service.put(owner, command));
        assertEquals(0, store.saves);
    }
}
