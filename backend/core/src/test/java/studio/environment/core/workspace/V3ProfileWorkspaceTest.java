package studio.environment.core.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.session.Owner;
import studio.environment.core.profile.Profile;
import studio.environment.core.profile.ProfileResult;

class V3ProfileWorkspaceTest {
    static final Owner OWNER = V3NativeWorkspaceTest.OWNER;
    static final NativeCommand.Reference REF = new NativeCommand.Reference("33333333-3333-4333-8333-333333333333", "2");
    static NativeCommand.SaveProfile command(String revision) {
        return new NativeCommand.SaveProfile(V3NativeWorkspaceTest.ID, revision, V3NativeWorkspaceTest.REQUEST,
                DraftCommand.Format.YAML, "mock é 😀\r\n", REF);
    }
    static V3NativeRevision.Profile profile() {
        return new V3NativeRevision.Profile(new ProfileResult.Checked(new Profile("neutral", BigInteger.ONE,
                "a".repeat(64), List.of(new Profile.Entity("slot", "type", "Neutral", List.of())), List.of()), "b".repeat(64)), REF);
    }
    static V3NativeRevision historical() {
        var content = new V3NativeRevision.Definition(V3NativeWorkspaceTest.content().checked(), List.of());
        return new V3NativeRevision(REF.objectId(), "2", DraftCommand.Format.JSON, "mock", "c".repeat(64),
                "native-compiler-v3", "3", content, Optional.of(new V3NativeRevision.Publication("d".repeat(64), "1", List.of())));
    }
    static class Store implements V3NativeStore {
        V3NativeRevision history = historical(), replayed;
        int reads, appends; final List<String> calls = new ArrayList<>();
        public Optional<V3NativeRevision> replay(Owner owner, NativeCommand command) { calls.add("replay"); return Optional.ofNullable(replayed); }
        public V3NativeRevision read(Owner owner, String id, Optional<String> revision, boolean profile) {
            reads++; calls.add("read"); assertEquals(OWNER, owner); assertEquals(REF.objectId(), id); assertEquals(Optional.of("2"), revision); assertFalse(profile); return history;
        }
        public V3NativeRevision append(Owner owner, NativeCommand command, V3NativeRevision revision) {
            appends++; calls.add("append"); return revision;
        }
        public List<V3NativeRevision> list(Owner owner, boolean profile) { throw new AssertionError("Unexpected listing."); }
    }
    @Test void exactReplayHasNoReferenceLookupCompileOrAppend() {
        var store = new Store(); store.replayed = historical();
        var service = new V3ProfileWorkspace(store, (c, d) -> { throw new AssertionError("Replay compiled."); });
        assertSame(store.replayed, service.saveProfile(OWNER, command("1")));
        assertEquals(List.of("replay"), store.calls); assertEquals(0, store.reads); assertEquals(0, store.appends);
    }
    @Test void draftHasExactSourceDigestBoundedNextRevisionAndNoPublication() {
        var store = new Store(); var content = profile(); var command = command("4");
        var service = new V3ProfileWorkspace(store, (c, d) -> { store.calls.add("compile"); assertSame(command, c); assertSame(((V3NativeRevision.Definition) store.history.content()).checked(), d); return content; });
        var result = service.saveProfile(OWNER, command);
        assertEquals(List.of("replay", "read", "compile", "append"), store.calls);
        assertEquals("5", result.workspaceRevision()); assertEquals(command.source(), result.source()); assertEquals(command.format(), result.format());
        assertEquals("1282ac5eeb34a16554ec816a5d13c0d05303ce3932f66fb68117ea754a21ddc3", result.sourceDigest());
        assertEquals("profile-compiler-v3", result.compilerVersion()); assertEquals("3", result.schemaVersion());
        assertSame(content, result.content()); assertTrue(result.publication().isEmpty());
    }
    @Test void absentPublicationIncompleteAndUnsupportedHistoryCannotCompile() {
        var actual = historical();
        var histories = List.of(
                new V3NativeRevision(actual.objectId(), "2", actual.format(), actual.source(), actual.sourceDigest(), "native-compiler-v3", "3", actual.content(), Optional.empty()),
                new V3NativeRevision(actual.objectId(), "2", actual.format(), actual.source(), actual.sourceDigest(), "native-compiler-v3", "3", V3NativeWorkspaceTest.content(), actual.publication()),
                new V3NativeRevision(actual.objectId(), "2", actual.format(), actual.source(), actual.sourceDigest(), "future-compiler", "3", actual.content(), actual.publication()));
        var expected = List.of("DEFINITION_NOT_PUBLISHED", "DEFINITION_INCOMPLETE", "UNSUPPORTED_COMPILER");
        for (int i = 0; i < histories.size(); i++) {
            var store = new Store(); store.history = histories.get(i);
            var service = new V3ProfileWorkspace(store, (c, d) -> { throw new AssertionError("Ineligible history compiled."); });
            assertEquals(expected.get(i), assertThrows(WorkspaceRejection.class, () -> service.saveProfile(OWNER, command("0"))).diagnostics().getFirst().code());
            assertEquals(0, store.appends);
        }
    }
    @Test void overflowingRevisionRefusesBeforeLookupAndCompilerResultCannotReplaceReference() {
        var store = new Store();
        var noCompile = new V3ProfileWorkspace(store, (c, d) -> { throw new AssertionError("Overflow compiled."); });
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class, () -> noCompile.saveProfile(OWNER, command("9".repeat(1024)))).code());
        assertEquals(0, store.reads);
        for (var compiler : List.<V3ProfileWorkspace.Compiler>of((c, d) -> null, (c, d) -> new V3NativeRevision.Profile(profile().checked(),
                new NativeCommand.Reference(REF.objectId(), "3")))) {
            var service = new V3ProfileWorkspace(store, compiler);
            assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class, () -> service.saveProfile(OWNER, command("0"))).code());
        }
        assertEquals(0, store.appends);
    }
    @Test void appendFailureIsNotRetriedAndDoesNotProduceSuccess() {
        var failure = new WorkspaceRefusal(WorkspaceRefusal.Code.CONFLICT);
        var store = new Store() {
            public V3NativeRevision append(Owner owner, NativeCommand command, V3NativeRevision revision) {
                super.append(owner, command, revision); throw failure;
            }
        };
        var service = new V3ProfileWorkspace(store, (c, d) -> profile());
        assertSame(failure, assertThrows(WorkspaceRefusal.class, () -> service.saveProfile(OWNER, command("0"))));
        assertEquals(1, store.appends);
    }
}
