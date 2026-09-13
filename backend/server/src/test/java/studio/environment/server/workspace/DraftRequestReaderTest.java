package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import studio.environment.core.workspace.*;

class DraftRequestReaderTest {
    final DraftRequestReader reader = new DraftRequestReader();
    final String id = "00000000-0000-4000-8000-000000000001";
    String wrapper(String source) { return "{\"expectedRevision\":\"0\",\"requestId\":\"00000000-0000-4000-8000-000000000002\",\"format\":\"JSON\",\"source\":\"" + source + "\"}"; }
    DraftCommand read(String body) { return reader.read(id, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))); }
    @Test void preservesExactUnicodeSourceAndClosedTypedCommand() {
        var command = read(wrapper("  invented \\uD83C\\uDF1F \\n"));
        assertEquals("  invented 🌟 \n", command.source());
        assertEquals("0", command.expectedRevision());
        assertEquals(DraftCommand.Format.JSON, command.format());
    }
    @Test void rejectsDuplicateWrapperKeysUnknownKeysAndEscapedLoneSurrogates() {
        for (var body : java.util.List.of(wrapper("ok").replace("{", "{\"format\":\"YAML\","),
                wrapper("ok").replace("{", "{\"unknown\":false,"), wrapper("\\uD800"), wrapper("\\uDC00"))) {
            assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class, () -> read(body)).code());
        }
    }
    @Test void stopsOversizedBodyBeforeReadingUnboundedInput() {
        var consumed = new java.util.concurrent.atomic.AtomicInteger();
        var stream = new InputStream() { public int read() { consumed.incrementAndGet(); return ' '; } };
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class, () -> reader.read(id, stream)).code());
        assertTrue(consumed.get() <= 8 * 1024 * 1024 + 1);
    }

    @Test void malformedUtf8AndOversizedDecodedSourceHaveDistinctRefusals() {
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class,
                () -> reader.read(id, new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28}))).code());
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class,
                () -> read(wrapper("é".repeat(524_289)))).code());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class,
                () -> read(wrapper("ok").replace("\"expectedRevision\":\"0\"", "\"expectedRevision\":\"" + "1".repeat(1025) + "\""))).code());
    }
    @Test void digestFramesDecodedFieldsAndIgnoresWrapperLayout() throws Exception {
        var first = read(wrapper("x"));
        var reformatted = read("  " + wrapper("x").replace(",", ", ") + "\n");
        assertEquals(WorkspaceDigests.command(first), WorkspaceDigests.command(reformatted));
        String independentFrame = "1:036:00000000-0000-4000-8000-0000000000024:JSON1:x";
        String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(independentFrame.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, WorkspaceDigests.command(first));
        assertNotEquals(WorkspaceDigests.fields("ab", "c"), WorkspaceDigests.fields("a", "bc"));
    }
}
