package studio.environment.supervisor;
import java.io.InputStream;
import java.nio.*;
import java.nio.charset.*;
import java.util.Arrays;

final class BoundedSecret implements AutoCloseable {
    private byte[] bytes;
    private BoundedSecret(byte[] bytes){this.bytes=bytes;}
    static BoundedSecret read(InputStream input)throws java.io.IOException {
        byte[] buffer=new byte[4096];int used=0;
        try {
            for(;;){int b=input.read();Refusal.require(b>=0,"CREDENTIAL_EOF");if(b=='\n')break;Refusal.require(b!=0&&b!='\r',"INVALID_CREDENTIAL");Refusal.require(used<buffer.length,"CREDENTIAL_LIMIT");buffer[used++]=(byte)b;}
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);CharBuffer chars=CharBuffer.allocate(4096);
            try{var result=decoder.decode(ByteBuffer.wrap(buffer,0,used),chars,true);Refusal.require(!result.isError(),"INVALID_CREDENTIAL");chars.flip();int count=0;while(chars.hasRemaining()){char c=chars.get();if(Character.isHighSurrogate(c)){Refusal.require(chars.hasRemaining()&&Character.isLowSurrogate(chars.get()),"INVALID_CREDENTIAL");}count++;}Refusal.require(count<=1024,"CREDENTIAL_LIMIT");}finally{Arrays.fill(chars.array(),'\0');}
            return new BoundedSecret(Arrays.copyOf(buffer,used));
        }finally{Arrays.fill(buffer,(byte)0);}
    }
    byte[] line(){Refusal.require(bytes!=null,"CREDENTIAL_CLOSED");byte[] line=Arrays.copyOf(bytes,bytes.length+1);line[line.length-1]='\n';return line;}
    @Override public void close(){if(bytes!=null){Arrays.fill(bytes,(byte)0);bytes=null;}}
    @Override public String toString(){return "BoundedSecret[redacted]";}
}
