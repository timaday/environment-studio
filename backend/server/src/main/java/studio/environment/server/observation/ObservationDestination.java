package studio.environment.server.observation;

import java.util.*;
import studio.environment.core.definitionv2.NativeDefinition.Engine;

/** Trusted server composition only. No request can construct URLs or driver properties. */
public record ObservationDestination(String id, Engine engine, String host, int port, String database,
        Transport transport, String trustMaterial, String transportIdentity, Map<String, String> expectedPhysicalIdentity,
        String provisioningPolicyVersion, String operationPolicyVersion) {
    public enum Transport { VERIFIED_TLS, DISPOSABLE_LOOPBACK }
    public ObservationDestination {
        Objects.requireNonNull(engine); Objects.requireNonNull(transport);
        if (id == null || id.isBlank() || host == null || !host.matches("[A-Za-z0-9.-]{1,253}")
                || database == null || !database.matches("[A-Za-z0-9_]{1,128}") || port < 1 || port > 65535
                || transportIdentity == null || transportIdentity.isBlank() || provisioningPolicyVersion == null || provisioningPolicyVersion.isBlank()
                || operationPolicyVersion == null || operationPolicyVersion.isBlank()) throw new IllegalArgumentException("INVALID_DESTINATION_CONFIGURATION");
        if (transport == Transport.DISPOSABLE_LOOPBACK && !Set.of("127.0.0.1", "localhost").contains(host)) throw new IllegalArgumentException("INVALID_DISPOSABLE_ENDPOINT");
        if (transport == Transport.VERIFIED_TLS && (trustMaterial == null || trustMaterial.isBlank())) throw new IllegalArgumentException("TRUST_MATERIAL_REQUIRED");
        expectedPhysicalIdentity = Map.copyOf(expectedPhysicalIdentity);
        var keys = engine == Engine.POSTGRESQL ? Set.of("systemIdentifier", "databaseOid", "databaseName") : Set.of("dbid", "dbUniqueName", "conId", "conUid", "conName", "pdbGuid");
        if (!expectedPhysicalIdentity.keySet().equals(keys) || expectedPhysicalIdentity.values().stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("INDEPENDENT_DESTINATION_REQUIRED");
    }
    @Override public String toString() { return "ObservationDestination[REDACTED]"; }
}
