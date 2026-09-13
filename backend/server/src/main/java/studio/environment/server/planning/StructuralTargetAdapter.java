package studio.environment.server.planning;

import java.util.List;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.planning.TargetCompilationResult;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.TargetIntentCompiler;
import studio.environment.server.projection.DocumentSource;
import studio.environment.server.projection.GraphProjectionAdapter;
import studio.environment.server.projection.ProjectionResult;
import static studio.environment.server.planning.PlanningXml.fail;

/** V2 facade retains checked projection and physical intent semantics. */
public final class StructuralTargetAdapter {
    public MaterializationResult materialize(ReadyToPublish definition, String bindingId, List<TargetSource> sources, TargetIntent intent, List<TargetPlacement> placements) {
        if (definition == null || bindingId == null || sources == null || intent == null || placements == null) return MaterializationResult.reject("INVALID_INPUT");
        try {
            var binding = definition.checked().definition().bindings().stream().filter(b -> b.id().equals(bindingId)).findFirst().orElse(null);
            return new PhysicalTargetMaterializer().materialize(definition.checked().definition().logical(), binding, sources, intent, placements,
                    inputs -> {
                        var projected = new GraphProjectionAdapter().project(definition, bindingId, inputs.stream().map(s -> new DocumentSource(s.documentId(), s.source())).toList());
                        if (projected instanceof ProjectionResult.Rejected refused) fail(refused.diagnostics().getFirst().code());
                        return ((ProjectionResult.Accepted)projected).graph();
                    }, (observed, requested) -> {
                        var compiled = new TargetIntentCompiler().compile(definition, new GraphValidationResult.Accepted(observed), requested);
                        if (compiled instanceof TargetCompilationResult.Rejected refused) fail(refused.codes().getFirst());
                        return ((TargetCompilationResult.Expected)compiled).target();
                    }, () -> false).physical();
        } catch (PlanningXml.Refusal refused) { return MaterializationResult.reject(refused.code); }
    }
}
