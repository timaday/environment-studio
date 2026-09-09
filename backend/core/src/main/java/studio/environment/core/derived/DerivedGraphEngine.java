package studio.environment.core.derived;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import studio.environment.core.Outcome;
import studio.environment.core.definitionv3.DerivedSemantics;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.definitionv3.NativeDefinition.Derivation;
import studio.environment.core.planning.TargetIntent;
import static studio.environment.core.derived.ComputedGraph.*;

/** Exact bounded computation over validated internal input, never runtime admission. */
public final class DerivedGraphEngine {
    private static final Comparator<Key> KEYS = Comparator.comparing(Key::computedType, DerivedGraphEngine::text)
            .thenComparing(Key::derivation, DerivedGraphEngine::text).thenComparing(Key::value, DerivedGraphEngine::text);
    private record MemberKey(String relation, DerivedInput.Ref physical, Key computed) { }
    private record PairKey(String relation, Key source, Key target) { }
    private static final Comparator<MemberKey> MEMBERS = Comparator.comparing(MemberKey::relation, DerivedGraphEngine::text)
            .thenComparing(MemberKey::physical, DerivedGraphEngine::reference).thenComparing(MemberKey::computed, KEYS);
    private static final Comparator<PairKey> PAIRS = Comparator.comparing(PairKey::relation, DerivedGraphEngine::text)
            .thenComparing(PairKey::source, KEYS).thenComparing(PairKey::target, KEYS);

    public DerivedResult evaluate(Checked definition, DerivedInput.Pin expected, DerivedInput input, BooleanSupplier cancelled) {
        if (cancelled == null) return new DerivedResult.Refused("INVALID_INPUT");
        try {
            cancellation(cancelled);
            var validated = DerivedInputValidator.check(definition, expected, input);
            cancellation(cancelled);
            return switch (validated) {
                case DerivedInputValidator.Refused refused -> new DerivedResult.Refused(refused.code());
                case DerivedInputValidator.Incomplete incomplete -> new DerivedResult.Incomplete(incomplete.derivations());
                case DerivedInputValidator.Valid valid -> compute(definition, input, cancelled);
            };
        } catch (Refusal refused) {
            return new DerivedResult.Refused(refused.code);
        }
    }

