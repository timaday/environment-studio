package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
class PrivacyScriptTest {
    static Path scratch, child, longChild, overChild, aliasChild, probe;
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-script-native-");
        Files.setPosixFilePermissions(scratch,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        child=scratch.resolve("interpreter");probe=scratch.resolve("probe");
        var flags=List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now");
        var command=new ArrayList<>(flags);command.addAll(List.of("-DES_SCRIPT_CHILD","src/test/c/privacy-script-probe.c","-o",child.toString()));
        assertEquals(0,PrivacyPeerTest.run(command));
        Files.setPosixFilePermissions(child,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        longChild=scratch.resolve("i".repeat(252-scratch.toString().length()-1));
        overChild=scratch.resolve("j".repeat(253-scratch.toString().length()-1));
        Files.copy(child,longChild);Files.copy(child,overChild);aliasChild=scratch.resolve("literal-alias");Files.createSymbolicLink(aliasChild,child);
        Files.setPosixFilePermissions(longChild,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(overChild,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var header=new StringBuilder();array(header,"interpreter_expected",Files.readAllBytes(child));
        array(header,"script_plain_expected",("#!"+child+"\n# independently invented script\n").getBytes(StandardCharsets.UTF_8));
        array(header,"script_option_expected",("#!"+child+" -e\n# independently invented script\n").getBytes(StandardCharsets.UTF_8));
        array(header,"script_long_expected",("#!"+longChild+"\n# independently invented script\n").getBytes(StandardCharsets.UTF_8));
        array(header,"script_alias_expected",("#!"+aliasChild+"\n# independently invented script\n").getBytes(StandardCharsets.UTF_8));
        Files.writeString(scratch.resolve("script-expected.h"),header);
        command=new ArrayList<>(flags);command.addAll(List.of("-Wl,--wrap=open,--wrap=__open_2,--wrap=openat,--wrap=__openat_2,--wrap=close,--wrap=pread,--wrap=__pread_chk,--wrap=clock_gettime","-Isrc/main/c","-I"+scratch,"src/main/c/privacy-peer.c","src/main/c/privacy-file.c","src/main/c/privacy-hash.c","src/main/c/privacy-image.c","src/main/c/privacy-arguments.c","src/main/c/privacy-script.c","src/test/c/privacy-script-probe.c","-lcrypto","-o",probe.toString()));
        assertEquals(0,PrivacyPeerTest.run(command));
    }
    static void array(StringBuilder out,String name,byte[] bytes) throws Exception {
        out.append("static const unsigned char ").append(name).append("[32]={");
        for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))out.append(Byte.toUnsignedInt(value)).append(',');
        out.append("};\n");
    }
    @Test void actualKernelScriptAndInterpreterMatchCompiledBytes() throws Exception {check("live");}
    @Test void exactOptionalFlagAndMaximumShebangHaveActualKernelVectors() throws Exception {checks("option","shebang-exact","shebang-over","literal-alias");}
    @Test void exactScriptArgumentAndWholeVectorBoundsAreIndependent() throws Exception {checks("path-exact","path-over","arg-exact","arg-over","count-exact","count-over","bytes-exact","bytes-over");}
    @Test void scriptContentPathAndInterpreterDigestsMustAllMatch() throws Exception {checks("wrong-script-digest","wrong-interpreter-digest","wrong-script-inode","wrong-interpreter-inode","path-substitution","changed-header","changed-trust");}
    @Test void fixedShapeRefusesExtraOrChangedObservedArguments() throws Exception {checks("extra-argument","bad-index","bad-option","header-cr","header-tab","args-change-second","args-change-final");}
    @Test void boundedPrefixHandlesFragmentationAndFaults() throws Exception {checks("prefix-eintr","prefix-partial","prefix-io","prefix-eof");}
    @Test void originalScopesAndDescriptorSeparationAreMandatory() throws Exception {checks("script-control","interpreter-control","hash-control","script-deadline","interpreter-deadline","hash-deadline","descriptor-alias");}
    @Test void nullAndOverlappingInputsCannotMutateBorrowedOwners() throws Exception {checks("null-peer","null-script","null-interpreter","null-hash","null-expected","null-output","output-owner","output-arguments","output-expected","expected-owner","arguments-owner","null-path","overflow-span");}
    @Test void deadCancelledAndExpiredOperationsRefuseThroughFinalCleanup() throws Exception {checks("death-before","death-final","cancel-before","cancel-final","deadline-before","deadline-final");}
    @Test void originalHashOccurrenceBudgetIncludesAllFiveMeasurements() throws Exception {checks("objects-exact","objects-over");}
    @Test void uncertainCloseHasPriorityAndNeverClosesReusedDescriptor() throws Exception {checks("close-fault","close-cancel");}
    static void checks(String... modes) throws Exception {for(String mode:modes)check(mode);}
    static void check(String mode) throws Exception {
        Path invoked=mode.equals("shebang-exact")?longChild:mode.equals("shebang-over")?overChild:mode.equals("literal-alias")?aliasChild:child;
        var builder=new ProcessBuilder(probe.toString(),invoked.toString(),mode,(mode.equals("literal-alias")?child:invoked).toString()).redirectErrorStream(true);
        builder.environment().clear();builder.environment().put("PATH","/usr/bin:/bin");var process=builder.start();
        try {
            assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));
            String output=new String(process.getInputStream().readNBytes(8193),StandardCharsets.US_ASCII);
            assertTrue(output.isEmpty()||output.matches("(?:SCRIPT_(?:ASSERT|RESULT)_[0-9]+\n)*"),"Unexpected owned probe output");
            assertEquals(0,process.exitValue(),output);
        } finally {if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}}
    }
    @AfterAll static void cleanup() throws Exception {
        if(scratch!=null){try(var files=Files.list(scratch)){for(var file:files.toList())Files.delete(file);}Files.delete(scratch);}
    }
}
