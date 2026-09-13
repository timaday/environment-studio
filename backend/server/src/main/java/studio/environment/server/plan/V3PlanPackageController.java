package studio.environment.server.plan;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import studio.environment.server.session.HostedSessions;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

@RestController
@ConditionalOnProperty(name="studio.mode",havingValue="hosted")
public final class V3PlanPackageController {
    private final PlanRuntime runtime;
    private final HostedSessions sessions;
    public V3PlanPackageController(PlanRuntime runtime, HostedSessions sessions){this.runtime=runtime;this.sessions=sessions;}
    @PostMapping("/api/v3/plans/{planId}/package-candidates/guarded")
    public void guardedPackage(@PathVariable("planId") String planId, HttpServletRequest request, HttpServletResponse response) {
        var lease=PlanController.lease(request); var service=runtime.service();
        service.requireOwned(lease,planId,V3); V3PlanTransport.requireJson(request);
        var summary=service.viewV3(lease,java.util.Optional.of(planId)).summary();
        var target=runtime.packageTarget(lease.owner(),summary.destinationId());
        var admission=service.reserveView(lease,planId,V3); V3PlanTransfers.Operation operation;
        try { operation=runtime.transfers().admitSemantic(lease); }
        catch(RuntimeException failure){admission.close();throw failure;}
        new V3PlanTransport(service,sessions).packageBody(lease,planId,request,response,operation,admission,target);
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure,HttpServletResponse response){V3PlanTransport.refuseBeforeTransfer(response,failure);}
}
