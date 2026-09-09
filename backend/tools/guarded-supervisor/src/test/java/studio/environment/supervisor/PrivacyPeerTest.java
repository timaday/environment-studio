package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

class PrivacyPeerTest {
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
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-privacy-peer-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        assertEquals(0,run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=getsockopt,--wrap=open,--wrap=read,--wrap=close","-Isrc/main/c","src/main/c/privacy-peer.c","src/test/c/privacy-peer-probe.c","-o",probe.toString())));
    }
    void check(String mode) throws Exception {assertEquals(0,run(List.of(probe.toString(),mode)),mode);}
    @Test void actualConnectingChildIsPinned() throws Exception {check("live");}
    @Test void exitedConnectingChildRefuses() throws Exception {check("dead");}
    @Test void pinnedChildDeathWipesIdentity() throws Exception {check("exit");}
    @Test void originalDeadlineRefuses() throws Exception {check("deadline");}
    @Test void cancellationHasPriorityAndRemainsBorrowed() throws Exception {check("cancel");}
    @Test void closeOnceCannotCloseReusedDescriptor() throws Exception {check("reuse");}
    @Test void closedPinCannotReturnEmptySuccess() throws Exception {check("closed-read");}
    @Test void uncertainCloseIsStickyAndNeverRetried() throws Exception {check("uncertain-close");}
    @Test void unsupportedKernelHasNoNumericFallback() throws Exception {check("unsupported");}
    @Test void malformedKernelAndProcResultsRefuseAndClose() throws Exception {for(String mode:List.of("short-option","no-cloexec","wrong-uid","bad-fdinfo","oversized"))check(mode);}
    @Test void deathDuringIdentityReadsRefuses() throws Exception {check("during-read");}
    @Test void procIdentityAllowsLegalCommDelimiters() throws Exception {check("comm");}
    @Test void actualPartialProcReadsPreserveIdentity() throws Exception {check("partial");}
    @Test void changedStartIdentityCannotReplacePinnedProcess() throws Exception {check("start-mismatch");}
    @Test void inheritedSocketIdentifiesCreatorNotLaterChild() throws Exception {check("inherited");}
    @Test void invalidAliasAndReinitializationPreserveDescriptorOwners() throws Exception {for(String mode:List.of("fd-alias","short-cred","reinit"))check(mode);}
    @Test void callerCannotExtendTheMaximumLaunchAllowance() throws Exception {for(String mode:List.of("excess-deadline","long-deadline","max-deadline"))check(mode);}
    @Test void missingOutputTerminatesLivePinWithoutClosingBorrowedDescriptors() throws Exception {check("null-output");}
    @Test void adoptedKernelPinTransfersOnceAndUsesExistingIdentityChecks() throws Exception {for(String mode:List.of("adopt-live","adopt-cancel","adopt-dead","adopt-cloexec","adopt-mismatch","adopt-nonpin","adopt-start","adopt-alias"))check(mode);}
    @Test void adoptionRejectsForeignMetadataAndRetainsUntransferredPins() throws Exception {for(String mode:List.of("adopt-uid","adopt-gid","adopt-reinit","adopt-null"))check(mode);}
    @Test void transferredPinCloseUncertaintyCannotRetryReusedNumber() throws Exception {check("adopt-uncertain");}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);}
}
