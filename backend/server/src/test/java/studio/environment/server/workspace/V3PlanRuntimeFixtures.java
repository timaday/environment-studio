package studio.environment.server.workspace;

import java.nio.file.*;
import java.util.*;
import studio.environment.core.workspace.*;
import studio.environment.core.session.Owner;

/** Actual private SQLite with independently invented repository fixtures only. */
public final class V3PlanRuntimeFixtures {
    public static V3NativeRevision draft(Path directory,Owner owner,String object,String expected)throws Exception {
        var service=new V3NativeWorkspace(new V3NativeSqliteStore(new SqliteDraftStore(directory)),new V3NativeWorkspaceCompiler());
        return service.saveDefinition(owner,new NativeCommand.SaveDefinition(object,expected,UUID.randomUUID().toString(),DraftCommand.Format.JSON,
                Files.readString(Path.of("../../fixtures/native-v3/definition.json"))));
    }
    public static NativeRevision v2(Path directory,Owner owner)throws Exception {
        var service=new NativeWorkspace(new NativeSqliteStore(new SqliteDraftStore(directory)),new NativeWorkspaceCompiler(),owner::equals);
        var draft=service.mutate(owner,new NativeCommand.SaveDefinition(UUID.randomUUID().toString(),"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,
                Files.readString(Path.of("../../fixtures/native-v2/definition.json"))));
        var checked=((NativeRevision.Definition)draft.content()).checked();
        var policies=checked.definition().bindings().stream().flatMap(b->b.documents().stream().map(d->new NativeCommand.Policy(b.id(),d.id(),"deny"))).toList();
        return service.mutate(owner,new NativeCommand.PublishDefinition(draft.objectId(),"1",UUID.randomUUID().toString(),policies));
    }
    public static Map<String,Long> counts(Path directory,boolean v3)throws Exception {
        var counts=new TreeMap<String,Long>();
        var tables=new ArrayList<>(List.of("catalog","native_revisions","native_replays"));
        if(v3)tables.addAll(List.of("v3_native_revisions","v3_native_replays"));
        try(var connection=new SqliteDraftStore(directory).open();var statement=connection.createStatement()) {
            for(var table:tables)try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){if(!rows.next())throw new AssertionError("MISSING_COUNT");counts.put(table,rows.getLong(1));}
        }
        return counts;
    }
}
