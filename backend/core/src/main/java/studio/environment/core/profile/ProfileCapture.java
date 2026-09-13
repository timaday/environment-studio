package studio.environment.core.profile;

import java.math.BigInteger;
import java.util.List;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;

public final class ProfileCapture {
    public record SlotMapping(ObservedGraph.Key observed, String slotId, String label) {
        @Override public String toString() { return "SlotMapping[redacted]"; }
    }
    public record Command(String id, BigInteger revision, List<SlotMapping> mappings) {
        public Command { Profile.bound(mappings.size(), Profile.MAX_ENTITIES); mappings = List.copyOf(mappings); }
        @Override public String toString() { return "CaptureCommand[redacted]"; }
    }
    public ProfileResult capture(ReadyToPublish definition, GraphValidationResult.Accepted observation, Command command) {
        return new PhysicalProfileCapture().capture(definition.checked().definition().logical(),
                definition.checked().logicalDigest(), observation.graph(), command,
                profile -> new ProfileValidator().validate(definition, profile));
    }
}
