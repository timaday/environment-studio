package studio.environment.server.plan;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.workspace.NativeCommand;
import static org.junit.jupiter.api.Assertions.*;

class PlanPreviewDigestTest {
    @Test void matchesIndependentNativeFramingOracleIncludingLargeDecimalRevision() throws Exception {
        var fixture=JsonMapper.builder().build().readTree(Files.readString(Path.of("../../fixtures/plan-http-v1/preview-digest.json")));
        var pins=fixture.get("pins");var profile=pins.get("profile");
        var preview=new HostedPlanService.CompositionPreview(pins.get("planId").asString(),pins.get("revision").asString(),pins.get("observationFingerprint").asString(),
                new NativeCommand.Reference(profile.get("objectId").asString(),profile.get("workspaceRevision").asString()),pins.get("publicationDigest").asString(),List.of("first"),pins.get("rootsDigest").asString(),pins.get("closureDigest").asString(),null);
        assertEquals(fixture.get("previewDigest").asString(),HostedPlanService.compositionPreviewDigest(preview));
    }
}
