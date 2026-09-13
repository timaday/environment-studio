package studio.environment.server.planning;

import java.util.*;
import java.math.BigInteger;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definition.DefinitionDraft.RelationKind;

/** Independently invented structural fixture; explicit checked publication witness only. */
public final class V3StructuralWireFixtures {
    public static final String XML="<items><item id='one' tone='alpha' finish='x' next='two' secret='MOCK-STRUCTURAL-SECRET' optional=''/><item id='two' tone='beta' finish='y'/></items>";
    private final SharedV3PlanXmlTest fixture;
    public final HostedPlanService service;
    public final SessionLedger.Lease lease;
    public final String plan;
    public V3StructuralWireFixtures() {
        var base=V3PhysicalHttpWitnesses.definition().definition();var logical=base.logical();var binding=base.bindings().getFirst();var projection=binding.documents().getFirst().entities().getFirst();
        var relation=new Relation("link","item","item",RelationKind.REFERENCE,BigInteger.ZERO,BigInteger.ONE,false);
        var operations=new ArrayList<>(logical.operationCapabilities());operations.add(Operation.MOVE_RELATION);
        var model=new studio.environment.core.definitionv3.NativeDefinition.Logical(logical.entityTypes(),List.of(relation),logical.rules(),operations,logical.computedTypes(),logical.derivations(),logical.cooccurrences(),logical.computedRules());
        var mapped=new Projection(projection.id(),projection.type(),projection.path(),projection.fields(),List.of(new ReferenceMapping("link",new ExpandedName("","next"))));
        var bound=new Binding(binding.id(),binding.engine(),binding.storage(),binding.schema(),binding.table(),binding.keyColumn(),binding.xmlColumn(),binding.keyType(),List.of(new Document("sheet","1",List.of(mapped))));
        var result=new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new studio.environment.core.definitionv3.NativeDefinition(base.id(),base.revision(),model,List.of(bound)));
        var checked=org.junit.jupiter.api.Assertions.assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,result).checked();
        fixture=SharedV3PlanXmlTest.with(checked,XML);service=fixture.service();lease=fixture.lease;plan=fixture.inspected(service);
    }
    public HostedPlanService.ViewSnapshot snapshot(String revision){return fixture.snapshot(service,plan,revision);}
    public PlanCommand.Ref reference(String identity){return snapshot("2").reference(DerivedTargetInputAdapterTest.old(identity));}
}
