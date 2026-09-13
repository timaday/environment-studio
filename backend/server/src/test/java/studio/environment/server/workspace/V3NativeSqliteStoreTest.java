package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.sql.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

class V3NativeSqliteStoreTest {
    Path directory;
    final Owner owner = new Owner("https://invented.invalid", "mock-owner");
    @BeforeEach void setup() throws Exception {
        directory = Files.createTempDirectory("es-v3-store-mock-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    @AfterEach void cleanup() throws Exception {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) Files.delete(file);
        }
        Files.delete(directory);
    }
    @Test void initializedV3PersistsExactIncompleteHistoryAndReplayAcrossRestart() throws Exception {
        assertDoesNotThrow(() -> SqliteDraftStore.initializeV3(directory));
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var revision = V3NativeSnapshotCodecTest.draft();
        var command = new NativeCommand.SaveDefinition(revision.objectId(), "0", UUID.randomUUID().toString(),
                revision.format(), revision.source());
        assertTrue(store.replay(owner, command).isEmpty());
        assertEquals(revision, store.append(owner, command, revision));
        store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        assertEquals(revision, store.replay(owner, command).orElseThrow());
        assertEquals(revision, store.read(owner, revision.objectId(), Optional.of("1"), false));
        assertEquals(List.of(revision), store.list(owner, false));
        assertTrue(store.list(owner, true).isEmpty());
        assertTrue(new SqliteDraftStore(directory).list(owner).isEmpty());
        assertTrue(new NativeSqliteStore(new SqliteDraftStore(directory)).list(owner, false).isEmpty());
    }
    @Test void explicitV3UpgradePreservesV2SourceSnapshotAndReplay() throws Exception {
        SqliteDraftStore.initialize(directory);
        var oldStore = new NativeSqliteStore(new SqliteDraftStore(directory));
        var service = new NativeWorkspace(oldStore, new NativeWorkspaceCompiler(), ignored -> false);
        var command = new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(),
                DraftCommand.Format.JSON, Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
        var old = service.mutate(owner, command);
        byte[] snapshot = new NativeSnapshotCodec().encode(old);
        assertDoesNotThrow(() -> SqliteDraftStore.upgradeV3(directory));
        var restarted = new NativeSqliteStore(new SqliteDraftStore(directory));
        assertEquals(old, restarted.replay(owner, command).orElseThrow());
        assertArrayEquals(snapshot, new NativeSnapshotCodec().encode(restarted.read(owner, old.objectId(), Optional.empty(), false)));
        assertTrue(new V3NativeSqliteStore(new SqliteDraftStore(directory)).list(owner, false).isEmpty());
    }
    static NativeCommand.SaveDefinition command(String id, String expected, String source) {
        return new NativeCommand.SaveDefinition(id, expected, UUID.randomUUID().toString(), DraftCommand.Format.JSON, source);
    }
    static V3NativeRevision revision(NativeCommand.SaveDefinition command) {
        var compiled = assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,
                new studio.environment.server.definition.NativeV3DefinitionBytesCompiler().compile(
                        StrictUtf8.encode(command.source()), studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON));
        return new V3NativeRevision(command.objectId(), new java.math.BigInteger(command.expectedRevision()).add(java.math.BigInteger.ONE).toString(),
                command.format(), command.source(), V3NativeWorkspaceDigests.source(command.source()), "native-compiler-v3", "3",
                new V3NativeRevision.Definition(compiled.checked(), compiled.diagnostics()), Optional.empty());
    }
    static void refusal(WorkspaceRefusal.Code code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(WorkspaceRefusal.class, action).code());
    }
    @Test void kindAndOwnerIsolationIncludesLegacyAndV2Collisions() throws Exception {
        SqliteDraftStore.initializeV3(directory);
        var shared = new SqliteDraftStore(directory); var store = new V3NativeSqliteStore(shared);
        var one = V3NativeSnapshotCodecTest.draft(); var command = command(one.objectId(), "0", one.source());
        store.append(owner, command, one);
        var foreign = new Owner(owner.issuer(), "foreign");
        refusal(WorkspaceRefusal.Code.NOT_FOUND, () -> store.read(foreign, one.objectId(), Optional.empty(), false));
        refusal(WorkspaceRefusal.Code.NOT_FOUND, () -> store.replay(foreign, command));
        refusal(WorkspaceRefusal.Code.NOT_FOUND, () -> store.read(owner, one.objectId(), Optional.empty(), true));
        refusal(WorkspaceRefusal.Code.CONFLICT, () -> new NativeSqliteStore(shared).replay(owner, command));
        refusal(WorkspaceRefusal.Code.CONFLICT, () -> shared.replay(owner, new DraftCommand(one.objectId(), "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON, "{}")));
        var oldCommand = command(UUID.randomUUID().toString(), "0", Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
        new NativeWorkspace(new NativeSqliteStore(shared), new NativeWorkspaceCompiler(), ignored -> false).mutate(owner, oldCommand);
        refusal(WorkspaceRefusal.Code.CONFLICT, () -> store.replay(owner, command(oldCommand.objectId(), "0", one.source())));
        assertEquals(1, store.list(owner, false).size()); assertEquals(1, new NativeSqliteStore(shared).list(owner, false).size());
    }
    @Test void concurrentStaleCommandsCommitOneAndOriginalReplaySurvivesLaterRevisions() throws Exception {
        SqliteDraftStore.initializeV3(directory);var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var one = V3NativeSnapshotCodecTest.draft();var original = command(one.objectId(), "0", one.source());store.append(owner, original, one);
        var a = command(one.objectId(), "1", one.source());var b = command(one.objectId(), "1", one.source());
        var nextA = revision(a);var nextB = revision(b);var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {start.await();try{return store.append(owner,a,nextA);}catch(WorkspaceRefusal refusal){return refusal.code();}});
            var second = executor.submit(() -> {start.await();try{return store.append(owner,b,nextB);}catch(WorkspaceRefusal refusal){return refusal.code();}});
            start.countDown();var results = List.of(first.get(10,TimeUnit.SECONDS), second.get(10,TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(V3NativeRevision.class::isInstance).count());assertTrue(results.contains(WorkspaceRefusal.Code.CONFLICT));
        }
        assertEquals("2", store.read(owner, one.objectId(), Optional.empty(), false).workspaceRevision());
        assertEquals(one, store.replay(owner, original).orElseThrow());
        var changedRequest = new NativeCommand.SaveDefinition(original.objectId(), original.expectedRevision(), original.requestId(), original.format(), original.source()+" ");
        refusal(WorkspaceRefusal.Code.CONFLICT, () -> store.replay(owner, changedRequest));
    }
    @Test void revokedCommitRollsBackRevisionCatalogAndReplayTogether() throws Exception {
        SqliteDraftStore.initializeV3(directory);var shared = new SqliteDraftStore(directory);
        var revoked = new V3NativeSqliteStore(shared.withCommit(connection -> {throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);}));
        var one = V3NativeSnapshotCodecTest.draft();var command = command(one.objectId(), "0", one.source());
        refusal(WorkspaceRefusal.Code.FORBIDDEN, () -> revoked.append(owner, command, one));
        var reopened = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        assertTrue(reopened.list(owner, false).isEmpty());assertTrue(reopened.replay(owner, command).isEmpty());
        assertEquals(one, reopened.append(owner, command, one));
    }
    @Test void registeredSchemaUpgradeAndRepeatRefusalsLeaveBytesUnchanged() throws Exception {
        SqliteDraftStore.initialize(directory);
        refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> new V3NativeSqliteStore(new SqliteDraftStore(directory)));
        SqliteDraftStore.upgradeV3(directory);var database = directory.resolve(PrivateWorkspacePath.DATABASE);byte[] intact = Files.readAllBytes(database);
        for (org.junit.jupiter.api.function.Executable action : List.<org.junit.jupiter.api.function.Executable>of(
                () -> SqliteDraftStore.upgradeV3(directory), () -> SqliteDraftStore.initializeV3(directory), () -> SqliteDraftStore.upgrade(directory))) {
            refusal(WorkspaceRefusal.Code.UNAVAILABLE, action);assertArrayEquals(intact, Files.readAllBytes(database));
        }
        try (var connection=DriverManager.getConnection("jdbc:sqlite:"+database);var statement=connection.createStatement()) {statement.execute("PRAGMA user_version=99");}
        byte[] unknown=Files.readAllBytes(database);
        refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> new SqliteDraftStore(directory));
        refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> SqliteDraftStore.upgradeV3(directory));assertArrayEquals(unknown, Files.readAllBytes(database));
    }
    @Test void everyV3EnvelopeAndKindPartitionCorruptionRefusesRestart() throws Exception {
        SqliteDraftStore.initializeV3(directory);var store=new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var one=V3NativeSnapshotCodecTest.draft();store.append(owner,command(one.objectId(),"0",one.source()),one);
        var database=directory.resolve(PrivateWorkspacePath.DATABASE);byte[] intact=Files.readAllBytes(database);
        for (String sql : List.of("UPDATE catalog SET subject='other'", "UPDATE catalog SET native_id='other'",
                "UPDATE v3_artifact_types SET kind='profile-v3'", "UPDATE v3_native_revisions SET snapshot_digest='00'",
                "UPDATE v3_native_replays SET request_digest='00'", "UPDATE v3_native_replays SET kind='save-profile'",
                "INSERT INTO artifact_types SELECT object_id,'definition-v2' FROM v3_artifact_types",
                "UPDATE v3_native_revisions SET snapshot=replace(CAST(snapshot AS TEXT),'native-compiler-v3','native-compiler-v2')")) {
            Files.write(database,intact);
            try(var connection=DriverManager.getConnection("jdbc:sqlite:"+database);var statement=connection.createStatement()){statement.executeUpdate(sql);}
            refusal(WorkspaceRefusal.Code.UNAVAILABLE, () -> new SqliteDraftStore(directory));
        }
    }
    @Test void snapshotExpansionBeyondRecordBudgetHasTypedSizeRefusal() throws Exception {
        SqliteDraftStore.initializeV3(directory);var store=new V3NativeSqliteStore(new SqliteDraftStore(directory));
        String source=V3NativeSnapshotCodec.JSON.writeValueAsString(V3NativeSnapshotCodec.JSON.readTree(V3NativeSnapshotCodecTest.draft().source()));
        source="\n".repeat(1_048_576-StrictUtf8.encode(source).length)+source;
        var command=command(UUID.randomUUID().toString(),"0",source);var revision=revision(command);
        refusal(WorkspaceRefusal.Code.TOO_LARGE, () -> store.append(owner,command,revision));
        assertTrue(store.list(owner,false).isEmpty());
    }
    @Test void newOfflineCommandsAreExplicitAndDoNotChangeOldDefaults() throws Exception {
        assertDoesNotThrow(() -> studio.environment.server.EnvironmentStudioApplication.main(new String[]{"--initialize-workspace-v3="+directory}));
        assertTrue(new V3NativeSqliteStore(new SqliteDraftStore(directory)).list(owner,false).isEmpty());
        byte[] intact=Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE));
        for (String[] args : List.of(new String[]{"--initialize-workspace-v3="+directory,"--server.port=0"},
                new String[]{"--upgrade-workspace-v3="+directory,"--initialize-workspace="+directory},
                new String[]{"--upgrade-workspace-v3=relative"},new String[]{"--initialize-workspace-v3x="+directory})) {
            assertThrows(IllegalArgumentException.class, () -> studio.environment.server.EnvironmentStudioApplication.main(args));
            assertArrayEquals(intact,Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE)));
        }
    }
    @Test void sharedObjectBudgetCountsAllThreeVersionsAndDoesNotEvictHistory() throws Exception {
        SqliteDraftStore.initializeV3(directory);var shared=new SqliteDraftStore(directory);
        var legacy=new DraftWorkspace(shared, c -> new studio.environment.server.definition.DefinitionBytesCompiler()
                .compile(StrictUtf8.encode(c.source()),studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON));
        for(int i=0;i<98;i++)legacy.put(owner,new DraftCommand(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),
                DraftCommand.Format.JSON,SqliteDraftStoreTest.source("mock-"+i,1)));
        var old=new NativeWorkspace(new NativeSqliteStore(shared),new NativeWorkspaceCompiler(),ignored->false);
        var oldCommand=command(UUID.randomUUID().toString(),"0",Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
        var oldSaved=old.mutate(owner,oldCommand);
        var store=new V3NativeSqliteStore(shared);var one=V3NativeSnapshotCodecTest.draft();var original=command(one.objectId(),"0",one.source());
        store.append(owner,original,one);
        var excess=command(UUID.randomUUID().toString(),"0",one.source());var excessRevision=revision(excess);
        refusal(WorkspaceRefusal.Code.CAPACITY,()->store.append(owner,excess,excessRevision));
        refusal(WorkspaceRefusal.Code.CAPACITY,()->old.mutate(owner,command(UUID.randomUUID().toString(),"0",oldCommand.source())));
        refusal(WorkspaceRefusal.Code.CAPACITY,()->legacy.put(owner,new DraftCommand(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,SqliteDraftStoreTest.source("mock-extra",1))));
        assertEquals(98,shared.list(owner).size());assertEquals(1,new NativeSqliteStore(shared).list(owner,false).size());assertEquals(1,store.list(owner,false).size());
        assertEquals(one,store.replay(owner,original).orElseThrow());assertEquals(oldSaved,old.mutate(owner,oldCommand));
    }
    @Test void allThirtyTwoV3RevisionsRemainAvailableWithOriginalReplayAtCapacity() throws Exception {
        SqliteDraftStore.initializeV3(directory);var store=new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var one=V3NativeSnapshotCodecTest.draft();var original=command(one.objectId(),"0",one.source());store.append(owner,original,one);
        for(int i=1;i<32;i++){var next=command(one.objectId(),Integer.toString(i),one.source());store.append(owner,next,revision(next));}
        var excess=command(one.objectId(),"32",one.source());var excessRevision=revision(excess);
        refusal(WorkspaceRefusal.Code.CAPACITY,()->store.append(owner,excess,excessRevision));
        assertEquals(one,store.replay(owner,original).orElseThrow());assertEquals("32",store.read(owner,one.objectId(),Optional.empty(),false).workspaceRevision());
    }
    V3NativeRevision historicalPublishedDefinition(V3NativeSqliteStore store) throws Exception {
        // Artificial prior history supplied directly to the internal store. This is not current publication admission.
        var actual=V3NativeSnapshotCodecTest.draft();var current=(V3NativeRevision.Definition)actual.content();
        var versions=new TreeMap<>(current.checked().mechanisms());versions.replaceAll((name,value)->java.math.BigInteger.valueOf(9));
        var checked=new studio.environment.core.definitionv3.NativeCompilationResult.Checked(current.checked().definition(),current.checked().logicalDigest(),current.checked().bindingDigests(),versions);
        var content=new V3NativeRevision.Definition(checked,List.of());
        var prior=new V3NativeRevision(actual.objectId(),"1",actual.format(),actual.source(),actual.sourceDigest(),actual.compilerVersion(),"3",content,Optional.empty());
        store.append(owner,command(prior.objectId(),"0",prior.source()),prior);
        var policies=checked.definition().bindings().stream().flatMap(b->b.documents().stream().map(d->new NativeCommand.Policy(b.id(),d.id(),"deny"))).toList();
        var next=new V3NativeRevision(prior.objectId(),"2",prior.format(),prior.source(),prior.sourceDigest(),prior.compilerVersion(),"3",content,Optional.empty());
        var publication=new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(next,"1",policies),"1",policies);
        var published=new V3NativeRevision(next.objectId(),next.workspaceRevision(),next.format(),next.source(),next.sourceDigest(),next.compilerVersion(),"3",content,Optional.of(publication));
        return store.append(owner,new NativeCommand.PublishDefinition(prior.objectId(),"1",UUID.randomUUID().toString(),policies),published);
    }
    @Test void profileHistoryPinsOwnedPublishedV3DefinitionAndRejectsDraftOrChangedDigest() throws Exception {
        SqliteDraftStore.initializeV3(directory);var store=new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var published=historicalPublishedDefinition(store);
        var current=((V3NativeRevision.Definition)V3NativeSnapshotCodecTest.draft().content()).checked();
        String source=Files.readString(Path.of("../../fixtures/native-v3/profile.json"));
        var accepted=assertInstanceOf(studio.environment.server.profile.V3ProfileBytesAdapter.Result.Accepted.class,
                new studio.environment.server.profile.V3ProfileBytesAdapter().read(current,StrictUtf8.encode(source),studio.environment.server.definition.BoundedDocumentParser.Format.JSON));
        String id=UUID.randomUUID().toString();var reference=new NativeCommand.Reference(published.objectId(),"2");
        var command=new NativeCommand.SaveProfile(id,"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,source,reference);
        var revision=new V3NativeRevision(id,"1",command.format(),source,V3NativeWorkspaceDigests.source(source),"profile-compiler-v3","3",new V3NativeRevision.Profile(accepted.checked(),reference),Optional.empty());
        assertEquals(revision,store.append(owner,command,revision));
        assertEquals(revision,new V3NativeSqliteStore(new SqliteDraftStore(directory)).read(owner,id,Optional.empty(),true));
        assertEquals(revision,store.replay(owner,command).orElseThrow());
        var draftRef=new NativeCommand.Reference(published.objectId(),"1");String another=UUID.randomUUID().toString();
        var draftCommand=new NativeCommand.SaveProfile(another,"0",UUID.randomUUID().toString(),command.format(),source,draftRef);
        var draftProfile=new V3NativeRevision(another,"1",command.format(),source,revision.sourceDigest(),revision.compilerVersion(),"3",new V3NativeRevision.Profile(accepted.checked(),draftRef),Optional.empty());
        refusal(WorkspaceRefusal.Code.UNAVAILABLE,()->store.append(owner,draftCommand,draftProfile));
        refusal(WorkspaceRefusal.Code.NOT_FOUND,()->store.append(new Owner(owner.issuer(),"other"),draftCommand,draftProfile));
        var p=accepted.checked().profile();var changed=new studio.environment.core.profile.Profile(p.id(),p.revision(),"f".repeat(64),p.entities(),p.relations());
        var changedContent=new V3NativeRevision.Profile(new studio.environment.core.profile.ProfileResult.Checked(changed,accepted.checked().contentDigest()),reference);
        var bad=new V3NativeRevision(another,"1",command.format(),source,revision.sourceDigest(),revision.compilerVersion(),"3",changedContent,Optional.empty());
        var badCommand=new NativeCommand.SaveProfile(another,"0",UUID.randomUUID().toString(),command.format(),source,reference);
        refusal(WorkspaceRefusal.Code.UNAVAILABLE,()->store.append(owner,badCommand,bad));
        assertEquals(1,store.list(owner,true).size());
    }
    @Test void v3UpgradeCommandPreservesV1RecordsAndExactReplay() throws Exception {
        SqliteDraftStore.initialize(directory);var shared=new SqliteDraftStore(directory);
        var old=new DraftWorkspace(shared,c->new studio.environment.server.definition.DefinitionBytesCompiler().compile(StrictUtf8.encode(c.source()),studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON));
        var command=new DraftCommand(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,SqliteDraftStoreTest.source("mock-legacy",1));var saved=assertInstanceOf(DraftWorkspace.Saved.class,old.put(owner,command)).draft();
        assertDoesNotThrow(()->studio.environment.server.EnvironmentStudioApplication.main(new String[]{"--upgrade-workspace-v3="+directory}));
        var reopened=new SqliteDraftStore(directory);assertEquals(saved,reopened.replay(owner,command).orElseThrow());assertEquals(saved,reopened.read(owner,saved.objectId(),Optional.empty()));
        assertEquals(1,reopened.list(owner).size());assertTrue(new V3NativeSqliteStore(reopened).list(owner,false).isEmpty());
    }
}
