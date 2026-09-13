package studio.environment.core.plan;

import java.util.Objects;
import studio.environment.core.derived.DerivedInput;

/** Immutable equality expectations from an owning plan; construction grants no authority. */
public record V3PlanPins(DerivedInput.Pin original,DerivedInput.Pin decisions) {
    public V3PlanPins { Objects.requireNonNull(original);Objects.requireNonNull(decisions); }
    @Override public String toString(){return "V3PlanPins[redacted]";}
}
