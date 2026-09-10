package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class IndependentOwnedBodyTest {
    static final class Bytes extends ServletInputStream {
        final byte[] bytes; int position; ReadListener listener;
        Bytes(String text) { bytes=text.getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
        public boolean isFinished() { return position==bytes.length; }
        public boolean isReady() { return true; }
        public void setReadListener(ReadListener value) { listener=value; }
        public int read() { return position==bytes.length?-1:bytes[position++]; }
        public int read(byte[] out,int offset,int length) {
            int count=Math.min(length,bytes.length-position);
            if(count==0)return -1;
            System.arraycopy(bytes,position,out,offset,count);position+=count;return count;
        }
    }
    @Test void alternatingSingleAndBulkReadsShareOneByteBudgetIncludingBufferedEof() throws Exception {
        for(String text:new String[]{"abcd","abcde"}) {
            var input=new Bytes(text);
            try(var body=new OwnedServletBody(input,4,System.nanoTime()+2_000_000_000L,()->false)) {
                input.listener.onAllDataRead();assertEquals('a',body.read());
                byte[] out=new byte[6];assertEquals(3,body.read(out,1,3));
                assertArrayEquals(new byte[]{0,'b','c','d',0,0},out);
                if(text.length()==4)assertEquals(-1,body.read(out));
                else assertEquals(PlanBodyFailure.Code.BODY_TOO_LARGE,assertThrows(PlanBodyFailure.class,()->body.read(out)).code());
            }
        }
    }
    @Test void allDataNotificationCanReturnWhileFinishedProbeIsHeldAndBulkReaderKeepsBufferedData() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var bytes=new Bytes("xy");
        var input=new ServletInputStream() {
            boolean probed;
            public void setReadListener(ReadListener value) { bytes.listener=value; }
            public boolean isReady() { return true; }
            public int read() { return bytes.read(); }
            public int read(byte[] out,int offset,int length) { return bytes.read(out,offset,length); }
            public boolean isFinished() {
                if(!probed) {probed=true;entered.countDown();try {if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("CONTROL_TIMEOUT");}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
                return bytes.isFinished();
            }
        };
        try(var body=new OwnedServletBody(input,2,System.nanoTime()+5_000_000_000L,()->false)) {
            byte[] out=new byte[2];var result=new FutureTask<Integer>(()->body.read(out));
            var callback=new FutureTask<Void>(()->{bytes.listener.onAllDataRead();return null;});
            Thread reader=new Thread(result), signal=new Thread(callback);boolean returned=false;
            try {
                reader.start();assertTrue(entered.await(1,TimeUnit.SECONDS));signal.start();
                try{callback.get(250,TimeUnit.MILLISECONDS);returned=true;}catch(TimeoutException observed){}
                assertFalse(result.isDone());
            } finally {release.countDown();reader.join(1500);signal.join(1500);}
            assertFalse(reader.isAlive());assertFalse(signal.isAlive());assertTrue(returned);
            assertEquals(2,result.get());assertArrayEquals(new byte[]{'x','y'},out);assertEquals(-1,body.read());
        }
    }
}
