package studio.environment.core.definitionv2;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import studio.environment.core.definition.DefinitionDiagnostic;

public sealed interface NativeCompilationResult permits NativeCompilationResult.Rejected,
        NativeCompilationResult.Incomplete, NativeCompilationResult.ReadyToPublish {
    List<DefinitionDiagnostic> diagnostics();
    record Checked(NativeDefinition definition, String logicalDigest, Map<String, String> bindingDigests,
            Map<String, BigInteger> mechanisms) {
        public Checked { Objects.requireNonNull(definition); Objects.requireNonNull(logicalDigest);
            bindingDigests = Collections.unmodifiableMap(new java.util.TreeMap<>(bindingDigests));
            mechanisms = Collections.unmodifiableMap(new java.util.TreeMap<>(mechanisms)); }
    }
    record Rejected(List<DefinitionDiagnostic> diagnostics) implements NativeCompilationResult {
        public Rejected { diagnostics = DefinitionDiagnostic.ordered(diagnostics);
            if (diagnostics.isEmpty()) throw new IllegalArgumentException("A refusal requires diagnostics."); }
    }
    record Incomplete(Checked checked, List<DefinitionDiagnostic> diagnostics) implements NativeCompilationResult {
        public Incomplete { Objects.requireNonNull(checked); diagnostics = DefinitionDiagnostic.ordered(diagnostics);
            if (diagnostics.isEmpty() || diagnostics.stream().anyMatch(d -> d.phase() != DefinitionDiagnostic.Phase.PUBLICATION))
                throw new IllegalArgumentException("An incomplete definition requires publication blockers only."); }
    }
    /** Compiler consistency/capability result, never an actual published workspace revision. */
    record ReadyToPublish(Checked checked) implements NativeCompilationResult {
        public ReadyToPublish { Objects.requireNonNull(checked); }
        @Override public List<DefinitionDiagnostic> diagnostics() { return List.of(); }
    }
}
