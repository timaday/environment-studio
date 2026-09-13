package studio.environment.server.plan;

import java.io.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import studio.environment.core.plan.PlanRefusal;

/** Bounded UTF-8 chunks: no geometric whole-response copy and no reusable encoded session cache. */
final class PlanViewEncoding extends OutputStream implements AutoCloseable {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final int maximum;private final Runnable verify;private final List<byte[]> chunks=new ArrayList<>();private int size,used;private boolean attempted,complete,closed;
    PlanViewEncoding(int maximum){this(maximum,()->{});}
    PlanViewEncoding(int maximum,Runnable verify){if(maximum<1 || maximum>134_217_728)throw new IllegalArgumentException("INVALID_RESPONSE_LIMIT");this.maximum=maximum;this.verify=Objects.requireNonNull(verify);}
    void encode(Object value){
        if(attempted || closed)throw new IllegalStateException("RESPONSE_ENCODING_CONSUMED");attempted=true;
        try {verify.run();JSON.writeValue(new FilterOutputStream(this){@Override public void close(){} @Override public void write(byte[] bytes,int offset,int length){PlanViewEncoding.this.write(bytes,offset,length);}},value);verify.run();complete=true;}
        catch(RuntimeException failure){close();Throwable cause=failure;for(int i=0;i<8 && cause!=null;i++,cause=cause.getCause())if(cause instanceof PlanRefusal bounded)throw bounded;throw new IllegalStateException("PLAN_RESPONSE_ENCODING_UNAVAILABLE");}
    }
    int size(){return size;}
    @Override public void write(int value){verify.run();if(closed || size>=maximum)throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);space();chunks.getLast()[used++]=(byte)value;size++;}
    @Override public void write(byte[] bytes,int offset,int length){
        verify.run();if(closed || length>maximum-size)throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);
        while(length>0){verify.run();space();int count=Math.min(length,chunks.getLast().length-used);System.arraycopy(bytes,offset,chunks.getLast(),used,count);offset+=count;length-=count;used+=count;size+=count;}
    }
    private void space(){verify.run();if(chunks.isEmpty() || used==chunks.getLast().length){chunks.add(new byte[Math.min(8192,maximum-size)]);used=0;}}
    void write(OutputStream output,Runnable verify)throws IOException {
        if(!complete || closed)throw new IllegalStateException("RESPONSE_ENCODING_UNAVAILABLE");
        for(int i=0;i<chunks.size();i++){verify.run();byte[] chunk=chunks.get(i);output.write(chunk,0,i==chunks.size()-1?used:chunk.length);}verify.run();
    }
    @Override public void close(){if(closed)return;closed=true;chunks.forEach(chunk->Arrays.fill(chunk,(byte)0));chunks.clear();}
    @Override public String toString(){return "EncodedPlanView[redacted]";}
}
