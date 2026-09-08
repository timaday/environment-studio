package studio.environment.server;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CapabilitiesController {
    private final studio.environment.server.security.RuntimeConfiguration.RuntimeMode mode;
    CapabilitiesController(studio.environment.server.security.RuntimeConfiguration.RuntimeMode mode) { this.mode = mode; }
    record Capabilities(String mode, boolean inspectionEnabled, boolean exportEnabled,
                        List<String> qualifiedDatabaseAdapters, List<String> blockers) { }

    @GetMapping("/api/v1/capabilities")
    Capabilities capabilities() {
        return new Capabilities(mode.name().toLowerCase(java.util.Locale.ROOT), false, false, List.of(),
                mode == studio.environment.server.security.RuntimeConfiguration.RuntimeMode.DEMO
                        ? List.of("HOSTED_AUTH_NOT_IMPLEMENTED", "DATABASE_ADAPTERS_NOT_QUALIFIED", "SQL_WRITERS_NOT_IMPLEMENTED")
                        : List.of("DATABASE_ADAPTERS_NOT_QUALIFIED", "SQL_WRITERS_NOT_IMPLEMENTED"));
    }
}
