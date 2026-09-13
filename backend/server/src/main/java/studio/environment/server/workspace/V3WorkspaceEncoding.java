package studio.environment.server.workspace;

import java.io.OutputStream;
import java.util.*;
import studio.environment.core.workspace.WorkspaceRefusal;

/** Mutable complete encoding capped before allocation; single transfer clock. */
final class V3WorkspaceEncoding extends OutputStream {
    static final int LIMIT=8*1024*1024,CHUNK=8192;
    private final List<byte[]> chunks=new ArrayList<>();
    private final Runnable verify;
    private final long deadline;
    private int size;
    private boolean closed;
    V3WorkspaceEncoding(Runnable verify,long deadline){this.verify=Objects.requireNonNull(verify);this.deadline=deadline;long remaining=deadline-System.nanoTime();if(remaining<=0||remaining>30_000_000_000L)throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);}
    void check(){verify.run();if(closed||Thread.currentThread().isInterrupted()||deadline-System.nanoTime()<=0)throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE);}
    @Override public void write(int value){byte[] one={(byte)value};try{write(one,0,1);}finally{Arrays.fill(one,(byte)0);}}
    @Override public void write(byte[] bytes,int offset,int length){
        Objects.checkFromIndexSize(offset,length,bytes.length);check();
        if(length>LIMIT-size)throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
        while(length>0){check();int position=size%CHUNK;if(position==0)chunks.add(new byte[CHUNK]);int count=Math.min(length,CHUNK-position);
            System.arraycopy(bytes,offset,chunks.getLast(),position,count);size+=count;offset+=count;length-=count;}check();
    }
    void transfer(V3WorkspaceOutput output){int remaining=size;for(byte[] bytes:chunks){check();int count=Math.min(CHUNK,remaining);output.write(bytes,count);remaining-=count;}output.flush();check();}
    int size(){return size;}
    @Override public void close(){closed=true;chunks.forEach(bytes->Arrays.fill(bytes,(byte)0));chunks.clear();size=0;}
    @Override public String toString(){return "V3WorkspaceEncoding[redacted]";}
}
