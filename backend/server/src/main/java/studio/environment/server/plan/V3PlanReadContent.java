package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.projection.*;

/** Fresh complete source comparison; immutable plan pins still require their live owning admission. */
final class V3PlanReadContent {
    static void verify(HostedPlanService.ViewSnapshot snapshot,boolean target,Cancellation cancellation) {
        live(cancellation);
        if(!(snapshot.definition().model() instanceof PlanDefinition.V3 model)) throw refused();
        var pins=snapshot.v3Pins().orElseThrow(V3PlanReadContent::refused);
        var original=snapshot.selected(false);
        if(!pins.original().bindingId().equals(snapshot.binding()))throw refused();
        original(model,pins.original(),original,cancellation);
        if(target) {
            var retained=snapshot.selected(true);
            var result=new V3PlanContentAdapter().materialize(model,pins.original(),original,pins.decisions(),snapshot.draft(),cancellation);
            live(cancellation);
            if(!(result instanceof V3PlanContent.Result.Complete complete) || !complete.content().equals(retained)) throw refused();
        }
        live(cancellation);
    }
    static void original(PlanDefinition.V3 model,DerivedInput.Pin expected,PlanPorts.Content original,Cancellation cancellation) {
        live(cancellation);
        if(!(original.evidence() instanceof PlanContentEvidence.V3Observed evidence)
                || !evidence.observationFingerprint().equals(expected.revisionToken())
                || evidence.input().kind()!=DerivedInput.Kind.OBSERVED || !evidence.input().pin().equals(expected)) throw refused();
        var input=new DerivedGraphProjectionAdapter.Snapshot(expected.revisionToken(),expected.logicalDigest(),expected.bindingId(),expected.bindingDigest(),
                original.sources().stream().map(s->new DocumentSource(s.documentId(),s.xml())).toList());
        var projected=new DerivedGraphProjectionAdapter().project(model.checked(),expected,input,cancellation::cancelled);
        live(cancellation);
        if(!(projected instanceof DerivedGraphProjectionAdapter.Complete actual)
                || !actual.physical().equals(original.graph()) || !actual.input().equals(evidence.input()) || !actual.derived().equals(evidence.derived())
                || !actual.sources().documents().stream().map(d->new PlanPorts.Source(d.documentId(),d.source(),d.digest())).toList().equals(original.sources())) throw refused();
        var provenance=new HashMap<studio.environment.core.graph.ObservedGraph.Key,TargetIntent.Ref>();
        for(var entity:actual.physical().entities()) {
            live(cancellation);if(provenance.putIfAbsent(entity.key(),new TargetIntent.Ref.Existing(entity.key()))!=null) throw refused();
        }
        if(!provenance.equals(original.provenance())) throw refused();
        live(cancellation);
    }
    static void live(Cancellation cancellation){if(cancellation.cancelled())throw new PlanRefusal(PlanRefusal.Code.CANCELLED);}
    private static PlanRefusal refused(){return new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);}
}
