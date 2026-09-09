package studio.environment.server.workspace;

import java.math.BigInteger;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.workspace.*;

/** Separate schema-3 typed historical codec only, independent of current publication eligibility. */
final class V3NativeSnapshotCodec {
    static final JsonMapper JSON = JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder()
        .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(64).maxTokenCount(200_000)
            .maxStringLength(1_048_576).maxNumberLength(1024).maxDocumentLength(2_097_152).build()).build())
        .addModule(new SimpleModule().addSerializer(BigInteger.class,ToStringSerializer.instance))
        .addModule(NativeFieldMappingCodec.module())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final com.networknt.schema.Schema DEFINITION_SHAPE=shape("definition-inspection-v3.schema.json");
    private static final com.networknt.schema.Schema PROFILE_SHAPE=shape("profile-inspection-v3.schema.json");
    private static com.networknt.schema.Schema shape(String name) {
        try(var input=V3NativeSnapshotCodec.class.getResourceAsStream("/schemas/"+name)) {
            if(input==null)throw unavailable();
            return com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_7,
                builder->builder.schemaLoader(loader->loader.fetchRemoteResources(false).block(iri->true))).getSchema(JSON.readTree(input));
        }catch(java.io.IOException failure){throw unavailable();}
    }
    byte[] encode(V3NativeRevision revision) {
        try {
            byte[] bytes = raw(revision);
            decode(bytes);
            return bytes;
        } catch (RuntimeException invalid) { throw unavailable(); }
    }
    private byte[] raw(V3NativeRevision revision) {
        var root=JSON.createObjectNode();root.put("kind",revision.profile()?"profile-v3":"definition-v3");
        root.put("objectId",revision.objectId());root.put("workspaceRevision",revision.workspaceRevision());root.put("format",revision.format().name());
        root.put("source",revision.source());root.put("sourceDigest",revision.sourceDigest());root.put("compilerVersion",revision.compilerVersion());root.put("schemaVersion",revision.schemaVersion());
        root.set("content",JSON.valueToTree(revision.content()));revision.publication().ifPresent(p->root.set("publication",JSON.valueToTree(p)));
        return JSON.writeValueAsBytes(root);
    }
    V3NativeRevision decode(byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > 2_097_152) throw unavailable();
            var root=JSON.readTree(StrictUtf8.decode(bytes));
            strictStrings(root);
            Set<String> keys=new HashSet<>(Set.of("kind","objectId","workspaceRevision","format","source","sourceDigest","compilerVersion","schemaVersion","content"));
            if(root.has("publication"))keys.add("publication");if(!root.isObject()||!root.propertyNames().equals(keys))throw unavailable();
            String kind=text(root,"kind");boolean profile=kind.equals("profile-v3");if(!profile&&!kind.equals("definition-v3"))throw unavailable();
            if(!text(root,"compilerVersion").equals(profile?"profile-compiler-v3":"native-compiler-v3")||!text(root,"schemaVersion").equals("3"))throw unavailable();
            numericFields(root, profile);
            V3NativeRevision.Content content=profile?JSON.treeToValue(root.get("content"),V3NativeRevision.Profile.class):JSON.treeToValue(root.get("content"),V3NativeRevision.Definition.class);
            var revision=new V3NativeRevision(text(root,"objectId"),text(root,"workspaceRevision"),DraftCommand.Format.valueOf(text(root,"format")),text(root,"source"),
                text(root,"sourceDigest"),text(root,"compilerVersion"),text(root,"schemaVersion"),content,
                root.has("publication")?Optional.of(JSON.treeToValue(root.get("publication"),V3NativeRevision.Publication.class)):Optional.empty());
            if(!DraftCommand.uuid(revision.objectId())||!NativeCommand.revision(revision.workspaceRevision(),false)||!NativeCommand.revision(content.nativeRevision(),false)
                ||!revision.sourceDigest().equals(WorkspaceDigests.sha256(StrictUtf8.encode(revision.source())))||StrictUtf8.encode(revision.source()).length > 1_048_576||!Arrays.equals(bytes,raw(revision)))throw unavailable();
            if(content instanceof V3NativeRevision.Definition d) {
                if (d.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.phase() != studio.environment.core.definition.DefinitionDiagnostic.Phase.PUBLICATION
                        || !diagnostic.code().matches("[A-Z][A-Z0-9_]{0,127}"))) throw unavailable();
                if(!d.checked().logicalDigest().matches("[a-f0-9]{64}")||d.checked().bindingDigests().values().stream().anyMatch(v->!v.matches("[a-f0-9]{64}")))throw unavailable();
                if(!d.checked().mechanisms().keySet().equals(studio.environment.core.definitionv3.NativeMechanisms.required(d.checked().definition()).keySet())
                    ||d.checked().mechanisms().values().stream().anyMatch(v->v==null||v.signum()<=0||v.toString().length()>1024))throw unavailable();
                var ids=new HashSet<String>();d.checked().definition().bindings().forEach(b->ids.add(b.id()));if(!ids.equals(d.checked().bindingDigests().keySet()))throw unavailable();
            }
            if(!(profile?PROFILE_SHAPE:DEFINITION_SHAPE).validate(model(revision)).isEmpty())throw unavailable();
            if(content instanceof V3NativeRevision.Profile p&&!p.checked().contentDigest().matches("[a-f0-9]{64}"))throw unavailable();
            if(revision.publication().isPresent()) {
                var publication=revision.publication().orElseThrow();
                if (!NativeCommand.revision(publication.sourceRevision(), false)
                        || !new BigInteger(publication.sourceRevision()).add(BigInteger.ONE).toString().equals(revision.workspaceRevision())) throw unavailable();
                if(!V3NativeWorkspaceDigests.publication(revision,publication.sourceRevision(),publication.exportPolicies()).equals(publication.digest()))throw unavailable();
                if(profile&&!publication.exportPolicies().isEmpty())throw unavailable();
                if(content instanceof V3NativeRevision.Definition d) { if(!d.historicalReady())throw unavailable();validatePolicies(d,publication.exportPolicies()); }
            }
            return revision;
        } catch(RuntimeException invalid) { throw unavailable(); }
    }
    ObjectNode model(V3NativeRevision revision) {
        Object value=revision.content() instanceof V3NativeRevision.Definition d?d.checked().definition():((V3NativeRevision.Profile)revision.content()).checked().profile();
        ObjectNode result=JSON.valueToTree(value);result.put("schemaVersion","3");lower(result);
        if(revision.content() instanceof V3NativeRevision.Definition) {
            for(var entity:result.get("logical").get("entityTypes")) {var identity=(ObjectNode)entity.get("identity");identity.put("scope","type");identity.put("normalization","exact");}
            for (String rules : List.of("rules", "computedRules"))
                for (var rule : result.get("logical").get(rules)) ((ObjectNode)rule).put("kind", "entity-count");
        }
        return result;
    }
    private static void lower(JsonNode node) {
        Set<String> enums=Set.of("engine","storage","keyType","valueType","classification","sensitivity","kind");
        if(node.isObject())for(var entry:node.properties()) {
            if(enums.contains(entry.getKey())&&entry.getValue().isString())((ObjectNode)node).put(entry.getKey(),entry.getValue().asString().toLowerCase(Locale.ROOT).replace('_','-'));
            else if(entry.getKey().equals("operationCapabilities")) {var array=((ObjectNode)node).putArray(entry.getKey());entry.getValue().forEach(v->array.add(v.asString().toLowerCase(Locale.ROOT).replace('_','-')));}
            else lower(entry.getValue());
        } else if(node.isArray())node.forEach(V3NativeSnapshotCodec::lower);
    }
    private static void strictStrings(JsonNode node) {
        if (node.isString()) StrictUtf8.encode(node.asString());
        else if (node.isObject()) for (var field : node.properties()) {
            StrictUtf8.encode(field.getKey());
            strictStrings(field.getValue());
        } else if (node.isArray()) node.forEach(V3NativeSnapshotCodec::strictStrings);
    }
    // Only typed numeric paths: map keys such as a binding named "minimum" are opaque IDs.
    private static void numericFields(JsonNode root, boolean profile) {
        JsonNode checked = root.get("content").get("checked");
        JsonNode model = checked.get(profile ? "profile" : "definition");
        decimal(model.get("revision"));
        if (profile) return;
        JsonNode mechanisms = checked.get("mechanisms");
        if (!mechanisms.isObject()) throw unavailable();
        mechanisms.properties().forEach(version -> decimal(version.getValue()));
        JsonNode logical = model.get("logical");
        for (String name : List.of("relations", "rules", "cooccurrences", "computedRules")) {
            JsonNode declarations = logical.get(name);
            if (!declarations.isArray()) throw unavailable();
            for (JsonNode declaration : declarations) {
                decimal(declaration.get("minimum"));
                decimal(declaration.get("maximum"));
            }
        }
    }
    // Numeric strings must be bounded before Jackson constructs BigInteger values.
    private static void decimal(JsonNode value) {
        if (value == null || !value.isString()) throw unavailable();
        String text = value.asString();
        if (text.length() > 1024 || !text.matches("0|[1-9][0-9]*")) throw unavailable();
    }
    private static void validatePolicies(V3NativeRevision.Definition definition, List<NativeCommand.Policy> policies) {
        var expected = new HashSet<List<String>>();
        definition.checked().definition().bindings().forEach(binding -> binding.documents().forEach(document ->
                expected.add(List.of(binding.id(), document.id()))));
        var actual = new HashSet<List<String>>();
        for (var policy : policies) if (!actual.add(List.of(policy.bindingId(), policy.documentId()))) throw unavailable();
        if (!actual.equals(expected)) throw unavailable();
    }
    static String text(JsonNode node,String name) { var value=node.get(name);if(value==null||!value.isString())throw unavailable();return value.asString(); }
    private static WorkspaceRefusal unavailable() { return new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
}
