package studio.environment.server.workspace;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.*;
import studio.environment.server.session.HostedSessions;

/** Draft/history authority only; mutable response encodings never escape the worker. */
@RestController
@ConditionalOnProperty(name="studio.mode",havingValue="hosted")
public final class V3NativeWorkspaceController {
    private final WorkspaceRuntime runtime;
    private final HostedSessions sessions;
    private final V3NativeSnapshotCodec codec=new V3NativeSnapshotCodec();
    public V3NativeWorkspaceController(WorkspaceRuntime runtime,HostedSessions sessions){this.runtime=runtime;this.sessions=sessions;}
    @PutMapping(value="/api/v3/definitions/{objectId}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public void saveDefinition(@PathVariable("objectId") String id,HttpServletRequest request,HttpServletResponse response)throws IOException {
        start(request,response,true,(lease,body)->{
            var command=new DraftRequestReader().read(id,body);verify(lease);body.close();
            var result=runtime.v3Service(WorkspaceCommit.authenticated(sessions,lease)).saveDefinition(lease.owner(),
                new NativeCommand.SaveDefinition(command.objectId(),command.expectedRevision(),command.requestId(),command.format(),command.source()));
            verify(lease);return view(result);
        });
    }
    @GetMapping("/api/v3/definitions")
    public void definitions(HttpServletRequest request,HttpServletResponse response)throws IOException {
        start(request,response,false,(lease,body)->{
            var root=V3NativeSnapshotCodec.JSON.createObjectNode();var array=root.putArray("definitions");
            for(var revision:runtime.v3Store().list(lease.owner(),false)){
                verify(lease);var d=(V3NativeRevision.Definition)revision.content();var item=array.addObject();
                item.put("objectId",revision.objectId());item.put("workspaceRevision",revision.workspaceRevision());
                item.put("nativeId",d.nativeId());item.put("nativeRevision",d.nativeRevision());item.put("sourceDigest",revision.sourceDigest());
                item.put("state",revision.state());item.put("compilationKind",d.historicalReady()?"historical-ready":"incomplete");item.put("logicalDigest",d.checked().logicalDigest());
            }return root;
        });
    }
    @GetMapping("/api/v3/definitions/{objectId}")
    public void definition(@PathVariable("objectId") String id,HttpServletRequest request,HttpServletResponse response)throws IOException {read(id,Optional.empty(),request,response);}
    @GetMapping("/api/v3/definitions/{objectId}/revisions/{revision}")
    public void definitionRevision(@PathVariable("objectId") String id,@PathVariable("revision") String revision,HttpServletRequest request,HttpServletResponse response)throws IOException {read(id,Optional.of(revision),request,response);}
    private void read(String id,Optional<String> revision,HttpServletRequest request,HttpServletResponse response)throws IOException {
        start(request,response,false,(lease,body)->view(runtime.v3Store().read(lease.owner(),id,revision,false)));
    }
    @PutMapping(value="/api/v3/profiles/{objectId}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public void saveProfile(@PathVariable("objectId") String id,HttpServletRequest request,HttpServletResponse response)throws IOException {
        start(request,response,true,(lease,body)->{
            var command=new V3ProfileRequestReader().read(id,body);verify(lease);body.close();
            var result=runtime.v3ProfileService(WorkspaceCommit.authenticated(sessions,lease)).saveProfile(lease.owner(),command);
            verify(lease);return profileView(result);
        });
    }
    @GetMapping("/api/v3/profiles")
    public void profiles(HttpServletRequest request,HttpServletResponse response)throws IOException {
        start(request,response,false,(lease,body)->{
            var root=V3NativeSnapshotCodec.JSON.createObjectNode();var array=root.putArray("profiles");
            for(var revision:runtime.v3Store().list(lease.owner(),true)){
                verify(lease);var profile=(V3NativeRevision.Profile)revision.content();var item=array.addObject();
                item.put("objectId",revision.objectId());item.put("workspaceRevision",revision.workspaceRevision());
                item.put("nativeId",profile.nativeId());item.put("nativeRevision",profile.nativeRevision());
                item.put("sourceDigest",revision.sourceDigest());item.put("state",revision.state());
                item.put("contentDigest",profile.checked().contentDigest());item.set("definition",V3NativeSnapshotCodec.JSON.valueToTree(profile.definition()));
            }return root;
        });
    }
    @GetMapping("/api/v3/profiles/{objectId}")
    public void profile(@PathVariable("objectId") String id,HttpServletRequest request,HttpServletResponse response)throws IOException {readProfile(id,Optional.empty(),request,response);}
    @GetMapping("/api/v3/profiles/{objectId}/revisions/{revision}")
    public void profileRevision(@PathVariable("objectId") String id,@PathVariable("revision") String revision,HttpServletRequest request,HttpServletResponse response)throws IOException {readProfile(id,Optional.of(revision),request,response);}
    private void readProfile(String id,Optional<String> revision,HttpServletRequest request,HttpServletResponse response)throws IOException {
        start(request,response,false,(lease,body)->profileView(runtime.v3Store().read(lease.owner(),id,revision,true)));
    }
    private ObjectNode profileView(V3NativeRevision revision){
        var profile=(V3NativeRevision.Profile)revision.content();var root=V3NativeSnapshotCodec.JSON.createObjectNode();
        root.put("objectId",revision.objectId());root.put("workspaceRevision",revision.workspaceRevision());root.put("sourceDigest",revision.sourceDigest());
        root.put("format",revision.format().name());root.put("source",revision.source());root.put("compilerVersion",revision.compilerVersion());root.put("schemaVersion",revision.schemaVersion());root.put("state",revision.state());
        root.set("definition",V3NativeSnapshotCodec.JSON.valueToTree(profile.definition()));var projection=root.putObject("projection");
        projection.put("kind","structurally-valid");projection.set("model",codec.model(revision));projection.put("contentDigest",profile.checked().contentDigest());projection.putArray("diagnostics");
        revision.publication().ifPresent(p->{var publication=root.putObject("publication");publication.put("digest",p.digest());publication.put("sourceRevision",p.sourceRevision());});return root;
    }
    @FunctionalInterface private interface Action {JsonNode run(SessionLedger.Lease lease,V3WorkspaceBody body);}
    private void verify(SessionLedger.Lease lease){sessions.guard(lease,()->true).orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN));}
    private void start(HttpServletRequest request,HttpServletResponse response,boolean hasBody,Action action)throws IOException {
        SessionLedger.Lease lease=null;V3WorkspaceOperations.Operation operation=null;V3WorkspaceCompletion completion=null;V3WorkspaceBody body=null;boolean started=false,async=false;
        try{
            if(!(request.getAttribute(HostedSessions.REQUEST_LEASE) instanceof SessionLedger.Lease captured))throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);
            lease=captured;verify(lease);operation=runtime.v3Operations().admit(lease);verify(lease);
            if(hasBody){String type=request.getContentType();if(Collections.list(request.getHeaders("Content-Type")).size()!=1||type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);}
            AsyncContext context=request.startAsync();async=true;context.setTimeout(30_000);
            var ownedOperation=operation;
            completion=new V3WorkspaceCompletion(context,outcome->{if(outcome==V3WorkspaceCompletion.Outcome.COMPLETE)ownedOperation.complete(sessions);else if(outcome==V3WorkspaceCompletion.Outcome.INCONCLUSIVE)ownedOperation.inconclusive(sessions);});
            context.setTimeout(0);var original=lease;
            var ownedCompletion=completion;
            Runnable check=()->{verify(original);ownedCompletion.check();};
            if(hasBody)body=new V3WorkspaceBody(request.getInputStream(),check,System.nanoTime()+30_000_000_000L);
            var ownedBody=body;
            Thread worker=new Thread(null,()->{
                try{
                    JsonNode result;int status=200;
                    try{check.run();result=action.run(original,ownedBody);check.run();}
                    catch(WorkspaceRejection rejected){verify(original);result=rejected(rejected);status=422;}
                    transfer(response,status,result,check);
                }catch(RuntimeException failure){safeError(response,live(original)?failure:new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN));}
                finally{if(ownedBody!=null)ownedBody.close();ownedCompletion.workerClosed();}
            },"hosted-v3-workspace",0,false);
            worker.setDaemon(true);worker.start();started=true;
        }catch(IOException failure){safeError(response,new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE));}
        catch(RuntimeException failure){safeError(response,failure);}
        finally{if(!started){if(body!=null)body.close();if(completion!=null)completion.workerClosed();else if(operation!=null){if(async)operation.inconclusive(sessions);else operation.complete(sessions);}}}
    }
    private boolean live(SessionLedger.Lease lease){return sessions.guard(lease,()->true).isPresent();}
    private static void transfer(HttpServletResponse response,int status,JsonNode value,Runnable verify){
        long deadline=System.nanoTime()+30_000_000_000L;
        try(var bytes=new V3WorkspaceEncoding(verify,deadline)){
            // Jackson may close its target; the owning scope alone wipes the encoded chunks.
            var sink=new OutputStream(){public void write(int value){bytes.write(value);}public void write(byte[] value,int offset,int count){bytes.write(value,offset,count);}};
            V3NativeSnapshotCodec.JSON.writeValue(sink,value);bytes.check();
            response.setStatus(status);response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
            try(var output=new V3WorkspaceOutput(response.getOutputStream(),verify,deadline)){bytes.check();bytes.transfer(output);}
        }catch(IOException failure){throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);}
    }
    private static void safeError(HttpServletResponse response,RuntimeException failure){
        // Container completion aborts a committed partial response; never append a JSON result.
        if(response.isCommitted())return;
        WorkspaceRefusal.Code code=failure instanceof WorkspaceRefusal refusal?refusal.code():WorkspaceRefusal.Code.UNAVAILABLE;
        int status=switch(code){case FORBIDDEN->403;case INVALID_REQUEST->400;case NOT_FOUND->404;case CONFLICT->409;case TOO_LARGE->413;case CAPACITY->429;case UNAVAILABLE->503;};
        try{response.resetBuffer();response.setHeader("Content-Length",null);response.setHeader("Cache-Control","no-store");response.setStatus(status);response.setContentType("application/json");
            var output=response.getOutputStream();if(!output.isReady())return;
            output.write(("{\"code\":\""+code.name()+"\",\"message\":\"The workspace request could not be completed.\"}").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }catch(IOException|RuntimeException ignored){/* No retry or delivery claim. */}
    }
    private ObjectNode view(V3NativeRevision revision){
        var root=V3NativeSnapshotCodec.JSON.createObjectNode();root.put("objectId",revision.objectId());root.put("workspaceRevision",revision.workspaceRevision());root.put("sourceDigest",revision.sourceDigest());
        root.put("format",revision.format().name());root.put("source",revision.source());root.put("compilerVersion",revision.compilerVersion());root.put("schemaVersion",revision.schemaVersion());root.put("state",revision.state());
        var d=(V3NativeRevision.Definition)revision.content();var projection=root.putObject("projection");projection.put("kind",d.historicalReady()?"historical-ready":"incomplete");projection.set("model",codec.model(revision));
        projection.put("logicalDigest",d.checked().logicalDigest());projection.set("bindingDigests",V3NativeSnapshotCodec.JSON.valueToTree(d.checked().bindingDigests()));projection.set("mechanisms",V3NativeSnapshotCodec.JSON.valueToTree(d.checked().mechanisms()));
        var diagnostics=projection.putArray("diagnostics");d.diagnostics().forEach(diag->{var item=diagnostics.addObject();item.put("phase",diag.phase().name().toLowerCase(Locale.ROOT));item.put("code",diag.code());item.put("pointer",diag.pointer());item.put("message",diag.message());});
        revision.publication().ifPresent(p->{var pub=root.putObject("publication");pub.put("digest",p.digest());pub.put("sourceRevision",p.sourceRevision());pub.set("exportPolicies",V3NativeSnapshotCodec.JSON.valueToTree(p.exportPolicies()));});return root;
    }
    private static ObjectNode rejected(WorkspaceRejection rejection){var root=V3NativeSnapshotCodec.JSON.createObjectNode();root.put("kind","rejected");var diagnostics=root.putArray("diagnostics");rejection.diagnostics().forEach(d->{var item=diagnostics.addObject();item.put("phase",d.phase().name().toLowerCase(Locale.ROOT));item.put("code",d.code());item.put("pointer",d.pointer());item.put("message",d.message());});return root;}
    @Override public String toString(){return "V3NativeWorkspaceController[redacted]";}
}
