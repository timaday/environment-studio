package studio.environment.server.plan;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.Supplier;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.core.plan.PlanCommand;
import static studio.environment.server.plan.PlanViewRequest.*;

/** Closed typed streaming requests; no body tree, implicit consent or numeric coercion. */
final class PlanViewReader {
    private JsonParser parser;
    PlanViewRequest read(Route route,InputStream input) {
        int ceiling=route.collection()?67_108_864:16_384;
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(route.collection()?8:4).maxTokenCount(route.collection()?1_000_000:128).maxNameLength(64).maxStringLength(2048).build()).build();
        try(var reader=new InputStreamReader(new Limited(input,ceiling),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));var parsed=factory.createParser(reader)) {
            parser=parsed;parser.nextToken();
            var data=object(name->switch(name){
                case "revision","side","documentId","projectionId","mode","profileId","profileRevision","section","fieldId" -> text();
                case "offset","limit" -> integer();
                case "completeDocumentDisclosure" -> {if(parser.currentToken()!=JsonToken.VALUE_TRUE)throw invalid();yield Boolean.TRUE;}
                case "entity" -> entity(); case "profile" -> publication(); case "selection" -> selection(); case "mappings" -> array(this::mapping);
                default -> throw invalid();
            });
            if(parser.nextToken()!=null)throw invalid();
            String revision=revision(string(data,"revision"));
            return switch(route){
                case BINDINGS -> {keys(data,"revision","entity","offset","limit");yield new Bindings(revision,(PlanCommand.Ref)data.get("entity"),offset(data,256),limit(data));}
                case BINDING_LOCATIONS -> {keys(data,"revision","entity","fieldId","side","offset","limit","completeDocumentDisclosure");yield new BindingLocations(revision,(PlanCommand.Ref)data.get("entity"),id(string(data,"fieldId")),side(string(data,"side")),offset(data,Integer.MAX_VALUE),limit(data),true);}
                case MATERIALIZATION,DOCUMENTS,VALIDATION -> {keys(data,"revision");yield new Revision(revision);}
                case ENTITIES,RELATIONS -> {keys(data,"revision","side","offset","limit");yield new GraphPage(revision,side(string(data,"side")),offset(data),limit(data));}
                case DRAFT,CONTAINMENT -> {keys(data,"revision","offset","limit");yield new DraftPage(revision,offset(data),limit(data));}
                case PLACEMENTS -> {keys(data,"revision","documentId","projectionId","offset","limit");yield new Placements(revision,id(string(data,"documentId")),id(string(data,"projectionId")),offset(data),limit(data));}
                case DOCUMENT -> {keys(data,"revision","side","documentId","mode","completeDocumentDisclosure");yield new Document(revision,side(string(data,"side")),id(string(data,"documentId")),mode(string(data,"mode")),true);}
                case CAPTURE -> {keys(data,"revision","profileId","profileRevision","mappings");yield new Capture(revision,id(string(data,"profileId")),revision(string(data,"profileRevision")),mappings(data));}
                case PREVIEW -> {keys(data,"revision","profile","selection","section","offset","limit");var selection=(Selection)data.get("selection");yield new Preview(revision,(NativeCommand.Reference)data.get("profile"),selection.all(),selection.roots(),section(string(data,"section")),offset(data),limit(data));}
            };
        } catch(PlanBodyFailure failure){throw failure;} catch(IOException|RuntimeException failure){throw invalid();} finally {parser=null;}
    }
    private record Selection(boolean all,List<String> roots) { }
    private Selection selection(){
        var data=object(name->switch(name){case "kind"->text();case "roots"->array(()->id(text()));default->throw invalid();});
        if("all".equals(string(data,"kind"))){keys(data,"kind");return new Selection(true,List.of());}
        if(!"selected".equals(string(data,"kind")))throw invalid();keys(data,"kind","roots");
        @SuppressWarnings("unchecked") var roots=(List<String>)data.get("roots");
        if(roots.isEmpty() || new HashSet<>(roots).size()!=roots.size())throw invalid();return new Selection(false,roots);
    }
    private NativeCommand.Reference publication(){var data=object(name->{if(!Set.of("objectId","workspaceRevision").contains(name))throw invalid();return text();});keys(data,"objectId","workspaceRevision");return new NativeCommand.Reference(uuid(string(data,"objectId")),revision(string(data,"workspaceRevision")));}
    private Mapping mapping(){
        var data=object(name->switch(name){case "slotId","label"->text();case "entity"->existing();default->throw invalid();});keys(data,"entity","slotId","label");
        String label=string(data,"label");if(label.isEmpty() || label.codePointCount(0,label.length())>128)throw invalid();
        return new Mapping((String)data.get("entity"),id(string(data,"slotId")),label);
    }
    private PlanCommand.Ref entity(){
        var data=object(name->{if(!Set.of("kind","handle","slotId","typeId").contains(name))throw invalid();return text();});
        return switch(string(data,"kind")) {
            case "existing" -> {keys(data,"kind","handle");yield new PlanCommand.Ref.Existing(uuid(string(data,"handle")));}
            case "fresh" -> {keys(data,"kind","slotId","typeId");yield new PlanCommand.Ref.Fresh(id(string(data,"slotId")),id(string(data,"typeId")));}
            default -> throw invalid();
        };
    }
    private String existing(){var data=object(name->{if(!Set.of("kind","handle").contains(name))throw invalid();return text();});keys(data,"kind","handle");if(!"existing".equals(string(data,"kind")))throw invalid();return uuid(string(data,"handle"));}
    private static List<Mapping> mappings(Map<String,Object> data){@SuppressWarnings("unchecked")var mappings=(List<Mapping>)data.get("mappings");if(mappings.isEmpty())throw invalid();return mappings;}
    private <T> List<T> array(Supplier<T> item){if(parser.currentToken()!=JsonToken.START_ARRAY)throw invalid();var values=new ArrayList<T>();while(parser.nextToken()!=JsonToken.END_ARRAY){if(values.size()>=20000)throw invalid();values.add(item.get());}return List.copyOf(values);}
    private Map<String,Object> object(java.util.function.Function<String,Object> value){
        if(parser.currentToken()!=JsonToken.START_OBJECT)throw invalid();var result=new LinkedHashMap<String,Object>();
        while(parser.nextToken()!=JsonToken.END_OBJECT){if(parser.currentToken()!=JsonToken.PROPERTY_NAME || result.size()>=8)throw invalid();String key=parser.currentName();parser.nextToken();if(result.putIfAbsent(key,value.apply(key))!=null)throw invalid();}return result;
    }
    private String text(){if(parser.currentToken()!=JsonToken.VALUE_STRING)throw invalid();String value=parser.getString();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}return value;}
    private int integer(){if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw invalid();String token=parser.getString();if(!token.matches("0|[1-9][0-9]{0,9}"))throw invalid();return Integer.parseInt(token);}
    private static int offset(Map<String,Object> data){return offset(data,50000);}
    private static int offset(Map<String,Object> data,int maximum){int value=(Integer)data.get("offset");if(value>maximum)throw invalid();return value;}
    private static int limit(Map<String,Object> data){int value=(Integer)data.get("limit");if(value<1 || value>100)throw invalid();return value;}
    private static Mode mode(String value){return switch(value){case "raw"->Mode.RAW;case "placeholders"->Mode.PLACEHOLDERS;case "formatted"->Mode.FORMATTED;default->throw invalid();};}
    private static Section section(String value){return switch(value){case "included"->Section.INCLUDED;case "dependencies"->Section.DEPENDENCIES;case "relations"->Section.RELATIONS;case "conflicts"->Section.CONFLICTS;default->throw invalid();};}
    private static Side side(String value){return switch(value){case "current"->Side.CURRENT;case "target"->Side.TARGET;default->throw invalid();};}
    private static String string(Map<String,Object> data,String key){if(!(data.get(key) instanceof String value))throw invalid();return value;}
    private static void keys(Map<String,Object> data,String... keys){if(!data.keySet().equals(Set.of(keys)))throw invalid();}
    private static String revision(String value){if(!value.matches("[1-9][0-9]{0,1023}"))throw invalid();return value;}
    private static String id(String value){if(!value.matches("[a-z][a-z0-9.-]{0,63}"))throw invalid();return value;}
    private static String uuid(String value){if(!UUID.fromString(value).toString().equals(value))throw invalid();return value;}
    private static PlanBodyFailure invalid(){return new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY);}
    private static final class Limited extends FilterInputStream {
        private final int ceiling;private int count;
        Limited(InputStream input,int ceiling){super(input);this.ceiling=ceiling;}
        private void counted(int n){if(n>0 && (count+=n)>ceiling)throw new PlanBodyFailure(PlanBodyFailure.Code.BODY_TOO_LARGE);}
        @Override public int read()throws IOException{int value=in.read();if(value>=0)counted(1);return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=in.read(bytes,offset,Math.min(length,ceiling+1-count));counted(n);return n;}
    }
}
