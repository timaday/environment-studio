package studio.environment.server.plan;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;
import static studio.environment.server.plan.PlanBodyFailure.Code.*;

/** One owned reader, no payload queue. Only readiness callbacks signal its immediately started worker. */
final class OwnedServletBody extends InputStream implements ReadListener {
    private final ServletInputStream input;
    private final BooleanSupplier cancelled;
    private final long deadline;
    private final int byteLimit;
    private final Object signal=new Object();
    private boolean closed,finished;
    private PlanBodyFailure failure;
    private int bytes;
    OwnedServletBody(ServletInputStream input,int byteLimit,long deadline,BooleanSupplier cancelled) {
        this.input=input; this.byteLimit=byteLimit; this.deadline=deadline; this.cancelled=cancelled;
        input.setReadListener(this);
    }
    long deadline() { return deadline; }
    @Override public int read() throws IOException {
        synchronized(signal) {
            if(!ready()) return -1;
            int next=input.read(); if(next>=0) count(1); return next;
        }
    }
    @Override public int read(byte[] target,int offset,int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset,length,target.length);
        if(length==0) return 0;
        synchronized(signal) {
            if(!ready()) return -1;
            int count=input.read(target,offset,Math.min(length,byteLimit-bytes+1));
            if(count>0) count(count); return count;
        }
    }
    private void count(int count) {
        bytes+=count;
        if(bytes>byteLimit) throw new PlanBodyFailure(BODY_TOO_LARGE);
        if(System.nanoTime()-deadline>=0) throw new PlanBodyFailure(BODY_DEADLINE);
    }
    private boolean ready() {
        while(true) {
            if(closed) throw new PlanBodyFailure(MALFORMED_BODY);
            if(cancelled.getAsBoolean()) throw new PlanBodyFailure(CANCELLED);
            if(failure!=null) throw failure;
            long remaining=deadline-System.nanoTime();
            if(remaining<=0) throw new PlanBodyFailure(BODY_DEADLINE);
            if(finished || input.isFinished()) return false;
            if(input.isReady()) return true;
            try {signal.wait(Math.max(1,Math.min(100,remaining/1_000_000)));}
            catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new PlanBodyFailure(MALFORMED_BODY);}
        }
    }
    @Override public void onDataAvailable() { synchronized(signal) { signal.notifyAll(); } }
    @Override public void onAllDataRead() { synchronized(signal) { finished=true; signal.notifyAll(); } }
    @Override public void onError(Throwable ignored) { synchronized(signal) { failure=new PlanBodyFailure(MALFORMED_BODY); signal.notifyAll(); } }
    @Override public void close() { synchronized(signal) { closed=true; signal.notifyAll(); } }
}
