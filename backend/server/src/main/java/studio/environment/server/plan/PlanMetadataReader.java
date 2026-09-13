package studio.environment.server.plan;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import studio.environment.core.plan.HostedPlanService.Mutation;
import studio.environment.core.workspace.NativeCommand;

/** Tiny credential-free wrappers; limits apply while reading rather than after a full body copy. */
final class PlanMetadataReader {
    record Create(String requestId,NativeCommand.Reference definition,String bindingId,String destinationId) { }
    private static final JsonFactory JSON=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxTokenCount(128).maxStringLength(16_384).maxNameLength(64).build()).build();
    Create create(InputStream input) {
        var data=read(input); keys(data,"expectedRevision","requestId","definition","bindingId","destinationId");
        if(!"0".equals(data.get("expectedRevision"))) throw invalid();
        if(!(data.get("definition") instanceof Map<?,?> reference) || !reference.keySet().equals(Set.of("objectId","workspaceRevision"))) throw invalid();
        return new Create(uuid(text(data.get("requestId"))),new NativeCommand.Reference(uuid(text(reference.get("objectId"))),revision(text(reference.get("workspaceRevision")))),tool(text(data.get("bindingId"))),tool(text(data.get("destinationId"))));
    }
    Mutation reserve(InputStream input) {
        var data=read(input); keys(data,"expectedRevision","requestId","discardDraftOnSuccess");
        if(!Boolean.TRUE.equals(data.get("discardDraftOnSuccess"))) throw invalid();
        return new Mutation(revision(text(data.get("expectedRevision"))),uuid(text(data.get("requestId"))));
    }
    void empty(InputStream input) { keys(read(input)); }
    studio.environment.core.plan.HostedPlanService.ReviewCommand review(InputStream input) {
        var data=read(input);keys(data,"expectedRevision","requestId","inputFingerprint","destinationId","artifactIntent");
        String fingerprint=text(data.get("inputFingerprint"));
        if(!fingerprint.matches("[0-9a-f]{64}") || !"protected-self-contained".equals(data.get("artifactIntent")))throw invalid();
        return new studio.environment.core.plan.HostedPlanService.ReviewCommand(
                new Mutation(revision(text(data.get("expectedRevision"))),uuid(text(data.get("requestId")))),
                fingerprint,tool(text(data.get("destinationId"))),studio.environment.core.plan.HostedPlanService.ArtifactIntent.PROTECTED_SELF_CONTAINED);
    }
    private Map<String,Object> read(InputStream input) {
        try(var reader=new InputStreamReader(new Limited(input),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));var parser=JSON.createParser(reader)) {
            parser.nextToken(); var data=object(parser,0); if(parser.nextToken()!=null) throw invalid(); return data;
        } catch(PlanBodyFailure failure) { throw failure; }
        catch(IOException|RuntimeException failure) { throw invalid(); }
    }
    private Map<String,Object> object(JsonParser parser,int depth) {
        if(parser.currentToken()!=JsonToken.START_OBJECT || depth>1) throw invalid();
        var result=new LinkedHashMap<String,Object>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()!=JsonToken.PROPERTY_NAME || result.size()>=8) throw invalid();
            String name=parser.currentName(); parser.nextToken();
            Object value=switch(parser.currentToken()) {
                case VALUE_STRING -> text(parser.getString());
                case VALUE_TRUE -> Boolean.TRUE; case VALUE_FALSE -> Boolean.FALSE;
                case START_OBJECT -> object(parser,depth+1);
                default -> throw invalid();
            };
            if(result.putIfAbsent(name,value)!=null) throw invalid();
        }
        return result;
    }
    private static void keys(Map<String,Object> data,String... keys) { if(!data.keySet().equals(Set.of(keys))) throw invalid(); }
    private static String text(Object object) {
        if(!(object instanceof String value)) throw invalid();
        for(int i=0;i<value.length();i++) {
            char ch=value.charAt(i);
            if(Character.isHighSurrogate(ch)) {if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();}
            else if(Character.isLowSurrogate(ch)) throw invalid();
        }
        return value;
    }
    private static String tool(String value) { if(!value.matches("[a-z][a-z0-9.-]{0,63}")) throw invalid(); return value; }
    private static String revision(String value) { if(!value.matches("[1-9][0-9]{0,1023}")) throw invalid(); return value; }
    private static String uuid(String value) { try { if(!UUID.fromString(value).toString().equals(value)) throw invalid(); } catch(IllegalArgumentException failure) {throw invalid();}return value; }
    private static PlanBodyFailure invalid() { return new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY); }
    private static final class Limited extends FilterInputStream {
        int bytes;
        Limited(InputStream input) {super(input);}
        private void count(int count) {if(count>0 && (bytes+=count)>16_384) throw new PlanBodyFailure(PlanBodyFailure.Code.BODY_TOO_LARGE);}
        @Override public int read() throws IOException {int value=in.read();if(value>=0)count(1);return value;}
        @Override public int read(byte[] buffer,int offset,int length) throws IOException {int count=in.read(buffer,offset,Math.min(length,16_385-bytes));count(count);return count;}
    }
}
