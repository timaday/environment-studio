package studio.environment.core.derived;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import studio.environment.core.definition.DefinitionDraft.Relation;
import studio.environment.core.definitionv2.NativeLexicalRules;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import static studio.environment.core.derived.DerivedInput.*;

/** Consistency checks on adapter-supplied data, never lexical, owner or execution authority. */
public final class DerivedInputValidator {
    private DerivedInputValidator() { }
    public sealed interface Check permits Valid, Incomplete, Refused { }
    public record Valid() implements Check { }
    public record Incomplete(List<String> derivations) implements Check {
        public Incomplete { derivations = derivations.stream().distinct().sorted().toList(); }
    }
    public record Refused(String code) implements Check { }
    public static Check check(NativeCompilationResult.Checked definition, Pin expected, DerivedInput input) {
        if (definition == null || expected == null || input == null) return new Refused("INVALID_INPUT");
        try { return validate(definition, expected, input); }
        catch (Invalid invalid) { return new Refused(invalid.code); }
    }
    private static Check validate(NativeCompilationResult.Checked definition, Pin expected, DerivedInput input) {
        // Checked is data, not proof that this exact declaration/vector passed the compiler.
        var compiled = new NativeDefinitionCompiler().compile(definition.definition());
        if (!(compiled instanceof NativeCompilationResult.Incomplete checked)
                || !checked.checked().equals(definition)
                || checked.diagnostics().stream().anyMatch(d -> !d.code().equals("MECHANISM_UNQUALIFIED")))
            fail("INVALID_DEFINITION");
        if (!expected.equals(input.pin())) fail("STALE_INPUT");
        Binding binding = definition.definition().bindings().stream().filter(b -> b.id().equals(expected.bindingId())).findFirst().orElse(null);
        if (binding == null || !expected.logicalDigest().equals(definition.logicalDigest())
                || !expected.bindingDigest().equals(definition.bindingDigests().get(expected.bindingId()))
                || expected.revisionToken().isEmpty() || expected.revisionToken().length() > 128
                || !xmlText(expected.revisionToken())) fail("INVALID_PIN");
        Map<String, Document> documents = new HashMap<>(); binding.documents().forEach(d -> documents.put(d.id(), d));
        if (!documents.keySet().equals(expected.documentDigests().keySet())
                || expected.documentDigests().values().stream().anyMatch(d -> !d.matches("[0-9a-f]{64}"))) fail("INVALID_PIN");
        var logical = definition.definition().logical();
        Map<String, EntityType> types = new HashMap<>(); logical.entityTypes().forEach(t -> types.put(t.id(), t));
        Map<String, Relation> relations = new HashMap<>(); logical.relations().forEach(r -> relations.put(r.id(), r));
        Set<Ref> references = new HashSet<>(); Set<ObservedGraph.Key> keys = new HashSet<>();
        Set<PhysicalLocation> locations = new HashSet<>(); Set<String> slots = new HashSet<>();
        Set<String> unresolved = new TreeSet<>();
        for (var entity : input.entities()) {
            var ref = entity.reference(); var type = types.get(ref.type());
            if (type == null || !references.add(ref)) fail("INVALID_GRAPH");
            if (input.kind() == Kind.OBSERVED) {
                if (!(ref instanceof Ref.Observed)) fail("INVALID_GRAPH");
                var observed = (Ref.Observed) ref;
                origin(observed, expected, documents);
                if (!keys.add(observed.key()) || !locations.add(new PhysicalLocation(observed.origin().documentId(), observed.origin().elementIndex()))) fail("INVALID_GRAPH");
            } else {
                if (!(ref instanceof Ref.Target)) fail("INVALID_GRAPH");
                var target = ((Ref.Target) ref).reference();
                if (target instanceof TargetIntent.Ref.Existing existing) {
                    if (!key(existing.key()) || !keys.add(existing.key())) fail("INVALID_GRAPH");
                } else if (target instanceof TargetIntent.Ref.Fresh fresh) {
                    if (fresh.slot() == null || !fresh.slot().matches("[a-z][a-z0-9.-]{0,63}") || !slots.add(fresh.slot())) fail("INVALID_GRAPH");
                } else fail("INVALID_GRAPH");
            }
            Map<String, Field> fields = new HashMap<>(); type.fields().forEach(f -> fields.put(f.id(), f));
            if (!fields.keySet().containsAll(entity.fields().keySet())) fail("INVALID_FIELD_STATE");
            for (var derivation : logical.derivations()) {
                if (!derivation.sourceType().equals(type.id())) continue;
                var field = fields.get(derivation.sourceField()); var state = entity.fields().get(field.id());
                if (state == null) fail("MISSING_FIELD_STATE");
                if (state instanceof FieldState.Unresolved) {
                    if (input.kind() == Kind.OBSERVED) fail("INVALID_FIELD_STATE");
                    unresolved.add(derivation.id());
                } else if (state instanceof FieldState.Absent) {
                    if (field.required()) fail("REQUIRED_FIELD_MISSING");
                } else if (state instanceof FieldState.Present present) {
                    if (!xmlText(present.text())) fail("INVALID_FIELD_STATE");
                    proof(ref, field.id(), present, expected, documents);
                }
            }
        }
        Set<Edge> edges = new HashSet<>();
        for (var edge : input.edges()) {
            var relation = relations.get(edge.relation());
            if (relation == null || !references.contains(edge.source()) || !references.contains(edge.target())
                    || !relation.fromType().equals(edge.source().type()) || !relation.toType().equals(edge.target().type())
                    || !edges.add(edge)) fail("INVALID_GRAPH");
        }
        return unresolved.isEmpty() ? new Valid() : new Incomplete(List.copyOf(unresolved));
    }
    private static void proof(Ref ref, String field, FieldState.Present present, Pin expected, Map<String, Document> documents) {
        if (ref instanceof Ref.Observed observed) {
            if (!(present.proof() instanceof Proof.Observed)) fail("INVALID_PROVENANCE");
            location(observed, field, present.text(), ((Proof.Observed) present.proof()).location(), expected, documents);
            return;
        }
        if (!(present.proof() instanceof Proof.Target)) fail("INVALID_PROVENANCE");
        var proof = (Proof.Target) present.proof();
        if (proof.decision() instanceof TargetIntent.FieldValue.Entered entered) {
            if (!present.text().equals(entered.text()) || proof.kept().isPresent()) fail("INVALID_PROVENANCE");
        } else if (proof.decision() instanceof TargetIntent.FieldValue.KeepObserved) {
            var target = ((Ref.Target) ref).reference();
            if (!(target instanceof TargetIntent.Ref.Existing) || proof.kept().isEmpty()) fail("INVALID_PROVENANCE");
            var kept = proof.kept().orElseThrow();
            if (!((TargetIntent.Ref.Existing) target).key().equals(kept.source().key())) fail("INVALID_PROVENANCE");
            origin(kept.source(), expected, documents);
            location(kept.source(), field, present.text(), kept.location(), expected, documents);
        } else fail("INVALID_PROVENANCE");
    }
    private static Projection origin(Ref.Observed ref, Pin expected, Map<String, Document> documents) {
        var origin = ref.origin(); var document = documents.get(origin.documentId());
        if (!key(ref.key()) || document == null || !Objects.equals(expected.documentDigests().get(origin.documentId()), origin.sourceDigest())) fail("INVALID_PROVENANCE");
        var projection = document.entities().stream().filter(p -> p.id().equals(origin.projectionId())).findFirst().orElse(null);
        if (projection == null || !projection.type().equals(ref.type()) || origin.elementIndex() < 0
                || origin.ancestry().size() != projection.path().size() - 1) fail("INVALID_PROVENANCE");
        int previous = -1;
        for (int parent : origin.ancestry()) {
            if (parent <= previous || parent >= origin.elementIndex() || previous == -1 && parent != 0) fail("INVALID_PROVENANCE");
            previous = parent;
        }
        if (origin.ancestry().isEmpty() && origin.elementIndex() != 0) fail("INVALID_PROVENANCE");
        return projection;
    }
    private static boolean key(ObservedGraph.Key key) {
        return key != null && key.type() != null && key.identity() != null && !key.identity().isEmpty() && xmlText(key.identity());
    }
    private static void location(Ref.Observed ref, String field, String text, Location location, Pin expected, Map<String, Document> documents) {
        var projection = origin(ref, expected, documents);
        var mapping = projection.fields().stream().filter(f -> f.field().equals(field)).findFirst().orElse(null);
        if (mapping == null) fail("INVALID_PROVENANCE");
        var value = location.value();
        attribute(ref, value, text);
        if (mapping.locator() instanceof DirectAttribute direct) {
            if (location.selector().isPresent() || value.elementIndex() != ref.origin().elementIndex() || !value.name().equals(direct.attribute())) fail("INVALID_PROVENANCE");
        } else if (mapping.locator() instanceof ChildProperty child) {
            if (location.selector().isEmpty()) fail("INVALID_PROVENANCE");
            var selector = location.selector().orElseThrow(); var discriminator = selector.discriminator();
            attribute(ref, discriminator, child.discriminatorValue());
            if (!selector.element().equals(child.element()) || selector.parentElementIndex() != ref.origin().elementIndex()
                    || value.elementIndex() <= selector.parentElementIndex() || discriminator.elementIndex() != value.elementIndex()
                    || !discriminator.name().equals(child.discriminatorAttribute()) || !value.name().equals(child.valueAttribute())) fail("INVALID_PROVENANCE");
        } else fail("INVALID_PROVENANCE");
    }
    private static void attribute(Ref.Observed ref, AttributePin attribute, String text) {
        if (!attribute.documentId().equals(ref.origin().documentId()) || !attribute.sourceDigest().equals(ref.origin().sourceDigest())
                || attribute.elementIndex() < 0 || !attribute.decodedValue().equals(text)
                || attribute.valueStart() < 0 || attribute.valueEnd() < attribute.valueStart() || attribute.valueEnd() > 1_048_576
                || attribute.quote() != '\'' && attribute.quote() != '"' || !qualifiedName(attribute)) fail("INVALID_PROVENANCE");
    }
    private static boolean qualifiedName(AttributePin attribute) {
        var name = attribute.name(); var qualified = attribute.qualifiedName();
        if (!NativeLexicalRules.validName(name, true)) return false;
        int colon = qualified.indexOf(':');
        if (name.namespaceUri().isEmpty()) return qualified.equals(name.localName());
        if (colon <= 0 || !qualified.substring(colon + 1).equals(name.localName())) return false;
        String prefix = qualified.substring(0, colon);
        return NativeLexicalRules.validName(new ExpandedName("", prefix), false) && !prefix.equals("xmlns")
                && prefix.equals("xml") == name.namespaceUri().equals("http://www.w3.org/XML/1998/namespace");
    }
    // XML 1.0 text is strict Unicode; TEXT's physical lexical rule alone does not check surrogates.
    private static boolean xmlText(String text) {
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xd7ff
                    || cp >= 0xe000 && cp <= 0xfffd || cp >= 0x10000 && cp <= 0x10ffff)) return false;
        }
        return true;
    }
    private record PhysicalLocation(String document, int element) { }
    private static void fail(String code) { throw new Invalid(code); }
    private static final class Invalid extends RuntimeException {
        private final String code;
        private Invalid(String code) { super(null, null, false, false); this.code = code; }
    }
}
