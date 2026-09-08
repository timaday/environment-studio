package studio.environment.server.observation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.observation.ObservationPort;
import studio.environment.core.observation.ObservationResult.Code;

class ObservationStreamTest {
    SqlRead read() { return new SqlRead(null, new ObservationPort.Cancellation(), System.nanoTime()+5_000_000_000L); }
    Reader singleCharacters(String source) {
        return new StringReader(source) {
            @Override public int read(char[] buffer,int offset,int count) throws IOException { return super.read(buffer,offset,Math.min(count,1)); }
        };
    }
    @Test void splitSurrogatesAndStrictUtf8BoundariesPreserveEveryCharacter() throws Exception {
        String source="aé𐀀\r\n";
        assertEquals(source,JdbcObservation.stream(read(),singleCharacters(source),9));
        var over=assertThrows(ObservationFailure.class,()->JdbcObservation.stream(read(),singleCharacters(source),8));
        assertEquals(Code.RESOURCE_LIMIT,over.code());
        for(String invalid:new String[]{"\uD800","\uDC00","\uD800a"}) {
            var failure=assertThrows(ObservationFailure.class,()->JdbcObservation.stream(read(),singleCharacters(invalid),100));
            assertEquals(Code.INVALID_SOURCE,failure.code());
        }
    }
    @Test void exactUtf16LimitAndCancelledStreamsCannotAuthorizePartialSource() throws Exception {
        String exact="x".repeat(1_048_576);
        assertEquals(exact,JdbcObservation.stream(read(),new StringReader(exact),1_048_576));
        assertEquals(Code.RESOURCE_LIMIT,assertThrows(ObservationFailure.class,()->JdbcObservation.stream(read(),new StringReader(exact+"x"),1_048_577)).code());
        var read=read();read.cancellation.cancel();
        assertEquals(Code.CANCELLED,assertThrows(ObservationFailure.class,()->JdbcObservation.stream(read,new StringReader("invented"),100)).code());
        assertEquals(Code.NULL_SOURCE,assertThrows(ObservationFailure.class,()->JdbcObservation.stream(read(),null,100)).code());
    }
}
