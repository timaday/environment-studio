package studio.environment.core.profile;

import studio.environment.core.definitionv3.NativeCompilationResult.Checked;
import studio.environment.core.graph.ObservedGraph;

/** Capture only caller-qualified physical structure; no graph or derived evidence retained. */
public final class V3ProfileCapture {
    public ProfileResult capture(Checked definition, ObservedGraph graph, ProfileCapture.Command command) {
        if (graph == null || command == null) return ProfileResult.rejected("INVALID_INPUT", "");
        if (!V3ProfileDefinition.eligible(definition)) return ProfileResult.rejected("INVALID_DEFINITION", "");
        return new PhysicalProfileCapture().capture(V3ProfileDefinition.physical(definition), definition.logicalDigest(), graph, command,
                profile -> new V3ProfileValidator().validate(definition, profile));
    }
}
