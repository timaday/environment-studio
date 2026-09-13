package studio.environment.server.plan;

import jakarta.servlet.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BufferedBodyCompletionTest {
    /** Network completion may precede consumption of the container's buffered bytes. */
    static final class Buffered extends ServletInputStream {
        final byte[] bytes;int position;ReadListener listener;
        Buffered(String source){bytes=source.getBytes(StandardCharsets.UTF_8);}
        @Override public boolean isFinished(){return position==bytes.length;}
        @Override public boolean isReady(){return !isFinished();}
        @Override public void setReadListener(ReadListener listener){this.listener=listener;}
        @Override public int read(byte[] target,int offset,int length) throws IOException {
            if(length==0)return 0;if(isFinished())return -1;
            int count=Math.min(3,Math.min(length,bytes.length-position));
            for(int i=0;i<count;i++)target[offset+i]=(byte)read();return count;
        }
        @Override public int read() throws IOException {
            if(isFinished())return -1;
            int next=bytes[position++]&255;
            if(position==1)listener.onAllDataRead();
            return next;
        }
    }
    @Test void completionNotificationCannotDiscardBufferedCredentialBytes() throws Exception {
        var input=new Buffered("{\"username\":\"MockReader\",\"password\":\"Db-Password-Canary-𐀀\"}");
        char[] user=new char[256],password=new char[2048];long deadline=System.nanoTime()+1_000_000_000L;
        try(var body=new OwnedServletBody(input,16384,deadline,()->false)) {
            var lengths=CredentialJsonReader.read(body,user,password,System::nanoTime,deadline);
            assertTrue(Arrays.equals("MockReader".toCharArray(),Arrays.copyOf(user,lengths.username())),"Exact mock username");
            assertTrue(Arrays.equals("Db-Password-Canary-𐀀".toCharArray(),Arrays.copyOf(password,lengths.password())),"Exact mock scalar password");
            assertEquals(-1,body.read());assertTrue(input.isFinished());
        } finally {Arrays.fill(user,'\0');Arrays.fill(password,'\0');}
    }
    @Test void completionNotificationCannotDiscardBufferedBulkReadBytes() throws Exception {
        var input=new Buffered("independently invented buffered body");
        try(var body=new OwnedServletBody(input,16384,System.nanoTime()+1_000_000_000L,()->false)) {
            assertArrayEquals(input.bytes,body.readAllBytes());assertEquals(-1,body.read());
        }
    }
}
