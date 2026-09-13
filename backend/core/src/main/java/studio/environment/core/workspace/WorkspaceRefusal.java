package studio.environment.core.workspace;

public final class WorkspaceRefusal extends RuntimeException {
    public enum Code { FORBIDDEN, INVALID_REQUEST, TOO_LARGE, CONFLICT, NOT_FOUND, CAPACITY, UNAVAILABLE }
    private final Code code;
    public WorkspaceRefusal(Code code) { super("WORKSPACE_" + code.name(), null, false, false); this.code = code; }
    public Code code() { return code; }
}
