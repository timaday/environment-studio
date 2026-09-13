package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class OwnedServletOutputTest {
    @Test void containerCallbacksCannotWaitForTheActiveWriterLock()throws Exception {
        try(var callbacks=Executors.newSingleThreadExecutor()) {
            var sink=new ServletOutputStream(){
                WriteListener listener;
                public boolean isReady(){return true;}
                public void setWriteListener(WriteListener value){listener=value;}
                public void write(int value)throws IOException {
                    // Models a container retaining its output lock while invoking a callback.
                    var callback=callbacks.submit(()->{try{listener.onWritePossible();}catch(IOException e){throw new AssertionError(e);}});
                    try{callback.get(500,TimeUnit.MILLISECONDS);}
                    catch(Exception refusal){throw new IOException("CALLBACK_WAITED_FOR_WRITER");}
                }
            };
            try(var output=new OwnedServletOutput(sink,()->{},30_000_000_000L,()->0L)) {
                assertDoesNotThrow(()->output.write(1));
            }
        }
    }
    static final class Sink extends ServletOutputStream {
        volatile boolean ready=true,throwNext;WriteListener listener;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();int largest;boolean servletClosed;
        public boolean isReady(){return ready;}
        public void setWriteListener(WriteListener listener){assertNull(this.listener);this.listener=listener;}
        public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
        public void write(byte[] source,int offset,int length)throws IOException {
            assertNotNull(listener);assertTrue(ready,"A nonblocking sink must never be written while unready");
            if(throwNext){throwNext=false;throw new IOException("PRIVATE_OUTPUT_CANARY");}
            largest=Math.max(largest,length);bytes.write(source,offset,length);
        }
        public void close(){servletClosed=true;}
        void signal()throws IOException{listener.onWritePossible();}
    }
    @Test void writeFailureCannotResumeTheSameTransferOrDiscloseItsCause()throws Exception {
        var sink=new Sink();sink.throwNext=true;
        try(var output=new OwnedServletOutput(sink,()->{},30_000_000_000L,()->0L)) {
            var error=assertThrows(IOException.class,()->output.write(41));assertEquals("PLAN_TRANSFER_REFUSED",error.getMessage());assertNull(error.getCause());
            assertThrows(IOException.class,()->output.write(42));assertEquals(0,sink.bytes.size());
        }
    }
    @Test void readyOutputPreservesExactBytesInBoundedChunksAndDoesNotOwnServletClosure()throws Exception {
        var sink=new Sink();byte[] expected=new byte[25_000];for(int i=0;i<expected.length;i++)expected[i]=(byte)(i%251);
        var output=new OwnedServletOutput(sink,()->{},30_000_000_000L,()->0L);
        output.write(expected);output.flush();output.close();output.close();assertArrayEquals(expected,sink.bytes.toByteArray());assertEquals(8192,sink.largest);assertFalse(sink.servletClosed);assertThrows(IOException.class,()->output.write(1));
    }
    @Test void authorityIsCheckedWhileNoReadinessEventArrives()throws Exception {
        var sink=new Sink();sink.ready=false;var revoked=new AtomicBoolean();var checked=new CountDownLatch(1);var checks=new AtomicInteger();
        try(var output=new OwnedServletOutput(sink,()->{if(checks.incrementAndGet()>=2)checked.countDown();if(revoked.get())throw new IllegalStateException("PRIVATE_AUTHORITY_CANARY");},System.nanoTime()+30_000_000_000L,System::nanoTime);var executor=Executors.newSingleThreadExecutor()) {
            var task=executor.submit(()->{try{output.write(1);return false;}catch(IOException refusal){return refusal.getMessage().equals("PLAN_TRANSFER_REFUSED");}});
            assertTrue(checked.await(1,TimeUnit.SECONDS));revoked.set(true);assertTrue(task.get(1,TimeUnit.SECONDS));assertEquals(0,sink.bytes.size());
        }
    }
    @Test void progressAndRepeatedReadinessNeverRenewTheOriginalDeadline()throws Exception {
        var sink=new Sink();var clock=new AtomicLong();
        try(var output=new OwnedServletOutput(sink,()->{},30_000_000_000L,clock::get)) {
            output.write(1);clock.set(29_999_999_999L);sink.signal();output.write(2);clock.incrementAndGet();sink.signal();
            assertThrows(IOException.class,()->output.write(3));assertArrayEquals(new byte[]{1,2},sink.bytes.toByteArray());
        }
    }
    @Test void disconnectSignalRefusesWithoutPublishingMoreBytes()throws Exception {
        var sink=new Sink();
        try(var output=new OwnedServletOutput(sink,()->{},30_000_000_000L,()->0L)) {
            output.write(1);output.onError(new IOException("PRIVATE_OUTPUT_CANARY"));assertThrows(IOException.class,()->output.write(2));assertArrayEquals(new byte[]{1},sink.bytes.toByteArray());
        }
    }
    @Test void revocationAfterOneChunkCannotPublishTheRemainderOrResume()throws Exception {
        var sink=new Sink();
        try(var output=new OwnedServletOutput(sink,()->{if(sink.bytes.size()>=8192)throw new IllegalStateException("PRIVATE_AUTHORITY_CANARY");},30_000_000_000L,()->0L)) {
            assertThrows(IOException.class,()->output.write(new byte[16_384]));assertEquals(8192,sink.bytes.size());assertThrows(IOException.class,()->output.write(1));assertEquals(8192,sink.bytes.size());
        }
    }
}
