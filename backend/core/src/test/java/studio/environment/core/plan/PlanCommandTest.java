package studio.environment.core.plan;

import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.planning.TargetIntent;
import static org.junit.jupiter.api.Assertions.*;

class PlanCommandTest {
    @Test void exactWireReplaySurvivesDiscardButDoesNotRestoreRetiredPlan() {
        var h=new PlanLifecycleTest.Harness(); h.inspect();
        var replacement=new PlanCommand(h.mutation(),new PlanCommand.Action.Replace(new PlanCommand.Draft(List.of(),List.of(),List.of())));
        var ack=h.service.command(h.base.lease,h.created.planId(),replacement);
        assertEquals("3",ack.revision());
        h.service.command(h.base.lease,h.created.planId(),new PlanCommand(h.mutation(),new PlanCommand.Action.Discard()));
        assertEquals(ack,h.service.command(h.base.lease,h.created.planId(),replacement));
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->h.service.command(h.base.lease,h.created.planId(),new PlanCommand(replacement.mutation(),new PlanCommand.Action.Discard()))).code());
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->h.service.summary(h.base.lease,h.created.planId())).code());
    }
    @Test void closingAdmissionDuringMaterializationRetainsScratchUntilWorkerReturns() throws Exception {
        var h=new PlanLifecycleTest.Harness(); h.inspect();
        h.renderEntered=new java.util.concurrent.CountDownLatch(1);
        h.renderRelease=new java.util.concurrent.CountDownLatch(1);
        var command=new PlanCommand(h.mutation(),new PlanCommand.Action.Replace(new PlanCommand.Draft(List.of(),List.of(),List.of())));
        var admission=h.service.reserveCommand(h.base.lease,h.created.planId());
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var pending=executor.submit(()->admission.execute(command));
            try {
                PlanLifecycleTest.await(h.renderEntered); admission.close();
                assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->h.service.reserveCommand(h.base.lease,h.created.planId())).code());
            } finally { h.renderRelease.countDown(); }
            pending.get(5,java.util.concurrent.TimeUnit.SECONDS);
        }
        try(var next=h.service.reserveCommand(h.base.lease,h.created.planId())) { assertTrue(next.live()); }
        admission.close();
    }
    @Test void batchPreservesUnmentionedChoicesAndRejectsDuplicatesAtomically() {
        var a=new PlanCommand.Ref.Fresh("first","sample"); var b=new PlanCommand.Ref.Fresh("second","sample");
        var original=new PlanPorts.Draft(new TargetIntent(List.of(new TargetIntent.EntityDecision.Create(new TargetIntent.Ref.Fresh("first","sample"),Map.of("value",new TargetIntent.FieldValue.Entered("Keep-MiXeD")),Map.of())),List.of()),List.of());
        var change=new PlanCommand.Change(new PlanCommand.Entity.Create(b,Map.of("value",new TargetIntent.FieldValue.Entered("New-Value")),Map.of()),List.of());
        var mutation=new HostedPlanService.Mutation("2",UUID.randomUUID().toString());
        var command=new PlanCommand(mutation,new PlanCommand.Action.Batch(List.of(change),List.of()));
        java.util.function.Function<PlanCommand.Ref,TargetIntent.Ref> resolve=ref->{var fresh=(PlanCommand.Ref.Fresh)ref; return new TargetIntent.Ref.Fresh(fresh.slotId(),fresh.typeId());};
        var result=command.apply(original,resolve);
        assertEquals(original.intent().entities().getFirst(),result.intent().entities().getFirst());
        assertEquals(2,result.intent().entities().size());
        assertThrows(PlanRefusal.class,()->new PlanCommand(mutation,new PlanCommand.Action.Batch(List.of(change,change),List.of())).apply(original,resolve));
        assertEquals(1,original.intent().entities().size());
    }
    @Test void incrementalEditsPreserveUnmentionedStateAndNeverSelectImplicitly() {
        var ref=new PlanCommand.Ref.Fresh("first","sample");
        var domain=new TargetIntent.Ref.Fresh("first","sample");
        var sibling=new TargetIntent.Ref.Fresh("second","sample");
        java.util.function.Function<PlanCommand.Ref,TargetIntent.Ref> resolve=r->{var f=(PlanCommand.Ref.Fresh)r; return new TargetIntent.Ref.Fresh(f.slotId(),f.typeId());};
        var initial=new PlanPorts.Draft(new TargetIntent(List.of(
            new TargetIntent.EntityDecision.Create(domain,Map.of("value",new TargetIntent.FieldValue.Unresolved(),"other",new TargetIntent.FieldValue.Entered("Preserve")),Map.of("link",new TargetIntent.ReferenceValue.Unresolved())),
            new TargetIntent.EntityDecision.Create(sibling,Map.of("value",new TargetIntent.FieldValue.Entered("Sibling")),Map.of())),List.of()),List.of());
        var mutation=new HostedPlanService.Mutation("2",UUID.randomUUID().toString());
        var bound=new PlanCommand(mutation,new PlanCommand.Action.BindField(ref,"value",new TargetIntent.FieldValue.Entered("Exact-MiXeD"))).apply(initial,resolve);
        var first=(TargetIntent.EntityDecision.Create)bound.intent().entities().getFirst();
        assertEquals(new TargetIntent.FieldValue.Entered("Exact-MiXeD"),first.fields().get("value"));
        assertEquals(new TargetIntent.FieldValue.Entered("Preserve"),first.fields().get("other"));
        assertEquals(initial.intent().entities().get(1),bound.intent().entities().get(1));
        var linked=new PlanCommand(mutation,new PlanCommand.Action.BindReference(ref,"link",new PlanCommand.ReferenceState.To(new PlanCommand.Ref.Fresh("second","sample")))).apply(bound,resolve);
        assertEquals(new TargetIntent.ReferenceValue.To(sibling),((TargetIntent.EntityDecision.Create)linked.intent().entities().getFirst()).references().get("link"));
        assertThrows(PlanRefusal.class,()->new PlanCommand(mutation,new PlanCommand.Action.BindField(ref,"unknown",new TargetIntent.FieldValue.Unresolved())).apply(initial,resolve));
        assertThrows(PlanRefusal.class,()->new PlanCommand(mutation,new PlanCommand.Action.BindField(new PlanCommand.Ref.Fresh("unselected","sample"),"value",new TargetIntent.FieldValue.Unresolved())).apply(initial,resolve));
        var forgotten=new PlanCommand(mutation,new PlanCommand.Action.Forget(ref)).apply(linked,resolve);
        assertEquals(List.of(initial.intent().entities().get(1)),forgotten.intent().entities());
        assertInstanceOf(TargetIntent.FieldValue.Unresolved.class,((TargetIntent.EntityDecision.Create)initial.intent().entities().getFirst()).fields().get("value"));
    }
    @Test void independentPythonWireMacVectorPreservesUnicodeAndDecimalRevision() {
        byte[] key=new byte[32]; for(int i=0;i<key.length;i++) key[i]=(byte)i;
        var command=new PlanCommand(new HostedPlanService.Mutation("9007199254740993","00000000-0000-4000-8000-000000000001"),
            new PlanCommand.Action.BindField(new PlanCommand.Ref.Fresh("one","sample"),"tone",new TargetIntent.FieldValue.Entered("MiXeD 𐀀\tValue")));
        assertEquals("4b9c9dd77f26175af81ff9458452d4f950f55ceae7caa76bc2f443292325b852",new CommandIdentity(key).digest("lease","plan",command.encoding()));
    }
}
