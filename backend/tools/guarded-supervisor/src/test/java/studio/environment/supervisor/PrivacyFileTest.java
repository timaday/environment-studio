package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;

class PrivacyFileTest {
    static Path scratch, probe;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-native-file-");probe=scratch.resolve("probe");
        assertEquals(0,PrivacyPeerTest.run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror",
            "-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now",
            "-Wl,--wrap=fstat,--wrap=fstatat,--wrap=fstatfs,--wrap=fgetxattr,--wrap=openat,--wrap=__openat_2,--wrap=close,--wrap=clock_gettime",
            "-Isrc/main/c","src/main/c/privacy-file.c","src/main/c/privacy-hash.c","src/test/c/privacy-file-probe.c","-lcrypto","-o",probe.toString())));
    }
    @Test void trustedOpenedFileFeedsActualBoundedHashingWithoutReopeningPath() throws Exception {
        check("live");
    }
    void check(String... modes) throws Exception {
        for(String mode:modes) {
            var builder=new ProcessBuilder(probe.toString(),mode).redirectErrorStream(true);
            builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");
            var child=builder.start();
            try {
                assertTrue(child.waitFor(20,java.util.concurrent.TimeUnit.SECONDS),"Owned native file probe deadline");
                byte[] output=child.getInputStream().readNBytes(8193);assertTrue(output.length<=8192);
                String diagnostic=new String(output,java.nio.charset.StandardCharsets.US_ASCII);
                assertTrue(diagnostic.isEmpty()||diagnostic.matches("(?:FILE_ASSERT_[0-9]+\n)*"),"Unexpected probe diagnostic");
                assertEquals(0,child.exitValue(),mode+" "+diagnostic);
            } finally {
                if(child.isAlive()){child.destroyForcibly();assertTrue(child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}
            }
        }
    }
    @Test void retainedDescriptorSurvivesPathReplacementAndSupportsExactUnicode() throws Exception {check("replace-after-open","unicode");}
    @Test void malformedPathsRefuseBeforeOwnershipChanges() throws Exception {check("relative","empty-component","dot","dotdot","trailing","root","nul","lf","cr","overlong-utf8","surrogate","invalid-continuation","too-long","null-path","null-owner");}
    @Test void symbolicLinksNeverBecomeAnAdmittedFile() throws Exception {check("leaf-symlink","ancestor-symlink","symlink-race");}
    @Test void untrustedWriteAuthorityAndOwnersRefuse() throws Exception {check("write-file","write-directory","owner-file","owner-directory");}
    @Test void stickyAncestorProtectsTheFollowingOwnedComponent() throws Exception {check("sticky-directory");}
    @Test void specialFilesAndPrivilegeEvidenceRefuse() throws Exception {check("fifo","directory","setuid","setgid","capability","capability-unknown");}
    @Test void missingFilesystemAndSyscallEvidenceNeverPasses() throws Exception {check("filesystem","stat-fail","lookup-fail","open-fail","clock-fail");}
    @Test void originalCancellationRemainsBorrowedThroughFinalClose() throws Exception {check("cancel-open","cancel-lookup","cancel-capability","cancel-close");}
    @Test void originalDeadlinesNeverRenewDuringTraversal() throws Exception {check("deadline-open","deadline-close","long-open");}
    @Test void uncertainCloseIsStickyWithoutClosingReusedDescriptorNumbers() throws Exception {check("close-fault","leaf-close-fault");}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);}
}
