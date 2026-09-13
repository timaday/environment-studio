package studio.environment.server.projection;

/** Transient exact source, never a log representation or database completeness claim. */
public record DocumentSource(String documentId, String source) {
    @Override public String toString() { return "DocumentSource[redacted]"; }
}
