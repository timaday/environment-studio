package studio.environment.server.planning;

import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.session.SessionLedger;

/** Explicit invented publication/observation witnesses, with the real XML adapter. */
public final class V3CommandWireFixtures {
    private final SharedV3PlanXmlTest fixture = new SharedV3PlanXmlTest();
    public final HostedPlanService service = fixture.service();
    public final SessionLedger.Lease lease = fixture.lease;
    public String inspected() { return fixture.inspected(service); }
    public HostedPlanService.ViewSnapshot snapshot(String id, String revision) {
        return fixture.snapshot(service, id, revision);
    }
    public String handle(String id, String revision, String identity) {
        return service.entities(lease, id, revision, false, 0, 100).entities().stream()
                .filter(entity -> entity.fields().stream().anyMatch(field -> field.field().equals("id")
                        && field.value().orElse("").equals(identity)))
                .findFirst().orElseThrow().handle();
    }
}
