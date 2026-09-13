package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.projection.DerivedGraphProjectionAdapter;
import studio.environment.server.projection.DocumentSource;
import studio.environment.server.planning.DerivedTargetInputAdapter;
import studio.environment.server.planning.DerivedTargetMaterializer;
import studio.environment.server.planning.TargetPlacement;

/** Internal complete v3 projection/target evidence. No hosted admission is wired here. */
public final class V3PlanContentAdapter implements V3PlanContent {
    @Override public Result project(PlanDefinition.V3 definition, String binding, ObservationResult.Observation observation, Cancellation cancellation) {
        if (definition == null || binding == null || observation == null || cancellation == null) return refused("INVALID_INPUT");
        try {
            live(cancellation);
            var checked=definition.checked();
            if (!digest(observation.fingerprint()) || !Objects.equals(checked.logicalDigest(),observation.logicalDigest())
                    || !Objects.equals(checked.bindingDigests().get(binding),observation.bindingDigest())) fail("INVALID_PIN");
            var selected=checked.definition().bindings().stream().filter(b->b.id().equals(binding)).findFirst().orElse(null);
            if (selected == null) fail("INVALID_PIN");
            if (observation.documents().size()>128) fail("RESOURCE_LIMIT");
            var declarations=new HashMap<String,studio.environment.core.definitionv2.NativeDefinition.Document>();
            for(var document:selected.documents()) if(declarations.putIfAbsent(document.id(),document)!=null) fail("INVALID_DEFINITION");
            if (observation.documents().size()!=declarations.size()) fail("INVENTORY_MISMATCH");
            var digests=new TreeMap<String,String>();var sources=new ArrayList<DocumentSource>();long bytes=0;
            for(var document:observation.documents()) {
                live(cancellation);
                if(document==null || document.documentId()==null || document.xml()==null || document.key()==null) fail("INVALID_INPUT");
                var declared=declarations.get(document.documentId());
                if(declared==null || digests.containsKey(document.documentId())
                        || !selected.keyType().name().toLowerCase(Locale.ROOT).equals(document.key().type())
                        || !declared.key().equals(document.key().value())) fail("INVENTORY_MISMATCH");
                if(document.xml().length()>1_048_576) fail("RESOURCE_LIMIT");
                long measured=utf8(document.xml(),cancellation);bytes+=measured;
                if(bytes>16L*1024*1024) fail("RESOURCE_LIMIT");
                if(document.utf8Bytes()!=measured || document.characters()!=document.xml().length()) fail("SOURCE_METADATA_MISMATCH");
                if(!digest(document.sourceDigest())) fail("INVALID_PIN");
                digests.put(document.documentId(),document.sourceDigest());sources.add(new DocumentSource(document.documentId(),document.xml()));
            }
            var pin=new DerivedInput.Pin(observation.fingerprint(),checked.logicalDigest(),binding,observation.bindingDigest(),digests);
            var snapshot=new DerivedGraphProjectionAdapter.Snapshot(pin.revisionToken(),pin.logicalDigest(),binding,pin.bindingDigest(),sources);
            var projected=new DerivedGraphProjectionAdapter().project(checked,pin,snapshot,cancellation::cancelled);
            if(projected instanceof DerivedGraphProjectionAdapter.Refused rejected) return refused(rejected.code());
            var complete=(DerivedGraphProjectionAdapter.Complete)projected;
            var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();
            for(var entity:complete.physical().entities()) if(provenance.putIfAbsent(entity.key(),new TargetIntent.Ref.Existing(entity.key()))!=null) fail("PROJECTION_MISMATCH");
            var evidence=new PlanContentEvidence.V3Observed(observation.fingerprint(),complete.input(),complete.derived());
            var content=new PlanPorts.Content(complete.sources().documents().stream().map(d->new PlanPorts.Source(d.documentId(),d.source(),d.digest())).toList(),complete.physical(),provenance,evidence);
            live(cancellation);return new Result.Complete(content);
        } catch(Failure failure) { return refused(failure.code); }
    }
    @Override public Result materialize(PlanDefinition.V3 definition, DerivedInput.Pin expectedCurrent, PlanPorts.Content original,
            DerivedInput.Pin expectedTarget, PlanPorts.Draft draft, Cancellation cancellation) {
        if(definition==null || expectedCurrent==null || original==null || expectedTarget==null || draft==null || cancellation==null) return refused("INVALID_INPUT");
        try {
            live(cancellation);
            if(!(original.evidence() instanceof PlanContentEvidence.V3Observed observed)) fail("UNSUPPORTED_CONTENT");
            var observed=(PlanContentEvidence.V3Observed)original.evidence();
            if(!digest(observed.observationFingerprint()) || !observed.observationFingerprint().equals(expectedCurrent.revisionToken())
                    || !observed.input().pin().equals(expectedCurrent) || observed.input().kind()!=DerivedInput.Kind.OBSERVED) fail("STALE_INPUT");
            if(expectedTarget.revisionToken().equals(expectedCurrent.revisionToken())
                    || !expectedTarget.logicalDigest().equals(expectedCurrent.logicalDigest())
                    || !expectedTarget.bindingId().equals(expectedCurrent.bindingId())
                    || !expectedTarget.bindingDigest().equals(expectedCurrent.bindingDigest())
                    || !expectedTarget.documentDigests().equals(expectedCurrent.documentDigests())) fail("STALE_INPUT");
            if(original.graph()==null || original.sources().size()>128) fail("INVALID_INPUT");
            var sources=new ArrayList<DocumentSource>();var supplied=new HashSet<String>();
            for(var source:original.sources()) {
                live(cancellation);
                if(source.documentId()==null || source.xml()==null || !Objects.equals(source.digest(),expectedCurrent.documentDigests().get(source.documentId()))
                        || !supplied.add(source.documentId())) fail("STALE_INPUT");
                sources.add(new DocumentSource(source.documentId(),source.xml()));
            }
            if(!supplied.equals(expectedCurrent.documentDigests().keySet())) fail("INVENTORY_MISMATCH");
            var snapshot=new DerivedGraphProjectionAdapter.Snapshot(expectedCurrent.revisionToken(),expectedCurrent.logicalDigest(),expectedCurrent.bindingId(),expectedCurrent.bindingDigest(),sources);
            var reprojected=new DerivedGraphProjectionAdapter().project(definition.checked(),expectedCurrent,snapshot,cancellation::cancelled);
            if(reprojected instanceof DerivedGraphProjectionAdapter.Refused rejected) return refused(rejected.code());
            var verified=(DerivedGraphProjectionAdapter.Complete)reprojected;
            originalMatches(original,observed,verified,cancellation);
            var bases=new HashMap<String,Optional<String>>();supplied.forEach(id->bases.put(id,Optional.empty()));
            var placements=draft.placements().stream().map(p->new TargetPlacement(p.entity(),p.documentId(),p.projectionId(),switch(p.parent()) {
                case PlanPorts.Parent.Existing old -> new TargetPlacement.Parent.Existing(old.documentId(),old.sourceDigest(),old.elementIndex());
                case PlanPorts.Parent.Created fresh -> new TargetPlacement.Parent.Created(fresh.entity());
            })).toList();
            var materialized=new DerivedTargetMaterializer().materialize(definition.checked(),expectedCurrent,snapshot,expectedTarget,
                    new DerivedTargetInputAdapter.Decisions(expectedTarget,draft.intent()),bases,placements,cancellation::cancelled);
            live(cancellation);
            if(materialized instanceof DerivedTargetMaterializer.Refused rejected) return refused(rejected.code());
            if(materialized instanceof DerivedTargetMaterializer.Incomplete incomplete) return new Result.Incomplete(incomplete.references());
            var complete=(DerivedTargetMaterializer.Complete)materialized;
            originalMatches(original,observed,complete.preliminary().current(),cancellation);
            if(!complete.preliminary().input().pin().equals(expectedTarget)
                    || complete.preliminary().input().kind()!=DerivedInput.Kind.TYPED_TARGET
                    || !complete.preliminary().derived().graph().pin().equals(expectedTarget)) fail("TARGET_PIN_MISMATCH");
            var finalProjection=complete.finalProjection();var finalPin=finalProjection.input().pin();
            if(finalProjection.input().kind()!=DerivedInput.Kind.OBSERVED || !finalProjection.physical().equals(complete.physical().graph())
                    || !finalPin.revisionToken().equals(expectedTarget.revisionToken()) || !finalPin.logicalDigest().equals(expectedTarget.logicalDigest())
                    || !finalPin.bindingId().equals(expectedTarget.bindingId()) || !finalPin.bindingDigest().equals(expectedTarget.bindingDigest())
                    || !finalProjection.derived().graph().pin().equals(finalPin)) fail("TARGET_PIN_MISMATCH");
            var entities=new HashMap<ObservedGraph.Key,ObservedGraph.Entity>();
            for(var entity:finalProjection.physical().entities()) if(entities.putIfAbsent(entity.key(),entity)!=null) fail("TARGET_PROVENANCE_MISMATCH");
            var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();var refs=new HashSet<TargetIntent.Ref>();
            for(var entry:complete.provenance().entrySet()) {
                live(cancellation);var ref=entry.getKey();var actual=entry.getValue();var entity=entities.get(actual.key());
                if(entity==null || !ref.type().equals(actual.key().type()) || !entity.origin().equals(actual.origin())
                        || provenance.putIfAbsent(actual.key(),ref)!=null || !refs.add(ref)) fail("TARGET_PROVENANCE_MISMATCH");
            }
            if(!provenance.keySet().equals(entities.keySet())) fail("TARGET_PROVENANCE_MISMATCH");
            var evidence=new PlanContentEvidence.V3Target(observed.observationFingerprint(),expectedCurrent,complete.preliminary().physical(),
                    complete.preliminary().input(),complete.preliminary().derived(),finalProjection.input(),finalProjection.derived());
            var result=new PlanPorts.Content(finalProjection.sources().documents().stream().map(d->new PlanPorts.Source(d.documentId(),d.source(),d.digest())).toList(),finalProjection.physical(),provenance,evidence);
            live(cancellation);return new Result.Complete(result);
        } catch(Failure failure) { return refused(failure.code); }
    }
    private static void originalMatches(PlanPorts.Content original,PlanContentEvidence.V3Observed evidence,
            DerivedGraphProjectionAdapter.Complete actual,Cancellation cancellation) {
        live(cancellation);
        if(!actual.physical().equals(original.graph()) || !actual.input().equals(evidence.input()) || !actual.derived().equals(evidence.derived())
                || !actual.sources().documents().stream().map(d->new PlanPorts.Source(d.documentId(),d.source(),d.digest())).toList().equals(original.sources())) fail("STALE_CONTENT");
        var expected=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();
        for(var entity:actual.physical().entities()) {
            live(cancellation);
            if(expected.putIfAbsent(entity.key(),new TargetIntent.Ref.Existing(entity.key()))!=null) fail("STALE_CONTENT");
        }
        if(!expected.equals(original.provenance())) fail("STALE_CONTENT");
    }
    private static long utf8(String value,Cancellation cancellation) {
        long bytes=0;
        for(int i=0;i<value.length();i++) {
            if((i&1023)==0) live(cancellation);
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(++i==value.length() || !Character.isLowSurrogate(value.charAt(i))) fail("INVALID_UNICODE");
                bytes+=4;
            } else if(Character.isLowSurrogate(c)) fail("INVALID_UNICODE");
            else bytes+=c<0x80?1:c<0x800?2:3;
        }
        return bytes;
    }
    private static boolean digest(String value) { return value!=null && value.matches("[0-9a-f]{64}"); }
    private static void live(Cancellation cancellation) { if(cancellation.cancelled()) fail("CANCELLED"); }
    private static Result.Refused refused(String code) { return new Result.Refused(code); }
    private static void fail(String code) { throw new Failure(code); }
    private static final class Failure extends RuntimeException {
        final String code;
        Failure(String code) { super(null,null,false,false);this.code=code; }
    }
}
