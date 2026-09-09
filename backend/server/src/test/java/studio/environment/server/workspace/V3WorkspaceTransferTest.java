package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.workspace.WorkspaceRefusal;

class V3WorkspaceTransferTest {
    static class Sink extends ServletOutputStream {
        volatile boolean ready=true;WriteListener listener;int calls,largest;boolean servletClosed;
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        public boolean isReady(){return ready;}
        public void setWriteListener(WriteListener value){assertNull(listener);listener=value;}
        public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
        public void write(byte[] input,int offset,int length)throws IOException{assertTrue(ready);calls++;largest=Math.max(largest,length);bytes.write(input,offset,length);}
        public void close(){servletClosed=true;}
    }
    @Test void outputAndEncodingRejectRenewedOrUnboundedClocks(){
        assertThrows(WorkspaceRefusal.class,()->new V3WorkspaceOutput(new Sink(),()->{},System.nanoTime()+60_000_000_000L));
        assertThrows(WorkspaceRefusal.class,()->new V3WorkspaceEncoding(()->{},System.nanoTime()+60_000_000_000L));
    }
    @Test void encodingIsExactBoundedAndWipesPreviouslyAllocatedChunks()throws Exception {
        var sink=new Sink();byte[] expected=new byte[25000];for(int i=0;i<expected.length;i++)expected[i]=(byte)(i%251);
        var encoded=new V3WorkspaceEncoding(()->{},System.nanoTime()+30_000_000_000L);encoded.write(expected);
        var field=V3WorkspaceEncoding.class.getDeclaredField("chunks");field.setAccessible(true);
        @SuppressWarnings("unchecked") var held=new ArrayList<>((List<byte[]>)field.get(encoded));
        try(var output=new V3WorkspaceOutput(sink,()->{},System.nanoTime()+30_000_000_000L)){encoded.transfer(output);}
        encoded.close();encoded.close();assertArrayEquals(expected,sink.bytes.toByteArray());assertEquals(8192,sink.largest);assertFalse(sink.servletClosed);
        for(byte[] chunk:held)assertArrayEquals(new byte[chunk.length],chunk);
        assertThrows(WorkspaceRefusal.class,()->encoded.write(1));
    }
    @Test void encodingCapacityIsExactAndOneOverRefusesBeforeAllocating()throws Exception {
        try(var bytes=new V3WorkspaceEncoding(()->{},System.nanoTime()+30_000_000_000L)){
            byte[] block=new byte[8192];for(int i=0;i<1024;i++)bytes.write(block);
            assertEquals(8388608,bytes.size());assertEquals(WorkspaceRefusal.Code.TOO_LARGE,assertThrows(WorkspaceRefusal.class,()->bytes.write(1)).code());
            assertEquals(8388608,bytes.size());
        }
    }
    @Test void blockedReadinessObservesRevocationAndNoFurtherBytes()throws Exception {
        var sink=new Sink();sink.ready=false;var revoked=new AtomicBoolean();var waiting=new CountDownLatch(1);
        try(var output=new V3WorkspaceOutput(sink,()->{waiting.countDown();if(revoked.get())throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);},System.nanoTime()+30_000_000_000L);var executor=Executors.newSingleThreadExecutor()){
            var task=executor.submit(()->assertThrows(WorkspaceRefusal.class,()->output.write(new byte[]{1},1)).code());
            assertTrue(waiting.await(1,TimeUnit.SECONDS));revoked.set(true);assertEquals(WorkspaceRefusal.Code.FORBIDDEN,task.get(1,TimeUnit.SECONDS));assertEquals(0,sink.calls);
        }
    }
    @Test void failureAfterFirstChunkCannotAppendTheRemainder()throws Exception {
        var sink=new Sink();Runnable check=()->{if(sink.calls>0)throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);};
        try(var bytes=new V3WorkspaceEncoding(check,System.nanoTime()+30_000_000_000L);var output=new V3WorkspaceOutput(sink,check,System.nanoTime()+30_000_000_000L)){
            bytes.write(new byte[20000]);assertThrows(WorkspaceRefusal.class,()->bytes.transfer(output));assertEquals(8192,sink.bytes.size());assertEquals(1,sink.calls);
        }
    }
    @Test void outputCallbacksNeverWaitForTheWriter()throws Exception {
        try(var executor=Executors.newSingleThreadExecutor()){
            var sink=new Sink(){@Override public void write(byte[] value,int offset,int length)throws IOException{
                try{executor.submit(()->{try{listener.onWritePossible();}catch(IOException failure){throw new AssertionError();}}).get(500,TimeUnit.MILLISECONDS);}catch(Exception failure){throw new IOException("MOCK_INVERSION");}
                super.write(value,offset,length);
            }};
            try(var output=new V3WorkspaceOutput(sink,()->{},System.nanoTime()+30_000_000_000L)){assertDoesNotThrow(()->output.write(new byte[]{1},1));}
        }
    }
    @Test void bodyNetworkEofDoesNotHideContainerBufferedBytesAndOwnedArraysAreWiped()throws Exception {
        byte[] expected="mock-é".getBytes(java.nio.charset.StandardCharsets.UTF_8);var input=new ByteArrayInputStream(expected);
        var stream=new ServletInputStream(){public boolean isReady(){return true;}public boolean isFinished(){return input.available()==0;}public void setReadListener(ReadListener listener){try{listener.onAllDataRead();}catch(IOException e){throw new AssertionError();}}public int read(){return input.read();}};
        var body=new V3WorkspaceBody(stream,()->{},System.nanoTime()+30_000_000_000L);byte[] result=body.readNBytes(100);assertArrayEquals(expected,result);body.close();assertArrayEquals(new byte[result.length],result);
    }
    @Test void errorCauseIsNeverExposedAndTransferCannotResume()throws Exception {
        var sink=new Sink(){boolean first=true;@Override public void write(byte[] b,int o,int n)throws IOException{if(first){first=false;throw new IOException("MOCK_PRIVATE_CAUSE");}super.write(b,o,n);}};
        try(var output=new V3WorkspaceOutput(sink,()->{},System.nanoTime()+30_000_000_000L)){
            var failure=assertThrows(WorkspaceRefusal.class,()->output.write(new byte[]{1},1));assertEquals("WORKSPACE_UNAVAILABLE",failure.getMessage());assertNull(failure.getCause());
            assertThrows(WorkspaceRefusal.class,()->output.write(new byte[]{2},1));assertEquals(0,sink.calls);
        }
    }
    @Test void revocationDuringOneAdmittedChunkIsObservedBeforeReturningSuccess(){
        var revoked=new AtomicBoolean();var sink=new Sink(){@Override public void write(byte[] bytes,int offset,int length)throws IOException{super.write(bytes,offset,length);revoked.set(true);}};
        try(var output=new V3WorkspaceOutput(sink,()->{if(revoked.get())throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);},System.nanoTime()+30_000_000_000L)){
            assertEquals(WorkspaceRefusal.Code.FORBIDDEN,assertThrows(WorkspaceRefusal.class,()->output.write(new byte[]{1},1)).code());assertEquals(1,sink.bytes.size());
        }
    }

    @Test void stalledBodyUsesItsOriginalDeadlineWithoutAReadinessCallback()throws Exception {
        var reads=new AtomicInteger();var stream=new ServletInputStream(){public boolean isReady(){return false;}public boolean isFinished(){return false;}public void setReadListener(ReadListener listener){}public int read(){reads.incrementAndGet();return 1;}};
        try(var body=new V3WorkspaceBody(stream,()->{},System.nanoTime()+40_000_000L)){
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->assertEquals(WorkspaceRefusal.Code.UNAVAILABLE,assertThrows(WorkspaceRefusal.class,body::read).code()));assertEquals(0,reads.get());
        }
    }

}
