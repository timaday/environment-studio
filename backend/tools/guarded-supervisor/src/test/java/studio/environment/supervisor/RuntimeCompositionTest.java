package studio.environment.supervisor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.server.export.PackageData;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeCompositionTest {
    @Test void onlyPostgresql1611TlsRuntimeIdentityIsCompiledForOrdinaryAdmission() {
        var client = new Configuration.Client("psql", "16.11", Path.of("/runtime"), RuntimeComposition.POSTGRES16_RUNTIME_IDENTITY, "a".repeat(64), Optional.empty());
        var destination = new Configuration.Destination(PackageData.Engine.POSTGRESQL, new PackageData.Destination("pilot", "db.example.invalid", 5432, "appdb", "verified-tls", "b".repeat(64), "policy-v1", new PackageData.PhysicalIdentity.Postgres("1", "2", "appdb")), new Configuration.Pinned(Path.of("/trust.pem"), "c".repeat(64)));
        var config = new Configuration(new Configuration.Pinned(Path.of("/setsid"), "d".repeat(64)), new Configuration.Pinned(Path.of("/terminal"), "e".repeat(64)), List.of(client), List.of(destination));
        assertTrue(RuntimeComposition.REGISTRY.permits(config, client, destination));
        assertFalse(RuntimeComposition.REGISTRY.permits(config, new Configuration.Client("psql", "18.6", Path.of("/runtime"), RuntimeComposition.POSTGRES16_RUNTIME_IDENTITY, "a".repeat(64), Optional.empty()), destination));
        assertFalse(RuntimeComposition.REGISTRY.permits(config, new Configuration.Client("psql", "16.11", Path.of("/runtime"), "0".repeat(64), "a".repeat(64), Optional.empty()), destination));
        assertFalse(RuntimeComposition.REGISTRY.permits(config, client, new Configuration.Destination(PackageData.Engine.POSTGRESQL, new PackageData.Destination("pilot", "db.example.invalid", 5432, "appdb", "disposable-loopback", "b".repeat(64), "policy-v1", new PackageData.PhysicalIdentity.Postgres("1", "2", "appdb")), new Configuration.Pinned(Path.of("/trust.pem"), "c".repeat(64)))));
        assertFalse(RuntimeComposition.REGISTRY.permits(config, new Configuration.Client("sqlplus", "23.26.3.0.0", Path.of("/runtime"), RuntimeComposition.POSTGRES16_RUNTIME_IDENTITY, "a".repeat(64), Optional.of("f".repeat(64))), new Configuration.Destination(PackageData.Engine.ORACLE, new PackageData.Destination("oracle", "db.example.invalid", 1521, "FREEPDB1", "verified-tls", "b".repeat(64), "policy-v1", new PackageData.PhysicalIdentity.Oracle("1", "MOCK", "3", "4", "FREEPDB1", "a".repeat(32))), new Configuration.Pinned(Path.of("/trust.pem"), "c".repeat(64)))));
    }

    @Test void psqlLaunchUsesFixedTlsCommandAndNoSecretEnvironment() throws Exception {
        var client = new Configuration.Client("psql", "16.11", Path.of("/opt/pgsql"), RuntimeComposition.POSTGRES16_RUNTIME_IDENTITY, "a".repeat(64), Optional.empty());
        var destination = new Configuration.Destination(PackageData.Engine.POSTGRESQL, new PackageData.Destination("pilot", "db.example.invalid", 5432, "appdb", "verified-tls", "b".repeat(64), "policy-v1", new PackageData.PhysicalIdentity.Postgres("1", "2", "appdb")), new Configuration.Pinned(Path.of("/trust.pem"), "c".repeat(64)));
        var secret = BoundedSecret.read(new ByteArrayInputStream("invented-password\n".getBytes(StandardCharsets.UTF_8)));
        try (var credentials = new Credentials("operator_user".toCharArray(), secret, true)) {
            var command = RuntimeComposition.psqlCommand(client, destination, credentials);
            assertEquals(List.of("/opt/pgsql/bin/psql", "-X", "-W", "-A", "-t", "-q", "-v", "ON_ERROR_STOP=on", "-v", "ON_ERROR_ROLLBACK=off", "-P", "pager=off", "-h", "db.example.invalid", "-p", "5432", "-U", "operator_user", "-d", "appdb"), command);
            var environment = RuntimeComposition.psqlEnvironment(Path.of("/tmp/root.crt"));
            assertEquals("verify-full", environment.get("PGSSLMODE"));
            assertEquals("/tmp/root.crt", environment.get("PGSSLROOTCERT"));
            assertFalse(environment.keySet().stream().anyMatch(key -> key.contains("PASS")));
            assertFalse(environment.values().stream().anyMatch(value -> value.contains("invented-password")));
        }
    }
}
