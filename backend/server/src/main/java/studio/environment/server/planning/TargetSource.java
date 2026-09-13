package studio.environment.server.planning;

import java.util.Optional;

/** Transient exact source with an optional trusted external document-base context. */
public record TargetSource(String documentId, String source, Optional<String> documentBase) {
    @Override public String toString() { return "TargetSource[redacted]"; }
}
