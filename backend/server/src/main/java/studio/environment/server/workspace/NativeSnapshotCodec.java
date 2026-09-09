package studio.environment.server.workspace;

import java.math.BigInteger;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.workspace.*;

/** Registered schema-2 typed historical codec, independent of current publication eligibility. */
final class NativeSnapshotCodec {
    static final JsonMapper JSON = JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder()
        .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(64).maxTokenCount(200_000)
            .maxStringLength(1_048_576).maxNumberLength(1024).maxDocumentLength(2_097_152).build()).build())
        .addModule(new SimpleModule().addSerializer(BigInteger.class,ToStringSerializer.instance))
        .addModule(NativeFieldMappingCodec.module())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final com.networknt.schema.Schema DEFINITION_SHAPE=shape("definition-inspection-v2.schema.json");
    private static final com.networknt.schema.Schema PROFILE_SHAPE=shape("profile-inspection-v2.schema.json");
    private static com.networknt.schema.Schema shape(String name) {
        try(var input=NativeSnapshotCodec.class.getResourceAsStream("/schemas/"+name)) {
            if(input==null)throw unavailable();
            return com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_7,
                builder->builder.schemaLoader(loader->loader.fetchRemoteResources(false).block(iri->true))).getSchema(JSON.readTree(input));
        }catch(java.io.IOException failure){throw unavailable();}
    }
    byte[] encode(NativeRevision revision) {
        var root=JSON.createObjectNode();root.put("kind",revision.profile()?"profile-v2":"definition-v2");
        root.put("objectId",revision.objectId());root.put("workspaceRevision",revision.workspaceRevision());root.put("format",revision.format().name());
        root.put("source",revision.source());root.put("sourceDigest",revision.sourceDigest());root.put("compilerVersion",revision.compilerVersion());root.put("schemaVersion",revision.schemaVersion());
        root.set("content",JSON.valueToTree(revision.content()));revision.publication().ifPresent(p->root.set("publication",JSON.valueToTree(p)));
        return JSON.writeValueAsBytes(root);
    }
    NativeRevision decode(byte[] bytes) {
        try {
            var root=JSON.readTree(StrictUtf8.decode(bytes));
            Set<String> keys=new HashSet<>(Set.of("kind","objectId","workspaceRevision","format","source","sourceDigest","compilerVersion","schemaVersion","content"));
            if(root.has("publication"))keys.add("publication");if(!root.isObject()||!root.propertyNames().equals(keys))throw unavailable();
            String kind=text(root,"kind");boolean profile=kind.equals("profile-v2");if(!profile&&!kind.equals("definition-v2"))throw unavailable();
            if(!text(root,"compilerVersion").equals(profile?"profile-compiler-v2":"native-compiler-v2")||!text(root,"schemaVersion").equals("2"))throw unavailable();
            NativeRevision.Content content=profile?JSON.treeToValue(root.get("content"),NativeRevision.Profile.class):JSON.treeToValue(root.get("content"),NativeRevision.Definition.class);
            var revision=new NativeRevision(text(root,"objectId"),text(root,"workspaceRevision"),DraftCommand.Format.valueOf(text(root,"format")),text(root,"source"),
                text(root,"sourceDigest"),text(root,"compilerVersion"),text(root,"schemaVersion"),content,
                root.has("publication")?Optional.of(JSON.treeToValue(root.get("publication"),NativeRevision.Publication.class)):Optional.empty());
            if(!DraftCommand.uuid(revision.objectId())||!NativeCommand.revision(revision.workspaceRevision(),false)||!NativeCommand.revision(content.nativeRevision(),false)
                ||!revision.sourceDigest().equals(WorkspaceDigests.sha256(StrictUtf8.encode(revision.source())))||!Arrays.equals(bytes,encode(revision)))throw unavailable();
            if(content instanceof NativeRevision.Definition d) {
                if(!d.ready())new studio.environment.core.definitionv2.NativeCompilationResult.Incomplete(d.checked(),d.diagnostics());
                if(!d.checked().logicalDigest().matches("[a-f0-9]{64}")||d.checked().bindingDigests().values().stream().anyMatch(v->!v.matches("[a-f0-9]{64}")))throw unavailable();
                if(!d.checked().mechanisms().keySet().equals(studio.environment.core.definitionv2.NativeMechanisms.required(d.checked().definition()).keySet())
                    ||d.checked().mechanisms().values().stream().anyMatch(v->v==null||v.signum()<=0||v.toString().length()>1024))throw unavailable();
                var ids=new HashSet<String>();d.checked().definition().bindings().forEach(b->ids.add(b.id()));if(!ids.equals(d.checked().bindingDigests().keySet()))throw unavailable();
            }
            if(!(profile?PROFILE_SHAPE:DEFINITION_SHAPE).validate(model(revision)).isEmpty())throw unavailable();
            if(content instanceof NativeRevision.Profile p&&!p.checked().contentDigest().matches("[a-f0-9]{64}"))throw unavailable();
            if(revision.publication().isPresent()) {
                var publication=revision.publication().orElseThrow();
                if(!NativeWorkspaceDigests.publication(revision,publication.sourceRevision(),publication.exportPolicies()).equals(publication.digest()))throw unavailable();
                if(profile&&!publication.exportPolicies().isEmpty())throw unavailable();
                if(content instanceof NativeRevision.Definition d) { if(!d.ready())throw unavailable();NativeWorkspace.validatePolicies(d,publication.exportPolicies()); }
            }
            return revision;
        } catch(RuntimeException invalid) { throw unavailable(); }
    }
    ObjectNode model(NativeRevision revision) {
        Object value=revision.content() instanceof NativeRevision.Definition d?d.checked().definition():((NativeRevision.Profile)revision.content()).checked().profile();
        ObjectNode result=JSON.valueToTree(value);result.put("schemaVersion","2");lower(result);
        if(revision.content() instanceof NativeRevision.Definition) {
            for(var entity:result.get("logical").get("entityTypes")) {var identity=(ObjectNode)entity.get("identity");identity.put("scope","type");identity.put("normalization","exact");}
            for(var rule:result.get("logical").get("rules"))((ObjectNode)rule).put("kind","entity-count");
        }
        return result;
    }
    private static void lower(JsonNode node) {
        Set<String> enums=Set.of("engine","storage","keyType","valueType","classification","sensitivity","kind");
        if(node.isObject())for(var entry:node.properties()) {
            if(enums.contains(entry.getKey())&&entry.getValue().isString())((ObjectNode)node).put(entry.getKey(),entry.getValue().asString().toLowerCase(Locale.ROOT).replace('_','-'));
            else if(entry.getKey().equals("operationCapabilities")) {var array=((ObjectNode)node).putArray(entry.getKey());entry.getValue().forEach(v->array.add(v.asString().toLowerCase(Locale.ROOT).replace('_','-')));}
            else lower(entry.getValue());
        } else if(node.isArray())node.forEach(NativeSnapshotCodec::lower);
    }
    static String text(JsonNode node,String name) { var value=node.get(name);if(value==null||!value.isString())throw unavailable();return value.asString(); }
    private static WorkspaceRefusal unavailable() { return new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
}
