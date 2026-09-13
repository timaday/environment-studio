package studio.environment.server.planning;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import studio.environment.core.Outcome;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.derived.DerivedTargetComparison;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.projection.DerivedGraphProjectionAdapter;
import studio.environment.server.projection.DocumentSource;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlResult;
import static studio.environment.server.planning.PlanningXml.fail;

/** Internal final XML recomputation; no hosted publication or export authority. */
public final class DerivedTargetMaterializer {
    public sealed interface Result permits Complete, Incomplete, Refused { }
    public record Complete(DerivedTargetInputAdapter.Complete preliminary, MaterializationResult.Complete physical,
            Map<TargetIntent.Ref, DerivedInput.Ref.Observed> provenance, DerivedGraphProjectionAdapter.Complete finalProjection) implements Result {
        public Complete {
            Objects.requireNonNull(preliminary); Objects.requireNonNull(physical); Objects.requireNonNull(finalProjection);
            if (provenance.size() > 20_000) throw new IllegalArgumentException("Provenance limit exceeded.");
            provenance = Map.copyOf(provenance);
        }
        @Override public String toString() { return "DerivedMaterializedTarget[redacted]"; }
    }
    public record Incomplete(List<String> references) implements Result {
        public Incomplete {
            if (references.size() > 256) throw new IllegalArgumentException("Incomplete reference limit exceeded.");
            references = List.copyOf(references);
        }
    }
    public record Refused(String code) implements Result { public Refused { Objects.requireNonNull(code); } }
    public Result materialize(Checked definition, DerivedInput.Pin expectedCurrent, DerivedGraphProjectionAdapter.Snapshot current,
            DerivedInput.Pin expectedTarget, DerivedTargetInputAdapter.Decisions supplied, Map<String, Optional<String>> documentBases,
            List<TargetPlacement> placements, BooleanSupplier cancelled) {
        if (definition == null || expectedCurrent == null || current == null || expectedTarget == null || supplied == null
                || documentBases == null || placements == null || cancelled == null) return new Refused("INVALID_INPUT");
        try { return assemble(definition, expectedCurrent, current, expectedTarget, supplied, documentBases, placements, cancelled); }
        catch (PlanningXml.Refusal refused) { return new Refused(refused.code); }
    }
    private Result assemble(Checked definition, DerivedInput.Pin expectedCurrent, DerivedGraphProjectionAdapter.Snapshot current,
            DerivedInput.Pin expectedTarget, DerivedTargetInputAdapter.Decisions supplied, Map<String, Optional<String>> documentBases,
            List<TargetPlacement> placements, BooleanSupplier cancelled) {
        cancellation(cancelled);
        if (documentBases.size() > 128 || placements.size() > 20_000) fail("RESOURCE_LIMIT");
        if (!documentBases.keySet().equals(expectedCurrent.documentDigests().keySet()) || documentBases.values().stream().anyMatch(Objects::isNull)) fail("INVENTORY_MISMATCH");
        var prepared = new DerivedTargetInputAdapter().prepare(definition, expectedCurrent, current, expectedTarget, supplied, cancelled);
        if (prepared instanceof DerivedTargetInputAdapter.Refused refused) fail(refused.code());
        if (prepared instanceof DerivedTargetInputAdapter.Incomplete incomplete) return new Incomplete(incomplete.references());
        var preliminary = (DerivedTargetInputAdapter.Complete) prepared;
        if (preliminary.derived().rules().stream().anyMatch(r -> r.outcome() != Outcome.PASS)) fail("DERIVED_RULE_FAILED");
        var sources = new ArrayList<TargetSource>();
        for (var source : current.documents()) sources.add(new TargetSource(source.documentId(), source.source(), documentBases.get(source.documentId())));
        var logical = definition.definition().logical();
        var physicalLogical = new NativeDefinition.Logical(logical.entityTypes(), logical.relations(), logical.rules(), logical.operationCapabilities());
        var binding = definition.definition().bindings().stream().filter(b -> b.id().equals(supplied.pin().bindingId())).findFirst().orElseThrow();
        var assembled = new PhysicalTargetMaterializer().materialize(physicalLogical, binding, sources, supplied.intent(), placements,
                documents -> candidate(definition, supplied.pin(), documents, cancelled).physical(),
                (observed, intent) -> {
                    if (!observed.equals(preliminary.current().physical()) || !intent.equals(supplied.intent())) fail("STALE_INPUT");
                    return preliminary.physical();
                }, cancelled);
        cancellation(cancelled);
        var finalProjection = candidate(definition, supplied.pin(), assembled.physical().documents(), cancelled);
        if (!finalProjection.physical().equals(assembled.physical().graph())) fail("SEMANTIC_OUTCOME_MISMATCH");
        Map<TargetIntent.Ref, DerivedInput.Ref.Observed> provenance = new HashMap<>();
        assembled.provenance().forEach((ref, entity) -> provenance.put(ref, new DerivedInput.Ref.Observed(entity.key(), entity.origin())));
        var compared = new DerivedTargetComparison().compare(definition, expectedTarget, preliminary.input(), preliminary.derived(),
                finalProjection.input().pin(), finalProjection.input(), finalProjection.derived(), provenance, cancelled);
        if (compared instanceof DerivedTargetComparison.Refused refused) fail(refused.code());
        cancellation(cancelled);
        return new Complete(preliminary, assembled.physical(), provenance, finalProjection);
    }
    private DerivedGraphProjectionAdapter.Complete candidate(Checked definition, DerivedInput.Pin target,
            List<TargetSource> sources, BooleanSupplier cancelled) {
        // These are newly assembled source fingerprints, not substitutes for original snapshot authority.
        var digests = new TreeMap<String, String>(); var documents = new ArrayList<DocumentSource>();
        long bytes = 0; var xml = new LosslessXmlAdapter();
        for (var source : sources) {
            cancellation(cancelled);
            if (source.source().length() > PlanningXml.MAX_CHARS) fail("RESOURCE_LIMIT");
            bytes += PlanningXml.utf8(source.source()); if (bytes > PlanningXml.MAX_BYTES) fail("RESOURCE_LIMIT");
            var parsed = xml.project(source.source());
            if (parsed instanceof XmlResult.Rejected refused) fail(refused.diagnostics().getFirst().code());
            if (digests.putIfAbsent(source.documentId(), ((XmlResult.Accepted) parsed).document().digest()) != null) fail("INVENTORY_MISMATCH");
            documents.add(new DocumentSource(source.documentId(), source.source()));
        }
        var pin = new DerivedInput.Pin(target.revisionToken(), target.logicalDigest(), target.bindingId(), target.bindingDigest(), digests);
        var snapshot = new DerivedGraphProjectionAdapter.Snapshot(target.revisionToken(), target.logicalDigest(), target.bindingId(), target.bindingDigest(), documents);
        var projected = new DerivedGraphProjectionAdapter().project(definition, pin, snapshot, cancelled);
        if (projected instanceof DerivedGraphProjectionAdapter.Refused refused) fail(refused.code());
        return (DerivedGraphProjectionAdapter.Complete) projected;
    }
    private static void cancellation(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) fail("CANCELLED"); }
}
