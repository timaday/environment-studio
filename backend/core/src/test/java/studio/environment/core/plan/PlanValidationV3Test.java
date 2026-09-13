package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.environment.core.Outcome;
import studio.environment.core.derived.ComputedGraph;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.observation.ObservationPort.Cancellation;

class PlanValidationV3Test {
    @Test void legacyV2ValidationKeepsItsIndependentLiteralByteFrame() throws Exception {
        var fixture=new PlanLifecycleTest.Harness();assertEquals(HostedPlanService.Phase.SUCCEEDED,fixture.inspect().phase());
        String plan=fixture.created.planId();assertEquals(36,plan.length());
        // Independently framed contract bytes; only the service-generated UUID is substituted.
        String bytes="ES-PLAN-INPUT-1\0O13:S13:bindingDigestS14:binding-digestS9:bindingIdS16:invented-binding"
                +"S7:currentA1:O2:S10:documentIdS6:sampleS12:sourceDigestS13:source-digest"
                +"S21:definitionPublicationS18:publication-digestS13:destinationIdS11:destinationS16:documentPoliciesA0:"
                +"S5:draftO3:S11:containmentA0:S8:entitiesA0:S10:placementsA0:"
                +"S22:observationFingerprintS64:"+"a".repeat(64)+"S6:planIdS36:"+plan
                +"S19:profilePublicationsA0:S8:revisionS1:2S6:targetA1:O2:S10:documentIdS6:sampleS12:sourceDigestS13:source-digest"
                +"S8:versionsO4:S8:compilerS18:native-compiler-v2S6:parserS41:woodstox-7.2.2-xml10-fifth-edition-patch1"
                +"S5:rulesS16:generic-graph-v1S6:writerS20:structural-target-v1";
        String expected=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var result=fixture.service.validate(fixture.base.lease,plan,"2");assertEquals(expected,result.inputFingerprint());
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,
                ()->fixture.service.validateV3(fixture.base.lease,plan,"2")).code());
        assertEquals(result,fixture.service.validate(fixture.base.lease,plan,"2"));assertFalse(result.exportAvailable());
    }
    @Test void literalWholeInputOraclePinsTheSeparateV3Encoding() {
        // Closed input-encoding witness only; these constructed records confer no XML or publication authority.
        var old=new HostedPlanServiceTest().definition;
        var physical=old.compiled().checked().definition();
        var logical=new studio.environment.core.definitionv3.NativeDefinition.Logical(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var definition=new studio.environment.core.definitionv3.NativeDefinition("invented",BigInteger.ONE,logical,physical.bindings());
        var checked=new studio.environment.core.definitionv3.NativeCompilationResult.Checked(definition,"logical",Map.of("invented-binding","binding-digest"),Map.of("mock-mechanism",BigInteger.TWO));
        var published=new PlanPorts.PublishedDefinition(old.reference(),"mock-publication",new PlanDefinition.V3(checked),
                List.of(new studio.environment.core.workspace.NativeCommand.Policy("invented-binding","sample","deny")));
        var current=new PlanPorts.Content(List.of(new PlanPorts.Source("sample","<mock/>","source-digest")),
                new studio.environment.core.graph.ObservedGraph(List.of(),List.of()),Map.of());
        var original=new studio.environment.core.derived.DerivedInput.Pin("observed","logical","invented-binding","binding-digest",Map.of("sample","source-digest"));
        var decisions=new studio.environment.core.derived.DerivedInput.Pin("decision","logical","invented-binding","binding-digest",Map.of("sample","source-digest"));
        var snapshot=new HostedPlanService.ViewSnapshot("7",published,"invented-binding",Optional.of(current),Optional.empty(),PlanPorts.Draft.empty(),
                Map.of(),Map.of(),Optional.of(new V3PlanPins(original,decisions)));
        var context=new PlanValidationV3.Context("mock-plan","mock-destination",List.of("profile-z","profile-a"));
        var result=PlanValidationV3.evaluate(context,snapshot,new Cancellation());
        // Independently framed Python hashlib oracle from the contract's entire field map.
        assertEquals("baae12671d2727786c086da7d52dc401a34c615a86e492a6871239fe1ac12963",result.inputFingerprint());
        assertFalse(result.targetComplete());assertFalse(result.exportAvailable());
        assertEquals(result,PlanValidationV3.evaluate(new PlanValidationV3.Context("mock-plan","mock-destination",List.of("profile-a","profile-z")),snapshot,new Cancellation()));
    }
    @Test void literalTypedFrameOraclePreservesAllRuleFieldsAndUtf8Length() {
        // Independently framed with Python hashlib from the documented map, not Java output.
        var rules=List.of(new DerivedResult.RuleCheck(DerivedResult.RuleKind.ENTITY_COUNT,"mock-count",Optional.empty(),
                        BigInteger.TWO,BigInteger.ONE,BigInteger.TWO,Outcome.PASS),
                new DerivedResult.RuleCheck(DerivedResult.RuleKind.COOCCURRENCE,"mock-pair",Optional.of(new ComputedGraph.Key("t","d","é𐀀")),
                        BigInteger.valueOf(3),BigInteger.ZERO,BigInteger.TWO,Outcome.FAIL));
        assertEquals("afddb09160c169f760425ee3ca93f6c0a43fafd5e4efb12e2b04f2060141bfdb",
                PlanValidationV3.rulesDigest(rules,new Cancellation()));
        assertEquals("fa0f16811fd22bdbd481549f61f5d03abf139fb6fc7ce94ecb3e137833c15f57",
                PlanValidationV3.rulesDigest(List.of(),new Cancellation()));
        assertNotEquals(PlanValidationV3.rulesDigest(rules,new Cancellation()),PlanValidationV3.rulesDigest(rules.reversed(),new Cancellation()));
    }
    @Test void cancellationDuringLazyRuleTraversalCannotReturnADigest() {
        var control=new Cancellation();
        var rule=new DerivedResult.RuleCheck(DerivedResult.RuleKind.ENTITY_COUNT,"mock-count",Optional.empty(),BigInteger.ZERO,BigInteger.ZERO,BigInteger.ONE,Outcome.PASS);
        var rules=new AbstractList<DerivedResult.RuleCheck>() {
            @Override public int size(){return 2;}
            @Override public DerivedResult.RuleCheck get(int index){if(index==0)control.cancel();return rule;}
        };
        assertEquals(PlanRefusal.Code.CANCELLED,assertThrows(PlanRefusal.class,()->PlanValidationV3.rulesDigest(rules,control)).code());
    }
}
