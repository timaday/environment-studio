package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.OptionalInt;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.plan.PlanCommand;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanPorts.Draft;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.TargetIntent.FieldValue;
import studio.environment.server.plan.V3PlanComputedViews;
import studio.environment.server.plan.V3PlanComputedViews.ResultKey;
import studio.environment.core.derived.ComputedGraph;
import studio.environment.core.Outcome;
import static studio.environment.server.planning.DerivedTargetInputAdapterTest.*;

/** Independently invented mock XML, actual compiler/projection/content and shared owner. */
class SharedV3PlanComputedViewsTest {
    @Test void completeOriginalGraphRetainsFailedCardinalityWithoutClaimingValidity() {
        var base = definition(false).definition(); var logical = base.logical();
        var narrowed = new studio.environment.core.definitionv3.NativeDefinition.Logical(logical.entityTypes(), logical.relations(), logical.rules(),
                logical.operationCapabilities(), logical.computedTypes(), logical.derivations(),
                List.of(new studio.environment.core.definitionv3.NativeDefinition.Cooccurrence("pair", "by-tone", "by-finish", java.math.BigInteger.ZERO, java.math.BigInteger.ONE)), logical.computedRules());
        var checked = assertInstanceOf(studio.environment.core.definitionv3.NativeCompilationResult.Incomplete.class,
                new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new studio.environment.core.definitionv3.NativeDefinition(base.id(), base.revision(), narrowed, base.bindings()))).checked();
        var fixture = SharedV3PlanXmlTest.with(checked, XML); var service = fixture.service(); String plan = fixture.inspected(service);
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("2"); var page = V3PlanComputedViews.rules(view, false, 0, 100);
                assertEquals(2, page.total());
                assertEquals(List.of("alpha", "beta"), page.items().stream().map(r -> r.source().orElseThrow().value()).toList());
                assertEquals(List.of(Outcome.FAIL, Outcome.PASS), page.items().stream().map(r -> r.outcome()).toList());
                assertEquals(List.of(java.math.BigInteger.TWO, java.math.BigInteger.ONE), page.items().stream().map(r -> r.actual()).toList());
                assertEquals(4, V3PlanComputedViews.nodes(view, false, 0, 100).total());
                return true;
            });
        }
    }
    @Test void finalChildLocationsAndOpaqueReferenceSurviveIdentityRenameAndFreshReplacement() {
        String xml = "<items xmlns:p='urn:props'><item id='one' finish='x'><p:entry p:key='tone' p:value='alpha'/></item></items>";
        var fixture = SharedV3PlanXmlTest.with(definition(true), xml); var service = fixture.service(); String plan = fixture.inspected(service);
        var original = fixture.snapshot(service, plan, "2"); var reference = (PlanCommand.Ref.Existing) original.reference(old("one"));
        service.replaceDraft(fixture.lease, plan, new HostedPlanService.Mutation("2", UUID.randomUUID().toString()),
                new Draft(intent(edit("one", new FieldValue.Entered("renamed"), new FieldValue.Entered("new&tone"))), List.of()));
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("3"); var target = view.snapshot().target().orElseThrow(); var source = target.sources().getFirst();
                var key = new ResultKey.NodeKey(new ComputedGraph.Key("tones", "by-tone", "new&tone"));
                var contributor = V3PlanComputedViews.contributors(view, true, key, 0, 100, true).items().getFirst();
                assertEquals(reference, contributor.physical()); assertEquals(source.digest(), contributor.origin().sourceDigest());
                var location = contributor.roles().getFirst().location(); var pin = location.value();
                assertEquals("new&tone", pin.decodedValue()); assertEquals("new&amp;tone", source.xml().substring(pin.valueStart(), pin.valueEnd()));
                assertEquals(source.digest(), pin.sourceDigest()); assertNotEquals(digest(xml), pin.sourceDigest());
                assertEquals("urn:props", pin.name().namespaceUri()); assertEquals("value", pin.name().localName());
                assertEquals("tone", location.selector().orElseThrow().discriminator().decodedValue());
                assertEquals(1, location.selector().orElseThrow().parentElementIndex());
                assertEquals(PlanRefusal.Code.NOT_FOUND, assertThrows(PlanRefusal.class,
                        () -> V3PlanComputedViews.contributors(view, true, new ResultKey.NodeKey(new ComputedGraph.Key("tones", "by-tone", "alpha")), 0, 100, true)).code());
                return true;
            });
        }
        var fresh = new PlanCommand.Ref.Fresh("replacement", "item"); var source = original.current().orElseThrow().sources().getFirst();
        var create = new PlanCommand.Change(new PlanCommand.Entity.Create(fresh, Map.of("id", new FieldValue.Entered("one"), "tone", new FieldValue.Entered("gamma"), "finish", new FieldValue.Entered("z")), Map.of()),
                List.of(new PlanCommand.Placement(fresh, "sheet", "items", new PlanCommand.Parent.Existing("sheet", source.digest(), "0"))));
        service.command(fixture.lease, plan, new PlanCommand(new HostedPlanService.Mutation("3", UUID.randomUUID().toString()),
                new PlanCommand.Action.Batch(List.of(new PlanCommand.Change(new PlanCommand.Entity.Remove(reference), List.of()), create), List.of())));
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("4");
                var key = new ResultKey.NodeKey(new ComputedGraph.Key("tones", "by-tone", "gamma"));
                var contributor = V3PlanComputedViews.contributors(view, true, key, 0, 100, true).items().getFirst();
                assertEquals(fresh, contributor.physical()); assertNotEquals(reference, contributor.physical());
                assertEquals(List.of(fresh, fresh), V3PlanComputedViews.memberships(view, true, 0, 100).items().stream().map(V3PlanComputedViews.Membership::physical).toList());
                assertEquals(2, contributor.roles().getFirst().location().value().elementIndex());
                return true;
            });
        }
    }
    @Test void duplicateRemovalOptionalAbsenceAndUnresolvedTargetRemainDistinct() {
        String xml = "<items><item id='one' tone='alpha' finish='x'/><item id='two' tone='alpha' finish='x'/><item id='three'/></items>";
        var fixture = SharedV3PlanXmlTest.with(definition(false), xml); var service = fixture.service(); String plan = fixture.inspected(service);
        service.replaceDraft(fixture.lease, plan, new HostedPlanService.Mutation("2", UUID.randomUUID().toString()),
                new Draft(intent(new TargetIntent.EntityDecision.Remove(old("one"))), List.of()));
        for (int removed = 1; removed <= 2; removed++) {
            String revision = Integer.toString(removed + 2);
            try (var view = service.reserveView(fixture.lease, plan)) {
                final int count = removed;
                view.run(() -> {
                    view.pin(revision);
                    var pairs = V3PlanComputedViews.cooccurrences(view, true, 0, 100);
                    assertEquals(count == 1 ? 1 : 0, pairs.total());
                    assertEquals(count == 1 ? List.of(1) : List.of(), pairs.items().stream().map(V3PlanComputedViews.Cooccurrence::contributorTotal).toList());
                    assertEquals(count == 1 ? 2 : 0, V3PlanComputedViews.nodes(view, true, 0, 100).total());
                    assertEquals(2, V3PlanComputedViews.nodes(view, false, 0, 100).total()); return true;
                });
            }
            if (removed == 1) service.replaceDraft(fixture.lease, plan, new HostedPlanService.Mutation("3", UUID.randomUUID().toString()),
                    new Draft(intent(new TargetIntent.EntityDecision.Remove(old("one")), new TargetIntent.EntityDecision.Remove(old("two"))), List.of()));
        }
        service.replaceDraft(fixture.lease, plan, new HostedPlanService.Mutation("4", UUID.randomUUID().toString()),
                new Draft(intent(edit("one", new FieldValue.KeepObserved(), new FieldValue.Unresolved())), List.of()));
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("5"); assertEquals(2, V3PlanComputedViews.nodes(view, false, 0, 100).total());
                assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET, assertThrows(PlanRefusal.class,
                        () -> V3PlanComputedViews.cooccurrences(view, true, 0, 100)).code()); return true;
            });
        }
    }
    @Test void exactUnicodeOrderingAndPageInputLimitsArePreserved() {
        String xml = "<items><item id='one' tone='é'/><item id='two' tone='é'/><item id='three' tone='𐀀'/><item id='four' tone=''/><item id='five' tone=' alpha'/></items>";
        var fixture = SharedV3PlanXmlTest.with(definition(false), xml); var service = fixture.service(); String plan = fixture.inspected(service);
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("2"); var page = V3PlanComputedViews.nodes(view, false, 0, 100);
                assertEquals(List.of(" alpha", "é", "é", "", "𐀀"), page.items().stream().map(n -> n.key().value()).toList());
                assertEquals(5, page.total()); assertTrue(page.items().stream().allMatch(n -> n.contributorTotal() == 1));
                for (int[] bounds : List.of(new int[]{-1, 1}, new int[]{0, 0}, new int[]{0, 101}))
                    assertEquals(PlanRefusal.Code.INVALID_REQUEST, assertThrows(PlanRefusal.class,
                            () -> V3PlanComputedViews.nodes(view, false, bounds[0], bounds[1])).code());
                assertEquals("ComputedPage[redacted]", page.toString());
                assertEquals("ComputedNodeView[redacted]", page.items().getFirst().toString()); return true;
            });
        }
    }
    @Test void forgedFullTargetProofRefusesEvenAnOriginalOrBeyondEndRequest() {
        var fixture = new SharedV3PlanXmlTest(); var adapter = new SharedV3PlanCompositionAuthorityTest.ControlledContent();
        var service = fixture.service(adapter); String plan = fixture.inspected(service); adapter.forge = true;
        assertTrue(service.materialize(fixture.lease, plan, "2").complete());
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("2");
                for (boolean target : List.of(false, true)) assertEquals(PlanRefusal.Code.PROJECTION_REFUSED,
                        assertThrows(PlanRefusal.class, () -> V3PlanComputedViews.nodes(view, target, Integer.MAX_VALUE, 100)).code());
                return true;
            });
        }
    }
    @Test void aRenderedContributorPageCannotOutliveItsOriginalAdmission() throws Exception {
        var fixture = new SharedV3PlanXmlTest(); var service = fixture.service(); String plan = fixture.inspected(service);
        var view = service.reserveView(fixture.lease, plan); var entered = new CountDownLatch(1); var released = new CountDownLatch(1);
        var result = new AtomicReference<Object>(); var failure = new AtomicReference<Throwable>();
        var flag = new AtomicReference<studio.environment.core.observation.ObservationPort.Cancellation>();
        var thread = new Thread(() -> {
            try { result.set(view.run(() -> { view.pin("2"); return view.read((snapshot, control) -> {
                var page = V3PlanComputedViews.contributors(view, false, new ResultKey.NodeKey(new ComputedGraph.Key("tones", "by-tone", "alpha")), 0, 1, true);
                assertEquals(2, page.total()); flag.set(control); entered.countDown();
                try { assertTrue(released.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                return page;
            }); })); } catch (Throwable error) { failure.set(error); }
        }); thread.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); view.close(); assertTrue(flag.get().cancelled());
            assertEquals(PlanRefusal.Code.CAPACITY, assertThrows(PlanRefusal.class, () -> service.reserveView(fixture.lease, plan)).code());
        } finally { released.countDown(); thread.join(5000); view.close(); }
        assertFalse(thread.isAlive()); assertNull(result.get()); assertEquals(PlanRefusal.Code.CONFLICT, assertInstanceOf(PlanRefusal.class, failure.get()).code());
    }
    @Test void duplicatePairsRetainEveryContributorAndOrderedFieldRole() {
        String xml = "<items><item id='one' tone='alpha' finish='x'/><item id='two' tone='alpha' finish='x'/><item id='three' tone='beta' finish='y'/></items>";
        var fixture = SharedV3PlanXmlTest.with(definition(false), xml);
        var service = fixture.service(); String plan = fixture.inspected(service);
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("2"); var snapshot = view.snapshot();
                var members = assertDoesNotThrow(() -> V3PlanComputedViews.memberships(view, false, 0, 100));
                assertEquals(6, members.total());
                assertTrue(members.items().stream().allMatch(m -> m.contributorTotal() == 1));
                var pairs = V3PlanComputedViews.cooccurrences(view, false, 0, 100);
                assertEquals(2, pairs.total());
                assertEquals(List.of("alpha:x", "beta:y"), pairs.items().stream().map(p -> p.source().value()+":"+p.target().value()).toList());
                assertEquals(List.of(2,1), pairs.items().stream().map(V3PlanComputedViews.Cooccurrence::contributorTotal).toList());
                var pair = pairs.items().getFirst(); var key = new ResultKey.CooccurrenceKey(pair.relation(), pair.source(), pair.target());
                for (int index = 0; index < 2; index++) {
                    var page = V3PlanComputedViews.contributors(view, false, key, index, 1, true);
                    assertEquals(2, page.total()); assertEquals(index == 0 ? OptionalInt.of(1) : OptionalInt.empty(), page.nextOffset());
                    var contributor = page.items().getFirst();
                    assertEquals(snapshot.reference(old(index == 0 ? "one" : "two")), contributor.physical());
                    assertEquals(index + 1, contributor.origin().elementIndex());
                    assertEquals(List.of("tone", "finish"), contributor.roles().stream().map(V3PlanComputedViews.FieldLocation::field).toList());
                    assertEquals(List.of("alpha", "x"), contributor.roles().stream().map(r -> r.location().value().decodedValue()).toList());
                    for (var role : contributor.roles()) {
                        var pin = role.location().value();
                        assertEquals("sheet", pin.documentId()); assertEquals(digest(xml), pin.sourceDigest());
                        assertEquals(pin.decodedValue(), xml.substring(pin.valueStart(), pin.valueEnd()));
                    }
                }
                var node = new ResultKey.NodeKey(new ComputedGraph.Key("tones", "by-tone", "alpha"));
                assertEquals(2, V3PlanComputedViews.contributors(view, false, node, 0, 100, true).total());
                var member = members.items().getFirst();
                assertEquals(1, V3PlanComputedViews.contributors(view, false,
                        new ResultKey.MembershipKey(member.relation(), member.physical(), member.computed()), 0, 100, true).total());
                assertEquals(2, V3PlanComputedViews.rules(view, false, 0, 100).total());
                assertTrue(V3PlanComputedViews.rules(view, false, 0, 100).items().stream().allMatch(r -> r.outcome() == Outcome.PASS));
                assertEquals(PlanRefusal.Code.DISCLOSURE_REQUIRED, assertThrows(PlanRefusal.class,
                        () -> V3PlanComputedViews.contributors(view, false, key, 0, 100, false)).code());
                assertEquals(PlanRefusal.Code.NOT_FOUND, assertThrows(PlanRefusal.class,
                        () -> V3PlanComputedViews.contributors(view, false, new ResultKey.NodeKey(new ComputedGraph.Key("tones", "by-tone", "missing")), 0, 100, true)).code());
                return true;
            });
        }
    }
    @Test void originalComputedNodesArePagedWithExactKeysAndCompleteContributorCounts() {
        var fixture = new SharedV3PlanXmlTest();
        var service = fixture.service();
        String plan = fixture.inspected(service);
        try (var view = service.reserveView(fixture.lease, plan)) {
            view.run(() -> {
                view.pin("2");
                var first = assertDoesNotThrow(() -> V3PlanComputedViews.nodes(view, false, 0, 2));
                assertEquals("2", first.revision());
                assertEquals(4, first.total());
                assertEquals(OptionalInt.of(2), first.nextOffset());
                assertEquals(List.of("finishes:by-finish:x", "finishes:by-finish:y"),
                        first.items().stream().map(n -> n.key().computedType()+":"+n.key().derivation()+":"+n.key().value()).toList());
                assertEquals(List.of(2, 1), first.items().stream().map(V3PlanComputedViews.Node::contributorTotal).toList());
                var second = V3PlanComputedViews.nodes(view, false, 2, 2);
                assertEquals(List.of("alpha", "beta"), second.items().stream().map(n -> n.key().value()).toList());
                assertEquals(List.of(2, 1), second.items().stream().map(V3PlanComputedViews.Node::contributorTotal).toList());
                assertTrue(second.nextOffset().isEmpty());
                var beyond = V3PlanComputedViews.nodes(view, false, Integer.MAX_VALUE, 100);
                assertEquals(4, beyond.total()); assertTrue(beyond.items().isEmpty()); assertTrue(beyond.nextOffset().isEmpty());
                assertEquals(PlanRefusal.Code.INCOMPLETE_TARGET,
                        assertThrows(PlanRefusal.class, () -> V3PlanComputedViews.nodes(view, true, 0, 100)).code());
                return true;
            });
        }
    }
}
