package studio.environment.supervisor;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

class TerminalHelperTest {
    Path scratch;
    @BeforeEach void ownedTrustedScratch()throws Exception {scratch=Files.createTempDirectory(Path.of("/tmp"),"es-terminal-helper-",java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));}
    @AfterEach void removeOwnedScratch()throws Exception {Files.deleteIfExists(scratch.resolve("terminal-control"));Files.deleteIfExists(scratch);}
    @Test void boundedNativeTerminalEntryPreservesExactState()throws Exception{
        Path helper=scratch.resolve("terminal-control");
        var compiler=new ProcessBuilder("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","src/main/c/terminal-control.c","-o",helper.toString());compiler.environment().clear();compiler.environment().putAll(Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C"));var compile=compiler.inheritIO().start();assertTrue(compile.waitFor(20,TimeUnit.SECONDS));assertEquals(0,compile.exitValue());Files.setPosixFilePermissions(helper,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var run=new ProcessBuilder("/usr/bin/python3","src/test/resources/terminal-helper-probe.py",helper.toString()).redirectErrorStream(true).start();assertTrue(run.waitFor(20,TimeUnit.SECONDS));byte[] output=run.getInputStream().readNBytes(8193);assertTrue(output.length<=8192);assertEquals(0,run.exitValue(),"Independent PTY helper behavior failed");
    }
    @Test void javaOwnsActualEntryFallbackShutdownAndRestorationDeadline()throws Exception {
        Path helper=scratch.resolve("terminal-control");
        var compiler=new ProcessBuilder("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","src/main/c/terminal-control.c","-o",helper.toString());
        compiler.environment().clear();compiler.environment().putAll(Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C"));var compile=compiler.inheritIO().start();assertTrue(compile.waitFor(20,TimeUnit.SECONDS));assertEquals(0,compile.exitValue());Files.setPosixFilePermissions(helper,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var wide=new ProcessBuilder("/usr/bin/python3","src/test/resources/terminal-helper-extended.py",helper.toString()).redirectErrorStream(true).start();
        try {assertTrue(wide.waitFor(10,TimeUnit.SECONDS));assertEquals(0,wide.exitValue(),"Independent wide/foreign PTY checks failed");}finally{if(wide.isAlive()){wide.descendants().forEach(ProcessHandle::destroyForcibly);wide.destroyForcibly();}}
        var run=new ProcessBuilder("/usr/bin/python3","src/test/resources/terminal-console-probe.py",Path.of(System.getProperty("java.home"),"bin/java").toString(),System.getProperty("java.class.path"),helper.toString()).redirectErrorStream(true).start();
        try {assertTrue(run.waitFor(30,TimeUnit.SECONDS));byte[] output=run.getInputStream().readNBytes(8193);assertTrue(output.length<=8192);assertEquals(0,run.exitValue(),"Independent Java PTY behavior failed");}
        finally {if(run.isAlive()){run.descendants().forEach(ProcessHandle::destroyForcibly);run.destroyForcibly();}}
    }
}
