package studio.environment.server.plan;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.workspace.WorkspaceRefusal;
import studio.environment.server.session.HostedSessions;

/** One admitted worker owns its application resources through sole async settlement. */
final class V3PlanTransport {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(V3PlanTransport.class);
    private final HostedPlanService service;
    private final HostedSessions sessions;
    private final LongSupplier clock;
    V3PlanTransport(HostedPlanService service,HostedSessions sessions){this(service,sessions,System::nanoTime);}
    V3PlanTransport(HostedPlanService service,HostedSessions sessions,LongSupplier clock){
        this.service=Objects.requireNonNull(service);this.sessions=Objects.requireNonNull(sessions);this.clock=Objects.requireNonNull(clock);
    }
    private enum BodyMode {
        NONE(0,0),SMALL(16_384,10_000_000_000L),SEMANTIC(134_217_728,30_000_000_000L);
        final int bytes;final long nanos;
        BodyMode(int bytes,long nanos){this.bytes=bytes;this.nanos=nanos;}
    }
    @FunctionalInterface interface BodyAction { V3PlanReply apply(OwnedServletBody body); }
    void read(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,Supplier<V3PlanReply> action){
        start(lease,request,response,operation,status,BodyMode.NONE,()->{},()->false,body->action.get(),Optional.empty());
    }
    void body(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,AutoCloseable resource,BooleanSupplier cancelled,BodyAction action){
        start(lease,request,response,operation,status,BodyMode.SMALL,resource,cancelled,body->action.apply(body.orElseThrow()),Optional.empty());
    }
    void semanticBody(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,AutoCloseable resource,BooleanSupplier cancelled,BodyAction action){
        start(lease,request,response,operation,status,BodyMode.SEMANTIC,resource,cancelled,body->action.apply(body.orElseThrow()),Optional.empty());
    }
    enum PhysicalRoute {
        DOCUMENTS(PlanViewRequest.Route.DOCUMENTS),ENTITIES(PlanViewRequest.Route.ENTITIES),
        RELATIONS(PlanViewRequest.Route.RELATIONS),DRAFT(PlanViewRequest.Route.DRAFT),
        CONTAINMENT(PlanViewRequest.Route.CONTAINMENT),PLACEMENTS(PlanViewRequest.Route.PLACEMENTS),
        BINDINGS(PlanViewRequest.Route.BINDINGS),DOCUMENT(PlanViewRequest.Route.DOCUMENT),
        BINDING_LOCATIONS(PlanViewRequest.Route.BINDING_LOCATIONS);
        final PlanViewRequest.Route request;
        PhysicalRoute(PlanViewRequest.Route request){this.request=request;}
    }
    private enum ResponseMode {
        SMALL(32_768),VIEW(134_217_728);
        final int bytes;
        ResponseMode(int bytes){this.bytes=bytes;}
    }
    /** A pinned view stays executing through encoding, writable waits and final verification. */
    private final class ViewScope {
        private final SessionLedger.Lease lease;
        private final String planId;
        private final HostedPlanService.ViewAdmission admission;
        private boolean pinned;
        ViewScope(SessionLedger.Lease lease,String planId,HostedPlanService.ViewAdmission admission){
            this.lease=lease;this.planId=planId;this.admission=admission;
        }
        void run(Runnable transfer){admission.run(()->{transfer.run();return true;});}
        void verify(){
            service.requireOwned(lease,planId,studio.environment.core.plan.PlanDefinition.Version.V3);
            if(pinned)admission.verify();
            else if(!admission.live())throw new PlanRefusal(PlanRefusal.Code.CANCELLED);
        }
        private PlanViewRequest request(PlanViewRequest.Route route,OwnedServletBody body){
            var request=new PlanViewReader().read(route,body);
            admission.pin(request.revision());pinned=true;return request;
        }
        V3PlanReply materialize(OwnedServletBody body){
            var request=request(PlanViewRequest.Route.MATERIALIZATION,body);
            return new V3PlanReply.Materialized(planId,request.revision(),admission.materialize());
        }
        V3PlanReply physical(PhysicalRoute route,OwnedServletBody body){
            var request=request(route.request,body);
            var result=switch(route){
                case DOCUMENTS -> V3PlanPhysicalViews.documents(admission);
                case DOCUMENT -> {
                    var doc=(PlanViewRequest.Document)request;
                    var value=admission.document(doc.side()==PlanViewRequest.Side.TARGET,doc.documentId(),
                            studio.environment.core.plan.PlanPorts.ViewMode.valueOf(doc.mode().name()),doc.disclosed());
                    yield PlanViewProjection.object("revision",request.revision(),"documentId",value.documentId(),
                            "side",doc.side().name().toLowerCase(java.util.Locale.ROOT),"mode",doc.mode().name().toLowerCase(java.util.Locale.ROOT),
                            "text",value.text(),"exact",value.exact(),"redacted",value.redacted(),
                            "unmappedConcreteMayRemain",value.unmappedConcreteMayRemain(),"omissions",value.omissions());
                }
                case BINDING_LOCATIONS -> {
                    var page=(PlanViewRequest.BindingLocations)request;
                    yield V3PlanPhysicalViews.locations(admission,page.entity(),page.fieldId(),
                            page.side()==PlanViewRequest.Side.TARGET,page.offset(),page.limit(),page.disclosed());
                }
                case ENTITIES -> {
                    var page=(PlanViewRequest.GraphPage)request;
                    yield V3PlanPhysicalViews.entities(admission,page.side()==PlanViewRequest.Side.TARGET,page.offset(),page.limit());
                }
                case RELATIONS -> {
                    var page=(PlanViewRequest.GraphPage)request;
                    yield V3PlanPhysicalViews.relations(admission,page.side()==PlanViewRequest.Side.TARGET,page.offset(),page.limit());
                }
                case DRAFT -> {
                    var page=(PlanViewRequest.DraftPage)request;
                    yield V3PlanPhysicalViews.draft(admission,page.offset(),page.limit());
                }
                case CONTAINMENT -> {
                    var page=(PlanViewRequest.DraftPage)request;
                    yield V3PlanPhysicalViews.containment(admission,page.offset(),page.limit());
                }
                case PLACEMENTS -> {
                    var page=(PlanViewRequest.Placements)request;
                    yield V3PlanPhysicalViews.placements(admission,page.documentId(),page.projectionId(),page.offset(),page.limit());
                }
                case BINDINGS -> {
                    var page=(PlanViewRequest.Bindings)request;
                    yield V3PlanPhysicalViews.bindings(admission,page.entity(),page.offset(),page.limit());
                }
            };
            return new V3PlanReply.Physical(planId,result);
        }
    }
    void materializationBody(SessionLedger.Lease lease,String planId,HttpServletRequest request,HttpServletResponse response,
            V3PlanTransfers.Operation operation,HostedPlanService.ViewAdmission admission){
        var scope=new ViewScope(lease,planId,admission);
        start(lease,request,response,operation,200,BodyMode.SMALL,admission,()->!admission.live(),
                body->scope.materialize(body.orElseThrow()),Optional.of(scope));
    }
    void physicalBody(SessionLedger.Lease lease,String planId,HttpServletRequest request,HttpServletResponse response,
            V3PlanTransfers.Operation operation,HostedPlanService.ViewAdmission admission,PhysicalRoute route){
        var scope=new ViewScope(lease,planId,admission);
        start(lease,request,response,operation,200,BodyMode.SMALL,admission,()->!admission.live(),
                body->scope.physical(route,body.orElseThrow()),Optional.of(scope));
    }
    private void check(SessionLedger.Lease lease,V3PlanTransfers.Operation operation){
        if(!service.live(lease))throw new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED);
        if(operation.cancelled())throw new PlanRefusal(PlanRefusal.Code.CANCELLED);
    }
    private void start(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,
            BodyMode mode,AutoCloseable resource,BooleanSupplier cancelled,Function<Optional<OwnedServletBody>,V3PlanReply> action,Optional<ViewScope> scope){
        OwnedAsyncCompletion completion=null;Optional<OwnedServletBody> body=Optional.empty();
        var cleanupUncertain=new AtomicBoolean();boolean asyncAttempted=false,started=false;
        try {
            check(lease,operation);if(mode!=BodyMode.NONE)requireJson(request);
            asyncAttempted=true;var context=request.startAsync();context.setTimeout(30_000);
            completion=new OwnedAsyncCompletion(context,outcome->operation.settlement(cleanupUncertain.get()?OwnedAsyncCompletion.Outcome.INCONCLUSIVE:outcome,sessions));
            context.setTimeout(0);var owner=completion;check(lease,operation);owner.checkActive();
            if(mode!=BodyMode.NONE){
                long deadline=System.nanoTime()+mode.nanos;
                body=Optional.of(new OwnedServletBody(request.getInputStream(),mode.bytes,deadline,()->{
                    check(lease,operation);owner.checkActive();return cancelled.getAsBoolean();
                }));
            }
            var ownedBody=body;
            var worker=new Thread(null,()->{
                try {
                    Runnable transfer=()->{
                        V3PlanReply reply;
                        try {check(lease,operation);owner.checkActive();reply=Objects.requireNonNull(action.apply(ownedBody));}
                        catch(RuntimeException failure){sendFailure(response,failure,lease,operation,owner,clock.getAsLong()+30_000_000_000L,scope);return;}
                        sendReply(response,status,reply,lease,operation,owner,scope);
                    };
                    if(scope.isPresent())scope.orElseThrow().run(transfer);else transfer.run();
                } catch(RuntimeException failure){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
                finally {
                    closeResources(ownedBody,resource,cleanupUncertain);
                    owner.workerClosed();
                }
            },"hosted-v3-plan-transfer",0,false);
            worker.setDaemon(true);worker.start();started=true;
        } catch(IOException failure){refuseBeforeTransfer(response,new IllegalStateException("PLAN_INTERNAL_REFUSAL"));}
        catch(RuntimeException failure){refuseBeforeTransfer(response,failure);}
        finally {
            if(!started){
                closeResources(body,resource,cleanupUncertain);
                if(completion!=null)completion.workerClosed();
                else operation.settlement(asyncAttempted || cleanupUncertain.get()?OwnedAsyncCompletion.Outcome.INCONCLUSIVE:OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
            }
        }
    }
    private static void closeResources(Optional<OwnedServletBody> body,AutoCloseable resource,AtomicBoolean uncertain){
        try {body.ifPresent(OwnedServletBody::close);}catch(RuntimeException failure){uncertain.set(true);}
        try {resource.close();}catch(Exception failure){uncertain.set(true);}
    }
    private Runnable outputAuthority(SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion,long deadline,Optional<ViewScope> scope){
        return ()->{check(lease,operation);completion.checkActive();if(deadline-clock.getAsLong()<=0)throw new PlanRefusal(PlanRefusal.Code.CANCELLED);scope.ifPresent(ViewScope::verify);};
    }
    private void sendReply(HttpServletResponse response,int status,V3PlanReply reply,SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion,Optional<ViewScope> scope){
        long deadline=clock.getAsLong()+30_000_000_000L;var outputAttempted=new AtomicBoolean();
        Runnable authority=outputAuthority(lease,operation,completion,deadline,scope);
        Runnable verify=()->{authority.run();reply.verify(service,lease);};
        try {write(response,status,reply.wire(),verify,deadline,outputAttempted,scope.isPresent()?ResponseMode.VIEW:ResponseMode.SMALL);}
        catch(RuntimeException failure){
            if(!outputAttempted.get() && !response.isCommitted())sendFailure(response,failure,lease,operation,completion,deadline,scope);
            else LOG.warn("V3_PLAN_RESPONSE_ABORTED");
        } catch(IOException failure){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
    }
    private void sendFailure(HttpServletResponse response,RuntimeException failure,SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion,long deadline,Optional<ViewScope> scope){
        if(response.isCommitted()){LOG.warn("V3_PLAN_RESPONSE_ABORTED");return;}
        var refusal=refusal(failure);var verify=outputAuthority(lease,operation,completion,deadline,scope);
        try {
            verify.run();response.resetBuffer();response.setHeader("Content-Length",null);
            if(refusal.closeConnection())response.setHeader("Connection","close");
            write(response,refusal.status(),Map.of("code",refusal.code()),verify,deadline,new AtomicBoolean(),scope.isPresent()?ResponseMode.VIEW:ResponseMode.SMALL);
        } catch(IOException|RuntimeException denied){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
    }
    private void write(HttpServletResponse response,int status,Object value,Runnable verify,long deadline,AtomicBoolean outputAttempted,ResponseMode mode)throws IOException {
        try(var encoded=new PlanViewEncoding(mode.bytes,verify)){
            encoded.encode(value);verify.run();outputAttempted.set(true);
            var stream=response.getOutputStream();verify.run();
            response.setStatus(status);response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setContentLength(encoded.size());
            try(var output=new OwnedServletOutput(stream,verify,deadline,clock)){
                encoded.write(output,verify);output.flush();verify.run();
            }
        }
    }
    static void requireJson(HttpServletRequest request){
        String type=request.getContentType();
        if(Collections.list(request.getHeaders("Content-Type")).size()!=1 || type==null || !type.split(";",2)[0].trim().equalsIgnoreCase("application/json"))
            throw new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY);
    }
    static void refuseBeforeTransfer(HttpServletResponse response,RuntimeException failure){
        if(response.isCommitted()){LOG.warn("V3_PLAN_RESPONSE_ABORTED");return;}
        var refusal=refusal(failure);response.setStatus(refusal.status());response.setHeader("Cache-Control","no-store");response.setContentLength(0);
        response.setHeader("X-Environment-Studio-Code",refusal.code());if(refusal.closeConnection())response.setHeader("Connection","close");
    }
    private record Refusal(int status,String code,boolean closeConnection){}
    private static Refusal refusal(RuntimeException failure){
        if(failure instanceof WorkspaceRefusal workspace){
            if(workspace.code()==WorkspaceRefusal.Code.NOT_FOUND)return new Refusal(404,"NOT_FOUND",false);
            if(workspace.code()==WorkspaceRefusal.Code.UNAVAILABLE)return new Refusal(503,"PLAN_SERVICES_UNAVAILABLE",false);
        }
        if(failure instanceof PlanRuntime.Unavailable)return new Refusal(503,"PLAN_SERVICES_UNAVAILABLE",false);
        if(failure instanceof PlanRuntime.DestinationDenied)return new Refusal(403,"DESTINATION_DENIED",false);
        if(failure instanceof PlanBodyFailure body)return new Refusal(body.code()==PlanBodyFailure.Code.BODY_TOO_LARGE?413:body.code()==PlanBodyFailure.Code.CANCELLED?409:400,body.code().name(),true);
        if(failure instanceof PlanRefusal refused){
            int status=switch(refused.code()){
                case SESSION_REQUIRED -> 401;case NOT_FOUND -> 404;
                case CONFLICT,PLAN_BUSY,CREDENTIALS_ALREADY_CONSUMED,RESERVATION_EXPIRED,STALE_PREVIEW,CANCELLED -> 409;
                case CAPACITY -> 429;case EXPORT_UNAVAILABLE,CLEANUP_INCONCLUSIVE -> 503;
                default -> 422;
            };
            return new Refusal(status,refused.code().name(),false);
        }
        return new Refusal(500,"PLAN_INTERNAL_REFUSAL",false);
    }
}
