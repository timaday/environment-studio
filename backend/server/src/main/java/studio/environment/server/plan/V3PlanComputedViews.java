package studio.environment.server.plan;

import java.util.List;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.BiFunction;
import studio.environment.core.derived.ComputedGraph;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanContentEvidence;
import studio.environment.core.plan.PlanDefinition;
import studio.environment.core.plan.PlanCommand;
import studio.environment.core.plan.PlanRefusal;

/** Internal computed presentation; no runtime admission or physical command authority. */
public final class V3PlanComputedViews {
    private V3PlanComputedViews() { }
    public record Page<T>(String revision, int total, int offset, OptionalInt nextOffset, List<T> items) {
        public Page { items = List.copyOf(items); }
        @Override public String toString() { return "ComputedPage[redacted]"; }
    }
    public record Node(ComputedGraph.Key key, int contributorTotal) {
        @Override public String toString() { return "ComputedNodeView[redacted]"; }
    }
    public record Membership(String relation, PlanCommand.Ref physical, ComputedGraph.Key computed, int contributorTotal) {
        @Override public String toString() { return "ComputedMembershipView[redacted]"; }
    }
    public record Cooccurrence(String relation, ComputedGraph.Key source, ComputedGraph.Key target, int contributorTotal) {
        @Override public String toString() { return "ComputedCooccurrenceView[redacted]"; }
    }
    public record FieldLocation(String field, DerivedInput.Location location) {
        @Override public String toString() { return "ComputedFieldLocationView[redacted]"; }
    }
    public record Contributor(PlanCommand.Ref physical, ObservedGraph.Origin origin, List<FieldLocation> roles) {
        public Contributor { roles = List.copyOf(roles); }
        @Override public String toString() { return "ComputedContributorView[redacted]"; }
    }
    public sealed interface ResultKey {
        record NodeKey(ComputedGraph.Key key) implements ResultKey {
            @Override public String toString() { return "ComputedNodeSelector[redacted]"; }
        }
        record MembershipKey(String relation, PlanCommand.Ref physical, ComputedGraph.Key computed) implements ResultKey {
            @Override public String toString() { return "ComputedMembershipSelector[redacted]"; }
        }
        record CooccurrenceKey(String relation, ComputedGraph.Key source, ComputedGraph.Key target) implements ResultKey {
            @Override public String toString() { return "ComputedCooccurrenceSelector[redacted]"; }
        }
    }
    public static Page<Membership> memberships(HostedPlanService.ViewAdmission admission, boolean target, int offset, int limit) {
        return read(admission, target, (snapshot, control) -> page(snapshot.revision(),
                derived(snapshot, target).graph().memberships(), offset, limit, control,
                edge -> new Membership(edge.relation(), reference(snapshot, target, edge.physical()), edge.computed(), edge.contributors().size())));
    }
    public static Page<Cooccurrence> cooccurrences(HostedPlanService.ViewAdmission admission, boolean target, int offset, int limit) {
        return read(admission, target, (snapshot, control) -> page(snapshot.revision(),
                derived(snapshot, target).graph().cooccurrences(), offset, limit, control,
                edge -> new Cooccurrence(edge.relation(), edge.source(), edge.target(), edge.contributors().size())));
    }
    public static Page<DerivedResult.RuleCheck> rules(HostedPlanService.ViewAdmission admission, boolean target, int offset, int limit) {
        return read(admission, target, (snapshot, control) -> page(snapshot.revision(),
                derived(snapshot, target).rules(), offset, limit, control, Function.identity()));
    }
    public static Page<Contributor> contributors(HostedPlanService.ViewAdmission admission, boolean target,
            ResultKey key, int offset, int limit, boolean disclosed) {
        if (!disclosed) throw new PlanRefusal(PlanRefusal.Code.DISCLOSURE_REQUIRED);
        if (key == null) throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
        return read(admission, target, (snapshot, control) -> page(snapshot.revision(),
                select(snapshot, target, key, control), offset, limit, control,
                contributor -> contributor(snapshot, target, contributor, control)));
    }
    private static PlanCommand.Ref reference(HostedPlanService.ViewSnapshot snapshot, boolean target, DerivedInput.Ref physical) {
        if (!(physical instanceof DerivedInput.Ref.Observed observed)) throw refused();
        var provenance = snapshot.selected(target).provenance().get(observed.key());
        if (provenance == null) throw refused();
        return snapshot.reference(provenance);
    }
    private static Contributor contributor(HostedPlanService.ViewSnapshot snapshot, boolean target,
            ComputedGraph.Contributor contributor, Cancellation control) {
        if (!(contributor.physical() instanceof DerivedInput.Ref.Observed observed)) throw refused();
        var roles = new ArrayList<FieldLocation>();
        for (var role : contributor.roles()) {
            V3PlanReadContent.live(control);
            if (!(role.proof() instanceof DerivedInput.Proof.Observed proof)) throw refused();
            roles.add(new FieldLocation(role.field(), proof.location()));
        }
        return new Contributor(reference(snapshot, target, observed), observed.origin(), roles);
    }
    private static List<ComputedGraph.Contributor> select(HostedPlanService.ViewSnapshot snapshot, boolean target,
            ResultKey key, Cancellation control) {
        var graph = derived(snapshot, target).graph();
        switch (key) {
            case ResultKey.NodeKey node -> {
                for (var candidate : graph.nodes()) {
                    V3PlanReadContent.live(control);
                    if (candidate.key().equals(node.key())) return candidate.contributors();
                }
            }
            case ResultKey.MembershipKey member -> {
                for (var candidate : graph.memberships()) {
                    V3PlanReadContent.live(control);
                    if (candidate.relation().equals(member.relation()) && candidate.computed().equals(member.computed())
                            && reference(snapshot, target, candidate.physical()).equals(member.physical())) return candidate.contributors();
                }
            }
            case ResultKey.CooccurrenceKey pair -> {
                for (var candidate : graph.cooccurrences()) {
                    V3PlanReadContent.live(control);
                    if (candidate.relation().equals(pair.relation()) && candidate.source().equals(pair.source())
                            && candidate.target().equals(pair.target())) return candidate.contributors();
                }
            }
        }
        throw new PlanRefusal(PlanRefusal.Code.NOT_FOUND);
    }
    public static Page<Node> nodes(HostedPlanService.ViewAdmission admission, boolean target, int offset, int limit) {
        return read(admission, target, (snapshot, control) -> page(snapshot.revision(),
                derived(snapshot, target).graph().nodes(), offset, limit, control,
                node -> new Node(node.key(), node.contributors().size())));
    }
    private static DerivedResult.Complete derived(HostedPlanService.ViewSnapshot snapshot, boolean target) {
        if (!(snapshot.selected(target).evidence() instanceof PlanContentEvidence.V3 evidence))
            throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
        return evidence.derived();
    }
    private static PlanRefusal refused() { return new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED); }
    private static <T> T read(HostedPlanService.ViewAdmission admission, boolean target,
            BiFunction<HostedPlanService.ViewSnapshot, Cancellation, T> render) {
        return admission.read((snapshot, control) -> {
            if (!(snapshot.definition().model() instanceof PlanDefinition.V3))
                throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
            V3PlanReadContent.verify(snapshot, snapshot.target().isPresent(), control);
            snapshot.selected(target);
            var result = render.apply(snapshot, control);
            V3PlanReadContent.live(control);
            return result;
        });
    }
    private static <S, T> Page<T> page(String revision, List<S> source, int offset, int limit,
            Cancellation control, Function<S, T> map) {
        if (offset < 0 || limit < 1 || limit > 100) throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
        int start = Math.min(offset, source.size());
        int end = start + Math.min(limit, source.size() - start);
        var items = new ArrayList<T>();
        for (int index = start; index < end; index++) {
            V3PlanReadContent.live(control);
            items.add(map.apply(source.get(index)));
        }
        V3PlanReadContent.live(control);
        return new Page<>(revision, source.size(), offset,
                end < source.size() ? OptionalInt.of(end) : OptionalInt.empty(), items);
    }
}
