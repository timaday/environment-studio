package studio.environment.core.plan;

import java.util.HashSet;
import java.util.TreeMap;
import studio.environment.core.CheckResult;
import studio.environment.core.Outcome;
import studio.environment.core.RequiredCheck;
import studio.environment.core.observation.ObservationPort.Cancellation;

/** Whole-document permission for the one explicit protected self-contained artifact intent. */
final class PlanReviewV3 {
    private PlanReviewV3() { }
    static Outcome contentPolicy(HostedPlanService.ViewSnapshot snapshot,Cancellation control) {
        var binding=snapshot.definition().model().bindings().stream().filter(b->b.id().equals(snapshot.binding())).findFirst()
                .orElseThrow(()->new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED));
        var documents=new HashSet<String>();
        for(var document:binding.documents()) { live(control);if(!documents.add(document.id()))throw refused(); }
        for(boolean target:new boolean[]{false,true}) {
            var actual=new HashSet<String>();
            for(var source:snapshot.selected(target).sources()) { live(control);if(!actual.add(source.documentId()))throw refused(); }
            if(!actual.equals(documents))throw refused();
        }
        var policies=new TreeMap<String,String>();
        boolean malformed=false,denied=false;
        for(var policy:snapshot.definition().policies()) {
            live(control);
            if(!policy.bindingId().equals(snapshot.binding()))continue;
            if(!documents.contains(policy.documentId()) || policies.putIfAbsent(policy.documentId(),policy.content())!=null)malformed=true;
            denied|=policy.content().equals("deny");
        }
        live(control);
        return malformed?Outcome.ERROR:denied?Outcome.FAIL:policies.size()!=documents.size()?Outcome.UNKNOWN:Outcome.PASS;
    }
    static HostedPlanService.V3Validation reviewed(HostedPlanService.V3Validation validation,Outcome policy) {
        var checks=validation.checks().stream().map(check->new CheckResult(check.check(),
                check.check()==RequiredCheck.REVIEW?Outcome.PASS:check.check()==RequiredCheck.CONTENT_POLICY?policy:check.outcome(),check.inputFingerprint())).toList();
        return new HostedPlanService.V3Validation(validation.inputFingerprint(),checks,validation.applicationRules(),validation.computedRules());
    }
    private static PlanRefusal refused(){return new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);}
    private static void live(Cancellation control){if(control.cancelled())throw new PlanRefusal(PlanRefusal.Code.CANCELLED);}
}