    private DerivedResult compute(Checked definition, DerivedInput input, BooleanSupplier cancelled) {
        var nodes = new TreeMap<Key, List<Contributor>>(KEYS);
        var members = new TreeMap<MemberKey, List<Contributor>>(MEMBERS);
        var pairs = new TreeMap<PairKey, List<Contributor>>(PAIRS);
        var budget = new Budget(input.entities().size(), input.edges().size());
        var derivations = definition.definition().logical().derivations().stream()
                .sorted(Comparator.comparing(Derivation::id)).toList();
        var entities = new ArrayList<>(input.entities());
        entities.sort((left, right) -> {
            cancellation(cancelled);
            return reference(left.reference(), right.reference());
        });
        for (var entity : entities) {
            cancellation(cancelled);
            var selected = new TreeMap<String, Key>();
            for (var derivation : derivations) {
                cancellation(cancelled);
                if (!derivation.sourceType().equals(entity.reference().type())) continue;
                var state = entity.fields().get(derivation.sourceField());
                if (!(state instanceof DerivedInput.FieldState.Present present)) continue;
                long bytes = bytes(present.text(), cancelled);
                var key = new Key(derivation.computedType(), derivation.id(), present.text());
                var contributors = nodes.get(key);
                if (contributors == null) {
                    budget.node(bytes);
                    contributors = new ArrayList<>();
                    nodes.put(key, contributors);
                }
                budget.edge();
                budget.links(2);
                var contributor = new Contributor(entity.reference(), List.of(new FieldRole(derivation.sourceField(), present.proof())));
                contributors.add(contributor);
                var member = new MemberKey(derivation.membershipRelation(), entity.reference(), key);
                if (members.putIfAbsent(member, List.of(contributor)) != null) fail("DUPLICATE_CONTRIBUTOR");
                selected.put(derivation.id(), key);
            }
            for (var declaration : definition.definition().logical().cooccurrences()) {
                cancellation(cancelled);
                var from = selected.get(declaration.fromDerivation());
                var to = selected.get(declaration.toDerivation());
                if (from == null || to == null) continue;
                var key = new PairKey(declaration.id(), from, to);
                var contributors = pairs.get(key);
                if (contributors == null) {
                    budget.edge();
                    contributors = new ArrayList<>();
                    pairs.put(key, contributors);
                }
                budget.links(1);
                var fromDeclaration = derivations.stream().filter(d -> d.id().equals(declaration.fromDerivation())).findFirst().orElseThrow();
                var toDeclaration = derivations.stream().filter(d -> d.id().equals(declaration.toDerivation())).findFirst().orElseThrow();
                var fromValue = (DerivedInput.FieldState.Present) entity.fields().get(fromDeclaration.sourceField());
                var toValue = (DerivedInput.FieldState.Present) entity.fields().get(toDeclaration.sourceField());
                contributors.add(new Contributor(entity.reference(), List.of(new FieldRole(fromDeclaration.sourceField(), fromValue.proof()),
                        new FieldRole(toDeclaration.sourceField(), toValue.proof()))));
            }
        }
        var resultNodes = new ArrayList<Node>();
        for (var entry : nodes.entrySet()) {
            cancellation(cancelled);
            resultNodes.add(new Node(entry.getKey(), entry.getValue()));
        }
        var resultMembers = new ArrayList<Membership>();
        for (var entry : members.entrySet()) {
            cancellation(cancelled);
            var key = entry.getKey();
            resultMembers.add(new Membership(key.relation(), key.physical(), key.computed(), entry.getValue()));
        }
        var resultPairs = new ArrayList<Cooccurrence>();
        for (var entry : pairs.entrySet()) {
            cancellation(cancelled);
            var key = entry.getKey();
            resultPairs.add(new Cooccurrence(key.relation(), key.source(), key.target(), entry.getValue()));
        }
        var rules = rules(definition, nodes, pairs, cancelled);
        var result = new DerivedResult.Complete(new ComputedGraph(input.pin(), resultNodes, resultMembers, resultPairs), rules);
        cancellation(cancelled);
        return result;
    }

