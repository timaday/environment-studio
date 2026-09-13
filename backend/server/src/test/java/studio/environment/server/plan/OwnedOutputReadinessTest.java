package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.io.IOException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class OwnedOutputReadinessTest {
    static final class Probe extends ServletOutputStream {
        final Runnable duringReadiness;int writes,flushes;
        Probe(Runnable duringReadiness){this.duringReadiness=duringReadiness;}
        public boolean isReady(){duringReadiness.run();return true;}
        public void setWriteListener(WriteListener ignored){}
        public void write(int value){writes++;}
        public void write(byte[] bytes,int offset,int length){writes++;}
        public void flush(){flushes++;}
    }
    @Test void revocationDuringReadinessCannotStartEitherWriteOrFlush()throws Exception {
        for(boolean flush:new boolean[]{false,true}) {
            var revoked=new AtomicBoolean();var sink=new Probe(()->revoked.set(true));
            try(var writer=new OwnedServletOutput(sink,()->{if(revoked.get())throw new IllegalStateException("invented-revocation");},30_000_000_000L,()->0L)) {
                var refused=assertThrows(IOException.class,()->{if(flush)writer.flush();else writer.write(new byte[]{41,42});});
                assertEquals("PLAN_TRANSFER_REFUSED",refused.getMessage());assertNull(refused.getCause());
                assertEquals(0,sink.writes);assertEquals(0,sink.flushes);
            }
        }
    }
    @Test void readinessCannotSpendTheRemainingDeadlineAndThenStartWriteOrFlush()throws Exception {
        for(boolean flush:new boolean[]{true,false}) {
            var clock=new AtomicLong();var sink=new Probe(()->clock.set(30_000_000_000L));
            try(var writer=new OwnedServletOutput(sink,()->{},30_000_000_000L,clock::get)) {
                assertThrows(IOException.class,()->{if(flush)writer.flush();else writer.write(41);});
                assertEquals(0,sink.flushes);assertEquals(0,sink.writes);
            }
        }
    }
}
