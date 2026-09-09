package studio.environment.core.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.profile.Profile;
import studio.environment.core.profile.ProfileResult;

class V3NativeHistoryTest {
    static final String ID = "11111111-1111-4111-8111-111111111111";
    static final String REQUEST = "22222222-2222-4222-8222-222222222222";
    @Test void versionedCommandUsesIndependentFramingAndPreservesOldDomain() {
        var command = new NativeCommand.SaveDefinition(ID, "0", REQUEST, DraftCommand.Format.JSON, "mock é 😀");
        assertEquals("8f4c1e6babcd980e179577da883f52e905e80d23790d734b7d5350748fedce6d", V3NativeWorkspaceDigests.commandDigest(command));
        assertEquals("ce378f410ea138f695b83459fa9e46b44d77677217425b05fcb408036681df55", NativeWorkspaceDigests.commandDigest(command));
    }
    @Test void profilePublicationHasIndependentVersionedDomainAndPinnedDefinition() {
        var profile = new Profile("neutral", BigInteger.ONE, "b".repeat(64), List.of(), List.of());
        var content = new V3NativeRevision.Profile(new ProfileResult.Checked(profile, "a".repeat(64)),
                new NativeCommand.Reference("33333333-3333-4333-8333-333333333333", "7"));
        var revision = new V3NativeRevision(ID, "2", DraftCommand.Format.JSON, "mock", V3NativeWorkspaceDigests.source("mock"),
                "profile-compiler-v3", "3", content, Optional.empty());
        assertEquals("409b74845b53e0b7149e7914df22dbe01e6b31b6788b878a81e4cf193a24d5eb",
                V3NativeWorkspaceDigests.publication(revision, "1", List.of()));
        assertFalse(revision.toString().contains("mock"));
        assertFalse(content.toString().contains("neutral"));
    }

    @Test void policyOrderIsCanonicalButEveryClosedCommandFieldStillParticipates() {
        var first = new NativeCommand.Policy("mock-a", "doc-a", "deny");
        var second = new NativeCommand.Policy("mock-b", "doc-b", "protected-self-contained");
        var command = new NativeCommand.PublishDefinition(ID, "1", REQUEST, List.of(first, second));
        String digest = V3NativeWorkspaceDigests.commandDigest(command);
        assertEquals(digest, V3NativeWorkspaceDigests.commandDigest(new NativeCommand.PublishDefinition(ID, "1", REQUEST, List.of(second, first))));
        assertNotEquals(digest, V3NativeWorkspaceDigests.commandDigest(new NativeCommand.PublishDefinition(ID, "2", REQUEST, List.of(first, second))));
        assertNotEquals(digest, V3NativeWorkspaceDigests.commandDigest(new NativeCommand.PublishDefinition(ID, "1", ID, List.of(first, second))));
        assertNotEquals(digest, V3NativeWorkspaceDigests.commandDigest(new NativeCommand.PublishProfile(ID, "1", REQUEST)));
        var ref = new NativeCommand.Reference("33333333-3333-4333-8333-333333333333", "1");
        var other = new NativeCommand.Reference(ref.objectId(), "2");
        assertNotEquals(V3NativeWorkspaceDigests.commandDigest(new NativeCommand.SaveProfile(ID, "0", REQUEST, DraftCommand.Format.JSON, "neutral", ref)),
                V3NativeWorkspaceDigests.commandDigest(new NativeCommand.SaveProfile(ID, "0", REQUEST, DraftCommand.Format.JSON, "neutral", other)));
    }
    @Test void malformedScalarCannotBeSilentlyReplacedInVersionedFraming() {
        String malformed = "invented" + (char) 0xd800;
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,
                assertThrows(WorkspaceRefusal.class, () -> V3NativeWorkspaceDigests.source(malformed)).code());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class,
                () -> V3NativeWorkspaceDigests.commandDigest(new NativeCommand.SaveDefinition(ID, "0", REQUEST,
                        DraftCommand.Format.JSON, malformed))).code());
    }
}
