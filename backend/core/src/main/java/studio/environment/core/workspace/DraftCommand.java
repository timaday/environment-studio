package studio.environment.core.workspace;

import java.util.UUID;

public record DraftCommand(String objectId, String expectedRevision, String requestId, Format format, String source) {
    public enum Format { JSON, YAML }
    public DraftCommand {
        if (!uuid(objectId) || !uuid(requestId) || expectedRevision == null || expectedRevision.length() > 1024 || !expectedRevision.matches("0|[1-9][0-9]*")
                || format == null || source == null) throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);
    }
    public static boolean uuid(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException invalid) { return false; }
    }
    @Override public String toString() { return "DraftCommand[content=REDACTED]"; }
}
