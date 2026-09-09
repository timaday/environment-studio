package studio.environment.server.plan;

import java.nio.charset.StandardCharsets;
import java.util.*;
import studio.environment.core.graph.*;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.planning.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.server.planning.*;
import studio.environment.server.profile.ProfileBytesAdapter;
import studio.environment.server.projection.*;

/** Qualified adapters supply exact sources; independent projection establishes graph/provenance each time. */
public final class PlanContentAdapter implements ContentAdapter {
    private final GraphProjectionAdapter projection = new GraphProjectionAdapter();
    private final StructuralTargetAdapter targets = new StructuralTargetAdapter();
    private final ProfileBytesAdapter profiles = new ProfileBytesAdapter();
    @Override public ContentResult project(PublishedDefinition definition,String binding,ObservationResult.Observation observation) {
        var result=projection.project(definition.compiled(),binding,observation.documents().stream().map(document->new DocumentSource(document.documentId(),document.xml())).toList());
        if(!(result instanceof ProjectionResult.Accepted accepted)) return rejected("PROJECTION_REFUSED");
        if(!accepted.logicalDigest().equals(observation.logicalDigest()) || !accepted.bindingDigest().equals(observation.bindingDigest())) return rejected("PROJECTION_PIN_MISMATCH");
        var supplied=new HashMap<String,String>(); observation.documents().forEach(document->supplied.put(document.documentId(),document.sourceDigest()));
        if(accepted.projection().documents().stream().anyMatch(document->!document.digest().equals(supplied.get(document.documentId())))) return rejected("SOURCE_DIGEST_MISMATCH");
        var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>(); accepted.graph().entities().forEach(entity->provenance.put(entity.key(),new TargetIntent.Ref.Existing(entity.key())));
        return new ContentResult.Complete(content(accepted,provenance));
    }
    @Override public ContentResult materialize(PublishedDefinition definition,String binding,Content current,Draft draft) {
        var sources=current.sources().stream().map(source->new TargetSource(source.documentId(),source.xml(),Optional.empty())).toList();
        var placements=draft.placements().stream().map(placement->new TargetPlacement(placement.entity(),placement.documentId(),placement.projectionId(),switch(placement.parent()) {
            case Parent.Existing old -> new TargetPlacement.Parent.Existing(old.documentId(),old.sourceDigest(),old.elementIndex());
            case Parent.Created fresh -> new TargetPlacement.Parent.Created(fresh.entity());
        })).toList();
        var materialized=targets.materialize(definition.compiled(),binding,sources,draft.intent(),placements);
        if(materialized instanceof MaterializationResult.Rejected refused) return new ContentResult.Rejected(refused.codes());
        var complete=(MaterializationResult.Complete)materialized;
        var independent=projection.project(definition.compiled(),binding,complete.documents().stream().map(source->new DocumentSource(source.documentId(),source.source())).toList());
        if(!(independent instanceof ProjectionResult.Accepted projected) || !projected.graph().equals(complete.graph())) return rejected("TARGET_REPROJECTION_MISMATCH");
        var expected=new TargetIntentCompiler().compile(definition.compiled(),new GraphValidationResult.Accepted(current.graph()),draft.intent());
        if(!(expected instanceof TargetCompilationResult.Expected checked)) return rejected("EXPECTED_TARGET_REFUSED");
        var provenance=new HashMap<ObservedGraph.Key,TargetIntent.Ref>();
        for(var entity:checked.target().entities()) if(provenance.putIfAbsent(entity.identity(),entity.reference())!=null) return rejected("AMBIGUOUS_PROVENANCE");
        if(provenance.size()!=projected.graph().entities().size() || projected.graph().entities().stream().anyMatch(entity->!provenance.containsKey(entity.key()))) return rejected("TARGET_PROVENANCE_MISMATCH");
        return new ContentResult.Complete(content(projected,provenance));
    }
    @Override public Capture capture(PublishedDefinition definition,String binding,Content current,ProfileCapture.Command command) {
        var projected=projection.project(definition.compiled(),binding,current.sources().stream().map(source->new DocumentSource(source.documentId(),source.xml())).toList());
        if(!(projected instanceof ProjectionResult.Accepted observation) || !observation.graph().equals(current.graph())) throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
        var result=profiles.capture(definition.compiled(),observation,command);
        if(!(result instanceof ProfileBytesAdapter.Result.Accepted accepted)) throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
        var encoded=profiles.write(definition.compiled(),accepted.checked());
        if(!(encoded instanceof ProfileBytesAdapter.ExportResult.Encoded bytes)) throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
        return new Capture(new String(bytes.bytes(),StandardCharsets.UTF_8),accepted.checked());
    }
    @Override public DocumentView compare(studio.environment.core.plan.HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode) {
        return PlanDocumentViews.render(snapshot,target,documentId,mode);
    }
    private static Content content(ProjectionResult.Accepted projection,Map<ObservedGraph.Key,TargetIntent.Ref> provenance) {
        return new Content(projection.projection().documents().stream().map(document->new Source(document.documentId(),document.source(),document.digest())).toList(),projection.graph(),provenance);
    }
    private static ContentResult.Rejected rejected(String code) { return new ContentResult.Rejected(List.of(code)); }
}
