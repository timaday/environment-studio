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
        PublishedProfile profile(Owner owner, NativeCommand.Reference reference, PublishedDefinition definition);
    }
    public record PublishedDefinition(NativeCommand.Reference reference, String publicationDigest,
            ReadyToPublish compiled, List<NativeCommand.Policy> policies) {
        public PublishedDefinition { policies = List.copyOf(policies); }
        @Override public String toString() { return "PublishedDefinitionPin[redacted]"; }
    }
    public record PublishedProfile(NativeCommand.Reference reference, String publicationDigest, ProfileResult.Checked checked) {
        @Override public String toString() { return "PublishedProfilePin[redacted]"; }
    }
    public record Destination(String id, Engine engine, ObservationPort observations) { }
    public record Source(String documentId, String xml, String digest) {
        @Override public String toString() { return "PlanSource[redacted]"; }
    }
    public record Content(List<Source> sources, ObservedGraph graph, Map<ObservedGraph.Key,TargetIntent.Ref> provenance) {
        public Content { sources = List.copyOf(sources); provenance = Map.copyOf(provenance); }
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
        ContentResult project(PublishedDefinition definition, String binding, ObservationResult.Observation observation);
        ContentResult materialize(PublishedDefinition definition, String binding, Content current, Draft draft);
        Capture capture(PublishedDefinition definition, String binding, Content current, ProfileCapture.Command command);
        default DocumentView compare(HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode) {
            throw new PlanRefusal(PlanRefusal.Code.DISCLOSURE_REQUIRED);
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
