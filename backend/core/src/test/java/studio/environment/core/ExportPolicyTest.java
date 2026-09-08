package studio.environment.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportPolicyTest {
    private static final String REVISION = "a".repeat(64);

    private List<CheckResult> passingEvidence() {
        return Arrays.stream(RequiredCheck.values())
                .map(check -> new CheckResult(check, Outcome.PASS, REVISION)).toList();
    }

    @Test void completeCurrentPassingEvidenceIsEligible() {
        assertTrue(new ExportPolicy().evaluate(REVISION, passingEvidence()).eligible());
    }

    @Test void noEvidenceIsNeverVacuouslyValid() {
        var decision = new ExportPolicy().evaluate(REVISION, List.of());
        assertFalse(decision.eligible());
        assertEquals(RequiredCheck.values().length, decision.blockers().size());
    }

    @Test void eachMissingRequiredCategoryBlocks() {
        for (RequiredCheck missing : RequiredCheck.values()) {
            var evidence = passingEvidence().stream().filter(e -> e.check() != missing).toList();
            assertFalse(new ExportPolicy().evaluate(REVISION, evidence).eligible(), missing.name());
        }
    }

    @Test void everyNonPassOutcomeBlocksEveryRequiredCategory() {
        for (RequiredCheck check : RequiredCheck.values()) {
            for (Outcome outcome : Outcome.values()) {
                if (outcome == Outcome.PASS) continue;
                var evidence = new ArrayList<>(passingEvidence());
                evidence.set(check.ordinal(), new CheckResult(check, outcome, REVISION));
                assertFalse(new ExportPolicy().evaluate(REVISION, evidence).eligible(), check + ":" + outcome);
            }
        }
    }

    @Test void staleEvidenceAndDuplicateEvidenceBlock() {
        var evidence = new ArrayList<>(passingEvidence());
        evidence.set(0, new CheckResult(RequiredCheck.SCOPE, Outcome.PASS, "b".repeat(64)));
        assertFalse(new ExportPolicy().evaluate(REVISION, evidence).eligible());
        evidence = new ArrayList<>(passingEvidence());
        evidence.add(evidence.get(0));
        assertFalse(new ExportPolicy().evaluate(REVISION, evidence).eligible());
    }

    @Test void inputOrderDoesNotChangeDecisionAndResultsAreImmutable() {
        var evidence = new ArrayList<>(passingEvidence());
        evidence.remove(0);
        var first = new ExportPolicy().evaluate(REVISION, evidence);
        java.util.Collections.reverse(evidence);
        assertEquals(first, new ExportPolicy().evaluate(REVISION, evidence));
        assertThrows(UnsupportedOperationException.class, () -> first.blockers().clear());
    }

    @Test void malformedRevisionIsRejectedAtBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new ExportPolicy().evaluate("", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CheckResult(RequiredCheck.SCOPE, Outcome.PASS, "bad"));
    }
}
