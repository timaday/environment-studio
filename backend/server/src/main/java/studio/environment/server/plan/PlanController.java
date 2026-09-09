package studio.environment.server.plan;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

/** Closed initial routes. Raw bodies are read only by immediately started admitted workers, never queued. */
@RestController
@ConditionalOnProperty(name="studio.mode",havingValue="hosted")
public final class PlanController {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PlanController.class);
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final Semaphore METADATA_READERS=new Semaphore(4);
    private final PlanRuntime runtime;
    public PlanController(PlanRuntime runtime) {this.runtime=runtime;}
    @GetMapping("/api/v1/destinations")
    public void destinations(HttpServletRequest request,HttpServletResponse response) throws IOException {
        var lease=lease(request); write(response,200,Map.of("destinations",runtime.visible(lease.owner())));
    }
    @GetMapping("/api/v1/plans/current")
    public void current(HttpServletRequest request,HttpServletResponse response) throws IOException {
        summaryResponse(lease(request),Optional.empty(),response);
    }
    @GetMapping("/api/v1/plans/{planId}")
    public void summary(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        summaryResponse(lease(request),Optional.of(planId),response);
    }
    private void summaryResponse(SessionLedger.Lease lease,Optional<String> planId,HttpServletResponse response) throws IOException {
        var service=runtime.service();
        try(var slot=metadata();var encoded=new PlanViewEncoding(32_768)) {
            var snapshot=service.view(lease,planId);encoded.encode(view(snapshot));
            Runnable verify=()->service.verifySummary(lease,snapshot);verify.run();
            var output=response.getOutputStream();verify.run();
            response.setStatus(200);response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setContentLength(encoded.size());
            encoded.write(output,verify);output.flush();verify.run();
        } catch(RuntimeException failure) {
            if(response.isCommitted())throw new IOException("SUMMARY_TRANSFER_REFUSED");
            response.resetBuffer();response.setHeader("Content-Length",null);throw failure;
        }
    }
    @PostMapping("/api/v1/plans")
    public void create(HttpServletRequest request,HttpServletResponse response) throws IOException {
        var lease=lease(request); runtime.service();
        var slot=metadata();
        start(lease,request,response,slot,()->!runtime.service().live(lease),16_384,10,201,body->ack(runtime.create(lease,new PlanMetadataReader().create(body))));
    }
    @PostMapping("/api/v1/plans/{planId}/inspections")
    public void reserve(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var lease=lease(request); var service=runtime.service(); service.requireOwned(lease,planId);
        var slot=metadata();
        start(lease,request,response,slot,()->!runtime.service().live(lease),16_384,10,202,body->ack(service.reserve(lease,planId,new PlanMetadataReader().reserve(body))));
    }
    @PostMapping("/api/v1/operations/{operationId}/credentials")
    public void credentials(@PathVariable("operationId") String operationId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var lease=lease(request); var submission=runtime.service().claimCredentials(lease,operationId);
        start(lease,request,response,submission,submission::cancelled,16_384,10,200,body->{
            var failure=new PlanBodyFailure[1];
            var status=submission.process((user,password)->{
                try {return CredentialJsonReader.read(body,user,password,System::nanoTime,body.deadline());}
                catch(PlanBodyFailure rejected) {failure[0]=rejected;throw rejected;}
            });
            if(failure[0]!=null) throw failure[0];
            return status(status);
        });
    }
    @GetMapping("/api/v1/operations/{operationId}")
    public void operation(@PathVariable("operationId") String operationId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        write(response,200,status(runtime.service().status(lease(request),operationId)));
    }
    @PostMapping("/api/v1/operations/{operationId}/cancel")
    public void cancel(@PathVariable("operationId") String operationId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var lease=lease(request); var service=runtime.service(); service.status(lease,operationId);
        var slot=metadata();
        start(lease,request,response,slot,()->!runtime.service().live(lease),16_384,10,200,body->{new PlanMetadataReader().empty(body);return status(service.cancel(lease,operationId));});
    }
    @PostMapping("/api/v1/plans/{planId}/commands")
    public void command(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var lease=lease(request); var admission=runtime.service().reserveCommand(lease,planId);
        start(lease,request,response,admission,()->!admission.live(),134_217_728,30,200,body->ack(admission.execute(new PlanCommandReader().read(body))));
    }
    static SessionLedger.Lease lease(HttpServletRequest request) {
        if(!(request.getAttribute(HostedSessions.REQUEST_LEASE) instanceof SessionLedger.Lease lease)) throw new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED);
        return lease;
    }
    interface BodyAction { Object apply(OwnedServletBody body); }
    static MetadataSlot metadata() {
        if(!METADATA_READERS.tryAcquire()) throw new PlanRefusal(PlanRefusal.Code.CAPACITY);
        return new MetadataSlot();
    }
    static final class MetadataSlot implements AutoCloseable {
        private final AtomicBoolean closed=new AtomicBoolean();
        public void close(){if(closed.compareAndSet(false,true))METADATA_READERS.release();}
    }
    void start(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,AutoCloseable admission,BooleanSupplier cancelled,
            int limit,int seconds,int success,BodyAction action) throws IOException {
        AsyncContext context=null; OwnedServletBody body=null; boolean started=false;
        try {
            String type=request.getContentType();
            if(Collections.list(request.getHeaders("Content-Type")).size()!=1 || type==null || !type.split(";",2)[0].trim().equalsIgnoreCase("application/json")) throw new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY);
            context=request.startAsync(); context.setTimeout(0);
            body=new OwnedServletBody(request.getInputStream(),limit,System.nanoTime()+seconds*1_000_000_000L,cancelled);
            final AsyncContext ownedContext=context; final OwnedServletBody ownedBody=body;
            var worker=new Thread(null,()->{
                try {
                    try {write(response,success,action.apply(ownedBody));}
                    catch(RuntimeException failure) {if(!response.isCommitted()) refuse(response,runtime.service().live(lease)?failure:new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED));}
                } catch(IOException disconnected) {
                    LOG.warn("PLAN_RESPONSE_DISCONNECTED"); // Existing operation status remains authoritative; no authentication retry.
                } finally {
                    ownedBody.close(); try {close(admission);} finally {ownedContext.complete();}
                }
            },"hosted-plan-body",0,false);
            worker.setDaemon(true); worker.start(); started=true;
        } finally {
            if(!started) {if(body!=null)body.close();try {close(admission);} finally {if(context!=null)context.complete();}}
        }
    }
    private static void close(AutoCloseable resource) {
        try {resource.close();}
        catch(RuntimeException failure) {throw new IllegalStateException("PLAN_READER_CLEANUP_INCONCLUSIVE");}
        catch(Exception failure) {throw new IllegalStateException("PLAN_READER_CLEANUP_INCONCLUSIVE");}
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure,HttpServletResponse response) throws IOException {refuse(response,failure);}
    private static void refuse(HttpServletResponse response,RuntimeException failure) throws IOException {
        int status=500; String code="PLAN_INTERNAL_REFUSAL";
        if(failure instanceof PlanRuntime.Unavailable) {status=503;code="PLAN_SERVICES_UNAVAILABLE";}
        else if(failure instanceof PlanRuntime.DestinationDenied) {status=403;code="DESTINATION_DENIED";}
        else if(failure instanceof PlanBodyFailure body) {status=body.code()==PlanBodyFailure.Code.BODY_TOO_LARGE?413:body.code()==PlanBodyFailure.Code.CANCELLED?409:400;code=body.code().name();response.setHeader("Connection","close");}
        else if(failure instanceof PlanRefusal refused) {
            code=refused.code().name();
            status=switch(refused.code()) {
                case SESSION_REQUIRED -> 401; case NOT_FOUND -> 404;
                case CONFLICT,PLAN_BUSY,CREDENTIALS_ALREADY_CONSUMED,RESERVATION_EXPIRED,STALE_PREVIEW,CANCELLED -> 409;
                case CAPACITY -> 429; case EXPORT_UNAVAILABLE,CLEANUP_INCONCLUSIVE -> 503;
                default -> 422;
            };
        }
        write(response,status,Map.of("code",code));
    }
    private static void write(HttpServletResponse response,int status,Object value) throws IOException {
        if(value instanceof PlanResponse owned){owned.write(response,status);return;}
        response.setStatus(status); response.setHeader("Cache-Control","no-store"); response.setContentType("application/json");
        JSON.writeValue(response.getOutputStream(),value);
    }
    private static Map<String,Object> ack(HostedPlanService.Ack ack) {
        var result=new LinkedHashMap<String,Object>();result.put("planId",ack.planId());result.put("revision",ack.revision());ack.operationId().ifPresent(value->result.put("operationId",value));return result;
    }
    private static Map<String,Object> status(HostedPlanService.Status status) {
        var result=new LinkedHashMap<String,Object>();result.put("operationId",status.operationId());result.put("planId",status.planId());
        result.put("phase",status.phase().name().toLowerCase(Locale.ROOT));result.put("code",status.code());result.put("cleanup",status.cleanup().name().toLowerCase(Locale.ROOT).replace('_','-'));
        status.installedRevision().ifPresent(value->result.put("installedRevision",value));return result;
    }
    private static Map<String,Object> view(HostedPlanService.View view) {
        var result=new LinkedHashMap<String,Object>();result.put("planId",view.planId());result.put("revision",view.revision());
        result.put("definition",Map.of("objectId",view.definition().objectId(),"workspaceRevision",view.definition().workspaceRevision()));
        result.put("bindingId",view.bindingId());result.put("destinationId",view.destinationId());result.put("currentCounts",view.currentCounts());result.put("targetCounts",view.targetCounts());
        result.put("observedDestination",view.observedDestination().orElse(null));
        result.put("inspectionValid",view.inspectionValid());result.put("targetComplete",view.targetComplete());result.put("exportAvailable",false);result.put("blockers",view.blockers());
        view.activeOperationId().ifPresent(value->result.put("activeOperationId",value));return result;
    }
}
