package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import studio.environment.core.plan.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Independent invented history and explicit test-only qualification, never v3 availability. */
class V3PlanWorkspaceBridgeTest {
    Path directory;
    V3NativeSqliteStore store;
    final Owner owner = new Owner("https://invented.invalid", "plan-publications-owner");
    final V3NativeWorkspaceCompiler actual = new V3NativeWorkspaceCompiler();
    @BeforeEach void setup() throws Exception {
        directory = Files.createTempDirectory("es-v3-plan-publication-mock-");
        SqliteDraftStore.initializeV3(directory);
        store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
    }
    @AfterEach void cleanup() throws Exception {
        try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(directory);
    }
    V3NativeRevision.Definition witness(NativeCommand.SaveDefinition command) {
        var result = actual.definition(command);
        assertEquals(List.of("MECHANISM_UNQUALIFIED"), result.diagnostics().stream().map(d -> d.code()).toList());
        return new V3NativeRevision.Definition(result.checked(), List.of());
    }
    V3PlanWorkspaceBridge bridge() { return new V3PlanWorkspaceBridge(store, this::witness, new V3ProfileWorkspaceCompiler()); }
    static NativeCommand.Reference reference(V3NativeRevision revision) {
        return new NativeCommand.Reference(revision.objectId(), revision.workspaceRevision());
    }
    V3NativeRevision publish(String source, DraftCommand.Format format) {
        var draft = new V3NativeWorkspace(store, this::witness).saveDefinition(owner,
                new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), format, source));
        var policies = ((V3NativeRevision.Definition) draft.content()).checked().definition().bindings().stream()
                .flatMap(b -> b.documents().stream().map(d -> new NativeCommand.Policy(b.id(), d.id(), "deny"))).toList();
        return new V3PublicationWorkspace(store, this::witness, new V3ProfileWorkspaceCompiler(), ignored -> true)
                .publishDefinition(owner, new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), policies));
    }
    V3NativeRevision profile(V3NativeRevision definition, DraftCommand.Format format) throws Exception {
        String source = (format == DraftCommand.Format.YAML ? "---\n" : "") + V3ProfileHttpFixtures.source() + "\r\n";
        var draft = new V3ProfileWorkspace(store, new V3ProfileWorkspaceCompiler()).saveProfile(owner,
                new NativeCommand.SaveProfile(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), format, source, reference(definition)));
        return new V3PublicationWorkspace(store, this::witness, new V3ProfileWorkspaceCompiler(), ignored -> false)
                .publishProfile(owner, new NativeCommand.PublishProfile(draft.objectId(), "1", UUID.randomUUID().toString()));
    }
    Map<String, Long> counts() throws Exception {
        var result = new TreeMap<String, Long>();
        try (var connection = new SqliteDraftStore(directory).open(); var statement = connection.createStatement()) {
            for (var table : List.of("catalog", "v3_native_revisions", "v3_native_replays"))
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { assertTrue(rows.next()); result.put(table, rows.getLong(1)); }
        }
        return result;
    }
    static void refused(PlanRefusal.Code code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(PlanRefusal.class, operation).code());
    }
    @Test void exactOwnedPublicationIsRecompiledFromItsOriginalSourceAfterLaterDraftAndRestart() throws Exception {
        var original = V3ProfileHttpFixtures.definition(directory, owner, false);
        V3ProfileHttpFixtures.laterDefinition(directory, owner, original);
        var reopened = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var calls = new ArrayList<NativeCommand.SaveDefinition>();
        var lookup = new V3PlanWorkspaceBridge(reopened, command -> { calls.add(command); return witness(command); }, new V3ProfileWorkspaceCompiler());
        var pin = assertDoesNotThrow(() -> lookup.definition(owner, reference(original)));
        assertEquals(reference(original), pin.reference());
        assertEquals(original.publication().orElseThrow().digest(), pin.publicationDigest());
        assertEquals(((V3NativeRevision.Definition) original.content()).checked(), pin.checked());
        assertEquals(original.publication().orElseThrow().exportPolicies(), pin.policies());
        assertEquals(1, calls.size());
        assertEquals(original.source(), calls.getFirst().source());
        assertEquals(original.format(), calls.getFirst().format());
        assertEquals("2", calls.getFirst().expectedRevision());
        assertEquals("3", reopened.read(owner, original.objectId(), Optional.empty(), false).workspaceRevision());
        assertEquals("V3PlanDefinition[redacted]", pin.toString());
    }

    @Test void actualCompilerRefusesHistoricalPublicationAndNeverChangesHistory() throws Exception {
        var definition = V3ProfileHttpFixtures.definition(directory, owner, false);
        var publishedProfile = profile(definition, DraftCommand.Format.JSON);
        var selected = bridge().definition(owner, reference(definition));
        var before = counts();
        var actualLookup = new V3PlanWorkspaceBridge(store);
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION, () -> actualLookup.definition(owner, reference(definition)));
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION, () -> actualLookup.profile(owner, reference(publishedProfile), selected));
        assertEquals(before, counts());
        assertEquals(definition, store.read(owner, definition.objectId(), Optional.of("2"), false));
        assertEquals(publishedProfile, store.read(owner, publishedProfile.objectId(), Optional.of("2"), true));
    }

    @Test void immutableYamlProfileUsesExactSourceAndOriginalPublicationAfterBothObjectsAdvance() throws Exception {
        var definition = publish("---\n" + Files.readString(Path.of("../../fixtures/native-v3/definition.json")) + "\r\n", DraftCommand.Format.YAML);
        var publishedProfile = profile(definition, DraftCommand.Format.YAML);
        var selected = bridge().definition(owner, reference(definition));
        V3ProfileHttpFixtures.laterDefinition(directory, owner, definition);
        new V3ProfileWorkspace(store, new V3ProfileWorkspaceCompiler()).saveProfile(owner,
                new NativeCommand.SaveProfile(publishedProfile.objectId(), "2", UUID.randomUUID().toString(),
                        DraftCommand.Format.JSON, V3ProfileHttpFixtures.source().replace("Second neutral slot", "Later neutral slot"), reference(definition)));
        var calls = new ArrayList<NativeCommand.SaveProfile>();
        var actualProfiles = new V3ProfileWorkspaceCompiler();
        var lookup = new V3PlanWorkspaceBridge(new V3NativeSqliteStore(new SqliteDraftStore(directory)), this::witness,
                (command, checked) -> { calls.add(command); return actualProfiles.profile(command, checked); });
        var result = assertDoesNotThrow(() -> lookup.profile(owner, reference(publishedProfile), selected));
        assertEquals(reference(publishedProfile), result.reference());
        assertEquals(publishedProfile.publication().orElseThrow().digest(), result.publicationDigest());
        assertEquals(((V3NativeRevision.Profile) publishedProfile.content()).checked(), result.checked());
        assertEquals(2, calls.size());
        for (var call : calls) {
            assertEquals(publishedProfile.source(), call.source()); assertEquals(DraftCommand.Format.YAML, call.format());
            assertEquals(reference(definition), call.definition()); assertEquals("2", call.expectedRevision());
        }
        assertEquals("3", store.read(owner, publishedProfile.objectId(), Optional.empty(), true).workspaceRevision());
        assertEquals("V3PlanProfile[redacted]", result.toString());
    }

    @Test void compatibleOtherBindingIsAllowedAndDifferentLogicalDefinitionRefuses() throws Exception {
        String source = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        var donor = publish(source, DraftCommand.Format.JSON);
        var publishedProfile = profile(donor, DraftCommand.Format.JSON);
        var target = publish(source.replace("mock-pg", "different-pg"), DraftCommand.Format.JSON);
        var from = bridge().definition(owner, reference(donor));
        var into = bridge().definition(owner, reference(target));
        assertEquals(from.checked().logicalDigest(), into.checked().logicalDigest());
        assertNotEquals(from.checked().bindingDigests(), into.checked().bindingDigests());
        var calls = new ArrayList<String>(); var actualProfiles = new V3ProfileWorkspaceCompiler();
        var lookup = new V3PlanWorkspaceBridge(store, this::witness, (command, checked) -> {
            calls.add(checked.bindingDigests().keySet().toString()); return actualProfiles.profile(command, checked);
        });
        assertEquals(((V3NativeRevision.Profile) publishedProfile.content()).checked(),
                assertDoesNotThrow(() -> lookup.profile(owner, reference(publishedProfile), into)).checked());
        assertEquals(List.of(from.checked().bindingDigests().keySet().toString(), into.checked().bindingDigests().keySet().toString()), calls);
        var incompatible = publish(source.replace("\"minimum\": 0", "\"minimum\": 1"), DraftCommand.Format.JSON);
        var other = bridge().definition(owner, reference(incompatible));
        assertNotEquals(from.checked().logicalDigest(), other.checked().logicalDigest());
        refused(PlanRefusal.Code.PROFILE_REFUSED, () -> lookup.profile(owner, reference(publishedProfile), other));
    }

    @Test void missingForeignKindAndV2HistoryKeepNonDisclosingStoreRefusals() throws Exception {
        var definition = V3ProfileHttpFixtures.definition(directory, owner, false);
        var publishedProfile = profile(definition, DraftCommand.Format.JSON);
        var selected = bridge().definition(owner, reference(definition));
        var foreign = new Owner(owner.issuer(), "foreign-plan-owner");
        var v2 = new NativeWorkspace(new NativeSqliteStore(new SqliteDraftStore(directory)), new NativeWorkspaceCompiler(), ignored -> true)
                .mutate(owner, new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(),
                        DraftCommand.Format.JSON, Files.readString(Path.of("../../fixtures/native-v2/definition.json"))));
        var absent = new NativeCommand.Reference(UUID.randomUUID().toString(), "1");
        for (var operation : List.<org.junit.jupiter.api.function.Executable>of(
                () -> bridge().definition(foreign, reference(definition)), () -> bridge().definition(owner, absent),
                () -> bridge().definition(owner, reference(publishedProfile)),
                () -> bridge().definition(owner, new NativeCommand.Reference(v2.objectId(), "1")),
                () -> bridge().profile(owner, reference(definition), selected),
                () -> bridge().profile(foreign, reference(publishedProfile), selected)))
            assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class, operation).code());
        refused(PlanRefusal.Code.PUBLICATION_REQUIRED, () -> bridge().definition(owner, new NativeCommand.Reference(definition.objectId(), "1")));
        refused(PlanRefusal.Code.PUBLICATION_REQUIRED, () -> bridge().profile(owner, new NativeCommand.Reference(publishedProfile.objectId(), "1"), selected));
        refused(PlanRefusal.Code.INVALID_REQUEST, () -> bridge().definition(null, reference(definition)));
        refused(PlanRefusal.Code.INVALID_REQUEST, () -> bridge().definition(owner, null));
        refused(PlanRefusal.Code.INVALID_REQUEST, () -> bridge().profile(owner, reference(publishedProfile), null));
    }

    @Test void completeCurrentMetadataEqualityAndRepeatQualificationAreRequired() throws Exception {
        var definition = V3ProfileHttpFixtures.definition(directory, owner, false);
        var qualified = new java.util.concurrent.atomic.AtomicBoolean(true);
        var lookup = new V3PlanWorkspaceBridge(store, command -> qualified.get() ? witness(command) : actual.definition(command), new V3ProfileWorkspaceCompiler());
        assertDoesNotThrow(() -> lookup.definition(owner, reference(definition)));
        qualified.set(false);
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION, () -> lookup.definition(owner, reference(definition)));
        for (boolean mechanisms : List.of(true, false)) {
            var changed = new V3PlanWorkspaceBridge(store, command -> {
                var checked = witness(command).checked();
                var versions = new TreeMap<>(checked.mechanisms()); var bindings = new TreeMap<>(checked.bindingDigests());
                if (mechanisms) versions.replaceAll((key, value) -> value.add(java.math.BigInteger.ONE));
                else bindings.replaceAll((key, value) -> "0".repeat(64));
                return new V3NativeRevision.Definition(new studio.environment.core.definitionv3.NativeCompilationResult.Checked(
                        checked.definition(), checked.logicalDigest(), bindings, versions), List.of());
            }, new V3ProfileWorkspaceCompiler());
            refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION, () -> changed.definition(owner, reference(definition)));
        }
        var nullCompiler = new V3PlanWorkspaceBridge(store, ignored -> null, new V3ProfileWorkspaceCompiler());
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class,
                () -> nullCompiler.definition(owner, reference(definition))).code());
        var unsupportedHistory = V3ProfileHttpFixtures.definition(directory, owner, true);
        refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION, () -> bridge().definition(owner, reference(unsupportedHistory)));
    }

    @Test void editedSelectedPublicationAndPoliciesCannotBeUsedAsInternalPins() throws Exception {
        var definition = V3ProfileHttpFixtures.definition(directory, owner, false);
        var publishedProfile = profile(definition, DraftCommand.Format.JSON);
        var selected = bridge().definition(owner, reference(definition));
        var pins = List.of(new V3PlanWorkspace.Definition(selected.reference(), "0".repeat(64), selected.checked(), selected.policies()),
                new V3PlanWorkspace.Definition(selected.reference(), selected.publicationDigest(), selected.checked(), List.of()));
        for (var pin : pins) refused(PlanRefusal.Code.UNSUPPORTED_DEFINITION, () -> bridge().profile(owner, reference(publishedProfile), pin));
    }

    @Test void profileRecompilationMustMatchFullContentAndOriginalDefinitionOnBothChecks() throws Exception {
        var definition = V3ProfileHttpFixtures.definition(directory, owner, false);
        var publishedProfile = profile(definition, DraftCommand.Format.JSON);
        var selected = bridge().definition(owner, reference(definition));
        var actualProfiles = new V3ProfileWorkspaceCompiler();
        for (int corruptCall : List.of(1, 2)) {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var lookup = new V3PlanWorkspaceBridge(store, this::witness, (command, checked) -> {
                if (calls.incrementAndGet() != corruptCall) return actualProfiles.profile(command, checked);
                return actualProfiles.profile(new NativeCommand.SaveProfile(command.objectId(), command.expectedRevision(), command.requestId(),
                        command.format(), command.source().replace("First neutral slot", "Changed neutral slot"), command.definition()), checked);
            });
            refused(PlanRefusal.Code.PROFILE_REFUSED, () -> lookup.profile(owner, reference(publishedProfile), selected));
            assertEquals(corruptCall, calls.get());
        }
        var wrongReference = new V3PlanWorkspaceBridge(store, this::witness, (command, checked) ->
                new V3NativeRevision.Profile(actualProfiles.profile(command, checked).checked(), new NativeCommand.Reference(UUID.randomUUID().toString(), "2")));
        refused(PlanRefusal.Code.PROFILE_REFUSED, () -> wrongReference.profile(owner, reference(publishedProfile), selected));
        var nullCompiler = new V3PlanWorkspaceBridge(store, this::witness, (command, checked) -> null);
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class,
                () -> nullCompiler.profile(owner, reference(publishedProfile), selected)).code());
    }

    @Test void sourceOnlyCompilerFailuresAreSafeAndDoNotMutateOrEnumerateTheStore() throws Exception {
        var definition = V3ProfileHttpFixtures.definition(directory, owner, false);
        var publishedProfile = profile(definition, DraftCommand.Format.JSON);
        var selected = bridge().definition(owner, reference(definition));
        var before = counts();
        V3NativeStore readsOnly = new V3NativeStore() {
            public V3NativeRevision read(Owner user, String id, Optional<String> revision, boolean profile) {
                assertEquals(owner, user); assertTrue(revision.isPresent());
                return store.read(user, id, revision, profile);
            }
            public Optional<V3NativeRevision> replay(Owner user, NativeCommand command) { throw new AssertionError("Lookup replayed."); }
            public V3NativeRevision append(Owner user, NativeCommand command, V3NativeRevision revision) { throw new AssertionError("Lookup wrote."); }
            public List<V3NativeRevision> list(Owner user, boolean profile) { throw new AssertionError("Lookup enumerated."); }
        };
        var positive = new V3PlanWorkspaceBridge(readsOnly, this::witness, new V3ProfileWorkspaceCompiler());
        assertDoesNotThrow(() -> positive.profile(owner, reference(publishedProfile), selected));
        var diagnostic = new studio.environment.core.definition.DefinitionDiagnostic(
                studio.environment.core.definition.DefinitionDiagnostic.Phase.SEMANTIC, "MOCK_SOURCE_FAILURE", "$", "invented-private-value-canary");
        var definitionFailure = new V3PlanWorkspaceBridge(readsOnly, command -> { throw new WorkspaceRejection(List.of(diagnostic)); }, new V3ProfileWorkspaceCompiler());
        assertEquals("UNSUPPORTED_DEFINITION", assertThrows(PlanRefusal.class,
                () -> definitionFailure.definition(owner, reference(definition))).getMessage());
        var profileFailure = new V3PlanWorkspaceBridge(readsOnly, this::witness, (command, checked) -> { throw new WorkspaceRejection(List.of(diagnostic)); });
        assertEquals("PROFILE_REFUSED", assertThrows(PlanRefusal.class,
                () -> profileFailure.profile(owner, reference(publishedProfile), selected)).getMessage());
        var tooLarge = new V3PlanWorkspaceBridge(readsOnly, command -> { throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE); }, new V3ProfileWorkspaceCompiler());
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class,
                () -> tooLarge.definition(owner, reference(definition))).code());
        assertEquals(before, counts());
    }
}
