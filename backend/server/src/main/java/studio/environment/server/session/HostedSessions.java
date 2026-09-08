package studio.environment.server.session;

import jakarta.servlet.http.*;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import studio.environment.core.session.Owner;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.session.SessionLedger.CleanupReport;
import studio.environment.core.session.SessionLedger.CleanupState;

/** Servlet adapter: revoked slots remain quarantined until every independent cleanup is conclusive. */
public final class HostedSessions {
    public static final String REQUEST_LEASE = HostedSessions.class.getName() + ".requestLease";
    private static final String SLOT = HostedSessions.class.getName() + ".slot";
    private static final class Slot {
        final String id;
        final HttpSession session;
        final Instant created;
        final boolean[] hookComplete;
        volatile Instant lastSeen;
        volatile SessionLedger.Lease lease;
        volatile boolean retired;
        boolean servletComplete;
        boolean running;
        int pendingAttempts;
        Slot(String id, HttpSession session, Instant now, int hookCount) {
            this.id = id;
            this.session = session;
            this.created = now;
            this.lastSeen = now;
            this.hookComplete = new boolean[hookCount];
        }
    }
    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();
    private final SessionLedger ledger;
    private final List<SessionCleanup> cleanup;
    private final Clock clock;

    public HostedSessions(Clock clock, List<SessionCleanup> cleanup) {
        this.clock = clock;
        this.cleanup = List.copyOf(cleanup);
        this.ledger = new SessionLedger(clock, this::invalidate);
    }
    public boolean reserveLogin(HttpServletRequest request) {
        expire();
        var existing = request.getSession(false);
        if (existing != null && existing.getAttribute(SLOT) instanceof Binding binding) {
            var slot = slots.get(binding.id);
            if (slot != null && !slot.retired) return true;
        }
        synchronized (slots) {
            if (slots.size() >= 64) return false;
            String id = UUID.randomUUID().toString();
            var session = request.getSession();
            slots.put(id, new Slot(id, session, clock.instant(), cleanup.size()));
            session.setMaxInactiveInterval(1800);
            session.setAttribute(SLOT, new Binding(id));
            return true;
        }
    }
    public SessionLedger.Admission authenticated(HttpSession session, OidcUser principal) {
        if (!(session.getAttribute(SLOT) instanceof Binding binding)) return new SessionLedger.Denied(SessionLedger.Refusal.CAPACITY);
        var slot = slots.get(binding.id);
        if (slot == null || slot.retired) return new SessionLedger.Denied(SessionLedger.Refusal.CAPACITY);
        synchronized (slot) {
            if (slot.retired || slot.lease != null) return new SessionLedger.Denied(SessionLedger.Refusal.CAPACITY);
            var owner = new Owner(principal.getIssuer().toString(), principal.getSubject());
            var now = clock.instant();
            if (!now.isBefore(slot.lastSeen.plus(SessionLedger.IDLE)) || !now.isBefore(slot.created.plus(SessionLedger.ABSOLUTE)))
                return new SessionLedger.Denied(SessionLedger.Refusal.EXPIRED);
            var admission = ledger.admit(binding.id, owner, slot.created.plus(SessionLedger.ABSOLUTE));
            if (admission instanceof SessionLedger.Accepted accepted) { slot.lease = accepted.lease(); slot.lastSeen = clock.instant(); }
            return admission;
        }
    }

