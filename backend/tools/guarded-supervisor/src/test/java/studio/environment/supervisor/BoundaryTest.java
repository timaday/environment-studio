package studio.environment.supervisor;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class BoundaryTest {
    @TempDir Path directory;
    @Test void argumentsAreClosedAndNeverCarryCredentials(){
        String[] valid={"apply","--package","/mock/archive.zip","--sha256","a".repeat(64),"--configuration","/mock/config.json","--destination","mock"};assertEquals("mock",Invocation.parse(valid).destination());
        for(String field:List.of("--password","--username","--command","--test-mode","--client")){String[] bad=valid.clone();bad[1]=field;assertThrows(Refusal.class,()->Invocation.parse(bad));}
        String[] duplicate=valid.clone();duplicate[3]="--package";assertThrows(Refusal.class,()->Invocation.parse(duplicate));
        for(String path:List.of("relative","/mock/../config","/mock/line\nbreak","/mock/\uD800")){String[] bad=valid.clone();bad[2]=path;assertThrows(Refusal.class,()->Invocation.parse(bad));}
    }
    @Test void singleReadRejectsSymlinksDirectoriesAndOversize()throws Exception {
        Path file=directory.resolve("data");Files.writeString(file,"original");Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));assertArrayEquals("original".getBytes(StandardCharsets.UTF_8),AdmittedFiles.read(file,8));assertThrows(Refusal.class,()->AdmittedFiles.read(file,7));
        Path link=directory.resolve("link");Files.createSymbolicLink(link,file);assertThrows(Refusal.class,()->AdmittedFiles.read(link,100));assertThrows(Refusal.class,()->AdmittedFiles.read(directory,100));
        byte[] admitted=AdmittedFiles.read(file,8);Files.writeString(file,"replaced");assertArrayEquals("original".getBytes(StandardCharsets.UTF_8),admitted);
    }
    @Test void configurationRejectsRawNumericSpellingDuplicateKeysAndNestedExtensions()throws Exception{
        String base=Files.readString(Path.of("../../../fixtures/guarded-supervisor-v1/configuration.json"));
        for(String invalid:List.of(base.replace("5432","5432.0"),base.replace("5432","5.432e3"),base.replace("5432","05432"),base.replace("\"port\": 5432","\"port\": 5432,\"port\": 5432"),base.replace("\"port\": 5432","\"port\": 5432,\"password\": \"canary\""),base+"{}",base.replace("db.example.invalid","db..invalid")))assertFalse(Configuration.accepts(invalid.getBytes(StandardCharsets.UTF_8)));
        assertFalse(Configuration.accepts(new byte[1_048_577]));
    }
    @Test void trustRefusesPrivateKeysMixedContentAndEmptyInput(){for(String input:List.of("", "-----BEGIN PRIVATE KEY-----\nYQ==\n-----END PRIVATE KEY-----\n", "-----BEGIN CERTIFICATE-----\nYQ==\n-----END CERTIFICATE-----\n"))assertThrows(Refusal.class,()->TrustCertificates.parse(input.getBytes(StandardCharsets.US_ASCII)));}
    @Test void publicCertificateBytesAreParsedOnceAndCloned()throws Exception{byte[] pem=Files.readAllBytes(Path.of("../../../fixtures/guarded-supervisor-v1/mock-ca.pem"));var trust=TrustCertificates.parse(pem);assertEquals(1,trust.certificates().size());pem[0]='!';assertEquals('-',trust.bytes()[0]);byte[] returned=trust.bytes();returned[0]='!';assertEquals('-',trust.bytes()[0]);byte[] mixed=(new String(trust.bytes(),StandardCharsets.US_ASCII)+"PRIVATE INPUT").getBytes(StandardCharsets.US_ASCII);assertThrows(Refusal.class,()->TrustCertificates.parse(mixed));}
    @Test void configurationCannotPopulateTheCompiledQualificationRegistry()throws Exception{Path config=directory.resolve("config.json");Files.copy(Path.of("../../../fixtures/guarded-supervisor-v1/configuration.json"),config);Files.setPosixFilePermissions(config,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));var invocation=new Invocation(directory.resolve("absent.zip"),"a".repeat(64),config,"invented-mock");assertEquals("RUNTIME_UNQUALIFIED",assertThrows(Refusal.class,()->Admission.admit(invocation,Admission.COMPILED_REGISTRY)).code);}
    @Test void untrustedWriteModesRefuseAndOwnedStickyDescendantsRemainBounded()throws Exception{
        Path parent=Files.createDirectory(directory.resolve("parent")),file=parent.resolve("file");Files.writeString(file,"mock");Files.setPosixFilePermissions(parent,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw----"));assertEquals("UNTRUSTED_FILE_WRITE",assertThrows(Refusal.class,()->AdmittedFiles.read(file,8)).code);
        Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));Files.setPosixFilePermissions(parent,java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));assertEquals("UNTRUSTED_FILE_WRITE",assertThrows(Refusal.class,()->AdmittedFiles.read(file,8)).code);
        Files.setAttribute(parent,"unix:mode",01777);assertArrayEquals("mock".getBytes(StandardCharsets.UTF_8),AdmittedFiles.read(file,8));Files.setPosixFilePermissions(parent,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
}
