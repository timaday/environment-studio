package studio.environment.server.workspace;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import studio.environment.core.workspace.WorkspaceRefusal;

final class StrictUtf8 {
    private StrictUtf8() { }
    static String decode(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException invalid) { throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST); }
    }
    static byte[] encode(String value) {
        try {
            var buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            var bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
        } catch (CharacterCodingException invalid) { throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST); }
    }
}
