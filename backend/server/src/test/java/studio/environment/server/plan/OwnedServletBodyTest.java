package studio.environment.server.plan;

import jakarta.servlet.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedServletBodyTest {
    static final class Stream extends ServletInputStream {
        ReadListener listener;
        boolean ready;
        int reads;
        @Override public boolean isFinished() { return false; }
        @Override public boolean isReady() { return ready; }
        @Override public void setReadListener(ReadListener listener) { this.listener=listener; }
        @Override public int read() { reads++; if(!ready) throw new AssertionError("READ_WITHOUT_READINESS"); return 'x'; }
    }
    @Test void quietBodyExpiresWithoutEverCallingBlockingRead() {
        var stream=new Stream();
        try(var body=new OwnedServletBody(stream,16_384,System.nanoTime()+20_000_000L,()->false)) {
            assertEquals(PlanBodyFailure.Code.BODY_DEADLINE,assertThrows(PlanBodyFailure.class,body::read).code());
            assertEquals(0,stream.reads);
        }
    }
    @Test void closedReaderIgnoresLateCallbackAndCannotReadAgain() throws Exception {
        var stream=new Stream(); stream.ready=true;
        var body=new OwnedServletBody(stream,16_384,System.nanoTime()+1_000_000_000L,()->false);
        body.close(); stream.listener.onDataAvailable();
        assertThrows(PlanBodyFailure.class,body::read); assertEquals(0,stream.reads);
    }
    @Test void cancelledReaderNeverTouchesReadyInput() {
        var stream=new Stream(); stream.ready=true;
        try(var body=new OwnedServletBody(stream,16_384,System.nanoTime()+1_000_000_000L,()->true)) {
            assertThrows(PlanBodyFailure.class,body::read); assertEquals(0,stream.reads);
        }
    }
}
