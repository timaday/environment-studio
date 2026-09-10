package studio.environment.server.workspace;

import java.nio.file.Path;
import java.util.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;
import studio.environment.server.planning.V3WorkflowHttpWitnesses;
import static org.junit.jupiter.api.Assertions.*;

/** Invented actual compiler output stored as test-only historical publication, never runtime qualification. */
public final class V3WorkflowStorageFixtures {
    public static void definition(Path directory,Owner owner,String object){
        var checked=V3WorkflowHttpWitnesses.definition();
        var content=new V3NativeRevision.Definition(checked,List.of());
        var shell=new V3NativeRevision(object,"1",DraftCommand.Format.JSON,"{}",V3NativeWorkspaceDigests.source("{}"),"native-compiler-v3","3",content,Optional.empty());
        var tree=new V3NativeSnapshotCodec().model(shell);
        tree.put("revision",checked.definition().revision().intValueExact());
        var capabilities=((tools.jackson.databind.node.ObjectNode)tree.get("logical")).putArray("operationCapabilities");
        checked.definition().logical().operationCapabilities().forEach(capability->capabilities.add(capability.name().toLowerCase(Locale.ROOT).replace('_','-')));
        for(String name:List.of("rules","computedRules","cooccurrences"))for(var rule:tree.get("logical").get(name)){
            var node=(tools.jackson.databind.node.ObjectNode)rule;
            node.put("minimum",new java.math.BigInteger(node.get("minimum").asString()));node.put("maximum",new java.math.BigInteger(node.get("maximum").asString()));
        }
        for(var rule:tree.get("logical").get("relations")){
            var node=(tools.jackson.databind.node.ObjectNode)rule;node.put("minimum",new java.math.BigInteger(node.get("minimum").asString()));node.put("maximum",new java.math.BigInteger(node.get("maximum").asString()));
        }
        String source=V3NativeSnapshotCodec.JSON.writeValueAsString(tree);
        var command=new NativeCommand.SaveDefinition(object,"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,source);
        var parsed=new V3NativeWorkspaceCompiler().definition(command).checked();
        assertEquals(checked,parsed);
        var store=new V3NativeSqliteStore(new SqliteDraftStore(directory));
        var one=new V3NativeRevision(object,"1",command.format(),source,V3NativeWorkspaceDigests.source(source),"native-compiler-v3","3",content,Optional.empty());store.append(owner,command,one);
        var policies=checked.definition().bindings().stream().flatMap(b->b.documents().stream().map(d->new NativeCommand.Policy(b.id(),d.id(),"deny"))).toList();
        var two=new V3NativeRevision(object,"2",one.format(),source,one.sourceDigest(),one.compilerVersion(),"3",content,Optional.empty());
        var pub=new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(two,"1",policies),"1",policies);
        store.append(owner,new NativeCommand.PublishDefinition(object,"1",UUID.randomUUID().toString(),policies),new V3NativeRevision(object,"2",one.format(),source,one.sourceDigest(),one.compilerVersion(),"3",content,Optional.of(pub)));
    }
}
