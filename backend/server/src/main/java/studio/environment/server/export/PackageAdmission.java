package studio.environment.server.export;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv2.NativeLexicalRules;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlResult;
import static studio.environment.server.export.PackageData.*;
import static studio.environment.server.export.PackageJson.fail;

/** Mechanical admission only; never a validated plan or permission to export or execute. */
public final class PackageAdmission {
    private final Schema executionSchema, payloadSchema, manifestSchema;
    public PackageAdmission() {
        var manifest=resource("guarded-manifest-v1.schema.json");var execution=(ObjectNode)manifest.get("properties").get("execution").deepCopy();execution.set("$defs",manifest.get("$defs"));
        var registry=SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,b->b.schemaLoader(l->l.fetchRemoteResources(false).block(iri->true)));
        executionSchema=registry.getSchema(execution);payloadSchema=registry.getSchema(resource("guarded-payload-v1.schema.json"));manifestSchema=registry.getSchema(manifest);
    }
    private static JsonNode resource(String name) {
        try(var stream=PackageAdmission.class.getResourceAsStream("/schemas/"+name)) {if(stream==null)throw new IllegalStateException("PACKAGE_SCHEMA_UNAVAILABLE");return JsonMapper.builder().build().readTree(stream);}
        catch(IOException invalid){throw new IllegalStateException("PACKAGE_SCHEMA_UNAVAILABLE");}
    }
    public sealed interface Result {
        final class Accepted implements Result {
            private final Execution execution;private final Payload payload;private final Counts counts;private final String payloadDigest,programDigest;
            private final JsonNode executionTree,payloadTree;
            private Accepted(Execution execution,Payload payload,Counts counts,String payloadDigest,String programDigest,JsonNode executionTree,JsonNode payloadTree){this.execution=execution;this.payload=payload;this.counts=counts;this.payloadDigest=payloadDigest;this.programDigest=programDigest;this.executionTree=executionTree;this.payloadTree=payloadTree;}
            public Execution execution(){return execution;}public Payload payload(){return payload;}public Counts counts(){return counts;}public String payloadDigest(){return payloadDigest;}public String programDigest(){return programDigest;}
            public byte[] canonicalExecution(){return PackageJson.canonical(executionTree,PackageJson.SMALL);}public byte[] canonicalPayload(){return PackageJson.canonical(payloadTree,PackageJson.LARGE);}
            JsonNode executionTree(){return executionTree.deepCopy();}
            @Override public String toString(){return "MechanicallyAdmittedPackageInputs[redacted,unqualified]";}
        }
        record Rejected(String code) implements Result { }
    }
    public Result read(byte[] executionJson,byte[] payloadJson) {
        try {
            var execution=PackageJson.parse(executionJson,PackageJson.SMALL,8192);var payload=PackageJson.parse(payloadJson,PackageJson.LARGE,4096);
            if(!executionSchema.validate(execution).isEmpty() || !payloadSchema.validate(payload).isEmpty())fail("SCHEMA_VIOLATION");
            var decodedExecution=execution(execution);var decodedPayload=payload(payload);var counts=validate(decodedExecution,decodedPayload);
            String payloadDigest=PackageJson.sha256(payloadJson),programDigest=PackageJson.executionDigest(execution,payloadDigest);
            return new Result.Accepted(decodedExecution,decodedPayload,counts,payloadDigest,programDigest,execution,payload);
        }catch(PackageJson.Refusal refused){return new Result.Rejected(refused.code);}
    }
    JsonNode manifest(byte[] json) {
        var tree=PackageJson.parse(json,PackageJson.SMALL,8192);if(!manifestSchema.validate(tree).isEmpty())fail("SCHEMA_VIOLATION");return tree;
    }
    private static Execution execution(JsonNode node) {
        var client=node.get("client");var destination=node.get("destination");var identity=destination.get("expectedPhysicalIdentity");var engine=Engine.valueOf(text(node,"engine").toUpperCase(java.util.Locale.ROOT));
        PhysicalIdentity physical=engine==Engine.POSTGRESQL?new PhysicalIdentity.Postgres(text(identity,"systemIdentifier"),text(identity,"databaseOid"),text(identity,"databaseName")):
            new PhysicalIdentity.Oracle(text(identity,"dbid"),text(identity,"dbUniqueName"),text(identity,"conId"),text(identity,"conUid"),text(identity,"conName"),text(identity,"pdbGuid"));
        Map<String,String> mechanisms=new TreeMap<>();node.get("mechanisms").properties().forEach(e->mechanisms.put(e.getKey(),e.getValue().asString()));
        List<String> profiles=new ArrayList<>();node.get("profilePublicationDigests").forEach(p->profiles.add(p.asString()));
        List<Policy> policies=new ArrayList<>();node.get("exportPolicies").forEach(p->policies.add(new Policy(text(p,"documentId"),text(p,"content"))));
        return new Execution(engine,text(node,"storage"),new Client(text(client,"family"),text(client,"version"),text(client,"platform")),
            new Versions(text(node,"serverVersion"),text(node,"supervisorVersion"),text(node,"templateVersion"),text(node,"writerVersion"),text(node,"parserVersion"),mechanisms),
            new Plan(text(node,"planId"),text(node,"planRevision"),text(node,"planInputFingerprint"),text(node,"observationFingerprint"),text(node,"definitionPublicationDigest"),profiles),
            new Binding(text(node,"bindingId"),text(node,"logicalDigest"),text(node,"bindingDigest")),
            new Destination(text(destination,"id"),text(destination,"host"),destination.get("port").intValue(),text(destination,"database"),text(destination,"transport"),text(destination,"transportIdentity"),text(destination,"provisioningPolicyVersion"),physical),policies);
    }
    private static Payload payload(JsonNode node) {
        var table=node.get("table");List<Document> documents=new ArrayList<>();
        for(var record:node.get("records")){var key=record.get("key");documents.add(new Document(text(record,"documentId"),new Key(keyType(text(key,"type")),text(key,"value")),text(record,"originalHex"),text(record,"targetHex")));}
        return new Payload(text(node,"bindingId"),Engine.valueOf(text(node,"engine").toUpperCase(java.util.Locale.ROOT)),text(node,"storage"),new Table(text(table,"schema"),text(table,"name"),text(table,"keyColumn"),text(table,"xmlColumn"),keyType(text(table,"keyType"))),documents);
    }
    private static Counts validate(Execution execution,Payload payload) {
        if(execution.engine()!=payload.engine() || !execution.storage().equals(payload.storage()) || !execution.binding().id().equals(payload.bindingId()))fail("EXECUTION_PAYLOAD_MISMATCH");
        var table=payload.table();if(table.keyColumn().equals(table.xmlColumn()))fail("INVALID_TABLE");
        int identifierLimit=payload.engine()==Engine.POSTGRESQL?63:128;
        for(String identifier:List.of(table.schema(),table.name(),table.keyColumn(),table.xmlColumn()))if(identifier.length()>identifierLimit)fail("INVALID_IDENTIFIER");
        sorted(execution.plan().profilePublicationDigests());sorted(execution.exportPolicies().stream().map(Policy::documentId).toList());sorted(payload.records().stream().map(Document::documentId).toList());
        if(!execution.exportPolicies().stream().map(Policy::documentId).toList().equals(payload.records().stream().map(Document::documentId).toList()))fail("POLICY_INVENTORY_MISMATCH");
        HashSet<Key> keys=new HashSet<>();long original=0,target=0;int changed=0;var xml=new LosslessXmlAdapter();
        for(var record:payload.records()) {
            if(record.key().type()!=table.keyType() || !NativeLexicalRules.validKey(record.key().type()==KeyType.INT64?NativeDefinition.KeyType.INT64:NativeDefinition.KeyType.TEXT,record.key().value()))fail("INVALID_KEY");
            if(!keys.add(record.key()))fail("DUPLICATE_KEY");
            if((record.originalHex().length()&1)!=0 || (record.targetHex().length()&1)!=0)fail("INVALID_HEX");
            original+=record.originalHex().length()/2;target+=record.targetHex().length()/2;if(original>16L*1024*1024 || target>16L*1024*1024)fail("RESOURCE_LIMIT");
            for(String hex:List.of(record.originalHex(),record.targetHex())){
                String source=PackageJson.utf8(HexFormat.of().parseHex(hex));if(source.length()>1_048_576)fail("RESOURCE_LIMIT");
                if(xml.project(source) instanceof XmlResult.Rejected)fail("INVALID_XML");
            }
            if(!record.originalHex().equals(record.targetHex()))changed++;
        }
        return new Counts(payload.records().size(),changed,original,target);
    }
    private static void sorted(List<String> values){for(int i=1;i<values.size();i++)if(PackageJson.UTF8.compare(values.get(i-1),values.get(i))>=0)fail("NONCANONICAL_ORDER");}
    private static String text(JsonNode node,String name){return node.get(name).asString();}
    private static KeyType keyType(String value){return value.equals("int64")?KeyType.INT64:KeyType.TEXT;}
}
