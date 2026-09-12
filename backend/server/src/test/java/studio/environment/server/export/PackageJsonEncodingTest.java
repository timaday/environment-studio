package studio.environment.server.export;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class PackageJsonEncodingTest {
    @Test void preservesLiteralEscapesAndExactUtf8ByteLimit() {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("z", "\ud83d\ude03");
        root.put("a", "\u0000\b\t\n\f\r\"\\\u00e9");
        var expected = "{\"a\":\"\\u0000\\b\\t\\n\\f\\r\\\"\\\\\u00e9\",\"z\":\"\ud83d\ude03\"}".getBytes(StandardCharsets.UTF_8);
        var actual = PackageJson.canonical(root, expected.length);
        assertArrayEquals(expected, actual);
        assertEquals("RESOURCE_LIMIT", assertThrows(PackageJson.Refusal.class,
                () -> PackageJson.canonical(root, expected.length - 1)).code);
        actual[0] = '!';
        assertArrayEquals(expected, PackageJson.canonical(root, expected.length));
    }

    @Test void preservesUnsignedUtf8KeyOrderAndRejectsMalformedSurrogates() {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("\ud800\udc00", 1); root.put("\ue000", 0);
        var expected = "{\"\ue000\":0,\"\ud800\udc00\":1}".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, PackageJson.canonical(root, expected.length));
        for (String malformed : new String[] {"\ud800", "\udc00", "\ud800x"}) {
            var invalid = JsonNodeFactory.instance.objectNode().put("text", malformed);
            assertEquals("INVALID_UNICODE", assertThrows(PackageJson.Refusal.class,
                    () -> PackageJson.canonical(invalid, 1024)).code);
        }
    }

    @Test void decodesSupplementaryTextAcrossBufferBoundariesAndPreservesUtf8ErrorPrecedence() {
        String text = "x".repeat(8182) + "\ud83d\ude03\u00e9";
        byte[] encoded = ("{\"text\":\"" + text + "\"}").getBytes(StandardCharsets.UTF_8);
        assertEquals(text, PackageJson.parse(encoded, encoded.length, 8).get("text").asString());
        byte[] malformed = new byte[20_000];
        java.util.Arrays.fill(malformed, (byte)' ');
        malformed[0] = '!'; // Invalid JSON precedes the invalid UTF-8 in the input.
        malformed[malformed.length - 1] = (byte)0xc0;
        assertEquals("INVALID_UTF8", assertThrows(PackageJson.Refusal.class,
                () -> PackageJson.parse(malformed, malformed.length, 8)).code);
        byte[] truncated = ("{\"text\":\"" + "x".repeat(8182) + "\ud83d\ude03").getBytes(StandardCharsets.UTF_8);
        truncated = java.util.Arrays.copyOf(truncated, truncated.length - 1);
        byte[] incomplete = truncated;
        assertEquals("INVALID_UTF8", assertThrows(PackageJson.Refusal.class,
                () -> PackageJson.parse(incomplete, incomplete.length, 8)).code);
        assertEquals("INVALID_JSON", assertThrows(PackageJson.Refusal.class,
                () -> PackageJson.parse("\ufeff{}".getBytes(StandardCharsets.UTF_8), 32, 8)).code);
    }

    @Test void streamsCanonicalJsonWithTheSameDigestAndCrcAsAllocatedBytes() throws Exception {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("z", "x".repeat(9000) + "\ud83d\ude03");
        root.put("a", "line\nquote\"");
        byte[] canonical = PackageJson.canonical(root, PackageJson.SMALL);
        var crc = new CRC32(); crc.update(canonical);
        var metrics = PackageJson.canonicalMetrics(root, PackageJson.SMALL);
        assertEquals(canonical.length, metrics.bytes());
        assertEquals(crc.getValue(), metrics.crc32());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical)), metrics.sha256());
        var streamed = new ByteArrayOutputStream();
        PackageJson.writeCanonical(root, PackageJson.SMALL, streamed);
        assertArrayEquals(canonical, streamed.toByteArray());
    }
}
