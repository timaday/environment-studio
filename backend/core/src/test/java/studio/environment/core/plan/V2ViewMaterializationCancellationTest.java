package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import static studio.environment.core.plan.PlanDefinition.Version.V2;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import studio.environment.core.graph.ObservedGraph;
import static studio.environment.core.plan.PlanPorts.*;

/** Controlled ports and invented XML isolate target publication from adapter completion. */
class V2ViewMaterializationCancellationTest {
    private static final String ORIGINAL = "<sample>MiXeD-Canary</sample>";
    private static final String REPLACEMENT = "<sample>Later-Value</sample>";

    @ParameterizedTest(name = "refused={0}, closed={1}")
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void viewClosureGovernsBothTargetInstallationAndRemoval(boolean refused, boolean closed) throws Exception {
        var h = held(refused);
        var view = h.service.reserveView(h.base.lease, h.created.planId(), V2);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> view.run(() -> { view.pin("2"); return view.materialize(); }));
            try {
                PlanLifecycleTest.await(h.renderEntered);
                if (closed) { view.close(); view.close(); }
                scratchHeld(h);
            } finally { h.renderRelease.countDown(); }
            if (closed) {
                var failure = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
                assertEquals(PlanRefusal.Code.CONFLICT, assertInstanceOf(PlanRefusal.class, failure.getCause()).code());
            } else {
                var result = pending.get(5, TimeUnit.SECONDS);
                assertEquals(!refused, result.complete());
                assertEquals(refused ? List.of("UNRESOLVED_FIELDS") : List.of(), result.diagnostics());
                // The executing callback ended, but the still-owned view has not closed.
                scratchHeld(h);
            }
        } finally { h.renderRelease.countDown(); view.close(); }
        var summary = h.service.summary(h.base.lease, h.created.planId());
        assertEquals("2", summary.revision());
        assertTrue(summary.inspectionValid());
        assertEquals(closed || !refused, summary.targetComplete());
        assertEquals(closed || !refused ? List.of("EXPORT_UNAVAILABLE") : List.of("EXPORT_UNAVAILABLE", "TARGET_INCOMPLETE", "UNRESOLVED_FIELDS"),
                h.service.view(h.base.lease, java.util.Optional.of(h.created.planId())).blockers());
        try (var next = h.service.reserveView(h.base.lease, h.created.planId(), V2)) {
            next.run(() -> {
                next.pin("2"); var snapshot = next.snapshot();
                assertEquals(ORIGINAL, snapshot.current().orElseThrow().sources().getFirst().xml());
                if (closed) assertEquals(ORIGINAL, snapshot.target().orElseThrow().sources().getFirst().xml(),
                        "closed view must preserve its previous target for either late adapter outcome");
                else if (refused) assertTrue(snapshot.target().isEmpty());
                else assertEquals(REPLACEMENT, snapshot.target().orElseThrow().sources().getFirst().xml());
                return true;
            });
        }
        try (var next = h.service.reserveCommand(h.base.lease, h.created.planId(), V2)) { assertTrue(next.live()); }
    }

    @ParameterizedTest(name = "accepted v2 command refused={0}")
    @ValueSource(booleans = {false, true})
    void closingAnAlreadyAcceptedV2CommandPreservesCompletionAndReplay(boolean refused) throws Exception {
        var h = held(refused);
        var command = new PlanCommand(h.mutation(), new PlanCommand.Action.Replace(new PlanCommand.Draft(List.of(), List.of(), List.of())));
        var admission = h.service.reserveCommand(h.base.lease, h.created.planId(), V2);
        HostedPlanService.Ack ack;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> admission.execute(command));
            try {
                PlanLifecycleTest.await(h.renderEntered);
                assertEquals("3", h.service.summary(h.base.lease, h.created.planId()).revision());
                assertFalse(h.service.summary(h.base.lease, h.created.planId()).targetComplete());
                admission.close(); admission.close(); scratchHeld(h);
            } finally { h.renderRelease.countDown(); }
            ack = assertDoesNotThrow(() -> pending.get(5, TimeUnit.SECONDS), "closing an accepted v2 command must preserve its completion");
        } finally { h.renderRelease.countDown(); admission.close(); }
        assertEquals("3", ack.revision());
        assertEquals(!refused, h.service.summary(h.base.lease, h.created.planId()).targetComplete());
        try (var next = h.service.reserveView(h.base.lease, h.created.planId(), V2)) {
            next.run(() -> {
                next.pin("3"); var snapshot = next.snapshot();
                assertEquals(ORIGINAL, snapshot.current().orElseThrow().sources().getFirst().xml());
                if (refused) assertTrue(snapshot.target().isEmpty());
                else assertEquals(REPLACEMENT, snapshot.target().orElseThrow().sources().getFirst().xml());
                return true;
            });
        }
        int renders = h.renders.get();
        assertEquals(ack, h.service.command(h.base.lease, h.created.planId(), command));
        assertEquals(renders, h.renders.get(), "exact accepted replay must not render again");
        try (var next = h.service.reserveCommand(h.base.lease, h.created.planId(), V2)) { assertTrue(next.live()); }
    }

    private static PlanLifecycleTest.Harness held(boolean refused) {
        var h = new PlanLifecycleTest.Harness(); h.inspect();
        assertTrue(h.service.summary(h.base.lease, h.created.planId()).targetComplete());
        h.content = new Content(List.of(new Source("sample", REPLACEMENT, "replacement-digest")), new ObservedGraph(List.of(), List.of()), Map.of());
        h.incompleteTarget.set(refused); h.renderEntered = new CountDownLatch(1); h.renderRelease = new CountDownLatch(1);
        return h;
    }
    private static void scratchHeld(PlanLifecycleTest.Harness h) {
        assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                () -> h.service.reserveView(h.base.lease, h.created.planId(), V2)).code());
        assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class,
                () -> h.service.reserveCommand(h.base.lease, h.created.planId(), V2)).code());
    }
}
