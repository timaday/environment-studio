package studio.environment.server.export;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;

/** Closed transient data, never publication or execution authority. */
public final class PackageData {
    private PackageData() { }
    public enum Engine { POSTGRESQL, ORACLE; public String token() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public enum KeyType { TEXT, INT64; public String token() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public record Client(String family, String version, String platform) { @Override public String toString() { return "Client[redacted]"; } }
    public record Versions(String server, String supervisor, String template, String writer, String parser, Map<String, String> mechanisms) {
        public Versions { limit(mechanisms.size(), 8); mechanisms = Collections.unmodifiableMap(new TreeMap<>(mechanisms)); }
        @Override public String toString() { return "Versions[redacted]"; }
    }
    public record Plan(String id, String revision, String inputFingerprint, String observationFingerprint, String definitionPublicationDigest, List<String> profilePublicationDigests) {
        public Plan { limit(profilePublicationDigests.size(), 100); profilePublicationDigests = List.copyOf(profilePublicationDigests); }
        @Override public String toString() { return "PlanPins[redacted]"; }
    }
    public record Binding(String id, String logicalDigest, String bindingDigest) { @Override public String toString() { return "BindingPins[redacted]"; } }
    public sealed interface PhysicalIdentity {
        record Postgres(String systemIdentifier, String databaseOid, String databaseName) implements PhysicalIdentity { @Override public String toString() { return "PostgresIdentity[redacted]"; } }
        record Oracle(String dbid, String dbUniqueName, String conId, String conUid, String conName, String pdbGuid) implements PhysicalIdentity { @Override public String toString() { return "OracleIdentity[redacted]"; } }
    }
    public record Destination(String id, String host, int port, String database, String transport, String transportIdentity, String provisioningPolicyVersion, PhysicalIdentity expectedPhysicalIdentity) { @Override public String toString() { return "Destination[redacted]"; } }
    public record Policy(String documentId, String content) { @Override public String toString() { return "Policy[redacted]"; } }
    public record Execution(Engine engine, String storage, Client client, Versions versions, Plan plan, Binding binding, Destination destination, List<Policy> exportPolicies) {
        public Execution { limit(exportPolicies.size(), 128); exportPolicies = List.copyOf(exportPolicies); }
        @Override public String toString() { return "Execution[redacted]"; }
    }
    public record Table(String schema, String name, String keyColumn, String xmlColumn, KeyType keyType) { @Override public String toString() { return "Table[redacted]"; } }
    public record Key(KeyType type, String value) { @Override public String toString() { return "Key[redacted]"; } }
    public record Document(String documentId, Key key, String originalHex, String targetHex) { @Override public String toString() { return "Document[redacted]"; } }
    public record Payload(String bindingId, Engine engine, String storage, Table table, List<Document> records) {
        public Payload { limit(records.size(), 128); records = List.copyOf(records); }
        @Override public String toString() { return "Payload[redacted]"; }
    }
    public record Counts(int records, int changedRecords, long originalBytes, long targetBytes) { }
    private static void limit(int size, int maximum) { if (size > maximum) throw new IllegalArgumentException("PACKAGE_RESOURCE_LIMIT"); }
}
