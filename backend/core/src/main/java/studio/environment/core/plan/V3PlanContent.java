package studio.environment.core.plan;

import java.util.List;
import java.util.Objects;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult;

/** Actual v3 source/target qualification; the shared plan owner installs results under its live lease. */
public interface V3PlanContent {
    Result project(PlanDefinition.V3 definition, String binding, ObservationResult.Observation observation, Cancellation cancellation);
    Result materialize(PlanDefinition.V3 definition, DerivedInput.Pin expectedCurrent, PlanPorts.Content original,
            DerivedInput.Pin expectedTarget, PlanPorts.Draft draft, Cancellation cancellation);
    sealed interface Result {
        record Complete(PlanPorts.Content content) implements Result {
            public Complete { Objects.requireNonNull(content); }
            @Override public String toString() { return "CompletePlanContentV3[redacted]"; }
        }
        record Incomplete(List<String> references) implements Result {
            public Incomplete {
                if (references.size() > 256) throw new IllegalArgumentException("Incomplete reference limit exceeded.");
                references = List.copyOf(references);
            }
        }
        record Refused(String code) implements Result { public Refused { Objects.requireNonNull(code); } }
    }
}
