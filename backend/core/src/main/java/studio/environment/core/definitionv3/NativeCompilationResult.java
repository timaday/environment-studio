package studio.environment.core.definitionv3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import studio.environment.core.definition.DefinitionDiagnostic;

/** Closed compiler data; actual v3 compilation remains unqualified. */
public sealed interface NativeCompilationResult permits NativeCompilationResult.Rejected,
        NativeCompilationResult.Incomplete, NativeCompilationResult.ReadyToPublish {
    List<DefinitionDiagnostic> diagnostics();
    /** Internal consistency only. Callers must freshly compile the supplied declaration. */
    default boolean isCompatibleWith(Checked expected) {
        return switch (this) {
            case Rejected ignored -> false;
            case Incomplete incomplete -> incomplete.checked().equals(expected)
                    && incomplete.diagnostics().stream().allMatch(d -> d.code().equals("MECHANISM_UNQUALIFIED"));
            case ReadyToPublish ready -> ready.checked().equals(expected);
        };
    }
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
    /** Compiler eligibility data, never a published revision or runtime authority. */
    record ReadyToPublish(Checked checked) implements NativeCompilationResult {
        public ReadyToPublish { Objects.requireNonNull(checked); }
        @Override public List<DefinitionDiagnostic> diagnostics() { return List.of(); }
        @Override public String toString() { return "ReadyToPublishV3[redacted]"; }
    }
}
