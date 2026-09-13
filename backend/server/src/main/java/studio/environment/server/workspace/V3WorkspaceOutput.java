package studio.environment.server.workspace;

import jakarta.servlet.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import studio.environment.core.workspace.WorkspaceRefusal;

/** One worker, no callback lock held across a servlet call; no payload callback queue. */
final class V3WorkspaceOutput implements AutoCloseable,WriteListener {
    private final ServletOutputStream output;
    private final Runnable verify;
    private final long deadline;
    private volatile Thread writer;
    private volatile boolean failed,closed;
    V3WorkspaceOutput(ServletOutputStream output,Runnable verify,long deadline) {
        this.output=Objects.requireNonNull(output);this.verify=Objects.requireNonNull(verify);this.deadline=deadline;
        long remaining=deadline-System.nanoTime();if(remaining<=0||remaining>30_000_000_000L)throw unavailable();
        check();output.setWriteListener(this);
    }
    private void check(){verify.run();if(failed||closed||Thread.currentThread().isInterrupted()||deadline-System.nanoTime()<=0)throw unavailable();}
    private void ready(){while(true){check();if(output.isReady())return;LockSupport.parkNanos(this,Math.min(100_000_000L,deadline-System.nanoTime()));}}
    void write(byte[] bytes,int length){
        if(length<0||length>8192||length>bytes.length)throw unavailable();writer=Thread.currentThread();
        try{ready();output.write(bytes,0,length);check();}catch(IOException failure){failed=true;throw unavailable();}finally{writer=null;}
    }
    void flush(){writer=Thread.currentThread();try{ready();output.flush();check();}catch(IOException failure){failed=true;throw unavailable();}finally{writer=null;}}
    @Override public void onWritePossible(){LockSupport.unpark(writer);}
    @Override public void onError(Throwable ignored){failed=true;LockSupport.unpark(writer);}
    @Override public void close(){closed=true;LockSupport.unpark(writer);}
    private static WorkspaceRefusal unavailable(){return new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);}
    @Override public String toString(){return "V3WorkspaceOutput[redacted]";}
}
