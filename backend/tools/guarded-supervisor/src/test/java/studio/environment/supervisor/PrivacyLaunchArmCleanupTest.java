package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Invented schedules exercise production ownership; no platform substitution. */
class PrivacyLaunchArmCleanupTest {
    static Path directory, probe;
    @BeforeAll static void compile() throws Exception {
        directory=Files.createTempDirectory("es-arm-cleanup-");
        probe=directory.resolve("probe");
        var command=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Wl,--wrap=es_root_disarm","-Isrc/main/c"));
        for(String name:List.of("peer","fork","listener","wire","connection","root","launch","maps","file","hash","image","elf"))command.add("src/main/c/privacy-"+name+".c");
        command.addAll(List.of("src/test/c/privacy-launch-arm-cleanup-probe.c","-lcrypto","-o",probe.toString()));
        assertEquals(0,PrivacyConnectionTest.run(command));
    }
    void check(String mode) throws Exception {assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),mode)),mode);}
    @Test void refusedContenderClosesCompletelyWithoutAffectingAcquiredWindow() throws Exception {check("contention");}
    @Test void refusedContenderCanCloseBeforeItsFinallyDisarmRefuses() throws Exception {check("close-first");}
    @Test void acquiredWindowWaitsForDisarmAndRetainsExpiredCleanup() throws Exception {check("acquired-pending");}
    @Test void explicitCleanupFailureRemainsUncertainEvenWithoutAcquiredWindow() throws Exception {check("unowned-cleanup-fault");}
    @Test void arbitraryDisarmFailureCannotClaimNoOwnership() throws Exception {check("disarm-fault");}
    @AfterAll static void cleanup() throws Exception {if(probe!=null)Files.deleteIfExists(probe);if(directory!=null)Files.deleteIfExists(directory);}
}
