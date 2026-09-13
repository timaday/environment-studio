package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Invented publications and an explicit test compiler witness, never runtime qualification. */
class V3PublicationWorkspaceTest {
    Path directory;
    V3NativeSqliteStore store;
    final Owner owner = new Owner("https://invented.invalid", "publication-owner");
    final V3NativeWorkspaceCompiler actual = new V3NativeWorkspaceCompiler();
    @BeforeEach void setup() throws Exception {
        directory = Files.createTempDirectory("es-v3-publication-mock-");
        SqliteDraftStore.initializeV3(directory); store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
    }
    @AfterEach void cleanup() throws Exception {
        try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
        Files.delete(directory);
    }
    V3NativeRevision.Definition testCompilerWitness(NativeCommand.SaveDefinition command) {
        var compiled = actual.definition(command);
        assertEquals(List.of("MECHANISM_UNQUALIFIED"), compiled.diagnostics().stream().map(d -> d.code()).toList());
        return new V3NativeRevision.Definition(compiled.checked(), List.of());
    }
    V3NativeRevision draft() throws Exception {
        var command = new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(),
                DraftCommand.Format.JSON, Files.readString(Path.of("../../fixtures/native-v3/definition.json")) + "\r\n");
        var content = testCompilerWitness(command);
        return store.append(owner, command, new V3NativeRevision(command.objectId(), "1", command.format(), command.source(),
                V3NativeWorkspaceDigests.source(command.source()), "native-compiler-v3", "3", content, Optional.empty()));
    }
    List<NativeCommand.Policy> policies(V3NativeRevision revision) {
        return ((V3NativeRevision.Definition) revision.content()).checked().definition().bindings().stream()
                .flatMap(b -> b.documents().stream().map(d -> new NativeCommand.Policy(b.id(), d.id(), "deny"))).toList();
    }
    V3PublicationWorkspace testService() {
        return new V3PublicationWorkspace(store, this::testCompilerWitness, new V3ProfileWorkspaceCompiler(), ignored -> true);
    }
    @Test void explicitTestQualificationAppendsPublicationAndPreservesImmutableExactHistory() throws Exception {
        var draft = draft();
        var command = new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), policies(draft));
        var published = assertDoesNotThrow(() -> testService().publishDefinition(owner, command));
        assertEquals("2", published.workspaceRevision()); assertEquals("published", published.state());
        assertEquals(draft.source(), published.source()); assertEquals(draft.content(), published.content());
        assertEquals("1", published.publication().orElseThrow().sourceRevision());
        assertEquals(command.exportPolicies(), published.publication().orElseThrow().exportPolicies());
        assertEquals(draft, store.read(owner, draft.objectId(), Optional.of("1"), false));
        assertEquals(published, store.replay(owner, command).orElseThrow());
        var reopened = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        assertEquals(published, reopened.read(owner, draft.objectId(), Optional.empty(), false));
    }

    @Test void actualCompilerCannotPublishIncompleteOrTestHistoricalReadyDefinitions() throws Exception {
        var service = new V3PublicationWorkspace(store, actual, new V3ProfileWorkspaceCompiler(), ignored -> true);
        var historicalReady = draft();
        var command = new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(),
                historicalReady.format(), historicalReady.source());
        var incomplete = new V3NativeWorkspace(store, actual).saveDefinition(owner, command);
        for (var revision : List.of(historicalReady, incomplete)) {
            var publish = new NativeCommand.PublishDefinition(revision.objectId(), "1", UUID.randomUUID().toString(), policies(revision));
            assertEquals("DEFINITION_INCOMPLETE", assertThrows(WorkspaceRejection.class,
                    () -> service.publishDefinition(owner, publish)).diagnostics().getFirst().code());
            assertEquals(revision, store.read(owner, revision.objectId(), Optional.empty(), false));
            assertTrue(store.replay(owner, publish).isEmpty());
        }
    }

    @Test void exactReplayPrecedesCompilationButStillRequiresCurrentMaintainerAdmission() throws Exception {
        var draft = draft();
        var command = new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), policies(draft));
        var published = testService().publishDefinition(owner, command);
        new V3NativeWorkspace(store, actual).saveDefinition(owner, new NativeCommand.SaveDefinition(draft.objectId(), "2",
                UUID.randomUUID().toString(), draft.format(), draft.source() + "\n"));
        V3NativeWorkspace.Compiler noCompile = ignored -> { throw new AssertionError("Replay compiled."); };
        var replay = new V3PublicationWorkspace(store, noCompile, new V3ProfileWorkspaceCompiler(), ignored -> true);
        assertEquals(published, assertDoesNotThrow(() -> replay.publishDefinition(owner, command)));
        var denied = new V3PublicationWorkspace(store, noCompile, new V3ProfileWorkspaceCompiler(), ignored -> false);
        assertEquals(WorkspaceRefusal.Code.FORBIDDEN, assertThrows(WorkspaceRefusal.class,
                () -> denied.publishDefinition(owner, command)).code());
        var altered = new ArrayList<>(command.exportPolicies());
        var first = altered.getFirst(); altered.set(0, new NativeCommand.Policy(first.bindingId(), first.documentId(), "protected-self-contained"));
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class,
                () -> replay.publishDefinition(owner, new NativeCommand.PublishDefinition(command.objectId(), "1", command.requestId(), altered))).code());
    }

    @Test void missingExtraAndDuplicatePoliciesRefuseBeforeCompilationOrAppend() throws Exception {
        var draft = draft(); var all = policies(draft);
        var duplicate = new ArrayList<>(all); duplicate.add(all.getFirst());
        var extra = new ArrayList<>(all); extra.add(new NativeCommand.Policy("invented", "absent", "deny"));
        var cases = List.of(all.subList(1, all.size()), extra, duplicate);
        var service = new V3PublicationWorkspace(store, ignored -> { throw new AssertionError("Invalid policies compiled."); },
                new V3ProfileWorkspaceCompiler(), ignored -> true);
        for (var selected : cases) {
            var command = new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), selected);
            assertThrows(WorkspaceRejection.class, () -> service.publishDefinition(owner, command));
            assertTrue(store.replay(owner, command).isEmpty());
            assertEquals(draft, store.read(owner, draft.objectId(), Optional.empty(), false));
        }
    }

    @Test void authorityRevokedDuringCompilationOrActualCommitNeverPersistsPublication() throws Exception {
        var draft = draft(); var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
        var command = new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), policies(draft));
        var service = new V3PublicationWorkspace(store, supplied -> { var result = testCompilerWitness(supplied); allowed.set(false); return result; },
                new V3ProfileWorkspaceCompiler(), ignored -> allowed.get());
        assertEquals(WorkspaceRefusal.Code.FORBIDDEN, assertThrows(WorkspaceRefusal.class,
                () -> service.publishDefinition(owner, command)).code());
        assertEquals(draft, store.read(owner, draft.objectId(), Optional.empty(), false)); assertTrue(store.replay(owner, command).isEmpty());
        var deniedStore = new V3NativeSqliteStore(new SqliteDraftStore(directory).withCommit(connection -> {
            throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);
        }));
        var denied = new V3PublicationWorkspace(deniedStore, this::testCompilerWitness, new V3ProfileWorkspaceCompiler(), ignored -> true);
        assertEquals(WorkspaceRefusal.Code.FORBIDDEN, assertThrows(WorkspaceRefusal.class,
                () -> denied.publishDefinition(owner, command)).code());
        assertEquals(draft, store.read(owner, draft.objectId(), Optional.empty(), false)); assertTrue(store.replay(owner, command).isEmpty());
        assertEquals("2", testService().publishDefinition(owner, command).workspaceRevision());
    }

    @Test void profilePublicationUsesExactHistoricalReferenceAndCurrentDefinitionQualification() throws Exception {
        var draft = draft();
        var definition = testService().publishDefinition(owner, new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), policies(draft)));
        var reference = new NativeCommand.Reference(definition.objectId(), "2");
        var save = new NativeCommand.SaveProfile(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), DraftCommand.Format.YAML,
                "---\n" + V3ProfileHttpFixtures.source() + "\r\n", reference);
        var profile = new V3ProfileWorkspace(store, new V3ProfileWorkspaceCompiler()).saveProfile(owner, save);
        var command = new NativeCommand.PublishProfile(profile.objectId(), "1", UUID.randomUUID().toString());
        var actualService = new V3PublicationWorkspace(store, actual, new V3ProfileWorkspaceCompiler(), ignored -> false);
        assertEquals("DEFINITION_INCOMPLETE", assertThrows(WorkspaceRejection.class,
                () -> actualService.publishProfile(owner, command)).diagnostics().getFirst().code());
        assertTrue(store.replay(owner, command).isEmpty());
        V3ProfileHttpFixtures.laterDefinition(directory, owner, definition);
        var service = new V3PublicationWorkspace(store, this::testCompilerWitness, new V3ProfileWorkspaceCompiler(), ignored -> false);
        var published = service.publishProfile(owner, command);
        assertEquals("2", published.workspaceRevision()); assertEquals(profile.source(), published.source());
        assertEquals(reference, ((V3NativeRevision.Profile) published.content()).definition());
        assertTrue(published.publication().orElseThrow().exportPolicies().isEmpty());
        assertEquals("1", published.publication().orElseThrow().sourceRevision());
        assertEquals(published, actualService.publishProfile(owner, command));
        assertEquals(profile, store.read(owner, profile.objectId(), Optional.of("1"), true));
    }

    @Test void actualConcurrentPublicationHasOneCommitAndNoStaleReplay() throws Exception {
        var draft = draft(); var reached = new java.util.concurrent.CountDownLatch(2); var proceed = new java.util.concurrent.CountDownLatch(1);
        var service = new V3PublicationWorkspace(store, command -> {
            var compiled = testCompilerWitness(command); reached.countDown();
            try { assertTrue(proceed.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            return compiled;
        }, new V3ProfileWorkspaceCompiler(), ignored -> true);
        var commands = List.of(new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), policies(draft)),
                new NativeCommand.PublishDefinition(draft.objectId(), "1", UUID.randomUUID().toString(), policies(draft)));
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var pending = new ArrayList<java.util.concurrent.Future<String>>();
            for (var command : commands) pending.add(workers.submit(() -> {
                try { return service.publishDefinition(owner, command).state(); }
                catch (WorkspaceRefusal refusal) { return refusal.code().name(); }
            }));
            try { assertTrue(reached.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
            finally { proceed.countDown(); }
            var results = new ArrayList<String>();
            for (var result : pending) results.add(result.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(List.of("CONFLICT", "published"), results.stream().sorted().toList());
            for (int i = 0; i < commands.size(); i++) {
                var command = commands.get(i);
                if (results.get(i).equals("published")) assertTrue(store.replay(owner, command).isPresent());
                else assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class,
                        () -> store.replay(owner, command)).code());
            }
            assertEquals("2", store.read(owner, draft.objectId(), Optional.empty(), false).workspaceRevision());
        }
    }

    @Test void revisionCapacityAndForeignOwnershipRefuseWithoutPublication() throws Exception {
        var draft = draft(); var drafts = new V3NativeWorkspace(store, this::testCompilerWitness);
        for (int expected = 1; expected < 32; expected++) drafts.saveDefinition(owner, new NativeCommand.SaveDefinition(draft.objectId(),
                Integer.toString(expected), UUID.randomUUID().toString(), draft.format(), draft.source()));
        var command = new NativeCommand.PublishDefinition(draft.objectId(), "32", UUID.randomUUID().toString(), policies(draft));
        assertEquals(WorkspaceRefusal.Code.CAPACITY, assertThrows(WorkspaceRefusal.class,
                () -> testService().publishDefinition(owner, command)).code());
        assertEquals("draft", store.read(owner, draft.objectId(), Optional.empty(), false).state());
        assertTrue(store.replay(owner, command).isEmpty());
        var foreign = new Owner(owner.issuer(), "different-owner");
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND, assertThrows(WorkspaceRefusal.class,
                () -> testService().publishDefinition(foreign, command)).code());
        assertEquals(WorkspaceRefusal.Code.CONFLICT, assertThrows(WorkspaceRefusal.class,
                () -> testService().publishProfile(owner, new NativeCommand.PublishProfile(draft.objectId(), "32", UUID.randomUUID().toString()))).code());
    }
}
