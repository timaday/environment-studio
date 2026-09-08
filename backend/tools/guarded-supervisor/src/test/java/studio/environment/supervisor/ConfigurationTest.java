package studio.environment.supervisor;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigurationTest {
    @Test void admitsClosedInventedConfigurationShapeWithoutConferringQualification() throws Exception {
        assertTrue(Configuration.accepts(Files.readAllBytes(Path.of("../../../fixtures/guarded-supervisor-v1/configuration.json"))));
    }
}
