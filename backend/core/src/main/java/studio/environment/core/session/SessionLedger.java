package studio.environment.core.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Process-local authority. Revocation is permanent; incomplete cleanup retains bounded capacity. */
public final class SessionLedger {
    public static final Duration IDLE = Duration.ofMinutes(30);
    public static final Duration ABSOLUTE = Duration.ofHours(8);
    public static final int MAX_CLEANUP_ATTEMPTS = 3;
    public record Lease(String id, Owner owner, Instant absoluteExpiresAt) { }
    public enum Refusal { OWNER_ACTIVE, CAPACITY, CLEANUP_INCONCLUSIVE, EXPIRED }
    public enum CleanupState { IN_PROGRESS, INCONCLUSIVE, COMPLETE }
    public record CleanupReport(String sessionId, CleanupState state, int attempts) { }
    public sealed interface Admission permits Accepted, Denied { }
    public record Accepted(Lease lease) implements Admission { }
    public record Denied(Refusal reason) implements Admission { }
    private static final class CommitState { CommitPermit active; }
    private record Entry(Lease lease, Instant lastSeen, CommitState commit) { }
    /** Explicit completion is permitted only after the adapter observes connection closure. */
    public final class CommitPermit {
        private final CommitState state;
        private CommitPermit(CommitState state) { this.state = state; }
        public void complete() {
            synchronized (sessions) { if (state.active == this) state.active = null; }
        }
    }
    private static final class CleanupWork {
        final Lease lease;
        final CommitState commit;
        int attempts;
        boolean resumeRequested;
        CleanupState state = CleanupState.INCONCLUSIVE;
        CleanupWork(Entry entry) { this.lease = entry.lease; this.commit = entry.commit; }
        CleanupReport report() { return new CleanupReport(lease.id(), state, attempts); }
    }
    private final Clock clock;
    private final Consumer<Lease> cleanup;
    private final Map<String, Entry> sessions = new LinkedHashMap<>();
    // Guarded by sessions alongside active authority; together these maps never exceed 64.
    private final Map<String, CleanupWork> pending = new LinkedHashMap<>();

