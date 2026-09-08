package studio.environment.core.workspace;

import java.util.List;
import studio.environment.core.definition.DefinitionDiagnostic;

public final class WorkspaceRejection extends RuntimeException {
    private final List<DefinitionDiagnostic> diagnostics;
    public WorkspaceRejection(List<DefinitionDiagnostic> diagnostics) {
        super("WORKSPACE_REJECTED", null, false, false);
        if (diagnostics.isEmpty() || diagnostics.size() > 256) throw new IllegalArgumentException("Invalid diagnostics.");
        this.diagnostics = List.copyOf(diagnostics);
    }
    public List<DefinitionDiagnostic> diagnostics() { return diagnostics; }
    public static WorkspaceRejection publication(String code) {
        return new WorkspaceRejection(List.of(new DefinitionDiagnostic(DefinitionDiagnostic.Phase.PUBLICATION, code, "", "Publication requires complete compatible evidence.")));
    }
}
