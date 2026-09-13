package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;
import static studio.environment.server.planning.V3PlanContentOperationsTest.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.environment.core.Outcome;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent;

/** Independent mock witness: a valid repair must not legitimize forged original rule evidence. */
class IndependentV3PlanContentTest {
    @Test void repairedTargetDoesNotAuthorizeTamperedObservedRuleOutcome() {
        var definition = definition(false).definition();
        var logical = definition.logical();
        var bounded = new NativeDefinition.Logical(logical.entityTypes(), logical.relations(),
                logical.rules(), logical.operationCapabilities(), logical.computedTypes(),
                logical.derivations(), List.of(new NativeDefinition.Cooccurrence(
                        "pair", "by-tone", "by-finish", BigInteger.ZERO, BigInteger.ONE)),
                logical.computedRules());
        var model = new PlanDefinition.V3(compile(new NativeDefinition(definition.id(),
                definition.revision(), bounded, definition.bindings())));
        var original = project(model, Map.of("sheet", XML));
        var evidence = (PlanContentEvidence.V3Observed) original.evidence();
        assertEquals(1, evidence.derived().rules().stream()
                .filter(rule -> rule.outcome() == Outcome.FAIL).count());
        var repair = new PlanPorts.Draft(intent(
                new TargetIntent.EntityDecision.Remove(old("two"))), List.of());
        var repaired = assertInstanceOf(V3PlanContent.Result.Complete.class,
                target(model, original, repair)).content();
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/>"
                + "<item id='three' tone='beta' finish='x'/></items>",
                repaired.sources().getFirst().xml());

        var forgedRules = evidence.derived().rules().stream().map(rule ->
                new DerivedResult.RuleCheck(rule.kind(), rule.declaration(), rule.source(),
                        rule.actual(), rule.minimum(), rule.maximum(), Outcome.PASS)).toList();
        var forged = new PlanPorts.Content(original.sources(), original.graph(), original.provenance(),
                new PlanContentEvidence.V3Observed(evidence.observationFingerprint(), evidence.input(),
                        new DerivedResult.Complete(evidence.derived().graph(), forgedRules)));
        assertEquals(new V3PlanContent.Result.Refused("STALE_CONTENT"),
                target(model, forged, repair));
        assertEquals(XML, original.sources().getFirst().xml());
    }
}
