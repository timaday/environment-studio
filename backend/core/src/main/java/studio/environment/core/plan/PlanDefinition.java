package studio.environment.core.plan;

import java.util.Objects;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition.Logical;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;

/** Versioned internal metadata; publication and live plan authority are separate. */
public sealed interface PlanDefinition {
    Logical physical();
    record V2(ReadyToPublish ready) implements PlanDefinition {
        public V2 { Objects.requireNonNull(ready); }
        @Override public Logical physical() { return ready.checked().definition().logical(); }
        @Override public String toString() { return "PlanDefinitionV2[redacted]"; }
    }
    record V3(Checked checked) implements PlanDefinition {
        public V3 { Objects.requireNonNull(checked); }
        @Override public Logical physical() {
            var logical = checked.definition().logical();
            return new Logical(logical.entityTypes(), logical.relations(), logical.rules(), logical.operationCapabilities());
        }
        @Override public String toString() { return "PlanDefinitionV3[redacted]"; }
    }
}
