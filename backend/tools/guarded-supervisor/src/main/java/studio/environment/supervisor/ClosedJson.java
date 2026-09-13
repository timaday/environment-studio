package studio.environment.supervisor;

import java.nio.*;
import java.nio.charset.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.node.JsonNodeFactory;

final class ClosedJson {
    private static final JsonNodeFactory N=JsonNodeFactory.instance;
    static JsonNode read(byte[] bytes,int maximum,int depth,int tokens) {
        Refusal.require(bytes.length<=maximum,"RESOURCE_LIMIT");
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(depth).maxStringLength(maximum).maxNameLength(128).maxNumberLength(9).maxDocumentLength(maximum).maxTokenCount(tokens).build()).build();
        try(var parser=factory.createParser(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString())) {
            var node=read(parser,parser.nextToken());Refusal.require(node.isObject()&&parser.nextToken()==null,"INVALID_JSON");return node;
        }catch(Refusal e){throw e;}catch(Exception e){throw new Refusal("INVALID_JSON");}
    }
    private static JsonNode read(JsonParser p,JsonToken t) {
        Refusal.require(t!=null,"INVALID_JSON");
        return switch(t) {
            case START_OBJECT -> {var n=N.objectNode();while(p.nextToken()!=JsonToken.END_OBJECT){Refusal.require(p.currentToken()==JsonToken.PROPERTY_NAME,"INVALID_JSON");String k=p.currentName();scalar(k);n.set(k,read(p,p.nextToken()));}yield n;}
            case START_ARRAY -> {var n=N.arrayNode();while(p.nextToken()!=JsonToken.END_ARRAY)n.add(read(p,p.currentToken()));yield n;}
            case VALUE_STRING -> {String s=p.getString();scalar(s);yield N.stringNode(s);}
            case VALUE_NUMBER_INT -> {String s=p.getString();Refusal.require(s.matches("0|[1-9][0-9]{0,8}"),"INVALID_INTEGER");yield N.numberNode(Long.parseLong(s));}
            case VALUE_TRUE -> N.booleanNode(true);
            case VALUE_FALSE -> N.booleanNode(false);
            case VALUE_NULL -> N.nullNode();
            default -> throw new Refusal("INVALID_JSON");
        };
    }
    static void scalar(String s) {for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){Refusal.require(++i<s.length()&&Character.isLowSurrogate(s.charAt(i)),"INVALID_UNICODE");}else Refusal.require(!Character.isLowSurrogate(c),"INVALID_UNICODE");}}
}