    private static List<DerivedResult.RuleCheck> rules(Checked definition, Map<Key, List<Contributor>> nodes,
            Map<PairKey, List<Contributor>> pairs, BooleanSupplier cancelled) {
        var result = new ArrayList<DerivedResult.RuleCheck>();
        var counts = new TreeMap<String, BigInteger>();
        for (var key : nodes.keySet()) {
            cancellation(cancelled);
            counts.merge(key.computedType(), BigInteger.ONE, BigInteger::add);
        }
        for (var rule : definition.definition().logical().computedRules()) {
            cancellation(cancelled);
            result.add(check(DerivedResult.RuleKind.ENTITY_COUNT, rule.id(), Optional.empty(),
                    counts.getOrDefault(rule.type(), BigInteger.ZERO), rule.minimum(), rule.maximum()));
        }
        for (var relation : definition.definition().logical().cooccurrences()) {
            cancellation(cancelled);
            var outgoing = new TreeMap<Key, BigInteger>(KEYS);
            for (var edge : pairs.keySet()) {
                cancellation(cancelled);
                if (edge.relation().equals(relation.id())) outgoing.merge(edge.source(), BigInteger.ONE, BigInteger::add);
            }
            for (var key : nodes.keySet()) {
                cancellation(cancelled);
                if (key.derivation().equals(relation.fromDerivation())) result.add(check(DerivedResult.RuleKind.COOCCURRENCE,
                        relation.id(), Optional.of(key), outgoing.getOrDefault(key, BigInteger.ZERO), relation.minimum(), relation.maximum()));
            }
        }
        var order = Comparator.comparing(DerivedResult.RuleCheck::declaration, DerivedGraphEngine::text)
                .thenComparing(r -> r.source().orElse(null), Comparator.nullsFirst(KEYS));
        result.sort((left, right) -> { cancellation(cancelled); return order.compare(left, right); });
        return List.copyOf(result);
    }
    private static DerivedResult.RuleCheck check(DerivedResult.RuleKind kind, String id, Optional<Key> source,
            BigInteger actual, BigInteger minimum, BigInteger maximum) {
        return new DerivedResult.RuleCheck(kind, id, source, actual, minimum, maximum,
                actual.compareTo(minimum) >= 0 && actual.compareTo(maximum) <= 0 ? Outcome.PASS : Outcome.FAIL);
    }
    private static int reference(DerivedInput.Ref left, DerivedInput.Ref right) {
        if (left instanceof DerivedInput.Ref.Observed a && right instanceof DerivedInput.Ref.Observed b) {
            int result = text(a.origin().documentId(), b.origin().documentId());
            if (result == 0) result = text(a.origin().projectionId(), b.origin().projectionId());
            return result == 0 ? Integer.compare(a.origin().elementIndex(), b.origin().elementIndex()) : result;
        }
        if (left instanceof DerivedInput.Ref.Target a && right instanceof DerivedInput.Ref.Target b) {
            int result = text(a.type(), b.type());
            if (result != 0) return result;
            boolean oldA = a.reference() instanceof TargetIntent.Ref.Existing;
            boolean oldB = b.reference() instanceof TargetIntent.Ref.Existing;
            if (oldA != oldB) return oldA ? -1 : 1;
            String keyA = oldA ? ((TargetIntent.Ref.Existing) a.reference()).key().identity() : ((TargetIntent.Ref.Fresh) a.reference()).slot();
            String keyB = oldB ? ((TargetIntent.Ref.Existing) b.reference()).key().identity() : ((TargetIntent.Ref.Fresh) b.reference()).slot();
            return text(keyA, keyB);
        }
        throw new IllegalArgumentException("Mixed derived input kinds.");
    }
    /** Scalar-value ordering equals unsigned UTF-8 ordering after strict validation. */
    private static int text(String left, String right) {
        int a = 0, b = 0;
        while (a < left.length() && b < right.length()) {
            int x = left.codePointAt(a), y = right.codePointAt(b);
            if (x != y) return Integer.compare(x, y);
            a += Character.charCount(x); b += Character.charCount(y);
        }
        return Integer.compare(left.length() - a, right.length() - b);
    }
    private static long bytes(String value, BooleanSupplier cancelled) {
        if (value.isEmpty()) fail("INVALID_DERIVED_IDENTITY");
        long bytes = 0;
        for (int offset = 0; offset < value.length();) {
            if ((offset & 255) == 0) cancellation(cancelled);
            int cp = value.codePointAt(offset);
            if (cp >= 0xd800 && cp <= 0xdfff) fail("INVALID_DERIVED_IDENTITY");
            bytes += cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            offset += Character.charCount(cp);
        }
        return bytes;
    }
    private static final class Budget {
        private int nodes, edges, links;
        private long identityBytes;
        Budget(int nodes, int edges) { this.nodes = nodes; this.edges = edges; }
        void node(long bytes) {
            if (nodes >= DerivedSemantics.MAX_TOTAL_NODES || bytes > DerivedSemantics.MAX_IDENTITY_UTF8_BYTES - identityBytes) fail("RESOURCE_LIMIT");
            nodes++; identityBytes += bytes;
        }
        void edge() { if (edges >= DerivedSemantics.MAX_TOTAL_EDGES) fail("RESOURCE_LIMIT"); edges++; }
        void links(int count) { if (count > DerivedSemantics.MAX_CONTRIBUTOR_LINKS - links) fail("RESOURCE_LIMIT"); links += count; }
    }
    private static void cancellation(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) fail("CANCELLED"); }
    private static void fail(String code) { throw new Refusal(code); }
    private static final class Refusal extends RuntimeException {
        private final String code;
        Refusal(String code) { super(null, null, false, false); this.code = code; }
    }
}
