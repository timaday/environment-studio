package studio.environment.core.planning;

import java.util.List;
public sealed interface TargetCompilationResult {
    record Expected(ExpectedTarget target) implements TargetCompilationResult { @Override public String toString() { return "Expected[redacted]"; } }
    record Rejected(List<String> codes) implements TargetCompilationResult {
        public Rejected { TargetIntent.bound(codes.size(), 256); codes = codes.stream().distinct().sorted().toList(); }
    }
    static Rejected reject(String code) { return new Rejected(List.of(code)); }
}
