package studio.environment.server.plan;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.server.planning.V3CommandWireFixtures;
import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

class V3CommandWireCompatibilityTest {
    private static final String ORIGINAL = "<items><!-- mock -->\r\n<item id='one' tone='al&#112;ha' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>";

    @Test void publicWireEditAndIncrementalBindingPreserveExactXmlAndRecomputeTuples() {
        var fixture = new V3CommandWireFixtures();
        String plan = fixture.inspected();
        String one = fixture.handle(plan, "2", "one");
        String first = wire("2", UUID.randomUUID().toString(), "\"kind\":\"upsert-entity\",\"decision\":" + retain(one, "beta") + ",\"placements\":[]");
        assertEquals("3", execute(fixture, plan, first).revision());
        assertTarget(fixture, plan, "3", "<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",
                List.of("by-tone:alpha", "by-tone:beta", "by-finish:x", "by-finish:y"), List.of("alpha:y", "beta:x"));
        String incremental = wire("3", UUID.randomUUID().toString(), "\"kind\":\"bind-field\",\"entity\":" + existing(one)
                + ",\"fieldId\":\"tone\",\"state\":{\"kind\":\"entered\",\"text\":\"violet & 𐀀\"}");
        assertEquals("4", execute(fixture, plan, incremental).revision());
        assertTarget(fixture, plan, "4", "<items><!-- mock -->\r\n<item id='one' tone='violet &amp; 𐀀' finish='x'/><item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",
                List.of("by-tone:alpha", "by-tone:beta", "by-tone:violet & 𐀀", "by-finish:x", "by-finish:y"), List.of("alpha:y", "beta:x", "violet & 𐀀:x"));
    }

    @Test void batchUsesTwoOpaqueOriginalHandlesAndCoalescesOnlyActualPairs() {
        var fixture = new V3CommandWireFixtures();
        String plan = fixture.inspected();
        String first = retain(fixture.handle(plan, "2", "one"), "gamma");
        String second = retain(fixture.handle(plan, "2", "two"), "gamma");
        String batch = wire("2", UUID.randomUUID().toString(), "\"kind\":\"batch-upsert\",\"changes\":[{\"decision\":"
                + first + ",\"placements\":[]},{\"decision\":" + second + ",\"placements\":[]}],\"containment\":[]");
        assertEquals("3", execute(fixture, plan, batch).revision());
        assertTarget(fixture, plan, "3", "<items><!-- mock -->\r\n<item id='one' tone='gamma' finish='x'/><item id='two' tone='gamma' finish='y'/><item id='three' tone='beta' finish='x'/></items>",
                List.of("by-tone:beta", "by-tone:gamma", "by-finish:x", "by-finish:y"), List.of("beta:x", "gamma:x", "gamma:y"));
    }

    @Test void exactDecodedReplaySurvivesDiscardAndReplacementButCollisionCannotMutateEither() {
        var fixture = new V3CommandWireFixtures();
        String plan = fixture.inspected();
        String request = UUID.randomUUID().toString();
        String change = wire("2", request, "\"kind\":\"upsert-entity\",\"decision\":"
                + retain(fixture.handle(plan, "2", "one"), "beta") + ",\"placements\":[]");
        var ack = execute(fixture, plan, change);
        assertEquals(ack, execute(fixture, plan, change));
        String collision = change.replace("\"text\":\"beta\"", "\"text\":\"gamma\"");
        assertEquals(PlanRefusal.Code.CONFLICT, assertThrows(PlanRefusal.class, () -> execute(fixture, plan, collision)).code());
        execute(fixture, plan, wire("3", UUID.randomUUID().toString(), "\"kind\":\"discard\""));
        String replacement = fixture.inspected();
        assertNotEquals(plan, replacement);
        assertEquals(ack, execute(fixture, plan, change));
        assertEquals(PlanRefusal.Code.CONFLICT, assertThrows(PlanRefusal.class, () -> execute(fixture, plan, collision)).code());
        assertEquals("2", fixture.service.viewV3(fixture.lease, java.util.Optional.of(replacement)).summary().revision());
        assertEquals(ORIGINAL, fixture.snapshot(replacement, "2").current().orElseThrow().sources().getFirst().xml());
    }

    private static HostedPlanService.Ack execute(V3CommandWireFixtures fixture, String plan, String json) {
        try (var admission = fixture.service.reserveCommand(fixture.lease, plan, V3)) {
            var decoded = new PlanCommandReader().read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            return admission.execute(decoded);
        }
    }
    private static String wire(String revision, String request, String action) {
        return "{\"expectedRevision\":\"" + revision + "\",\"requestId\":\"" + request + "\"," + action + "}";
    }
    private static String existing(String handle) { return "{\"kind\":\"existing\",\"handle\":\"" + handle + "\"}"; }
    private static String retain(String handle, String tone) {
        return "{\"kind\":\"retain\",\"entity\":" + existing(handle)
                + ",\"fields\":{\"id\":{\"kind\":\"keep-observed\"},\"tone\":{\"kind\":\"entered\",\"text\":\"" + tone
                + "\"},\"finish\":{\"kind\":\"keep-observed\"}},\"references\":{}}";
    }
    private static void assertTarget(V3CommandWireFixtures fixture, String plan, String revision, String xml, List<String> nodes, List<String> pairs) {
        var snapshot = fixture.snapshot(plan, revision);
        assertEquals(ORIGINAL, snapshot.current().orElseThrow().sources().getFirst().xml());
        var target = snapshot.target().orElseThrow();
        assertEquals(xml, target.sources().getFirst().xml());
        var proof = assertInstanceOf(PlanContentEvidence.V3Target.class, target.evidence());
        assertEquals(nodes.stream().sorted().toList(), proof.derived().graph().nodes().stream()
                .map(node -> node.key().derivation() + ":" + node.key().value()).sorted().toList());
        assertEquals(pairs, proof.derived().graph().cooccurrences().stream().map(edge -> edge.source().value() + ":" + edge.target().value()).toList());
        assertEquals(6, proof.derived().graph().memberships().size());
    }
}
