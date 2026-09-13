package studio.environment.server.workspace;

import java.sql.Connection;
import java.sql.SQLException;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.WorkspaceRefusal;
import studio.environment.server.session.HostedSessions;

/** Private metadata commit policy; request-scoped and never an authority supplied by uploaded data. */
@FunctionalInterface
interface WorkspaceCommit {
    void commit(Connection connection) throws SQLException;

    static WorkspaceCommit authenticated(HostedSessions sessions, SessionLedger.Lease lease) {
        return connection -> {
            var permit = sessions.admitCommit(lease)
                .orElseThrow(() -> new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN));
            try {
                connection.commit();
            } finally {
                // Even failed commit can be indeterminate. Only observed close releases its permit.
                try { connection.close(); permit.complete(); }
                catch (Error failure) {
                    try { sessions.quarantine(lease); }
                    finally { throw failure; }
                } catch (SQLException | RuntimeException failure) {
                    sessions.quarantine(lease);
                    throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);
                }
            }
        };
    }
}
