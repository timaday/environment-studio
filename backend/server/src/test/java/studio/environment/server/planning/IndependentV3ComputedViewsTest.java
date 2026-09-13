package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.derived.ComputedGraph;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.server.plan.V3PlanComputedViews;
import studio.environment.server.plan.V3PlanComputedViews.ResultKey;

class IndependentV3ComputedViewsTest {
    @Test void sameFieldSelfPairRetainsTwoDeclaredRolesAndExactSelectorAcrossDuplicateOccurrences() {
        var base = definition(false).definition(); var logical = base.logical();
        var self = new NativeDefinition.Cooccurrence("self-pair","by-tone","by-tone",BigInteger.ZERO,BigInteger.ONE);
        var changed = new NativeDefinition.Logical(logical.entityTypes(),logical.relations(),logical.rules(),logical.operationCapabilities(),logical.computedTypes(),logical.derivations(),List.of(self),logical.computedRules());
        var checked = assertInstanceOf(NativeCompilationResult.Incomplete.class,new NativeDefinitionCompiler().compile(new NativeDefinition(base.id(),base.revision(),changed,base.bindings()))).checked();
        String xml = "<items><item id='one' tone='alpha'/><item id='two' tone='alpha'/></items>";
        var fixture = SharedV3PlanXmlTest.with(checked,xml); var service = fixture.service(); String plan = fixture.inspected(service);
        try (var view = service.reserveView(fixture.lease,plan)) {
            view.run(() -> {
                view.pin("2"); var snapshot = view.snapshot();
                var node = new ComputedGraph.Key("tones","by-tone","alpha");
                var key = new ResultKey.CooccurrenceKey("self-pair",node,node);
                var pairs = V3PlanComputedViews.cooccurrences(view,false,0,100);
                assertEquals(List.of(new V3PlanComputedViews.Cooccurrence("self-pair",node,node,2)),pairs.items());
                for (int i = 0; i < 2; i++) {
                    var page = V3PlanComputedViews.contributors(view,false,key,i,1,true);
                    assertEquals(2,page.total()); var contributor = page.items().getFirst();
                    assertEquals(snapshot.reference(old(i == 0 ? "one" : "two")),contributor.physical());
                    assertEquals(i+1,contributor.origin().elementIndex());
                    assertEquals(List.of("tone","tone"),contributor.roles().stream().map(V3PlanComputedViews.FieldLocation::field).toList());
                    assertEquals(contributor.roles().get(0),contributor.roles().get(1));
                    var pin = contributor.roles().getFirst().location().value();
                    assertEquals("alpha",xml.substring(pin.valueStart(),pin.valueEnd()));
                    assertThrows(UnsupportedOperationException.class,() -> contributor.roles().clear());
                }
                var empty = V3PlanComputedViews.contributors(view,false,key,Integer.MAX_VALUE,100,true);
                assertEquals(2,empty.total()); assertTrue(empty.items().isEmpty()); assertTrue(empty.nextOffset().isEmpty());
                assertEquals(PlanRefusal.Code.DISCLOSURE_REQUIRED,assertThrows(PlanRefusal.class,() -> V3PlanComputedViews.contributors(view,false,key,Integer.MAX_VALUE,100,false)).code());
                var foreignTuple = new ComputedGraph.Key("finishes","by-finish","alpha");
                assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,() -> V3PlanComputedViews.contributors(view,false,new ResultKey.CooccurrenceKey("self-pair",node,foreignTuple),0,100,true)).code());
                return true;
            });
        }
    }
}
