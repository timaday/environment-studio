package studio.environment.server.plan;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
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
    @FunctionalInterface interface BodyAction { V3PlanReply apply(OwnedServletBody body); }
    void read(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,Supplier<V3PlanReply> action){
        start(lease,request,response,operation,status,false,()->{},()->false,body->action.get());
    }
    void body(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,AutoCloseable resource,BooleanSupplier cancelled,BodyAction action){
        start(lease,request,response,operation,status,true,resource,cancelled,body->action.apply(body.orElseThrow()));
    }
    private void check(SessionLedger.Lease lease,V3PlanTransfers.Operation operation){
        if(!service.live(lease))throw new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED);
        if(operation.cancelled())throw new PlanRefusal(PlanRefusal.Code.CANCELLED);
    }
    private void start(SessionLedger.Lease lease,HttpServletRequest request,HttpServletResponse response,V3PlanTransfers.Operation operation,int status,
            boolean readsBody,AutoCloseable resource,BooleanSupplier cancelled,Function<Optional<OwnedServletBody>,V3PlanReply> action){
        OwnedAsyncCompletion completion=null;Optional<OwnedServletBody> body=Optional.empty();
        var cleanupUncertain=new AtomicBoolean();boolean asyncAttempted=false,started=false;
        try {
            check(lease,operation);if(readsBody)requireJson(request);
            asyncAttempted=true;var context=request.startAsync();context.setTimeout(30_000);
            completion=new OwnedAsyncCompletion(context,outcome->operation.settlement(cleanupUncertain.get()?OwnedAsyncCompletion.Outcome.INCONCLUSIVE:outcome,sessions));
            context.setTimeout(0);var owner=completion;check(lease,operation);owner.checkActive();
            if(readsBody){
                long deadline=System.nanoTime()+10_000_000_000L;
                body=Optional.of(new OwnedServletBody(request.getInputStream(),16_384,deadline,()->{
                    check(lease,operation);owner.checkActive();return cancelled.getAsBoolean();
                }));
            }
            var ownedBody=body;
            var worker=new Thread(null,()->{
                try {
                    V3PlanReply reply;
                    try {check(lease,operation);owner.checkActive();reply=Objects.requireNonNull(action.apply(ownedBody));}
                    catch(RuntimeException failure){sendFailure(response,failure,lease,operation,owner,clock.getAsLong()+30_000_000_000L);return;}
                    sendReply(response,status,reply,lease,operation,owner);
                } finally {
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
    private Runnable outputAuthority(SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion,long deadline){
        return ()->{check(lease,operation);completion.checkActive();if(deadline-clock.getAsLong()<=0)throw new PlanRefusal(PlanRefusal.Code.CANCELLED);};
    }
    private void sendReply(HttpServletResponse response,int status,V3PlanReply reply,SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion){
        long deadline=clock.getAsLong()+30_000_000_000L;var outputAttempted=new AtomicBoolean();
        Runnable authority=outputAuthority(lease,operation,completion,deadline);
        Runnable verify=()->{authority.run();reply.verify(service,lease);};
        try {write(response,status,reply.wire(),verify,deadline,outputAttempted);}
        catch(RuntimeException failure){
            if(!outputAttempted.get() && !response.isCommitted())sendFailure(response,failure,lease,operation,completion,deadline);
            else LOG.warn("V3_PLAN_RESPONSE_ABORTED");
        } catch(IOException failure){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
    }
    private void sendFailure(HttpServletResponse response,RuntimeException failure,SessionLedger.Lease lease,V3PlanTransfers.Operation operation,OwnedAsyncCompletion completion,long deadline){
        if(response.isCommitted()){LOG.warn("V3_PLAN_RESPONSE_ABORTED");return;}
        var refusal=refusal(failure);var verify=outputAuthority(lease,operation,completion,deadline);
        try {
            verify.run();response.resetBuffer();response.setHeader("Content-Length",null);
            if(refusal.closeConnection())response.setHeader("Connection","close");
            write(response,refusal.status(),Map.of("code",refusal.code()),verify,deadline,new AtomicBoolean());
        } catch(IOException|RuntimeException denied){LOG.warn("V3_PLAN_RESPONSE_ABORTED");}
    }
    private void write(HttpServletResponse response,int status,Object value,Runnable verify,long deadline,AtomicBoolean outputAttempted)throws IOException {
        try(var encoded=new PlanViewEncoding(32_768,verify)){
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
