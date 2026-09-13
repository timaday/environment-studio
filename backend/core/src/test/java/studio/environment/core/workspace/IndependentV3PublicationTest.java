package studio.environment.core.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Independently invented application-port controls; no runtime qualification. */
class IndependentV3PublicationTest {
    V3PublicationWorkspaceTest.Store profileStore() {
        var store = new V3PublicationWorkspaceTest.Store();
        var source = V3ProfileWorkspaceTest.command("1");
        store.current = new V3NativeRevision(source.objectId(), "1", source.format(), source.source(),
                V3NativeWorkspaceDigests.source(source.source()), "profile-compiler-v3", "3",
                V3ProfileWorkspaceTest.profile(), Optional.empty());
        return store;
    }
    NativeCommand.PublishProfile command() {
        return new NativeCommand.PublishProfile(V3NativeWorkspaceTest.ID, "1", V3NativeWorkspaceTest.REQUEST);
    }
    @Test void profileReferenceEligibilityPrecedesBothCompilers() {
        var original = V3ProfileWorkspaceTest.historical();
        var cases = List.of(
                new V3NativeRevision(original.objectId(), "2", original.format(), original.source(), original.sourceDigest(),
                        original.compilerVersion(), "3", original.content(), Optional.empty()),
                new V3NativeRevision(original.objectId(), "2", original.format(), original.source(), original.sourceDigest(),
                        "future-compiler", "3", original.content(), original.publication()),
                new V3NativeRevision(original.objectId(), "2", original.format(), original.source(), original.sourceDigest(),
                        original.compilerVersion(), "3", V3NativeWorkspaceTest.content(), original.publication()));
        var codes = List.of("DEFINITION_NOT_PUBLISHED", "UNSUPPORTED_COMPILER", "DEFINITION_INCOMPLETE");
        for (int i = 0; i < cases.size(); i++) {
            var store = profileStore(); store.reference = cases.get(i);
            var service = new V3PublicationWorkspace(store,
                    c -> { fail("Ineligible reference reached definition compiler"); return null; },
                    (c, d) -> { fail("Ineligible reference reached profile compiler"); return null; }, ignored -> false);
            assertEquals(codes.get(i), assertThrows(WorkspaceRejection.class,
                    () -> service.publishProfile(V3NativeWorkspaceTest.OWNER, command())).diagnostics().getFirst().code());
            assertEquals(0, store.appends);
        }
    }
    @Test void profileReplayDoesNotReadHistoryOrRequireDefinitionMaintainer() {
        var store = profileStore(); store.replayed = store.current;
        var service = new V3PublicationWorkspace(store,
                c -> { fail("Replay recompiled definition"); return null; },
                (c, d) -> { fail("Replay recompiled profile"); return null; },
                ignored -> { fail("Profile owner was subjected to definition-maintainer policy"); return false; });
        assertSame(store.replayed, assertDoesNotThrow(() -> service.publishProfile(V3NativeWorkspaceTest.OWNER, command())));
        assertEquals(0, store.reads); assertEquals(0, store.appends);
    }
    @Test void profileCompileReceivesExactPinnedSourceAndReferenceOnly() {
        var store = profileStore();
        var profile = (V3NativeRevision.Profile) store.current.content();
        var definition = (V3NativeRevision.Definition) store.reference.content();
        var service = new V3PublicationWorkspace(store, c -> {
            assertEquals(store.reference.objectId(), c.objectId());
            assertEquals("2", c.expectedRevision());
            assertEquals(store.reference.source(), c.source());
            return definition;
        }, (c, d) -> {
            assertEquals(store.current.source(), c.source());
            assertEquals(store.current.format(), c.format());
            assertEquals(profile.definition(), c.definition());
            assertSame(definition.checked(), d);
            return profile;
        }, ignored -> false);
        var published = service.publishProfile(V3NativeWorkspaceTest.OWNER, command());
        assertEquals("2", published.workspaceRevision());
        assertEquals("1", published.publication().orElseThrow().sourceRevision());
        assertSame(profile, published.content());
        assertEquals(1, store.appends);
    }
}
