package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.workspace.*;

class V3ProfileRequestReaderTest {
    private final String id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private final NativeCommand.Reference reference = new NativeCommand.Reference("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "2");
    private byte[] valid(String source) { return V3ProfileHttpFixtures.body(source, "YAML", "0", "cccccccc-cccc-4ccc-8ccc-cccccccccccc", reference); }
    @Test void exactUnicodeSourceAndClosedReferenceAreRetainedAndBytesWiped() {
        byte[] bytes = valid("mock é 𐀀\r\n");
        var body = new ByteArrayInputStream(bytes) { @Override public byte[] readNBytes(int count) { return bytes; } };
        var command = new V3ProfileRequestReader().read(id, body);
        assertEquals("mock é 𐀀\r\n", command.source());
        assertEquals(reference, command.definition());
        assertEquals(DraftCommand.Format.YAML, command.format());
        assertArrayEquals(new byte[bytes.length], bytes);
    }
    @Test void duplicateExtraMissingNestedTrailingAndWrongTokenTypesRefuseAndWipe() {
        String base = new String(valid("mock"), StandardCharsets.UTF_8);
        var cases = List.of(base + " {}", base.substring(0, base.length() - 1) + ",\"source\":\"other\"}",
                base.replace("\"workspaceRevision\":\"2\"", "\"workspaceRevision\":\"2\",\"workspaceRevision\":\"3\""),
                base.replace("\"workspaceRevision\":\"2\"", "\"extra\":\"2\""),
                base.replace("\"workspaceRevision\":\"2\"", "\"workspaceRevision\":2"),
                base.replace("\"expectedRevision\":\"0\"", "\"expectedRevision\":\"00\""),
                base.replace("\"format\":\"YAML\"", "\"format\":\"yaml\""),
                base.substring(0, base.length() - 1) + ",\"model\":{}}", "{}", "[]");
        for (String value : cases) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            var body = new ByteArrayInputStream(bytes) { @Override public byte[] readNBytes(int count) { return bytes; } };
            assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,
                    assertThrows(WorkspaceRefusal.class, () -> new V3ProfileRequestReader().read(id, body)).code());
            assertArrayEquals(new byte[bytes.length], bytes);
        }
    }
    @Test void malformedUtf8SurrogatesAndByteLimitsAreTyped() {
        for (byte[] bytes : List.of(new byte[]{(byte) 0xff}, valid("mock").clone())) {
            if (bytes.length > 1) bytes = new String(bytes, StandardCharsets.UTF_8).replace("mock", "\\ud800").getBytes(StandardCharsets.UTF_8);
            var body = new ByteArrayInputStream(bytes);
            assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,
                    assertThrows(WorkspaceRefusal.class, () -> new V3ProfileRequestReader().read(id, body)).code());
        }
        var exact = new V3ProfileRequestReader().read(id, new ByteArrayInputStream(valid("é".repeat(524288))));
        assertEquals(524288, exact.source().length());
        for (byte[] bytes : List.of(valid("é".repeat(524289)), new byte[8 * 1024 * 1024 + 1])) {
            assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class,
                    () -> new V3ProfileRequestReader().read(id, new ByteArrayInputStream(bytes))).code());
        }
    }
}
