package studio.environment.core.profile;

import studio.environment.core.definitionv3.NativeCompilationResult.Checked;

/** V3 physical structure and digest only; portable-byte and owner admission remain external. */
public final class V3ProfileValidator {
    public ProfileResult validate(Checked definition, Profile profile) {
        if (profile == null) return ProfileResult.rejected("INVALID_PROFILE", "");
        if (!V3ProfileDefinition.eligible(definition)) return ProfileResult.rejected("INVALID_DEFINITION", "");
        return new PhysicalProfileValidator().validate(V3ProfileDefinition.physical(definition),
                definition.logicalDigest(), profile, ProfileEncoding::digestV3);
    }
}
