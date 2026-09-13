package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

class PrivacyWireTest {
    static Path scratch;
    static Path probe;
    static Path partialProbe;
    static int run(List<String> command) throws Exception {
        var builder=new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");
        var process=builder.start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"owned wire probe deadline");
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
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-privacy-wire-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        assertEquals(0,run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror",
            "-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-pthread",
            "-Isrc/main/c","src/main/c/privacy-wire.c","src/test/c/privacy-wire-probe.c","-o",probe.toString())));
        Files.setPosixFilePermissions(probe,PosixFilePermissions.fromString("rwx------"));
        partialProbe=scratch.resolve("partial-probe");
        assertEquals(0,run(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror",
            "-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-pthread",
            "-DES_WIRE_TEST_PARTIAL_SEND","-Wl,--wrap=send","-Isrc/main/c","src/main/c/privacy-wire.c",
            "src/test/c/privacy-wire-probe.c","-o",partialProbe.toString())));
        Files.setPosixFilePermissions(partialProbe,PosixFilePermissions.fromString("rwx------"));
    }
    void check(String mode) throws Exception { assertEquals(0,run(List.of(probe.toString(),mode)),mode); }
    @Test void independentCanonicalFixturesAndEveryTruncation() throws Exception {check("codec");}
    @Test void everyParentFrameSplitPreservesHandshake() throws Exception {check("parent-fragments");}
    @Test void everyChildFrameSplitPreservesHandshake() throws Exception {check("child-fragments");}
    @Test void reorderedMalformedTruncatedAndTrailingFramesRefuse() throws Exception {check("malformed");}
    @Test void wrongTupleOrProofNeverCompletes() throws Exception {check("correlation");}
    @Test void receivedAndTruncatedAncillaryDescriptorsAreClosed() throws Exception {check("ancillary");}
    @Test void abortAndPeerRefusalAreTerminal() throws Exception {check("terminals");}
    @Test void stalledReadUsesAbsoluteDeadline() throws Exception {check("deadline");}
    @Test void backpressureUsesSameAbsoluteDeadline() throws Exception {check("backpressure");}
    @Test void cancellationWakesWithoutConsumingBorrowedEvent() throws Exception {check("cancel");}
    @Test void interruptedPollingCannotRestartDeadline() throws Exception {check("eintr");}
    @Test void abortTerminatesBlockedChildWrite() throws Exception {check("abort-write");}
    @Test void parentRequiresEofAfterAckWithinSameDeadline() throws Exception {check("final-stall");}
    @Test void parentRefusesAnyPostAckBytes() throws Exception {check("final-trailing");}
    @Test void partialProgressCannotRenewDeadline() throws Exception {check("trickle");}
    @Test void positivePartialSocketWritesPreserveHandshake() throws Exception {
        assertEquals(0,run(List.of(partialProbe.toString(),"parent-fragments")));
    }
    @Test void invalidDescriptorAliasCannotCloseBorrowedCancellation() throws Exception {check("alias");}
    @AfterAll static void cleanup() throws Exception {
        if(probe!=null)Files.deleteIfExists(probe);
        if(partialProbe!=null)Files.deleteIfExists(partialProbe);
        if(scratch!=null)Files.deleteIfExists(scratch);
    }
}
