package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.workspace.*;

class V3PublicationRequestReaderTest {
    static final String ID="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", REQUEST="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    static final String PROFILE="{\"expectedRevision\":\"1\",\"requestId\":\""+REQUEST+"\"}";
    static final String POLICY="{\"bindingId\":\"mock-pg\",\"documentId\":\"sheet\",\"content\":\"deny\"}";
    static String definition(String policies) {return PROFILE.substring(0,PROFILE.length()-1)+",\"exportPolicies\":["+policies+"]}";}
    NativeCommand read(String source,boolean profile) {
        byte[] bytes=source.getBytes(StandardCharsets.UTF_8);
        try {
            var input=new ByteArrayInputStream(bytes){@Override public byte[] readNBytes(int n){return bytes;}};
            return profile?new V3PublicationRequestReader().profile(ID,input):new V3PublicationRequestReader().definition(ID,input);
        } finally {assertArrayEquals(new byte[bytes.length],bytes);}
    }
    @Test void exactClosedCommandsPreserveDuplicatePoliciesForApplicationAndWipe() {
        assertEquals(new NativeCommand.PublishProfile(ID,"1",REQUEST),read(PROFILE,true));
        var command=(NativeCommand.PublishDefinition)read(definition(POLICY+","+POLICY),false);
        assertEquals(List.of(new NativeCommand.Policy("mock-pg","sheet","deny"),new NativeCommand.Policy("mock-pg","sheet","deny")),command.exportPolicies());
        assertThrows(UnsupportedOperationException.class,()->command.exportPolicies().clear());
        assertFalse(command.toString().contains(REQUEST));
    }
    @Test void duplicatesUnknownMissingTrailingWrongTypesAndAuthorityVocabularyRefuse() {
        var values=new ArrayList<>(List.of("{}","[]",PROFILE+" {}",PROFILE+" null",PROFILE.replace("\"1\"","1"),PROFILE.replace("\"1\"","\"01\""),PROFILE.replace("\"1\"","\""+"1".repeat(1025)+"\""),PROFILE.replace(REQUEST,"wrong"),PROFILE.replace("\"requestId\"","\"expectedRevision\"")));
        for(String key:List.of("model","source","owner","format","definition","ready","computed"))values.add(PROFILE.substring(0,PROFILE.length()-1)+",\""+key+"\":{}}");
        for(String value:values)assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,assertThrows(WorkspaceRefusal.class,()->read(value,true)).code());
        for(String value:List.of(PROFILE,definition(POLICY)+" []",definition(POLICY.replace("\"sheet\"","\"sheet\",\"documentId\":\"other\"")),definition(POLICY.replace("\"content\"","\"extra\"")),definition(POLICY.replace("\"deny\"","\"allow\"")),definition(POLICY.replace("\"sheet\"","null")),definition(POLICY.replace("sheet","\\ud800")),definition(POLICY).replace(":[",":{}")))
            assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,assertThrows(WorkspaceRefusal.class,()->read(value,false)).code());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,assertThrows(WorkspaceRefusal.class,()->read(definition(""),true)).code());
    }
    @Test void exactPolicyAndWrapperLimitsAndUnicodeRefusals() {
        String policies=String.join(",",Collections.nCopies(20000,POLICY));
        assertEquals(20000,((NativeCommand.PublishDefinition)read(definition(policies),false)).exportPolicies().size());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,assertThrows(WorkspaceRefusal.class,()->read(definition(policies+","+POLICY),false)).code());
        assertEquals(new NativeCommand.PublishProfile(ID,"1",REQUEST),read(PROFILE+" ".repeat(8*1024*1024-PROFILE.length()),true));
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE,assertThrows(WorkspaceRefusal.class,()->read(PROFILE+" ".repeat(8*1024*1024),true)).code());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST,assertThrows(WorkspaceRefusal.class,()->new V3PublicationRequestReader().profile(ID,new ByteArrayInputStream(new byte[]{(byte)0xc0,(byte)0xaf}))).code());
    }
    @Test void originalAuthorityAndIoRefusalsAreNotSwallowed() {
        for(var code:List.of(WorkspaceRefusal.Code.FORBIDDEN,WorkspaceRefusal.Code.UNAVAILABLE,WorkspaceRefusal.Code.TOO_LARGE)) {
            var original=new WorkspaceRefusal(code);
            assertSame(original,assertThrows(WorkspaceRefusal.class,()->new V3PublicationRequestReader().profile(ID,new InputStream(){public int read(){throw original;}})));
        }
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE,assertThrows(WorkspaceRefusal.class,()->new V3PublicationRequestReader().profile(ID,new InputStream(){public int read()throws IOException{throw new IOException("INVENTED-IO-CANARY");}})).code());
    }
}
