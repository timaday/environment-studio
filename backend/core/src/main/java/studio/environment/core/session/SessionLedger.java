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
    private record Entry(Lease lease, Instant lastSeen) { }
    private static final class CleanupWork {
        final Lease lease;
        int attempts;
        CleanupState state = CleanupState.INCONCLUSIVE;
        CleanupWork(Lease lease) { this.lease = lease; }
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
            sessions.put(id, new Entry(lease, now));
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
    private static boolean live(Entry entry, Instant now) {
        return now.isBefore(entry.lastSeen.plus(IDLE)) && now.isBefore(entry.lease.absoluteExpiresAt());
    }
    public Optional<Lease> touch(String id) {
        expire();
        synchronized (sessions) {
            var entry = sessions.get(id);
            var now = clock.instant();
            if (entry == null || !live(entry, now)) return Optional.empty();
            sessions.put(id, new Entry(entry.lease, now));
            return Optional.of(entry.lease);
        }
    }
    public Optional<CleanupReport> close(String id) {
        synchronized (sessions) {
            var removed = sessions.remove(id);
            if (removed == null) return Optional.ofNullable(pending.get(id)).map(CleanupWork::report);
            pending.put(id, new CleanupWork(removed.lease));
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
            expired.forEach(id -> pending.put(id, new CleanupWork(sessions.remove(id).lease)));
        }
        // Each attempt reports its own outcome; a failed cleanup cannot strand another revoked lease.
        return expired.stream().map(this::retryCleanup).flatMap(Optional::stream).toList();
    }
    public List<CleanupReport> cleanupReports() {
        synchronized (sessions) { return pending.values().stream().map(CleanupWork::report).toList(); }
    }
    /** Internal lifecycle operation only. Never restores authentication or automatically loops retries. */
    public Optional<CleanupReport> retryCleanup(String id) {
        CleanupWork work;
        synchronized (sessions) {
            work = pending.get(id);
            if (work == null) return Optional.empty();
            if (work.state == CleanupState.IN_PROGRESS || work.attempts >= MAX_CLEANUP_ATTEMPTS) return Optional.of(work.report());
            work.state = CleanupState.IN_PROGRESS;
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
        synchronized (sessions) {
            work.state = complete ? CleanupState.COMPLETE : CleanupState.INCONCLUSIVE;
            if (complete) pending.remove(id);
            return Optional.of(work.report());
        }
    }
}
