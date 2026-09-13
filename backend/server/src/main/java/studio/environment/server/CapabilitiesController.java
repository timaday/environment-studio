package studio.environment.server;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CapabilitiesController {
    private final studio.environment.server.security.RuntimeConfiguration.RuntimeMode mode;
    private final studio.environment.server.workspace.WorkspaceRuntime workspace;
    private final studio.environment.server.plan.PlanRuntime plans;
    CapabilitiesController(studio.environment.server.security.RuntimeConfiguration.RuntimeMode mode,
            studio.environment.server.workspace.WorkspaceRuntime workspace,
            studio.environment.server.plan.PlanRuntime plans) { this.mode = mode; this.workspace = workspace; this.plans = plans; }
    record Capabilities(String mode, boolean definitionWorkspaceEnabled, boolean inspectionEnabled,
                        boolean inspectionUiEnabled, boolean inspectionApiConfigured, boolean exportEnabled,
                        List<String> qualifiedDatabaseAdapters, List<String> blockers) { }

    @GetMapping("/api/v1/capabilities")
    Capabilities capabilities() {
        return new Capabilities(mode.name().toLowerCase(java.util.Locale.ROOT), workspace.enabled(), false,
                false, plans.inspectionApiConfigured(), false, List.of(),
                mode == studio.environment.server.security.RuntimeConfiguration.RuntimeMode.DEMO
                        ? List.of("HOSTED_MODE_REQUIRED", "DATABASE_ADAPTERS_NOT_QUALIFIED", "SQL_WRITERS_NOT_IMPLEMENTED")
                        : List.of("DATABASE_ADAPTERS_NOT_QUALIFIED", "SQL_WRITERS_NOT_IMPLEMENTED"));
    }
}
