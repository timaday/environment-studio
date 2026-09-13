package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.old;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.observation.ObservationPort.Cancellation;
import studio.environment.core.observation.ObservationResult;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.server.plan.*;

class IndependentV3PhysicalViewsTest {
    @Test void equalValuesAndCountsCannotHideSwappedPhysicalOriginsBehindUntouchedDerivedProof() {
        var actual = new PlanContentAdapter();
        var adapter = new ContentAdapter() {
            public ContentResult project(PublishedDefinition d, String b, ObservationResult.Observation o) { return actual.project(d,b,o); }
            public ContentResult project(PublishedDefinition d, String b, ObservationResult.Observation o, Cancellation flag) {
                var accepted = (ContentResult.Complete) actual.project(d,b,o,flag);
                var content = accepted.content();
                var entities = new ArrayList<>(content.graph().entities());
                var first = entities.get(0); var second = entities.get(1);
                entities.set(0,new ObservedGraph.Entity(first.key(),first.fields(),second.origin()));
                entities.set(1,new ObservedGraph.Entity(second.key(),second.fields(),first.origin()));
                return new ContentResult.Complete(new Content(content.sources(),new ObservedGraph(entities,content.graph().edges()),content.provenance(),content.evidence()));
            }
            public ContentResult materialize(PublishedDefinition d,String b,Content c,Draft draft) { return actual.materialize(d,b,c,draft); }
            public Capture capture(PublishedDefinition d,String b,Content c,ProfileCapture.Command command) { return actual.capture(d,b,c,command); }
        };
        var fixture = new SharedV3PlanXmlTest(); var service = fixture.service(adapter); String plan = fixture.inspected(service);
        try (var view = service.reserveView(fixture.lease,plan)) {
            view.run(() -> {
                view.pin("2");
                var ref = view.snapshot().reference(old("one"));
                assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,() -> V3PlanPhysicalViews.entities(view,false,0,100)).code());
                assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,() -> V3PlanPhysicalViews.bindings(view,ref,0,100)).code());
                assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,assertThrows(PlanRefusal.class,() -> V3PlanPhysicalViews.locations(view,ref,"id",false,0,100,true)).code());
                return true;
            });
        }
    }

    @Test void closedAdmissionDoesNotInvokeTrustedReaderAndCannotReleaseAnotherViewsCapacity() {
        var fixture = new SharedV3PlanXmlTest(); var service = fixture.service(); String plan = fixture.inspected(service);
        var first = service.reserveView(fixture.lease,plan); var called = new AtomicBoolean();
        first.run(() -> { first.pin("2"); assertEquals(3,V3PlanPhysicalViews.entities(first,false,0,100).get("total")); return true; });
        first.close();
        try (var second = service.reserveView(fixture.lease,plan)) {
            second.run(() -> {
                second.pin("2");
                assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,() -> first.read((snapshot,flag) -> {called.set(true); return 1;})).code());
                assertFalse(called.get()); first.close();
                assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,() -> service.reserveView(fixture.lease,plan)).code());
                assertEquals(3,V3PlanPhysicalViews.entities(second,false,0,100).get("total"));
                return true;
            });
        }
    }
}
