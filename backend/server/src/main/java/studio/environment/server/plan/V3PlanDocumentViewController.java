package studio.environment.server.plan;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import studio.environment.server.session.HostedSessions;
import static studio.environment.core.plan.PlanDefinition.Version.V3;

@RestController
@ConditionalOnProperty(name = "studio.mode", havingValue = "hosted")
public final class V3PlanDocumentViewController {
    private final PlanRuntime runtime;
    private final HostedSessions sessions;
    public V3PlanDocumentViewController(PlanRuntime runtime, HostedSessions sessions) {
        this.runtime = runtime; this.sessions = sessions;
    }
    @PostMapping("/api/v3/plans/{planId}/views/document")
    public void document(@PathVariable("planId") String planId, HttpServletRequest request, HttpServletResponse response) {
        start(planId, request, response, V3PlanTransport.PhysicalRoute.DOCUMENT);
    }
    @PostMapping("/api/v3/plans/{planId}/views/binding-locations")
    public void bindingLocations(@PathVariable("planId") String planId, HttpServletRequest request, HttpServletResponse response) {
        start(planId, request, response, V3PlanTransport.PhysicalRoute.BINDING_LOCATIONS);
    }
    private void start(String planId, HttpServletRequest request, HttpServletResponse response, V3PlanTransport.PhysicalRoute route) {
        var lease = PlanController.lease(request); var service = runtime.service();
        service.requireOwned(lease, planId, V3); V3PlanTransport.requireJson(request);
        var admission = service.reserveView(lease, planId, V3);
        V3PlanTransfers.Operation operation;
        try { operation = runtime.transfers().admitSemantic(lease); }
        catch (RuntimeException failure) { admission.close(); throw failure; }
        new V3PlanTransport(service, sessions).physicalBody(lease, planId, request, response, operation, admission, route);
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure, HttpServletResponse response) {
        V3PlanTransport.refuseBeforeTransfer(response, failure);
    }
}
