package studio.environment.core.plan;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BooleanSupplier;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.observation.ObservationResult.Document;
import studio.environment.core.observation.ObservationResult.Key;

/** Non-authorizing state comparison; the future artifact owner supplies fresh evidence. */
public final class PlanReadback {
    private PlanReadback() { }
    public enum Result { MATCHES, DIFFERS, UNKNOWN }
    public record Destination(String engine, String id, String host, int port, String database,
            String transportIdentity, String provisioningPolicyVersion, Map<String,String> physicalIdentity) {
        public Destination {
            try {
                require(Set.of("postgresql","oracle").contains(engine));
                require(text(id,256) && text(host,253) && port > 0 && port <= 65535
                        && text(database,256) && text(transportIdentity,256) && text(provisioningPolicyVersion,256));
                // Reuse the closed physical identity shape; this display record grants no authority.
                physicalIdentity = new PlanObservedDestination(engine,physicalIdentity,"0".repeat(64),false).identity();
            } catch (Invalid | PlanRefusal | NullPointerException invalid) { throw expectation(); }
        }
        @Override public String toString() { return "ReadbackDestination[redacted]"; }
    }
    public record Expected(int modelVersion, String logicalDigest, String bindingDigest,
            Destination destination, List<Document> documents) {
        public Expected {
            try {
                require((modelVersion == 2 || modelVersion == 3) && sha(logicalDigest) && sha(bindingDigest) && destination != null);
                require(documents != null && !documents.isEmpty() && documents.size() <= 128);
                var snapshot = new ArrayList<Document>(128);
                for (var document : documents) {
                    require(snapshot.size() < 128);
                    snapshot.add(document);
                }
                documents = List.copyOf(snapshot);
                inventory(documents,()->false);
            } catch (Invalid | NullPointerException | ConcurrentModificationException invalid) { throw expectation(); }
        }
        @Override public String toString() { return "ReadbackExpected[redacted]"; }
    }
    public static Result compare(Expected expected, ObservationResult observed, BooleanSupplier cancelled) {
        if (expected == null || cancelled == null || cancelled.getAsBoolean()) return Result.UNKNOWN;
        if (!(observed instanceof ObservationResult.Complete complete)) return Result.UNKNOWN;
        try {
            var actual = complete.observation();
            require(sha(actual.fingerprint()) && expected.logicalDigest().equals(actual.logicalDigest())
                    && expected.bindingDigest().equals(actual.bindingDigest()));
            evidence(expected,actual.evidence());
            var documents = inventory(actual.documents(),cancelled);
            boolean equal = documents.size() == expected.documents().size();
            // Expected is detached and validated at construction; never normalize its XML.
            for (var document : expected.documents()) {
                require(!cancelled.getAsBoolean());
                var found = documents.get(document.documentId());
                if (found == null || !document.key().equals(found.key()) || !document.xml().equals(found.xml())) equal = false;
            }
            require(!cancelled.getAsBoolean());
            return equal ? Result.MATCHES : Result.DIFFERS;
        } catch (Invalid invalid) { return Result.UNKNOWN; }
    }
    private static void evidence(Expected expected, Map<String,Object> evidence) {
        var d = expected.destination();
        require(d.engine().equals(evidence.get("engine")) && "complete".equals(evidence.get("cleanup")));
        var endpoint = Map.of("id",d.id(),"host",d.host(),"port",d.port(),"database",d.database(),
                "transportIdentity",d.transportIdentity(),"provisioningPolicyVersion",d.provisioningPolicyVersion(),
                "expectedPhysicalIdentity",d.physicalIdentity(),"observedPhysicalIdentity",d.physicalIdentity());
        require(endpoint.equals(evidence.get("destination")));
        var metadata = Map.of("adapterVersion","jdbc-observation-v" + expected.modelVersion(),
                "operationPolicyVersion",d.engine() + "-read-operation-v1","visibility","complete",
                "readOnlyOperation","verified","snapshot",d.engine().equals("postgresql") ? "repeatable-read-read-only" : "read-only");
        require(metadata.equals(evidence.get("metadata")));
    }
    private static Map<String,Document> inventory(List<Document> documents, BooleanSupplier cancelled) {
        require(documents != null && !documents.isEmpty() && documents.size() <= 128);
        var result = new TreeMap<String,Document>();
        var keys = new HashSet<Key>();
        long bytes = 0;
        String type = null;
        for (var document : documents) {
            require(!cancelled.getAsBoolean());
            require(document != null && identifier(document.documentId()) && key(document.key()));
            require(type == null || type.equals(document.key().type())); type = document.key().type();
            require(keys.add(document.key()) && result.put(document.documentId(),document) == null);
            String xml = document.xml();
            require(xml != null && !xml.isEmpty() && xml.length() <= 1_048_576
                    && document.characters() == xml.length() && sha(document.sourceDigest()));
            MessageDigest digest = digest();
            byte[] encoded = new byte[4];
            long count = 0;
            for (int offset = 0, points = 0; offset < xml.length(); points++) {
                if ((points & 255) == 0) require(!cancelled.getAsBoolean());
                int cp = xml.codePointAt(offset); require(cp < 0xd800 || cp > 0xdfff);
                offset += Character.charCount(cp);
                int size;
                if (cp < 0x80) { size=1; encoded[0]=(byte)cp; }
                else if (cp < 0x800) { size=2; encoded[0]=(byte)(0xc0 | cp>>6); encoded[1]=(byte)(0x80 | cp&63); }
                else if (cp < 0x10000) { size=3; encoded[0]=(byte)(0xe0 | cp>>12); encoded[1]=(byte)(0x80 | cp>>6&63); encoded[2]=(byte)(0x80 | cp&63); }
                else { size=4; encoded[0]=(byte)(0xf0 | cp>>18); encoded[1]=(byte)(0x80 | cp>>12&63); encoded[2]=(byte)(0x80 | cp>>6&63); encoded[3]=(byte)(0x80 | cp&63); }
                count += size; require(bytes + count <= 16L * 1_048_576);
                digest.update(encoded,0,size);
            }
            Arrays.fill(encoded,(byte)0);
            require(count == document.utf8Bytes() && HexFormat.of().formatHex(digest.digest()).equals(document.sourceDigest()));
            bytes += count;
        }
        require(!cancelled.getAsBoolean());
        return result;
    }
    private static boolean key(Key key) {
        if (key == null) return false;
        if (key.type().equals("text")) return scalar(key.value(),256);
        if (!key.type().equals("int64") || !key.value().matches("0|-?[1-9][0-9]{0,18}")) return false;
        try { Long.parseLong(key.value()); return true; } catch (NumberFormatException invalid) { return false; }
    }
    private static boolean identifier(String value) { return value != null && value.matches("[a-z][a-z0-9.-]{0,63}"); }
    private static boolean sha(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static boolean scalar(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum * 2 || value.codePointCount(0,value.length()) > maximum) return false;
        for (int i=0;i<value.length();) { int cp=value.codePointAt(i); if (cp>=0xd800 && cp<=0xdfff) return false; i+=Character.charCount(cp); }
        return true;
    }
    private static boolean text(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum * 2 || value.codePointCount(0,value.length()) > maximum) return false;
        for (int i=0;i<value.length();) { int cp=value.codePointAt(i); if (Character.isISOControl(cp) || cp>=0xd800 && cp<=0xdfff) return false; i+=Character.charCount(cp); }
        return true;
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException absent) { throw new IllegalStateException("SHA256_REQUIRED"); } }
    private static void require(boolean condition) { if (!condition) throw new Invalid(); }
    private static IllegalArgumentException expectation() { return new IllegalArgumentException("INVALID_READBACK_EXPECTATION"); }
    private static final class Invalid extends RuntimeException { private Invalid() { super("READBACK_UNAVAILABLE",null,false,false); } }
}
