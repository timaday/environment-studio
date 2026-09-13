package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.workspace.DraftCommand;

/** Independently invented immutable donor/recipient publications in real SQLite. */
class IndependentV3PlanLookupTest {
    @Test void donorQualificationWithdrawalCannotBeHiddenByStillQualifiedCompatibleRecipient() throws Exception {
        var fixture = new V3PlanWorkspaceBridgeTest();
        fixture.setup();
        try {
            String source = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
            var donor = fixture.publish(source, DraftCommand.Format.JSON);
            var recipient = fixture.publish(source.replace("mock-pg", "recipient-pg"), DraftCommand.Format.JSON);
            var profile = fixture.profile(donor, DraftCommand.Format.JSON);
            var selected = fixture.bridge().definition(fixture.owner, V3PlanWorkspaceBridgeTest.reference(recipient));
            var original = fixture.bridge().definition(fixture.owner, V3PlanWorkspaceBridgeTest.reference(donor));
            assertEquals(original.checked().logicalDigest(), selected.checked().logicalDigest());
            assertNotEquals(original.checked().bindingDigests(), selected.checked().bindingDigests());
            var withdrawn = new AtomicBoolean();
            var profileCalls = new AtomicInteger();
            var compiler = new V3ProfileWorkspaceCompiler();
            var bridge = new V3PlanWorkspaceBridge(fixture.store, command -> {
                if (withdrawn.get() && command.objectId().equals(donor.objectId())) {
                    return fixture.actual.definition(command);
                }
                return fixture.witness(command);
            }, (command, definition) -> {
                profileCalls.incrementAndGet();
                return compiler.profile(command, definition);
            });
            assertEquals(profile.publication().orElseThrow().digest(), bridge.profile(fixture.owner,
                    V3PlanWorkspaceBridgeTest.reference(profile), selected).publicationDigest());
            assertEquals(2, profileCalls.get());
            var before = fixture.counts();
            withdrawn.set(true);
            assertEquals(selected, bridge.definition(fixture.owner, selected.reference()));
            var refusal = assertThrows(PlanRefusal.class, () -> bridge.profile(fixture.owner,
                    V3PlanWorkspaceBridgeTest.reference(profile), selected));
            assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION, refusal.code());
            assertEquals(2, profileCalls.get(), "Refused donor must stop before any profile value work.");
            assertEquals(before, fixture.counts());
            withdrawn.set(false);
            assertEquals(profile.publication().orElseThrow().digest(), bridge.profile(fixture.owner,
                    V3PlanWorkspaceBridgeTest.reference(profile), selected).publicationDigest());
            assertEquals(4, profileCalls.get());
        } finally {
            fixture.cleanup();
        }
    }
}
