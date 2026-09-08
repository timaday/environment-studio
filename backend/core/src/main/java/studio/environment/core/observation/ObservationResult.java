package studio.environment.core.observation;

import java.util.*;

public sealed interface ObservationResult permits ObservationResult.Complete, ObservationResult.Refused {
    enum Cleanup { COMPLETE, INCONCLUSIVE }
    enum Code { CAPACITY, INVALID_SELECTION, DESTINATION_UNQUALIFIED, DESTINATION_MISMATCH, METADATA_UNAVAILABLE, VISIBILITY_UNQUALIFIED,
        ACCOUNT_NOT_READ_ONLY, STORAGE_UNSUPPORTED, INVENTORY_MISMATCH, NULL_SOURCE, EMPTY_SOURCE, INVALID_SOURCE,
        RESOURCE_LIMIT, CANCELLED, DEADLINE_EXCEEDED, DATABASE_FAILURE, CLEANUP_INCONCLUSIVE }
    Cleanup cleanup();
    record Complete(Observation observation) implements ObservationResult {
        public Complete { Objects.requireNonNull(observation); }
        @Override public Cleanup cleanup() { return Cleanup.COMPLETE; }
        @Override public String toString() { return "Complete[observation=REDACTED,cleanup=COMPLETE]"; }
    }
    interface CleanupHandle {
        Cleanup status();
        Cleanup retry();
        void cancel();
    }
    record Refused(Code code, Cleanup cleanup, Optional<CleanupHandle> cleanupHandle) implements ObservationResult {
        public Refused(Code code, Cleanup cleanup) { this(code, cleanup, Optional.empty()); }
        public Refused { Objects.requireNonNull(code); Objects.requireNonNull(cleanup); cleanupHandle = Objects.requireNonNull(cleanupHandle); }
        @Override public String toString() { return "Refused[code=" + code + ",cleanup=" + cleanup + "]"; }
    }
    record Key(String type, String value) {
        public Key { Objects.requireNonNull(type); Objects.requireNonNull(value); }
        @Override public String toString() { return "Key[REDACTED]"; }
    }
    record Document(String documentId, Key key, String xml, long utf8Bytes, long characters, String sourceDigest) {
        @Override public String toString() { return "Document[REDACTED]"; }
    }
    record Observation(String fingerprint, String logicalDigest, String bindingDigest, List<Document> documents,
                       Map<String, Object> evidence) {
        public Observation { documents = List.copyOf(documents); evidence = Map.copyOf(evidence); }
        @Override public String toString() { return "Observation[REDACTED]"; }
    }
}
