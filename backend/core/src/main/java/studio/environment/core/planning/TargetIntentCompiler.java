package studio.environment.core.planning;

import java.util.Comparator;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.graph.GraphValidationResult;
import static studio.environment.core.planning.TargetIntent.*;

/** V2 physical target facade; historical ordering and readiness contract stay unchanged. */
public final class TargetIntentCompiler {
    public static final Comparator<Ref> REFERENCES = Comparator.comparing(Ref::type)
            .thenComparingInt(r -> r instanceof Ref.Existing ? 0 : 1)
            .thenComparing(r -> r instanceof Ref.Existing old ? old.key().identity() : ((Ref.Fresh)r).slot());
    public TargetCompilationResult compile(ReadyToPublish definition, GraphValidationResult.Accepted observation, TargetIntent intent) {
        if (definition == null || observation == null || intent == null) return TargetCompilationResult.reject("INVALID_INPUT");
        return new PhysicalTargetIntentCompiler(REFERENCES).compile(definition.checked().definition().logical(), observation, intent);
    }
}
