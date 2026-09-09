package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Independently invented immutable history; never current v3 publication admission. */
class V3ProfileWorkspaceTest {
    Path directory;
    final Owner owner = new Owner("https://invented.invalid", "profile-draft-owner");
    V3NativeSqliteStore store;
    @BeforeEach void setup() throws Exception {
        directory = Files.createTempDirectory("es-v3-profile-draft-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SqliteDraftStore.initializeV3(directory);
        store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
    }
    @AfterEach void cleanup() throws Exception {
        try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(directory);
    }
    V3NativeRevision historicalDefinition() throws Exception { return historicalDefinition(false); }
    V3NativeRevision historicalDefinition(boolean oldMechanisms) throws Exception {
        // Test-only prior publication. The actual compiler is still incomplete.
        var actual = V3NativeSnapshotCodecTest.draft();
        var checked = ((V3NativeRevision.Definition) actual.content()).checked();
        if (oldMechanisms) {
            var versions = new TreeMap<>(checked.mechanisms()); versions.replaceAll((name, version) -> java.math.BigInteger.valueOf(9));
            checked = new studio.environment.core.definitionv3.NativeCompilationResult.Checked(checked.definition(), checked.logicalDigest(), checked.bindingDigests(), versions);
        }
        var content = new V3NativeRevision.Definition(checked, List.of());
        var prior = new V3NativeRevision(actual.objectId(), "1", actual.format(), actual.source(), actual.sourceDigest(),
                actual.compilerVersion(), "3", content, Optional.empty());
        store.append(owner, new NativeCommand.SaveDefinition(prior.objectId(), "0", UUID.randomUUID().toString(), prior.format(), prior.source()), prior);
        var policies = checked.definition().bindings().stream().flatMap(b -> b.documents().stream()
                .map(d -> new NativeCommand.Policy(b.id(), d.id(), "deny"))).toList();
        var next = new V3NativeRevision(prior.objectId(), "2", prior.format(), prior.source(), prior.sourceDigest(),
                prior.compilerVersion(), "3", content, Optional.empty());
        var publication = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(next, "1", policies), "1", policies);
        var published = new V3NativeRevision(next.objectId(), "2", next.format(), next.source(), next.sourceDigest(),
                next.compilerVersion(), "3", content, Optional.of(publication));
        return store.append(owner, new NativeCommand.PublishDefinition(prior.objectId(), "1", UUID.randomUUID().toString(), policies), published);
    }
    NativeCommand.SaveProfile command(V3NativeRevision definition, String source, DraftCommand.Format format) {
        return new NativeCommand.SaveProfile(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), format, source,
                new NativeCommand.Reference(definition.objectId(), definition.workspaceRevision()));
    }
    @Test void actualJsonAndYamlProfilesPersistExactSourceAndPinnedHistoricalDefinition() throws Exception {
        var definition = historicalDefinition();
        String json = Files.readString(Path.of("../../fixtures/native-v3/profile.json"));
        for (var format : DraftCommand.Format.values()) {
            String source = (format == DraftCommand.Format.YAML ? "---\n" : "") + json + "\r\n";
            var command = command(definition, source, format);
            var actual = assertDoesNotThrow(() -> new V3ProfileWorkspace(store, new V3ProfileWorkspaceCompiler()).saveProfile(owner, command));
            assertEquals(source, actual.source()); assertEquals(format, actual.format()); assertEquals("1", actual.workspaceRevision());
            assertEquals("profile-compiler-v3", actual.compilerVersion()); assertEquals("3", actual.schemaVersion());
            assertTrue(actual.publication().isEmpty()); assertEquals("draft", actual.state());
            assertEquals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(StrictUtf8.encode(source))), actual.sourceDigest());
            var content = assertInstanceOf(V3NativeRevision.Profile.class, actual.content());
            assertEquals(command.definition(), content.definition());
            assertEquals(Set.of("first", "second", "dependency"), content.checked().profile().entities().stream()
                    .map(studio.environment.core.profile.Profile.Entity::id).collect(java.util.stream.Collectors.toSet()));
            var restarted = new V3NativeSqliteStore(new SqliteDraftStore(directory));
            assertEquals(actual, restarted.read(owner, actual.objectId(), Optional.of("1"), true));
            assertEquals(actual, restarted.replay(owner, command).orElseThrow());
            assertFalse(actual.toString().contains(source));
        }
    }
    V3ProfileWorkspace service(V3NativeStore selected) { return new V3ProfileWorkspace(selected, new V3ProfileWorkspaceCompiler()); }
    String source() throws Exception { return Files.readString(Path.of("../../fixtures/native-v3/profile.json")); }
    @Test void historicalReplayPrecedesCompilationAndSurvivesLaterDefinitionAndProfileEdits() throws Exception {
        var definition = historicalDefinition();
        var first = command(definition, "---\n" + source() + "\r\n", DraftCommand.Format.YAML);
        var one = service(store).saveProfile(owner, first);
        var laterDefinition = new NativeCommand.SaveDefinition(definition.objectId(), "2", UUID.randomUUID().toString(), definition.format(), definition.source());
        assertEquals("3", new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler()).saveDefinition(owner, laterDefinition).workspaceRevision());
        var laterProfile = new NativeCommand.SaveProfile(first.objectId(), "1", UUID.randomUUID().toString(), DraftCommand.Format.JSON, source(), first.definition());
        var two = service(store).saveProfile(owner, laterProfile);
        assertEquals("2", two.workspaceRevision());
        var restarted = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var noCompile = new V3ProfileWorkspace(restarted, (c, d) -> { throw new AssertionError("Historical replay compiled."); });
        assertEquals(one, noCompile.saveProfile(owner, first));
        assertEquals(two, restarted.read(owner, first.objectId(), Optional.empty(), true));
        var altered = new NativeCommand.SaveProfile(first.objectId(), first.expectedRevision(), first.requestId(), first.format(), first.source() + "\n", first.definition());
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class, () -> noCompile.saveProfile(owner, altered)).code());
    }
    @Test void ownedPublishedReferenceIsRequiredAndWrongOwnerVersionOrKindNeverCompiles() throws Exception {
        var definition = historicalDefinition(); var command = command(definition, source(), DraftCommand.Format.JSON);
        var forbiddenCompiler = new V3ProfileWorkspace(store, (c, d) -> { throw new AssertionError("Invalid reference compiled."); });
        var foreign = new Owner(owner.issuer(), "foreign");
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class, () -> forbiddenCompiler.saveProfile(foreign, command)).code());
        var draft = new NativeCommand.SaveProfile(command.objectId(), "0", UUID.randomUUID().toString(), command.format(), command.source(),
                new NativeCommand.Reference(definition.objectId(), "1"));
        assertEquals("DEFINITION_NOT_PUBLISHED", assertThrows(WorkspaceRejection.class, () -> forbiddenCompiler.saveProfile(owner, draft)).diagnostics().getFirst().code());
        var saved = service(store).saveProfile(owner, command);
        var profileReference = new NativeCommand.SaveProfile(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), command.format(), command.source(),
                new NativeCommand.Reference(saved.objectId(), "1"));
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class, () -> forbiddenCompiler.saveProfile(owner, profileReference)).code());
        var oldSource = Files.readString(Path.of("../../fixtures/native-v2/definition.json"));
        var old = new NativeWorkspace(new NativeSqliteStore(new SqliteDraftStore(directory)), new NativeWorkspaceCompiler(), ignored -> false)
                .mutate(owner, new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON, oldSource));
        var oldReference = new NativeCommand.SaveProfile(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), command.format(), command.source(),
                new NativeCommand.Reference(old.objectId(), "1"));
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class, () -> forbiddenCompiler.saveProfile(owner, oldReference)).code());
        assertEquals(1, store.list(owner, true).size());
    }
    @Test void actualParserRejectsComputedStructureValuesAndIncompatibleDigestWithoutPersistence() throws Exception {
        var definition = historicalDefinition();
        var tree = V3NativeSnapshotCodec.JSON.readTree(source());
        var extras = List.of("values", "contributors", "computedTypes", "bindings", "donorIdentity");
        var malformed = new ArrayList<String>(List.of("{}", source() + "{}", source().replace("\"schemaVersion\": \"3\"", "\"schemaVersion\": \"2\"")));
        for (String key : extras) { var changed = tree.deepCopy(); ((tools.jackson.databind.node.ObjectNode) changed).put(key, "INVENTED-DONOR-CANARY"); malformed.add(changed.toString()); }
        var wrongDigest = tree.deepCopy(); ((tools.jackson.databind.node.ObjectNode) wrongDigest).put("logicalDefinitionDigest", "0".repeat(64)); malformed.add(wrongDigest.toString());
        var computedSlot = tree.deepCopy(); ((tools.jackson.databind.node.ObjectNode) computedSlot.at("/entities/0")).put("type",
                ((V3NativeRevision.Definition) definition.content()).checked().definition().logical().computedTypes().getFirst().id());
        malformed.add(computedSlot.toString());
        var computedEdge = tree.deepCopy(); ((tools.jackson.databind.node.ObjectNode) computedEdge.at("/relations/0")).put("type",
                ((V3NativeRevision.Definition) definition.content()).checked().definition().logical().derivations().getFirst().membershipRelation());
        malformed.add(computedEdge.toString());
        for (String bad : malformed) {
            var command = command(definition, bad, DraftCommand.Format.JSON);
            var rejection = assertThrows(WorkspaceRejection.class, () -> service(store).saveProfile(owner, command));
            assertTrue(rejection.diagnostics().size() <= 256);
            assertFalse(rejection.toString().contains("INVENTED-DONOR-CANARY"));
            assertFalse(rejection.diagnostics().toString().contains("INVENTED-DONOR-CANARY"));
            assertTrue(store.replay(owner, command).isEmpty());
        }
        assertTrue(store.list(owner, true).isEmpty());
        assertFalse(new String(Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE)), java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("INVENTED-DONOR-CANARY"));
    }
    @Test void originalCommitRefusalPersistsNoProfileOrReplayAndExactCommandCanLaterSucceed() throws Exception {
        var definition = historicalDefinition(); var command = command(definition, source(), DraftCommand.Format.JSON);
        var rejected = new V3NativeSqliteStore(new SqliteDraftStore(directory).withCommit(connection -> {
            throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);
        }));
        assertEquals(WorkspaceRefusal.Code.FORBIDDEN, assertThrows(WorkspaceRefusal.class, () -> service(rejected).saveProfile(owner, command)).code());
        var reopened = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        assertTrue(reopened.list(owner, true).isEmpty()); assertTrue(reopened.replay(owner, command).isEmpty());
        var saved = service(reopened).saveProfile(owner, command);
        assertEquals("1", saved.workspaceRevision()); assertTrue(saved.publication().isEmpty());
    }
    @Test void concurrentStaleSavesAppendOnlyOneRevision() throws Exception {
        var definition = historicalDefinition(); var first = command(definition, source(), DraftCommand.Format.JSON);
        service(store).saveProfile(owner, first);
        var a = new NativeCommand.SaveProfile(first.objectId(), "1", UUID.randomUUID().toString(), first.format(), first.source(), first.definition());
        var b = new NativeCommand.SaveProfile(first.objectId(), "1", UUID.randomUUID().toString(), first.format(), first.source() + "\n", first.definition());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<java.util.concurrent.Future<Object>>();
            for (var c : List.of(a, b)) results.add(executor.submit(() -> { start.await(); try { return service(store).saveProfile(owner, c); } catch (WorkspaceRefusal refused) { return refused.code(); } }));
            start.countDown(); var actual = new ArrayList<Object>();
            for (var result : results) actual.add(result.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, actual.stream().filter(V3NativeRevision.class::isInstance).count()); assertTrue(actual.contains(WorkspaceRefusal.Code.CONFLICT));
        }
        assertEquals("2", store.read(owner, first.objectId(), Optional.empty(), true).workspaceRevision());
    }
    @Test void byteLimitsAndMalformedUnicodeLeaveNoProfileOrReplay() throws Exception {
        var definition = historicalDefinition();
        var command = command(definition, " ".repeat(1_048_577), DraftCommand.Format.JSON);
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class, () -> service(store).saveProfile(owner, command)).code());
        assertTrue(store.replay(owner, command).isEmpty());
        var malformed = command(definition, "bad" + (char) 0xd800, DraftCommand.Format.JSON);
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class, () -> service(store).saveProfile(owner, malformed)).code());
        assertTrue(store.list(owner, true).isEmpty());
    }
    @Test void readableHistoricalMechanismsDoNotBecomeCurrentProfileValidationAuthority() throws Exception {
        var historical = historicalDefinition(true); var command = command(historical, source(), DraftCommand.Format.JSON);
        assertEquals(historical, store.read(owner, historical.objectId(), Optional.of("2"), false));
        assertEquals("INVALID_DEFINITION", assertThrows(WorkspaceRejection.class,
                () -> service(store).saveProfile(owner, command)).diagnostics().getFirst().code());
        assertTrue(store.list(owner, true).isEmpty()); assertTrue(store.replay(owner, command).isEmpty());
    }
    @Test void exactSourceByteBoundaryRemainsAnUnpublishedValueFreeDraft() throws Exception {
        var definition = historicalDefinition(); String profile = source();
        String maximum = " ".repeat(1_048_576 - StrictUtf8.encode(profile).length) + profile;
        var command = command(definition, maximum, DraftCommand.Format.JSON);
        var saved = service(store).saveProfile(owner, command);
        assertEquals(maximum, saved.source()); assertTrue(saved.publication().isEmpty());
        assertEquals(saved, store.read(owner, saved.objectId(), Optional.empty(), true));
    }
}
