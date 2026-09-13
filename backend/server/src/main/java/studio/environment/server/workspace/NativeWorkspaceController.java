package studio.environment.server.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.workspace.*;
import studio.environment.server.session.HostedSessions;

@RestController
@ConditionalOnProperty(name="studio.mode",havingValue="hosted")
public final class NativeWorkspaceController {
    private final WorkspaceRuntime runtime;
    private final HostedSessions sessions;
    private final NativeSnapshotCodec codec=new NativeSnapshotCodec();
    public NativeWorkspaceController(WorkspaceRuntime runtime,HostedSessions sessions){this.runtime=runtime;this.sessions=sessions;}
    /** The closed output projection is intentionally safe under Spring DEBUG return-value logging. */
    public static final class View {
        private final JsonNode body;
        View(JsonNode body){this.body=body.deepCopy();}
        @JsonValue public JsonNode body(){return body.deepCopy();}
        @Override public String toString(){return "NativeWorkspaceView[redacted]";}
    }
    @PutMapping(value="/api/v2/definitions/{objectId}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveDefinition(@PathVariable("objectId") String objectId,HttpServletRequest request){return mutate(objectId,false,false,request);}
    @PutMapping(value="/api/v2/profiles/{objectId}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveProfile(@PathVariable("objectId") String objectId,HttpServletRequest request){return mutate(objectId,true,false,request);}
    @PostMapping(value="/api/v2/definitions/{objectId}/publish",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publishDefinition(@PathVariable("objectId") String objectId,HttpServletRequest request){return mutate(objectId,false,true,request);}
    @PostMapping(value="/api/v2/profiles/{objectId}/publish",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publishProfile(@PathVariable("objectId") String objectId,HttpServletRequest request){return mutate(objectId,true,true,request);}
    private ResponseEntity<?> mutate(String id,boolean profile,boolean publish,HttpServletRequest request) {
        var lease=sessions.current(request).orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN));
        var owner=lease.owner();
        if(publish&&!profile&&!runtime.canPublish(owner))throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);
        var service=runtime.nativeService(WorkspaceCommit.authenticated(sessions,lease));
        try {
            var command=new NativeRequestReader().read(id,profile,publish,request.getInputStream());
            sessions.guard(lease,()->true).orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN));
            var result=service.mutate(owner,command);
            sessions.guard(lease,()->true).orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN));
            return response(200,view(result));
        }
        catch(IOException invalid){throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);}
    }
    @GetMapping("/api/v2/definitions") public ResponseEntity<?> definitions(HttpServletRequest request){return listing(false,request);}
    @GetMapping("/api/v2/profiles") public ResponseEntity<?> profiles(HttpServletRequest request){return listing(true,request);}
    private ResponseEntity<?> listing(boolean profile,HttpServletRequest request) {
        var owner=sessions.requireOwner(request);var root=NativeSnapshotCodec.JSON.createObjectNode();var array=root.putArray(profile?"profiles":"definitions");
        for(var revision:runtime.nativeStore().list(owner,profile)) {
            var item=array.addObject();item.put("objectId",revision.objectId());item.put("workspaceRevision",revision.workspaceRevision());item.put("nativeId",revision.content().nativeId());
            item.put("nativeRevision",revision.content().nativeRevision());item.put("sourceDigest",revision.sourceDigest());item.put("state",revision.state());
            if(revision.content() instanceof NativeRevision.Definition d){item.put("compilationKind",d.ready()?"ready-to-publish":"incomplete");item.put("logicalDigest",d.checked().logicalDigest());}
            else {var p=(NativeRevision.Profile)revision.content();item.put("contentDigest",p.checked().contentDigest());item.set("definition",NativeSnapshotCodec.JSON.valueToTree(p.definition()));}
        }
        if(!profile)root.put("canPublish",runtime.canPublish(owner));return response(200,new View(root));
    }
    @GetMapping("/api/v2/definitions/{objectId}") public ResponseEntity<?> definition(@PathVariable("objectId") String objectId,HttpServletRequest request){return read(objectId,Optional.empty(),false,request);}
    @GetMapping("/api/v2/profiles/{objectId}") public ResponseEntity<?> profile(@PathVariable("objectId") String objectId,HttpServletRequest request){return read(objectId,Optional.empty(),true,request);}
    @GetMapping("/api/v2/definitions/{objectId}/revisions/{revision}") public ResponseEntity<?> definitionRevision(@PathVariable("objectId") String objectId,@PathVariable("revision") String revision,HttpServletRequest request){return read(objectId,Optional.of(revision),false,request);}
    @GetMapping("/api/v2/profiles/{objectId}/revisions/{revision}") public ResponseEntity<?> profileRevision(@PathVariable("objectId") String objectId,@PathVariable("revision") String revision,HttpServletRequest request){return read(objectId,Optional.of(revision),true,request);}
    private ResponseEntity<?> read(String id,Optional<String> revision,boolean profile,HttpServletRequest request){return response(200,view(runtime.nativeStore().read(sessions.requireOwner(request),id,revision,profile)));}
    View view(NativeRevision revision) {
        var root=NativeSnapshotCodec.JSON.createObjectNode();root.put("objectId",revision.objectId());root.put("workspaceRevision",revision.workspaceRevision());root.put("sourceDigest",revision.sourceDigest());
        root.put("format",revision.format().name());root.put("source",revision.source());root.put("compilerVersion",revision.compilerVersion());root.put("schemaVersion",revision.schemaVersion());root.put("state",revision.state());
        var projection=root.putObject("projection");projection.set("model",codec.model(revision));
        if(revision.content() instanceof NativeRevision.Definition d) {
            projection.put("kind",d.ready()?"ready-to-publish":"incomplete");projection.put("logicalDigest",d.checked().logicalDigest());
            projection.set("bindingDigests",NativeSnapshotCodec.JSON.valueToTree(d.checked().bindingDigests()));projection.set("mechanisms",NativeSnapshotCodec.JSON.valueToTree(d.checked().mechanisms()));
            var diagnostics=projection.putArray("diagnostics");d.diagnostics().forEach(diag->{var item=diagnostics.addObject();item.put("phase",diag.phase().name().toLowerCase(Locale.ROOT));item.put("code",diag.code());item.put("pointer",diag.pointer());item.put("message",diag.message());});
        }else {var p=(NativeRevision.Profile)revision.content();root.set("definition",NativeSnapshotCodec.JSON.valueToTree(p.definition()));projection.put("kind","ready-to-publish");projection.put("contentDigest",p.checked().contentDigest());projection.putArray("diagnostics");}
        revision.publication().ifPresent(p->{var pub=root.putObject("publication");pub.put("digest",p.digest());pub.put("sourceRevision",p.sourceRevision());if(!revision.profile())pub.set("exportPolicies",NativeSnapshotCodec.JSON.valueToTree(p.exportPolicies()));});
        return new View(root);
    }
    @ExceptionHandler(WorkspaceRejection.class) public ResponseEntity<?> rejected(WorkspaceRejection rejection) {
        var root=NativeSnapshotCodec.JSON.createObjectNode();root.put("kind","rejected");var diagnostics=root.putArray("diagnostics");
        rejection.diagnostics().forEach(d->{var item=diagnostics.addObject();item.put("phase",d.phase().name().toLowerCase(Locale.ROOT));item.put("code",d.code());item.put("pointer",d.pointer());item.put("message",d.message());});
        return response(422,new View(root));
    }
    @ExceptionHandler(WorkspaceRefusal.class) public ResponseEntity<?> refused(WorkspaceRefusal refusal) {
        int status=switch(refusal.code()){case FORBIDDEN->403;case INVALID_REQUEST->400;case NOT_FOUND->404;case CONFLICT->409;case TOO_LARGE->413;case CAPACITY->429;case UNAVAILABLE->503;};
        var body=NativeSnapshotCodec.JSON.createObjectNode();body.put("code",refusal.getMessage());body.put("message","The workspace request could not be completed.");return response(status,new View(body));
    }
    private static ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
}
