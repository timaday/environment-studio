package studio.environment.core.derived;

import java.util.List;
import java.util.Objects;

/** Complete computed partition and contributor evidence; never physical or export authority. */
public record ComputedGraph(DerivedInput.Pin pin, List<Node> nodes, List<Membership> memberships,
        List<Cooccurrence> cooccurrences) {
    public ComputedGraph {
        Objects.requireNonNull(pin); nodes = List.copyOf(nodes);
        memberships = List.copyOf(memberships); cooccurrences = List.copyOf(cooccurrences);
    }
    @Override public String toString() { return "ComputedGraph[redacted]"; }
    public record Key(String computedType, String derivation, String value) {
        public Key { Objects.requireNonNull(computedType); Objects.requireNonNull(derivation); Objects.requireNonNull(value); }
        @Override public String toString() { return "ComputedKey[redacted]"; }
    }
    public record FieldRole(String field, DerivedInput.Proof proof) {
        public FieldRole { Objects.requireNonNull(field); Objects.requireNonNull(proof); }
        @Override public String toString() { return "ComputedFieldRole[redacted]"; }
    }
    public record Contributor(DerivedInput.Ref physical, List<FieldRole> roles) {
        public Contributor {
            Objects.requireNonNull(physical); roles = List.copyOf(roles);
            if (roles.isEmpty() || roles.size() > 2) throw new IllegalArgumentException("Invalid contributor roles.");
        }
        @Override public String toString() { return "ComputedContributor[redacted]"; }
    }
    public record Node(Key key, List<Contributor> contributors) {
        public Node { Objects.requireNonNull(key); contributors = nonempty(contributors); }
        @Override public String toString() { return "ComputedNode[redacted]"; }
    }
    public record Membership(String relation, DerivedInput.Ref physical, Key computed, List<Contributor> contributors) {
        public Membership { Objects.requireNonNull(relation); Objects.requireNonNull(physical); Objects.requireNonNull(computed); contributors = nonempty(contributors); }
        @Override public String toString() { return "Membership[redacted]"; }
    }
    public record Cooccurrence(String relation, Key source, Key target, List<Contributor> contributors) {
        public Cooccurrence { Objects.requireNonNull(relation); Objects.requireNonNull(source); Objects.requireNonNull(target); contributors = nonempty(contributors); }
        @Override public String toString() { return "Cooccurrence[redacted]"; }
    }
    private static List<Contributor> nonempty(List<Contributor> contributors) {
        if (contributors.isEmpty()) throw new IllegalArgumentException("Contributor evidence is required.");
        return List.copyOf(contributors);
    }
}
