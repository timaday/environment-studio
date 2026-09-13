package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeDefinition.Engine;
import studio.environment.core.observation.*;

class ObservationBoundaryTest {
    ObservationDestination destination() { return new ObservationDestination("invented", Engine.POSTGRESQL, "127.0.0.1", 1, "invented", ObservationDestination.Transport.DISPOSABLE_LOOPBACK, "", "independent-test-transport", Map.of("systemIdentifier", "1", "databaseOid", "2", "databaseName", "invented"), "invented-policy-v1", "postgresql-read-operation-v1"); }
    @Test void invalidSelectionAndCancellationRefuseBeforeConnectionAllocationAndClearCredentials() {
        var credentials = new TransientCredentials("invented".toCharArray(), "independent-password-canary".toCharArray());
        var result = new JdbcObservation(destination()).observe(new ObservationPort.Selection(null, "missing"), credentials, new ObservationPort.Cancellation());
        assertEquals(ObservationResult.Code.INVALID_SELECTION, ((ObservationResult.Refused) result).code());
        assertTrue(credentials.closed());
        assertFalse(result.toString().contains("canary"));
    }
    @Test void productionConfigurationCannotUseUnverifiedRemoteTransport() {
        assertThrows(IllegalArgumentException.class, () -> new ObservationDestination("invented", Engine.ORACLE, "remote.invalid", 1521, "invented", ObservationDestination.Transport.DISPOSABLE_LOOPBACK, "", "unverified", Map.of(), "invented", "invented"));
    }
}
