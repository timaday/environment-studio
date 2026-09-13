package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.ServletOutputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.server.session.HostedSessions;

/** Independent invented schedules; no current v3 publication authority. */
class IndependentV1PlanVersionTest {
    @Test void aRetiredV3OperationStaysHiddenWhenCurrentPlanReturnsToV2() throws Exception {
        var f = new PlanV1VersionBoundaryTest.Fixture();
        var old = f.create(true);
        String operation = f.service.reserve(f.lease, old.planId(),
                new HostedPlanService.Mutation("1", UUID.randomUUID().toString())).operationId().orElseThrow();
        f.service.cancel(f.lease, operation);
        f.service.discard(f.lease, old.planId(), new HostedPlanService.Mutation("1", UUID.randomUUID().toString()));
        var current = f.create(false);
        var mvc = MockMvcBuilders.standaloneSetup(f.controller, f.views).build();
        mvc.perform(get("/api/v1/operations/" + operation).requestAttr(HostedSessions.REQUEST_LEASE, f.lease))
                .andExpect(status().isNotFound()).andExpect(content().json("{\"code\":\"NOT_FOUND\"}"));
        mvc.perform(get("/api/v1/plans/current").requestAttr(HostedSessions.REQUEST_LEASE, f.lease))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planId").value(current.planId()));
        assertEquals(HostedPlanService.Phase.CANCELLED, f.service.status(f.lease, operation).phase());
        assertEquals(1, f.closes.get());
    }

    @Test void sameVersionReplacementDuringOutputAcquisitionCannotPublishOldSummary() throws Exception {
        var f = new PlanV1VersionBoundaryTest.Fixture();
        var original = f.create(false);
        var replacement = new AtomicReference<String>();
        var response = new MockHttpServletResponse() {
            @Override public ServletOutputStream getOutputStream() {
                f.service.discard(f.lease, original.planId(), new HostedPlanService.Mutation("1", UUID.randomUUID().toString()));
                replacement.set(f.create(false).planId());
                return super.getOutputStream();
            }
        };
        PlanV1VersionBoundaryTest.missing(() -> f.controller.current(PlanV1VersionBoundaryTest.unread(f), response));
        assertEquals(0, response.getContentAsByteArray().length);
        assertNotEquals(original.planId(), replacement.get());
        assertEquals(replacement.get(), f.service.view(f.lease, Optional.empty()).planId());
        var after = new MockHttpServletResponse();
        f.controller.current(PlanV1VersionBoundaryTest.unread(f), after);
        assertEquals(200, after.getStatus());
        assertTrue(after.getContentAsString().contains(replacement.get()));
    }

    @Test void requestVersionHintsCannotSelectV3ThroughTheLegacyRoute() throws Exception {
        var f = new PlanV1VersionBoundaryTest.Fixture();
        var plan = f.create(true);
        var mvc = MockMvcBuilders.standaloneSetup(f.controller, f.views).build();
        for (String path : new String[]{"/api/v1/plans/current", "/api/v1/plans/" + plan.planId()}) {
            mvc.perform(get(path).param("version", "V3").param("schemaVersion", "3")
                            .header("X-Plan-Version", "V3").requestAttr(HostedSessions.REQUEST_LEASE, f.lease))
                    .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().json("{\"code\":\"NOT_FOUND\"}"));
        }
        assertEquals(0, f.reservations.get());
        assertEquals(plan.planId(), f.service.view(f.lease, Optional.empty()).planId());
    }
}
