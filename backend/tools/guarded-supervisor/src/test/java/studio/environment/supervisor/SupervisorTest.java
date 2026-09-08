package studio.environment.supervisor;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
class SupervisorTest {
    @TempDir Path directory;
    String[] arguments()throws Exception {
        byte[] archive=PackageCheckTest.archive(false,false,true);Path zip=directory.resolve("mock.zip");Files.write(zip,archive);var admitted=PackageCheck.read(archive,AdmittedFiles.sha256(archive));var mapper=JsonMapper.builder().build();var config=(ObjectNode)mapper.readTree(Files.readAllBytes(Path.of("../../../fixtures/guarded-supervisor-v1/configuration.json")));
        Path runtime=directory.resolve("runtime"),bin=Files.createDirectories(runtime.resolve("bin")),nativeFile=bin.resolve("psql"),helper=directory.resolve("helper"),trust=directory.resolve("trust.pem");Files.writeString(nativeFile,"independent mock executable identity, never executed");Files.writeString(helper,"independent mock helper identity, never executed");Files.copy(Path.of("../../../fixtures/guarded-supervisor-v1/mock-ca.pem"),trust);
        for(String field:List.of("sessionLauncher","terminalControl")){var n=(ObjectNode)config.get(field);n.put("path",helper.toString()).put("sha256",AdmittedFiles.sha256(Files.readAllBytes(helper)));}
        var client=(ObjectNode)config.get("clients").get(0);client.put("runtimeRoot",runtime.toString()).put("executableSha256",AdmittedFiles.sha256(Files.readAllBytes(nativeFile)));
        var d=(ObjectNode)mapper.readTree(admitted.inputs().canonicalExecution()).get("destination").deepCopy();d.remove("transport");d.put("engine","postgresql");d.putObject("trustMaterial").put("path",trust.toString()).put("sha256",AdmittedFiles.sha256(Files.readAllBytes(trust)));config.putArray("destinations").add(d);Path file=directory.resolve("config.json");Files.write(file,mapper.writeValueAsBytes(config));
        try(var files=Files.walk(directory)){for(Path path:files.toList())Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString(Files.isDirectory(path)?"rwx------":"rw-------"));}
        return new String[]{"apply","--package",zip.toString(),"--sha256",AdmittedFiles.sha256(archive),"--configuration",file.toString(),"--destination",d.get("id").asString()};
    }
    @Test void injectedQualificationStillRequiresCompleteAdmissionBeforeOneCredentialRead()throws Exception{
        var reads=new AtomicInteger();var restored=new AtomicInteger();var opened=new AtomicInteger();var console=new Supervisor.ConsolePort(){public Credentials read(boolean pg){reads.incrementAndGet();try{return new Credentials("MOCKUSER".toCharArray(),BoundedSecret.read(new ByteArrayInputStream("invented-secret\n".getBytes())),pg);}catch(IOException e){throw new AssertionError(e);}}public SessionEngine.Cleanup restore(){restored.incrementAndGet();return SessionEngine.Cleanup.COMPLETE;}};
        var result=new Supervisor().apply(arguments(),(c,n,d)->true,console,(a,c)->{opened.incrementAndGet();assertEquals("MOCKUSER",c.username());return new SessionEngineTest.Scripted();});
        assertEquals("APPLIED",result.outcome());assertEquals(0,result.exit());assertEquals(1,reads.get());assertEquals(1,opened.get());assertEquals(1,restored.get());assertFalse(result.json().contains("MOCKUSER"));
    }
    @Test void unqualifiedRuntimeNeverCallsCredentialOrNativePorts()throws Exception{
        var console=new Supervisor.ConsolePort(){public Credentials read(boolean pg){fail("must not prompt");return null;}public SessionEngine.Cleanup restore(){fail("must not acquire console");return SessionEngine.Cleanup.INCONCLUSIVE;}};
        var result=new Supervisor().apply(arguments(),Admission.COMPILED_REGISTRY,console,(a,c)->{fail("must not launch");return null;});assertEquals("RUNTIME_UNQUALIFIED",result.code());assertEquals(2,result.exit());
    }
    @Test void consoleReadErrorStillRestoresTerminal()throws Exception{var restored=new AtomicInteger();var console=new Supervisor.ConsolePort(){public Credentials read(boolean pg){throw new AssertionError("INDEPENDENT_READ_ERROR");}public SessionEngine.Cleanup restore(){restored.incrementAndGet();return SessionEngine.Cleanup.COMPLETE;}};String[] args=arguments();var result=assertDoesNotThrow(()->new Supervisor().apply(args,(a,b,c)->true,console,(a,c)->{fail("must not launch");return null;}));assertEquals("REFUSED",result.outcome());assertEquals(1,restored.get());}
    @Test void restorationErrorPreservesAcknowledgedApplied()throws Exception{var console=new Supervisor.ConsolePort(){public Credentials read(boolean pg){try{return new Credentials("MOCKUSER".toCharArray(),BoundedSecret.read(new ByteArrayInputStream("invented\n".getBytes())),pg);}catch(IOException e){throw new AssertionError(e);}}public SessionEngine.Cleanup restore(){throw new AssertionError("INDEPENDENT_RESTORE_ERROR");}};String[] args=arguments();var result=assertDoesNotThrow(()->new Supervisor().apply(args,(a,b,c)->true,console,(a,c)->new SessionEngineTest.Scripted()));assertEquals("APPLIED",result.outcome());assertEquals(SessionEngine.Cleanup.INCONCLUSIVE,result.cleanup());assertEquals(5,result.exit());}
    @Test void outerFallbackNeverMisreportsUnknownPhaseAsPreclientRefusal(){var result=Main.execute(()->{throw new AssertionError("INDEPENDENT_UNKNOWN_PHASE");});assertEquals("UNKNOWN",result.outcome());assertEquals(4,result.exit());assertEquals(SessionEngine.Cleanup.INCONCLUSIVE,result.cleanup());}
}
