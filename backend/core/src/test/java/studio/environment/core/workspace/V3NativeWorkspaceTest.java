package studio.environment.core.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.session.Owner;

class V3NativeWorkspaceTest {
    static final Owner OWNER = new Owner("https://mock.invalid", "invented-owner");
    static final String ID = "11111111-1111-4111-8111-111111111111";
    static final String REQUEST = "22222222-2222-4222-8222-222222222222";
    static NativeCommand.SaveDefinition command(String expected, String source) {
        return new NativeCommand.SaveDefinition(ID, expected, REQUEST, DraftCommand.Format.JSON, source);
    }
    static V3NativeRevision.Definition content() {
        var logical = new NativeDefinition.Logical(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var model = new NativeDefinition("neutral", BigInteger.ONE, logical, List.of());
        return new V3NativeRevision.Definition(new NativeCompilationResult.Checked(model, "a".repeat(64), Map.of(), Map.of()),
                List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PUBLICATION, "MECHANISM_UNQUALIFIED", "", "Incomplete.")));
    }
    static final class Store implements V3NativeStore {
        Optional<V3NativeRevision> replay = Optional.empty();
        V3NativeRevision appended;
        Owner owner;
        NativeCommand command;
        int replays, appends;
        WorkspaceRefusal replayFailure, appendFailure;
        public Optional<V3NativeRevision> replay(Owner owner, NativeCommand command) {
            replays++; this.owner = owner; this.command = command;
            if (replayFailure != null) throw replayFailure;
            return replay;
        }
        public V3NativeRevision append(Owner owner, NativeCommand command, V3NativeRevision revision) {
            appends++; assertSame(this.owner, owner); assertSame(this.command, command);
            if (appendFailure != null) throw appendFailure;
            appended = revision; return revision;
        }
        public V3NativeRevision read(Owner owner, String id, Optional<String> revision, boolean profile) { throw new AssertionError("No read during save."); }
        public List<V3NativeRevision> list(Owner owner, boolean profile) { throw new AssertionError("No list during save."); }
    }
    @Test void exactReplayReturnsHistoricalObjectBeforeAnyCompilationOrAppend() {
        var store = new Store();
        var history = new V3NativeRevision(ID, "2", DraftCommand.Format.JSON, "old", "b".repeat(64),
                "native-compiler-v3", "3", content(), Optional.empty());
        store.replay = Optional.of(history);
        var service = new V3NativeWorkspace(store, command -> { throw new AssertionError("Replay compiled."); });
        assertSame(history, assertDoesNotThrow(() -> service.saveDefinition(OWNER, command("1", "not new compilable input"))));
        assertEquals(1, store.replays); assertEquals(0, store.appends);
    }
    @Test void unseenCommandCreatesExactNextImmutableIncompleteDraft() {
        var store = new Store(); var content = content(); var command = command("4", "mock é 😀\r\n");
        var calls = new AtomicInteger();
        var service = new V3NativeWorkspace(store, supplied -> { calls.incrementAndGet(); assertSame(command, supplied); return content; });
        var revision = assertDoesNotThrow(() -> service.saveDefinition(OWNER, command));
        assertEquals("5", revision.workspaceRevision()); assertEquals(ID, revision.objectId());
        assertEquals(command.source(), revision.source()); assertEquals(command.format(), revision.format());
        assertEquals("1282ac5eeb34a16554ec816a5d13c0d05303ce3932f66fb68117ea754a21ddc3", revision.sourceDigest());
        assertEquals("native-compiler-v3", revision.compilerVersion()); assertEquals("3", revision.schemaVersion());
        assertSame(content, revision.content()); assertTrue(revision.publication().isEmpty());
        assertEquals(1, calls.get()); assertEquals(1, store.appends);
        assertThrows(UnsupportedOperationException.class, () -> ((V3NativeRevision.Definition) revision.content()).diagnostics().clear());
    }

    @Test void compilerRejectionLeavesStoreUntouched() {
        var store = new Store();
        var refusal = new WorkspaceRejection(List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.SEMANTIC,
                "INVALID_MODEL", "", "Refused.")));
        var service = new V3NativeWorkspace(store, command -> { throw refusal; });
        assertSame(refusal, assertThrows(WorkspaceRejection.class, () -> service.saveDefinition(OWNER, command("0", "mock"))));
        assertEquals(1, store.replays); assertEquals(0, store.appends); assertNull(store.appended);
    }
    @Test void ownerOrReplayRefusalPreventsCompilation() {
        var store = new Store(); store.replayFailure = new WorkspaceRefusal(WorkspaceRefusal.Code.NOT_FOUND);
        var service = new V3NativeWorkspace(store, command -> { throw new AssertionError("Refused owner compiled."); });
        assertSame(store.replayFailure, assertThrows(WorkspaceRefusal.class, () -> service.saveDefinition(OWNER, command("0", "mock"))));
        assertEquals(0, store.appends);
    }
    @Test void atomicAppendRefusalIsPropagatedWithoutRetryOrSuccess() {
        var store = new Store(); store.appendFailure = new WorkspaceRefusal(WorkspaceRefusal.Code.CONFLICT);
        var calls = new AtomicInteger();
        var service = new V3NativeWorkspace(store, command -> { calls.incrementAndGet(); return content(); });
        assertSame(store.appendFailure, assertThrows(WorkspaceRefusal.class, () -> service.saveDefinition(OWNER, command("0", "mock"))));
        assertEquals(1, store.replays); assertEquals(1, store.appends); assertEquals(1, calls.get()); assertNull(store.appended);
    }
    @Test void noUnboundedNextRevisionNullCompilerResultOrMalformedSourceCanAppend() {
        var store = new Store(); var calls = new AtomicInteger();
        var service = new V3NativeWorkspace(store, command -> { calls.incrementAndGet(); return content(); });
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class,
                () -> service.saveDefinition(OWNER, command("9".repeat(1024), "mock"))).code());
        assertEquals(0, calls.get());
        var emptyCompiler = new V3NativeWorkspace(store, command -> null);
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class,
                () -> emptyCompiler.saveDefinition(OWNER, command("0", "mock"))).code());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class,
                () -> service.saveDefinition(OWNER, command("0", "bad" + (char) 0xd800))).code());
        assertEquals(0, store.appends);
    }
}
