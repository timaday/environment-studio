package studio.environment.core.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv2.NativeDefinition.*;

class ObservationInventoryTest {
    final Binding binding = new Binding("invented", Engine.POSTGRESQL, Storage.TEXT, "invented", "sample", "key", "xml", KeyType.TEXT,
            List.of(new Document("first", "01", List.of()), new Document("second", "1", List.of())));
    ObservationResult.Document row(String id, String key) { return new ObservationResult.Document(id, new ObservationResult.Key("text", key), "<invented/>", 11, 11, "independent-mock-digest"); }
    @Test void exactTypedMembershipPreservesLeadingZerosAndDocumentIdentity() {
        assertTrue(ObservationInventory.complete(binding, List.of(row("second", "1"), row("first", "01"))));
        assertFalse(ObservationInventory.complete(binding, List.of(row("first", "1"), row("second", "01"))));
        assertFalse(ObservationInventory.complete(binding, List.of(row("first", "01"), row("first", "01"))));
        assertFalse(ObservationInventory.complete(binding, List.of(row("first", "01"))));
    }
    @Test void credentialsAreOperationOwnedAndSafeToPrint() {
        var password = "independent-credential-canary".toCharArray();
        var credentials = new TransientCredentials("invented-reader".toCharArray(), password);
        credentials.close();
        assertTrue(credentials.closed());
        assertThrows(IllegalStateException.class, credentials::copyPassword);
        assertFalse(credentials.toString().contains("canary"));
        assertEquals('i', password[0]); // The caller's buffer remains its responsibility.
    }
}
