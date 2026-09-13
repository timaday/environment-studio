package studio.environment.server.workspace;

import java.util.List;
import studio.environment.core.workspace.*;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.server.definition.*;
import studio.environment.server.profile.ProfileBytesAdapter;

/** Only the portable adapter Accepted result may enter profile persistence. */
final class NativeWorkspaceCompiler implements NativeWorkspace.Compiler {
    @Override public NativeRevision.Definition definition(NativeCommand.SaveDefinition command) {
        var result=new NativeDefinitionBytesCompiler().compile(StrictUtf8.encode(command.source()),DefinitionBytesCompiler.Format.valueOf(command.format().name()));
        return switch(result) {
            case NativeCompilationResult.Rejected rejected -> throw new WorkspaceRejection(rejected.diagnostics());
            case NativeCompilationResult.Incomplete incomplete -> new NativeRevision.Definition(incomplete.checked(),incomplete.diagnostics());
            case NativeCompilationResult.ReadyToPublish ready -> new NativeRevision.Definition(ready.checked(),List.of());
        };
    }
    @Override public NativeRevision.Profile profile(NativeCommand.SaveProfile command,NativeCompilationResult.ReadyToPublish definition) {
        return accepted(command.definition(),new ProfileBytesAdapter().read(definition,StrictUtf8.encode(command.source()),BoundedDocumentParser.Format.valueOf(command.format().name())));
    }
    private static DefinitionDiagnostic.Phase phase(String code) {
        return switch(code) {
            case "INVALID_SYNTAX","INVALID_UTF8","RESOURCE_LIMIT" -> DefinitionDiagnostic.Phase.PARSE;
            case "SCHEMA_VIOLATION" -> DefinitionDiagnostic.Phase.SHAPE;
            default -> DefinitionDiagnostic.Phase.SEMANTIC;
        };
    }
    static NativeRevision.Profile accepted(NativeCommand.Reference definition,ProfileBytesAdapter.Result result) {
        return switch(result) {
            case ProfileBytesAdapter.Result.Accepted accepted -> new NativeRevision.Profile(accepted.checked(),definition);
            case ProfileBytesAdapter.Result.Rejected rejected -> {
                if(rejected.diagnostics().stream().anyMatch(d->d.code().equals("BYTE_LIMIT")))throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
                throw new WorkspaceRejection(rejected.diagnostics().stream().map(d->new DefinitionDiagnostic(phase(d.code()),d.code(),d.path(),"Provide a bounded compatible value-free profile.")).toList());
            }
        };
    }
}
