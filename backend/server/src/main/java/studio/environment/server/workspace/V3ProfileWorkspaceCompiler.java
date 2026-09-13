package studio.environment.server.workspace;

import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.workspace.*;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.server.profile.V3ProfileBytesAdapter;
import studio.environment.server.definition.BoundedDocumentParser;

final class V3ProfileWorkspaceCompiler implements V3ProfileWorkspace.Compiler {
    public V3NativeRevision.Profile profile(NativeCommand.SaveProfile command, NativeCompilationResult.Checked definition) {
        var result = new V3ProfileBytesAdapter().read(definition, StrictUtf8.encode(command.source()),
                BoundedDocumentParser.Format.valueOf(command.format().name()));
        return switch (result) {
            case V3ProfileBytesAdapter.Result.Accepted accepted -> new V3NativeRevision.Profile(accepted.checked(), command.definition());
            case V3ProfileBytesAdapter.Result.Rejected rejected -> {
                if (rejected.diagnostics().stream().anyMatch(d -> d.code().equals("BYTE_LIMIT")))
                    throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
                throw new WorkspaceRejection(rejected.diagnostics().stream().map(d -> new DefinitionDiagnostic(
                        phase(d.code()), d.code(), d.path(), "Provide a bounded compatible value-free profile.")).toList());
            }
        };
    }
    private static DefinitionDiagnostic.Phase phase(String code) {
        return switch (code) {
            case "INVALID_SYNTAX", "INVALID_UTF8", "RESOURCE_LIMIT" -> DefinitionDiagnostic.Phase.PARSE;
            case "SCHEMA_VIOLATION" -> DefinitionDiagnostic.Phase.SHAPE;
            default -> DefinitionDiagnostic.Phase.SEMANTIC;
        };
    }
}
