package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class IndependentPrivacyImageTest {
    static Path scratch, child, probe;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory("es-independent-image-");
        child=scratch.resolve("child");probe=scratch.resolve("probe");
        var flags=List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now");
        var command=new ArrayList<>(flags);
        command.addAll(List.of("-DINDEPENDENT_IMAGE_CHILD","src/test/c/independent-image-probe.c","-o",child.toString()));
        assertEquals(0,PrivacyPeerTest.run(command));
        Files.setPosixFilePermissions(child,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var header=new StringBuilder("static const unsigned char independent_expected[32]={");
        for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(child)))header.append(Byte.toUnsignedInt(b)).append(',');
        Files.writeString(scratch.resolve("independent-image-expected.h"),header.append("};\n"));
        command=new ArrayList<>(flags);
        command.addAll(List.of("-Wl,--wrap=open,--wrap=__open_2,--wrap=openat,--wrap=__openat_2,--wrap=fcntl,--wrap=fstat,--wrap=close","-Isrc/main/c","-I"+scratch,"src/main/c/privacy-peer.c","src/main/c/privacy-file.c","src/main/c/privacy-hash.c","src/main/c/privacy-image.c","src/test/c/independent-image-probe.c","-lcrypto","-o",probe.toString()));
        assertEquals(0,PrivacyPeerTest.run(command));
    }
    @Test void nativeChildWithUnusualCommUsesExactFileAndPreservesOffset() throws Exception { check("live"); }
    @Test void everyAcquiredDescriptorRequiresActualReadonlyCloexecNonPathEvidence() throws Exception {
        for(String target:List.of("root","directory","executable"))for(String fault:List.of("cloexec","write","path"))check(target+"-"+fault);
    }
    @Test void differentDeviceAloneRefusesAndExpectedDigestCannotOverlapOwner() throws Exception {check("device-only");check("expected-owner");}
    @Test void cleanupUncertaintyOutranksCancellationWithoutDrainingBorrowedControl() throws Exception {check("cleanup-cancel");}
    static void check(String mode) throws Exception {
        var builder=new ProcessBuilder(probe.toString(),child.toString(),mode).redirectErrorStream(true);
        builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");var process=builder.start();
        try {
            assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));
            String output=new String(process.getInputStream().readNBytes(8193),java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(output.isEmpty()||output.matches("(?:INDEPENDENT_IMAGE_[0-9]+\n)*"),"Unexpected owned probe diagnostic");
            assertEquals(0,process.exitValue(),output);
        } finally {if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}}
    }
    @AfterAll static void cleanup() throws Exception {
        if(scratch!=null){try(var files=Files.list(scratch)){for(var file:files.toList())Files.delete(file);}Files.delete(scratch);}
    }
}
