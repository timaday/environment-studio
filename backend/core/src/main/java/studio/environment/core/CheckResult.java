package studio.environment.core;

import java.util.Objects;

public record CheckResult(RequiredCheck check, Outcome outcome, String inputFingerprint) {
    public CheckResult {
        Objects.requireNonNull(check);
        Objects.requireNonNull(outcome);
        requireFingerprint(inputFingerprint);
    }

    static void requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("INVALID_INPUT_FINGERPRINT");
        }
    }
}
