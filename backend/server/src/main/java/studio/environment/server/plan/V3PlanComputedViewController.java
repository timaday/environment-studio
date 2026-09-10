package studio.environment.server.plan;

import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import studio.environment.server.session.HostedSessions;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

@RestController
@ConditionalOnProperty(name = "studio.mode", havingValue = "hosted")
public final class V3PlanComputedViewController {
    private final PlanRuntime runtime; private final HostedSessions sessions;
    public V3PlanComputedViewController(PlanRuntime runtime, HostedSessions sessions) { this.runtime = runtime; this.sessions = sessions; }
    @PostMapping("/api/v3/plans/{planId}/views/computed/nodes")
    public void nodes(@PathVariable("planId") String id, HttpServletRequest request, HttpServletResponse response) { start(id, request, response, V3PlanComputedReader.Route.NODES); }
    @PostMapping("/api/v3/plans/{planId}/views/computed/memberships")
    public void memberships(@PathVariable("planId") String id, HttpServletRequest request, HttpServletResponse response) { start(id, request, response, V3PlanComputedReader.Route.MEMBERSHIPS); }
    @PostMapping("/api/v3/plans/{planId}/views/computed/cooccurrences")
    public void cooccurrences(@PathVariable("planId") String id, HttpServletRequest request, HttpServletResponse response) { start(id, request, response, V3PlanComputedReader.Route.COOCCURRENCES); }
    @PostMapping("/api/v3/plans/{planId}/views/computed/rules")
    public void rules(@PathVariable("planId") String id, HttpServletRequest request, HttpServletResponse response) { start(id, request, response, V3PlanComputedReader.Route.RULES); }
    @PostMapping("/api/v3/plans/{planId}/views/computed/contributors")
    public void contributors(@PathVariable("planId") String id, HttpServletRequest request, HttpServletResponse response) { start(id, request, response, V3PlanComputedReader.Route.CONTRIBUTORS); }
    private void start(String id, HttpServletRequest request, HttpServletResponse response, V3PlanComputedReader.Route route) {
        var lease = PlanController.lease(request); var service = runtime.service();
        service.requireOwned(lease, id, V3); V3PlanTransport.requireJson(request);
        var admission = service.reserveView(lease, id, V3); V3PlanTransfers.Operation operation;
        try { operation = runtime.transfers().admitSemantic(lease); }
        catch (RuntimeException failure) { admission.close(); throw failure; }
        new V3PlanTransport(service, sessions).computedBody(lease, id, request, response, operation, admission, route);
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure, HttpServletResponse response) { V3PlanTransport.refuseBeforeTransfer(response, failure); }
}
