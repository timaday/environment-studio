package studio.environment.server.planning;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import java.util.Map;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.observation.ObservationResult;

/** Explicit invented HTTP qualification witness; never a runtime publication authority. */
public final class V3StructuralHttpWitnesses {
    private V3StructuralHttpWitnesses() {}
    public static NativeCompilationResult.Checked definition() {
        var base=DerivedTargetInputAdapterTest.definition(false).definition();var logical=base.logical();var binding=base.bindings().getFirst();var projection=binding.documents().getFirst().entities().getFirst();
        var original=logical.entityTypes().getFirst();var fields=new ArrayList<>(original.fields());
        fields.add(new Field("secret",ValueType.TEXT,false,Classification.ENVIRONMENT,Sensitivity.SECRET,true,true));
        fields.add(new Field("optional",ValueType.TEXT,false,Classification.ENVIRONMENT,Sensitivity.PUBLIC,true,true));
        var type=new EntityType(original.id(),original.label(),fields,original.identity());var mappings=new ArrayList<>(projection.fields());
        for(String field:List.of("secret","optional"))mappings.add(new FieldMapping(field,new ExpandedName("",field)));
        var mapped=new Projection(projection.id(),projection.type(),projection.path(),mappings,List.of(new ReferenceMapping("link",new ExpandedName("","next"))));
        var model=new studio.environment.core.definitionv3.NativeDefinition.Logical(List.of(type),List.of(new studio.environment.core.definition.DefinitionDraft.Relation("link","item","item",studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE,java.math.BigInteger.ZERO,java.math.BigInteger.ONE,false)),logical.rules(),List.of(Operation.RETAIN_ENTITY,Operation.CREATE_ENTITY,Operation.REMOVE_ENTITY,Operation.BIND_FIELD,Operation.MOVE_RELATION),logical.computedTypes(),logical.derivations(),logical.cooccurrences(),logical.computedRules());
        var bound=new Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),binding.table(),binding.keyColumn(),binding.xmlColumn(),binding.keyType(),List.of(new Document("sheet","1",List.of(mapped))));
        var compiled=new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new studio.environment.core.definitionv3.NativeDefinition(base.id(),base.revision(),model,List.of(bound)));
        return org.junit.jupiter.api.Assertions.assertInstanceOf(NativeCompilationResult.Incomplete.class,compiled).checked();
    }
    public static final String XML="<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x' secret='MOCK-SECRET-VIEW' optional='' next='two'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";
    public static ObservationResult observation() {
        var checked = definition();
        String xml = XML;
        var identity = Map.of("systemIdentifier", "731", "databaseOid", "19", "databaseName", "invented_db");
        Map<String, Object> evidence = Map.of("engine", "postgresql", "cleanup", "complete",
                "destination", Map.of("id", "mock-destination", "host", "invented.invalid", "port", 5432,
                        "database", "invented_db", "transportIdentity", "c".repeat(64),
                        "provisioningPolicyVersion", "mock-v1", "observedPhysicalIdentity", identity,
                        "expectedPhysicalIdentity", identity),
                "metadata", Map.of("adapterVersion", "jdbc-observation-v3", "operationPolicyVersion", "postgresql-read-operation-v1",
                        "visibility", "complete", "readOnlyOperation", "verified", "snapshot", "repeatable-read-read-only"));
        return new ObservationResult.Complete(new ObservationResult.Observation("c".repeat(64),
                checked.logicalDigest(), checked.bindingDigests().get("mock-pg"),
                List.of(new ObservationResult.Document("sheet", new ObservationResult.Key("int64", "1"), xml,
                        xml.getBytes(StandardCharsets.UTF_8).length, xml.length(), DerivedTargetInputAdapterTest.digest(xml))), evidence));
    }
}
