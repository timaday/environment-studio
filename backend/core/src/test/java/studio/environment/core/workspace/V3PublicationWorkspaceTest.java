package studio.environment.core.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.session.Owner;

/** Pure application witnesses; no test compiler result qualifies production. */
class V3PublicationWorkspaceTest {
    final Owner owner = V3NativeWorkspaceTest.OWNER;
    static V3NativeRevision.Definition readyWitness() {
        return new V3NativeRevision.Definition(V3NativeWorkspaceTest.content().checked(), List.of());
    }
    static V3NativeRevision definition(String revision) {
        return new V3NativeRevision(V3NativeWorkspaceTest.ID, revision, DraftCommand.Format.JSON, "mock", V3NativeWorkspaceDigests.source("mock"),
                "native-compiler-v3", "3", readyWitness(), Optional.empty());
    }
    static class Store implements V3NativeStore {
        V3NativeRevision current = definition("1"), reference = V3ProfileWorkspaceTest.historical(), replayed;
        int appends, reads;
        WorkspaceRefusal failure;
        public Optional<V3NativeRevision> replay(Owner owner, NativeCommand command) { return Optional.ofNullable(replayed); }
        public V3NativeRevision read(Owner owner, String id, Optional<String> revision, boolean profile) {
            reads++; return revision.isPresent() ? reference : current;
        }
        public V3NativeRevision append(Owner owner, NativeCommand command, V3NativeRevision revision) {
            appends++; if (failure != null) throw failure; return revision;
        }
        public List<V3NativeRevision> list(Owner owner, boolean profile) { throw new AssertionError("Unexpected listing."); }
    }
    NativeCommand.PublishDefinition command(String expected) {
        return new NativeCommand.PublishDefinition(V3NativeWorkspaceTest.ID, expected, V3NativeWorkspaceTest.REQUEST, List.of());
    }
    V3PublicationWorkspace service(Store store, V3NativeWorkspace.Compiler compiler) {
        return new V3PublicationWorkspace(store, compiler, (c, d) -> { throw new AssertionError("Unexpected profile compile."); }, ignored -> true);
    }
    static void refuses(String expected, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(RuntimeException.class, action);
        String code = failure instanceof WorkspaceRefusal refused ? refused.code().name()
                : assertInstanceOf(WorkspaceRejection.class, failure).diagnostics().getFirst().code();
        assertEquals(expected, code);
    }
    @Test void absentAndChangedCurrentCompilerEvidenceCannotAuthorizePublication() {
        var changed = new studio.environment.core.definitionv3.NativeCompilationResult.Checked(readyWitness().checked().definition(),
                readyWitness().checked().logicalDigest(), Map.of(), Map.of("native-compiler-v3", java.math.BigInteger.valueOf(9)));
        var compilers = List.<V3NativeWorkspace.Compiler>of(c -> null, c -> new V3NativeRevision.Definition(changed, List.of()));
        for (int i = 0; i < compilers.size(); i++) {
            var store = new Store();
            var compiler = compilers.get(i);
            refuses(i == 0 ? "UNAVAILABLE" : "CURRENT_COMPILATION_MISMATCH", () -> service(store, compiler).publishDefinition(owner, command("1")));
            assertEquals(0, store.appends);
        }
    }
    @Test void overflowStalePublishedAndUnsupportedDraftsNeverRecompile() {
        var ordinary = definition("1");
        var published = new V3NativeRevision(ordinary.objectId(), "1", ordinary.format(), ordinary.source(), ordinary.sourceDigest(),
                ordinary.compilerVersion(), "3", ordinary.content(), Optional.of(new V3NativeRevision.Publication("a".repeat(64), "1", List.of())));
        var unsupported = new V3NativeRevision(ordinary.objectId(), "1", ordinary.format(), ordinary.source(), ordinary.sourceDigest(),
                "future-compiler", "3", ordinary.content(), Optional.empty());
        var currents = List.of(definition("9".repeat(1024)), published, unsupported, definition("2"));
        var codes = List.of("TOO_LARGE", "CONFLICT", "UNSUPPORTED_COMPILER", "CONFLICT");
        for (int i = 0; i < currents.size(); i++) {
            var current = currents.get(i);
            var store = new Store(); store.current = current;
            String expected = current.workspaceRevision().length() > 1 ? current.workspaceRevision() : "1";
            var compiler = service(store, c -> { throw new AssertionError("Ineligible draft compiled."); });
            refuses(codes.get(i), () -> compiler.publishDefinition(owner, command(expected)));
            assertEquals(0, store.appends);
        }
    }
    @Test void profileCompilerCannotReplaceExactReferenceOrCheckedProfile() {
        var original = V3ProfileWorkspaceTest.profile();
        var current = new V3NativeRevision(V3NativeWorkspaceTest.ID, "1", DraftCommand.Format.JSON, "mock", V3NativeWorkspaceDigests.source("mock"),
                "profile-compiler-v3", "3", original, Optional.empty());
        var changedReference = new V3NativeRevision.Profile(original.checked(), new NativeCommand.Reference(original.definition().objectId(), "3"));
        var changedProfile = new V3NativeRevision.Profile(new studio.environment.core.profile.ProfileResult.Checked(original.checked().profile(), "c".repeat(64)), original.definition());
        var compilers = List.<V3ProfileWorkspace.Compiler>of((c, d) -> null, (c, d) -> changedReference, (c, d) -> changedProfile);
        for (int i = 0; i < compilers.size(); i++) {
            var compiler = compilers.get(i);
            var store = new Store(); store.current = current;
            var service = new V3PublicationWorkspace(store, c -> readyWitness(), compiler, ignored -> false);
            refuses(i == 0 ? "UNAVAILABLE" : "CURRENT_COMPILATION_MISMATCH", () -> service.publishProfile(owner,
                    new NativeCommand.PublishProfile(current.objectId(), "1", V3NativeWorkspaceTest.REQUEST)));
            assertEquals(0, store.appends);
        }
    }
    @Test void exactAppendRefusalPropagatesOnceAndNullInputsCannotReachStore() {
        var store = new Store(); var failure = new WorkspaceRefusal(WorkspaceRefusal.Code.CAPACITY); store.failure = failure;
        var service = service(store, c -> readyWitness());
        assertSame(failure, assertThrows(WorkspaceRefusal.class, () -> service.publishDefinition(owner, command("1"))));
        assertEquals(1, store.appends);
        int reads = store.reads;
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class, () -> service.publishDefinition(null, command("1"))).code());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class, () -> service.publishDefinition(owner, null)).code());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class, () -> service.publishProfile(owner, null)).code());
        assertEquals(reads, store.reads); assertEquals(1, store.appends);
    }
}