    public SessionLedger(Clock clock, Consumer<Lease> cleanup) {
        this.clock = Objects.requireNonNull(clock);
        this.cleanup = Objects.requireNonNull(cleanup);
    }
    public Admission admit(String id, Owner owner) {
        return admit(id, owner, clock.instant().plus(ABSOLUTE));
    }
    /** Authentication cannot restart the pending-login absolute lifetime. */
    public Admission admit(String id, Owner owner, Instant absoluteDeadline) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("INVALID_SESSION_ID");
        Objects.requireNonNull(owner);
        expire();
        synchronized (sessions) {
            if (sessions.containsKey(id) || pending.containsKey(id)) throw new IllegalArgumentException("INVALID_SESSION_ID");
            if (pending.values().stream().anyMatch(work -> work.lease.owner().equals(owner))) return new Denied(Refusal.CLEANUP_INCONCLUSIVE);
            if (sessions.values().stream().anyMatch(entry -> entry.lease.owner().equals(owner))) return new Denied(Refusal.OWNER_ACTIVE);
            if (sessions.size() + pending.size() >= 64) return new Denied(Refusal.CAPACITY);
            var now = clock.instant();
            Objects.requireNonNull(absoluteDeadline);
            if (!now.isBefore(absoluteDeadline)) return new Denied(Refusal.EXPIRED);
            var maximum = now.plus(ABSOLUTE);
            var lease = new Lease(id, owner, absoluteDeadline.isBefore(maximum) ? absoluteDeadline : maximum);
            sessions.put(id, new Entry(lease, now, new CommitState()));
            return new Accepted(lease);
        }
    }
    /** Short authority transitions only: no I/O or cleanup, and never renews idle lifetime.
     * Revocation uses this same monitor. Expired entries remain denied until normal lifecycle cleanup.
     */
    public <T> Optional<T> guard(Lease lease, java.util.function.Supplier<T> transition) {
        Objects.requireNonNull(lease); Objects.requireNonNull(transition);
        synchronized (sessions) {
            var entry = sessions.get(lease.id());
            var now = clock.instant();
            if (entry == null || !entry.lease.equals(lease) || !live(entry, now)) return Optional.empty();
            return Optional.of(Objects.requireNonNull(transition.get()));
        }
    }
    /** Final commit admission only; the caller performs no I/O under the authority monitor.
     * An admitted commit precedes later revocation and retains capacity until observed cleanup.
     */
    public Optional<CommitPermit> admitCommit(Lease lease) {
        Objects.requireNonNull(lease);
        synchronized (sessions) {
            var entry = sessions.get(lease.id());
            if (entry == null || !entry.lease.equals(lease) || !live(entry, clock.instant())
                    || entry.commit.active != null) return Optional.empty();
            var permit = new CommitPermit(entry.commit);
            entry.commit.active = permit;
            return Optional.of(permit);
        }
    }
    public boolean hasOutstandingCommit(Lease lease) {
        synchronized (sessions) {
            var entry = sessions.get(lease.id());
            if (entry != null && entry.lease.equals(lease)) return entry.commit.active != null;
            var work = pending.get(lease.id());
            return work != null && work.lease.equals(lease) && work.commit.active != null;
        }
    }
    private static boolean live(Entry entry, Instant now) {
        return now.isBefore(entry.lastSeen.plus(IDLE)) && now.isBefore(entry.lease.absoluteExpiresAt());
    }
    public Optional<Lease> touch(String id) {
        expire();
        synchronized (sessions) {
            var entry = sessions.get(id);
            var now = clock.instant();
            if (entry == null || !live(entry, now)) return Optional.empty();
            sessions.put(id, new Entry(entry.lease, now, entry.commit));
            return Optional.of(entry.lease);
        }
    }
    public Optional<CleanupReport> close(String id) {
        synchronized (sessions) {
            var removed = sessions.remove(id);
            if (removed == null) return Optional.ofNullable(pending.get(id)).map(CleanupWork::report);
            pending.put(id, new CleanupWork(removed));
        }
        return retryCleanup(id);
    }
    public List<CleanupReport> expire() {
        List<String> expired;
        synchronized (sessions) {
            var now = clock.instant();
            expired = sessions.values().stream()
                    .filter(entry -> !now.isBefore(entry.lastSeen.plus(IDLE)) || !now.isBefore(entry.lease.absoluteExpiresAt()))
                    .map(entry -> entry.lease.id()).toList();
            expired.forEach(id -> pending.put(id, new CleanupWork(sessions.remove(id))));
        }
        // Each attempt reports its own outcome; a failed cleanup cannot strand another revoked lease.
        return expired.stream().map(this::retryCleanup).flatMap(Optional::stream).toList();
    }
    public List<CleanupReport> cleanupReports() {
        synchronized (sessions) { return pending.values().stream().map(CleanupWork::report).toList(); }
    }
    /** Internal completion notification; coalesces only while an original cleanup attempt is running. */
    public Optional<CleanupReport> resumeCleanup(String id) {
        return retryCleanup(id,true);
    }
    /** Internal lifecycle operation only. Never restores authentication or automatically loops retries. */
    public Optional<CleanupReport> retryCleanup(String id) {
        return retryCleanup(id,false);
    }
    private Optional<CleanupReport> retryCleanup(String id,boolean completionNotification) {
        CleanupWork work;
        synchronized (sessions) {
            work = pending.get(id);
            if (work == null) return Optional.empty();
            if (work.state == CleanupState.IN_PROGRESS) {
                if(completionNotification)work.resumeRequested=true;
                return Optional.of(work.report());
            }
            if (work.commit.active != null && work.attempts > 0) return Optional.of(work.report());
            if (work.attempts >= MAX_CLEANUP_ATTEMPTS) return Optional.of(work.report());
            work.state = CleanupState.IN_PROGRESS;
            work.resumeRequested = false;
            work.attempts++;
        }
        boolean complete;
        try {
            cleanup.accept(work.lease);
            complete = true;
        } catch (RuntimeException failure) {
            // The typed report and quarantined capacity retain the failure; untrusted exception text does not.
            complete = false;
        }
        boolean resume;
        synchronized (sessions) {
            complete = complete && work.commit.active == null;
            work.state = complete ? CleanupState.COMPLETE : CleanupState.INCONCLUSIVE;
            if (complete) pending.remove(id);
            resume=!complete && work.resumeRequested && work.commit.active==null && work.attempts<MAX_CLEANUP_ATTEMPTS;
            work.resumeRequested=false;
        }
        if(resume){var retried=retryCleanup(id);if(retried.isPresent())return retried;}
        synchronized(sessions){return Optional.of(work.report());}
    }
}
