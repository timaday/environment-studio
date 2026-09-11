package studio.environment.core.planning;

import java.util.Comparator;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.graph.GraphValidationResult;
import static studio.environment.core.planning.TargetIntent.*;

/** Internal physical expectation only. Checked records and results grant no runtime authority. */
public final class V3TargetIntentCompiler {
    private static final Comparator<Ref> REFERENCES = Comparator.comparing(Ref::type, V3TargetIntentCompiler::text)
            .thenComparingInt(r -> r instanceof Ref.Existing ? 0 : 1)
            .thenComparing(r -> r instanceof Ref.Existing old ? old.key().identity() : ((Ref.Fresh) r).slot(), V3TargetIntentCompiler::text);

    public TargetCompilationResult compile(NativeCompilationResult.Checked definition, GraphValidationResult.Accepted observation, TargetIntent intent) {
        if (definition == null || observation == null || intent == null) return TargetCompilationResult.reject("INVALID_INPUT");
        var compiled = new NativeDefinitionCompiler().compile(definition.definition());
        if (!compiled.isCompatibleWith(definition))
            return TargetCompilationResult.reject("INVALID_DEFINITION");
        var logical = definition.definition().logical();
        var physical = new studio.environment.core.definitionv2.NativeDefinition.Logical(logical.entityTypes(), logical.relations(),
                logical.rules(), logical.operationCapabilities());
        return new PhysicalTargetIntentCompiler(REFERENCES).compile(physical, observation, intent);
    }

    /** Unicode scalar order equals unsigned UTF-8 order for admitted text. No encoding allocation. */
    private static int text(String left, String right) {
        int a = 0, b = 0;
        while (a < left.length() && b < right.length()) {
            int x = left.codePointAt(a), y = right.codePointAt(b);
            if (x != y) return Integer.compare(x, y);
            a += Character.charCount(x); b += Character.charCount(y);
        }
        return Integer.compare(left.length() - a, right.length() - b);
    }
}
