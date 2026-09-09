package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.Outcome;
import studio.environment.core.RequiredCheck;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanPorts.Draft;
import studio.environment.core.planning.TargetIntent.FieldValue;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv2.NativeDefinition.CountRule;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;

class SharedV3PlanValidationTest {
    @Test void physicalAndComputedInclusiveBoundsPreserveEveryCompleteRule() {
        var base=definition(false).definition();var logical=base.logical();
        var narrowed=new NativeDefinition.Logical(logical.entityTypes(),logical.relations(),
                List.of(new CountRule("physical-max","item",BigInteger.ZERO,BigInteger.valueOf(3)),
                        new CountRule("physical-min","item",BigInteger.valueOf(3),BigInteger.TEN)),
                logical.operationCapabilities(),logical.computedTypes(),logical.derivations(),
                List.of(new NativeDefinition.Cooccurrence("pair","by-tone","by-finish",BigInteger.ONE,BigInteger.TWO)),
                List.of(new CountRule("tone-count","tones",BigInteger.TWO,BigInteger.TWO)));
        var checked=assertInstanceOf(NativeCompilationResult.Incomplete.class,new NativeDefinitionCompiler().compile(
                new NativeDefinition(base.id(),base.revision(),narrowed,base.bindings()))).checked();
        var fixture=SharedV3PlanXmlTest.with(checked,XML);var service=fixture.service();String plan=fixture.inspected(service);
        var current=service.validateV3(fixture.lease,plan,"2");
        assertEquals(Map.of("physical-max",Outcome.UNKNOWN,"physical-min",Outcome.UNKNOWN),current.applicationRules());
        assertEquals(Outcome.UNKNOWN,outcome(current,RequiredCheck.SEMANTICS));
        assertTrue(service.materialize(fixture.lease,plan,"2").complete());
        var complete=service.validateV3(fixture.lease,plan,"2");
        assertEquals(Map.of("physical-max",Outcome.PASS,"physical-min",Outcome.PASS),complete.applicationRules());
        assertEquals(List.of("physical-max","physical-min"),List.copyOf(complete.applicationRules().keySet()));
        var rules=complete.computedRules().orElseThrow();
        assertEquals(List.of("pair","pair","tone-count"),rules.stream().map(r->r.declaration()).toList());
        assertEquals(List.of(Outcome.PASS,Outcome.PASS,Outcome.PASS),rules.stream().map(r->r.outcome()).toList());
        assertEquals(List.of(BigInteger.TWO,BigInteger.ONE,BigInteger.TWO),rules.stream().map(r->r.actual()).toList());
        assertEquals(Outcome.PASS,outcome(complete,RequiredCheck.SEMANTICS));
        assertThrows(UnsupportedOperationException.class,()->complete.checks().clear());
        assertThrows(UnsupportedOperationException.class,()->complete.applicationRules().clear());
        assertThrows(UnsupportedOperationException.class,()->rules.clear());
        assertFalse(complete.exportAvailable());
    }
    @Test void failedCurrentComputedRulesDoNotOverrideTheExistingTargetRefusal() {
        var base=definition(false).definition();var logical=base.logical();
        var narrowed=new NativeDefinition.Logical(logical.entityTypes(),logical.relations(),logical.rules(),logical.operationCapabilities(),
                logical.computedTypes(),logical.derivations(),
                List.of(new NativeDefinition.Cooccurrence("pair","by-tone","by-finish",BigInteger.ZERO,BigInteger.ONE)),logical.computedRules());
        var checked=assertInstanceOf(NativeCompilationResult.Incomplete.class,new NativeDefinitionCompiler().compile(
                new NativeDefinition(base.id(),base.revision(),narrowed,base.bindings()))).checked();
        var fixture=SharedV3PlanXmlTest.with(checked,XML);var service=fixture.service();String plan=fixture.inspected(service);
        try(var view=service.reserveView(fixture.lease,plan)) {
            view.run(()->{view.pin("2");assertEquals(List.of(Outcome.FAIL,Outcome.PASS),
                    studio.environment.server.plan.V3PlanComputedViews.rules(view,false,0,100).items().stream().map(r->r.outcome()).toList());return true;});
        }
        var materialized=service.materialize(fixture.lease,plan,"2");assertFalse(materialized.complete());
        assertEquals(List.of("DERIVED_RULE_FAILED"),materialized.diagnostics());
        var result=service.validateV3(fixture.lease,plan,"2");assertFalse(result.targetComplete());
        assertEquals(Outcome.UNKNOWN,outcome(result,RequiredCheck.SEMANTICS));assertTrue(result.computedRules().isEmpty());
    }
    @Test void completeEmptyRulesOptionalAbsenceAndUnresolvedTargetAreDifferentStates() {
        var fixture=SharedV3PlanXmlTest.with(definition(false),"<items><item id='one'/></items>");
        var service=fixture.service();String plan=fixture.inspected(service);
        var current=service.validateV3(fixture.lease,plan,"2");assertTrue(current.computedRules().isEmpty());
        assertTrue(service.materialize(fixture.lease,plan,"2").complete());
        var complete=service.validateV3(fixture.lease,plan,"2");assertTrue(complete.targetComplete());
        assertEquals(List.of(),complete.computedRules().orElseThrow());assertEquals(Outcome.PASS,outcome(complete,RequiredCheck.SEMANTICS));
        assertNotEquals(current.inputFingerprint(),complete.inputFingerprint());
        service.replaceDraft(fixture.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),
                new Draft(intent(edit("one",new FieldValue.KeepObserved(),new FieldValue.Unresolved())),List.of()));
        var unknown=service.validateV3(fixture.lease,plan,"3");assertFalse(unknown.targetComplete());
        assertEquals(Outcome.UNKNOWN,outcome(unknown,RequiredCheck.VALUES));assertEquals(Outcome.UNKNOWN,outcome(unknown,RequiredCheck.SEMANTICS));
        assertNotEquals(current.inputFingerprint(),unknown.inputFingerprint());
    }
    @Test void explicitValueChangesRecomputeValidationFromActualFinalXml() {
        var fixture=new SharedV3PlanXmlTest();var service=fixture.service();String plan=fixture.inspected(service);
        service.materialize(fixture.lease,plan,"2");var original=service.validateV3(fixture.lease,plan,"2");
        service.replaceDraft(fixture.lease,plan,new HostedPlanService.Mutation("2",UUID.randomUUID().toString()),
                new Draft(intent(edit("one",new FieldValue.KeepObserved(),new FieldValue.Entered("beta"))),List.of()));
        var changed=service.validateV3(fixture.lease,plan,"3");
        assertEquals(List.of(BigInteger.ONE,BigInteger.ONE),changed.computedRules().orElseThrow().stream().map(r->r.actual()).toList());
        assertNotEquals(original.inputFingerprint(),changed.inputFingerprint());
        assertEquals(changed,service.validateV3(fixture.lease,plan,"3"));
    }
    private static Outcome outcome(HostedPlanService.V3Validation result,RequiredCheck check) {
        return result.checks().stream().filter(c->c.check()==check).findFirst().orElseThrow().outcome();
    }
    @Test void currentOnlyAndActuallyMaterializedTargetHaveDistinctFreshValidation() {
        var fixture=new SharedV3PlanXmlTest();var lookups=new AtomicInteger();
        fixture.definitionLookup=definition->{lookups.incrementAndGet();return definition;};
        var service=fixture.service();String plan=fixture.inspected(service);assertEquals(1,lookups.get());
        var current=assertDoesNotThrow(()->service.validateV3(fixture.lease,plan,"2"));
        assertEquals(2,lookups.get());assertFalse(current.targetComplete());assertTrue(current.computedRules().isEmpty());
        assertEquals(Arrays.asList(RequiredCheck.values()),current.checks().stream().map(c->c.check()).toList());
        var currentPass=EnumSet.of(RequiredCheck.SCOPE,RequiredCheck.DEFINITION,RequiredCheck.DESTINATION);
        for(var check:current.checks()){
            assertEquals(currentPass.contains(check.check())?Outcome.PASS:Outcome.UNKNOWN,check.outcome());
            assertEquals(current.inputFingerprint(),check.inputFingerprint());
        }
        assertFalse(current.exportAvailable());assertTrue(current.applicationRules().isEmpty());
        assertTrue(service.materialize(fixture.lease,plan,"2").complete());
        var complete=assertDoesNotThrow(()->service.validateV3(fixture.lease,plan,"2"));
        assertEquals(3,lookups.get());assertTrue(complete.targetComplete());assertFalse(complete.exportAvailable());
        assertNotEquals(current.inputFingerprint(),complete.inputFingerprint());
        var unsupported=EnumSet.of(RequiredCheck.CLIENT_CAPABILITY,RequiredCheck.CONTENT_POLICY,RequiredCheck.REVIEW);
        for(var check:complete.checks()){
            assertEquals(unsupported.contains(check.check())?Outcome.UNKNOWN:Outcome.PASS,check.outcome());
            assertEquals(complete.inputFingerprint(),check.inputFingerprint());
        }
        var rules=complete.computedRules().orElseThrow();assertEquals(2,rules.size());
        assertEquals(List.of("alpha","beta"),rules.stream().map(r->r.source().orElseThrow().value()).toList());
        assertEquals(List.of(java.math.BigInteger.TWO,java.math.BigInteger.ONE),rules.stream().map(r->r.actual()).toList());
        assertEquals(complete,service.validateV3(fixture.lease,plan,"2"));assertEquals(4,lookups.get());
        try(var admission=service.reserveView(fixture.lease,plan)){
            admission.run(()->{admission.pin("2");assertEquals(complete,admission.validationV3());return true;});
        }
        assertEquals(5,lookups.get());assertEquals("PlanValidationV3[redacted]",complete.toString());
        assertEquals(PlanRefusal.Code.EXPORT_UNAVAILABLE,assertThrows(PlanRefusal.class,()->service.requestExport(fixture.lease,plan,"2")).code());
    }
}
