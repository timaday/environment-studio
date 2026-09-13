package studio.environment.core.definition;

import java.util.List;
import java.util.Objects;

public sealed interface DefinitionResult permits DefinitionResult.Rejected, DefinitionResult.Incomplete {
    List<DefinitionDiagnostic> diagnostics();
    record Rejected(List<DefinitionDiagnostic> diagnostics) implements DefinitionResult {
        public Rejected { diagnostics = DefinitionDiagnostic.ordered(diagnostics);
            if (diagnostics.isEmpty()) throw new IllegalArgumentException("A rejection requires diagnostics."); }
    }
    record Incomplete(DefinitionDraft draft, List<DefinitionDiagnostic> diagnostics) implements DefinitionResult {
        public Incomplete { Objects.requireNonNull(draft); diagnostics = DefinitionDiagnostic.ordered(diagnostics);
            if (diagnostics.isEmpty() || diagnostics.stream().anyMatch(d -> d.phase() != DefinitionDiagnostic.Phase.PUBLICATION))
                throw new IllegalArgumentException("An incomplete result requires publication blockers only."); }
    }
}
