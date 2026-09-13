package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.old;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.PublishedProfile;
import studio.environment.core.planning.TargetIntent;

class IndependentV3PlanCompositionTest {
    @Test void reuseOfFreshReplacementDoesNotResurrectSameLiteralOriginal() {
        var fixture = new SharedV3PlanXmlTest();
        var service = fixture.service();
        String plan = fixture.inspected(service);
        var profile = SharedV3PlanProfileCompositionTest.PROFILE;
        fixture.profile = new PublishedProfile(profile, "independent-profile-publication",
                service.capture(fixture.lease, plan, "2", SharedV3PlanCaptureTest.command()).draft().checked());
        var observed = fixture.snapshot(service, plan, "2");
        var fresh = new PlanCommand.Ref.Fresh("replacement", "item");
        var source = observed.current().orElseThrow().sources().getFirst();
        var create = new PlanCommand.Change(new PlanCommand.Entity.Create(fresh, Map.of(
                "id", new TargetIntent.FieldValue.Entered("one"),
                "tone", new TargetIntent.FieldValue.Entered("gamma"),
                "finish", new TargetIntent.FieldValue.Entered("z")), Map.of()),
                List.of(new PlanCommand.Placement(fresh, "sheet", "items",
                        new PlanCommand.Parent.Existing("sheet", source.digest(), "0"))));
        var remove = new PlanCommand.Change(new PlanCommand.Entity.Remove(assertInstanceOf(PlanCommand.Ref.Existing.class, observed.reference(old("one")))), List.of());
        service.command(fixture.lease, plan, new PlanCommand(new HostedPlanService.Mutation("2", UUID.randomUUID().toString()),
                new PlanCommand.Action.Batch(List.of(remove, create), List.of())));
        var preview = service.previewProfile(fixture.lease, plan, "3", profile, List.of("slot-1"));
        var removedOriginal = new PlanCommand(new HostedPlanService.Mutation("3", UUID.randomUUID().toString()),
                new PlanCommand.Action.Compose(profile, HostedPlanService.compositionPreviewDigest(preview),
                        preview.roots(), List.of(new PlanCommand.ProfileDecision.UseExisting("slot-1", observed.reference(old("one"))))));
        assertEquals(PlanRefusal.Code.INVALID_REQUEST, assertThrows(PlanRefusal.class,
                () -> service.command(fixture.lease, plan, removedOriginal)).code());
        assertEquals("3", service.summary(fixture.lease, plan).revision());
        var reuse = new PlanCommand(new HostedPlanService.Mutation("3", UUID.randomUUID().toString()),
                new PlanCommand.Action.Compose(profile, HostedPlanService.compositionPreviewDigest(preview),
                        preview.roots(), List.of(new PlanCommand.ProfileDecision.UseExisting("slot-1", fresh))));
        var acknowledgement = service.command(fixture.lease, plan, reuse);
        assertEquals("4", acknowledgement.revision());
        assertEquals(acknowledgement, service.command(fixture.lease, plan, reuse));
        var snapshot = fixture.snapshot(service, plan, "4");
        var target = snapshot.target().orElseThrow();
        assertEquals("<items><!-- mock -->\r\n<item id='two' tone='alpha' finish='y'/><item id='three' tone='beta' finish='x'/><item xmlns=\"\" finish=\"z\" id=\"one\" tone=\"gamma\"/></items>", target.sources().getFirst().xml());
        assertEquals(new TargetIntent.Ref.Fresh("replacement", "item"), target.provenance().get(old("one").key()));
        assertTrue(snapshot.draft().intent().entities().stream().anyMatch(e -> e instanceof TargetIntent.EntityDecision.Remove removed && removed.entity().equals(old("one"))));
        assertEquals(List.of("alpha:y", "beta:x", "gamma:z"), ((PlanContentEvidence.V3Target) target.evidence()).derived().graph().cooccurrences().stream()
                .map(edge -> edge.source().value() + ":" + edge.target().value()).toList());
    }
}
