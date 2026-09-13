package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;

class PlanEncodingAuthorityTest {
    @JsonPropertyOrder({"prefix","value"})
    public static final class DuringEncoding {
        private final Runnable transition;final AtomicInteger reads=new AtomicInteger();
        DuringEncoding(Runnable transition) { this.transition=transition; }
        public String getPrefix() { reads.incrementAndGet();return "p".repeat(20000); }
        public String getValue() { transition.run();return "after"; }
    }
    @SuppressWarnings("unchecked")
    static List<byte[]> chunks(PlanViewEncoding encoding) throws Exception {
        var field=PlanViewEncoding.class.getDeclaredField("chunks");field.setAccessible(true);
        return List.copyOf((List<byte[]>)field.get(encoding));
    }
    @Test void revokedBeforeEncodingRefusesBeforeAnyGetterOrOutput() {
        var touched=new AtomicBoolean();var bytes=new ByteArrayOutputStream();
        try(var encoding=new PlanViewEncoding(32768,()->{throw new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED);})) {
            var value=new DuringEncoding(()->touched.set(true));
            assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->encoding.encode(value)).code());
            assertFalse(touched.get());assertEquals(0,value.reads.get());assertThrows(IllegalStateException.class,()->encoding.write(bytes,()->{}));assertEquals(0,bytes.size());
        }
    }
    @Test void lostAuthorityOrOriginalDeadlineDuringSerializationWipesAlreadyAllocatedBytes() throws Exception {
        for(boolean deadline:new boolean[]{false,true}) {
            var live=new AtomicBoolean(true);var now=new AtomicLong(1);long originalDeadline=10;
            var retained=new ArrayList<byte[]>();var bytes=new ByteArrayOutputStream();
            Runnable verify=()->{if(!live.get())throw new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED);
                if(now.get()>=originalDeadline)throw new PlanRefusal(PlanRefusal.Code.CANCELLED);};
            try(var encoding=new PlanViewEncoding(32768,verify)) {
                var value=new DuringEncoding(()->{
                    try {retained.addAll(chunks(encoding));}catch(Exception failure){throw new AssertionError(failure);}
                    if(deadline)now.set(originalDeadline);else live.set(false);
                });
                assertEquals(deadline?PlanRefusal.Code.CANCELLED:PlanRefusal.Code.SESSION_REQUIRED,
                        assertThrows(PlanRefusal.class,()->encoding.encode(value)).code());
                assertFalse(retained.isEmpty(),"prefix must exercise cleanup of actual allocated bytes");
                for(byte[] chunk:retained)assertArrayEquals(new byte[chunk.length],chunk);
                assertTrue(chunks(encoding).isEmpty());
                assertThrows(IllegalStateException.class,()->encoding.write(bytes,verify));assertEquals(0,bytes.size());
                assertThrows(IllegalStateException.class,()->encoding.encode("retry"));
            }
        }
    }
    @Test void unexpectedVerifierFailureHasOnlyStaticDiagnosticAndCannotBeReused() throws Exception {
        var encoding=new PlanViewEncoding(32768,()->{throw new IllegalStateException("invented-encoding-message");});
        var failure=assertThrows(IllegalStateException.class,()->encoding.encode("invented-value"));
        assertEquals("PLAN_RESPONSE_ENCODING_UNAVAILABLE",failure.getMessage());assertNull(failure.getCause());
        assertTrue(chunks(encoding).isEmpty());assertThrows(IllegalStateException.class,()->encoding.encode("retry"));
        encoding.close();
    }
    @Test void exactUtf8LimitAndOneByteOverflowRetainIndependentLiteralOutput() throws Exception {
        // Quotes2 + ASCII32762 + four-byte supplementary character =32768 bytes.
        String value="a".repeat(32762)+"𐀀";var checks=new AtomicInteger();
        try(var encoding=new PlanViewEncoding(32768,checks::incrementAndGet)) {
            encoding.encode(value);assertEquals(32768,encoding.size());var output=new ByteArrayOutputStream();
            encoding.write(output,()->{});assertEquals("\""+value+"\"",output.toString(StandardCharsets.UTF_8));assertTrue(checks.get()>0);
        }
        try(var encoding=new PlanViewEncoding(32768,()->{})) {
            assertEquals(PlanRefusal.Code.RESOURCE_LIMIT,assertThrows(PlanRefusal.class,()->encoding.encode(value+"a")).code());
            assertTrue(chunks(encoding).isEmpty());
        }
    }
}
