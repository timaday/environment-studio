package studio.environment.core.definitionv3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import studio.environment.core.definition.DefinitionDiagnostic;

/** V3 is not qualified: no ready/publication result exists in this implementation slice. */
public sealed interface NativeCompilationResult permits NativeCompilationResult.Rejected, NativeCompilationResult.Incomplete {
    List<DefinitionDiagnostic> diagnostics();
    record Checked(NativeDefinition definition, String logicalDigest, Map<String, String> bindingDigests,
            Map<String, BigInteger> mechanisms) {
        public Checked {
            Objects.requireNonNull(definition); Objects.requireNonNull(logicalDigest);
            bindingDigests = Collections.unmodifiableMap(new TreeMap<>(bindingDigests));
            mechanisms = Collections.unmodifiableMap(new TreeMap<>(mechanisms));
        }
        @Override public String toString() { return "CheckedV3[redacted]"; }
    }
    record Rejected(List<DefinitionDiagnostic> diagnostics) implements NativeCompilationResult {
        public Rejected {
            diagnostics = DefinitionDiagnostic.ordered(diagnostics);
            if (diagnostics.isEmpty()) throw new IllegalArgumentException("A refusal requires diagnostics.");
        }
    }
    record Incomplete(Checked checked, List<DefinitionDiagnostic> diagnostics) implements NativeCompilationResult {
        public Incomplete {
            Objects.requireNonNull(checked); diagnostics = DefinitionDiagnostic.ordered(diagnostics);
            if (diagnostics.isEmpty() || diagnostics.stream().anyMatch(d -> d.phase() != DefinitionDiagnostic.Phase.PUBLICATION))
                throw new IllegalArgumentException("An incomplete definition requires publication blockers only.");
        }
        @Override public String toString() { return "IncompleteV3[redacted]"; }
    }
}
