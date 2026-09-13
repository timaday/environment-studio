package studio.environment.server.planning;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.observation.ObservationResult;

/** Explicit independently invented multi-document/child-property witness; no publication authority. */
public final class V3WorkflowHttpWitnesses {
    private V3WorkflowHttpWitnesses() {}
    public static NativeCompilationResult.Checked definition() {
        var base=V3StructuralHttpWitnesses.definition().definition();var logical=base.logical();var binding=base.bindings().getFirst();var projection=binding.documents().getFirst().entities().getFirst();
        var original=logical.entityTypes().getFirst();var fields=new ArrayList<>(original.fields());fields.add(new Field("extra",ValueType.TEXT,false,Classification.ENVIRONMENT,Sensitivity.PUBLIC,true,true));
        var type=new EntityType(original.id(),original.label(),fields,original.identity());var mappings=new ArrayList<>(projection.fields());mappings.add(new FieldMapping("extra",new ChildProperty(new ExpandedName("","prop"),new ExpandedName("","key"),"extra",new ExpandedName("","value"))));
        var mapped=new Projection("items",projection.type(),projection.path(),mappings,projection.references());var tail=new Projection("tail-items",projection.type(),projection.path(),mappings,projection.references());
        var model=new studio.environment.core.definitionv3.NativeDefinition.Logical(List.of(type),logical.relations().stream().map(r->new studio.environment.core.definition.DefinitionDraft.Relation(r.id(),r.fromType(),r.toType(),r.kind(),r.minimum(),r.maximum(),true)).toList(),logical.rules(),logical.operationCapabilities(),logical.computedTypes(),logical.derivations(),logical.cooccurrences(),logical.computedRules());
        var bound=new Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),binding.table(),binding.keyColumn(),binding.xmlColumn(),binding.keyType(),List.of(new Document("sheet","1",List.of(mapped)),new Document("tail","2",List.of(tail))));
        var result=new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new studio.environment.core.definitionv3.NativeDefinition(base.id(),base.revision(),model,List.of(bound)));
        var incomplete=org.junit.jupiter.api.Assertions.assertInstanceOf(NativeCompilationResult.Incomplete.class,result);org.junit.jupiter.api.Assertions.assertTrue(incomplete.diagnostics().stream().allMatch(d->d.code().equals("MECHANISM_UNQUALIFIED")));return incomplete.checked();
    }
    public static final String SHEET="<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x' next='two' secret='MOCK-DOC-SECRET' optional=''><prop key='extra' value='al&#112;ha'/><prop key='other' value='alpha'/></item><unmapped sample='alpha'/></items>";
    public static final String TAIL="<items><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x' next='two'/></items>";
    public static ObservationResult observation() {
        var checked=definition();var identity=Map.of("systemIdentifier","731","databaseOid","19","databaseName","invented_db");
        Map<String,Object> evidence=Map.of("engine","postgresql","cleanup","complete","destination",Map.of("id","mock-destination","host","invented.invalid","port",5432,"database","invented_db","transportIdentity","c".repeat(64),"provisioningPolicyVersion","mock-v1","observedPhysicalIdentity",identity,"expectedPhysicalIdentity",identity),"metadata",Map.of("adapterVersion","jdbc-observation-v3","operationPolicyVersion","postgresql-read-operation-v1","visibility","complete","readOnlyOperation","verified","snapshot","repeatable-read-read-only"));
        var documents=new ArrayList<ObservationResult.Document>();for(var entry:Map.of("sheet",SHEET,"tail",TAIL).entrySet()){String xml=entry.getValue();documents.add(new ObservationResult.Document(entry.getKey(),new ObservationResult.Key("int64",entry.getKey().equals("sheet")?"1":"2"),xml,xml.getBytes(StandardCharsets.UTF_8).length,xml.length(),DerivedTargetInputAdapterTest.digest(xml)));}
        documents.sort(Comparator.comparing(ObservationResult.Document::documentId));return new ObservationResult.Complete(new ObservationResult.Observation("c".repeat(64),checked.logicalDigest(),checked.bindingDigests().get("mock-pg"),documents,evidence));
    }
}
