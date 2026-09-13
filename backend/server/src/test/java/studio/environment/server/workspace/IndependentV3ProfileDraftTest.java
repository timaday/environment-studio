package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.environment.core.workspace.*;

class IndependentV3ProfileDraftTest {
    private V3ProfileWorkspaceTest fixture;
    @BeforeEach void setup() throws Exception {
        fixture = new V3ProfileWorkspaceTest();
        fixture.setup();
    }
    @AfterEach void cleanup() throws Exception { fixture.cleanup(); }

    @Test void fullRevisionCapacityPreservesOriginalReplayAndRefusesNewRequest() throws Exception {
        var definition = fixture.historicalDefinition();
        var first = fixture.command(definition, fixture.source(), DraftCommand.Format.JSON);
        var service = fixture.service(fixture.store);
        var original = service.saveProfile(fixture.owner, first);
        for (int previous = 1; previous < 32; previous++) {
            var next = new NativeCommand.SaveProfile(first.objectId(), Integer.toString(previous),
                    UUID.randomUUID().toString(), first.format(), first.source(), first.definition());
            assertEquals(Integer.toString(previous + 1), service.saveProfile(fixture.owner, next).workspaceRevision());
        }
        var over = new NativeCommand.SaveProfile(first.objectId(), "32", UUID.randomUUID().toString(),
                first.format(), first.source(), first.definition());
        assertEquals(WorkspaceRefusal.Code.CAPACITY,
                assertThrows(WorkspaceRefusal.class, () -> service.saveProfile(fixture.owner, over)).code());
        assertTrue(fixture.store.replay(fixture.owner, over).isEmpty());
        assertEquals("32", fixture.store.read(fixture.owner, first.objectId(), Optional.empty(), true).workspaceRevision());
        assertEquals(original, service.saveProfile(fixture.owner, first));
    }

    @Test void legalSourceThatExpandsBeyondSnapshotBudgetDoesNotPersist() throws Exception {
        var definition = fixture.historicalDefinition();
        String source = fixture.source();
        String expanded = "\t".repeat(1_048_576 - StrictUtf8.encode(source).length) + source;
        assertEquals(1_048_576, StrictUtf8.encode(expanded).length);
        var command = fixture.command(definition, expanded, DraftCommand.Format.JSON);
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE,
                assertThrows(WorkspaceRefusal.class, () -> fixture.service(fixture.store)
                        .saveProfile(fixture.owner, command)).code());
        assertTrue(fixture.store.list(fixture.owner, true).isEmpty());
        assertTrue(fixture.store.replay(fixture.owner, command).isEmpty());
    }

    @Test void validDifferentNativeIdCannotReplaceOwnedProfile() throws Exception {
        var definition = fixture.historicalDefinition();
        var first = fixture.command(definition, fixture.source(), DraftCommand.Format.JSON);
        var service = fixture.service(fixture.store);
        var original = service.saveProfile(fixture.owner, first);
        String changed = first.source().replace("mock-profile", "independent-profile");
        assertNotEquals(first.source(), changed);
        var second = new NativeCommand.SaveProfile(first.objectId(), "1", UUID.randomUUID().toString(),
                first.format(), changed, first.definition());
        assertEquals(WorkspaceRefusal.Code.CONFLICT,
                assertThrows(WorkspaceRefusal.class, () -> service.saveProfile(fixture.owner, second)).code());
        assertEquals(original, fixture.store.read(fixture.owner, first.objectId(), Optional.empty(), true));
        assertTrue(fixture.store.replay(fixture.owner, second).isEmpty());
    }
}
