package studio.environment.server.plan;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

/** V3-only closed validation union; capture/preview retain the established collection grammar. */
final class V3PlanWorkflowReader {
    enum Route { CAPTURE, PREVIEW, VALIDATION }
    sealed interface Validation {
        String revision();
        record Summary(String revision) implements Validation { }
        record Page(String revision,String inputFingerprint,int offset,int limit) implements Validation {
            @Override public String toString(){return "ValidationPageRequest[redacted]";}
        }
    }
    Validation validation(InputStream input) {
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxTokenCount(128)
                        .maxNameLength(64).maxStringLength(2048).build()).build();
        try(var reader=new InputStreamReader(new Limited(input),StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));var parser=factory.createParser(reader)){
            if(parser.nextToken()!=JsonToken.START_OBJECT)throw invalid();
            var fields=new LinkedHashMap<String,Object>();
            while(parser.nextToken()!=JsonToken.END_OBJECT){
                if(parser.currentToken()!=JsonToken.PROPERTY_NAME)throw invalid();String name=parser.currentName();parser.nextToken();
                Object value;
                switch(name){
                    case "revision","section","inputFingerprint"->{if(parser.currentToken()!=JsonToken.VALUE_STRING)throw invalid();value=parser.getString();}
                    case "offset","limit"->{if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw invalid();String token=parser.getString();if(!token.matches("0|[1-9][0-9]{0,9}"))throw invalid();value=Integer.parseInt(token);}
                    default->throw invalid();
                }
                if(fields.putIfAbsent(name,value)!=null)throw invalid();
            }
            if(parser.nextToken()!=null || !(fields.get("revision") instanceof String revision) || !revision.matches("[1-9][0-9]{0,1023}"))throw invalid();
            if(fields.keySet().equals(Set.of("revision")))return new Validation.Summary(revision);
            if(!fields.keySet().equals(Set.of("revision","section","inputFingerprint","offset","limit"))
                    || !"computed-rules".equals(fields.get("section")) || !(fields.get("inputFingerprint") instanceof String fingerprint)
                    || !fingerprint.matches("[0-9a-f]{64}"))throw invalid();
            int limit=(Integer)fields.get("limit");if(limit<1 || limit>100)throw invalid();
            return new Validation.Page(revision,fingerprint,(Integer)fields.get("offset"),limit);
        }catch(PlanBodyFailure failure){throw failure;}catch(IOException|RuntimeException failure){throw invalid();}
    }
    private static PlanBodyFailure invalid(){return new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY);}
    private static final class Limited extends FilterInputStream {
        private int count;
        Limited(InputStream input){super(input);}
        private void counted(int n){if(n>0 && (count+=n)>16384)throw new PlanBodyFailure(PlanBodyFailure.Code.BODY_TOO_LARGE);}
        @Override public int read()throws IOException{int value=in.read();if(value>=0)counted(1);return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=in.read(bytes,offset,Math.min(length,16385-count));counted(n);return n;}
    }
}
