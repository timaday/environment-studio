package studio.environment.core.plan;

import java.util.Objects;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition.Logical;
import studio.environment.core.definitionv3.NativeCompilationResult.Checked;

/** Versioned internal metadata; publication and live plan authority are separate. */
public sealed interface PlanDefinition {
    Logical physical();
    default java.util.List<studio.environment.core.definitionv2.NativeDefinition.Binding> bindings() {
        return switch(this) {case V2 v2 -> v2.ready().checked().definition().bindings();case V3 v3 -> v3.checked().definition().bindings();};
    }
    default String logicalDigest() {
        return switch(this) {case V2 v2 -> v2.ready().checked().logicalDigest();case V3 v3 -> v3.checked().logicalDigest();};
    }
    default java.util.Map<String,String> bindingDigests() {
        return switch(this) {case V2 v2 -> v2.ready().checked().bindingDigests();case V3 v3 -> v3.checked().bindingDigests();};
    }
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
