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
        var blockers = new java.util.ArrayList<String>();
        if (mode != studio.environment.server.security.RuntimeConfiguration.RuntimeMode.HOSTED) blockers.add("HOSTED_MODE_REQUIRED");
        if (!plans.postgresql16PilotConfigured()) blockers.add("DATABASE_ADAPTERS_NOT_QUALIFIED");
        if (!plans.exportConfigured()) blockers.add("SQL_WRITERS_NOT_IMPLEMENTED");
        boolean enabled = blockers.isEmpty();
        return new Capabilities(mode.name().toLowerCase(java.util.Locale.ROOT), workspace.enabled(), enabled,
                enabled, plans.inspectionApiConfigured(), plans.exportConfigured(), plans.qualifiedDatabaseAdapters(), List.copyOf(blockers));
    }
}
