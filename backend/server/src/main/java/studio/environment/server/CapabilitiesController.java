package studio.environment.server;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CapabilitiesController {
    record Capabilities(String mode, boolean inspectionEnabled, boolean exportEnabled,
                        List<String> qualifiedDatabaseAdapters, List<String> blockers) { }

    @GetMapping("/api/v1/capabilities")
    Capabilities capabilities() {
        return new Capabilities("demo", false, false, List.of(),
                List.of("DEFINITION_COMPILER_NOT_IMPLEMENTED", "HOSTED_AUTH_NOT_IMPLEMENTED",
                        "DATABASE_ADAPTERS_NOT_QUALIFIED", "SQL_WRITERS_NOT_IMPLEMENTED"));
    }
}
