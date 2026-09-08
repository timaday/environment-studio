package studio.environment.server.workspace;

import java.io.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.*;
import studio.environment.core.workspace.*;

final class NativeRequestReader {
    private static final JsonMapper JSON=JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(4).maxTokenCount(180_020).maxStringLength(8_388_608).maxNameLength(64).maxDocumentLength(8_388_608).build()).build()).build();
    NativeCommand read(String id, boolean profile, boolean publish,InputStream input) {
        try {
            byte[] bytes=input.readNBytes(8_388_609);if(bytes.length>8_388_608)throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
            var root=JSON.readTree(StrictUtf8.decode(bytes));if(root==null)throw invalid();
            var names=new HashSet<>(Set.of("expectedRevision","requestId"));
            if(!publish) {names.add("format");names.add("source");if(profile)names.add("definition");}else if(!profile)names.add("exportPolicies");
            keys(root,names);String expected=text(root,"expectedRevision"),request=text(root,"requestId");
            if(publish) {
                if(profile)return new NativeCommand.PublishProfile(id,expected,request);
                var array=root.get("exportPolicies");if(!array.isArray()||array.size()>20_000)throw invalid();var policies=new ArrayList<NativeCommand.Policy>();
                for(var node:array) {keys(node,Set.of("bindingId","documentId","content"));policies.add(new NativeCommand.Policy(text(node,"bindingId"),text(node,"documentId"),text(node,"content")));}
                return new NativeCommand.PublishDefinition(id,expected,request,policies);
            }
            String source=text(root,"source");if(StrictUtf8.encode(source).length>1_048_576)throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
            var format=DraftCommand.Format.valueOf(text(root,"format"));
            if(profile) {var ref=root.get("definition");keys(ref,Set.of("objectId","workspaceRevision"));return new NativeCommand.SaveProfile(id,expected,request,format,source,new NativeCommand.Reference(text(ref,"objectId"),text(ref,"workspaceRevision")));}
            return new NativeCommand.SaveDefinition(id,expected,request,format,source);
        } catch(IOException|tools.jackson.core.JacksonException|IllegalArgumentException invalid) {throw invalid();}
    }
    private static void keys(JsonNode node,Set<String> expected) {if(node==null||!node.isObject()||!node.propertyNames().equals(expected))throw invalid();}
    private static String text(JsonNode root,String name) {var value=root.get(name);if(value==null||!value.isString())throw invalid();String text=value.asString();StrictUtf8.encode(text);return text;}
    private static WorkspaceRefusal invalid() {return new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);}
}
