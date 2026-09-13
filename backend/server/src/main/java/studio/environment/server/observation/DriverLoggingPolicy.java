package studio.environment.server.observation;

import java.util.List;
import java.util.logging.*;

/** Qualified logger names from the pinned pgjdbc 42.7.13 and ojdbc17 23.26.3.0.0 bytecode.
 * Retain strong references: JUL otherwise allows a checked logger to disappear before driver initialization.
 * No driver initialization, credential access, logging-level change or global configuration mutation.
 */
final class DriverLoggingPolicy {
    private DriverLoggingPolicy() { }
    private static final List<Logger> PINNED = List.of(
        Logger.getLogger("oracle"),
        Logger.getLogger("oracle.jdbc"),
        Logger.getLogger("oracle.jdbc.driver"),
        Logger.getLogger("oracle.jdbc.driver.resource.InstalledProviders"),
        Logger.getLogger("oracle.jdbc.replay.driver.ReplayLoggerFactory"),
        Logger.getLogger("oracle.jdbc.internal.replay"),
        Logger.getLogger("org.postgresql"),
        Logger.getLogger("org.postgresql.Driver"),
        Logger.getLogger("org.postgresql.core.Encoding"),
        Logger.getLogger("org.postgresql.core.QueryExecutorBase"),
        Logger.getLogger("org.postgresql.core.v3.AuthenticationPluginManager"),
        Logger.getLogger("org.postgresql.core.v3.ConnectionFactoryImpl"),
        Logger.getLogger("org.postgresql.core.v3.QueryExecutorImpl"),
        Logger.getLogger("org.postgresql.core.v3.ScramAuthenticator"),
        Logger.getLogger("org.postgresql.core.v3.SimpleQuery"),
        Logger.getLogger("org.postgresql.core.v3.replication.V3PGReplicationStream"),
        Logger.getLogger("org.postgresql.core.v3.replication.V3ReplicationProtocol"),
        Logger.getLogger("org.postgresql.ds.common.BaseDataSource"),
        Logger.getLogger("org.postgresql.gss.GssAction"),
        Logger.getLogger("org.postgresql.gss.MakeGSS"),
        Logger.getLogger("org.postgresql.jdbc.BooleanTypeUtil"),
        Logger.getLogger("org.postgresql.jdbc.PgConnection"),
        Logger.getLogger("org.postgresql.jdbc.TypeInfoCache"),
        Logger.getLogger("org.postgresql.jdbcurlresolver.PgPassParser"),
        Logger.getLogger("org.postgresql.jdbcurlresolver.PgServiceConfParser"),
        Logger.getLogger("org.postgresql.largeobject.BlobInputStream"),
        Logger.getLogger("org.postgresql.ssl.MakeSSL"),
        Logger.getLogger("org.postgresql.ssl.PGjdbcHostnameVerifier"),
        Logger.getLogger("org.postgresql.sspi.SSPIClient"),
        Logger.getLogger("org.postgresql.util.LazyCleanerImpl"),
        Logger.getLogger("org.postgresql.util.PGPropertyMaxResultBufferParser"),
        Logger.getLogger("org.postgresql.util.PGPropertyUtil"),
        Logger.getLogger("org.postgresql.util.ServerErrorMessage"),
        Logger.getLogger("org.postgresql.util.SharedTimer"),
        Logger.getLogger("org.postgresql.util.StreamWrapper"),
        Logger.getLogger("org.postgresql.xa.PGXAConnection"),
        Logger.getLogger("org.postgresql.xa.RecoveredXid"));

    static boolean verboseDriverLogging() {
        // The qualified Oracle Diagnostic route uses its default name. A custom route is unqualified.
        String oracleName = System.getProperty("oracle.jdbc.diagnostic.loggerName");
        if (oracleName != null && !oracleName.equals("oracle.jdbc")) return true;
        for (var logger : PINNED) if (verbose(logger)) return true;
        var manager = LogManager.getLogManager();
        var names = manager.getLoggerNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith("org.postgresql.") || name.startsWith("oracle.jdbc.")) {
                var logger = manager.getLogger(name);
                if (logger != null && verbose(logger)) return true;
            }
        }
        return false;
    }
    private static boolean verbose(Logger logger) {
        for (var current = logger; current != null; current = current.getParent()) {
            var level = current.getLevel();
            if (level != null) return level.intValue() < Level.INFO.intValue();
        }
        return true;
    }
}
