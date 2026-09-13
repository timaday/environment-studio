package studio.environment.core.workspace;

import java.util.*;

/** Closed decoded mutation, scoped by the authenticated owner and UUID catalog. */
public sealed interface NativeCommand {
    String objectId(); String expectedRevision(); String requestId();
    default boolean profile() { return this instanceof SaveProfile || this instanceof PublishProfile; }
    record Reference(String objectId, String workspaceRevision) {
        public Reference { if (!DraftCommand.uuid(objectId) || !revision(workspaceRevision, false)) throw invalid(); }
        @Override public String toString() { return "DefinitionReference[redacted]"; }
    }
    record Policy(String bindingId, String documentId, String content) {
        public Policy {
            if (!id(bindingId) || !id(documentId) || !Set.of("deny", "protected-self-contained").contains(content)) throw invalid();
        }
        @Override public String toString() { return "DocumentPolicy[redacted]"; }
    }
    record SaveDefinition(String objectId, String expectedRevision, String requestId, DraftCommand.Format format, String source) implements NativeCommand {
        public SaveDefinition { check(objectId, expectedRevision, requestId); Objects.requireNonNull(format); Objects.requireNonNull(source); }
        @Override public String toString() { return "SaveDefinition[redacted]"; }
    }
    record SaveProfile(String objectId, String expectedRevision, String requestId, DraftCommand.Format format, String source, Reference definition) implements NativeCommand {
        public SaveProfile { check(objectId, expectedRevision, requestId); Objects.requireNonNull(format); Objects.requireNonNull(source); Objects.requireNonNull(definition); }
        @Override public String toString() { return "SaveProfile[redacted]"; }
    }
    record PublishDefinition(String objectId, String expectedRevision, String requestId, List<Policy> exportPolicies) implements NativeCommand {
        public PublishDefinition { check(objectId, expectedRevision, requestId); exportPolicies = sorted(exportPolicies); }
        @Override public String toString() { return "PublishDefinition[redacted]"; }
    }
    record PublishProfile(String objectId, String expectedRevision, String requestId) implements NativeCommand {
        public PublishProfile { check(objectId, expectedRevision, requestId); }
        @Override public String toString() { return "PublishProfile[redacted]"; }
    }
    static List<Policy> sorted(List<Policy> policies) {
        if (policies == null || policies.size() > 20_000) throw invalid();
        return policies.stream().sorted(Comparator.comparing(Policy::bindingId).thenComparing(Policy::documentId)).toList();
    }
    static boolean revision(String value, boolean zero) { return value != null && value.length() <= 1024 && value.matches(zero ? "0|[1-9][0-9]*" : "[1-9][0-9]*"); }
    private static boolean id(String value) { return value != null && value.matches("[a-z][a-z0-9.-]{0,63}"); }
    private static void check(String id, String revision, String request) { if (!DraftCommand.uuid(id) || !DraftCommand.uuid(request) || !revision(revision, true)) throw invalid(); }
    private static WorkspaceRefusal invalid() { return new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST); }
}
