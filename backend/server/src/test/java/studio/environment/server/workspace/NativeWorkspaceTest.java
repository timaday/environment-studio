package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

class NativeWorkspaceTest {
    Path directory;
    final Owner owner=new Owner("https://independent.invalid","mock-owner");
    final String id="00000000-0000-4000-8000-000000000081";
    final AtomicBoolean publisher=new AtomicBoolean(true);
    NativeStore store;NativeWorkspace workspace;
    @BeforeEach void setup() throws Exception {
        directory=Files.createTempDirectory("es-native-mock-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SqliteDraftStore.initialize(directory);reopen();
    }
    void reopen(){store=new NativeSqliteStore(new SqliteDraftStore(directory));workspace=new NativeWorkspace(store,new NativeWorkspaceCompiler(),o->publisher.get());}
    String source() throws Exception{return Files.readString(Path.of("../../fixtures/native-v2/definition.json"));}
    NativeCommand.SaveDefinition save(String expected)throws Exception{return new NativeCommand.SaveDefinition(id,expected,UUID.randomUUID().toString(),DraftCommand.Format.JSON,source());}
    NativeCommand.PublishDefinition publish(NativeRevision saved){
        var d=(NativeRevision.Definition)saved.content();var policies=new ArrayList<NativeCommand.Policy>();
        d.checked().definition().bindings().forEach(b->b.documents().forEach(doc->policies.add(new NativeCommand.Policy(b.id(),doc.id(),"deny"))));
        Collections.reverse(policies);return new NativeCommand.PublishDefinition(id,saved.workspaceRevision(),UUID.randomUUID().toString(),policies);
    }
    @Test void draftPublicationForkHistoryAndRevokedReplayAcrossRestart()throws Exception {
        var command=save("0");var first=workspace.mutate(owner,command);assertEquals("draft",first.state());
        var publish=publish(first);var published=workspace.mutate(owner,publish);assertEquals("2",published.workspaceRevision());
        assertEquals("published",published.state());assertEquals(first.source(),published.source());assertEquals(first.content(),published.content());
        assertEquals("3",workspace.mutate(owner,save("2")).workspaceRevision());reopen();
        assertEquals(first,workspace.mutate(owner,command));assertEquals(published,workspace.mutate(owner,publish));
        assertEquals(published,store.read(owner,id,Optional.of("2"),false));
        publisher.set(false);assertEquals(WorkspaceRefusal.Code.FORBIDDEN,assertThrows(WorkspaceRefusal.class,()->workspace.mutate(owner,publish)).code());
        assertEquals("3",store.read(owner,id,Optional.empty(),false).workspaceRevision());
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND,assertThrows(WorkspaceRefusal.class,()->store.read(new Owner(owner.issuer(),"another"),id,Optional.empty(),false)).code());
        assertTrue(new SqliteDraftStore(directory).list(owner).isEmpty());
    }
    @Test void ownedProfilePinsHistoricalPublicationAndCannotUseDraftOrOtherOwner()throws Exception {
        var first=workspace.mutate(owner,save("0"));
        String profileSource=Files.readString(Path.of("../../fixtures/profile-v2/profile.json"));
        var bad=new NativeCommand.SaveProfile(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,profileSource,new NativeCommand.Reference(id,"1"));
        assertThrows(WorkspaceRejection.class,()->workspace.mutate(owner,bad));
        var published=workspace.mutate(owner,publish(first));
        var command=new NativeCommand.SaveProfile(bad.objectId(),"0",bad.requestId(),bad.format(),bad.source(),new NativeCommand.Reference(id,published.workspaceRevision()));
        var saved=workspace.mutate(owner,command);assertEquals("draft",saved.state());
        workspace.mutate(owner,save("2"));
        var profile=workspace.mutate(owner,new NativeCommand.PublishProfile(command.objectId(),"1",UUID.randomUUID().toString()));
        assertEquals("published",profile.state());reopen();assertEquals(saved,workspace.mutate(owner,command));
        assertEquals(profile,store.read(owner,command.objectId(),Optional.of("2"),true));
        var other=new Owner(owner.issuer(),"other");
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND,assertThrows(WorkspaceRefusal.class,()->workspace.mutate(other,new NativeCommand.SaveProfile(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),command.format(),command.source(),command.definition()))).code());
    }
    @Test void staleConcurrentPublishCommitsOnlyOneAndKindCannotChange()throws Exception {
        var first=workspace.mutate(owner,save("0"));var a=publish(first);var b=publish(first);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures=new ArrayList<>();for(var c:List.of(a,b))futures.add(executor.submit(()->{start.await();try{return workspace.mutate(owner,c);}catch(WorkspaceRefusal refusal){return refusal.code();}}));
            start.countDown();var results=List.of(futures.get(0).get(),futures.get(1).get());
            assertEquals(1,results.stream().filter(NativeRevision.class::isInstance).count());assertTrue(results.contains(WorkspaceRefusal.Code.CONFLICT));
        }
        assertEquals(WorkspaceRefusal.Code.CONFLICT,assertThrows(WorkspaceRefusal.class,()->workspace.mutate(owner,new NativeCommand.PublishProfile(id,"2",UUID.randomUUID().toString()))).code());
    }
    @Test void wireProjectionMatchesClosedInspectionSchemas()throws Exception {
        var first=workspace.mutate(owner,save("0"));var model=new NativeSnapshotCodec().model(first);
        var registry=com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_7);
        var schema=registry.getSchema(NativeSnapshotCodec.JSON.readTree(Files.readString(Path.of("../../schemas/definition-inspection-v2.schema.json"))));
        assertTrue(schema.validate(model).isEmpty(),()->schema.validate(model).toString());
    }
    @Test void allThirtyTwoRevisionsRemainImmutableAndOriginalReplaySurvivesCapacity() throws Exception {
        var original=save("0");var first=workspace.mutate(owner,original);
        for(int revision=1;revision<32;revision++)workspace.mutate(owner,save(Integer.toString(revision)));
        assertEquals(WorkspaceRefusal.Code.CAPACITY,assertThrows(WorkspaceRefusal.class,()->workspace.mutate(owner,save("32"))).code());
        assertEquals(first,workspace.mutate(owner,original));assertEquals("32",store.read(owner,id,Optional.empty(),false).workspaceRevision());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"xml-span-v1,2", "native-compiler-v2,1"})
    void historicalMechanismVersionRemainsReadableButCannotNewlyPublish(String mechanism, String version) throws Exception {
        var command=save("0");var current=new NativeWorkspaceCompiler().definition(command);var checked=current.checked();
        var versions=new TreeMap<>(checked.mechanisms());versions.put(mechanism,new java.math.BigInteger(version));
        var historical=new NativeRevision.Definition(new studio.environment.core.definitionv2.NativeCompilationResult.Checked(checked.definition(),checked.logicalDigest(),checked.bindingDigests(),versions),List.of());
        var draft=new NativeRevision(id,"1",command.format(),command.source(),NativeWorkspaceDigests.source(command.source()),"native-compiler-v2","2",historical,Optional.empty());
        store.append(owner,command,draft);reopen();assertEquals(draft,store.read(owner,id,Optional.empty(),false));assertEquals(draft,workspace.mutate(owner,command));
        assertThrows(WorkspaceRejection.class,()->workspace.mutate(owner,publish(draft)));
    }
    @Test void sharedOwnerQuotaIsAtomicAcrossLegacyAndNativeArtifacts() throws Exception {
        var legacy=new SqliteDraftStore(directory);var compiler=new studio.environment.server.definition.DefinitionBytesCompiler();
        var service=new DraftWorkspace(legacy,c->compiler.compile(StrictUtf8.encode(c.source()),studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON));
        for(int i=0;i<99;i++)service.put(owner,new DraftCommand(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,SqliteDraftStoreTest.source("mock-"+i,1)));
        var start=new CountDownLatch(1);String source=source();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Object>>();
            for(int i=0;i<2;i++)tasks.add(executor.submit(()->{start.await();try{return workspace.mutate(owner,new NativeCommand.SaveDefinition(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,source));}catch(WorkspaceRefusal refusal){return refusal.code();}}));
            start.countDown();var results=List.of(tasks.get(0).get(),tasks.get(1).get());assertEquals(1,results.stream().filter(NativeRevision.class::isInstance).count());assertTrue(results.contains(WorkspaceRefusal.Code.CAPACITY));
        }
        assertEquals(99,legacy.list(owner).size());assertEquals(1,store.list(owner,false).size());
    }
    @Test void everyAuthorityEnvelopeFieldCorruptionFailsClosed() throws Exception {
        var first=workspace.mutate(owner,save("0"));workspace.mutate(owner,publish(first));
        byte[] intact=Files.readAllBytes(directory.resolve(PrivateWorkspacePath.DATABASE));
        for(String sql:List.of("UPDATE catalog SET issuer='https://other.invalid'","UPDATE catalog SET subject='other'","UPDATE catalog SET native_id='other'",
            "UPDATE artifact_types SET kind='profile-v2'","UPDATE native_revisions SET snapshot_digest='00'","UPDATE native_replays SET request_digest='00'",
            "UPDATE native_replays SET kind='save-profile'","UPDATE native_revisions SET definition_id='00000000-0000-4000-8000-000000000099',definition_revision=1",
            "UPDATE native_revisions SET snapshot=replace(CAST(snapshot AS TEXT),'native-compiler-v2','unknown-compiler-v2')",
            "UPDATE native_revisions SET snapshot=replace(CAST(snapshot AS TEXT),'deny','protected-self-contained')")) {
            Files.write(directory.resolve(PrivateWorkspacePath.DATABASE),intact);
            try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(PrivateWorkspacePath.DATABASE));var statement=connection.createStatement()){statement.executeUpdate(sql);}
            assertEquals(WorkspaceRefusal.Code.UNAVAILABLE,assertThrows(WorkspaceRefusal.class,()->new SqliteDraftStore(directory)).code());
        }
    }
    @Test void ownerAndAuthorityFieldCorruptionRefuseStartup()throws Exception {
        workspace.mutate(owner,save("0"));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(PrivateWorkspacePath.DATABASE));var s=connection.createStatement()) {
            s.executeUpdate("UPDATE catalog SET subject='another-owner'");
        }
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE,assertThrows(WorkspaceRefusal.class,()->new SqliteDraftStore(directory)).code());
    }
}
