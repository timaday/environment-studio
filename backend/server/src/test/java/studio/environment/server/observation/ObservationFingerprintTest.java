package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ObservationFingerprintTest {
    @Test void nativeFramesMatchIndependentUtf8OracleAndIgnoreMapOrder() {
        // Expected digest calculated by an independent Python frame implementation, not this Java code.
        Map<String,Object> value=Map.of("engine","oracle","documents",List.of(Map.of("documentId","é𐀀","xml","<x>𐀀</x>","utf8Bytes",11,"characters",9)),"cleanup","complete");
        assertEquals("8529bb9cc0fe275e0b6cd0d0fa79f04c0ee2508bb65a9b4933bf34aba6e9e68b",ObservationFingerprint.hash(value));
        var reversed=new LinkedHashMap<String,Object>();
        reversed.put("cleanup","complete"); reversed.put("documents",value.get("documents")); reversed.put("engine","oracle");
        assertEquals(ObservationFingerprint.hash(value),ObservationFingerprint.hash(reversed));
        reversed.put("cleanup","inconclusive");
        assertNotEquals(ObservationFingerprint.hash(value),ObservationFingerprint.hash(reversed));
    }
    @Test void invalidUtf16CannotBecomeReplacementCharactersInAuthority() {
        assertThrows(ObservationFailure.class,()->ObservationFingerprint.source("\uD800"));
    }
}
