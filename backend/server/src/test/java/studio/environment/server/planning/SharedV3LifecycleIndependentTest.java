package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;

/** Independent identical-source reinspection must retire old command references. */
class SharedV3LifecycleIndependentTest {
    @Test void identicalReinspectionRetiresOldHandlesAndTargetDecisions() {
        var fixture = new SharedV3PlanXmlTest();
        var service = fixture.service();
        String id = fixture.inspected(service);
        var original = fixture.snapshot(service, id, "2");
        var oldHandle = (PlanCommand.Ref.Existing) original.reference(old("one"));
        var fields = edit("one", new TargetIntent.FieldValue.KeepObserved(),
                new TargetIntent.FieldValue.Entered("beta")).fields();
        var edit = new PlanCommand(new HostedPlanService.Mutation("2", UUID.randomUUID().toString()),
                new PlanCommand.Action.Upsert(new PlanCommand.Change(
                        new PlanCommand.Entity.Retain(oldHandle, fields, Map.of()), List.of())));
        assertEquals("3", service.command(fixture.lease, id, edit).revision());
        assertNotEquals(XML, fixture.snapshot(service, id, "3").target().orElseThrow()
                .sources().getFirst().xml());

        var reserved = service.reserve(fixture.lease, id,
                new HostedPlanService.Mutation("3", UUID.randomUUID().toString()));
        var status = service.submit(fixture.lease, reserved.operationId().orElseThrow(), (user, password) -> {
            user[0] = 'a'; password[0] = 'X'; return new CredentialLengths(1, 1);
        });
        assertEquals(HostedPlanService.Phase.SUCCEEDED, status.phase());
        var refreshed = fixture.snapshot(service, id, "4");
        assertEquals(Draft.empty(), refreshed.draft());
        assertEquals(XML, refreshed.current().orElseThrow().sources().getFirst().xml());
        assertTrue(refreshed.target().isEmpty());
        assertFalse(service.summary(fixture.lease,id).targetComplete());
        assertEquals(original.current().orElseThrow().evidence(), refreshed.current().orElseThrow().evidence());
        // Same source/fingerprint is deliberately insufficient to retain a prior observation's handle.
        var stale = new PlanCommand(new HostedPlanService.Mutation("4", UUID.randomUUID().toString()),
                new PlanCommand.Action.Upsert(new PlanCommand.Change(
                        new PlanCommand.Entity.Retain(oldHandle, fields, Map.of()), List.of())));
        assertEquals(PlanRefusal.Code.INVALID_REQUEST,
                assertThrows(PlanRefusal.class, () -> service.command(fixture.lease, id, stale)).code());
        assertEquals("4", service.summary(fixture.lease, id).revision());
        var currentHandle = (PlanCommand.Ref.Existing) refreshed.reference(old("one"));
        assertNotEquals(oldHandle, currentHandle);
        var current = new PlanCommand(new HostedPlanService.Mutation("4", UUID.randomUUID().toString()),
                new PlanCommand.Action.Upsert(new PlanCommand.Change(
                        new PlanCommand.Entity.Retain(currentHandle, fields, Map.of()), List.of())));
        assertEquals("5", service.command(fixture.lease, id, current).revision());
        assertEquals("<items><!-- mock -->\r\n<item id='one' tone='beta' finish='x'/>"
                + "<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/></items>",
                fixture.snapshot(service, id, "5").target().orElseThrow().sources().getFirst().xml());
    }
}
