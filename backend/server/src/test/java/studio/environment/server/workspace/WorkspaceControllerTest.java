package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.EnvironmentStudioApplication;

class WorkspaceControllerTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);
    final HostedSessions sessions = new HostedSessions(clock, List.of());
    Path directory;
    WorkspaceController controller;
    @BeforeEach void setup() throws Exception {
        directory = Files.createTempDirectory("es-workspace-http-mock-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        EnvironmentStudioApplication.main(new String[]{"--initialize-workspace=" + directory});
        controller = new WorkspaceController(new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory", directory.toString()), RuntimeMode.HOSTED), sessions);
    }
    MockHttpServletRequest request(String subject) {
        var request = new MockHttpServletRequest();
        assertTrue(sessions.reserveLogin(request));
        sessions.authenticated(request.getSession(), new DefaultOidcUser(List.of(), new OidcIdToken("independent-workspace-token-canary", clock.instant(), clock.instant().plusSeconds(300), Map.of("iss", "https://workspace-mock.invalid", "sub", subject))));
        return request;
    }
    @Test void responseUsesImmutableDecimalProjectionAndSafePrinting() throws Exception {
        var request = request("invented-owner");
        String source = SqliteDraftStoreTest.source("invented", 1).replace("\"revision\":1", "\"revision\":123456789012345678901234567890");
        String body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("expectedRevision", "0", "requestId", UUID.randomUUID().toString(), "format", "JSON", "source", source));
        request.setContent(StrictUtf8.encode(body));
        var response = controller.put(UUID.randomUUID().toString(), request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
        var view = (WorkspaceController.DraftView) response.getBody();
        assertEquals(source, view.source());
        assertEquals("123456789012345678901234567890", view.projection().model().get("revision").asString());
        assertEquals("draft", view.projection().model().get("status").asString());
        assertEquals("incomplete", view.projection().kind());
        assertFalse(view.toString().contains("invented"));
        assertFalse(view.projection().toString().contains("invented"));
        assertFalse(controller.list(request).getBody().toString().contains("invented"));
        var other = request("other-owner");
        assertEquals(404, controller.refusal(assertThrows(studio.environment.core.workspace.WorkspaceRefusal.class, () -> controller.current(view.objectId(), other))).getStatusCode().value());
    }
    @Test void absentConfigurationLeavesSessionAuthorityUsableAndWorkspaceUnavailable() {
        var request = request("invented-owner");
        var runtime = new WorkspaceRuntime(new MockEnvironment(), RuntimeMode.HOSTED);
        assertFalse(runtime.enabled());
        assertEquals("invented-owner", sessions.requireOwner(request).subject());
        var unavailable = new WorkspaceController(runtime, sessions);
        assertEquals(503, unavailable.refusal(assertThrows(studio.environment.core.workspace.WorkspaceRefusal.class, () -> unavailable.list(request))).getStatusCode().value());
    }
    @Test void initializerRefusesExtraArgumentsWithoutStartingWebApplication() {
        assertThrows(IllegalArgumentException.class, () -> EnvironmentStudioApplication.main(new String[]{"--initialize-workspace=" + directory, "--server.port=0"}));
    }
}
