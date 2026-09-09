package studio.environment.core.profile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition.EntityType;

/** V2 structural validation and historical digest domain. */
public final class ProfileValidator {
    static final Comparator<Profile.Relation> RELATIONS = PhysicalProfileValidator.RELATIONS;
    public ProfileResult validate(ReadyToPublish definition, Profile profile) {
        return new PhysicalProfileValidator().validate(definition.checked().definition().logical(),
                definition.checked().logicalDigest(), profile, ProfileEncoding::digest);
    }
    static boolean id(String value) { return PhysicalProfileValidator.id(value); }
    static List<String> required(EntityType type) { return PhysicalProfileValidator.required(type); }
    static <T> boolean cyclic(Map<T, T> parents) { return PhysicalProfileValidator.cyclic(parents); }
}
