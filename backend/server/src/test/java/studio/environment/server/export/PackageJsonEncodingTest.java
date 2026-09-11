package studio.environment.server.export;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
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
}
