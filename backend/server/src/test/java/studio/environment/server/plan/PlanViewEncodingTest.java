package studio.environment.server.plan;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
class PlanViewEncodingTest {
    @Test void boundedEncodingCountsActualUtf8AndChecksBeforeEveryTransfer() throws Exception {
        var encoded=new PlanViewEncoding(64);encoded.encode(Map.of("text","MiXeD 𐀀"));
        var bytes=new ByteArrayOutputStream();var checks=new AtomicInteger();encoded.write(bytes,checks::incrementAndGet);
        assertEquals("{\"text\":\"MiXeD 𐀀\"}",bytes.toString(StandardCharsets.UTF_8));assertEquals(bytes.size(),encoded.size());assertTrue(checks.get()>0);
        var small=new PlanViewEncoding(4);assertThrows(studio.environment.core.plan.PlanRefusal.class,()->small.encode(Map.of("text","long")));
    }
    @Test void revocationBeforeTransferEmitsNoBytes() {
        var encoded=new PlanViewEncoding(64);encoded.encode(Map.of("text","invented"));var bytes=new ByteArrayOutputStream();
        assertThrows(IllegalStateException.class,()->encoded.write(bytes,()->{throw new IllegalStateException("REVOKED");}));assertEquals(0,bytes.size());
    }
    @Test void revocationBetweenChunksNeverSendsRemainingBytes() throws Exception {
        var encoded=new PlanViewEncoding(20000);encoded.encode(Map.of("text","x".repeat(17000)));var bytes=new ByteArrayOutputStream();var checks=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->encoded.write(bytes,()->{if(checks.incrementAndGet()==2)throw new IllegalStateException("REVOKED");}));
        assertEquals(8192,bytes.size());assertTrue(bytes.size()<encoded.size());encoded.close();
    }
}
