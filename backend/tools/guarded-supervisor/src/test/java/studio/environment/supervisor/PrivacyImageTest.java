package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
class PrivacyImageTest {
    static Path scratch, child, probe;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory("es-image-native-");child=scratch.resolve("child");probe=scratch.resolve("probe");
        var flags=List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now");
        var command=new ArrayList<>(flags);command.addAll(List.of("-DES_IMAGE_CHILD","src/test/c/privacy-image-probe.c","-o",child.toString()));
        assertEquals(0,PrivacyPeerTest.run(command));
        Files.setPosixFilePermissions(child,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(child));
        var header=new StringBuilder("static const unsigned char image_expected[32]={");
        for(byte value:digest)header.append(Byte.toUnsignedInt(value)).append(',');
        header.append("};\n");Files.writeString(scratch.resolve("image-expected.h"),header);
        command=new ArrayList<>(flags);command.addAll(List.of("-Wl,--wrap=open,--wrap=__open_2,--wrap=openat,--wrap=__openat_2,--wrap=close,--wrap=fstat,--wrap=fstatfs,--wrap=clock_gettime,--wrap=EVP_DigestUpdate","-Isrc/main/c","-I"+scratch,"src/main/c/privacy-peer.c","src/main/c/privacy-file.c","src/main/c/privacy-hash.c","src/main/c/privacy-image.c","src/test/c/privacy-image-probe.c","-lcrypto","-o",probe.toString()));
        assertEquals(0,PrivacyPeerTest.run(command));
    }
    @Test void actualSocketBoundInventedExecutableMatchesItsCompiledDigest() throws Exception {check("live");}
    @Test void sameBytesNeedExactInodeAndCompiledDigest() throws Exception {checks("wrong-inode","wrong-digest");}
    @Test void retainedFileSurvivesPathReplacementButSecondExecMustMatch() throws Exception {checks("replace-path","exec-second");}
    @Test void deadProcessRefusesBeforeDuringAndAfterAcquisition() throws Exception {checks("death-before","death-second","death-final");}
    @Test void originalScopeAndDescriptorAliasesRefuseWithoutOwnerMutation() throws Exception {checks("foreign-hash-control","foreign-file-control","foreign-hash-deadline","foreign-file-deadline","descriptor-alias");}
    @Test void invalidPointerAndOutputAliasesPreserveOwners() throws Exception {checks("null-peer","null-file","null-hash","null-digest","null-output","owner-alias","output-owner","output-expected");}
    @Test void cancellationAndDeadlineRemainOriginalThroughFinalClose() throws Exception {checks("cancel-before","cancel-final","deadline-before","deadline-final");}
    @Test void verifiedProcfsAndMetadataFailuresNeverAdmit() throws Exception {checks("proc-filesystem","pid-filesystem","stat-fail","proc-open-fail","exe-open-fail","size-mismatch","crypto-fail");}
    @Test void sharedObjectAndByteBudgetsAreNotReset() throws Exception {checks("objects-exact","objects-over","bytes-over");}
    @Test void uncertainTemporaryCloseOverridesMismatchAndDoesNotCloseReusedNumber() throws Exception {checks("close-fault","mismatch-close-fault");}
    static void checks(String... modes) throws Exception {for(String mode:modes)check(mode);}
    static void check(String mode) throws Exception {
        var builder=new ProcessBuilder(probe.toString(),child.toString(),mode).redirectErrorStream(true);
        builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");var process=builder.start();
        try {
            assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));
            String output=new String(process.getInputStream().readNBytes(8193),java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(output.isEmpty()||output.matches("(?:IMAGE_(?:ASSERT|RESULT)_[0-9]+\n)*"),"Unexpected owned probe output");
            assertEquals(0,process.exitValue(),output);
        } finally {if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}}
    }
    @AfterAll static void cleanup() throws Exception {
        if(scratch!=null){try(var files=Files.list(scratch)){for(var file:files.toList())Files.delete(file);}Files.delete(scratch);}
    }
}
