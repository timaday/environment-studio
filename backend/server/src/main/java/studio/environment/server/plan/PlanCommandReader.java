package studio.environment.server.plan;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.Supplier;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanCommand;
import studio.environment.core.planning.TargetIntent.FieldValue;
import studio.environment.core.workspace.NativeCommand;
import static studio.environment.core.plan.PlanCommand.*;

/** Streaming typed decoding. No complete JSON tree or second raw source string is retained. */
final class PlanCommandReader {
    private static final JsonFactory JSON=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxTokenCount(8_000_000)
            .maxStringLength(2_097_152).maxNameLength(64).maxDocumentLength(134_217_728).build()).build();
    private JsonParser parser;
    PlanCommand read(InputStream source) {
        try(var reader=new InputStreamReader(new Limited(source),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
                var parsed=JSON.createParser(reader)) {
            parser=parsed; parser.nextToken();
            var data=object(name->switch(name) {
                case "kind","expectedRevision","requestId","fieldId","relationId","previewDigest" -> text();
                case "draft" -> draft();
                case "entity" -> reference();
                case "changes" -> array(this::change,20_000);
                case "containment" -> array(this::containment,20_000);
                case "placements" -> array(this::placement,20_000);
                case "decision" -> decision();
                case "state" -> state();
                case "profile" -> publication();
                case "selectedRoots" -> array(()->tool(text()),20_000);
                case "decisions" -> array(this::profileDecision,20_000);
                default -> throw invalid();
            });
            if(parser.nextToken()!=null) throw invalid();
            String kind=string(data,"kind");
            var mutation=new HostedPlanService.Mutation(revision(string(data,"expectedRevision")),uuid(string(data,"requestId")));
            Set<String> envelope=Set.of("kind","expectedRevision","requestId");
            Action action=switch(kind) {
                case "replace-draft" -> { keys(data,envelope,"draft"); yield new Action.Replace(value(data,"draft",Draft.class)); }
                case "batch-upsert" -> { keys(data,envelope,"changes","containment"); yield new Action.Batch(list(data,"changes"),list(data,"containment")); }
                case "upsert-entity" -> { keys(data,envelope,"decision","placements"); yield new Action.Upsert(new Change(value(data,"decision",Entity.class),list(data,"placements"))); }
                case "forget-entity-decision" -> { keys(data,envelope,"entity"); yield new Action.Forget(value(data,"entity",Ref.class)); }
                case "bind-field" -> { keys(data,envelope,"entity","fieldId","state"); yield new Action.BindField(value(data,"entity",Ref.class),tool(string(data,"fieldId")),field(value(data,"state",State.class))); }
                case "bind-reference" -> { keys(data,envelope,"entity","relationId","state"); yield new Action.BindReference(value(data,"entity",Ref.class),tool(string(data,"relationId")),referenceState(value(data,"state",State.class))); }
                case "move-containment" -> { keys(data,envelope,"decision","placements"); yield new Action.Move(value(data,"decision",Containment.class),list(data,"placements")); }
                case "compose-profile" -> {
                    keys(data,envelope,"profile","previewDigest","selectedRoots","decisions");
                    List<String> roots=list(data,"selectedRoots"); if(new HashSet<>(roots).size()!=roots.size()) throw invalid();
                    yield new Action.Compose(value(data,"profile",NativeCommand.Reference.class),digest(string(data,"previewDigest")),roots,list(data,"decisions"));
                }
                case "discard" -> { keys(data,envelope); yield new Action.Discard(); }
                default -> throw invalid();
            };
            return new PlanCommand(mutation,action);
        } catch(PlanBodyFailure failure) { throw failure; }
        catch(IOException | RuntimeException failure) { throw invalid(); }
        finally { parser=null; }
    }
    private Draft draft() {
        var data=object(name->switch(name) {
            case "entities" -> array(()->entity(decision()),20_000);
            case "containment" -> array(this::containment,20_000);
            case "placements" -> array(this::placement,20_000);
            default -> throw invalid();
        });
        keys(data,Set.of("entities","containment","placements"));
        return new Draft(list(data,"entities"),list(data,"containment"),list(data,"placements"));
    }
    private Change change() {
        var data=object(name->switch(name) { case "decision" -> entity(decision()); case "placements" -> array(this::placement,20_000); default -> throw invalid(); });
        keys(data,Set.of("decision","placements"));
        return new Change(value(data,"decision",Entity.class),list(data,"placements"));
    }
    private Object decision() {
        var data=object(name->switch(name) {
            case "kind","relationId" -> text(); case "entity","parent","child" -> reference();
            case "fields" -> object(field->{tool(field); return field(state());});
            case "references" -> object(relation->{tool(relation); return referenceState(state());});
            default -> throw invalid();
        });
        if(data.containsKey("relationId")) {
            keys(data,Set.of("relationId","parent","child"));
            return new Containment(tool(string(data,"relationId")),value(data,"parent",Ref.class),value(data,"child",Ref.class));
        }
        return switch(string(data,"kind")) {
            case "remove" -> { keys(data,Set.of("kind","entity")); yield new Entity.Remove(value(data,"entity",Ref.Existing.class)); }
            case "retain" -> { keys(data,Set.of("kind","entity","fields","references")); yield new Entity.Retain(value(data,"entity",Ref.Existing.class),map(data,"fields"),map(data,"references")); }
            case "create" -> { keys(data,Set.of("kind","entity","fields","references")); yield new Entity.Create(value(data,"entity",Ref.Fresh.class),map(data,"fields"),map(data,"references")); }
            default -> throw invalid();
        };
    }
    private static Entity entity(Object decision) { if(!(decision instanceof Entity value)) throw invalid(); return value; }
    private Containment containment() { Object decision=decision(); if(!(decision instanceof Containment value)) throw invalid(); return value; }
    private Ref reference() {
        var data=object(name->{if(!Set.of("kind","handle","slotId","typeId").contains(name)) throw invalid();return text();});
        return switch(string(data,"kind")) {
            case "existing" -> { keys(data,Set.of("kind","handle")); yield new Ref.Existing(uuid(string(data,"handle"))); }
            case "fresh" -> { keys(data,Set.of("kind","slotId","typeId")); yield new Ref.Fresh(tool(string(data,"slotId")),tool(string(data,"typeId"))); }
            default -> throw invalid();
        };
    }
    private record State(String kind,String text,Ref target) { @Override public String toString() { return "DecodedState[redacted]"; } }
    private State state() {
        var data=object(name->switch(name) {case "kind","text" -> text(); case "target" -> reference(); default -> throw invalid();});
        String kind=string(data,"kind");
        switch(kind) {
            case "entered" -> keys(data,Set.of("kind","text"));
            case "to" -> keys(data,Set.of("kind","target"));
            case "keep-observed","absent","unresolved" -> keys(data,Set.of("kind"));
            default -> throw invalid();
        }
        return new State(kind,(String)data.get("text"),(Ref)data.get("target"));
    }
    private static FieldValue field(State state) {
        return switch(state.kind()) {
            case "entered" -> { if(state.text().codePointCount(0,state.text().length())>1_048_576) throw invalid(); yield new FieldValue.Entered(state.text()); }
            case "keep-observed" -> new FieldValue.KeepObserved(); case "absent" -> new FieldValue.ExplicitlyAbsent(); case "unresolved" -> new FieldValue.Unresolved(); default -> throw invalid();
        };
    }
    private static ReferenceState referenceState(State state) {
        return switch(state.kind()) { case "to" -> new ReferenceState.To(state.target()); case "keep-observed" -> new ReferenceState.KeepObserved(); case "absent" -> new ReferenceState.Absent(); case "unresolved" -> new ReferenceState.Unresolved(); default -> throw invalid(); };
    }
    private Placement placement() {
        var data=object(name->switch(name) {case "entity" -> reference(); case "documentId","projectionId" -> tool(text()); case "parent" -> parent(); default -> throw invalid();});
        keys(data,Set.of("entity","documentId","projectionId","parent"));
        return new Placement(value(data,"entity",Ref.class),string(data,"documentId"),string(data,"projectionId"),value(data,"parent",Parent.class));
    }
    private Parent parent() {
        var data=object(name->switch(name) {case "kind","documentId","sourceDigest","elementIndex" -> text(); case "entity" -> reference(); default -> throw invalid();});
        return switch(string(data,"kind")) {
            case "fresh" -> {keys(data,Set.of("kind","entity")); yield new Parent.Fresh(value(data,"entity",Ref.Fresh.class));}
            case "existing" -> {
                keys(data,Set.of("kind","documentId","sourceDigest","elementIndex"));
                String index=string(data,"elementIndex"); if(!index.matches("0|[1-9][0-9]{0,4}")) throw invalid();
                yield new Parent.Existing(tool(string(data,"documentId")),digest(string(data,"sourceDigest")),index);
            }
            default -> throw invalid();
        };
    }
    private NativeCommand.Reference publication() {
        var data=object(name->{if(!Set.of("objectId","workspaceRevision").contains(name)) throw invalid(); return text();});
        keys(data,Set.of("objectId","workspaceRevision"));
        return new NativeCommand.Reference(uuid(string(data,"objectId")),revision(string(data,"workspaceRevision")));
    }
    private ProfileDecision profileDecision() {
        var data=object(name->switch(name) {case "kind","slotId","targetSlotId" -> text(); case "target" -> reference(); default -> throw invalid();});
        String slot=tool(string(data,"slotId"));
        return switch(string(data,"kind")) {
            case "create" -> { keys(data,Set.of("kind","slotId","targetSlotId")); yield new ProfileDecision.Create(slot,tool(string(data,"targetSlotId"))); }
            case "use-existing" -> { keys(data,Set.of("kind","slotId","target")); yield new ProfileDecision.UseExisting(slot,value(data,"target",Ref.class)); }
            case "cancel" -> { keys(data,Set.of("kind","slotId")); yield new ProfileDecision.Cancel(slot); }
            default -> throw invalid();
        };
    }
    private Map<String,Object> object(java.util.function.Function<String,Object> read) {
        if(parser.currentToken()!=JsonToken.START_OBJECT) throw invalid();
        var result=new LinkedHashMap<String,Object>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()!=JsonToken.PROPERTY_NAME || result.size()>=256) throw invalid();
            String name=parser.currentName(); parser.nextToken();
            if(result.putIfAbsent(name,read.apply(name))!=null) throw invalid();
        }
        return result;
    }
    private <T> List<T> array(Supplier<T> read,int max) {
        if(parser.currentToken()!=JsonToken.START_ARRAY) throw invalid();
        var values=new ArrayList<T>();
        while(parser.nextToken()!=JsonToken.END_ARRAY) { if(parser.currentToken()==null || values.size()>=max) throw invalid(); values.add(read.get()); }
        return values;
    }
    private String text() {
        if(parser.currentToken()!=JsonToken.VALUE_STRING) throw invalid();
        String value=parser.getString();
        for(int i=0;i<value.length();i++) {
            char ch=value.charAt(i);
            if(Character.isHighSurrogate(ch)) { if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid(); }
            else if(Character.isLowSurrogate(ch)) throw invalid();
        }
        return value;
    }
    private static void keys(Map<String,Object> data,Set<String> base,String... extra) { var keys=new HashSet<>(base); keys.addAll(List.of(extra)); if(!data.keySet().equals(keys)) throw invalid(); }
    private static String string(Map<String,Object> data,String key) { return value(data,key,String.class); }
    private static <T> T value(Map<String,Object> data,String key,Class<T> type) { Object value=data.get(key); if(!type.isInstance(value)) throw invalid(); return type.cast(value); }
    @SuppressWarnings("unchecked") private static <T> List<T> list(Map<String,Object> data,String key) { return (List<T>)value(data,key,List.class); }
    @SuppressWarnings("unchecked") private static <T> Map<String,T> map(Map<String,Object> data,String key) { return (Map<String,T>)value(data,key,Map.class); }
    private static String tool(String value) { if(!value.matches("[a-z][a-z0-9.-]{0,63}")) throw invalid(); return value; }
    private static String digest(String value) { if(!value.matches("[a-f0-9]{64}")) throw invalid(); return value; }
    private static String revision(String value) { if(!value.matches("[1-9][0-9]{0,1023}")) throw invalid(); return value; }
    private static String uuid(String value) { try { if(!UUID.fromString(value).toString().equals(value)) throw invalid(); } catch(IllegalArgumentException failure) { throw invalid(); } return value; }
    private static PlanBodyFailure invalid() { return new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY); }
    private static final class Limited extends FilterInputStream {
        long bytes;
        Limited(InputStream input) { super(input); }
        private void count(int length) { if(length>0 && (bytes+=length)>134_217_728) throw new PlanBodyFailure(PlanBodyFailure.Code.BODY_TOO_LARGE); }
        @Override public int read() throws IOException { int next=super.read(); if(next>=0) count(1); return next; }
        @Override public int read(byte[] target,int offset,int length) throws IOException { int count=in.read(target,offset,(int)Math.min(length,134_217_729-bytes)); count(count); return count; }
    }
}
