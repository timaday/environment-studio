package studio.environment.supervisor;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ClientProtocolTest {
    @TempDir Path directory;
    SessionEngine.Outcome execute(boolean oracle,String mode)throws Exception{
        byte[] bytes=PackageCheckTest.archive(false,oracle);var input=PackageCheck.read(bytes,AdmittedFiles.sha256(bytes));String nonce="0123456789abcdef0123456789abcdef";
        var command=List.of("/usr/bin/python3",Path.of("src/test/resources/protocol-child.py").toAbsolutePath().toString(),oracle?"oracle":"postgresql",mode,nonce,input.archiveDigest(),input.inputs().programDigest());
        NativeProcess child=new OwnedNativeProcess(Path.of("/usr/bin/setsid"),command,Map.of("LANG","C.UTF-8","LC_ALL","C.UTF-8"),directory);
        if(mode.equals("write-fault")){NativeProcess owned=child;child=new NativeProcess(){boolean failed;public void write(byte[] bytes,long deadline){String prefix=new String(bytes,0,Math.min(bytes.length,8),java.nio.charset.StandardCharsets.US_ASCII);if(!failed&&(prefix.startsWith("DO ")||prefix.startsWith("DECLARE"))){failed=true;throw new Refusal("INJECTED_WRITE_FAILURE");}owned.write(bytes,deadline);}public int read(long d){return owned.read(d);}public int exit(long d){return owned.exit(d);}public boolean alive(){return owned.alive();}public void terminate(){owned.terminate();}public void close(){owned.close();}};}
        var secret=BoundedSecret.read(new ByteArrayInputStream("invented-secret\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return new SessionEngine().execute(new ClientProtocol(child,input,nonce,"MOCKUSER",secret,"(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=db.example.invalid)(PORT=2484))(CONNECT_DATA=(SERVICE_NAME=MOCKPDB)))"));
    }
    @Test void exactBothEngineNativeCandidateTranscriptsReachOneCommit()throws Exception {for(boolean oracle:List.of(false,true))assertEquals(new SessionEngine.Outcome(SessionEngine.Status.APPLIED,SessionEngine.Cleanup.COMPLETE),execute(oracle,"success"));}
    @Test void precommitErrorWrongDigestOrSecondPromptNeverCommits()throws Exception{for(boolean oracle:List.of(false,true))for(String mode:List.of("earlier-error","wrong-ready","extra-prompt"))assertEquals(SessionEngine.Status.NOT_APPLIED,execute(oracle,mode).status());}
    @Test void lostAcknowledgementRemainsUnknownForBothClients()throws Exception{for(boolean oracle:List.of(false,true))assertEquals(SessionEngine.Status.UNKNOWN,execute(oracle,"lost-ack").status());}
    @Test void accountSyntaxIsNeverTrimmedFoldedOrInterpreted(){for(String account:List.of(" lower ","LOWER@host","LOWER/password","LOWER\nCOMMAND","lower"))assertThrows(Refusal.class,()->ClientProtocol.account(false,account));ClientProtocol.account(true,"_mock$user");ClientProtocol.account(false,"MOCK$USER#1");}
    @Test void usableClientRollbackRequiresExactFrameAndCleanExit()throws Exception{for(boolean oracle:List.of(false,true))assertEquals(new SessionEngine.Outcome(SessionEngine.Status.NOT_APPLIED,SessionEngine.Cleanup.COMPLETE),execute(oracle,"write-fault"));}
}
