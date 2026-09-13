package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Controlled container scheduling, not an assertion about a particular Tomcat call stack. */
class OwnedServletBodyConcurrencyTest {
    static final class Input extends ServletInputStream {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final boolean holdReady;
        volatile boolean consumed;
        ReadListener listener;
        Input(boolean holdReady) { this.holdReady = holdReady; }
        void hold() {
            entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("CONTROL_TIMEOUT"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        @Override public boolean isFinished() { return consumed; }
        @Override public boolean isReady() { if (holdReady) hold(); return true; }
        @Override public void setReadListener(ReadListener value) { listener = value; }
        @Override public int read() { if (!holdReady) hold(); if (consumed) return -1; consumed = true; return 'q'; }
    }
    @Test void readinessCallbackDoesNotWaitForHeldReadyCallAndBytesRemainReadable() throws Exception {
        heldCallback(true, false);
    }
    @Test void errorCallbackDoesNotWaitForHeldReadCall() throws Exception {
        heldCallback(false, true);
    }
    private void heldCallback(boolean ready, boolean error) throws Exception {
        var input = new Input(ready);
        var body = new OwnedServletBody(input, 16, System.nanoTime() + 5_000_000_000L, () -> false);
        var reader = new FutureTask<Integer>(body::read);
        var callbackEntered = new CountDownLatch(1);
        var callback = new FutureTask<Void>(() -> {
            callbackEntered.countDown();
            if (error) input.listener.onError(new IllegalStateException("invented-error"));
            else input.listener.onDataAvailable();
            return null;
        });
        Thread rt = new Thread(reader), ct = new Thread(callback);
        boolean returned = false;
        try {
            rt.start(); assertTrue(input.entered.await(1, TimeUnit.SECONDS));
            ct.start(); assertTrue(callbackEntered.await(1, TimeUnit.SECONDS));
            try { callback.get(250, TimeUnit.MILLISECONDS); returned = true; }
            catch (TimeoutException expectedControlObservation) { }
        } finally {
            input.release.countDown(); rt.join(1500); ct.join(1500);
        }
        assertFalse(rt.isAlive()); assertFalse(ct.isAlive());
        if (!error) { assertEquals((int)'q', reader.get()); assertEquals(-1, body.read()); }
        else { assertNotNull(reader); assertThrows(PlanBodyFailure.class, body::read); }
        body.close();
        assertTrue(returned, "callback waited for application-owned monitor across Servlet call");
    }
    @Test void cancellationDeadlineAndCloseStillRefuseWithoutInputConsumption() {
        var input = new Input(false);
        var cancelled = new AtomicBoolean(true);
        var body = new OwnedServletBody(input, 16, System.nanoTime() + 5_000_000_000L, cancelled::get);
        assertEquals(PlanBodyFailure.Code.CANCELLED, assertThrows(PlanBodyFailure.class, body::read).code());
        cancelled.set(false); body.close();
        assertEquals(PlanBodyFailure.Code.MALFORMED_BODY, assertThrows(PlanBodyFailure.class, body::read).code());
        var expired = new OwnedServletBody(new Input(false), 16, System.nanoTime() - 1, () -> false);
        assertEquals(PlanBodyFailure.Code.BODY_DEADLINE, assertThrows(PlanBodyFailure.class, expired::read).code());
        assertFalse(input.consumed); expired.close();
    }
    @Test void closeReturnsWhileReadHeldButDoesNotFinishTheReader() throws Exception {
        var input = new Input(false);
        var body = new OwnedServletBody(input, 16, System.nanoTime() + 5_000_000_000L, () -> false);
        var read = new FutureTask<Integer>(body::read);
        var close = new FutureTask<Void>(() -> { body.close(); return null; });
        Thread rt = new Thread(read), ct = new Thread(close);
        boolean closed = false;
        try {
            rt.start(); assertTrue(input.entered.await(1, TimeUnit.SECONDS)); ct.start();
            try { close.get(250, TimeUnit.MILLISECONDS); closed = true; }
            catch (TimeoutException observed) { }
            assertFalse(read.isDone(), "close cannot terminate the held container call");
        } finally { input.release.countDown(); rt.join(1500); ct.join(1500); }
        assertFalse(rt.isAlive()); assertFalse(ct.isAlive()); assertTrue(closed);
        assertEquals((int)'q', read.get());
        assertThrows(PlanBodyFailure.class, body::read);
    }
    @Test void notificationsBeforeRegistrationPreserveBufferedBytesAndErrorState() throws Exception {
        var input = new Input(false); input.release.countDown();
        var body = new OwnedServletBody(input, 16, System.nanoTime() + 5_000_000_000L, () -> false);
        input.listener.onDataAvailable(); input.listener.onAllDataRead();
        int actual = assertDoesNotThrow(() -> { return body.read(); });
        assertEquals((int)'q', actual); assertEquals(-1, body.read()); body.close();
        var failed = new Input(false);
        var other = new OwnedServletBody(failed, 16, System.nanoTime() + 5_000_000_000L, () -> false);
        failed.listener.onError(new IllegalStateException("invented-not-retained"));
        assertEquals(PlanBodyFailure.Code.MALFORMED_BODY, assertThrows(PlanBodyFailure.class, other::read).code());
        assertFalse(failed.consumed); other.close();
    }
    @Test void waitingReaderObservesCancellationAndOriginalDeadline() throws Exception {
        for (boolean cancel : new boolean[]{true, false}) {
            var entered = new CountDownLatch(1);
            var flag = new AtomicBoolean();
            var input = new ServletInputStream() {
                public boolean isFinished() { return false; }
                public boolean isReady() { entered.countDown(); return false; }
                public void setReadListener(ReadListener value) { }
                public int read() { throw new AssertionError("NOT_READY_READ"); }
            };
            var body = new OwnedServletBody(input, 16,
                    System.nanoTime() + (cancel ? 5_000_000_000L : 80_000_000L), flag::get);
            var result = new FutureTask<PlanBodyFailure.Code>(() -> assertThrows(PlanBodyFailure.class, body::read).code());
            Thread worker = new Thread(result); worker.start();
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS)); if (cancel) flag.set(true);
                assertEquals(cancel ? PlanBodyFailure.Code.CANCELLED : PlanBodyFailure.Code.BODY_DEADLINE,
                        result.get(1, TimeUnit.SECONDS));
            } finally { body.close(); worker.join(1500); }
            assertFalse(worker.isAlive());
        }
    }
    @Test void interruptWakesWaitingReaderAndPreservesInterruptFlag() throws Exception {
        var input = new OwnedServletBodyTest.Stream();
        var body = new OwnedServletBody(input, 16, System.nanoTime() + 5_000_000_000L, () -> false);
        var result = new FutureTask<Boolean>(() -> {
            Thread.currentThread().interrupt();
            assertEquals(PlanBodyFailure.Code.MALFORMED_BODY, assertThrows(PlanBodyFailure.class, body::read).code());
            return Thread.currentThread().isInterrupted();
        });
        Thread worker = new Thread(result); worker.start();
        try { assertTrue(result.get(1, TimeUnit.SECONDS)); assertEquals(0, input.reads); }
        finally { body.close(); worker.join(1500); }
        assertFalse(worker.isAlive());
    }
}
