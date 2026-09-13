package studio.environment.server.planning;

import java.util.List;
import java.math.BigInteger;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
import studio.environment.core.definitionv3.*;

/** Independently invented XML; explicit test publication never changes compiler readiness. */
public final class V3ComputedWireFixtures {
    private final SharedV3PlanXmlTest fixture;
    public final HostedPlanService service;
    public final SessionLedger.Lease lease;
    public final String plan;
    public V3ComputedWireFixtures(boolean child, String xml, boolean failedRules) {
        this(child, xml, failedRules, false);
    }
    public V3ComputedWireFixtures(boolean child, String xml, boolean failedRules, boolean self) {
        var checked = DerivedTargetInputAdapterTest.definition(child);
        if (failedRules || self) {
            var definition = checked.definition(); var l = definition.logical();
            var logical = new NativeDefinition.Logical(l.entityTypes(), l.relations(), l.rules(), l.operationCapabilities(), l.computedTypes(), l.derivations(),
                    List.of(new NativeDefinition.Cooccurrence("pair", "by-tone", self ? "by-tone" : "by-finish", BigInteger.ZERO, failedRules ? BigInteger.ONE : BigInteger.TEN)), l.computedRules());
            checked = org.junit.jupiter.api.Assertions.assertInstanceOf(NativeCompilationResult.Incomplete.class,
                    new NativeDefinitionCompiler().compile(new NativeDefinition(definition.id(), definition.revision(), logical, definition.bindings()))).checked();
        }
        fixture = SharedV3PlanXmlTest.with(checked, xml); service = fixture.service(); lease = fixture.lease; plan = fixture.inspected(service);
    }
    public HostedPlanService.ViewSnapshot snapshot(String revision) { return fixture.snapshot(service, plan, revision); }
    public PlanCommand.Ref reference(String identity) { return snapshot("2").reference(DerivedTargetInputAdapterTest.old(identity)); }
}
