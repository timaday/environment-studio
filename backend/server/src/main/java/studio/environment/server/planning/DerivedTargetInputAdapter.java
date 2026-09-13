package studio.environment.server.planning;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.derived.DerivedGraphEngine;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.planning.ExpectedTarget;
import studio.environment.core.planning.TargetCompilationResult;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.V3TargetIntentCompiler;
import studio.environment.server.projection.DerivedGraphProjectionAdapter;

/** Internal typed target preparation; final materialization and owner authority remain separate. */
public final class DerivedTargetInputAdapter {
    public record Decisions(DerivedInput.Pin pin, TargetIntent intent) {
        public Decisions { Objects.requireNonNull(pin); Objects.requireNonNull(intent); }
        @Override public String toString() { return "DerivedTargetDecisions[redacted]"; }
    }
    public sealed interface Result permits Complete, Incomplete, Refused { }
    public record Complete(DerivedGraphProjectionAdapter.Complete current, ExpectedTarget physical,
            DerivedInput input, DerivedResult.Complete derived) implements Result {
        public Complete { Objects.requireNonNull(current); Objects.requireNonNull(physical); Objects.requireNonNull(input); Objects.requireNonNull(derived); }
        @Override public String toString() { return "DerivedTypedTarget[redacted]"; }
    }
    public record Incomplete(List<String> references) implements Result {
        public Incomplete {
            if (references.size() > 256) throw new IllegalArgumentException("Incomplete reference limit exceeded.");
            references = references.stream().distinct().sorted().toList();
        }
    }
    public record Refused(String code) implements Result { public Refused { Objects.requireNonNull(code); } }
    public Result prepare(Checked definition, DerivedInput.Pin expectedCurrent, DerivedGraphProjectionAdapter.Snapshot current,
            DerivedInput.Pin expectedTarget, Decisions supplied, BooleanSupplier cancelled) {
        if (definition == null || expectedCurrent == null || current == null || expectedTarget == null || supplied == null || cancelled == null)
            return new Refused("INVALID_INPUT");
        try { return prepareTarget(definition, expectedCurrent, current, expectedTarget, supplied, cancelled); }
        catch (Failure failure) { return new Refused(failure.code); }
    }
    private Result prepareTarget(Checked definition, DerivedInput.Pin expectedCurrent, DerivedGraphProjectionAdapter.Snapshot current,
            DerivedInput.Pin expectedTarget, Decisions supplied, BooleanSupplier cancelled) {
        cancellation(cancelled);
        if (!expectedTarget.equals(supplied.pin())) fail("STALE_INPUT");
        var pin = supplied.pin();
        if (!pin.logicalDigest().equals(expectedCurrent.logicalDigest()) || !pin.bindingId().equals(expectedCurrent.bindingId())
                || !pin.bindingDigest().equals(expectedCurrent.bindingDigest()) || !pin.documentDigests().equals(expectedCurrent.documentDigests())) fail("STALE_INPUT");
        if (!revision(pin.revisionToken())) fail("INVALID_PIN");
        var projected = new DerivedGraphProjectionAdapter().project(definition, expectedCurrent, current, cancelled);
        if (projected instanceof DerivedGraphProjectionAdapter.Refused refused) fail(refused.code());
        var observed = (DerivedGraphProjectionAdapter.Complete) projected;
        cancellation(cancelled);
        var compiled = new V3TargetIntentCompiler().compile(definition, new GraphValidationResult.Accepted(observed.physical()), supplied.intent());
        cancellation(cancelled);
        if (compiled instanceof TargetCompilationResult.Rejected rejected) {
            if (rejected.codes().stream().allMatch(c -> c.equals("UNRESOLVED_FIELD") || c.equals("UNRESOLVED_REFERENCE"))) {
                var incomplete = unresolved(definition, supplied.intent(), cancelled);
                cancellation(cancelled);
                return new Incomplete(incomplete);
            }
            fail(rejected.codes().getFirst());
        }
        var physical = ((TargetCompilationResult.Expected) compiled).target();
        var input = input(definition, observed.input(), physical, supplied, cancelled);
        var evaluated = new DerivedGraphEngine().evaluate(definition, expectedTarget, input, cancelled);
        if (evaluated instanceof DerivedResult.Refused refused) fail(refused.code());
        if (evaluated instanceof DerivedResult.Incomplete incomplete) return new Incomplete(incomplete.derivations());
        cancellation(cancelled);
        return new Complete(observed, physical, input, (DerivedResult.Complete) evaluated);
    }
    private DerivedInput input(Checked definition, DerivedInput observed, ExpectedTarget physical, Decisions supplied, BooleanSupplier cancelled) {
        var sourceFields = new TreeMap<String, TreeSet<String>>();
        definition.definition().logical().derivations().forEach(d -> sourceFields.computeIfAbsent(d.sourceType(), ignored -> new TreeSet<>()).add(d.sourceField()));
        Map<TargetIntent.Ref.Existing, DerivedInput.Entity> originals = new HashMap<>();
        for (var entity : observed.entities()) {
            cancellation(cancelled);
            originals.put(new TargetIntent.Ref.Existing(((DerivedInput.Ref.Observed)entity.reference()).key()), entity);
        }
        Map<TargetIntent.Ref, TargetIntent.EntityDecision> choices = new HashMap<>();
        supplied.intent().entities().forEach(d -> choices.put(d.entity(), d));
        var entities = new ArrayList<DerivedInput.Entity>();
        for (var entity : physical.entities()) {
            cancellation(cancelled);
            var decision = choices.get(entity.reference());
            Map<String, TargetIntent.FieldValue> fields = decision instanceof TargetIntent.EntityDecision.Retain retain ? retain.fields()
                    : decision instanceof TargetIntent.EntityDecision.Create create ? create.fields() : Map.of();
            Map<String, DerivedInput.FieldState> states = new TreeMap<>();
            for (var field : sourceFields.getOrDefault(entity.reference().type(), new TreeSet<>())) {
                cancellation(cancelled);
                String value = entity.fields().get(field);
                if (value == null) { states.put(field, new DerivedInput.FieldState.Absent()); continue; }
                var choice = decision == null ? new TargetIntent.FieldValue.KeepObserved() : fields.get(field);
                Optional<DerivedInput.Kept> kept = Optional.empty();
                if (choice instanceof TargetIntent.FieldValue.KeepObserved) {
                    var original = originals.get(entity.reference());
                    if (original == null || !(original.fields().get(field) instanceof DerivedInput.FieldState.Present)) fail("INVALID_PROVENANCE");
                    var present = (DerivedInput.FieldState.Present) original.fields().get(field);
                    if (!value.equals(present.text()) || !(present.proof() instanceof DerivedInput.Proof.Observed)) fail("INVALID_PROVENANCE");
                    kept = Optional.of(new DerivedInput.Kept((DerivedInput.Ref.Observed) original.reference(), ((DerivedInput.Proof.Observed)present.proof()).location()));
                } else if (!(choice instanceof TargetIntent.FieldValue.Entered entered) || !value.equals(entered.text())) fail("INVALID_PROVENANCE");
                states.put(field, new DerivedInput.FieldState.Present(value, new DerivedInput.Proof.Target(choice, kept)));
            }
            entities.add(new DerivedInput.Entity(new DerivedInput.Ref.Target(entity.reference()), states));
        }
        var edges = new ArrayList<DerivedInput.Edge>();
        for (var edge : physical.edges()) {
            cancellation(cancelled);
            edges.add(new DerivedInput.Edge(edge.relation(), new DerivedInput.Ref.Target(edge.source()), new DerivedInput.Ref.Target(edge.target())));
        }
        return new DerivedInput(DerivedInput.Kind.TYPED_TARGET, supplied.pin(), entities, edges);
    }
    private List<String> unresolved(Checked definition, TargetIntent intent, BooleanSupplier cancelled) {
        var references = new TreeSet<String>(); var logical = definition.definition().logical();
        for (var decision : intent.entities()) {
            cancellation(cancelled);
            var type = logical.entityTypes().stream().filter(t -> t.id().equals(decision.entity().type())).findFirst().orElse(null);
            if (type == null) continue;
            Map<String, TargetIntent.FieldValue> fields = decision instanceof TargetIntent.EntityDecision.Retain retain ? retain.fields()
                    : decision instanceof TargetIntent.EntityDecision.Create create ? create.fields() : Map.of();
            for (var field : type.fields()) if (fields.get(field.id()) instanceof TargetIntent.FieldValue.Unresolved) {
                var derived = logical.derivations().stream().filter(d -> d.sourceType().equals(type.id()) && d.sourceField().equals(field.id())).toList();
                if (derived.isEmpty()) references.add(type.id() + "/" + field.id()); else derived.forEach(d -> references.add(d.id()));
                if (references.size() > 256) fail("RESOURCE_LIMIT");
            }
            Map<String, TargetIntent.ReferenceValue> relations = decision instanceof TargetIntent.EntityDecision.Retain retain ? retain.references()
                    : decision instanceof TargetIntent.EntityDecision.Create create ? create.references() : Map.of();
            for (var relation : logical.relations()) if (relation.fromType().equals(type.id()) && relations.get(relation.id()) instanceof TargetIntent.ReferenceValue.Unresolved) {
                references.add(relation.id()); if (references.size() > 256) fail("RESOURCE_LIMIT");
            }
        }
        if (references.isEmpty()) fail("INVALID_TARGET");
        return List.copyOf(references);
    }
    private static boolean revision(String token) {
        if (token.isEmpty() || token.length() > 128) return false;
        for (int i = 0; i < token.length();) {
            int cp = token.codePointAt(i); i += Character.charCount(cp);
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xd7ff
                    || cp >= 0xe000 && cp <= 0xfffd || cp >= 0x10000 && cp <= 0x10ffff)) return false;
        }
        return true;
    }
    private static void cancellation(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) fail("CANCELLED"); }
    private static void fail(String code) { throw new Failure(code); }
    private static final class Failure extends RuntimeException {
        private final String code;
        private Failure(String code) { super(null, null, false, false); this.code = code; }
    }
}
