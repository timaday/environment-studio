package studio.environment.core.planning;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;
import studio.environment.core.graph.ObservedGraph;

/** Explicit transient decisions; never publication, execution or export authority. */
public record TargetIntent(List<EntityDecision> entities, List<Containment> containment) {
    public TargetIntent { bound(entities.size(), 20_000); bound(containment.size(), 20_000); entities = List.copyOf(entities); containment = List.copyOf(containment); }
    @Override public String toString() { return "TargetIntent[redacted]"; }
    public sealed interface Ref {
        String type();
        record Existing(ObservedGraph.Key key) implements Ref {
            @Override public String type() { return key.type(); }
            @Override public String toString() { return "ExistingEntity[redacted]"; }
        }
        record Fresh(String slot, String type) implements Ref {
            @Override public String toString() { return "FreshEntity[redacted]"; }
        }
    }
    public sealed interface FieldValue {
        record Entered(String text) implements FieldValue { @Override public String toString() { return "Entered[redacted]"; } }
        record KeepObserved() implements FieldValue { }
        record ExplicitlyAbsent() implements FieldValue { }
        record Unresolved() implements FieldValue { }
    }
    public sealed interface ReferenceValue {
        record To(Ref target) implements ReferenceValue { @Override public String toString() { return "ReferenceTo[redacted]"; } }
        record KeepObserved() implements ReferenceValue { }
        record ExplicitlyAbsent() implements ReferenceValue { }
        record Unresolved() implements ReferenceValue { }
    }
    public sealed interface EntityDecision {
        Ref entity();
        record Retain(Ref.Existing entity, Map<String, FieldValue> fields, Map<String, ReferenceValue> references) implements EntityDecision {
            public Retain { fields = immutable(fields); references = immutable(references); }
            @Override public String toString() { return "Retain[redacted]"; }
        }
        record Create(Ref.Fresh entity, Map<String, FieldValue> fields, Map<String, ReferenceValue> references) implements EntityDecision {
            public Create { fields = immutable(fields); references = immutable(references); }
            @Override public String toString() { return "Create[redacted]"; }
        }
        record Remove(Ref.Existing entity) implements EntityDecision { @Override public String toString() { return "Remove[redacted]"; } }
    }
    public record Containment(String relation, Ref parent, Ref child) { @Override public String toString() { return "Containment[redacted]"; } }
    private static <V> Map<String, V> immutable(Map<String, V> values) { bound(values.size(), 256); return Collections.unmodifiableMap(new TreeMap<>(values)); }
    static void bound(int count, int maximum) { if (count > maximum) throw new IllegalArgumentException("Planning collection limit exceeded."); }
}
