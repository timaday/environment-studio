package studio.environment.core.definition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record DefinitionDiagnostic(Phase phase, String code, String pointer, String message) {
    public enum Phase { PARSE, SHAPE, SEMANTIC, PUBLICATION }
    public DefinitionDiagnostic {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(code);
        Objects.requireNonNull(pointer);
        Objects.requireNonNull(message);
    }
    public static List<DefinitionDiagnostic> ordered(List<DefinitionDiagnostic> diagnostics) {
        return diagnostics.stream().distinct().sorted(Comparator.comparing(DefinitionDiagnostic::phase)
                .thenComparing(DefinitionDiagnostic::pointer).thenComparing(DefinitionDiagnostic::code)).toList();
    }
}
