package studio.environment.server.observation;

import java.math.BigInteger;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import studio.environment.core.observation.ObservationResult.*;

final class ObservationFingerprint {
    static byte[] utf8(String text) {
        try {
            var buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            var bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
        } catch (CharacterCodingException invalid) { throw new ObservationFailure(Code.INVALID_SOURCE); }
    }
    static String source(String text) { return HexFormat.of().formatHex(digest().digest(utf8(text))); }
    static String hash(Map<String, Object> object) {
        return hash("ES-OBSERVATION-2", object);
    }
    static String hash(String domain, Object object) {
        var digest = digest(); digest.update(utf8(domain)); digest.update((byte) 0); frame(digest, object);
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void frame(MessageDigest digest, Object value) {
        if (value instanceof String text) { byte[] bytes = utf8(text); digest.update(utf8("S" + bytes.length + ":")); digest.update(bytes); }
        else if (value instanceof BigInteger || value instanceof Long || value instanceof Integer) digest.update(utf8("I" + value + ";"));
        else if (value instanceof Boolean flag) digest.update((byte) (flag ? 'T' : 'F'));
        else if (value instanceof List<?> values) { digest.update(utf8("A" + values.size() + ":")); values.forEach(item -> frame(digest, item)); }
        else if (value instanceof Map<?, ?> values) {
            digest.update(utf8("O" + values.size() + ":"));
            values.entrySet().stream().sorted((a,b) -> Arrays.compareUnsigned(utf8((String) a.getKey()), utf8((String) b.getKey())))
                    .forEach(entry -> { frame(digest, entry.getKey()); frame(digest, entry.getValue()); });
        } else throw new IllegalArgumentException("INVALID_FINGERPRINT_FIELD");
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException absent) { throw new IllegalStateException("SHA256_REQUIRED"); } }
}
