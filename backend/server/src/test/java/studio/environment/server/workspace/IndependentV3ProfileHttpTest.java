package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.session.HostedSessions;

/** Independent invented controls for the fixed profile HTTP candidate. */
class IndependentV3ProfileHttpTest {
    V3ProfileControllerTest fixture;
    @BeforeEach void setup() throws Exception { fixture = new V3ProfileControllerTest(); fixture.setup(); }
    @AfterEach void cleanup() throws Exception { fixture.cleanup(); }

    @Test void replayIdentityIncludesTheExactHistoricalReference() throws Exception {
        String id = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        String source = V3ProfileHttpFixtures.source();
        byte[] original = V3ProfileHttpFixtures.body(source, "JSON", "0", requestId, fixture.reference());
        var saved = fixture.save(id, original); assertEquals(200, saved.response.getStatus());
        var other = V3ProfileHttpFixtures.definition(fixture.fixture.directory, fixture.lease.owner(), false);
        var changedReference = new NativeCommand.Reference(other.objectId(), "2");
        assertEquals(409, fixture.save(id, V3ProfileHttpFixtures.body(source, "JSON", "0", requestId, changedReference)).response.getStatus());
        V3ProfileHttpFixtures.laterDefinition(fixture.fixture.directory, fixture.lease.owner(), fixture.definition);
        assertArrayEquals(saved.response.getContentAsByteArray(), fixture.save(id, original).response.getContentAsByteArray());
        var current = fixture.request(); fixture.fixture.controller.profile(id, current, current.response); current.await();
        assertEquals("1", V3NativeSnapshotCodec.JSON.readTree(current.response.getContentAsByteArray()).get("workspaceRevision").asString());
    }

    @Test void nativeRevisionRemainsExactBeyondJsonNumberPrecisionInViewsAndCatalog() throws Exception {
        String revision = "1" + "0".repeat(1023);
        String source = V3ProfileHttpFixtures.source().replace("\"revision\": 1", "\"revision\": 1e1023");
        assertTrue(source.contains("1e1023"));
        String id = UUID.randomUUID().toString();
        var saved = fixture.save(id, V3ProfileHttpFixtures.body(source, "JSON", "0", UUID.randomUUID().toString(), fixture.reference()));
        assertEquals(200, saved.response.getStatus(), saved.response.getContentAsString());
        var view = V3NativeSnapshotCodec.JSON.readTree(saved.response.getContentAsByteArray());
        assertTrue(view.at("/projection/model/revision").isString());
        assertEquals(revision, view.at("/projection/model/revision").asString());
        var list = fixture.request(); fixture.fixture.controller.profiles(list, list.response); list.await();
        assertEquals(revision, V3NativeSnapshotCodec.JSON.readTree(list.response.getContentAsByteArray()).at("/profiles/0/nativeRevision").asString());
        var history = fixture.request(); fixture.fixture.controller.profileRevision(id, "1", history, history.response); history.await();
        assertArrayEquals(saved.response.getContentAsByteArray(), history.response.getContentAsByteArray());
    }

    @Test void foreignHistoryAndCatalogDoNotDiscloseExistingProfile() throws Exception {
        String id = UUID.randomUUID().toString();
        var saved = fixture.save(id, V3ProfileHttpFixtures.body(V3ProfileHttpFixtures.source(), "JSON", "0", UUID.randomUUID().toString(), fixture.reference()));
        assertEquals(200, saved.response.getStatus());
        var foreign = fixture.fixture.request("independent-foreign-owner");
        fixture.lease = (SessionLedger.Lease) foreign.getAttribute(HostedSessions.REQUEST_LEASE);
        var list = fixture.request(); fixture.fixture.controller.profiles(list, list.response); list.await();
        assertEquals(200, list.response.getStatus()); assertEquals("{\"profiles\":[]}", list.response.getContentAsString());
        for (String revision : List.of("1", "2")) {
            var history = fixture.request(); fixture.fixture.controller.profileRevision(id, revision, history, history.response); history.await();
            assertEquals(404, history.response.getStatus());
            assertFalse(history.response.getContentAsString().contains("mock-profile"));
        }
    }
}
