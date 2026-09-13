package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;

/** Projection boundary controls; these synthetic DTOs are not public target qualification. */
class V3PlanWorkflowEncodingTest {
    @Test void nullAndCompleteEmptyRemainDifferentAndFingerprintPrecedesTargetPaging(){
        var missing=new HostedPlanService.V3Validation("a".repeat(64),List.of(),Map.of(),Optional.empty());
        var empty=new HostedPlanService.V3Validation("b".repeat(64),List.of(),Map.of(),Optional.of(List.of()));
        var summary=new V3PlanWorkflowReader.Validation.Summary("2");
        var absent=V3PlanWorkflowEncoding.validation(summary,missing,()->{});assertNull(absent.get("computedRuleCount"));assertEquals(false,absent.get("targetComplete"));assertFalse(absent.containsKey("computedRules"));
        var present=V3PlanWorkflowEncoding.validation(summary,empty,()->{});assertEquals(0,present.get("computedRuleCount"));assertEquals(true,present.get("targetComplete"));
        var wrong=new V3PlanWorkflowReader.Validation.Page("2","b".repeat(64),0,1);
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->V3PlanWorkflowEncoding.validation(wrong,missing,()->{})).code());
        var same=new V3PlanWorkflowReader.Validation.Page("2","a".repeat(64),0,1);
        assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET,assertThrows(PlanRefusal.class,()->V3PlanWorkflowEncoding.validation(same,missing,()->{})).code());
        var page=V3PlanWorkflowEncoding.validation(new V3PlanWorkflowReader.Validation.Page("2","b".repeat(64),Integer.MAX_VALUE,100),empty,()->{});
        assertEquals(Integer.MAX_VALUE,page.get("offset"));assertEquals(0,page.get("total"));assertNull(page.get("nextOffset"));assertEquals(List.of(),page.get("items"));
    }
    @Test void selectedRowsRemainLazyAndCancellationCanRefuseEncoding(){
        var rule=new studio.environment.core.derived.DerivedResult.RuleCheck(studio.environment.core.derived.DerivedResult.RuleKind.ENTITY_COUNT,"mock-rule",Optional.empty(),java.math.BigInteger.ONE,java.math.BigInteger.ZERO,java.math.BigInteger.TEN,studio.environment.core.Outcome.PASS);
        var value=new HostedPlanService.V3Validation("a".repeat(64),List.of(),Map.of(),Optional.of(Collections.nCopies(60001,rule)));
        var checks=new AtomicInteger();Runnable verify=()->{if(checks.incrementAndGet()>4)throw new PlanRefusal(PlanRefusal.Code.CONFLICT);};
        var page=V3PlanWorkflowEncoding.validation(new V3PlanWorkflowReader.Validation.Page("2","a".repeat(64),60000,1),value,verify);
        assertEquals(60001,page.get("total"));assertNull(page.get("nextOffset"));
        try(var encoder=new PlanViewEncoding(134217728)){assertThrows(RuntimeException.class,()->encoder.encode(page));}
    }
    @Test void legacyPreviewCannotLoseItsVersionAndStillBecomeAValidV3Reply(){
        var physical=new studio.environment.core.profile.ProfileComposer.Preview("mock-profile",java.math.BigInteger.ONE,"c".repeat(64),"d".repeat(64),List.of(),List.of(),List.of(),List.of(),List.of());
        var reference=new studio.environment.core.workspace.NativeCommand.Reference(UUID.randomUUID().toString(),"1");
        var legacy=new HostedPlanService.CompositionPreview(UUID.randomUUID().toString(),"2","o",reference,"p",List.of(),"r","c",physical);
        var request=new PlanViewRequest.Preview("2",reference,true,List.of(),PlanViewRequest.Section.INCLUDED,50000,100);
        assertEquals(PlanRefusal.Code.STALE_PREVIEW,assertThrows(PlanRefusal.class,()->V3PlanWorkflowEncoding.preview(legacy,request,()->{})).code());
        var versioned=new HostedPlanService.CompositionPreview(legacy.planId(),"2","o",reference,"p",List.of(),"r","c",physical,Optional.of(new studio.environment.core.profile.V3ProfileComposer.Preview(physical,List.of("by-key"))));
        var page=V3PlanWorkflowEncoding.preview(versioned,request,()->{});assertEquals(List.of("by-key"),page.get("affectedDerivations"));assertEquals(HostedPlanService.compositionPreviewDigest(versioned),page.get("previewDigest"));assertNotEquals(HostedPlanService.compositionPreviewDigest(legacy),page.get("previewDigest"));
    }

}
