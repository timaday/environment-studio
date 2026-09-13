package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.plan.PlanContentAdapter;

class IndependentV3PlanValidationTest {
    @Test void matchingPublicationDigestCannotHideChangedDocumentPolicyAndRecoveryKeepsOriginalIdentity() {
        var fixture = new SharedV3PlanXmlTest(); var service = fixture.service(); String plan = fixture.inspected(service);
        var first = service.validateV3(fixture.lease,plan,"2");
        fixture.definitionLookup = publication -> new PublishedDefinition(publication.reference(),publication.publicationDigest(),publication.model(),List.of(new NativeCommand.Policy("mock-pg","sheet","deny")));
        assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,() -> service.validateV3(fixture.lease,plan,"2")).code());
        fixture.definitionLookup = java.util.function.UnaryOperator.identity();
        assertEquals(first,service.validateV3(fixture.lease,plan,"2"));
        assertFalse(first.exportAvailable()); assertFalse(first.targetComplete());
    }

    @Test void unchangedDerivedProofAndValuesCannotAuthorizeDifferentOriginalPhysicalOrigins() {
        var actual = new PlanContentAdapter();
        var adapter = new ContentAdapter() {
            public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o) { return actual.project(d,b,o); }
            public ContentResult project(PublishedDefinition d,String b,ObservationResult.Observation o,Cancellation flag) {
                var c = ((ContentResult.Complete)actual.project(d,b,o,flag)).content();
                var entities = new ArrayList<>(c.graph().entities()); var one = entities.get(0); var two = entities.get(1);
                entities.set(0,new ObservedGraph.Entity(one.key(),one.fields(),two.origin()));
                entities.set(1,new ObservedGraph.Entity(two.key(),two.fields(),one.origin()));
                return new ContentResult.Complete(new Content(c.sources(),new ObservedGraph(entities,c.graph().edges()),c.provenance(),c.evidence()));
            }
            public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft) { return actual.materialize(d,b,c,draft); }
            public Capture capture(PublishedDefinition d,String b,Content c,ProfileCapture.Command command) { return actual.capture(d,b,c,command); }
            public void verifyV3(HostedPlanService.ViewSnapshot snapshot,boolean target,Cancellation control) { actual.verifyV3(snapshot,target,control); }
        };
        var fixture = new SharedV3PlanXmlTest(); var service = fixture.service(adapter); String plan = fixture.inspected(service);
        assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,() -> service.validateV3(fixture.lease,plan,"2")).code());
        assertFalse(service.summary(fixture.lease,plan).targetComplete());
        try (var admission = service.reserveView(fixture.lease,plan)) {
            admission.run(() -> {admission.pin("2");assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,admission::validationV3).code());return true;});
        }
    }
}
