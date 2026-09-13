package studio.environment.core.plan;

import java.util.List;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.profile.ProfileResult;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.NativeCommand;

/** Current versioned publication lookup; the owning plan retains lifecycle authority. */
public interface V3PlanWorkspace {
    Definition definition(Owner owner, NativeCommand.Reference reference);
    Profile profile(Owner owner, NativeCommand.Reference reference, Definition selectedDefinition);
    record Definition(NativeCommand.Reference reference, String publicationDigest,
            NativeCompilationResult.Checked checked, List<NativeCommand.Policy> policies) {
        public Definition { policies = List.copyOf(policies); }
        @Override public String toString() { return "V3PlanDefinition[redacted]"; }
    }
    record Profile(NativeCommand.Reference reference, String publicationDigest, ProfileResult.Checked checked) {
        @Override public String toString() { return "V3PlanProfile[redacted]"; }
    }
}
