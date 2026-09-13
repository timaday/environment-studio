package studio.environment.server.plan;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.LockSupport;
import static studio.environment.server.plan.PlanBodyFailure.Code.*;

/** One owned reader, no payload queue. Only readiness callbacks signal its immediately started worker. */
final class OwnedServletBody extends InputStream implements ReadListener {
    private final ServletInputStream input;
    private final BooleanSupplier cancelled;
    private final long deadline;
    private final int byteLimit;
    private final Object signal=new Object();
    private volatile boolean closed;
    private volatile Thread reader;
    private volatile PlanBodyFailure failure;
    private int bytes;
    OwnedServletBody(ServletInputStream input,int byteLimit,long deadline,BooleanSupplier cancelled) {
        this.input=input; this.byteLimit=byteLimit; this.deadline=deadline; this.cancelled=cancelled;
        input.setReadListener(this);
    }
    long deadline() { return deadline; }
    @Override public int read() throws IOException {
        synchronized(signal) {
            reader=Thread.currentThread();
            try {if(!ready()) return -1;
                int next=input.read(); if(next>=0) count(1); return next;
            } finally {reader=null;}
        }
    }
    @Override public int read(byte[] target,int offset,int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset,length,target.length);
        if(length==0) return 0;
        synchronized(signal) {
            reader=Thread.currentThread();
            try {if(!ready()) return -1;
                int count=input.read(target,offset,Math.min(length,byteLimit-bytes+1));
                if(count>0) count(count); return count;
            } finally {reader=null;}
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
            if(input.isFinished()) return false;
            if(input.isReady()) return true;
            LockSupport.parkNanos(this,Math.min(100_000_000L,remaining));
            if(Thread.currentThread().isInterrupted())throw new PlanBodyFailure(MALFORMED_BODY);
        }
    }
    @Override public void onDataAvailable() { LockSupport.unpark(reader); }
    // A network-completion callback can precede consumption of container-buffered bytes.
    @Override public void onAllDataRead() { LockSupport.unpark(reader); }
    @Override public void onError(Throwable ignored) { failure=new PlanBodyFailure(MALFORMED_BODY); LockSupport.unpark(reader); }
    @Override public void close() { closed=true; LockSupport.unpark(reader); }
}
