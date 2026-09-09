package studio.environment.supervisor;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;

/** New owned mock executables and independent root/hash-lifetime assertions. */
class IndependentPrivacyLaunchImageTest {
    static Path scratch,probe,source;
    static final List<Path> children=new ArrayList<>();
    @BeforeAll static void compile() throws Exception {
        scratch=Files.createTempDirectory(Path.of("/tmp"),"es-image-independent-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        probe=scratch.resolve("probe");source=scratch.resolve("child.c");
        var command=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror","-pthread","-D_FORTIFY_SOURCE=3","-fPIE","-pie","-Isrc/main/c","-Wl,--wrap=es_hash_open,--wrap=es_hash_close"));
        for(String name:List.of("peer","fork","listener","wire","connection","root","launch","maps","file","hash","image","elf"))command.add("src/main/c/privacy-"+name+".c");
        command.addAll(List.of("src/test/c/independent-privacy-launch-image-probe.c","-lcrypto","-o",probe.toString()));
        assertEquals(0,PrivacyConnectionTest.run(command));
        Files.writeString(source,"""
                #define _GNU_SOURCE
                #include <stdlib.h>
                #include <string.h>
                #include <sys/socket.h>
                #include <sys/un.h>
                #include <unistd.h>
                __attribute__((constructor)) static void pause_before_main(void){
                    const char *path=getenv("ES_INDEPENDENT_ENDPOINT");if(!path)_exit(51);
                    struct sockaddr_un address={.sun_family=AF_UNIX};
                    if(strlen(path)>=sizeof(address.sun_path))_exit(52);
                    memcpy(address.sun_path,path,strlen(path)+1);
                    int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
                    if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof(address)))_exit(53);
                    const unsigned char frame[12]={'E','S','P','R','V','0','0','1',1,0,0,0};
                    if(write(fd,frame,sizeof(frame))!=sizeof(frame))_exit(54);
                    char forbidden;if(read(fd,&forbidden,1)!=0)_exit(55);
                    if(close(fd))_exit(56);
                }
                int main(void){return 0;}
                """);
    }
    static void exercise(String name,List<String> flags,String elfType,String mode) throws Exception {
        Path child=scratch.resolve(name);children.add(child);
        var command=new ArrayList<>(List.of("/usr/bin/cc","-std=c17","-O2","-Wall","-Wextra","-Werror"));
        command.addAll(flags);command.addAll(List.of(source.toString(),"-o",child.toString()));
        assertEquals(0,PrivacyConnectionTest.run(command));
        Files.setPosixFilePermissions(child,PosixFilePermissions.fromString("rwx------"));
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(child)));
        assertEquals(0,PrivacyConnectionTest.run(List.of(probe.toString(),child.toString(),digest,elfType,mode)));
    }
    @Test void actualDynamicExecAndPieUseTheOriginalShorterStartupScope() throws Exception {
        exercise("dynamic-exec",List.of("-fno-pie","-no-pie"),"2","actual");
        exercise("dynamic-pie",List.of("-fPIE","-pie"),"3","actual");
    }
    @Test void actualStaticPieCannotSubstituteForADynamicRoot() throws Exception {
        exercise("static-pie",List.of("-static-pie"),"3","static");
    }
    @Test void hashSettlementRetainsItsBorrowedCancelDescriptorAcrossShortenedConcurrentClose() throws Exception {
        exercise("held",List.of("-fPIE","-pie"),"3","held-hash");
    }
    @AfterAll static void cleanup() throws Exception {
        for(Path child:children)Files.deleteIfExists(child);
        if(source!=null)Files.deleteIfExists(source);if(probe!=null)Files.deleteIfExists(probe);if(scratch!=null)Files.deleteIfExists(scratch);
    }
}
