package studio.environment.server.export;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

final class PackageJson {
    static final int LARGE = 80 * 1024 * 1024, SMALL = 65_536;
    static final Comparator<String> UTF8 = (a,b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final JsonFactory JSON = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
        .maxNestingDepth(12).maxStringLength(6_291_456).maxNameLength(128).maxNumberLength(9).maxDocumentLength(LARGE).maxTokenCount(16_384).build())
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private PackageJson() { }
    static final class Refusal extends RuntimeException { final String code; Refusal(String code) { super(null,null,false,false); this.code=code; } }
    static void fail(String code) { throw new Refusal(code); }
    static String utf8(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException invalid) { fail("INVALID_UTF8"); return null; }
    }
    static void unicode(String value) {
        for (int i=0;i<value.length();i++) { char c=value.charAt(i); if(Character.isHighSurrogate(c)) { if(++i==value.length() || !Character.isLowSurrogate(value.charAt(i)))fail("INVALID_UNICODE"); } else if(Character.isLowSurrogate(c))fail("INVALID_UNICODE"); }
    }
    static JsonNode parse(byte[] bytes,int maximum,int nodes) {
        if(bytes==null) { fail("INVALID_INPUT"); return null; } if(bytes.length>maximum)fail("RESOURCE_LIMIT");
        validateUtf8(bytes);
        try(var reader=utf8Reader(bytes);var parser=JSON.createParser(reader)) {
            var budget=new int[]{nodes}; var result=read(parser,parser.nextToken(),budget);
            if(!result.isObject() || parser.nextToken()!=null)fail("INVALID_JSON"); return result;
        } catch(StreamConstraintsException invalid) { fail("RESOURCE_LIMIT"); }
        catch(StreamReadException invalid) { fail("INVALID_JSON"); }
        catch(IOException invalid) { fail("INVALID_UTF8"); }
        return null;
    }
    private static void validateUtf8(byte[] bytes) {
        try(var reader=utf8Reader(bytes)) {
            char[] buffer=new char[8192]; while(reader.read(buffer)!=-1) { }
        } catch(IOException invalid) { fail("INVALID_UTF8"); }
    }
    private static Reader utf8Reader(byte[] bytes) {
        return new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
    }
    private static JsonNode read(JsonParser parser,JsonToken token,int[] budget) {
        if(--budget[0]<0)fail("RESOURCE_LIMIT"); if(token==null)fail("INVALID_JSON");
        return switch(token) {
            case START_OBJECT -> { var node=NODES.objectNode(); while(parser.nextToken()!=JsonToken.END_OBJECT) { if(parser.currentToken()!=JsonToken.PROPERTY_NAME)fail("INVALID_JSON"); if(--budget[0]<0)fail("RESOURCE_LIMIT"); String key=parser.currentName();unicode(key);node.set(key,read(parser,parser.nextToken(),budget)); } yield node; }
            case START_ARRAY -> { var node=NODES.arrayNode(); while(parser.nextToken()!=JsonToken.END_ARRAY)node.add(read(parser,parser.currentToken(),budget));yield node; }
            case VALUE_STRING -> { String text=parser.getString();unicode(text);yield NODES.stringNode(text); }
            case VALUE_NUMBER_INT -> { String text=parser.getString();if(!text.matches("0|[1-9][0-9]{0,8}"))fail("INVALID_NUMBER");yield NODES.numberNode(Long.parseLong(text)); }
            case VALUE_NUMBER_FLOAT -> { fail("INVALID_NUMBER"); yield NODES.nullNode(); }
            case VALUE_TRUE -> NODES.booleanNode(true); case VALUE_FALSE -> NODES.booleanNode(false);case VALUE_NULL -> NODES.nullNode();
            default -> { fail("INVALID_JSON");yield NODES.nullNode(); }
        };
    }
    static String sha256(byte[] bytes) { var digest=digest();return HexFormat.of().formatHex(digest.digest(bytes)); }
    static String executionDigest(JsonNode execution,String payloadDigest) {
        var digest=digest(); digest.update("ES-EXECUTION-1\0".getBytes(StandardCharsets.US_ASCII));
        var root=NODES.objectNode();root.set("execution",execution);root.put("payloadDigest",payloadDigest);frame(root,digest);return HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest() { try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException absent){throw new IllegalStateException("SHA256_UNAVAILABLE");} }
    private static void ascii(MessageDigest digest,String text) { digest.update(text.getBytes(StandardCharsets.US_ASCII)); }
    private static void frame(JsonNode node,MessageDigest digest) {
        if(node.isString()){byte[]bytes=node.asString().getBytes(StandardCharsets.UTF_8);ascii(digest,"S"+bytes.length+":");digest.update(bytes);}
        else if(node.isIntegralNumber())ascii(digest,"I"+node.bigIntegerValue()+";");
        else if(node.isBoolean())ascii(digest,node.asBoolean()?"T":"F");
        else if(node.isArray()){ascii(digest,"A"+node.size()+":");node.forEach(n->frame(n,digest));}
        else if(node.isObject()){ascii(digest,"O"+node.size()+":");node.propertyNames().stream().sorted(UTF8).forEach(k->{frame(NODES.stringNode(k),digest);frame(node.get(k),digest);});}
        else fail("INVALID_FRAME");
    }
    static byte[] canonical(JsonNode node,int maximum) {
        var output=new Bounded(maximum);
        write(node,output);
        output.allocate();
        write(node,output);
        return output.bytes();
    }
    private static void write(JsonNode node,Bounded out) {
        if(node.isString())string(node.asString(),out);
        else if(node.isIntegralNumber() || node.isBoolean() || node.isNull())out.add(node.toString());
        else if(node.isArray()){out.add("[");boolean first=true;for(var item:node){if(!first)out.add(",");first=false;write(item,out);}out.add("]");}
        else if(node.isObject()){out.add("{");boolean first=true;for(String key:node.propertyNames().stream().sorted(UTF8).toList()){if(!first)out.add(",");first=false;string(key,out);out.add(":");write(node.get(key),out);}out.add("}");}
        else fail("INVALID_JSON");
    }
    private static void string(String value,Bounded out) {
        unicode(value);out.add("\"");int start=0;
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='"'||c=='\\'||c<32){out.add(value.substring(start,i));out.add(switch(c){case '"'->"\\\"";case '\\'->"\\\\";case '\b'->"\\b";case '\f'->"\\f";case '\n'->"\\n";case '\r'->"\\r";case '\t'->"\\t";default->String.format(java.util.Locale.ROOT,"\\u%04x",(int)c);});start=i+1;}}
        out.add(value.substring(start));out.add("\"");
    }
    static final class Bounded {
        private final int maximum;
        private int size;
        private byte[] output;
        Bounded(int maximum){this.maximum=maximum;}
        void add(String text){
            if((long)size+text.length()>maximum)fail("RESOURCE_LIMIT");
            byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
            if((long)size+bytes.length>maximum)fail("RESOURCE_LIMIT");
            if(output!=null){
                if(bytes.length>output.length-size)fail("RESOURCE_LIMIT");
                System.arraycopy(bytes,0,output,size,bytes.length);
            }
            size+=bytes.length;
        }
        // Measure first, then allocate exactly once: no geometric growth or final full copy.
        void allocate(){output=new byte[size];size=0;}
        byte[] bytes(){if(size!=output.length)fail("INVALID_JSON");return output;}
    }
}
