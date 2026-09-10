package studio.environment.server.plan;

import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import studio.environment.server.session.HostedSessions;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

@RestController
@ConditionalOnProperty(name="studio.mode",havingValue="hosted")
public final class V3PlanWorkflowController {
    private final PlanRuntime runtime;
    private final HostedSessions sessions;
    public V3PlanWorkflowController(PlanRuntime runtime,HostedSessions sessions){this.runtime=runtime;this.sessions=sessions;}
    @PostMapping("/api/v3/plans/{planId}/profile-captures")
    public void capture(@PathVariable("planId") String id,HttpServletRequest request,HttpServletResponse response){start(id,request,response,V3PlanWorkflowReader.Route.CAPTURE);}
    @PostMapping("/api/v3/plans/{planId}/profile-previews")
    public void preview(@PathVariable("planId") String id,HttpServletRequest request,HttpServletResponse response){start(id,request,response,V3PlanWorkflowReader.Route.PREVIEW);}
    @PostMapping("/api/v3/plans/{planId}/validations")
    public void validation(@PathVariable("planId") String id,HttpServletRequest request,HttpServletResponse response){start(id,request,response,V3PlanWorkflowReader.Route.VALIDATION);}
    private void start(String id,HttpServletRequest request,HttpServletResponse response,V3PlanWorkflowReader.Route route){
        var lease=PlanController.lease(request);var service=runtime.service();
        service.requireOwned(lease,id,V3);V3PlanTransport.requireJson(request);
        var admission=service.reserveView(lease,id,V3);V3PlanTransfers.Operation operation;
        try{operation=runtime.transfers().admitSemantic(lease);}catch(RuntimeException failure){admission.close();throw failure;}
        new V3PlanTransport(service,sessions).workflowBody(lease,id,request,response,operation,admission,route);
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure,HttpServletResponse response){V3PlanTransport.refuseBeforeTransfer(response,failure);}
}
