package studio.environment.core.plan;

import java.util.Objects;

/** Safe plan diagnostic for operator location hints; never carries observed XML/database values. */
public record PlanDiagnostic(String phase, String code, String pointer, String message) {
    public PlanDiagnostic {
        Objects.requireNonNull(phase); Objects.requireNonNull(code); Objects.requireNonNull(pointer); Objects.requireNonNull(message);
        if (!phase.matches("parse|shape|semantic|publication") || !code.matches("[A-Z][A-Z0-9_]{0,127}")
                || pointer.length() > 512 || message.length() > 512 || unsafe(pointer) || unsafe(message)) {
            throw new IllegalArgumentException("Unsafe plan diagnostic.");
        }
    }
    private static boolean unsafe(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) return true; }
            else if (Character.isLowSurrogate(c) || c < 0x20 && c != '\t') return true;
        }
        return false;
    }
}
