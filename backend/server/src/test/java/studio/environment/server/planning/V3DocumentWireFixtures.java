package studio.environment.server.planning;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
/** Invented qualified child-property source; explicit test publication only. */
public final class V3DocumentWireFixtures {
    public static final String XML="<items xmlns:p='urn:props'><item id='one' finish='x' lookalike='alpha'><p:entry p:key='tone' p:value='al&#112;ha'/></item><item id='two' finish='y'/></items>";
    private final SharedV3PlanXmlTest fixture=SharedV3PlanXmlTest.with(DerivedTargetInputAdapterTest.definition(true),XML);
    public final HostedPlanService service=fixture.service();
    public final SessionLedger.Lease lease=fixture.lease;
    public final String plan=fixture.inspected(service);
    public HostedPlanService.ViewSnapshot snapshot(String revision){return fixture.snapshot(service,plan,revision);}
    public PlanCommand.Ref reference(String identity){return snapshot("2").reference(DerivedTargetInputAdapterTest.old(identity));}
}
