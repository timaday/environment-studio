package studio.environment.server.workspace;

import java.sql.*;
import java.util.*;
import studio.environment.core.workspace.*;
import studio.environment.core.session.Owner;

/** Schema-3 typed revisions share the original UUID/owner catalog and its quota. */
final class V3NativeSqliteStore implements V3NativeStore {
    static final List<String> DDL=List.of(
        "CREATE TABLE v3_artifact_types(object_id TEXT PRIMARY KEY,kind TEXT NOT NULL CHECK(kind IN ('definition-v3','profile-v3')),FOREIGN KEY(object_id) REFERENCES catalog(object_id))",
        "CREATE TABLE v3_native_revisions(object_id TEXT NOT NULL,revision INTEGER NOT NULL CHECK(revision BETWEEN 1 AND 32),snapshot BLOB NOT NULL,snapshot_digest TEXT NOT NULL,definition_id TEXT,definition_revision INTEGER,PRIMARY KEY(object_id,revision),FOREIGN KEY(object_id) REFERENCES v3_artifact_types(object_id),FOREIGN KEY(definition_id,definition_revision) REFERENCES v3_native_revisions(object_id,revision),CHECK((definition_id IS NULL)=(definition_revision IS NULL)))",
        "CREATE TABLE v3_native_replays(object_id TEXT NOT NULL,request_id TEXT NOT NULL,kind TEXT NOT NULL,request_digest TEXT NOT NULL,revision INTEGER NOT NULL,PRIMARY KEY(object_id,request_id),FOREIGN KEY(object_id,revision) REFERENCES v3_native_revisions(object_id,revision))");
    private final SqliteDraftStore shared;
    private static final V3NativeSnapshotCodec CODEC=new V3NativeSnapshotCodec();
    V3NativeSqliteStore(SqliteDraftStore shared) {if(shared.storageVersion()!=3)throw unavailable();this.shared=shared;}
    private record Catalog(String issuer,String subject,String nativeId,int revision,String kind) { }
    private static Catalog catalog(Connection connection,Owner owner,String id,boolean profile,boolean mutation) throws SQLException {
        try(var query=connection.prepareStatement("SELECT c.*,a.kind FROM catalog c LEFT JOIN v3_artifact_types a USING(object_id) WHERE c.object_id=?")) {
            query.setString(1,id);try(var rows=query.executeQuery()) {
                if(!rows.next())return null;
                if(!owner.issuer().equals(rows.getString("issuer"))||!owner.subject().equals(rows.getString("subject")))throw refuse(WorkspaceRefusal.Code.NOT_FOUND);
                String kind=rows.getString("kind");if(!(profile?"profile-v3":"definition-v3").equals(kind))throw refuse(mutation?WorkspaceRefusal.Code.CONFLICT:WorkspaceRefusal.Code.NOT_FOUND);
                return new Catalog(rows.getString("issuer"),rows.getString("subject"),rows.getString("native_id"),rows.getInt("current_revision"),kind);
            }
        }
    }
    @Override public Optional<V3NativeRevision> replay(Owner owner,NativeCommand command) {
        try(var connection=shared.open()) {connection.setAutoCommit(false);shared.validate(connection);return replay(connection,owner,command);}
        catch(SQLException failure) {throw unavailable();}
    }
    private static Optional<V3NativeRevision> replay(Connection connection,Owner owner,NativeCommand command) throws SQLException {
        var catalog=catalog(connection,owner,command.objectId(),command.profile(),true);
        if(catalog==null) {if(!command.expectedRevision().equals("0"))throw refuse(WorkspaceRefusal.Code.CONFLICT);return Optional.empty();}
        try(var query=connection.prepareStatement("SELECT * FROM v3_native_replays WHERE object_id=? AND request_id=?")) {
            query.setString(1,command.objectId());query.setString(2,command.requestId());try(var rows=query.executeQuery()) {
                if(rows.next()) {
                    if(!V3NativeWorkspaceDigests.commandDigest(command).equals(rows.getString("request_digest")))throw refuse(WorkspaceRefusal.Code.CONFLICT);
                    return Optional.of(read(connection,command.objectId(),rows.getInt("revision")));
                }
            }
        }
        if(!command.expectedRevision().equals(Integer.toString(catalog.revision())))throw refuse(WorkspaceRefusal.Code.CONFLICT);
        return Optional.empty();
    }
    @Override public V3NativeRevision append(Owner owner,NativeCommand command,V3NativeRevision revision) {
        byte[] encoded=CODEC.encodeForStorage(revision);
        if(encoded.length+StrictUtf8.encode(owner.issuer()).length+StrictUtf8.encode(owner.subject()).length+4096>2_097_152)throw refuse(WorkspaceRefusal.Code.TOO_LARGE);
        if(StrictUtf8.encode(revision.source()).length>1_048_576)throw refuse(WorkspaceRefusal.Code.TOO_LARGE);
        try(var connection=shared.open()) {
            connection.setAutoCommit(false);shared.validate(connection);
            var replay=replay(connection,owner,command);if(replay.isPresent()){shared.commit(connection);return replay.orElseThrow();}
            var old=catalog(connection,owner,command.objectId(),command.profile(),true);int previous=old==null?0:old.revision();int next=previous+1;
            if(next>32)throw refuse(WorkspaceRefusal.Code.CAPACITY);
            if(!revision.objectId().equals(command.objectId())||revision.profile()!=command.profile()||!revision.workspaceRevision().equals(Integer.toString(next))
                ||old!=null&&!old.nativeId().equals(revision.content().nativeId())||!V3NativeWorkspaceDigests.commandDigest(command).equals(V3NativeWorkspaceDigests.commandDigest(reconstruct(revision,command.requestId(),kind(command)))))throw refuse(WorkspaceRefusal.Code.CONFLICT);
            CODEC.decode(encoded);
            if(revision.content() instanceof V3NativeRevision.Profile profile) {
                catalog(connection,owner,profile.definition().objectId(),false,false);
                var definition=read(connection,profile.definition().objectId(),boundedRevision(profile.definition().workspaceRevision()));
                compatible(revision,definition);
            }
            if(revision.publication().isPresent()) publicationChain(revision,read(connection,command.objectId(),previous));
            if(old==null) {
                try(var query=connection.prepareStatement("SELECT COUNT(*) FROM catalog WHERE issuer=? AND subject=?")) {
                    query.setString(1,owner.issuer());query.setString(2,owner.subject());try(var rows=query.executeQuery()){if(!rows.next()||rows.getInt(1)>=100)throw refuse(WorkspaceRefusal.Code.CAPACITY);}
                }
            }
            try {if(java.nio.file.Files.getFileStore(shared.paths.database()).getUsableSpace()<(long)SqliteDraftStore.MAX_PAGES*4096+1_048_576)throw unavailable();}
            catch(java.io.IOException failure){throw unavailable();}
            if(old==null) {
                try(var insert=connection.prepareStatement("INSERT INTO catalog VALUES(?,?,?,?,?)")) {
                    insert.setString(1,command.objectId());insert.setString(2,owner.issuer());insert.setString(3,owner.subject());insert.setString(4,revision.content().nativeId());insert.setInt(5,next);insert.executeUpdate();
                }
                try(var insert=connection.prepareStatement("INSERT INTO v3_artifact_types VALUES(?,?)")){insert.setString(1,command.objectId());insert.setString(2,revision.profile()?"profile-v3":"definition-v3");insert.executeUpdate();}
            } else try(var update=connection.prepareStatement("UPDATE catalog SET current_revision=? WHERE object_id=?")){update.setInt(1,next);update.setString(2,command.objectId());update.executeUpdate();}
            try(var insert=connection.prepareStatement("INSERT INTO v3_native_revisions VALUES(?,?,?,?,?,?)")) {
                insert.setString(1,command.objectId());insert.setInt(2,next);insert.setBytes(3,encoded);insert.setString(4,integrity(owner.issuer(),owner.subject(),revision,encoded));
                if(revision.content() instanceof V3NativeRevision.Profile profile){insert.setString(5,profile.definition().objectId());insert.setInt(6,boundedRevision(profile.definition().workspaceRevision()));}
                else {insert.setNull(5,Types.VARCHAR);insert.setNull(6,Types.INTEGER);}insert.executeUpdate();
            }
            try(var insert=connection.prepareStatement("INSERT INTO v3_native_replays VALUES(?,?,?,?,?)")) {
                insert.setString(1,command.objectId());insert.setString(2,command.requestId());insert.setString(3,kind(command));insert.setString(4,V3NativeWorkspaceDigests.commandDigest(command));insert.setInt(5,next);insert.executeUpdate();
            }
            validate(connection);shared.commit(connection);shared.paths.validateFiles(true);return revision;
        } catch(SQLException failure){throw unavailable();}
    }
    private static String kind(NativeCommand c){return V3NativeWorkspaceDigests.command(c).get("kind").toString();}
    @Override public V3NativeRevision read(Owner owner,String id,Optional<String> revision,boolean profile) {
        if(!DraftCommand.uuid(id)||revision.isPresent()&&!NativeCommand.revision(revision.orElseThrow(),false))throw refuse(WorkspaceRefusal.Code.INVALID_REQUEST);
        try(var connection=shared.open()) {
            connection.setAutoCommit(false);shared.validate(connection);var catalog=catalog(connection,owner,id,profile,false);if(catalog==null)throw refuse(WorkspaceRefusal.Code.NOT_FOUND);
            return read(connection,id,revision.map(V3NativeSqliteStore::boundedRevision).orElse(catalog.revision()));
        } catch(SQLException failure){throw unavailable();}
    }
    @Override public List<V3NativeRevision> list(Owner owner,boolean profile) {
        try(var connection=shared.open()) {
            connection.setAutoCommit(false);shared.validate(connection);var result=new ArrayList<V3NativeRevision>();
            try(var query=connection.prepareStatement("SELECT c.object_id,c.current_revision FROM catalog c JOIN v3_artifact_types a USING(object_id) WHERE issuer=? AND subject=? AND kind=? ORDER BY c.object_id")) {
                query.setString(1,owner.issuer());query.setString(2,owner.subject());query.setString(3,profile?"profile-v3":"definition-v3");
                try(var rows=query.executeQuery()){while(rows.next()){if(result.size()>=100)throw unavailable();result.add(read(connection,rows.getString(1),rows.getInt(2)));}}
            }return List.copyOf(result);
        }catch(SQLException failure){throw unavailable();}
    }
    private static V3NativeRevision read(Connection connection,String id,int revision)throws SQLException {
        try(var query=connection.prepareStatement("SELECT r.*,c.issuer,c.subject,c.native_id,a.kind,length(r.snapshot) AS bytes FROM v3_native_revisions r JOIN catalog c USING(object_id) JOIN v3_artifact_types a USING(object_id) WHERE r.object_id=? AND r.revision=?")) {
            query.setString(1,id);query.setInt(2,revision);try(var rows=query.executeQuery()) {
                if(!rows.next())throw refuse(WorkspaceRefusal.Code.NOT_FOUND);
                if(rows.getLong("bytes")<1||rows.getLong("bytes")>2_097_152-4096)throw unavailable();
                byte[] bytes=rows.getBytes("snapshot");var saved=CODEC.decode(bytes);
                if(!id.equals(saved.objectId())||!Integer.toString(revision).equals(saved.workspaceRevision())||!saved.content().nativeId().equals(rows.getString("native_id"))
                    ||!(saved.profile()?"profile-v3":"definition-v3").equals(rows.getString("kind"))||!integrity(rows.getString("issuer"),rows.getString("subject"),saved,bytes).equals(rows.getString("snapshot_digest")))throw unavailable();
                if(saved.content() instanceof V3NativeRevision.Profile p) {
                    if(!p.definition().objectId().equals(rows.getString("definition_id"))||!p.definition().workspaceRevision().equals(rows.getString("definition_revision")))throw unavailable();
                }else if(rows.getString("definition_id")!=null||rows.getString("definition_revision")!=null)throw unavailable();
                return saved;
            }
        }
    }
    static void validate(Connection connection)throws SQLException {
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT a.object_id,c.issuer,c.subject,c.current_revision,COUNT(r.revision),MIN(r.revision),MAX(r.revision) FROM v3_artifact_types a JOIN catalog c USING(object_id) LEFT JOIN v3_native_revisions r USING(object_id) GROUP BY a.object_id")) {
            while(rows.next()) {
                if(rows.getInt(4)!=rows.getInt(5)||rows.getInt(4)!=rows.getInt(7)||rows.getInt(6)!=1)throw unavailable();
                for(int i=1;i<=rows.getInt(4);i++) {
                    var saved=read(connection,rows.getString(1),i);
                    if(saved.publication().isPresent()){if(i==1)throw unavailable();publicationChain(saved,read(connection,saved.objectId(),i-1));}
                    if(saved.content() instanceof V3NativeRevision.Profile p) {
                        if(catalog(connection,new Owner(rows.getString(2),rows.getString(3)),p.definition().objectId(),false,false)==null)throw unavailable();
                        compatible(saved,read(connection,p.definition().objectId(),boundedRevision(p.definition().workspaceRevision())));
                    }
                }
            }
        }catch(WorkspaceRefusal|WorkspaceRejection invalid){throw unavailable();}
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT * FROM v3_native_replays")) {
            while(rows.next()) {
                var saved=read(connection,rows.getString("object_id"),rows.getInt("revision"));
                var command=reconstruct(saved,rows.getString("request_id"),rows.getString("kind"));
                if(!V3NativeWorkspaceDigests.commandDigest(command).equals(rows.getString("request_digest")))throw unavailable();
            }
        }catch(RuntimeException invalid){throw unavailable();}
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT r.object_id,r.revision,COUNT(p.request_id) FROM v3_native_revisions r LEFT JOIN v3_native_replays p USING(object_id,revision) GROUP BY r.object_id,r.revision")) {
            while(rows.next())if(rows.getInt(3)!=1)throw unavailable();
        }
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT r.object_id FROM revisions r JOIN v3_artifact_types a USING(object_id) LIMIT 1")){if(rows.next())throw unavailable();}
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT a.object_id FROM artifact_types a JOIN v3_artifact_types v USING(object_id) LIMIT 1")){if(rows.next())throw unavailable();}
    }
    private static NativeCommand reconstruct(V3NativeRevision saved,String request,String kind) {
        String expected=Integer.toString(Integer.parseInt(saved.workspaceRevision())-1);
        return switch(kind) {
            case "save-definition" -> {if(saved.profile()||saved.publication().isPresent())throw unavailable();yield new NativeCommand.SaveDefinition(saved.objectId(),expected,request,saved.format(),saved.source());}
            case "save-profile" -> {if(!saved.profile()||saved.publication().isPresent())throw unavailable();yield new NativeCommand.SaveProfile(saved.objectId(),expected,request,saved.format(),saved.source(),((V3NativeRevision.Profile)saved.content()).definition());}
            case "publish-definition" -> {if(saved.profile()||saved.publication().isEmpty())throw unavailable();yield new NativeCommand.PublishDefinition(saved.objectId(),expected,request,saved.publication().orElseThrow().exportPolicies());}
            case "publish-profile" -> {if(!saved.profile()||saved.publication().isEmpty())throw unavailable();yield new NativeCommand.PublishProfile(saved.objectId(),expected,request);}
            default -> throw unavailable();
        };
    }
    private static void publicationChain(V3NativeRevision saved,V3NativeRevision prior) {
        var publication=saved.publication().orElseThrow();
        if(prior.publication().isPresent()||!publication.sourceRevision().equals(prior.workspaceRevision())||!saved.content().equals(prior.content())
            ||!saved.source().equals(prior.source())||saved.format()!=prior.format()||!saved.compilerVersion().equals(prior.compilerVersion())||!saved.schemaVersion().equals(prior.schemaVersion()))throw unavailable();
    }
    private static void compatible(V3NativeRevision profile,V3NativeRevision definition) {
        if(definition.profile()||definition.publication().isEmpty()||!((V3NativeRevision.Definition)definition.content()).historicalReady()
            ||!((V3NativeRevision.Profile)profile.content()).checked().profile().logicalDefinitionDigest().equals(((V3NativeRevision.Definition)definition.content()).checked().logicalDigest()))throw unavailable();
    }
    private static int boundedRevision(String revision){if(revision.length()>2||Integer.parseInt(revision)>32)throw refuse(WorkspaceRefusal.Code.NOT_FOUND);return Integer.parseInt(revision);}
    private static String integrity(String issuer,String subject,V3NativeRevision revision,byte[] bytes){return WorkspaceDigests.fields("native-workspace-v3",issuer,subject,revision.profile()?"profile-v3":"definition-v3",revision.objectId(),revision.workspaceRevision(),WorkspaceDigests.sha256(bytes));}
    private static WorkspaceRefusal refuse(WorkspaceRefusal.Code code){return new WorkspaceRefusal(code);}
    private static WorkspaceRefusal unavailable(){return refuse(WorkspaceRefusal.Code.UNAVAILABLE);}
}
