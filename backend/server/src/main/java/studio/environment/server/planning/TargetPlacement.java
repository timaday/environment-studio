package studio.environment.server.planning;

import studio.environment.core.planning.TargetIntent.Ref;

/** Explicit source-bound parent or an explicit creation; no inferred first match. */
public record TargetPlacement(Ref entity, String documentId, String projectionId, Parent parent) {
    public sealed interface Parent {
        record Existing(String documentId, String sourceDigest, int elementIndex) implements Parent {
            @Override public String toString() { return "ExistingParent[redacted]"; }
        }
        record Created(Ref.Fresh entity) implements Parent { @Override public String toString() { return "CreatedParent[redacted]"; } }
    }
    @Override public String toString() { return "TargetPlacement[redacted]"; }
}
