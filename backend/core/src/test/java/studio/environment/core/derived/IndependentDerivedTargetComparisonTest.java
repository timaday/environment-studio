package studio.environment.core.derived;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.environment.core.planning.TargetIntent;

class IndependentDerivedTargetComparisonTest {
    @Test void physicalBijectionStillMattersWhenEveryDerivedSourceIsAbsent() {
        var populated = DerivedTargetComparisonTest.fixture();
        Map<String, DerivedInput.FieldState> absent = Map.of(
                "tone", new DerivedInput.FieldState.Absent(), "finish", new DerivedInput.FieldState.Absent());
        var typed = new DerivedInput(populated.typed().kind(), populated.typed().pin(), populated.typed().entities().stream()
                .map(entity -> new DerivedInput.Entity(entity.reference(), absent)).toList(), List.of());
        var actual = new DerivedInput(populated.actual().kind(), populated.actual().pin(), populated.actual().entities().stream()
                .map(entity -> new DerivedInput.Entity(entity.reference(), absent)).toList(), List.of());
        var valid = new DerivedTargetComparisonTest.Fixture(populated.definition(), typed, actual, populated.mapping());
        assertTrue(DerivedTargetComparisonTest.evaluate(valid, typed).graph().nodes().isEmpty());
        assertTrue(DerivedTargetComparisonTest.evaluate(valid, actual).graph().nodes().isEmpty());
        assertEquals(new DerivedTargetComparison.Matched(), DerivedTargetComparisonTest.compare(valid));

        var first = ((DerivedInput.Ref.Target)typed.entities().getFirst().reference()).reference();
        var second = ((DerivedInput.Ref.Target)typed.entities().getLast().reference()).reference();
        var sameOccurrence = (DerivedInput.Ref.Observed)actual.entities().getFirst().reference();
        Map<TargetIntent.Ref, DerivedInput.Ref.Observed> collapsed = Map.of(first, sameOccurrence, second, sameOccurrence);
        assertEquals(new DerivedTargetComparison.Refused("INVALID_MAPPING"), DerivedTargetComparisonTest.compare(
                new DerivedTargetComparisonTest.Fixture(populated.definition(), typed, actual, collapsed)));
    }
}
