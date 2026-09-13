package studio.environment.server.projection;

import java.util.List;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.graph.GraphDiagnostic;

/** Internal v2 adapter. Input definitions come from the server compiler. */
public final class GraphProjectionAdapter {
    private final PhysicalGraphProjection physical = new PhysicalGraphProjection();
    public ProjectionResult project(ReadyToPublish definition, String bindingId, List<DocumentSource> documents) {
        if (definition == null || documents == null) return rejected("INVALID_INPUT");
        if (!studio.environment.core.definitionv2.NativeMechanisms.matchesDependencies(definition.checked())) return rejected("UNSUPPORTED_MECHANISM");
        NativeDefinition.Binding binding = definition.checked().definition().bindings().stream()
                .filter(candidate -> candidate.id().equals(bindingId)).findFirst().orElse(null);
        if (binding == null) return rejected("UNKNOWN_BINDING");
        return physical.project(definition.checked().definition().logical(), binding,
                definition.checked().logicalDigest(), definition.checked().bindingDigests().get(bindingId), documents, () -> false);
    }
    private static ProjectionResult rejected(String code) {
        return new ProjectionResult.Rejected(List.of(new GraphDiagnostic(code, "", "", "")));
    }
}
