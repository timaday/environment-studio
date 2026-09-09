package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;

/** Independent byte-built ELF cases and original-control probes. Never a runtime admission test. */
class IndependentPrivacyElfTest {
    static Path directory,probe;
    @BeforeAll static void compile() throws Exception {
        directory=Files.createTempDirectory(Path.of("/tmp"),"es-independent-elf-");Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));probe=directory.resolve("probe");
        assertEquals(0,PrivacyPeerTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=es_hash_file,--wrap=pread,--wrap=__pread_chk","-Isrc/main/c","src/main/c/privacy-file.c","src/main/c/privacy-hash.c","src/main/c/privacy-elf.c","src/test/c/independent-privacy-elf-probe.c","-lcrypto","-o",probe.toString())));
    }
    static ByteBuffer fixture() {
        var bytes=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(new byte[]{127,69,76,70,2,1,1,0,0,0,0,0,0,0,0,0});
        bytes.putShort((short)3).putShort((short)62).putInt(1).putLong(0).putLong(64).putLong(0).putInt(0);
        bytes.putShort((short)64).putShort((short)56).putShort((short)4).putShort((short)0).putShort((short)0).putShort((short)0);
        record(bytes,0,1,4,0,0x400000,4096,4096,4096);
        record(bytes,1,1,6,4096,0x401000,4096,8192,4096);
        record(bytes,2,7,4,6144,0x401800,2048,6144,8);
        record(bytes,3,0,0,0,0,0,0,0);return bytes;
    }
    static void record(ByteBuffer b,int index,int type,int flags,long offset,long address,long file,long memory,long align) {
        b.position(64+56*index);b.putInt(type).putInt(flags).putLong(offset).putLong(address).putLong(0).putLong(file).putLong(memory).putLong(align);
    }
    static void check(ByteBuffer bytes,int mode,int want) throws Exception {
        Path file=Files.createTempFile(directory,"fixture-",".elf");Files.write(file,bytes.array());Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array()));
        var builder=new ProcessBuilder(probe.toString(),file.toString(),Integer.toString(mode),Integer.toString(want),sha).redirectErrorStream(true);
        builder.environment().clear();var process=builder.start();
        try {assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));String output=new String(process.getInputStream().readNBytes(8193),java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(output.isEmpty()||output.matches("(?:INDEPENDENT_ELF_[0-9]+\\n)*"));assertEquals(0,process.exitValue(),output);
        }finally{if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}}
    }
    @Test void tlsFileAndBssMustFitTheSameLoadWithTheSameDisplacement() throws Exception {
        check(fixture(),0,0);
        check(fixture().putLong(64+2*56+40,6145),0,9);
        check(fixture().putLong(64+2*56+8,6136),0,9);
        var spans=fixture();record(spans,2,7,4,2048,0x400800,4096,4096,8);check(spans,0,9);
        var note=fixture();record(note,2,4,4,6144,0x900000,2048,2048,4);check(note,0,0);
        note.putInt(64+2*56,2);check(note,0,9);
    }
    @Test void emptyLoadCannotHideOverlapWithEarlierOccupiedMemoryAndNullFieldsAreOpaque() throws Exception {
        var b=fixture();record(b,0,1,4,0,0x400000,4096,8192,4096);record(b,1,1,4,0,0x401000,0,0,4096);
        record(b,2,1,4,4096,0x401000,4096,4096,4096);check(b,0,9);
        record(b,2,1,4,4096,0x402000,4096,4096,4096);check(b,0,0);
        record(b,3,0,-1,-1,-1,-1,-1,3);check(b,0,0);
    }
    @Test void firstLayoutReadCancellationAndFirstHashCleanupDominatePartialEvidence() throws Exception {
        check(fixture(),1,3);check(fixture(),2,8);
    }
    @AfterAll static void cleanup() throws Exception {
        if(directory!=null){try(var paths=Files.list(directory)){for(var file:paths.toList())Files.delete(file);}Files.delete(directory);}
    }
}
