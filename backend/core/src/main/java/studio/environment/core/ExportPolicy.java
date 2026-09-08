package studio.environment.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Reference evidence gate. Not a public authorization token or the complete plan validator. */
public final class ExportPolicy {
    public record Decision(List<String> blockers) {
        public Decision { blockers = List.copyOf(blockers); }
        public boolean eligible() { return blockers.isEmpty(); }
    }

    public Decision evaluate(String expectedFingerprint, Collection<CheckResult> evidence) {
        CheckResult.requireFingerprint(expectedFingerprint);
        Objects.requireNonNull(evidence);
        var grouped = new EnumMap<RequiredCheck, List<CheckResult>>(RequiredCheck.class);
        for (var item : evidence) {
            Objects.requireNonNull(item);
            grouped.computeIfAbsent(item.check(), key -> new ArrayList<>()).add(item);
        }
        var blockers = new ArrayList<String>();
        for (var required : RequiredCheck.values()) {
            var items = grouped.getOrDefault(required, List.of());
            if (items.isEmpty()) {
                blockers.add(required + ":MISSING");
            } else if (items.size() != 1) {
                blockers.add(required + ":DUPLICATE");
            } else {
                var result = items.getFirst();
                if (!expectedFingerprint.equals(result.inputFingerprint())) {
                    blockers.add(required + ":STALE");
                } else if (result.outcome() != Outcome.PASS) {
                    blockers.add(required + ":" + result.outcome());
                }
            }
        }
        return new Decision(blockers);
    }
}
