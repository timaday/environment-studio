package studio.environment.server.plan;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import studio.environment.server.session.HostedSessions;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

@RestController
@ConditionalOnProperty(name = "studio.mode", havingValue = "hosted")
public final class V3PlanMaterializationController {
    private final PlanRuntime runtime;
    private final HostedSessions sessions;
    public V3PlanMaterializationController(PlanRuntime runtime, HostedSessions sessions) {
        this.runtime = runtime; this.sessions = sessions;
    }
    @PostMapping("/api/v3/plans/{planId}/materializations")
    public void materialize(@PathVariable("planId") String planId, HttpServletRequest request, HttpServletResponse response) {
        var lease = PlanController.lease(request); var service = runtime.service();
        service.requireOwned(lease, planId, V3); V3PlanTransport.requireJson(request);
        var admission = service.reserveView(lease, planId, V3);
        V3PlanTransfers.Operation operation;
        try { operation = runtime.transfers().admitSemantic(lease); }
        catch (RuntimeException failure) { admission.close(); throw failure; }
        new V3PlanTransport(service, sessions).materializationBody(lease, planId, request, response, operation, admission);
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure, HttpServletResponse response) {
        V3PlanTransport.refuseBeforeTransfer(response, failure);
    }
}
