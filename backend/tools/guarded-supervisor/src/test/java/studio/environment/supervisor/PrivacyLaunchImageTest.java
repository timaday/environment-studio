package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;

class PrivacyLaunchImageTest {
    static Path scratch,probe,library;
    static String digest;
    static final List<String> SOURCES=List.of("peer","fork","listener","wire","connection","root","launch","maps","file","hash","image","elf");
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-owned-image-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");
        var command=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-fstack-protector-strong","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Wl,-z,relro,-z,now","-Isrc/main/c","-Wl,--wrap=es_hash_open,--wrap=es_hash_close,--wrap=es_file_close,--wrap=es_image_check,--wrap=es_elf_check"));
        for(String name:SOURCES)command.add("src/main/c/privacy-"+name+".c");
        command.addAll(List.of("src/test/c/privacy-launch-image-probe.c","-lcrypto","-o",probe.toString()));
        assertEquals(0,PrivacyConnectionTest.run(command));
        Files.setPosixFilePermissions(probe,PosixFilePermissions.fromString("rwx------"));
        digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(probe)));
    }
    @Test void actualCorrelatedConstructorHeldElfMatchesIndependentCompiledBytes() throws Exception {assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),digest)));}
    @Test void originalCountersAndInitializedFailedHashNeverResetOrLeak() throws Exception {modes("count507","count508","bytes-limit","partial-hash","hash-close");}
    @Test void failuresAndCleanupUncertaintyRemainIndependent() throws Exception {modes("digest","digest-close","close-uncertain","close-cancel","image-cancel","image-death","scope","double","wrong-thread");}
    @Test void sameBytesAtAnotherInodeAndUntrustedInstallationRefuse() throws Exception {
        Path other=scratch.resolve("other"),link=scratch.resolve("link");
        try {
            assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),digest,"missing",other.toString())));
            Files.copy(probe,other);Files.setPosixFilePermissions(other,PosixFilePermissions.fromString("rwx------"));
            assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),digest,"different-inode",other.toString())));
            Files.setPosixFilePermissions(other,PosixFilePermissions.fromString("rwxrwx---"));
            assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),digest,"writable",other.toString())));
            Files.createSymbolicLink(link,probe);
            assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),digest,"symlink",link.toString())));
        } finally {Files.deleteIfExists(link);Files.deleteIfExists(other);}
    }
    static void modes(String... modes) throws Exception {for(String mode:modes)assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),digest,mode)),mode);}
    @Test void invalidPreflightAndHeldCleanupNeverPublishOrReleaseActiveOwners() throws Exception {modes("invalid","held","shortened","image-deadline","close-deadline","tail-bytes","empty-dynamic","duplicate-interpreter");}
    @Test void actualStaticConstructorRootCannotBecomeDynamicElfEvidence() throws Exception {
        Path source=scratch.resolve("static.c"),binary=scratch.resolve("static");
        String probeSource=Files.readString(Path.of("src/test/c/privacy-launch-image-probe.c"));
        String constructor=probeSource.substring(probeSource.indexOf("__attribute__((constructor))"),probeSource.indexOf("static void cleanup"));
        try {
            Files.writeString(source,"#define _GNU_SOURCE\n#include <stdlib.h>\n#include <string.h>\n#include <sys/socket.h>\n#include <sys/un.h>\n#include <unistd.h>\n"+constructor+"\nint main(void){return 0;}\n");
            assertEquals(0,PrivacyConnectionTest.run(List.of("cc","-O2","-Wall","-Wextra","-Werror","-static",source.toString(),"-o",binary.toString())));
            Files.setPosixFilePermissions(binary,PosixFilePermissions.fromString("rwx------"));
            String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(binary)));
            assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),expected,"static",binary.toString())));
        } finally {Files.deleteIfExists(source);Files.deleteIfExists(binary);}
    }
    @Test void actualJavaOwnerForkAndRefusedSpawnShareExactNativeImageLifetime() throws Exception {
        library=scratch.resolve("libimageowner.so");
        var cmd=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-D_FORTIFY_SOURCE=3","-fPIC","-shared","-Isrc/main/c","-I"+System.getProperty("java.home")+"/include","-I"+System.getProperty("java.home")+"/include/linux"));
        for(String name:SOURCES)cmd.add("src/main/c/privacy-"+name+".c");
        cmd.addAll(List.of("src/main/c/privacy-controls.c","src/test/c/privacy-launch-image-jni-probe.c","-lcrypto","-o",library.toString()));
        assertEquals(0,PrivacyConnectionTest.run(cmd));
        String classes=Path.of("target/test-classes").toAbsolutePath()+java.io.File.pathSeparator+Path.of("target/classes").toAbsolutePath();
        for(String mode:List.of("FORK","POSIX_SPAWN","FAILED_EXEC"))assertEquals(0,PrivacyConnectionTest.run(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Djdk.lang.Process.launchMechanism="+(mode.equals("POSIX_SPAWN")?mode:"FORK"),"-cp",classes,PrivacyLaunchImageProbe.class.getName(),library.toString(),probe.toString(),digest,mode)),mode);
    }
    @AfterAll static void cleanup() throws Exception {if(library!=null)Files.deleteIfExists(library);if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);}
}
