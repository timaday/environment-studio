package studio.environment.supervisor;
import java.io.*;import java.nio.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class TerminalConsoleTest {
 static byte[] snapshot(){var b=ByteBuffer.allocate(112);b.put("LNXAMD64".getBytes(java.nio.charset.StandardCharsets.US_ASCII));b.putInt(1).putLong(1).putLong(2).putLong(3).putInt(10).putInt(10).putInt(11).putInt(60);return b.array();}
 static byte[] frame(int kind,byte[] data){return ByteBuffer.allocate(13+data.length).put("ESTTY001".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).put((byte)kind).putInt(data.length).put(data).array();}
 static byte[] joined(byte[]... parts)throws IOException{var out=new ByteArrayOutputStream();for(var part:parts)out.write(part);return out.toByteArray();}
 static final class MockProcess extends Process {
  final InputStream input;final ByteArrayOutputStream commands=new ByteArrayOutputStream();volatile boolean alive=true;
  MockProcess(InputStream input){this.input=input;}MockProcess(byte[] input){this(new ByteArrayInputStream(input));}
  public OutputStream getOutputStream(){return commands;}public InputStream getInputStream(){return input;}public InputStream getErrorStream(){return InputStream.nullInputStream();}
  public int waitFor(){alive=false;return 0;}public boolean waitFor(long timeout,TimeUnit unit){alive=false;return true;}public int exitValue(){return 0;}public boolean isAlive(){return alive;}
  public void destroy(){alive=false;try{input.close();}catch(IOException ignored){}}public Process destroyForcibly(){destroy();return this;}
 }
 @Test void acceptsExactBoundedFramesAndRequiresRestorationBeforeReturningCredential()throws Exception {
  var process=new MockProcess(joined(frame(1,snapshot()),frame(2,"mock_reader".getBytes()),frame(3,"mock-secret".getBytes()),frame(4,new byte[0])));
  var console=new TerminalConsole(()->process,TimeUnit.SECONDS.toNanos(1),TimeUnit.SECONDS.toNanos(1));
  try(var credentials=assertDoesNotThrow(()->console.read(true))){assertEquals("mock_reader",credentials.username());byte[] value=credentials.password().line();assertArrayEquals("mock-secret\n".getBytes(),value);Arrays.fill(value,(byte)0);}
  assertArrayEquals(new byte[]{1,2},process.commands.toByteArray());assertEquals(SessionEngine.Cleanup.COMPLETE,console.restore());
 }
 @Test void truncatedSnapshotNeverArmsAndCleanupRemainsBounded() {
  var process=new MockProcess(frame(1,Arrays.copyOf(snapshot(),50)));var console=new TerminalConsole(()->process,TimeUnit.SECONDS.toNanos(1),TimeUnit.SECONDS.toNanos(1));
  assertThrows(Refusal.class,()->console.read(true));assertArrayEquals(new byte[]{1},process.commands.toByteArray());assertTimeout(java.time.Duration.ofSeconds(2),console::restore);
 }
 @Test void unboundedPasswordLengthRefusesBeforePayloadAllocation()throws Exception {
  var bad=ByteBuffer.allocate(13).put("ESTTY001".getBytes()).put((byte)3).putInt(Integer.MAX_VALUE).array();
  var process=new MockProcess(joined(frame(1,snapshot()),frame(2,"mock_reader".getBytes()),bad));
  var fallback=new MockProcess(frame(4,new byte[0]));var starts=new java.util.concurrent.atomic.AtomicInteger();
  var console=new TerminalConsole(()->starts.getAndIncrement()==0?process:fallback,TimeUnit.SECONDS.toNanos(1),TimeUnit.SECONDS.toNanos(1));
  assertThrows(Refusal.class,()->console.read(true));assertEquals(SessionEngine.Cleanup.COMPLETE,console.restore());
  assertEquals(2,starts.get());assertArrayEquals(joined(new byte[]{5},ByteBuffer.allocate(4).putInt(112).array(),snapshot()),fallback.commands.toByteArray());
 }
 @Test void blockedCaptureIsBoundedWithoutArming() {
  var input=new InputStream(){boolean closed;public synchronized int read()throws IOException{while(!closed)try{wait();}catch(InterruptedException interrupted){throw new IOException();}return -1;}public synchronized void close(){closed=true;notifyAll();}};
  var process=new MockProcess(input);var console=new TerminalConsole(()->process,TimeUnit.MILLISECONDS.toNanos(50),TimeUnit.MILLISECONDS.toNanos(150));
  assertTimeout(java.time.Duration.ofSeconds(1),()->assertThrows(Refusal.class,()->console.read(true)));assertArrayEquals(new byte[]{1},process.commands.toByteArray());assertFalse(process.isAlive());
 }
 @Test void fallbackDeadlineStopsOwnedFallbackAndNeverRenewsCleanup()throws Exception {
  var first=new MockProcess(joined(frame(1,snapshot()),frame(2,"mock_reader".getBytes())));
  var blocked=new InputStream(){boolean closed;public synchronized int read()throws IOException{while(!closed)try{wait();}catch(InterruptedException ignored){}return -1;}public synchronized void close(){closed=true;notifyAll();}};
  var fallback=new MockProcess(blocked);var starts=new java.util.concurrent.atomic.AtomicInteger();
  var console=new TerminalConsole(()->starts.getAndIncrement()==0?first:fallback,TimeUnit.MILLISECONDS.toNanos(200),TimeUnit.MILLISECONDS.toNanos(100));
  assertTimeout(java.time.Duration.ofSeconds(1),()->assertThrows(Refusal.class,()->console.read(true)));
  try {
   assertEquals(SessionEngine.Cleanup.INCONCLUSIVE,console.restore());
   long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(200);while(fallback.isAlive()&&System.nanoTime()<deadline)Thread.sleep(2);
   assertFalse(fallback.isAlive(),"fallback process survived the parent restoration deadline");assertEquals(2,starts.get());
  }finally{fallback.destroyForcibly();}
 }
 @Test void directFrameBuffersAreWipedAfterCredentialTransfer()throws Exception {
  var buffers=Collections.newSetFromMap(new IdentityHashMap<byte[],Boolean>());
  var input=new ByteArrayInputStream(joined(frame(1,snapshot()),frame(2,"mock_reader".getBytes()),frame(3,"mock-secret".getBytes()),frame(4,new byte[0]))) {
   @Override public synchronized int read(byte[] bytes,int offset,int length){buffers.add(bytes);return super.read(bytes,offset,length);}
  };
  var process=new MockProcess(input);var console=new TerminalConsole(()->process,TimeUnit.SECONDS.toNanos(1),TimeUnit.SECONDS.toNanos(1));
  try(var credentials=console.read(true)){assertFalse(buffers.isEmpty());for(byte[] bytes:buffers)assertArrayEquals(new byte[bytes.length],bytes,"owned direct input buffer retained content");}
 }
}
