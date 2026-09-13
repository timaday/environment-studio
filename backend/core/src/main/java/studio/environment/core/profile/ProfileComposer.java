package studio.environment.core.profile;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import studio.environment.core.definition.DefinitionDraft.RelationKind;
import java.util.Set;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;

/** Preview and composition proposals, never a target plan or export authority. */
public final class ProfileComposer {
    public record Dependency(String slot, String causedBy, String relation, Reason reason) { }
    public enum Reason { REQUIRED_REFERENCE, CONTAINMENT_PARENT, DECLARED_REUSE_TARGET }
    public record Conflict(String code, String slot, String relation) { }
    public record Preview(String profileId, BigInteger revision, String contentDigest, String logicalDigest,
            List<String> selected, List<Profile.Entity> included, List<Dependency> dependencies,
            List<Profile.Relation> relations, List<Conflict> conflicts) {
        public Preview { Profile.bound(selected.size(), Profile.MAX_ENTITIES); Profile.bound(included.size(), Profile.MAX_ENTITIES); Profile.bound(dependencies.size(), Profile.MAX_RELATIONS); Profile.bound(relations.size(), Profile.MAX_RELATIONS); Profile.bound(conflicts.size(), Profile.MAX_RELATIONS); selected = List.copyOf(selected); included = List.copyOf(included); dependencies = List.copyOf(dependencies);
            relations = List.copyOf(relations); conflicts = List.copyOf(conflicts); }
        @Override public String toString() { return "ProfilePreview[redacted]"; }
    }
    public sealed interface PreviewResult {
        record Proposed(Preview preview) implements PreviewResult { }
        record Rejected(List<ProfileResult.Diagnostic> diagnostics) implements PreviewResult {
            public Rejected { Profile.bound(diagnostics.size(), 256); diagnostics = List.copyOf(diagnostics); }
        }
    }
    public sealed interface Decision {
        String slot();
        record Create(String slot, String targetSlot) implements Decision { }
        record UseExisting(String slot, ObservedGraph.Key target) implements Decision {
            @Override public String toString() { return "UseExisting[redacted]"; }
        }
        record Cancel(String slot) implements Decision { }
    }
    public sealed interface Target {
        record New(String slot, String type) implements Target { }
        record Existing(ObservedGraph.Key key) implements Target {
            @Override public String toString() { return "ExistingTarget[redacted]"; }
        }
    }
    public record RelationProposal(String relation, Target from, Target to) {
        @Override public String toString() { return "RelationProposal[redacted]"; }
    }
    public record UnresolvedFields(Target target, List<String> fields) {
        public UnresolvedFields { Profile.bound(fields.size(), 20_000); fields = List.copyOf(fields); }
        @Override public String toString() { return "UnresolvedFields[redacted]"; }
    }
    public record Draft(List<Target.New> additions, List<ObservedGraph.Key> retainedExisting,
            List<ObservedGraph.Edge> retainedRelations, List<RelationProposal> relationProposals, List<UnresolvedFields> unresolvedFields) {
        public Draft { Profile.bound(additions.size(), Profile.MAX_ENTITIES); Profile.bound(retainedExisting.size(), Profile.MAX_ENTITIES); Profile.bound(retainedRelations.size(), Profile.MAX_RELATIONS); Profile.bound(relationProposals.size(), Profile.MAX_RELATIONS); Profile.bound(unresolvedFields.size(), Profile.MAX_ENTITIES); additions = List.copyOf(additions); retainedExisting = List.copyOf(retainedExisting); retainedRelations = List.copyOf(retainedRelations);
            relationProposals = List.copyOf(relationProposals); unresolvedFields = List.copyOf(unresolvedFields); }
        @Override public String toString() { return "CompositionDraft[redacted]"; }
    }
    public sealed interface CompositionResult {
        record Prepared(Draft draft) implements CompositionResult { }
        record NeedsResolution(Draft draft, List<Conflict> conflicts) implements CompositionResult {
            public NeedsResolution { Profile.bound(conflicts.size(), 256); conflicts = List.copyOf(conflicts); }
        }
        record Rejected(List<ProfileResult.Diagnostic> diagnostics) implements CompositionResult {
            public Rejected { Profile.bound(diagnostics.size(), 256); diagnostics = List.copyOf(diagnostics); }
        }
        record Cancelled() implements CompositionResult { }
    }
    public PreviewResult preview(ReadyToPublish definition, ProfileResult.Checked profile, Set<String> selected) {
        return new PhysicalProfileComposer().preview(definition.checked().definition().logical(), profile, selected,
                value -> new ProfileValidator().validate(definition, value));
    }
    public CompositionResult compose(ReadyToPublish definition, ProfileResult.Checked profile, Preview preview,
            GraphValidationResult.Accepted target, List<Decision> decisions) {
        Comparator<ObservedGraph.Key> keys = Comparator.comparing(ObservedGraph.Key::type).thenComparing(ObservedGraph.Key::identity);
        return new PhysicalProfileComposer().compose(definition.checked().definition().logical(), profile, preview,
                target.graph(), decisions, value -> new ProfileValidator().validate(definition, value), keys);
    }
}
