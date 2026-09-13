package studio.environment.server.workspace;

import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.workspace.*;
import studio.environment.server.definition.DefinitionBytesCompiler;
import studio.environment.server.definition.NativeV3DefinitionBytesCompiler;

/** Actual v3 compiler boundary. Result projection grants no publication authority. */
final class V3NativeWorkspaceCompiler implements V3NativeWorkspace.Compiler {
    @Override public V3NativeRevision.Definition definition(NativeCommand.SaveDefinition command) {
        var result = new NativeV3DefinitionBytesCompiler().compile(StrictUtf8.encode(command.source()),
                DefinitionBytesCompiler.Format.valueOf(command.format().name()));
        return content(result);
    }
    static V3NativeRevision.Definition content(NativeCompilationResult result) {
        return switch (result) {
            case NativeCompilationResult.Rejected rejected -> {
                if (rejected.diagnostics().size() > 256) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
                throw new WorkspaceRejection(rejected.diagnostics());
            }
            case NativeCompilationResult.Incomplete incomplete ->
                    new V3NativeRevision.Definition(incomplete.checked(), incomplete.diagnostics());
            case NativeCompilationResult.ReadyToPublish ready ->
                    new V3NativeRevision.Definition(ready.checked(), ready.diagnostics());
        };
    }
}
