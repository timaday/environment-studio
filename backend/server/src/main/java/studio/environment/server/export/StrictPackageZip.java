package studio.environment.server.export;

import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.nio.charset.StandardCharsets;
import static studio.environment.server.export.PackageJson.fail;
import java.util.List;

/** Fixed-format container mechanism; integrity does not establish export or execution authority. */
public final class StrictPackageZip {
    public static final List<String> NAMES=List.of("manifest.json","payload.json","transaction.sql","instructions.txt");
    public static final class Member {
        private final String name;private final byte[]bytes;
        public Member(String name,byte[] bytes){this(name,bytes,false);}
        private Member(String name,byte[] bytes,boolean owned){if(bytes.length>PackageJson.LARGE)throw new IllegalArgumentException("PACKAGE_RESOURCE_LIMIT");this.name=name;this.bytes=owned?bytes:bytes.clone();}
        public String name(){return name;}public byte[]bytes(){return bytes.clone();}
        @Override public String toString(){return "PackageMember[redacted]";}
    }
    public sealed interface WriteResult { record Written(long bytes,String sha256)implements WriteResult{} record Rejected(String code)implements WriteResult{} }
    public sealed interface ReadResult { record Decoded(List<Member> members)implements ReadResult { public Decoded{if(members.size()>4)throw new IllegalArgumentException("PACKAGE_RESOURCE_LIMIT");members=List.copyOf(members);}@Override public String toString(){return "DecodedZip[redacted,unqualified]";} } record Rejected(String code)implements ReadResult{} }
    private static final long MAX_TOTAL=160L*1024*1024;
    public WriteResult write(List<Member> members,OutputStream output){
        try {
            if(members==null||output==null)fail("INVALID_INPUT");if(members.size()!=4)fail("ZIP_STRUCTURE");
            var snapshot=new ArrayList<Member>();for(int i=0;i<4;i++){var member=members.get(i);if(member==null)fail("INVALID_INPUT");snapshot.add(member);}members=List.copyOf(snapshot);
            long total=0;for(int i=0;i<4;i++){var member=members.get(i);if(member==null||!NAMES.get(i).equals(member.name))fail("ZIP_STRUCTURE");checkSize(i,member.bytes.length);total+=member.bytes.length;}if(total>MAX_TOTAL)fail("RESOURCE_LIMIT");
            var sink=new Sink(output);long[]offsets=new long[4],crcs=new long[4];
            for(int i=0;i<4;i++){var member=members.get(i);byte[]name=member.name.getBytes(StandardCharsets.US_ASCII);offsets[i]=sink.count;var crc=new CRC32();crc.update(member.bytes);crcs[i]=crc.getValue();
                sink.u32(0x04034b50);sink.u16(20);sink.u16(0x0800);sink.u16(0);sink.u16(0);sink.u16(33);sink.u32(crcs[i]);sink.u32(member.bytes.length);sink.u32(member.bytes.length);sink.u16(name.length);sink.u16(0);sink.bytes(name);sink.bytes(member.bytes);
            }
            long central=sink.count;
            for(int i=0;i<4;i++){var member=members.get(i);byte[]name=member.name.getBytes(StandardCharsets.US_ASCII);
                sink.u32(0x02014b50);sink.u16(0x0314);sink.u16(20);sink.u16(0x0800);sink.u16(0);sink.u16(0);sink.u16(33);sink.u32(crcs[i]);sink.u32(member.bytes.length);sink.u32(member.bytes.length);sink.u16(name.length);sink.u16(0);sink.u16(0);sink.u16(0);sink.u16(0);sink.u32(0100600L<<16);sink.u32(offsets[i]);sink.bytes(name);
            }
            long centralBytes=sink.count-central;sink.u32(0x06054b50);sink.u16(0);sink.u16(0);sink.u16(4);sink.u16(4);sink.u32(centralBytes);sink.u32(central);sink.u16(0);
            return new WriteResult.Written(sink.count,HexFormat.of().formatHex(sink.digest.digest()));
        }catch(PackageJson.Refusal refused){return new WriteResult.Rejected(refused.code);}catch(IOException failure){return new WriteResult.Rejected("OUTPUT_FAILURE");}
    }
    public ReadResult read(byte[] archive){
        try {
            if(archive==null)fail("INVALID_INPUT");if(archive.length>MAX_TOTAL+438)fail("RESOURCE_LIMIT");var cursor=new Cursor(archive);long total=0;long[]offsets=new long[4],crcs=new long[4],sizes=new long[4];int[]starts=new int[4];
            for(int i=0;i<4;i++){offsets[i]=cursor.position;cursor.expect32(0x04034b50);cursor.expect16(20);cursor.expect16(0x0800);cursor.expect16(0);cursor.expect16(0);cursor.expect16(33);crcs[i]=cursor.u32();sizes[i]=cursor.u32();if(cursor.u32()!=sizes[i])fail("ZIP_STRUCTURE");checkSize(i,sizes[i]);total+=sizes[i];if(total>MAX_TOTAL)fail("RESOURCE_LIMIT");cursor.expect16(NAMES.get(i).length());cursor.expect16(0);cursor.name(NAMES.get(i));starts[i]=cursor.position;cursor.skip(sizes[i]);var crc=new CRC32();crc.update(archive,starts[i],(int)sizes[i]);if(crc.getValue()!=crcs[i])fail("ZIP_CRC");}
            long central=cursor.position;
            for(int i=0;i<4;i++){cursor.expect32(0x02014b50);cursor.expect16(0x0314);cursor.expect16(20);cursor.expect16(0x0800);cursor.expect16(0);cursor.expect16(0);cursor.expect16(33);cursor.expect32(crcs[i]);cursor.expect32(sizes[i]);cursor.expect32(sizes[i]);cursor.expect16(NAMES.get(i).length());cursor.expect16(0);cursor.expect16(0);cursor.expect16(0);cursor.expect16(0);cursor.expect32(0100600L<<16);cursor.expect32(offsets[i]);cursor.name(NAMES.get(i));}
            long centralBytes=cursor.position-central;cursor.expect32(0x06054b50);cursor.expect16(0);cursor.expect16(0);cursor.expect16(4);cursor.expect16(4);cursor.expect32(centralBytes);cursor.expect32(central);cursor.expect16(0);if(cursor.position!=archive.length)fail("ZIP_STRUCTURE");
            List<Member> members=new ArrayList<>();for(int i=0;i<4;i++)members.add(new Member(NAMES.get(i),Arrays.copyOfRange(archive,starts[i],starts[i]+(int)sizes[i]),true));return new ReadResult.Decoded(members);
        }catch(PackageJson.Refusal refused){return new ReadResult.Rejected(refused.code);}
    }
    private static void checkSize(int index,long size){if(size<1||size>(index==0||index==3?PackageJson.SMALL:PackageJson.LARGE))fail("RESOURCE_LIMIT");}
    private static final class Cursor {
        final byte[]bytes;int position;Cursor(byte[]bytes){this.bytes=bytes;}
        void skip(long count){if(count<0||count>bytes.length-position)fail("ZIP_TRUNCATED");position+=(int)count;}
        long u32(){int start=position;skip(4);return (bytes[start]&255L)|((bytes[start+1]&255L)<<8)|((bytes[start+2]&255L)<<16)|((bytes[start+3]&255L)<<24);}
        int u16(){int start=position;skip(2);return(bytes[start]&255)|((bytes[start+1]&255)<<8);}
        void expect32(long expected){if(u32()!=expected)fail("ZIP_STRUCTURE");}void expect16(int expected){if(u16()!=expected)fail("ZIP_STRUCTURE");}
        void name(String expected){for(byte value:expected.getBytes(StandardCharsets.US_ASCII)){int start=position;skip(1);if(bytes[start]!=value)fail("ZIP_STRUCTURE");}}
    }
    private static final class Sink {
        final OutputStream output;final MessageDigest digest;long count;
        Sink(OutputStream output){this.output=output;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA256_UNAVAILABLE");}}
        void bytes(byte[]bytes)throws IOException{output.write(bytes);digest.update(bytes);count+=bytes.length;}
        void u16(int value)throws IOException{bytes(new byte[]{(byte)value,(byte)(value>>>8)});}void u32(long value)throws IOException{bytes(new byte[]{(byte)value,(byte)(value>>>8),(byte)(value>>>16),(byte)(value>>>24)});}
    }
}
