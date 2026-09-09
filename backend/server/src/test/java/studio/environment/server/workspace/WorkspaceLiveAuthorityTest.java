package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.workspace.WorkspaceRefusal;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;
import studio.environment.server.session.HostedSessions;

/** Independently invented workspace commands; real SQLite plus a deterministic blocked body. */
class WorkspaceLiveAuthorityTest {
    static final class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-09-09T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static final class PausedRequest extends MockHttpServletRequest {
        final CountDownLatch reading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final byte[] body;
        PausedRequest(byte[] body) { this.body = body; }
        @Override public ServletInputStream getInputStream() {
            var bytes = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                boolean first = true;
                public int read() throws IOException {
                    if (first) {
                        first = false; reading.countDown();
                        try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("MOCK_BODY_DEADLINE"); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("MOCK_INTERRUPTED"); }
                    }
                    return bytes.read();
                }
                public boolean isFinished() { return bytes.available() == 0; }
                public boolean isReady() { return true; }
                public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
    }
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void bodyReadRevocationCannotPersist(boolean nativeV2, boolean expire) throws Exception {
        var directory = Files.createTempDirectory("es-live-authority-mock-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SqliteDraftStore.initialize(directory);
        var clock = new MutableClock();
        var sessions = new HostedSessions(clock, List.of());
        var runtime = new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory", directory.toString()), RuntimeMode.HOSTED);
        String source = nativeV2 ? Files.readString(Path.of("../../fixtures/native-v2/definition.json")) : SqliteDraftStoreTest.source("independent-authority", 1);
        var body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(Map.of("expectedRevision", "0", "requestId", UUID.randomUUID().toString(), "format", "JSON", "source", source));
        var request = new PausedRequest(body);
        assertTrue(sessions.reserveLogin(request));
        sessions.authenticated(request.getSession(), new DefaultOidcUser(List.of(), new OidcIdToken("independent-authority-token", clock.instant(), clock.instant().plusSeconds(300), Map.of("iss", "https://authority-mock.invalid", "sub", "invented-owner"))));
        var lease = sessions.current(request).orElseThrow();
        request.setAttribute(HostedSessions.REQUEST_LEASE, lease);
        String id = UUID.randomUUID().toString();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var saving = executor.submit(() -> {
                try {
                    return nativeV2 ? new NativeWorkspaceController(runtime, sessions).saveDefinition(id, request)
                        : new WorkspaceController(runtime, sessions).put(id, request);
                } catch (WorkspaceRefusal refusal) { return refusal.code(); }
            });
            try {
                assertTrue(request.reading.await(5, TimeUnit.SECONDS));
                if (expire) { clock.now = clock.now.plusSeconds(1801); sessions.expire(); }
                else assertTrue(sessions.logout(request).isPresent());
            } finally { request.release.countDown(); }
            assertEquals(WorkspaceRefusal.Code.FORBIDDEN, saving.get(10, TimeUnit.SECONDS));
        }
        assertTrue(runtime.store().list(lease.owner()).isEmpty());
        assertTrue(runtime.nativeStore().list(lease.owner(), false).isEmpty());
    }
    @ParameterizedTest @CsvSource({"false", "true"})
    void revocationAtFinalCommitRollsBackPreparedRevisionAndReplay(boolean nativeV2) throws Exception {
        var directory = Files.createTempDirectory("es-commit-revoke-mock-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SqliteDraftStore.initialize(directory);
        var clock = new MutableClock();
        var sessions = new HostedSessions(clock, List.of());
        var runtime = new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory", directory.toString()), RuntimeMode.HOSTED);
        var request = new MockHttpServletRequest();
        assertTrue(sessions.reserveLogin(request));
        sessions.authenticated(request.getSession(), new DefaultOidcUser(List.of(), new OidcIdToken("independent-final-commit-token", clock.instant(), clock.instant().plusSeconds(300), Map.of("iss", "https://final-commit-mock.invalid", "sub", "invented-owner"))));
        var lease = sessions.current(request).orElseThrow();
        request.setAttribute(HostedSessions.REQUEST_LEASE, lease);
        String id = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        var commandV1 = new studio.environment.core.workspace.DraftCommand(id, "0", requestId, studio.environment.core.workspace.DraftCommand.Format.JSON, SqliteDraftStoreTest.source("independent-final-commit", 1));
        var commandV2 = new studio.environment.core.workspace.NativeCommand.SaveDefinition(id, "0", requestId, studio.environment.core.workspace.DraftCommand.Format.JSON, Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
        var reachedCommit = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        WorkspaceCommit policy = connection -> {
            reachedCommit.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new java.sql.SQLException("MOCK_COMMIT_DEADLINE"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.sql.SQLException("MOCK_INTERRUPTED"); }
            WorkspaceCommit.authenticated(sessions, lease).commit(connection);
        };
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var saving = threads.submit(() -> {
                try { return nativeV2 ? runtime.nativeService(policy).mutate(lease.owner(), commandV2) : runtime.service(policy).put(lease.owner(), commandV1); }
                catch (WorkspaceRefusal refusal) { return refusal.code(); }
            });
            try {
                assertTrue(reachedCommit.await(3, TimeUnit.SECONDS));
                sessions.logout(request);
            } finally { release.countDown(); }
            assertEquals(WorkspaceRefusal.Code.FORBIDDEN, saving.get(5, TimeUnit.SECONDS));
        }
        // Reopen independently: neither the catalog nor its replay record survived denied commit.
        var reopened = new SqliteDraftStore(directory);
        assertTrue(reopened.list(lease.owner()).isEmpty());
        assertTrue(new NativeSqliteStore(reopened).list(lease.owner(), false).isEmpty());
        assertTrue(nativeV2 ? new NativeSqliteStore(reopened).replay(lease.owner(), commandV2).isEmpty() : reopened.replay(lease.owner(), commandV1).isEmpty());
    }

    @ParameterizedTest @CsvSource({"false", "true"})
    void confirmedCommitSurvivesLogoutAndExactReplayAfterNewLogin(boolean nativeV2) throws Exception {
        var directory = Files.createTempDirectory("es-commit-replay-mock-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SqliteDraftStore.initialize(directory);
        var clock = new MutableClock();
        var sessions = new HostedSessions(clock, List.of());
        var runtime = new WorkspaceRuntime(new MockEnvironment().withProperty("studio.workspace.directory", directory.toString()), RuntimeMode.HOSTED);
        var principal = new DefaultOidcUser(List.of(), new OidcIdToken("independent-replay-token", clock.instant(), clock.instant().plusSeconds(300), Map.of("iss", "https://replay-mock.invalid", "sub", "invented-owner")));
        var firstRequest = new MockHttpServletRequest();
        assertTrue(sessions.reserveLogin(firstRequest));
        sessions.authenticated(firstRequest.getSession(), principal);
        var firstLease = sessions.current(firstRequest).orElseThrow();
        firstRequest.setAttribute(HostedSessions.REQUEST_LEASE, firstLease);
        String id = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String source = nativeV2 ? Files.readString(Path.of("../../fixtures/native-v2/definition.json")) : SqliteDraftStoreTest.source("independent-replay", 1);
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        byte[] original = json.writeValueAsBytes(Map.of("expectedRevision", "0", "requestId", requestId, "format", "JSON", "source", source));
        firstRequest.setContent(original);
        var v1 = new WorkspaceController(runtime, sessions);
        var v2 = new NativeWorkspaceController(runtime, sessions);
        var first = nativeV2 ? v2.saveDefinition(id, firstRequest) : v1.put(id, firstRequest);
        assertEquals(200, first.getStatusCode().value());
        // A second admitted commit under the same live lease must be possible.
        firstRequest.setContent(json.writeValueAsBytes(Map.of("expectedRevision", "1", "requestId", UUID.randomUUID().toString(), "format", "JSON", "source", source)));
        assertEquals(200, (nativeV2 ? v2.saveDefinition(id, firstRequest) : v1.put(id, firstRequest)).getStatusCode().value());
        sessions.logout(firstRequest);
        var fresh = new MockHttpServletRequest();
        assertTrue(sessions.reserveLogin(fresh));
        assertInstanceOf(studio.environment.core.session.SessionLedger.Accepted.class, sessions.authenticated(fresh.getSession(), principal));
        fresh.setContent(original);
        var replay = nativeV2 ? v2.saveDefinition(id, fresh) : v1.put(id, fresh);
        assertEquals(200, replay.getStatusCode().value());
        String replayRevision = nativeV2 ? ((NativeWorkspaceController.View) replay.getBody()).body().get("workspaceRevision").asString() : ((WorkspaceController.DraftView) replay.getBody()).workspaceRevision();
        assertEquals("1", replayRevision);
        assertEquals("2", nativeV2 ? runtime.nativeStore().read(firstLease.owner(), id, Optional.empty(), false).workspaceRevision() : runtime.store().read(firstLease.owner(), id, Optional.empty()).workspaceRevision());
    }

}
