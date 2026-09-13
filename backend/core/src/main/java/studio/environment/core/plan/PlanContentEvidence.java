package studio.environment.core.plan;

import java.util.Objects;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.planning.ExpectedTarget;

/** Internal complete evidence values. Constructors confer no source or plan authority. */
public sealed interface PlanContentEvidence {
    record V2() implements PlanContentEvidence { }
    sealed interface V3 extends PlanContentEvidence {
        String observationFingerprint();
        DerivedInput input();
        DerivedResult.Complete derived();
    }
    record V3Observed(String observationFingerprint, DerivedInput input,
            DerivedResult.Complete derived) implements V3 {
        public V3Observed { Objects.requireNonNull(observationFingerprint); Objects.requireNonNull(input); Objects.requireNonNull(derived); }
        @Override public String toString() { return "ObservedPlanEvidenceV3[redacted]"; }
    }
    record V3Target(String observationFingerprint, DerivedInput.Pin originalPin,
            ExpectedTarget physicalExpected, DerivedInput preliminary, DerivedResult.Complete preliminaryDerived,
            DerivedInput input, DerivedResult.Complete derived) implements V3 {
        public V3Target {
            Objects.requireNonNull(observationFingerprint); Objects.requireNonNull(originalPin); Objects.requireNonNull(physicalExpected);
            Objects.requireNonNull(preliminary); Objects.requireNonNull(preliminaryDerived); Objects.requireNonNull(input); Objects.requireNonNull(derived);
        }
        @Override public String toString() { return "TargetPlanEvidenceV3[redacted]"; }
    }
}
