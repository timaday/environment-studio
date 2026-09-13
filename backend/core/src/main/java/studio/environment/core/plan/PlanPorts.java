package studio.environment.core.plan;

import java.util.*;
import java.util.function.Supplier;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.observation.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.profile.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.NativeCommand;

/** Trusted composition ports. No incoming HTTP value may substitute for these authorities. */
public final class PlanPorts {
    private PlanPorts() { }
    public interface Authority { <T> Optional<T> guard(SessionLedger.Lease lease, Supplier<T> transition); }
    public interface Workspace {
        PublishedDefinition definition(Owner owner, NativeCommand.Reference reference);
        default PublishedDefinition definitionV3(Owner owner, NativeCommand.Reference reference) {
            throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        }
        PublishedProfile profile(Owner owner, NativeCommand.Reference reference, PublishedDefinition definition);
        default PublishedProfile profileV3(Owner owner, NativeCommand.Reference reference, PublishedDefinition definition) {
            throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        }
    }
    public record PublishedDefinition(NativeCommand.Reference reference, String publicationDigest,
            PlanDefinition model, List<NativeCommand.Policy> policies) {
        public PublishedDefinition { Objects.requireNonNull(model); policies = List.copyOf(policies); }
        public PublishedDefinition(NativeCommand.Reference reference, String publicationDigest,
                ReadyToPublish compiled, List<NativeCommand.Policy> policies) {
            this(reference, publicationDigest, new PlanDefinition.V2(compiled), policies);
        }
        /** Legacy v2 access is closed; checked v3 metadata cannot manufacture readiness. */
        public ReadyToPublish compiled() {
            return switch (model) {
                case PlanDefinition.V2 v2 -> v2.ready();
                case PlanDefinition.V3 ignored -> throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
            };
        }
        @Override public String toString() { return "PublishedDefinitionPin[redacted]"; }
    }
    public record PublishedProfile(NativeCommand.Reference reference, String publicationDigest, ProfileResult.Checked checked) {
        @Override public String toString() { return "PublishedProfilePin[redacted]"; }
    }
    public record Destination(String id, Engine engine, ObservationPort observations) { }
    public record Source(String documentId, String xml, String digest) {
        @Override public String toString() { return "PlanSource[redacted]"; }
    }
    public record Content(List<Source> sources, ObservedGraph graph, Map<ObservedGraph.Key,TargetIntent.Ref> provenance,
            PlanContentEvidence evidence) {
        public Content { sources = List.copyOf(sources); provenance = Map.copyOf(provenance); Objects.requireNonNull(evidence); }
        public Content(List<Source> sources, ObservedGraph graph, Map<ObservedGraph.Key,TargetIntent.Ref> provenance) {
            this(sources, graph, provenance, new PlanContentEvidence.V2());
        }
        @Override public String toString() { return "PlanContent[redacted]"; }
    }
    public sealed interface Parent {
        record Existing(String documentId, String sourceDigest, int elementIndex) implements Parent { }
        record Created(TargetIntent.Ref.Fresh entity) implements Parent { }
    }
    public record Placement(TargetIntent.Ref entity, String documentId, String projectionId, Parent parent) {
        @Override public String toString() { return "Placement[redacted]"; }
    }
    public record Draft(TargetIntent intent, List<Placement> placements) {
        public Draft { if (placements.size() > 20_000) throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT); placements = List.copyOf(placements); }
        public static Draft empty() { return new Draft(new TargetIntent(List.of(), List.of()), List.of()); }
        @Override public String toString() { return "PlanDraft[redacted]"; }
    }
    public sealed interface ContentResult {
        record Complete(Content content) implements ContentResult { }
        record Rejected(List<String> codes) implements ContentResult { public Rejected { codes = List.copyOf(codes); } }
    }
    public interface ContentAdapter {
        default void verifyV3(HostedPlanService.ViewSnapshot snapshot, boolean target, ObservationPort.Cancellation cancellation) {
            throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        }
        ContentResult project(PublishedDefinition definition, String binding, ObservationResult.Observation observation);
        default ContentResult project(PublishedDefinition definition, String binding, ObservationResult.Observation observation, ObservationPort.Cancellation cancellation) {
            if(definition.model() instanceof PlanDefinition.V3) return new ContentResult.Rejected(List.of("UNSUPPORTED_DEFINITION"));
            return project(definition,binding,observation);
        }
        ContentResult materialize(PublishedDefinition definition, String binding, Content current, Draft draft);
        default V3PlanContent.Result materializeV3(PublishedDefinition definition, studio.environment.core.derived.DerivedInput.Pin expectedCurrent,
                Content current, studio.environment.core.derived.DerivedInput.Pin expectedTarget, Draft draft, ObservationPort.Cancellation cancellation) {
            return new V3PlanContent.Result.Refused("UNSUPPORTED_DEFINITION");
        }
        Capture capture(PublishedDefinition definition, String binding, Content current, ProfileCapture.Command command);
        default Capture captureV3(PublishedDefinition definition,studio.environment.core.derived.DerivedInput.Pin expected,Content current,ProfileCapture.Command command,ObservationPort.Cancellation cancellation) {
            throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        }
        default DocumentView compare(HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode) {
            throw new PlanRefusal(PlanRefusal.Code.DISCLOSURE_REQUIRED);
        }
        default DocumentView compare(HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode,ObservationPort.Cancellation cancellation) {
            if(snapshot.definition().model() instanceof PlanDefinition.V3) throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
            return compare(snapshot,target,documentId,mode);
        }
    }
    public enum ViewMode { RAW, PLACEHOLDERS, FORMATTED }
    public record DocumentView(String documentId,ViewMode mode,String text,boolean exact,
            boolean redacted,boolean unmappedConcreteMayRemain,List<String> omissions) {
        public DocumentView { omissions=List.copyOf(omissions); }
        @Override public String toString() { return "DocumentView[redacted]"; }
    }
    public record Capture(String source, ProfileResult.Checked checked) {
        @Override public String toString() { return "PortableCapture[redacted]"; }
    }
    /** Adapter fills only these bounded, owned buffers. It must reject additional input and unsupported auth. */
    public interface CredentialReader { CredentialLengths read(char[] username, char[] password); }
    public record CredentialLengths(int username, int password) { }
}
