package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import studio.environment.core.workspace.*;
import studio.environment.core.session.Owner;

class NativeRequestTest {
    final String id="00000000-0000-4000-8000-000000000072";
    NativeCommand read(String body){return new NativeRequestReader().read(id,false,false,new ByteArrayInputStream(StrictUtf8.encode(body)));}
    @Test void decodedWhitespaceOrderIsImmaterialButExactSourceAndKindsAreNot() {
        String a="{\"source\":\"Ω\\n\",\"format\":\"JSON\",\"requestId\":\""+id+"\",\"expectedRevision\":\"0\"}";
        String b=" { \"expectedRevision\":\"0\",\"requestId\":\""+id+"\",\"format\":\"JSON\",\"source\":\"\\u03a9\\n\" } ";
        assertEquals(NativeWorkspaceDigests.commandDigest(read(a)),NativeWorkspaceDigests.commandDigest(read(b)));
        assertNotEquals(NativeWorkspaceDigests.commandDigest(read(a)),NativeWorkspaceDigests.commandDigest(read(a.replace("Ω","x"))));
    }
    @Test void duplicatesUnknownsLoneSurrogatesAndTrailingRootsRefuse() {
        String good="{\"expectedRevision\":\"0\",\"requestId\":\""+id+"\",\"format\":\"JSON\",\"source\":\"x\"}";
        for(String bad:List.of(good+"{}",good.replace("\"source\":\"x\"","\"source\":\"x\",\"source\":\"y\""),good.replace("\"x\"","\"\\ud800\""),good.replace("\"source\"","\"extension\"")))
            assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,assertThrows(WorkspaceRefusal.class,()->read(bad)).code());
    }
    @Test void absentPublisherDeniesAndExactPairCannotBeForged() {
        var owner=new Owner("https://mock.invalid","mock-subject");assertFalse(new DefinitionPublishers(new MockEnvironment()).test(owner));
        var env=new MockEnvironment().withProperty("studio.workspace.definition-publishers[0].issuer",owner.issuer()).withProperty("studio.workspace.definition-publishers[0].subject",owner.subject());
        assertThrows(IllegalStateException.class,()->new DefinitionPublishers(new MockEnvironment().withProperty("studio.workspace.definition-publishers[0].issuer",owner.issuer()).withProperty("studio.workspace.definition-publishers[0].subject",owner.subject()).withProperty("studio.workspace.definition-publishers[0].role","injected")));
        var policy=new DefinitionPublishers(env);assertTrue(policy.test(owner));assertFalse(policy.test(new Owner(owner.issuer(),"other")));assertFalse(policy.test(new Owner("https://other.invalid",owner.subject())));
    }
    @Test void byteLimitMapsToTooLargeWithoutTruncation() {
        String body=NativeSnapshotCodec.JSON.writeValueAsString(Map.of("expectedRevision","0","requestId",id,"format","JSON","source","x".repeat(1_048_577)));
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE,assertThrows(WorkspaceRefusal.class,()->read(body)).code());
    }
}
