package studio.environment.server;

import static org.junit.jupiter.api.Assertions.fail;
import studio.environment.core.definitionv3.NativeCompilationResult;

/** Shared test helper for fixtures that need checked v3 metadata without asserting publication state. */
public final class NativeV3TestSupport {
    private NativeV3TestSupport() { }
    public static NativeCompilationResult.Checked checked(NativeCompilationResult result) {
        return switch (result) {
            case NativeCompilationResult.ReadyToPublish ready -> ready.checked();
            case NativeCompilationResult.Incomplete incomplete -> incomplete.checked();
            case NativeCompilationResult.Rejected rejected -> fail(rejected.diagnostics().toString());
        };
    }
}
