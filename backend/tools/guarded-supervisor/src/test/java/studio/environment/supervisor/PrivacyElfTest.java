package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;
class PrivacyElfTest {
    static Path scratch,probe,executable,shared;
    static byte[] base;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-elf-native-");
        Files.setPosixFilePermissions(scratch,PosixFilePermissions.fromString("rwx------"));
        probe=scratch.resolve("probe");executable=scratch.resolve("mock-exec");shared=scratch.resolve("mock-shared");
        var bytes=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(new byte[]{127,'E','L','F',2,1,1,0,0,0,0,0,0,0,0,0});
        bytes.putShort((short)2).putShort((short)62).putInt(1).putLong(0x401000).putLong(64).putLong(0).putInt(0);
        bytes.putShort((short)64).putShort((short)56).putShort((short)3).putShort((short)0).putShort((short)0).putShort((short)0);
        program(bytes,1,4,0,0x400000,4096,4096,4096);
        program(bytes,1,5,4096,0x401000,4096,4096,4096);
        program(bytes,0x6474e551,6,0,0,0,0,16);base=bytes.array();
        var flags=List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-Wl,-z,relro,-z,now");
        for(boolean library:List.of(false,true)) {
            var command=new ArrayList<>(flags);
            command.addAll(library?List.of("-shared","-fPIC"):List.of("-no-pie"));
            command.addAll(List.of("-DES_ELF_MOCK","src/test/c/privacy-elf-probe.c","-o",(library?shared:executable).toString()));
            assertEquals(0,PrivacyPeerTest.run(command));
            Files.setPosixFilePermissions(library?shared:executable,PosixFilePermissions.fromString("rwx------"));
        }
        var command=new ArrayList<>(flags);command.addAll(List.of("-fPIE","-pie",
                "-Wl,--wrap=es_hash_file,--wrap=pread,--wrap=__pread_chk,--wrap=clock_gettime",
                "-Isrc/main/c","src/main/c/privacy-file.c","src/main/c/privacy-hash.c","src/main/c/privacy-elf.c",
                "src/test/c/privacy-elf-probe.c","-lcrypto","-o",probe.toString()));
        assertEquals(0,PrivacyPeerTest.run(command));
    }
    static void program(ByteBuffer b,int type,int flags,long offset,long address,long file,long memory,long align) {
        b.putInt(type).putInt(flags).putLong(offset).putLong(address).putLong(address).putLong(file).putLong(memory).putLong(align);
    }
    static ByteBuffer bytes() {return ByteBuffer.wrap(base.clone()).order(ByteOrder.LITTLE_ENDIAN);}
    static ByteBuffer allHeaders() {
        var b=bytes();Arrays.fill(b.array(),64,8192,(byte)0);b.putShort(56,(short)11);b.position(64);
        program(b,6,4,64,0x400040,616,616,8);
        program(b,3,4,800,0x400320,16,16,1);
        program(b,1,4,0,0x400000,4096,4096,4096);
        program(b,1,6,4096,0x401000,4096,4096,4096);
        program(b,2,6,1024,0x400400,16,16,8);
        program(b,7,4,1200,0x4004b0,8,16,8);
        program(b,4,4,1500,0,16,16,4);
        program(b,0x6474e550,4,1600,0x400640,32,32,4);
        program(b,0x6474e551,6,0,0,0,0,16);
        program(b,0x6474e552,4,4096,0x401000,128,128,1);
        program(b,0x6474e553,4,1600,0x400640,32,32,8);return b;
    }
    @Test void measuredInventedElfReturnsExactPhysicalLayoutWithoutMovingBorrowedOffset() throws Exception {check("basic",0,base);}
    @Test void actualOwnedCompilerExecutableAndSharedObjectAreStructurallySupported() throws Exception {
        check("actual-exec",0,Files.readAllBytes(executable));check("actual-shared",0,Files.readAllBytes(shared));
    }
    @Test void completeHeadersAndDeliberatelyUninterpretedSectionFieldsAreRetained() throws Exception {
        check("all-headers",0,allHeaders().array());
        check("opaque-dynamic",0,allHeaders().putLong(1024,22).putLong(1032,0).array());
        check("section-metadata",0,bytes().putLong(40,-1).putShort(58,(short)65535).putShort(60,(short)65534).putShort(62,(short)65533).array());
        check("gnu-osabi",0,bytes().put(7,(byte)3).array());
        var high=bytes().putShort(16,(short)3).putLong(24,0);
        high.putLong(80,Long.MIN_VALUE).putLong(88,0).putLong(136,Long.MIN_VALUE+4096).putLong(144,0);
        check("high-relative",0,high.array());
    }
    @Test void closedIdentificationAndHeaderWidthsRefuseWithoutChangingDigestMeaning() throws Exception {
        for(int offset:new int[]{0,4,5,6,7,8,9,15})check("ident-"+offset,9,bytes().put(offset,(byte)99).array());
        for(int offset:new int[]{16,18,52,54})check("header-"+offset,9,bytes().putShort(offset,(short)17).array());
        check("version",9,bytes().putInt(20,2).array());check("flags",9,bytes().putInt(48,1).array());
        check("phoff-low",9,bytes().putLong(32,63).array());check("phoff-wrap",9,bytes().putLong(32,-1).array());
        check("table-eof",9,bytes().putLong(32,8100).array());
        check("short-header",9,Arrays.copyOf(base,63));
    }
    @Test void exactProgramAndLoadBoundsRetainNullRecordsButDoNotResolveExtendedNumbers() throws Exception {
        var max=bytes().putShort(56,(short)128);max.putLong(64+127*56+16,-1);
        check("max-headers",0,max.array());
        check("phnum-over",6,bytes().putShort(56,(short)129).array());
        check("phnum-zero",9,bytes().putShort(56,(short)0).array());
        check("extended-phnum",9,bytes().putShort(56,(short)65535).array());
        for(int count:new int[]{32,33}) {
            var b=bytes();Arrays.fill(b.array(),64,8192,(byte)0);b.putShort(56,(short)count);b.position(64);
            for(int i=0;i<count;i++)program(b,1,4,0,0x400000+i*4096L,0,0,4096);
            check(count==32?"max-loads":"load-over",count==32?0:6,b.array());
        }
    }
    @Test void layoutArithmeticAndMemoryOverlapAreClosedWhileFileAndMetadataOverlapArePreserved() throws Exception {
        check("filesz-memory",9,bytes().putLong(64+40,4095).array());
        check("offset-bound",9,bytes().putLong(64+8,8193).array());
        check("file-end",9,bytes().putLong(64+8,4097).array());
        check("vaddr-wrap",9,bytes().putLong(64+16,-4095).array());
        check("paddr-wrap",9,bytes().putLong(64+24,-4095).array());
        check("align-power",9,bytes().putLong(64+48,3).array());
        check("align-congruence",9,bytes().putLong(64+16,0x400001).array());
        check("page-congruence",9,bytes().putLong(64+48,1).putLong(64+16,0x400001).array());
        check("load-memory-overlap",9,bytes().putLong(120+16,0x400000).array());
        check("load-order",9,bytes().putLong(120+16,0x3ff000).array());
        check("load-file-alias",0,bytes().putLong(120+8,0).array());
        var page=bytes().putLong(64+32,2048).putLong(64+40,2048);
        page.putLong(120+8,2048).putLong(120+16,0x400800).putLong(120+24,0x400800).putLong(120+32,2048).putLong(120+40,2048);
        check("rounded-page-overlap",0,page.array());
    }
    @Test void headerVocabularyPermissionsMultiplicityAndContainmentAreExact() throws Exception {
        for(int type:new int[]{5,8,0x6474e554,-1})check("unsupported-type-"+Integer.toUnsignedString(type),9,bytes().putInt(176,type).array());
        check("unknown-flags",9,bytes().putInt(68,12).array());
        check("wx-load",9,bytes().putInt(124,7).array());check("x-stack",9,bytes().putInt(180,1).array());
        check("stack-location",9,bytes().putLong(184,1).array());
        var duplicate=allHeaders();System.arraycopy(duplicate.array(),64+4*56,duplicate.array(),64+6*56,56);
        check("duplicate",9,duplicate.array());
        check("phdr-size",9,allHeaders().putLong(64+32,615).array());
        check("phdr-displacement",9,allHeaders().putLong(64+16,0x400041).array());
        check("interp-after-load",9,bytes().putInt(176,3).array());
        check("uncovered-dynamic",9,allHeaders().putLong(64+4*56+16,0x500400).array());
        check("tls-alignment",9,allHeaders().putLong(64+5*56+16,0x4004b1).array());
    }
    @Test void compiledAndRepeatedIdentityChecksRetainOriginalHashBudget() throws Exception {
        for(String mode:List.of("wrong-digest","change-after-first","second-device","second-inode","second-size"))check(mode,5,base);
        check("objects-exact",0,base);check("objects-over",6,base);
    }
    @Test void partialInterruptedAndFailedReadsHaveClosedResults() throws Exception {
        check("partial",0,base);check("eintr",0,base);check("read-error",7,base);check("read-eof",9,base);
    }
    @Test void nullAliasedAndForeignScopeArgumentsPreserveBorrowedOwners() throws Exception {
        for(String mode:List.of("null-file","null-hash","null-digest","null-output","output-file","output-hash","output-span","output-expected","digest-file","digest-hash","digest-span","owner-alias","foreign-cancel","foreign-deadline"))check(mode,1,base);
        check("cloexec",5,base);check("changed-mode",5,base);
    }
    @Test void finalControlsAndInjectedSharedCleanupUncertaintyNeverAdmit() throws Exception {
        check("cancel-before",3,base);check("cancel-final",3,base);
        check("deadline-before",4,base);check("deadline-final",4,base);
        check("hash-cleanup",8,base);check("cleanup-cancel",8,base);
    }
    static void check(String mode,int result,byte[] bytes) throws Exception {
        Path fixture=scratch.resolve("fixture-"+mode);Files.write(fixture,bytes);
        Files.setPosixFilePermissions(fixture,PosixFilePermissions.fromString("rw-------"));
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var builder=new ProcessBuilder(probe.toString(),fixture.toString(),mode,Integer.toString(result),sha).redirectErrorStream(true);
        builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");var process=builder.start();
        try {
            assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));
            String output=new String(process.getInputStream().readNBytes(8193),java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(output.isEmpty()||output.matches("(?:ELF_(?:ASSERT|RESULT)_[0-9]+\\n)*"),"Unexpected owned probe output");
            assertEquals(0,process.exitValue(),mode+":"+output);
        } finally {if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}}
    }
    @AfterAll static void cleanup() throws Exception {
        if(scratch!=null){try(var paths=Files.list(scratch)){for(var path:paths.toList())Files.delete(path);}Files.delete(scratch);}
    }
}
