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
import studio.environment.server.export.GuardedPackageAssembler;
import studio.environment.server.export.V3GuardedPackageCandidate;
import studio.environment.core.observation.ObservationPort.Cancellation;

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
        NONE(0,0),COLLECTION(67_108_864,30_000_000_000L),SMALL(16_384,10_000_000_000L),COMPUTED_SELECTOR(16_777_216,30_000_000_000L),SEMANTIC(134_217_728,30_000_000_000L);
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
        private final boolean review;
        private boolean pinned;
        ViewScope(SessionLedger.Lease lease,String planId,HostedPlanService.ViewAdmission admission){
            this(lease,planId,admission,false);
        }
        ViewScope(SessionLedger.Lease lease,String planId,HostedPlanService.ViewAdmission admission,boolean review){
            this.lease=lease;this.planId=planId;this.admission=admission;this.review=review;
        }
        void run(Runnable transfer){admission.run(()->{transfer.run();return true;});}
        void verify(){
            service.requireOwned(lease,planId,studio.environment.core.plan.PlanDefinition.Version.V3);
            if(review)admission.verifyReview();
            else if(pinned)admission.verify();
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
        V3PlanReply workflow(V3PlanWorkflowReader.Route route,OwnedServletBody body){
            if(route==V3PlanWorkflowReader.Route.REVIEW)
                return new V3PlanReply.Acknowledgement(admission.reviewV3(new PlanMetadataReader().review(body)));
            if(route==V3PlanWorkflowReader.Route.VALIDATION){
                var request=new V3PlanWorkflowReader().validation(body);
                admission.pin(request.revision());pinned=true;
                return new V3PlanReply.Workflow(planId,V3PlanWorkflowEncoding.validation(request,admission.validationV3(),this::verify));
            }
            var request=request(route==V3PlanWorkflowReader.Route.CAPTURE?PlanViewRequest.Route.CAPTURE:PlanViewRequest.Route.PREVIEW,body);
            return new V3PlanReply.Workflow(planId,route==V3PlanWorkflowReader.Route.CAPTURE
                    ?V3PlanWorkflowEncoding.capture(admission,(PlanViewRequest.Capture)request,this::verify)
                    :V3PlanWorkflowEncoding.preview(admission.preview(((PlanViewRequest.Preview)request).profile(),
                            ((PlanViewRequest.Preview)request).all()?List.of():((PlanViewRequest.Preview)request).roots()),(PlanViewRequest.Preview)request,this::verify));
        }
        V3PlanReply computed(V3PlanComputedReader.Route route,OwnedServletBody body){
            var request=new V3PlanComputedReader().read(route,body);
            admission.pin(request.revision());pinned=true;
            var result=switch(route){
                case NODES -> V3PlanComputedViews.nodes(admission,request.target(),request.offset(),request.limit());
                case MEMBERSHIPS -> V3PlanComputedViews.memberships(admission,request.target(),request.offset(),request.limit());
                case COOCCURRENCES -> V3PlanComputedViews.cooccurrences(admission,request.target(),request.offset(),request.limit());
                case RULES -> V3PlanComputedViews.rules(admission,request.target(),request.offset(),request.limit());
                case CONTRIBUTORS -> V3PlanComputedViews.contributors(admission,request.target(),request.selector().orElseThrow(),request.offset(),request.limit(),true);
            };
            return new V3PlanReply.Physical(planId,V3PlanComputedEncoding.page(result,this::verify));
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
    void computedBody(SessionLedger.Lease lease,String planId,HttpServletRequest request,HttpServletResponse response,
            V3PlanTransfers.Operation operation,HostedPlanService.ViewAdmission admission,V3PlanComputedReader.Route route){
        var scope=new ViewScope(lease,planId,admission);
        start(lease,request,response,operation,200,route==V3PlanComputedReader.Route.CONTRIBUTORS?BodyMode.COMPUTED_SELECTOR:BodyMode.SMALL,
                admission,()->!admission.live(),body->scope.computed(route,body.orElseThrow()),Optional.of(scope));
    }
    void workflowBody(SessionLedger.Lease lease,String planId,HttpServletRequest request,HttpServletResponse response,
            V3PlanTransfers.Operation operation,HostedPlanService.ViewAdmission admission,V3PlanWorkflowReader.Route route){
        var scope=new ViewScope(lease,planId,admission,route==V3PlanWorkflowReader.Route.REVIEW);
        start(lease,request,response,operation,200,route==V3PlanWorkflowReader.Route.VALIDATION || route==V3PlanWorkflowReader.Route.REVIEW?BodyMode.SMALL:BodyMode.COLLECTION,
                admission,()->!admission.live(),body->scope.workflow(route,body.orElseThrow()),Optional.of(scope));
    }
    void packageBody(SessionLedger.Lease lease,String planId,HttpServletRequest request,HttpServletResponse response,
            V3PlanTransfers.Operation operation,HostedPlanService.ViewAdmission admission,V3GuardedPackageCandidate.Target target){
        var scope=new ViewScope(lease,planId,admission);
        startPackage(lease,request,response,operation,admission,()->!admission.live(),scope,target);
    }
    private void check(SessionLedger.Lease lease,V3PlanTransfers.Operation operation){
        if(!service.live(lease))throw new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED);
        if(operation.cancelled())throw new PlanRefusal(PlanRefusal.Code.CANCELLED);
    }
    private void startPackage(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,
            AutoCloseable resource,BooleanSupplier cancelled,ViewScope scope,V3GuardedPackageCandidate.Target target){
        OwnedAsyncCompletion completion=null;Optional<OwnedServletBody> body=Optional.empty();
        var cleanupUncertain=new AtomicBoolean();boolean asyncAttempted=false,started=false;
        try {
            check(lease,operation);requireJson(request);
            asyncAttempted=true;var context=request.startAsync();context.setTimeout(30_000);
            completion=new OwnedAsyncCompletion(context,outcome->operation.settlement(cleanupUncertain.get()?OwnedAsyncCompletion.Outcome.INCONCLUSIVE:outcome,sessions),Optional.empty());
            context.setTimeout(0);var owner=completion;check(lease,operation);owner.checkActive();
            long inputDeadline=System.nanoTime()+BodyMode.SMALL.nanos;
            body=Optional.of(new OwnedServletBody(request.getInputStream(),BodyMode.SMALL.bytes,inputDeadline,()->{check(lease,operation);owner.checkActive();return cancelled.getAsBoolean();}));
            var ownedBody=body;
            var worker=new Thread(null,()->{
                try {
                    scope.run(()->{
                        long deadline=clock.getAsLong()+30_000_000_000L;var outputAttempted=new AtomicBoolean();
                        Runnable authority=outputAuthority(lease,operation,owner,deadline,Optional.of(scope));
                        V3GuardedPackageCandidate.Result prepared;
                        try {
                            check(lease,operation);owner.checkActive();
                            var parsed=new V3PlanPackageReader().read(ownedBody.orElseThrow());
                            scope.admission.pin(parsed.revision());
                            prepared=new V3GuardedPackageCandidate().prepare(scope.admission,target,parsed.inputFingerprint());
                        } catch(RuntimeException failure) { sendFailure(response,failure,lease,operation,owner,deadline,Optional.of(scope)); return; }
                        if(prepared instanceof V3GuardedPackageCandidate.Result.Rejected refused) {
                            sendPackageCode(response,refused.code(),lease,operation,owner,deadline,Optional.of(scope));return;
                        }
                        try {
                            authority.run();response.setStatus(200);response.setHeader("Cache-Control","no-store");
                            response.setHeader("X-Environment-Studio-Qualified","false");
                            response.setHeader("Content-Disposition","attachment; filename=\"environment-studio-guarded-package.zip\"");
                            response.setContentType("application/zip");response.setHeader("Content-Length",null);
                            outputAttempted.set(true);
                            try(var output=new OwnedServletOutput(response.getOutputStream(),authority,deadline,clock)){
                                var written=new V3GuardedPackageCandidate().write((V3GuardedPackageCandidate.Result.Prepared)prepared,output,new Cancellation());
                                if(!(written instanceof GuardedPackageAssembler.Result.Candidate))throw new PlanRefusal(PlanRefusal.Code.EXPORT_UNAVAILABLE);
                                output.flush();authority.run();
                            }
                        } catch(IOException|RuntimeException failure) {
                            if(!outputAttempted.get() && !response.isCommitted())sendFailure(response,refusalRuntime(failure),lease,operation,owner,deadline,Optional.of(scope));
                            else LOG.warn("V3_PLAN_RESPONSE_ABORTED");
                        }
                    });
                } catch(RuntimeException failure){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
                finally {closeResources(ownedBody,resource,cleanupUncertain);owner.workerClosed();}
            },"hosted-v3-plan-package",0,false);
            worker.setDaemon(true);worker.start();started=true;
        } catch(IOException failure){refuseBeforeTransfer(response,new IllegalStateException("PLAN_INTERNAL_REFUSAL"));}
        catch(RuntimeException failure){refuseBeforeTransfer(response,failure);}
        finally {
            if(!started){closeResources(body,resource,cleanupUncertain);if(completion!=null)completion.workerClosed();
            else operation.settlement(asyncAttempted || cleanupUncertain.get()?OwnedAsyncCompletion.Outcome.INCONCLUSIVE:OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
        }
    }
    private void start(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,
            BodyMode mode,AutoCloseable resource,BooleanSupplier cancelled,Function<Optional<OwnedServletBody>,V3PlanReply> action,Optional<ViewScope> scope){
        OwnedAsyncCompletion completion=null;Optional<OwnedServletBody> body=Optional.empty();
        var cleanupUncertain=new AtomicBoolean();boolean asyncAttempted=false,started=false;
        try {
            check(lease,operation);if(mode!=BodyMode.NONE)requireJson(request);
            asyncAttempted=true;var context=request.startAsync();context.setTimeout(30_000);
            completion=new OwnedAsyncCompletion(context,outcome->operation.settlement(cleanupUncertain.get()?OwnedAsyncCompletion.Outcome.INCONCLUSIVE:outcome,sessions),scope.filter(value->value.review).map(value->value.admission));
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
        try {write(response,status,reply.wire(),verify,deadline,outputAttempted,responseMode(scope));}
        catch(RuntimeException failure){
            if(!outputAttempted.get() && !response.isCommitted())sendFailure(response,failure,lease,operation,completion,deadline,scope);
            else LOG.warn("V3_PLAN_RESPONSE_ABORTED");
        } catch(IOException failure){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
    }
    private void sendPackageCode(HttpServletResponse response,String code,SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion,long deadline,Optional<ViewScope> scope){
        if(response.isCommitted()){LOG.warn("V3_PLAN_RESPONSE_ABORTED");return;}
        var verify=outputAuthority(lease,operation,completion,deadline,scope);
        try {
            verify.run();response.resetBuffer();response.setHeader("Content-Length",null);
            write(response,packageStatus(code),Map.of("code",code),verify,deadline,new AtomicBoolean(),ResponseMode.SMALL);
        } catch(IOException|RuntimeException denied){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
    }
    private static int packageStatus(String code){
        return switch(code){
            case "SESSION_REQUIRED" -> 401;case "NOT_FOUND" -> 404;
            case "CONFLICT","PLAN_BUSY","CANCELLED","INCOMPLETE_TARGET" -> 409;
            case "CAPACITY" -> 429;case "EXPORT_UNAVAILABLE","CLEANUP_INCONCLUSIVE" -> 503;
            default -> 422;
        };
    }
    private static RuntimeException refusalRuntime(Exception failure){return failure instanceof RuntimeException runtime?runtime:new IllegalStateException("PLAN_INTERNAL_REFUSAL");}
    private void sendFailure(HttpServletResponse response,RuntimeException failure,SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion,long deadline,Optional<ViewScope> scope){
        if(response.isCommitted()){LOG.warn("V3_PLAN_RESPONSE_ABORTED");return;}
        var refusal=refusal(failure);var verify=outputAuthority(lease,operation,completion,deadline,scope);
        try {
            verify.run();response.resetBuffer();response.setHeader("Content-Length",null);
            if(refusal.closeConnection())response.setHeader("Connection","close");
            write(response,refusal.status(),Map.of("code",refusal.code()),verify,deadline,new AtomicBoolean(),responseMode(scope));
        } catch(IOException|RuntimeException denied){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
    }
    private static ResponseMode responseMode(Optional<ViewScope> scope){return scope.filter(value->!value.review).isPresent()?ResponseMode.VIEW:ResponseMode.SMALL;}
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
