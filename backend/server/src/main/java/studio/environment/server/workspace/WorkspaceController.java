package studio.environment.server.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.core.workspace.*;
import studio.environment.server.session.HostedSessions;

@RestController
@ConditionalOnProperty(name = "studio.mode", havingValue = "hosted")
public final class WorkspaceController {
    private final WorkspaceRuntime runtime;
    private final HostedSessions sessions;
    private final SnapshotCodec codec = new SnapshotCodec();
    public WorkspaceController(WorkspaceRuntime runtime, HostedSessions sessions) { this.runtime = runtime; this.sessions = sessions; }
    public record Diagnostic(String phase, String code, String pointer, String message) { }
    public record Projection(String kind, JsonNode model, List<Diagnostic> diagnostics) {
        @Override public String toString() { return "Projection[content=REDACTED]"; }
    }
    public record DraftView(String objectId, String workspaceRevision, String sourceDigest, String format, String source,
                            String compilerVersion, String schemaVersion, Projection projection) {
        @Override public String toString() { return "DraftView[content=REDACTED]"; }
    }
    public record Listing(List<DraftStore.Summary> definitions) {
        @Override public String toString() { return "Listing[content=REDACTED]"; }
    }
    public record Rejected(String kind, List<Diagnostic> diagnostics) {
        @Override public String toString() { return "Rejected[content=REDACTED]"; }
    }
    public record ErrorView(String code, String message) { }
    @PutMapping(value = "/api/v1/definitions/{objectId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> put(@PathVariable("objectId") String objectId, HttpServletRequest request) {
        var owner = sessions.requireOwner(request);
        var service = runtime.service();
        try {
            var result = service.put(owner, new DraftRequestReader().read(objectId, request.getInputStream()));
            return switch (result) {
                case DraftWorkspace.Saved saved -> response(200, view(saved.draft()));
                case DraftWorkspace.Rejected rejected -> response(422, new Rejected("rejected", diagnostics(rejected.rejection().diagnostics())));
            };
        } catch (IOException failure) { throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST); }
    }
    @GetMapping("/api/v1/definitions")
    public ResponseEntity<?> list(HttpServletRequest request) { return response(200, new Listing(runtime.store().list(sessions.requireOwner(request)))); }
    @GetMapping("/api/v1/definitions/{objectId}")
    public ResponseEntity<?> current(@PathVariable("objectId") String objectId, HttpServletRequest request) {
        return response(200, view(runtime.store().read(sessions.requireOwner(request), objectId, Optional.empty())));
    }
    @GetMapping("/api/v1/definitions/{objectId}/revisions/{revision}")
    public ResponseEntity<?> revision(@PathVariable("objectId") String objectId, @PathVariable("revision") String revision, HttpServletRequest request) {
        return response(200, view(runtime.store().read(sessions.requireOwner(request), objectId, Optional.of(revision))));
    }
    @ExceptionHandler(WorkspaceRefusal.class)
    public ResponseEntity<?> refusal(WorkspaceRefusal refusal) {
        int status = switch (refusal.code()) {
            case FORBIDDEN -> 403; case INVALID_REQUEST -> 400; case TOO_LARGE -> 413; case CONFLICT -> 409;
            case NOT_FOUND -> 404; case CAPACITY, UNAVAILABLE -> 503;
        };
        return response(status, new ErrorView(refusal.getMessage(), switch (refusal.code()) {
            case FORBIDDEN -> "Publication is not authorized.";
            case INVALID_REQUEST -> "Provide a valid bounded draft request.";
            case TOO_LARGE -> "Reduce the draft request size.";
            case CONFLICT -> "Read the current revision before retrying with a new request identifier.";
            case NOT_FOUND -> "The requested draft is not available.";
            case CAPACITY, UNAVAILABLE -> "The draft workspace is unavailable; contact the operator.";
        }));
    }
    private static ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
    private static List<Diagnostic> diagnostics(List<DefinitionDiagnostic> diagnostics) {
        return diagnostics.stream().map(d -> new Diagnostic(d.phase().name().toLowerCase(Locale.ROOT), d.code(), d.pointer(), d.message())).toList();
    }
    DraftView view(SavedDraft saved) {
        var model = (ObjectNode) codec.view(saved.projection()).get("draft");
        lower(model, "status");
        for (var entity : model.get("entityTypes")) for (var field : entity.get("fields")) {
            lower((ObjectNode) field, "valueType", "classification", "sensitivity");
        }
        for (var relation : model.get("relations")) lower((ObjectNode) relation, "kind");
        return new DraftView(saved.objectId(), saved.workspaceRevision(), saved.sourceDigest(), saved.format().name(), saved.source(), saved.compilerVersion(), saved.schemaVersion(),
                new Projection("incomplete", model, diagnostics(saved.projection().diagnostics())));
    }
    private static void lower(ObjectNode object, String... fields) { for (String field : fields) object.put(field, object.get(field).asString().toLowerCase(Locale.ROOT)); }
}
