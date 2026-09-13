package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import studio.environment.core.plan.*;
import studio.environment.server.session.HostedSessions;

class V3PlanTransportTest {
    static final class Output extends ServletOutputStream {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        volatile boolean ready=true;final CountDownLatch checked=new CountDownLatch(1);
        WriteListener listener;final AtomicInteger flushes=new AtomicInteger();
        public boolean isReady(){checked.countDown();return ready;}
        public void setWriteListener(WriteListener listener){this.listener=listener;}
        public void write(int value){bytes.write(value);}
        public void write(byte[] bytes,int offset,int length){this.bytes.write(bytes,offset,length);}
        public void flush(){flushes.incrementAndGet();}
    }
    static final class Context {
        final CountDownLatch complete=new CountDownLatch(1);final List<Long> timeouts=new CopyOnWriteArrayList<>();
        final AtomicInteger calls=new AtomicInteger();volatile AsyncListener listener;Runnable beforeComplete=()->{};boolean rejectListener;
        final AsyncContext value=(AsyncContext)Proxy.newProxyInstance(AsyncContext.class.getClassLoader(),new Class<?>[]{AsyncContext.class},(proxy,method,args)->{
            return switch(method.getName()) {
                case "setTimeout" -> {timeouts.add((Long)args[0]);yield null;}
                case "addListener" -> {if(rejectListener)throw new IllegalStateException("invented-listener-refusal");listener=(AsyncListener)args[0];yield null;}
                case "complete" -> {calls.incrementAndGet();beforeComplete.run();complete.countDown();yield null;}
                case "toString" -> "InventedTransferContext";
                default -> throw new AssertionError("UNEXPECTED_CONTEXT_ACCESS");
            };
        });
    }
    static MockHttpServletResponse response(Output output){return new MockHttpServletResponse(){@Override public ServletOutputStream getOutputStream(){return output;}};}
    static MockHttpServletRequest request(Context context) {return new MockHttpServletRequest(){@Override public AsyncContext startAsync(){return context.value;}};}
    static MockHttpServletRequest bodyRequest(Context context,String text) {
        var bytes=new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        var input=new ServletInputStream(){
            public boolean isFinished(){return bytes.available()==0;}
            public boolean isReady(){return true;}
            public void setReadListener(ReadListener listener){}
            public int read(){return bytes.read();}
            public int read(byte[] target,int offset,int length){return bytes.read(target,offset,length);}
        };
        var request=new MockHttpServletRequest(){
            @Override public AsyncContext startAsync(){return context.value;}
            @Override public ServletInputStream getInputStream(){return input;}
        };
        request.setContentType("application/json");return request;
    }
    static void settled(V3PlanTransfers transfers,studio.environment.core.session.SessionLedger.Lease lease)throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while(transfers.awaitingWork(lease)&&System.nanoTime()<deadline)Thread.sleep(5);
        assertFalse(transfers.awaitingWork(lease));
    }
    @Test void preTransferRefusalUsesOnlyClosedHeadersAndNeverObtainsOutput() {
        var response=new MockHttpServletResponse(){
            @Override public ServletOutputStream getOutputStream(){throw new AssertionError("EARLY_OUTPUT_ACQUISITION");}
            @Override public PrintWriter getWriter(){throw new AssertionError("EARLY_WRITER_ACQUISITION");}
        };
        V3PlanTransport.refuseBeforeTransfer(response,new PlanRefusal(PlanRefusal.Code.CAPACITY));
        assertEquals(429,response.getStatus());assertEquals("CAPACITY",response.getHeader("X-Environment-Studio-Code"));
        assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals("0",response.getHeader("Content-Length"));assertEquals(0,response.getContentAsByteArray().length);
        V3PlanTransport.refuseBeforeTransfer(response,new IllegalArgumentException("invented-input-must-not-escape"));
        assertEquals(500,response.getStatus());assertEquals("PLAN_INTERNAL_REFUSAL",response.getHeader("X-Environment-Studio-Code"));
        assertFalse(response.getHeaderNames().stream().anyMatch(name->String.valueOf(response.getHeader(name)).contains("must-not-escape")));
    }
    @Test void actualClosedReplyUsesOwnedWorkerAndReleasesItsExactRecordAfterCompletion() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);var transfers=new V3PlanTransfers();
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());var record=transfers.admitMetadata(f.lease);
        var context=new Context();var output=new Output();var response=response(output);
        try {
            new V3PlanTransport(f.service,sessions).read(f.lease,request(context),response,record,201,()->new V3PlanReply.Acknowledgement(ack));
            assertTrue(context.complete.await(3,TimeUnit.SECONDS));settled(transfers,f.lease);
            assertEquals(201,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
            assertEquals("{\"planId\":\""+ack.planId()+"\",\"revision\":\"1\"}",output.bytes.toString(StandardCharsets.UTF_8));
            assertEquals(output.bytes.size(),response.getContentLength());assertEquals(1,output.flushes.get());
            assertEquals(List.of(30_000L,0L),context.timeouts);assertNotNull(context.listener);assertEquals(1,context.calls.get());
        } finally {record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
    }
    @Test void stalledOutputExpiresOnOriginalBudgetWithoutStartingAnErrorBudget() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);var transfers=new V3PlanTransfers();
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());var record=transfers.admitMetadata(f.lease);
        var clock=new AtomicLong();var context=new Context();var output=new Output();output.ready=false;
        try {
            new V3PlanTransport(f.service,sessions,clock::get).read(f.lease,request(context),response(output),record,200,()->new V3PlanReply.Acknowledgement(ack));
            assertTrue(output.checked.await(2,TimeUnit.SECONDS));clock.set(31_000_000_000L);
            assertTrue(context.complete.await(2,TimeUnit.SECONDS));settled(transfers,f.lease);
            assertEquals(0,output.bytes.size());assertEquals(0,output.flushes.get());assertEquals(1,context.calls.get());
        } finally {clock.set(61_000_000_000L);record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
    }
    @Test void revocationDuringOutputAcquisitionExposesNoCapturedPlanBytes() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();f.create(true);var reply=new V3PlanReply.Summary(f.service.viewV3(f.lease,Optional.empty()));
        var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());var record=transfers.admitMetadata(f.lease);
        var context=new Context();var output=new Output();
        var response=new MockHttpServletResponse(){@Override public ServletOutputStream getOutputStream(){f.ledger.close(f.lease.id());return output;}};
        try {
            new V3PlanTransport(f.service,sessions).read(f.lease,request(context),response,record,200,()->reply);
            assertTrue(context.complete.await(2,TimeUnit.SECONDS));settled(transfers,f.lease);
            assertEquals(0,output.bytes.size());assertEquals(0,output.flushes.get());assertNull(output.listener);
        } finally {record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);f.service.invalidate(f.lease);}
    }
    @Test void bodyErrorIsClosedAndOriginalResourcesCloseBeforeCompletion() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitCredentials(f.lease);var context=new Context();var output=new Output();var response=response(output);
        var closed=new AtomicBoolean();var captured=new AtomicReference<OwnedServletBody>();
        var request=bodyRequest(context,"{}");
        context.beforeComplete=()->assertTrue(closed.get(),"Original application resource closes before completion");
        try {
            new V3PlanTransport(f.service,sessions).body(f.lease,request,response,record,200,()->closed.set(true),()->false,body->{
                captured.set(body);new PlanMetadataReader().empty(body);throw new IllegalArgumentException("invented-cause-must-not-escape");
            });
            assertTrue(context.complete.await(2,TimeUnit.SECONDS));settled(transfers,f.lease);
            assertEquals(500,response.getStatus());assertEquals("{\"code\":\"PLAN_INTERNAL_REFUSAL\"}",output.bytes.toString(StandardCharsets.UTF_8));
            assertNotNull(captured.get());assertThrows(PlanBodyFailure.class,()->captured.get().read());assertTrue(closed.get());
        } finally {record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
    }
    @Test void invalidContentTypeBeforeAsyncClosesOnlyItsAdmissionWithoutRetainingUncertainty() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitMetadata(f.lease);var closed=new AtomicBoolean();
        var request=new MockHttpServletRequest(){@Override public AsyncContext startAsync(){throw new AssertionError("MALFORMED_STARTED_ASYNC");}};
        request.setContentType("text/plain");var response=new MockHttpServletResponse(){@Override public ServletOutputStream getOutputStream(){throw new AssertionError("MALFORMED_OPENED_OUTPUT");}};
        try {
            new V3PlanTransport(f.service,sessions).body(f.lease,request,response,record,200,()->closed.set(true),()->false,body->{throw new AssertionError("MALFORMED_RAN_ACTION");});
            assertTrue(closed.get());assertFalse(transfers.awaitingWork(f.lease));assertFalse(record.cancelled());
            assertEquals(400,response.getStatus());assertEquals("MALFORMED_BODY",response.getHeader("X-Environment-Studio-Code"));
        } finally {record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
    }
    @Test void failedAsyncAttemptKeepsHonestUncertaintyAfterApplicationResourceClosure() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitCredentials(f.lease);var closed=new AtomicBoolean();
        var request=new MockHttpServletRequest(){@Override public AsyncContext startAsync(){throw new IllegalStateException("invented-start-failure");}};
        request.setContentType("application/json");var response=new MockHttpServletResponse();
        new V3PlanTransport(f.service,sessions).body(f.lease,request,response,record,200,()->closed.set(true),()->false,body->{throw new AssertionError("FAILED_START_RAN_ACTION");});
        assertTrue(closed.get());assertTrue(record.cancelled());assertTrue(transfers.awaitingWork(f.lease));
        record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);assertTrue(transfers.awaitingWork(f.lease));
        assertEquals(500,response.getStatus());assertEquals("PLAN_INTERNAL_REFUSAL",response.getHeader("X-Environment-Studio-Code"));assertEquals(0,response.getContentAsByteArray().length);
    }
    @Test void timeoutCompletionDoesNotReleaseAWorkerStillInsideItsOriginalAction() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitMetadata(f.lease);var context=new Context();var output=new Output();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            new V3PlanTransport(f.service,sessions).read(f.lease,request(context),response(output),record,200,()->{
                entered.countDown();try {if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("ACTION_CONTROL_TIMEOUT");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return new V3PlanReply.Acknowledgement(ack);
            });
            assertTrue(entered.await(1,TimeUnit.SECONDS));context.listener.onTimeout(new AsyncEvent(context.value));
            assertEquals(1,context.calls.get());assertTrue(transfers.awaitingWork(f.lease));assertEquals(0,output.bytes.size());
            release.countDown();settled(transfers,f.lease);assertEquals(0,output.bytes.size());assertEquals(1,context.calls.get());
        } finally {release.countDown();record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
    }
    @Test void failedApplicationClosureRetainsTheRecordDespiteAcceptedHttpCompletion() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitCredentials(f.lease);var context=new Context();var output=new Output();var closed=new AtomicBoolean();
        new V3PlanTransport(f.service,sessions).body(f.lease,bodyRequest(context,"{}"),response(output),record,201,()->{
            closed.set(true);throw new IllegalStateException("invented-unproven-closure");
        },()->false,body->{new PlanMetadataReader().empty(body);return new V3PlanReply.Acknowledgement(ack);});
        assertTrue(context.complete.await(2,TimeUnit.SECONDS));long end=System.nanoTime()+2_000_000_000L;
        while(!record.cancelled()&&System.nanoTime()<end)Thread.sleep(5);
        assertTrue(closed.get());assertTrue(record.cancelled());assertTrue(transfers.awaitingWork(f.lease));assertEquals(1,context.calls.get());
        record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);assertTrue(transfers.awaitingWork(f.lease));
    }
    @Test void listenerRegistrationFailureKeepsBoundedContainerTimeoutAndUncertainOwnership() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitCredentials(f.lease);var context=new Context();context.rejectListener=true;var closed=new AtomicBoolean();
        var response=new MockHttpServletResponse();
        new V3PlanTransport(f.service,sessions).body(f.lease,bodyRequest(context,"{}"),response,record,200,()->closed.set(true),()->false,body->{throw new AssertionError("UNOWNED_ACTION");});
        assertTrue(closed.get());assertTrue(record.cancelled());assertTrue(transfers.awaitingWork(f.lease));
        assertEquals(List.of(30_000L),context.timeouts);assertEquals(0,context.calls.get());assertEquals(500,response.getStatus());assertEquals(0,response.getContentAsByteArray().length);
    }
    @Test void refusedCompletionCannotReleaseCredentialHttpCapacityAfterWorkerClosure() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitCredentials(f.lease);var context=new Context();var closed=new AtomicBoolean();
        context.beforeComplete=()->{assertTrue(closed.get());throw new IllegalStateException("invented-completion-refusal");};
        new V3PlanTransport(f.service,sessions).body(f.lease,bodyRequest(context,"{}"),response(new Output()),record,200,()->closed.set(true),()->false,body->{new PlanMetadataReader().empty(body);return new V3PlanReply.Acknowledgement(ack);});
        long end=System.nanoTime()+2_000_000_000L;while(!record.cancelled()&&System.nanoTime()<end)Thread.sleep(5);
        assertTrue(record.cancelled());assertTrue(transfers.awaitingWork(f.lease));assertEquals(1,context.calls.get());
        context.listener.onComplete(new AsyncEvent(context.value));record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);
        var other=new ArrayList<V3PlanTransfers.Operation>();
        try {for(int i=0;i<3;i++)other.add(transfers.admitCredentials(f.lease));assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->transfers.admitCredentials(f.lease)).code());}
        finally {other.forEach(value->value.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions));}
    }
    @Test void oneByteBeyondMetadataBodyLimitRefusesAndClosesTheOriginalResource() throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);var transfers=new V3PlanTransfers();var sessions=new HostedSessions(Clock.systemUTC(),List.of());
        var record=transfers.admitMetadata(f.lease);var context=new Context();var output=new Output();var response=response(output);var closed=new AtomicBoolean();
        try {
            new V3PlanTransport(f.service,sessions).body(f.lease,bodyRequest(context,"x".repeat(16_385)),response,record,201,()->closed.set(true),()->false,body->{
                try {byte[] scratch=new byte[1024];while(body.read(scratch)>=0){};}
                catch(IOException impossible){throw new AssertionError(impossible);}return new V3PlanReply.Acknowledgement(ack);
            });
            assertTrue(context.complete.await(2,TimeUnit.SECONDS));settled(transfers,f.lease);
            assertTrue(closed.get());assertEquals(413,response.getStatus());assertEquals("close",response.getHeader("Connection"));
            assertEquals("{\"code\":\"BODY_TOO_LARGE\"}",output.bytes.toString(StandardCharsets.UTF_8));
        } finally {record.settlement(OwnedAsyncCompletion.Outcome.COMPLETE,sessions);}
    }
}
