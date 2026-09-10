package studio.environment.server.session;

import studio.environment.core.session.SessionLedger;

/** Idempotent cleanup of one exact lease's transient work. A thrown failure remains inconclusive;
 * callers retry only unfinished obligations, at most three attempts total, without restoring authority. */
@FunctionalInterface
public interface SessionCleanup {
    void invalidate(SessionLedger.Lease lease);
    /** Passive exact-lease ownership only; synchronous hooks need no override. */
    default boolean awaitingWork(SessionLedger.Lease lease) { return false; }
}
