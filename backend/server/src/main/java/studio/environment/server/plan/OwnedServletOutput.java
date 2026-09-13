package studio.environment.server.plan;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/** One nonblocking servlet writer. Callbacks signal readiness; they never queue response bytes. */
final class OwnedServletOutput extends OutputStream implements WriteListener {
    private final ServletOutputStream output;
    private final Runnable verify;
    private final long deadline;
    private final LongSupplier clock;
    private final Object signal=new Object();
    private volatile boolean closed,failed;
    private volatile Thread writer;
    OwnedServletOutput(ServletOutputStream output,Runnable verify,long deadline,LongSupplier clock) throws IOException {
        this.output=Objects.requireNonNull(output);this.verify=Objects.requireNonNull(verify);this.deadline=deadline;this.clock=Objects.requireNonNull(clock);
        long remaining=deadline-clock.getAsLong();if(remaining<=0 || remaining>30_000_000_000L)throw refused();
        check();output.setWriteListener(this);
    }
    private IOException refused(){failed=true;return new IOException("PLAN_TRANSFER_REFUSED");}
    private void check() throws IOException {
        if(closed || failed || deadline-clock.getAsLong()<=0)throw refused();
        try {verify.run();}catch(RuntimeException invalidated){throw refused();}
    }
    private void ready() throws IOException {
        while(true) {
            check();if(output.isReady()){check();return;}
            long remaining=deadline-clock.getAsLong();if(remaining<=0)throw refused();
            LockSupport.parkNanos(this,Math.min(100_000_000L,remaining));
            if(Thread.currentThread().isInterrupted())throw refused();
        }
    }
    @Override public void write(int value)throws IOException {write(new byte[]{(byte)value},0,1);}
    @Override public void write(byte[] bytes,int offset,int length)throws IOException {
        Objects.checkFromIndexSize(offset,length,bytes.length);
        synchronized(signal) {
            writer=Thread.currentThread();
            try {while(length>0){ready();int count=Math.min(length,8192);output.write(bytes,offset,count);offset+=count;length-=count;check();}}
            catch(IOException|RuntimeException failure){throw refused();}
            finally {writer=null;}
        }
    }
    @Override public void flush()throws IOException {synchronized(signal){writer=Thread.currentThread();try{ready();output.flush();ready();}catch(IOException|RuntimeException failure){throw refused();}finally{writer=null;}}}
    @Override public void onWritePossible(){LockSupport.unpark(writer);}
    @Override public void onError(Throwable ignored){failed=true;LockSupport.unpark(writer);}
    // AsyncContext owns servlet completion. This close releases this writer's authority only.
    @Override public void close(){closed=true;LockSupport.unpark(writer);}
    @Override public String toString(){return "OwnedServletOutput[redacted]";}
}
