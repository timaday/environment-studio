package studio.environment.server.plan;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CredentialJsonReaderTest {
    @Test void decodesEscapesAndSupplementaryCharactersIntoOnlyOwnedBuffers() {
        char[] user=new char[256],password=new char[2048];
        var lengths=CredentialJsonReader.read(input("{\"password\":\"MiXeD\\uD800\\uDC00\\tValue\",\"username\":\"Mock\\u0052eader\"}"),user,password,()->0L,10);
        assertEquals(10,lengths.username());
        assertTrue(Arrays.equals("MockReader".toCharArray(),Arrays.copyOf(user,lengths.username())),"Owned user decoding mismatch");
        assertTrue(Arrays.equals("MiXeD𐀀\tValue".toCharArray(),Arrays.copyOf(password,lengths.password())),"Owned secret decoding mismatch");
    }
    @Test void rejectsDuplicatesUnknownTrailingMalformedUnicodeAndClearsPartialBuffers() {
        for(String json:new String[]{"{\"username\":\"u\",\"username\":\"x\",\"password\":\"p\"}","{\"username\":\"u\",\"password\":\"p\",\"extra\":0}","{\"username\":\"u\",\"password\":\"p\"}{}","{\"username\":\"u\",\"password\":\"\\uD800\"}","{\"username\":\"\",\"password\":\"p\"}"}) {
            char[] user=new char[256],password=new char[2048];
            assertEquals(PlanBodyFailure.Code.MALFORMED_BODY,assertThrows(PlanBodyFailure.class,()->CredentialJsonReader.read(input(json),user,password,()->0L,10)).code());
            assertTrue(Arrays.equals(user,new char[user.length]) && Arrays.equals(password,new char[password.length]),"Partial credential buffers must be cleared");
        }
    }
    @Test void byteLimitAndDeadlineAreClosedRefusals() {
        char[] user=new char[256],password=new char[2048];
        assertEquals(PlanBodyFailure.Code.BODY_TOO_LARGE,assertThrows(PlanBodyFailure.class,()->CredentialJsonReader.read(input(" ".repeat(16_385)),user,password,()->0L,10)).code());
        assertEquals(PlanBodyFailure.Code.BODY_DEADLINE,assertThrows(PlanBodyFailure.class,()->CredentialJsonReader.read(input("{}"),user,password,()->10L,10)).code());
    }
    private static ByteArrayInputStream input(String json) { return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)); }
}
