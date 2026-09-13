package studio.environment.core.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Native canonical digest framing only; no external DTO serialization or wire budgets. */
final class ProfileEncoding {
    private ProfileEncoding() { }
    static String digest(Profile profile) {
        return digest(profile, "2");
    }
    static String digestV3(Profile profile) { return digest(profile, "3"); }
    private static String digest(Profile profile, String version) {
        StringBuilder framed = new StringBuilder("ES-PROFILE-" + version + "\0");
        frame(object(profile, version), framed);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(framed.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Required digest unavailable."); }
    }
    private static Map<String, Object> object(Profile p, String version) {
        Map<String, Object> result = new TreeMap<>();
        result.put("schemaVersion", version); result.put("id", p.id()); result.put("logicalDefinitionDigest", p.logicalDefinitionDigest());
        List<Object> entities = new ArrayList<>();
        for (var e : p.entities()) entities.add(new TreeMap<>(Map.of("id", e.id(), "type", e.type(), "label", e.label(), "requiredInputs", e.requiredInputs())));
        List<Object> relations = new ArrayList<>();
        for (var r : p.relations()) relations.add(new TreeMap<>(Map.of("type", r.type(), "from", r.from(), "to", r.to())));
        result.put("entities", entities); result.put("relations", relations); return result;
    }
    private static void frame(Object value, StringBuilder out) {
        if (value instanceof String s) out.append('S').append(s.getBytes(StandardCharsets.UTF_8).length).append(':').append(s);
        else if (value instanceof List<?> list) { out.append('A').append(list.size()).append(':'); for (Object item : list) frame(item, out); }
        else if (value instanceof Map<?, ?> map) { out.append('O').append(map.size()).append(':'); for (var e : map.entrySet()) { frame(e.getKey(), out); frame(e.getValue(), out); } }
        else throw new IllegalArgumentException("Unsupported digest value.");
    }
}
