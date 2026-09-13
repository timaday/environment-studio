package studio.environment.server.plan;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

/** Closed small request for guarded package candidates; no caller-provided destination or client pins. */
final class V3PlanPackageReader {
    record Request(String revision, String inputFingerprint) { }
    Request read(InputStream input) {
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(3).maxTokenCount(64)
                        .maxNameLength(64).maxStringLength(2048).build()).build();
        try(var reader=new InputStreamReader(new Limited(input), StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)); var parser=factory.createParser(reader)) {
            if(parser.nextToken()!=JsonToken.START_OBJECT) throw invalid();
            var fields=new LinkedHashMap<String,String>();
            while(parser.nextToken()!=JsonToken.END_OBJECT) {
                if(parser.currentToken()!=JsonToken.PROPERTY_NAME) throw invalid();
                String name=parser.currentName(); parser.nextToken();
                if(!Set.of("revision","inputFingerprint").contains(name) || parser.currentToken()!=JsonToken.VALUE_STRING) throw invalid();
                if(fields.putIfAbsent(name,parser.getString())!=null) throw invalid();
            }
            if(parser.nextToken()!=null) throw invalid();
            String revision=fields.get("revision"), fingerprint=fields.get("inputFingerprint");
            if(revision==null || !revision.matches("[1-9][0-9]{0,1023}") || fingerprint==null || !fingerprint.matches("[0-9a-f]{64}")) throw invalid();
            return new Request(revision,fingerprint);
        } catch(PlanBodyFailure failure) { throw failure; }
        catch(IOException|RuntimeException failure) { throw invalid(); }
    }
    private static PlanBodyFailure invalid(){return new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY);}
    private static final class Limited extends FilterInputStream {
        private int count;
        Limited(InputStream input){super(input);}
        private void counted(int n){if(n>0 && (count+=n)>4096)throw new PlanBodyFailure(PlanBodyFailure.Code.BODY_TOO_LARGE);}
        @Override public int read()throws IOException{int value=in.read();if(value>=0)counted(1);return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=in.read(bytes,offset,Math.min(length,4097-count));counted(n);return n;}
    }
}
