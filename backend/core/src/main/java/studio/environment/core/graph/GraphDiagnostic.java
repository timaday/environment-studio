package studio.environment.core.graph;

/** Only tool codes and trusted declaration identifiers, never observed values. */
public record GraphDiagnostic(String code, String documentId, String projectionId, String declarationId) { }
