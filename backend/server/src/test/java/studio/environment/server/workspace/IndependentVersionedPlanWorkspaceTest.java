package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.PublishedDefinition;
import studio.environment.core.workspace.*;

class IndependentVersionedPlanWorkspaceTest {
    @Test void selectedCheckedPinsCannotBeReplacedWithFreshLookupMetadata() throws Exception {
        var fixture = new V3PlanWorkspaceBridgeTest();
        fixture.setup();
        try {
            var definition = V3ProfileHttpFixtures.definition(fixture.directory, fixture.owner, false);
            var profile = fixture.profile(definition, DraftCommand.Format.JSON);
            var bridge = new VersionedPlanWorkspace(VersionedPlanWorkspaceTest.NO_V2, fixture.bridge());
            var selected = bridge.definitionV3(fixture.owner, V3PlanWorkspaceBridgeTest.reference(definition));
            var checked = ((PlanDefinition.V3) selected.model()).checked();
            var changed = new TreeMap<>(checked.bindingDigests());
            changed.replaceAll((key, value) -> "0".repeat(64));
            var forged = new PublishedDefinition(selected.reference(), selected.publicationDigest(),
                    new PlanDefinition.V3(new NativeCompilationResult.Checked(checked.definition(), checked.logicalDigest(), changed, checked.mechanisms())),
                    selected.policies());
            var before = fixture.counts();
            assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION, assertThrows(PlanRefusal.class,
                    () -> bridge.profileV3(fixture.owner, V3PlanWorkspaceBridgeTest.reference(profile), forged)).code());
            assertEquals(((V3NativeRevision.Profile) profile.content()).checked(),
                    bridge.profileV3(fixture.owner, V3PlanWorkspaceBridgeTest.reference(profile), selected).checked());
            assertEquals(before, fixture.counts());
        } finally { fixture.cleanup(); }
    }

    @Test void formerlyQualifiedSelectionCannotCacheAuthorityForLaterProfileRead() throws Exception {
        var fixture = new V3PlanWorkspaceBridgeTest();
        fixture.setup();
        try {
            var definition = V3ProfileHttpFixtures.definition(fixture.directory, fixture.owner, false);
            var profile = fixture.profile(definition, DraftCommand.Format.JSON);
            var qualification = new AtomicBoolean(true);
            var current = new V3PlanWorkspaceBridge(fixture.store,
                    command -> qualification.get() ? fixture.witness(command) : fixture.actual.definition(command),
                    new V3ProfileWorkspaceCompiler());
            var bridge = new VersionedPlanWorkspace(VersionedPlanWorkspaceTest.NO_V2, current);
            var selected = bridge.definitionV3(fixture.owner, V3PlanWorkspaceBridgeTest.reference(definition));
            assertNotNull(bridge.profileV3(fixture.owner, V3PlanWorkspaceBridgeTest.reference(profile), selected));
            var before = fixture.counts();
            qualification.set(false);
            for (int attempt = 0; attempt < 2; attempt++) {
                assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION, assertThrows(PlanRefusal.class,
                        () -> bridge.profileV3(fixture.owner, V3PlanWorkspaceBridgeTest.reference(profile), selected)).code());
            }
            assertEquals(List.of("MECHANISM_UNQUALIFIED"), fixture.actual.definition(new NativeCommand.SaveDefinition(
                    definition.objectId(), definition.workspaceRevision(), java.util.UUID.randomUUID().toString(), definition.format(), definition.source()))
                    .diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
            assertEquals(before, fixture.counts());
        } finally { fixture.cleanup(); }
    }
}
