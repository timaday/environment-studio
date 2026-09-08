package studio.environment.server.profile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import studio.environment.core.profile.Profile;

/** Package-private external JSON allowlist and portable output budget, owned by the adapter. */
final class ProfileWireEncoding {
    private static final int MAX_BYTES = 1_048_576;
    private ProfileWireEncoding() { }
    static byte[] encode(Profile profile) {
        long nodes = 13L + profile.relations().size() * 7L;
        for (var entity : profile.entities()) nodes += 9L + entity.requiredInputs().size();
        checkNodes(nodes);
        number(profile.revision());
        StringBuilder out = new StringBuilder();
        json(object(profile), out);
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new Limit(Limit.Code.BYTE_LIMIT);
        return bytes;
    }
    static void checkCaptureNodes(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish definition,
            studio.environment.core.graph.ObservedGraph graph) {
        Map<String, Long> required = new java.util.HashMap<>();
        definition.checked().definition().logical().entityTypes().forEach(t -> required.put(t.id(), t.fields().stream().filter(f -> f.required()).count()));
        long nodes = 13L + graph.edges().size() * 7L;
        for (var entity : graph.entities()) nodes += 9L + required.getOrDefault(entity.key().type(), 0L);
        checkNodes(nodes);
    }
    static void checkProfileNodes(Profile profile) {
        long nodes = 13L + profile.relations().size() * 7L;
        for (var entity : profile.entities()) nodes += 9L + entity.requiredInputs().size();
        checkNodes(nodes);
    }
    private static void checkNodes(long nodes) { if (nodes > 20_000) throw new Limit(); }
    private static Map<String, Object> object(Profile p) {
        Map<String, Object> result = new TreeMap<>();
        result.put("schemaVersion", "2"); result.put("id", p.id()); result.put("logicalDefinitionDigest", p.logicalDefinitionDigest());
        result.put("revision", p.revision());
        List<Object> entities = new ArrayList<>();
        for (var e : p.entities()) entities.add(new TreeMap<>(Map.of("id", e.id(), "type", e.type(), "label", e.label(), "requiredInputs", e.requiredInputs())));
        List<Object> relations = new ArrayList<>();
        for (var r : p.relations()) relations.add(new TreeMap<>(Map.of("type", r.type(), "from", r.from(), "to", r.to())));
        result.put("entities", entities); result.put("relations", relations); return result;
    }
    private static String number(java.math.BigInteger value) {
        if (value.signum() < 1 || value.bitLength() > 3402) throw new Limit();
        String plain = value.toString();
        if (plain.length() > 1024) throw new Limit();
        if (plain.length() <= 256) return plain;
        int end = plain.length(); while (plain.charAt(end - 1) == '0') end--;
        String compact = plain.substring(0, end) + "e" + (plain.length() - end);
        if (compact.length() > 256) throw new Limit();
        return compact;
    }
    private static void json(Object value, StringBuilder out) {
        if (value instanceof String s) quote(s, out);
        else if (value instanceof java.math.BigInteger n) out.append(number(n));
        else if (value instanceof List<?> list) {
            out.append('['); boolean comma = false;
            for (Object item : list) { if (comma) out.append(','); json(item, out); comma = true; } out.append(']');
        } else if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean comma = false;
            for (var e : map.entrySet()) { if (comma) out.append(','); quote((String)e.getKey(), out); out.append(':'); json(e.getValue(), out); comma = true; } out.append('}');
        } else throw new IllegalArgumentException("Unsupported portable value.");
        if (out.length() > MAX_BYTES) throw new Limit(Limit.Code.BYTE_LIMIT);
    }
    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f"); case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c)); else out.append(c); }
            }
        }
        out.append('"');
    }
    static final class Limit extends RuntimeException {
        enum Code { RESOURCE_LIMIT, BYTE_LIMIT }
        private final Code code;
        Limit() { this(Code.RESOURCE_LIMIT); }
        Limit(Code code) { super("Profile resource limit.", null, false, false);this.code=code; }
        Code code() {return code;}
    }
}
