package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanRefusal;

class IndependentPlanEncodingTest {
    @Test void escapingAndMultibyteTextUseExactEncodedLimitAndLegacyBytes() throws Exception {
        String value="\\\"\n\té😀".repeat(2340)+"aaaaaa";
        byte[] expected=("\""+"\\\\\\\"\\n\\té😀".repeat(2340)+"aaaaaa\"").getBytes(StandardCharsets.UTF_8);
        assertEquals(32768,expected.length);
        for(boolean legacy:new boolean[]{false,true}) {
            try(var encoding=legacy?new PlanViewEncoding(32768):new PlanViewEncoding(32768,()->{})) {
                encoding.encode(value);var output=new ByteArrayOutputStream();encoding.write(output,()->{});
                assertArrayEquals(expected,output.toByteArray());
                var retained=chunks(encoding);assertFalse(retained.isEmpty());encoding.close();
                for(var bytes:retained)assertArrayEquals(new byte[bytes.length],bytes);
            }
        }
    }
    @SuppressWarnings("unchecked")
    static List<byte[]> chunks(PlanViewEncoding encoding) throws Exception {
        var field=PlanViewEncoding.class.getDeclaredField("chunks");field.setAccessible(true);
        return List.copyOf((List<byte[]>)field.get(encoding));
    }
    @Test void refusalAtSecondAllocationWipesFirstChunkAndCannotAffectAnotherOwner() throws Exception {
        var selected=new AtomicReference<PlanViewEncoding>();var retained=new ArrayList<byte[]>();
        Runnable guard=()->{
            try {
                var chunks=chunks(selected.get());
                if(!chunks.isEmpty()) {retained.addAll(chunks);throw new PlanRefusal(PlanRefusal.Code.SESSION_REQUIRED);}
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            catch(Exception e){if(e instanceof RuntimeException runtime)throw runtime;throw new AssertionError(e);}
        };
        try(var refused=new PlanViewEncoding(32768,guard);var independent=new PlanViewEncoding(32768,()->{})) {
            selected.set(refused);
            assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->refused.encode("x".repeat(20000))).code());
            assertFalse(retained.isEmpty());for(var bytes:retained)assertArrayEquals(new byte[bytes.length],bytes);
            assertTrue(chunks(refused).isEmpty());var output=new ByteArrayOutputStream();
            assertThrows(IllegalStateException.class,()->refused.write(output,()->{}));assertEquals(0,output.size());
            independent.encode("unrelated");independent.write(output,()->{});
            assertArrayEquals("\"unrelated\"".getBytes(StandardCharsets.US_ASCII),output.toByteArray());
        }
    }
}
