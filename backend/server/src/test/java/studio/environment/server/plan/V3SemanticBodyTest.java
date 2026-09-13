package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import studio.environment.core.plan.*;
import studio.environment.server.session.HostedSessions;

/** Generated whitespace exercises transport bounds only; command grammar is checked separately. */
class V3SemanticBodyTest {
    static final class Input extends ServletInputStream {
        final int size;int delivered;long acquired;
        Input(int size){this.size=size;}
        public boolean isReady(){return true;}
        public boolean isFinished(){return delivered==size;}
        public void setReadListener(ReadListener listener){}
        public int read(){if(delivered==size)return -1;delivered++;return ' ';}
        public int read(byte[] target,int offset,int length){
            if(delivered==size)return -1;int count=Math.min(length,size-delivered);
            Arrays.fill(target,offset,offset+count,(byte)' ');delivered+=count;return count;
        }
    }
    static MockHttpServletRequest request(V3PlanTransportTest.Context context,Input input){
        var request=new MockHttpServletRequest(){
            @Override public AsyncContext startAsync(){return context.value;}
            @Override public ServletInputStream getInputStream(){input.acquired=System.nanoTime();return input;}
        };request.setContentType("application/json");return request;
    }
    static long drain(OwnedServletBody body){
        byte[] scratch=new byte[8192];long count=0;
        try {int read;while((read=body.read(scratch))>=0)count+=read;return count;}
        catch(IOException failure){throw new UncheckedIOException(failure);}
        finally {Arrays.fill(scratch,(byte)0);}
    }
    @Test void largerBodyKeepsTheOriginalThirtySecondBudgetAndCommandScratchThroughAckOutput()throws Exception {
        var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);
        var registry=new V3PlanTransfers();var record=registry.admitSemantic(f.lease);
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());var admission=f.service.reserveCommand(f.lease,ack.planId(),PlanDefinition.Version.V3);
        var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();output.ready=false;
        var response=V3PlanTransportTest.response(output);var input=new Input(20_000);var read=new AtomicLong(-1);var budget=new AtomicLong(-1);
        try {
            new V3PlanTransport(f.service,sessions).semanticBody(f.lease,request(context,input),response,record,200,admission,()->!admission.live(),body->{
                budget.set(body.deadline()-input.acquired);read.set(drain(body));return new V3PlanReply.Acknowledgement(ack);
            });
            assertTrue(output.checked.await(3,TimeUnit.SECONDS));
            assertEquals(200,response.getStatus());assertEquals(20_000,read.get());
            assertTrue(budget.get()>20_000_000_000L && budget.get()<=30_000_000_000L,"semantic body must retain its original30s budget");
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->f.service.reserveCommand(f.lease,ack.planId(),PlanDefinition.Version.V3)).code());
            assertEquals(PlanRefusal.Code.CAPACITY,assertThrows(PlanRefusal.class,()->registry.admitSemantic(f.lease)).code());
            output.ready=true;assertTrue(context.complete.await(3,TimeUnit.SECONDS));V3PlanTransportTest.settled(registry,f.lease);
            try(var next=f.service.reserveCommand(f.lease,ack.planId(),PlanDefinition.Version.V3)){assertTrue(next.live());}
            assertEquals("{\"planId\":\""+ack.planId()+"\",\"revision\":\"1\"}",output.bytes.toString(StandardCharsets.UTF_8));
        } finally {output.ready=true;f.ledger.close(f.lease.id());context.complete.await(3,TimeUnit.SECONDS);admission.close();}
    }
    @Test void exactFullWireLimitAndOneExtraByteHaveIndependentExpectedOutcomes()throws Exception {
        for(int size:new int[]{134_217_728,134_217_729}) {
            var f=new PlanV1VersionBoundaryTest.Fixture();var ack=f.create(true);var registry=new V3PlanTransfers();var record=registry.admitSemantic(f.lease);
            var sessions=new HostedSessions(Clock.systemUTC(),List.of());var admission=f.service.reserveCommand(f.lease,ack.planId(),PlanDefinition.Version.V3);
            var context=new V3PlanTransportTest.Context();var output=new V3PlanTransportTest.Output();var response=V3PlanTransportTest.response(output);
            var input=new Input(size);var read=new AtomicLong(-1);
            try {
                new V3PlanTransport(f.service,sessions).semanticBody(f.lease,request(context,input),response,record,200,admission,()->!admission.live(),body->{
                    read.set(drain(body));return new V3PlanReply.Acknowledgement(ack);
                });
                assertTrue(context.complete.await(5,TimeUnit.SECONDS));V3PlanTransportTest.settled(registry,f.lease);
                assertEquals(size,input.delivered,"streaming limit must not fall back to small-body admission");
                if(size==134_217_728){assertEquals(200,response.getStatus());assertEquals(size,read.get());}
                else {assertEquals(413,response.getStatus());assertEquals(-1,read.get());assertEquals("{\"code\":\"BODY_TOO_LARGE\"}",output.bytes.toString(StandardCharsets.UTF_8));}
                try(var next=f.service.reserveCommand(f.lease,ack.planId(),PlanDefinition.Version.V3)){assertTrue(next.live());}
            } finally {f.ledger.close(f.lease.id());context.complete.await(3,TimeUnit.SECONDS);admission.close();}
        }
    }
}
