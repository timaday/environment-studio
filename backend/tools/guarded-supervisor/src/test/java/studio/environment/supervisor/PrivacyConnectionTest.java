package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

class PrivacyConnectionTest {
    static Path scratch;
    static Path probe;
    static int run(List<String> command) throws Exception {
        var builder=new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");
        var process=builder.start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"owned peer probe deadline");
            var output=process.getInputStream().readNBytes(8193);
            assertTrue(output.length<=8192,"bounded diagnostic output");
            String diagnostic=new String(output,java.nio.charset.StandardCharsets.US_ASCII);
            if(command.getFirst().equals("/usr/bin/cc"))assertEquals(0,process.exitValue(),"compiler setup: "+diagnostic);
            else if(process.exitValue()!=0)assertTrue(diagnostic.matches("(?:PROBE_ASSERT_LINE_[0-9]+\\n)*"),"unexpected diagnostic format");
            return process.exitValue();
        } finally {
            if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
        }
    }
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-privacy-connection-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        assertEquals(0,run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=close,--wrap=getsockopt,--wrap=recvmsg","-Isrc/main/c","src/main/c/privacy-listener.c","src/main/c/privacy-peer.c","src/main/c/privacy-wire.c","src/main/c/privacy-connection.c","src/test/c/privacy-connection-probe.c","-o",probe.toString())));
    }
    void check(String mode) throws Exception {assertEquals(0,run(List.of(probe.toString(),mode)),mode);}
    @Test void actualChildFragmentedPrepareHasLiveSameSocketIdentity() throws Exception {check("live");}
    @Test void malformedPrepareCannotCreateIdentityEvidence() throws Exception {check("malformed");}
    @Test void duplicateTruncatedAncillaryAndMissingPrepareRefuse() throws Exception {for(String mode:List.of("duplicate","truncated","ancillary","missing"))check(mode);}
    @Test void deadPeersCannotSupplyCurrentProcessEvidence() throws Exception {for(String mode:List.of("dead-before","post-dead","death-during-prepare"))check(mode);}
    @Test void originalCancellationAndDeadlineApplyAcrossComposition() throws Exception {for(String mode:List.of("cancel-before","cancel-ready","expired-read"))check(mode);}
    @Test void repeatedCallsCannotReplaceOwnersOrRepeatTheHandshake() throws Exception {for(String mode:List.of("wrong-token","reopen","repeat-prepare","null"))check(mode);}
    @Test void listenerFirstCleanupCannotCloseTransferredSocket() throws Exception {check("listener-first");}
    @Test void independentWireAndPinCleanupKeepUncertaintyAndNeverRetryNumbers() throws Exception {for(String mode:List.of("wire-close-uncertain","peer-close-uncertain","expired-close"))check(mode);}
    @Test void unsupportedPinsIdentityMismatchAndPostTransferSetupRefuseCleanly() throws Exception {for(String mode:List.of("unsupported","identity-mismatch","wire-setup"))check(mode);}
    @Test void identityOutputCannotOverwriteItsOwner() throws Exception {for(String mode:List.of("output-alias","output-listener-alias","read-alias"))check(mode);}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);}
}
