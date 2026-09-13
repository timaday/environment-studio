package studio.environment.server.workspace;

import jakarta.servlet.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import studio.environment.core.workspace.WorkspaceRefusal;

/** One worker reads; callbacks only publish state and wake it. */
final class V3WorkspaceBody extends InputStream implements ReadListener {
    private final ServletInputStream input;
    private final Runnable verify;
    private final long deadline;
    private volatile Thread reader;
    private volatile boolean failed,closed;
    private int count;
    private final List<byte[]> returned=new ArrayList<>();
    V3WorkspaceBody(ServletInputStream input,Runnable verify,long deadline) {
        this.input=Objects.requireNonNull(input);this.verify=Objects.requireNonNull(verify);this.deadline=deadline;
        long remaining=deadline-System.nanoTime();if(remaining<=0||remaining>30_000_000_000L)throw unavailable();
        check();input.setReadListener(this);
    }
    private void check(){verify.run();if(closed||failed||Thread.currentThread().isInterrupted()||deadline-System.nanoTime()<=0)throw unavailable();}
    private boolean ready(){while(true){check();if(input.isFinished())return false;if(input.isReady())return true;LockSupport.parkNanos(this,Math.min(100_000_000L,deadline-System.nanoTime()));}}
    @Override public int read()throws IOException {byte[] one=new byte[1];try{return read(one,0,1)<0?-1:one[0]&255;}finally{Arrays.fill(one,(byte)0);}}
    @Override public int read(byte[] target,int offset,int length)throws IOException {
        Objects.checkFromIndexSize(offset,length,target.length);if(length==0)return 0;reader=Thread.currentThread();
        try{if(!ready())return -1;int n=input.read(target,offset,Math.min(length,DraftRequestReader.MAX_BODY-count+1));
            if(n>0){count+=n;if(count>DraftRequestReader.MAX_BODY)throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);}check();return n;
        }catch(IOException failure){throw unavailable();}finally{reader=null;}
    }
    // The unchanged closed reader owns this returned array only until parse finishes.
    @Override public byte[] readNBytes(int requested)throws IOException {
        if(requested<0||requested>DraftRequestReader.MAX_BODY+1)throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
        byte[] scratch=new byte[requested];int length=0;
        try{while(length<requested){int n=read(scratch,length,requested-length);if(n<0)break;length+=n;}
            byte[] result=Arrays.copyOf(scratch,length);returned.add(result);return result;
        }finally{Arrays.fill(scratch,(byte)0);}
    }
    @Override public void onDataAvailable(){LockSupport.unpark(reader);}
    @Override public void onAllDataRead(){LockSupport.unpark(reader);}
    @Override public void onError(Throwable ignored){failed=true;LockSupport.unpark(reader);}
    @Override public void close(){closed=true;returned.forEach(bytes->Arrays.fill(bytes,(byte)0));returned.clear();LockSupport.unpark(reader);}
    private static WorkspaceRefusal unavailable(){return new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);}
    @Override public String toString(){return "V3WorkspaceBody[redacted]";}
}
