package studio.environment.core.derived;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import studio.environment.core.Outcome;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.planning.TargetIntent;

/** Internal derived consistency proof, never XML, physical identity or runtime authority. */
public final class DerivedTargetComparison {
    public sealed interface Result permits Matched, Refused { }
    public record Matched() implements Result { }
    public record Refused(String code) implements Result {
        public Refused { Objects.requireNonNull(code); }
    }
    public Result compare(Checked definition, DerivedInput.Pin expectedTarget, DerivedInput typed,
            DerivedResult.Complete typedResult, DerivedInput.Pin expectedFinal, DerivedInput observedFinal,
            DerivedResult.Complete finalResult, Map<TargetIntent.Ref, DerivedInput.Ref.Observed> verifiedMapping,
            BooleanSupplier cancelled) {
        if (definition == null || expectedTarget == null || typed == null || typedResult == null
                || expectedFinal == null || observedFinal == null || finalResult == null
                || verifiedMapping == null || cancelled == null) return new Refused("INVALID_INPUT");
        try {
            cancellation(cancelled);
            if (typed.kind() != DerivedInput.Kind.TYPED_TARGET || observedFinal.kind() != DerivedInput.Kind.OBSERVED)
                fail("INVALID_INPUT");
            if (!expectedTarget.equals(typed.pin()) || !expectedFinal.equals(observedFinal.pin())
                    || !expectedTarget.revisionToken().equals(expectedFinal.revisionToken())
                    || !expectedTarget.logicalDigest().equals(expectedFinal.logicalDigest())
                    || !expectedTarget.bindingId().equals(expectedFinal.bindingId())
                    || !expectedTarget.bindingDigest().equals(expectedFinal.bindingDigest())
                    || !expectedTarget.documentDigests().keySet().equals(expectedFinal.documentDigests().keySet())) fail("STALE_INPUT");
            if (verifiedMapping.size() > 20_000) fail("RESOURCE_LIMIT");
            bounded(typedResult, cancelled);
            bounded(finalResult, cancelled);
            canonical(definition, expectedTarget, typed, typedResult, cancelled);
            canonical(definition, expectedFinal, observedFinal, finalResult, cancelled);
            for (var result : List.of(typedResult, finalResult)) for (var rule : result.rules()) {
                cancellation(cancelled);
                if (rule.outcome() != Outcome.PASS) fail("DERIVED_RULE_FAILED");
            }
            var finalEntities = new HashMap<DerivedInput.Ref, DerivedInput.Entity>();
            var ranks = new HashMap<DerivedInput.Ref, Integer>();
            for (var entity : observedFinal.entities()) {
                cancellation(cancelled);
                if (finalEntities.put(entity.reference(), entity) != null) fail("INVALID_MAPPING");
                ranks.put(entity.reference(), ranks.size());
            }
            if (verifiedMapping.size() != typed.entities().size() || verifiedMapping.size() != finalEntities.size()) fail("INVALID_MAPPING");
            var mapping = new HashMap<DerivedInput.Ref, DerivedInput.Ref>();
            var used = new HashSet<DerivedInput.Ref>();
            for (var entity : typed.entities()) {
                cancellation(cancelled);
                var target = ((DerivedInput.Ref.Target)entity.reference()).reference();
                var actual = verifiedMapping.get(target);
                if (actual == null || !target.type().equals(actual.type()) || !finalEntities.containsKey(actual)
                        || !used.add(actual) || mapping.put(entity.reference(), actual) != null) fail("INVALID_MAPPING");
            }
            for (var entity : typed.entities()) {
                cancellation(cancelled);
                fields(entity, finalEntities.get(mapping.get(entity.reference())), cancelled);
            }
            compareGraphs(typedResult.graph(), finalResult.graph(), mapping, finalEntities, ranks, cancelled);
            if (!typedResult.rules().equals(finalResult.rules())) fail("TARGET_MISMATCH");
            cancellation(cancelled);
            return new Matched();
        } catch (Failure failure) { return new Refused(failure.code); }
    }
    private static void canonical(Checked definition, DerivedInput.Pin expected, DerivedInput input,
            DerivedResult.Complete supplied, BooleanSupplier cancelled) {
        var result = new DerivedGraphEngine().evaluate(definition, expected, input, cancelled);
        if (result instanceof DerivedResult.Refused refused) fail(refused.code());
        if (!(result instanceof DerivedResult.Complete)) fail("INCOMPLETE_INPUT");
        if (!result.equals(supplied)) fail("INVALID_RESULT");
        cancellation(cancelled);
    }
    private static void bounded(DerivedResult.Complete result, BooleanSupplier cancelled) {
        var graph = result.graph();
        if (graph.nodes().size() > 20_000 || graph.memberships().size() > 50_000
                || graph.cooccurrences().size() > 50_000 || result.rules().size() > 640_032) fail("RESOURCE_LIMIT");
        long associations = 0;
        for (var node : graph.nodes()) { cancellation(cancelled); associations += node.contributors().size(); }
        for (var edge : graph.memberships()) { cancellation(cancelled); associations += edge.contributors().size(); }
        for (var edge : graph.cooccurrences()) { cancellation(cancelled); associations += edge.contributors().size(); }
        if (associations > 100_000) fail("RESOURCE_LIMIT");
    }
    private static void fields(DerivedInput.Entity typed, DerivedInput.Entity actual, BooleanSupplier cancelled) {
        if (!typed.fields().keySet().equals(actual.fields().keySet())) fail("TARGET_MISMATCH");
        for (var entry : typed.fields().entrySet()) {
            cancellation(cancelled);
            var other = actual.fields().get(entry.getKey());
            if (entry.getValue() instanceof DerivedInput.FieldState.Absent) {
                if (!(other instanceof DerivedInput.FieldState.Absent)) fail("TARGET_MISMATCH");
            } else if (entry.getValue() instanceof DerivedInput.FieldState.Present present) {
                if (!(other instanceof DerivedInput.FieldState.Present observed) || !present.text().equals(observed.text())) fail("TARGET_MISMATCH");
            } else fail("INCOMPLETE_INPUT");
        }
    }
    private static void compareGraphs(ComputedGraph typed, ComputedGraph actual,
            Map<DerivedInput.Ref, DerivedInput.Ref> mapping, Map<DerivedInput.Ref, DerivedInput.Entity> finalEntities,
            Map<DerivedInput.Ref, Integer> ranks, BooleanSupplier cancelled) {
        if (typed.nodes().size() != actual.nodes().size() || typed.memberships().size() != actual.memberships().size()
                || typed.cooccurrences().size() != actual.cooccurrences().size()) fail("TARGET_MISMATCH");
        for (int i = 0; i < typed.nodes().size(); i++) {
            cancellation(cancelled);
            var left = typed.nodes().get(i); var right = actual.nodes().get(i);
            if (!left.key().equals(right.key())) fail("TARGET_MISMATCH");
            contributors(left.contributors(), right.contributors(), mapping, finalEntities, ranks, cancelled);
        }
        var members = new HashMap<MemberKey, ComputedGraph.Membership>();
        for (var edge : actual.memberships()) {
            cancellation(cancelled);
            if (members.put(new MemberKey(edge.relation(), edge.physical(), edge.computed()), edge) != null) fail("TARGET_MISMATCH");
        }
        for (var edge : typed.memberships()) {
            cancellation(cancelled);
            var right = members.remove(new MemberKey(edge.relation(), mapping.get(edge.physical()), edge.computed()));
            if (right == null) fail("TARGET_MISMATCH");
            contributors(edge.contributors(), right.contributors(), mapping, finalEntities, ranks, cancelled);
        }
        if (!members.isEmpty()) fail("TARGET_MISMATCH");
        for (int i = 0; i < typed.cooccurrences().size(); i++) {
            cancellation(cancelled);
            var left = typed.cooccurrences().get(i); var right = actual.cooccurrences().get(i);
            if (!left.relation().equals(right.relation()) || !left.source().equals(right.source()) || !left.target().equals(right.target())) fail("TARGET_MISMATCH");
            contributors(left.contributors(), right.contributors(), mapping, finalEntities, ranks, cancelled);
        }
    }
    private record MemberKey(String relation, DerivedInput.Ref physical, ComputedGraph.Key computed) { }
    private static void contributors(List<ComputedGraph.Contributor> typed, List<ComputedGraph.Contributor> actual,
            Map<DerivedInput.Ref, DerivedInput.Ref> mapping, Map<DerivedInput.Ref, DerivedInput.Entity> finalEntities,
            Map<DerivedInput.Ref, Integer> ranks, BooleanSupplier cancelled) {
        if (typed.size() != actual.size()) fail("TARGET_MISMATCH");
        var normalized = new ArrayList<ComputedGraph.Contributor>(typed.size());
        for (var contributor : typed) {
            cancellation(cancelled);
            var reference = mapping.get(contributor.physical());
            var entity = finalEntities.get(reference);
            if (entity == null) fail("INVALID_MAPPING");
            var roles = new ArrayList<ComputedGraph.FieldRole>(contributor.roles().size());
            for (var role : contributor.roles()) {
                cancellation(cancelled);
                if (!(entity.fields().get(role.field()) instanceof DerivedInput.FieldState.Present)) fail("TARGET_MISMATCH");
                var field = (DerivedInput.FieldState.Present)entity.fields().get(role.field());
                roles.add(new ComputedGraph.FieldRole(role.field(), field.proof()));
            }
            normalized.add(new ComputedGraph.Contributor(reference, roles));
        }
        var right = new ArrayList<>(actual);
        var order = Comparator.comparingInt((ComputedGraph.Contributor c) -> ranks.get(c.physical()));
        normalized.sort(order); right.sort(order);
        if (!normalized.equals(right)) fail("TARGET_MISMATCH");
        cancellation(cancelled);
    }
    private static void cancellation(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) fail("CANCELLED"); }
    private static void fail(String code) { throw new Failure(code); }
    private static final class Failure extends RuntimeException {
        private final String code;
        private Failure(String code) { super(null, null, false, false); this.code = code; }
    }
}
