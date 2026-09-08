package studio.environment.supervisor;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ProcessBoundaryTest {
    @TempDir Path working;
    static List<String> command(String testCase){return List.of("/usr/bin/python3",Path.of("src/test/resources/process-child.py").toAbsolutePath().toString(),testCase);}
    static long deadline(){return System.nanoTime()+TimeUnit.SECONDS.toNanos(2);}
    @Test void nativeOutputIsMergedAndEarlierErrorCannotBeSkipped() {
        var process=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("earlier-error"),Map.of(),working);try{
            var transcript=new Transcript(process);assertEquals("UNEXPECTED_TRANSCRIPT",assertThrows(Refusal.class,()->transcript.exact("READY\n",deadline())).code);assertThrows(Refusal.class,()->transcript.exact("READY\n",deadline()));
        }finally{closeRefusedProcess(process);}
    }
    @Test void quietOrTricklingOutputDoesNotExtendDeadline(){
        var process=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("trickle"),Map.of(),working);try{
            long start=System.nanoTime();assertThrows(Refusal.class,()->new Transcript(process).exact("READY\n",start+TimeUnit.MILLISECONDS.toNanos(150)));assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(2));
        }finally{closeRefusedProcess(process);}
    }
    @Test void outputOverflowFailsClosedAndNothingIsRendered(){var process=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("overflow"),Map.of(),working);try{assertThrows(Refusal.class,()->new Transcript(process).exact("READY\n",deadline()));assertFalse(process.toString().contains("xxxx"));}finally{closeRefusedProcess(process);}}
    @Test void credentialsAreBoundedDuringReadAndWipedAtClose()throws Exception{
        var secret=BoundedSecret.read(new ByteArrayInputStream("invented-λ😀\n".getBytes(StandardCharsets.UTF_8)));assertArrayEquals("invented-λ😀\n".getBytes(StandardCharsets.UTF_8),secret.line());assertFalse(secret.toString().contains("invented"));secret.close();assertThrows(Refusal.class,secret::line);
        for(byte[] invalid:List.of("a\rb\n".getBytes(),new byte[]{(byte)0xc0,(byte)0x80,10},("x".repeat(1025)+"\n").getBytes(),("😀".repeat(1025)+"\n").getBytes(StandardCharsets.UTF_8)))assertThrows(Refusal.class,()->BoundedSecret.read(new ByteArrayInputStream(invalid)));
        var exact=BoundedSecret.read(new ByteArrayInputStream(("😀".repeat(1024)+"\n").getBytes(StandardCharsets.UTF_8)));assertEquals(4097,exact.line().length);exact.close();
    }
    @Test void completeOutputBudgetIsEnforcedWhileReading(){var process=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("overflow"),Map.of(),working);try{long end=deadline();var refusal=assertThrows(Refusal.class,()->{for(int i=0;i<1_100_001;i++)process.read(end);});assertEquals("TRANSCRIPT_LIMIT",refusal.code);}finally{closeRefusedProcess(process);}}
    @Test void childHasNewSessionNoControllingTerminalAndOnlyFixedEnvironment(){try(var process=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("environment"),Map.of("LANG","C.UTF-8","LC_ALL","C.UTF-8"),working)){var transcript=new Transcript(process);transcript.line("CLEAN",deadline());transcript.eof(deadline());assertEquals(0,process.exit(deadline()));}}
    @Test void blockedWriteRejectsConcurrentSecretWithoutQueueingAndRetainsWorkerForCleanup()throws Exception{
        var child=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("blocked-input"),Map.of(),working);var finished=new java.util.concurrent.CountDownLatch(1);Thread first=null;
        try{new Transcript(child).line("BLOCKED",deadline());first=Thread.ofPlatform().daemon().start(()->{try{child.write(new byte[8*1024*1024],System.nanoTime()+TimeUnit.SECONDS.toNanos(3));}catch(Throwable expected){}finally{finished.countDown();}});
            long wait=deadline();while(first.getState()!=Thread.State.TIMED_WAITING&&System.nanoTime()<wait)Thread.sleep(1);assertEquals(Thread.State.TIMED_WAITING,first.getState());
            assertEquals("CONCURRENT_CLIENT_INPUT",assertThrows(Refusal.class,()->child.write("second-independent-secret\n".getBytes(StandardCharsets.UTF_8),System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(150))).code);
        }finally{child.close();assertTrue(finished.await(2,TimeUnit.SECONDS));if(first!=null)first.join(1000);}
    }
    @Test void blockedWriteDeadlineKeepsOwnedWorkerUntilCleanup()throws Exception{
        var child=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("blocked-input"),Map.of(),working);
        try{new Transcript(child).line("BLOCKED",deadline());assertEquals("CLIENT_INPUT_FAILED",assertThrows(Refusal.class,()->child.write(new byte[8*1024*1024],System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(150))).code);}
        finally{child.close();}
        var field=OwnedNativeProcess.class.getDeclaredField("writer");field.setAccessible(true);var worker=(Thread)field.get(child);assertNotNull(worker);assertFalse(worker.isAlive());
    }
    @Test void trackingRefusalStillCleansPreviouslyOwnedChildAndStreams()throws Exception{
        var child=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("blocked-input"),Map.of(),working);
        new Transcript(child).line("BLOCKED",deadline());
        var processField=OwnedNativeProcess.class.getDeclaredField("process");processField.setAccessible(true);var wrapper=(Process)processField.get(child);
        var handle=wrapper.descendants().findFirst().orElseThrow();var start=handle.info().startInstant().orElseThrow();
        // Model a full ownership ledger while preserving the real already-owned child.
        var ledger=new LinkedHashMap<ProcessHandle,java.time.Instant>(){@Override public int size(){return 16;}@Override public boolean containsKey(Object key){return false;}};ledger.put(handle,start);
        var field=OwnedNativeProcess.class.getDeclaredField("descendants");field.setAccessible(true);field.set(child,ledger);
        try{assertEquals("PROCESS_CLEANUP_INCONCLUSIVE",assertThrows(Refusal.class,child::close).code);
            assertFalse(wrapper.isAlive());assertFalse(handle.isAlive());
            var readerField=OwnedNativeProcess.class.getDeclaredField("reader");readerField.setAccessible(true);assertFalse(((Thread)readerField.get(child)).isAlive());
        }finally{if(handle.isAlive())handle.destroyForcibly();if(wrapper.isAlive())wrapper.destroyForcibly();}
    }
    private OwnedNativeProcess failedOutputChild()throws Exception{
        var child=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command("environment"),Map.of("LANG","C.UTF-8","LC_ALL","C.UTF-8"),working,stream->new FilterInputStream(stream){
            @Override public int read()throws IOException{int value=super.read();if(value==-1)throw new IOException("independent-output-failure-canary");return value;}
        });
        var field=OwnedNativeProcess.class.getDeclaredField("reader");field.setAccessible(true);var reader=(Thread)field.get(child);reader.join(2000);assertFalse(reader.isAlive());return child;
    }
    @Test void actualChildReadFailureCannotBecomeSuccessfulEofExitOrCleanup()throws Exception{
        var child=failedOutputChild();
        try{assertAll(
            ()->assertEquals("CLIENT_OUTPUT_FAILED",assertThrows(Refusal.class,()->child.read(deadline())).code),
            ()->assertEquals("CLIENT_OUTPUT_FAILED",assertThrows(Refusal.class,()->child.exit(deadline())).code),
            ()->assertEquals("PROCESS_CLEANUP_INCONCLUSIVE",assertThrows(Refusal.class,child::close).code));
        }finally{try{child.close();}catch(Refusal expected){}}
    }
    @Test void failedOutputResourceClosurePreservesAcknowledgedOutcomeAndPrecommitRefusal()throws Exception{
        for(boolean acknowledged:List.of(false,true)){
            var child=failedOutputChild();var wire=new SessionEngine.Wire(){
                public void authenticate(){if(!acknowledged)child.read(deadline());}public void bootstrap(){}public void program(){}public void readiness(){}public void commit(){}public void acknowledgement(){}public void cleanExit(){}
                public SessionEngine.Cleanup rollbackAndCleanup(){child.close();return SessionEngine.Cleanup.COMPLETE;}public void terminate(){child.terminate();}public void close(){child.close();}
            };
            assertEquals(new SessionEngine.Outcome(acknowledged?SessionEngine.Status.APPLIED:SessionEngine.Status.NOT_APPLIED,SessionEngine.Cleanup.INCONCLUSIVE),new SessionEngine().execute(wire));
        }
    }
    private static void closeRefusedProcess(OwnedNativeProcess child){
        try{child.close();}catch(Refusal refusal){assertEquals("PROCESS_CLEANUP_INCONCLUSIVE",refusal.code);}
        try{
            var process=OwnedNativeProcess.class.getDeclaredField("process");process.setAccessible(true);assertFalse(((Process)process.get(child)).isAlive());
            var ledger=OwnedNativeProcess.class.getDeclaredField("descendants");ledger.setAccessible(true);for(Object handle:((Map<?,?>)ledger.get(child)).keySet())assertFalse(((ProcessHandle)handle).isAlive());
            for(String name:List.of("reader","writer")){var field=OwnedNativeProcess.class.getDeclaredField(name);field.setAccessible(true);var worker=(Thread)field.get(child);if(worker!=null)assertFalse(worker.isAlive());}
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
}
