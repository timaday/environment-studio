package studio.environment.server.workspace;


import java.security.*;
import java.util.HexFormat;
import studio.environment.core.workspace.DraftCommand;

final class WorkspaceDigests {
    private WorkspaceDigests() { }
    static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    static String fields(String... values) {
        var hash = digest();
        for (String value : values) {
            byte[] bytes = StrictUtf8.encode(value);
            hash.update(StrictUtf8.encode(Integer.toString(bytes.length) + ":"));
            hash.update(bytes);
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    static String command(DraftCommand command) {
        return fields(command.expectedRevision(), command.requestId(), command.format().name(), command.source());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unsupported) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
}
