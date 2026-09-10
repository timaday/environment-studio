package studio.environment.server.workspace;

import java.nio.file.*;
import java.util.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Independently invented, explicitly test-produced publication history; no runtime qualification. */
public final class V3PublicationHttpFixtures {
    public record History(V3NativeRevision revision, NativeCommand command, String body) {
        @Override public String toString() { return "InventedPublicationHistory[redacted]"; }
    }
    public static String body(NativeCommand command) {
        var fields = new LinkedHashMap<String,Object>();
        fields.put("expectedRevision",command.expectedRevision());fields.put("requestId",command.requestId());
        if(command instanceof NativeCommand.PublishDefinition definition) fields.put("exportPolicies",definition.exportPolicies());
        return V3NativeSnapshotCodec.JSON.writeValueAsString(fields);
    }
    public static History history(Path directory, Owner owner, boolean profile) throws Exception {
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        V3NativeRevision draft;
        NativeCommand command;
        List<NativeCommand.Policy> policies;
        if(profile) {
            var definition = V3ProfileHttpFixtures.definition(directory,owner,false);
            draft = V3ProfileHttpFixtures.save(directory,owner,definition,V3ProfileHttpFixtures.source());
            policies=List.of();command=new NativeCommand.PublishProfile(draft.objectId(),"1",UUID.randomUUID().toString());
        } else {
            String source=Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
            var save=new NativeCommand.SaveDefinition(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,source);
            var checked=new V3NativeWorkspaceCompiler().definition(save).checked();
            // Explicit historical fixture, never an actual current compiler Ready result.
            draft=new V3NativeRevision(save.objectId(),"1",save.format(),source,V3NativeWorkspaceDigests.source(source),"native-compiler-v3","3",
                    new V3NativeRevision.Definition(checked,List.of()),Optional.empty());
            store.append(owner,save,draft);
            policies=checked.definition().bindings().stream().flatMap(b->b.documents().stream().map(d->new NativeCommand.Policy(b.id(),d.id(),"deny"))).toList();
            command=new NativeCommand.PublishDefinition(draft.objectId(),"1",UUID.randomUUID().toString(),policies);
        }
        var next=new V3NativeRevision(draft.objectId(),"2",draft.format(),draft.source(),draft.sourceDigest(),draft.compilerVersion(),"3",draft.content(),Optional.empty());
        var publication=new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(next,"1",policies),"1",policies);
        var published=store.append(owner,command,new V3NativeRevision(next.objectId(),"2",next.format(),next.source(),next.sourceDigest(),next.compilerVersion(),"3",next.content(),Optional.of(publication)));
        return new History(published,command,body(command));
    }
    public static void later(Path directory,Owner owner,History history) {
        var store=new V3NativeSqliteStore(new SqliteDraftStore(directory));var revision=history.revision();
        if(revision.content() instanceof V3NativeRevision.Profile profile) {
            new V3ProfileWorkspace(store,new V3ProfileWorkspaceCompiler()).saveProfile(owner,new NativeCommand.SaveProfile(revision.objectId(),"2",UUID.randomUUID().toString(),DraftCommand.Format.YAML,"---\n"+revision.source(),profile.definition()));
        } else new V3NativeWorkspace(store,new V3NativeWorkspaceCompiler()).saveDefinition(owner,new NativeCommand.SaveDefinition(revision.objectId(),"2",UUID.randomUUID().toString(),DraftCommand.Format.YAML,"---\n"+revision.source()));
    }
}
