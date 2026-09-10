package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.workspace.NativeCommand;

class V3PlanReplyTest {
    static String encoded(V3PlanReply reply) throws Exception {
        try(var encoded=new PlanViewEncoding(32768)) {
            encoded.encode(reply.wire());var output=new ByteArrayOutputStream();encoded.write(output,()->{});return output.toString(StandardCharsets.UTF_8);
        }
    }
    @Test void acknowledgementAndStatusHaveLiteralClosedBytesAndExactLargeRevisions() throws Exception {
        var ack=new V3PlanReply.Acknowledgement(new HostedPlanService.Ack("plan-a","9007199254740993",Optional.empty()));
        assertEquals("{\"planId\":\"plan-a\",\"revision\":\"9007199254740993\"}",encoded(ack));
        var reserved=new V3PlanReply.Acknowledgement(new HostedPlanService.Ack("plan-a","2",Optional.of("operation-a")));
        assertEquals("{\"planId\":\"plan-a\",\"revision\":\"2\",\"operationId\":\"operation-a\"}",encoded(reserved));
        var status=new V3PlanReply.Status(new HostedPlanService.Status("operation-a","plan-a",HostedPlanService.Phase.CANCELLED,"CANCELLED",HostedPlanService.Cleanup.IN_PROGRESS,Optional.of("9007199254740993")));
        assertEquals("{\"operationId\":\"operation-a\",\"planId\":\"plan-a\",\"phase\":\"cancelled\",\"code\":\"CANCELLED\",\"cleanup\":\"in-progress\",\"installedRevision\":\"9007199254740993\"}",encoded(status));
        assertThrows(UnsupportedOperationException.class,()->ack.wire().put("arbitrary",true));
        assertFalse(ack.toString().contains("plan-a"));assertFalse(status.toString().contains("operation-a"));
    }
    @Test void summaryKeepsMissingAndCompleteEmptyComputedPartitionsDistinct() throws Exception {
        var physical=new HostedPlanService.View("plan-a","2",new NativeCommand.Reference("00000000-0000-4000-8000-000000000009","2"),"binding-a","destination-a",Optional.empty(),
                new HostedPlanService.Counts(1,2,3),new HostedPlanService.Counts(0,0,0),true,false,false,List.of("EXPORT_UNAVAILABLE"),Optional.empty());
        var reply=new V3PlanReply.Summary(new HostedPlanService.V3View(physical,Optional.of(new HostedPlanService.ComputedCounts(0,0,0)),Optional.empty()));
        String expected="{\"planId\":\"plan-a\",\"revision\":\"2\",\"definition\":{\"objectId\":\"00000000-0000-4000-8000-000000000009\",\"workspaceRevision\":\"2\"},\"bindingId\":\"binding-a\",\"destinationId\":\"destination-a\",\"currentCounts\":{\"documents\":1,\"entities\":2,\"relations\":3},\"targetCounts\":{\"documents\":0,\"entities\":0,\"relations\":0},\"observedDestination\":null,\"inspectionValid\":true,\"targetComplete\":false,\"exportAvailable\":false,\"blockers\":[\"EXPORT_UNAVAILABLE\"],\"currentComputedCounts\":{\"nodes\":0,\"memberships\":0,\"cooccurrences\":0},\"targetComputedCounts\":null}";
        assertEquals(expected,encoded(reply));assertFalse(reply.toString().contains("destination-a"));
    }
    @Test void allRepliesRequireActualV3OwnershipAndPureChecksDoNotExpireReservation() throws Exception {
        for(boolean v3:new boolean[]{false,true}) {
            var f=new PlanV1VersionBoundaryTest.Fixture();var plan=f.create(v3);
            var ack=f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
            var status=f.service.status(f.lease,ack.operationId().orElseThrow());
            var raw=f.service.view(f.lease,Optional.empty());
            var nominal=new HostedPlanService.V3View(raw,Optional.empty(),Optional.empty());
            for(V3PlanReply reply:List.of(new V3PlanReply.Acknowledgement(ack),new V3PlanReply.Status(status),new V3PlanReply.Summary(nominal))) {
                if(v3)assertDoesNotThrow(()->reply.verify(f.service,f.lease));
                else assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->reply.verify(f.service,f.lease)).code());
            }
            if(v3) {
                f.clock.set(61_000_000_000L);new V3PlanReply.Acknowledgement(ack).verify(f.service,f.lease);new V3PlanReply.Status(status).verify(f.service,f.lease);
                assertEquals(0,f.closes.get());
            }
            f.service.cancel(f.lease,ack.operationId().orElseThrow());
        }
    }
    @Test void summaryVerificationRetainsOriginalSameRevisionStateAndLease() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var plan=f.create(true);
        var reply=new V3PlanReply.Summary(f.service.viewV3(f.lease,Optional.empty()));reply.verify(f.service,f.lease);
        var ack=f.service.reserve(f.lease,plan.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        assertEquals(PlanRefusal.Code.CONFLICT,assertThrows(PlanRefusal.class,()->reply.verify(f.service,f.lease)).code());
        var current=new V3PlanReply.Summary(f.service.viewV3(f.lease,Optional.empty()));current.verify(f.service,f.lease);
        f.ledger.close(f.lease.id());
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->current.verify(f.service,f.lease)).code());
        f.service.invalidate(f.lease);
    }
}
