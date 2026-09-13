package studio.environment.server.plan;

import jakarta.servlet.http.*;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import studio.environment.core.plan.*;
import static studio.environment.core.plan.PlanDefinition.Version.V3;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.HostedSessions;

/** Initial fixed-v3 routes. All transport admission follows original resource ownership. */
@RestController
@ConditionalOnProperty(name="studio.mode",havingValue="hosted")
public final class V3PlanController {
    private final PlanRuntime runtime;
    private final HostedSessions sessions;
    public V3PlanController(PlanRuntime runtime,HostedSessions sessions){this.runtime=runtime;this.sessions=sessions;}
    private V3PlanTransport transport(HostedPlanService service){return new V3PlanTransport(service,sessions);}
    @PostMapping("/api/v3/plans")
    public void create(HttpServletRequest request,HttpServletResponse response){
        var lease=PlanController.lease(request);var service=runtime.service();V3PlanTransport.requireJson(request);
        var admission=runtime.transfers().admitMetadata(lease);
        transport(service).body(lease,request,response,admission,201,()->{},()->false,
                body->new V3PlanReply.Acknowledgement(runtime.createV3(lease,new PlanMetadataReader().create(body))));
    }
    @GetMapping("/api/v3/plans/current")
    public void current(HttpServletRequest request,HttpServletResponse response){summary(PlanController.lease(request),Optional.empty(),request,response);}
    @GetMapping("/api/v3/plans/{planId}")
    public void summary(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response){summary(PlanController.lease(request),Optional.of(planId),request,response);}
    private void summary(SessionLedger.Lease lease,Optional<String> planId,HttpServletRequest request,HttpServletResponse response){
        var service=runtime.service();var captured=new V3PlanReply.Summary(service.viewV3(lease,planId));
        var admission=runtime.transfers().admitMetadata(lease);
        transport(service).read(lease,request,response,admission,200,()->captured);
    }
    @PostMapping("/api/v3/plans/{planId}/inspections")
    public void reserve(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response){
        var lease=PlanController.lease(request);var service=runtime.service();service.requireOwned(lease,planId,V3);V3PlanTransport.requireJson(request);
        var admission=runtime.transfers().admitMetadata(lease);
        transport(service).body(lease,request,response,admission,202,()->{},()->false,
                body->new V3PlanReply.Acknowledgement(service.reserve(lease,planId,new PlanMetadataReader().reserve(body),V3)));
    }
    @PostMapping("/api/v3/operations/{operationId}/credentials")
    public void credentials(@PathVariable("operationId") String operationId,HttpServletRequest request,HttpServletResponse response){
        var lease=PlanController.lease(request);var service=runtime.service();service.requireOperationOwned(lease,operationId,V3);V3PlanTransport.requireJson(request);
        var admission=runtime.transfers().admitCredentials(lease);HostedPlanService.Submission submission;
        try {submission=service.claimCredentials(lease,operationId,V3);}
        catch(RuntimeException failure){admission.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);throw failure;}
        transport(service).body(lease,request,response,admission,200,submission,submission::cancelled,body->{
            var bodyFailure=new PlanBodyFailure[1];
            var status=submission.process((user,password)->{
                try {return CredentialJsonReader.read(body,user,password,System::nanoTime,body.deadline());}
                catch(PlanBodyFailure failure){bodyFailure[0]=failure;throw failure;}
            });
            if(bodyFailure[0]!=null)throw bodyFailure[0];return new V3PlanReply.Status(status);
        });
    }
    @GetMapping("/api/v3/operations/{operationId}")
    public void operation(@PathVariable("operationId") String operationId,HttpServletRequest request,HttpServletResponse response){
        var lease=PlanController.lease(request);var service=runtime.service();service.requireOperationOwned(lease,operationId,V3);
        var admission=runtime.transfers().admitMetadata(lease);
        transport(service).read(lease,request,response,admission,200,()->new V3PlanReply.Status(service.status(lease,operationId,V3)));
    }
    @PostMapping("/api/v3/operations/{operationId}/cancel")
    public void cancel(@PathVariable("operationId") String operationId,HttpServletRequest request,HttpServletResponse response){
        var lease=PlanController.lease(request);var service=runtime.service();service.requireOperationOwned(lease,operationId,V3);V3PlanTransport.requireJson(request);
        var admission=runtime.transfers().admitMetadata(lease);
        transport(service).body(lease,request,response,admission,200,()->{},()->false,body->{
            new PlanMetadataReader().empty(body);return new V3PlanReply.Status(service.cancel(lease,operationId,V3));
        });
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure,HttpServletResponse response){V3PlanTransport.refuseBeforeTransfer(response,failure);}
}
