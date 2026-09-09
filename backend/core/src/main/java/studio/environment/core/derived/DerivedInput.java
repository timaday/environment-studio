package studio.environment.core.derived;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import studio.environment.core.definitionv2.NativeDefinition.ExpandedName;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;

/** Internal complete physical input. Adapters must establish source/topology facts and live ownership. */
public record DerivedInput(Kind kind, Pin pin, List<Entity> entities, List<Edge> edges) {
    public enum Kind { OBSERVED, TYPED_TARGET }
    public DerivedInput {
        Objects.requireNonNull(kind); Objects.requireNonNull(pin);
        bound(entities.size(), 20_000); bound(edges.size(), 50_000);
        entities = List.copyOf(entities); edges = List.copyOf(edges);
    }
    @Override public String toString() { return "DerivedInput[redacted]"; }
    /** Equality pin only, never a capability or a database-completeness assertion. */
    public record Pin(String revisionToken, String logicalDigest, String bindingId, String bindingDigest,
            Map<String, String> documentDigests) {
        public Pin {
            Objects.requireNonNull(revisionToken); Objects.requireNonNull(logicalDigest);
            Objects.requireNonNull(bindingId); Objects.requireNonNull(bindingDigest);
            bound(documentDigests.size(), 128); documentDigests = immutable(documentDigests);
        }
        @Override public String toString() { return "DerivedPin[redacted]"; }
    }
    public sealed interface Ref permits Ref.Observed, Ref.Target {
        String type();
        record Observed(ObservedGraph.Key key, ObservedGraph.Origin origin) implements Ref {
            public Observed { Objects.requireNonNull(key); Objects.requireNonNull(origin); }
            @Override public String type() { return key.type(); }
            @Override public String toString() { return "ObservedContributor[redacted]"; }
        }
        record Target(TargetIntent.Ref reference) implements Ref {
            public Target { Objects.requireNonNull(reference); }
            @Override public String type() { return reference.type(); }
            @Override public String toString() { return "TargetContributor[redacted]"; }
        }
    }
    /** Explicit states for source fields; adapters validate the remaining physical fields separately. */
    public record Entity(Ref reference, Map<String, FieldState> fields) {
        public Entity {
            Objects.requireNonNull(reference); bound(fields.size(), 256); fields = immutable(fields);
        }
        @Override public String toString() { return "DerivedPhysicalEntity[redacted]"; }
    }
    public record Edge(String relation, Ref source, Ref target) {
        public Edge { Objects.requireNonNull(relation); Objects.requireNonNull(source); Objects.requireNonNull(target); }
        @Override public String toString() { return "DerivedPhysicalEdge[redacted]"; }
    }
    public sealed interface FieldState permits FieldState.Present, FieldState.Absent, FieldState.Unresolved {
        record Present(String text, Proof proof) implements FieldState {
            public Present { Objects.requireNonNull(text); Objects.requireNonNull(proof); }
            @Override public String toString() { return "DerivedValue[redacted]"; }
        }
        record Absent() implements FieldState { }
        record Unresolved() implements FieldState { }
    }
    public sealed interface Proof permits Proof.Observed, Proof.Target {
        record Observed(Location location) implements Proof {
            public Observed { Objects.requireNonNull(location); }
            @Override public String toString() { return "ObservedFieldProof[redacted]"; }
        }
        record Target(TargetIntent.FieldValue decision, Optional<Kept> kept) implements Proof {
            public Target { Objects.requireNonNull(decision); kept = Objects.requireNonNull(kept); }
            @Override public String toString() { return "TargetFieldProof[redacted]"; }
        }
    }
    public record Kept(Ref.Observed source, Location location) {
        public Kept { Objects.requireNonNull(source); Objects.requireNonNull(location); }
        @Override public String toString() { return "KeptFieldProof[redacted]"; }
    }
    public record Location(AttributePin value, Optional<ChildSelector> selector) {
        public Location { Objects.requireNonNull(value); selector = Objects.requireNonNull(selector); }
        @Override public String toString() { return "DerivedFieldLocation[redacted]"; }
    }
    public record ChildSelector(int parentElementIndex, ExpandedName element, AttributePin discriminator) {
        public ChildSelector { Objects.requireNonNull(element); Objects.requireNonNull(discriminator); }
        @Override public String toString() { return "DerivedChildSelector[redacted]"; }
    }
    public record AttributePin(String documentId, String sourceDigest, int elementIndex, ExpandedName name,
            String qualifiedName, String decodedValue, int valueStart, int valueEnd, char quote) {
        public AttributePin {
            Objects.requireNonNull(documentId); Objects.requireNonNull(sourceDigest); Objects.requireNonNull(name);
            Objects.requireNonNull(qualifiedName); Objects.requireNonNull(decodedValue);
        }
        @Override public String toString() { return "DerivedAttributePin[redacted]"; }
    }
    private static <V> Map<String, V> immutable(Map<String, V> source) {
        var result = new TreeMap<String, V>();
        source.forEach((key, value) -> result.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
        return Collections.unmodifiableMap(result);
    }
    private static void bound(int count, int maximum) {
        if (count > maximum) throw new IllegalArgumentException("Derived input exceeds its structural limit.");
    }
}