    public Optional<SessionLedger.Lease> current(HttpServletRequest request) {
        expire();
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(SLOT) instanceof Binding binding)) return Optional.empty();
        var slot = slots.get(binding.id);
        if (slot == null || slot.retired) return Optional.empty();
        slot.lastSeen = clock.instant();
        return ledger.touch(binding.id);
    }
    /** Read/status polling checks authority without extending idle activity. */
    public Optional<SessionLedger.Lease> capture(HttpServletRequest request) {
        expire();
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute(SLOT) instanceof Binding binding)) return Optional.empty();
        var slot=slots.get(binding.id);
        if(slot==null) return Optional.empty();
        synchronized(slot) {
            if(slot.retired || slot.lease==null) return Optional.empty();
            return ledger.guard(slot.lease,()->slot.lease);
        }
    }
    public <T> Optional<T> guard(SessionLedger.Lease lease, java.util.function.Supplier<T> transition) {
        return ledger.guard(lease, transition);
    }
    public Owner requireOwner(HttpServletRequest request) {
        return current(request).orElseThrow(() -> new IllegalStateException("SESSION_AUTHORITY_REQUIRED")).owner();
    }
    public void expire() {
        ledger.expire();
        var now = clock.instant();
        slots.forEach((id, slot) -> {
            SessionLedger.Lease assigned;
            synchronized (slot) {
                if (slot.retired || (now.isBefore(slot.lastSeen.plus(SessionLedger.IDLE))
                        && now.isBefore(slot.created.plus(SessionLedger.ABSOLUTE)))) return;
                // Admission assigns the lease under this same monitor. Retire and select cleanup atomically.
                slot.retired = true;
                assigned = slot.lease;
            }
            if (assigned != null) ledger.close(id);
            else retryPending(slot);
        });
    }
    /** Uses only authority captured by the security filter, never a request-supplied owner or ID. */
    public Optional<CleanupReport> logout(HttpServletRequest request) {
        if (!(request.getAttribute(REQUEST_LEASE) instanceof SessionLedger.Lease lease)) return Optional.empty();
        var slot = slots.get(lease.id());
        if (slot != null) { synchronized (slot) { slot.retired = true; } }
        return ledger.close(lease.id());
    }
    public List<CleanupReport> cleanupReports() {
        var reports = new ArrayList<>(ledger.cleanupReports());
        slots.values().forEach(slot -> {
            synchronized (slot) {
                if (slot.retired && slot.lease == null) reports.add(pendingReport(slot));
            }
        });
        reports.sort(Comparator.comparing(CleanupReport::sessionId));
        return List.copyOf(reports);
    }
    /** Internal, bounded retry; successful hook obligations are never repeated. */
    public Optional<CleanupReport> retryCleanup(String id) {
        var slot = slots.get(id);
        if (slot == null) return Optional.empty();
        SessionLedger.Lease assigned;
        synchronized (slot) {
            if (!slot.retired) return Optional.empty();
            assigned = slot.lease;
        }
        return assigned == null ? Optional.of(retryPending(slot)) : ledger.retryCleanup(id);
    }
    private void invalidate(SessionLedger.Lease lease) {
        var slot = slots.get(lease.id());
        if (slot == null) throw new CleanupIncomplete();
        synchronized (slot) { slot.retired = true; }
        if (!performCleanup(slot, lease)) throw new CleanupIncomplete();
    }
    private CleanupReport retryPending(Slot slot) {
        synchronized (slot) {
            if (slot.running || slot.pendingAttempts >= SessionLedger.MAX_CLEANUP_ATTEMPTS) return pendingReport(slot);
            slot.pendingAttempts++;
            boolean complete = performCleanup(slot, null);
            return new CleanupReport(slot.id, complete ? CleanupState.COMPLETE : CleanupState.INCONCLUSIVE, slot.pendingAttempts);
        }
    }
    private static CleanupReport pendingReport(Slot slot) {
        return new CleanupReport(slot.id, slot.running ? CleanupState.IN_PROGRESS : CleanupState.INCONCLUSIVE, slot.pendingAttempts);
    }
    private boolean performCleanup(Slot slot, SessionLedger.Lease lease) {
        synchronized (slot) {
            slot.running = true;
            try {
                if (!slot.servletComplete) {
                    try {
                        slot.session.invalidate();
                        slot.servletComplete = true;
                    } catch (RuntimeException failure) {
                        // Only an observed already-invalid session is conclusive; other failures stay quarantined.
                        slot.servletComplete = observedInvalid(slot.session);
                    }
                }
                boolean hooksComplete = true;
                if (lease != null) {
                    for (int i = 0; i < cleanup.size(); i++) {
                        if (!slot.hookComplete[i]) {
                            try { cleanup.get(i).invalidate(lease); slot.hookComplete[i] = true; }
                            catch (RuntimeException failure) { hooksComplete = false; }
                        }
                    }
                } else Arrays.fill(slot.hookComplete, true);
                boolean complete = slot.servletComplete && hooksComplete;
                if (complete) slots.remove(slot.id, slot);
                return complete;
            } finally { slot.running = false; }
        }
    }
    private static boolean observedInvalid(HttpSession session) {
        try { session.getCreationTime(); return false; }
        catch (IllegalStateException invalidated) { return true; }
        catch (RuntimeException inconclusive) { return false; }
    }
    private static final class CleanupIncomplete extends RuntimeException {
        CleanupIncomplete() { super("SESSION_CLEANUP_INCONCLUSIVE", null, false, false); }
    }
    private final class Binding implements HttpSessionBindingListener {
        private final String id;
        private Binding(String id) { this.id = id; }
        @Override public void valueUnbound(HttpSessionBindingEvent event) {
            var slot = slots.get(id);
            if (slot == null) return;
            SessionLedger.Lease assigned;
            synchronized (slot) {
                slot.retired = true;
                if (slot.running) return;
                assigned = slot.lease;
            }
            if (assigned != null) ledger.close(id);
            else retryPending(slot);
        }
    }
}
