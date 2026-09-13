package studio.environment.server.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class StrictPackageZipTest {
    static List<StrictPackageZip.Member> members(){return List.of(member("manifest.json","{}"),member("payload.json","{}"),member("transaction.sql","BEGIN\nEND;\n"),member("instructions.txt","mock instructions\n"));}
    static StrictPackageZip.Member member(String name,String value){return new StrictPackageZip.Member(name,value.getBytes(StandardCharsets.UTF_8));}
    @Test void exactArchiveMatchesIndependentPythonStructOracle() throws Exception {
        var out=new ByteArrayOutputStream();var result=assertInstanceOf(StrictPackageZip.WriteResult.Written.class,new StrictPackageZip().write(members(),out));
        var expected=JsonMapper.builder().build().readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-writer-v1/zip-expected.json")));
        assertEquals(expected.get("sha256").asString(),result.sha256());assertEquals(expected.get("archiveBytes").longValue(),result.bytes());
        var decoded=assertInstanceOf(StrictPackageZip.ReadResult.Decoded.class,new StrictPackageZip().read(out.toByteArray()));assertEquals(4,decoded.members().size());
        for(int i=0;i<4;i++){assertEquals(members().get(i).name(),decoded.members().get(i).name());assertArrayEquals(members().get(i).bytes(),decoded.members().get(i).bytes());}
    }
    @Test void refusesEveryTruncationTrailingBytesAndAlteredFixedHeaders() {
        var out=new ByteArrayOutputStream();assertInstanceOf(StrictPackageZip.WriteResult.Written.class,new StrictPackageZip().write(members(),out));byte[] valid=out.toByteArray();
        for(int length=0;length<valid.length;length++)assertInstanceOf(StrictPackageZip.ReadResult.Rejected.class,new StrictPackageZip().read(java.util.Arrays.copyOf(valid,length)),"length "+length);
        assertInstanceOf(StrictPackageZip.ReadResult.Rejected.class,new StrictPackageZip().read(java.util.Arrays.copyOf(valid,valid.length+1)));
        for(int offset=0;offset<valid.length;offset++){byte[] changed=valid.clone();changed[offset]^=1;assertInstanceOf(StrictPackageZip.ReadResult.Rejected.class,new StrictPackageZip().read(changed),"offset "+offset);}
    }
    @Test void refusesVirtualOversizedMemberListAndOversizedSmallMemberBeforeOutput() {
        var virtual=new java.util.AbstractList<StrictPackageZip.Member>(){public int size(){return Integer.MAX_VALUE;}public StrictPackageZip.Member get(int i){throw new AssertionError("must not traverse");}};
        var out=new ByteArrayOutputStream();assertInstanceOf(StrictPackageZip.WriteResult.Rejected.class,new StrictPackageZip().write(virtual,out));assertEquals(0,out.size());
        var list=new java.util.ArrayList<>(members());list.set(0,new StrictPackageZip.Member("manifest.json",new byte[65_537]));assertInstanceOf(StrictPackageZip.WriteResult.Rejected.class,new StrictPackageZip().write(list,out));assertEquals(0,out.size());
        list=new java.util.ArrayList<>(members());list.set(1,list.get(0));assertInstanceOf(StrictPackageZip.WriteResult.Rejected.class,new StrictPackageZip().write(list,out));assertEquals(0,out.size());
    }
    @Test void outputFailureAndCallerMutationDoNotLeakOrChangeMemberBytes() {
        byte[] input="{}".getBytes(StandardCharsets.UTF_8);var member=new StrictPackageZip.Member("manifest.json",input);input[0]='x';assertEquals("{}",new String(member.bytes(),StandardCharsets.UTF_8));byte[] returned=member.bytes();returned[0]='x';assertEquals("{}",new String(member.bytes(),StandardCharsets.UTF_8));
        var output=new java.io.OutputStream(){public void write(int value)throws java.io.IOException{throw new java.io.IOException("secret-value-canary");}};
        var result=assertInstanceOf(StrictPackageZip.WriteResult.Rejected.class,new StrictPackageZip().write(members(),output));assertEquals("OUTPUT_FAILURE",result.code());assertFalse(result.toString().contains("secret-value-canary"));
    }
    @Test void streamsExactAggregateBoundaryAndRefusesOneByteOverBeforeOutput() {
        var maximum=List.of(new StrictPackageZip.Member("manifest.json",new byte[65_536]),new StrictPackageZip.Member("payload.json",new byte[80*1024*1024]),new StrictPackageZip.Member("transaction.sql",new byte[80*1024*1024-131_072]),new StrictPackageZip.Member("instructions.txt",new byte[65_536]));
        var result=assertInstanceOf(StrictPackageZip.WriteResult.Written.class,new StrictPackageZip().write(maximum,java.io.OutputStream.nullOutputStream()));assertEquals(160L*1024*1024+438,result.bytes());
        var exceeded=new java.util.ArrayList<>(maximum);exceeded.set(2,new StrictPackageZip.Member("transaction.sql",new byte[80*1024*1024-131_071]));var output=new ByteArrayOutputStream();
        assertEquals("RESOURCE_LIMIT",assertInstanceOf(StrictPackageZip.WriteResult.Rejected.class,new StrictPackageZip().write(exceeded,output)).code());assertEquals(0,output.size());
    }
    @Test void readerChecksLargeMemberBoundaryBeforeAccessingMissingData() {
        var output=new ByteArrayOutputStream();assertInstanceOf(StrictPackageZip.WriteResult.Written.class,new StrictPackageZip().write(members(),output));byte[] bytes=output.toByteArray();int second=30+"manifest.json".length()+2;
        var buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);buffer.putInt(second+18,80*1024*1024);buffer.putInt(second+22,80*1024*1024);
        assertEquals("ZIP_TRUNCATED",assertInstanceOf(StrictPackageZip.ReadResult.Rejected.class,new StrictPackageZip().read(bytes)).code());
        buffer.putInt(second+18,80*1024*1024+1);buffer.putInt(second+22,80*1024*1024+1);
        assertEquals("RESOURCE_LIMIT",assertInstanceOf(StrictPackageZip.ReadResult.Rejected.class,new StrictPackageZip().read(bytes)).code());
    }
}
