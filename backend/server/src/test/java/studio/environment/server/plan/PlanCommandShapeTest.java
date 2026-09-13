package studio.environment.server.plan;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanCommand;
import static org.junit.jupiter.api.Assertions.*;

class PlanCommandShapeTest {
    @Test void maximumEntityFieldReferenceShapeFitsEightActualStreamingBatches() {
        long fields=0,references=0,entities=0;
        for(int batch=0;batch<8;batch++) {
            var source=new BatchSource(batch);
            var decoded=new PlanCommandReader().read(source);
            var action=assertInstanceOf(PlanCommand.Action.Batch.class,decoded.action());
            assertEquals(2500,action.changes().size());
            for(var change:action.changes()) {
                var entity=assertInstanceOf(PlanCommand.Entity.Create.class,change.decision());
                assertEquals(256,entity.fields().size());assertEquals(256,entity.references().size());
                fields+=entity.fields().size();references+=entity.references().size();entities++;
            }
            assertEquals(source.expectedBytes,source.readBytes);
            assertTrue(source.readBytes<128L*1_048_576,"Maximum-shape partition exceeds the wire byte contract");
        }
        assertEquals(20_000,entities);assertEquals(5_120_000,fields);assertEquals(5_120_000,references);
        assertTrue(8<256,"Shape must not consume one replay ID per entity");
    }
    /** Generates independently invented JSON incrementally; does not retain a second full request body. */
    static final class BatchSource extends InputStream {
        final String members;
        final int batch;
        final long expectedBytes;
        long readBytes;
        int entity=-1,offset;
        byte[] segment;
        BatchSource(int batch) {
            this.batch=batch;
            var fields=new StringBuilder();var references=new StringBuilder();
            for(int i=0;i<256;i++) {
                if(i>0){fields.append(',');references.append(',');}
                fields.append('"').append("field-").append(i).append("\":{\"kind\":\"unresolved\"}");
                references.append('"').append("ref-").append(i).append("\":{\"kind\":\"unresolved\"}");
            }
            members=",\"typeId\":\"sample\"},\"fields\":{"+fields+"},\"references\":{"+references+"}},\"placements\":[]}";
            long count=header().getBytes(StandardCharsets.UTF_8).length+"],\"containment\":[]}".length();
            for(int i=0;i<2500;i++)count+=element(i).getBytes(StandardCharsets.UTF_8).length;
            expectedBytes=count;
        }
        String header(){return "{\"kind\":\"batch-upsert\",\"expectedRevision\":\""+(batch+2)+"\",\"requestId\":\"00000000-0000-4000-8000-"+String.format(java.util.Locale.ROOT,"%012d",batch+1)+"\",\"changes\":[";}
        String element(int index){return (index==0?"":",")+"{\"decision\":{\"kind\":\"create\",\"entity\":{\"kind\":\"fresh\",\"slotId\":\"slot-"+(batch*2500+index)+"\""+members;}
        boolean ready() {
            if(segment!=null && offset<segment.length)return true;
            if(entity>2500)return false;
            String next=entity==-1?header():entity==2500?"],\"containment\":[]}":element(entity);
            entity++;segment=next.getBytes(StandardCharsets.UTF_8);offset=0;return true;
        }
        @Override public int read(){if(!ready())return -1;readBytes++;return segment[offset++]&255;}
        @Override public int read(byte[] target,int start,int length){if(!ready())return -1;int count=Math.min(length,segment.length-offset);System.arraycopy(segment,offset,target,start,count);offset+=count;readBytes+=count;return count;}
    }
}
