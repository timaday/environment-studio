package studio.environment.core.plan;

import java.math.BigInteger;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import studio.environment.core.CheckResult;
import studio.environment.core.Outcome;
import studio.environment.core.RequiredCheck;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.workspace.NativeWorkspaceDigests;

/** Pure evaluation after the shared owner verifies fresh publication and complete XML proofs. */
final class PlanValidationV3 {
    private PlanValidationV3() { }
    record Context(String planId,String destinationId,List<String> profilePublications) {
        Context { profilePublications=profilePublications.stream().sorted().toList(); }
        @Override public String toString(){return "ValidationContextV3[redacted]";}
    }
    static HostedPlanService.V3Validation evaluate(Context context,HostedPlanService.ViewSnapshot snapshot,Cancellation control) {
        live(control);
        if(!(snapshot.definition().model() instanceof PlanDefinition.V3 model))throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        var pins=snapshot.v3Pins().orElseThrow(PlanValidationV3::refused);
        boolean complete=snapshot.target().isPresent();
        Optional<List<DerivedResult.RuleCheck>> computed=Optional.empty();
        var counts=new TreeMap<String,BigInteger>();
        if(complete) {
            var target=snapshot.selected(true);
            if(!(target.evidence() instanceof PlanContentEvidence.V3Target proof))throw refused();
            computed=Optional.of(proof.derived().rules());
            for(var entity:target.graph().entities()) {
                live(control);counts.merge(entity.key().type(),BigInteger.ONE,BigInteger::add);
            }
        }
        var physical=new TreeMap<String,Outcome>();
        for(var rule:model.physical().rules()) {
            live(control);var actual=counts.getOrDefault(rule.type(),BigInteger.ZERO);
            var outcome=!complete?Outcome.UNKNOWN:actual.compareTo(rule.minimum())>=0&&actual.compareTo(rule.maximum())<=0?Outcome.PASS:Outcome.FAIL;
            if(physical.putIfAbsent(rule.id(),outcome)!=null)throw refused();
        }
        String computedDigest=computed.map(rules->rulesDigest(rules,control)).orElse("");
        var fields=new TreeMap<String,Object>();
        fields.put("planId",context.planId());fields.put("revision",snapshot.revision());
        fields.put("definitionPublication",snapshot.definition().publicationDigest());
        fields.put("definitionReference",NativeWorkspaceDigests.reference(snapshot.definition().reference()));
        fields.put("profilePublications",context.profilePublications());
        fields.put("bindingId",snapshot.binding());fields.put("bindingDigest",model.bindingDigests().get(snapshot.binding()));
        fields.put("logicalDigest",model.logicalDigest());fields.put("destinationId",context.destinationId());
        fields.put("originalPin",pin(pins.original()));fields.put("decisionPin",pin(pins.decisions()));
        live(control);fields.put("draft",DraftEncoding.encode(snapshot.draft()));live(control);
        fields.put("current",sources(snapshot.selected(false)));fields.put("target",snapshot.target().map(PlanValidationV3::sources).orElse(List.of()));
        fields.put("targetComplete",complete);fields.put("documentPolicies",NativeWorkspaceDigests.policies(snapshot.definition().policies()));
        var mechanisms=new TreeMap<String,String>();model.checked().mechanisms().forEach((key,value)->mechanisms.put(key,value.toString()));
        fields.put("mechanisms",mechanisms);
        fields.put("versions",Map.of("compiler","native-compiler-v3","parser","woodstox-7.2.2-xml10-fifth-edition-patch1",
                "writer","structural-target-v1","rules","generic-graph-v1","derived","derived-graph-v1"));
        var encodedPhysical=new TreeMap<String,String>();physical.forEach((key,value)->encodedPhysical.put(key,value.name()));
        fields.put("physicalRules",encodedPhysical);fields.put("computedRulesDigest",computedDigest);
        live(control);String fingerprint=NativeWorkspaceDigests.hash("ES-PLAN-INPUT-3",fields);live(control);
        boolean failed=physical.containsValue(Outcome.FAIL);
        if(computed.isPresent())for(var rule:computed.orElseThrow()){live(control);failed|=rule.outcome()==Outcome.FAIL;}
        var checks=new ArrayList<CheckResult>();
        for(var category:RequiredCheck.values()) {
            var outcome=switch(category) {
                case SCOPE,DEFINITION,DESTINATION -> Outcome.PASS;
                case CLIENT_CAPABILITY,CONTENT_POLICY,REVIEW -> Outcome.UNKNOWN;
                case SEMANTICS -> !complete?Outcome.UNKNOWN:failed?Outcome.FAIL:Outcome.PASS;
                default -> complete?Outcome.PASS:Outcome.UNKNOWN;
            };
            checks.add(new CheckResult(category,outcome,fingerprint));
        }
        live(control);return new HostedPlanService.V3Validation(fingerprint,checks,physical,computed);
    }
    private static Map<String,Object> pin(DerivedInput.Pin pin) {
        return Map.of("revisionToken",pin.revisionToken(),"logicalDigest",pin.logicalDigest(),"bindingId",pin.bindingId(),
                "bindingDigest",pin.bindingDigest(),"documentDigests",pin.documentDigests());
    }
    private static List<Map<String,String>> sources(PlanPorts.Content content) {
        return content.sources().stream().sorted(Comparator.comparing(PlanPorts.Source::documentId))
                .map(source->Map.of("documentId",source.documentId(),"sourceDigest",source.digest())).toList();
    }
    static String rulesDigest(List<DerivedResult.RuleCheck> rules,Cancellation control) {
        live(control);
        // The framer walks one map at a time; do not retain repeated source-key text encodings.
        var values=new AbstractList<Map<String,Object>>() {
            @Override public int size(){return rules.size();}
            @Override public Map<String,Object> get(int index) {
                live(control);var rule=rules.get(index);
                var source=rule.source().map(key->List.of(Map.of("computedType",key.computedType(),"derivation",key.derivation(),"value",key.value()))).orElse(List.of());
                return Map.of("kind",rule.kind().name(),"declaration",rule.declaration(),"source",source,
                        "actual",rule.actual().toString(),"minimum",rule.minimum().toString(),"maximum",rule.maximum().toString(),"outcome",rule.outcome().name());
            }
        };
        String result=NativeWorkspaceDigests.hash("ES-PLAN-DERIVED-RULES-3",values);live(control);return result;
    }
    private static void live(Cancellation control){if(control.cancelled())throw new PlanRefusal(PlanRefusal.Code.CANCELLED);}
    private static PlanRefusal refused(){return new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);}
}
