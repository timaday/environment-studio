import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import studio.environment.core.Outcome;
import studio.environment.core.definition.DefinitionDraft.Classification;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definition.DefinitionDraft.ValueType;
import studio.environment.core.definitionv2.NativeDefinition.*;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.definitionv3.NativeDefinitionCompiler;
import studio.environment.core.derived.*;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.TargetIntent.*;
import studio.environment.server.planning.*;
import studio.environment.server.projection.*;

/** External independently invented shape; modes separate fixture construction from execution. */
public final class CapacityShapeProbe {
    final int groups, documents, documentBytes, longValues, longLength;
    final int physical;
    CapacityShapeProbe(boolean large) {
        groups = large ? 2000 : 2; documents = large ? 128 : 2;
        documentBytes = large ? 131072 : 4096;
        longValues = large ? 1721 : 1; longLength = large ? 839 : 19;
        physical = groups * 5;
    }
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    static void equal(Object expected,Object actual,String message) { check(Objects.equals(expected,actual),message); }
    static String digest(String text) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    static String number(int value,int width) { check(value >= 0 && width > 0, "NUMBER_DOMAIN"); String digits = Integer.toString(value); return "0".repeat(Math.max(0, width - digits.length())) + digits; }
    static String document(int d) { return "sheet-" + number(d,3); }
    static String projection(int d) { return "items-" + number(d,3); }
    static String identity(int i) { return "p" + number(i,5); }
    String value(int k,boolean target) {
        String prefix=(target ? "T" : "C") + number(k,4);
        return prefix + "x".repeat((k < longValues ? longLength : longLength-1)-prefix.length());
    }
    int physicalIndex(String id) { int i=Integer.parseInt(id.substring(1));check(i>=0&&i<physical,"PHYSICAL_RANGE");equal(identity(i),id,"PHYSICAL_KEY");return i; }
    int groupIndex(String text,boolean target) { int k=Integer.parseInt(text.substring(1,5));check(k>=0&&k<groups,"GROUP_RANGE");equal(value(k,target),text,"GROUP_VALUE");return k; }
    int derivationIndex(ComputedGraph.Key key,boolean target) {
        int d=Integer.parseInt(key.derivation().substring(1));check(d>=0&&d<5,"DERIVATION_RANGE");
        equal("d"+d,key.derivation(),"DERIVATION_ID");equal("c"+d,key.computedType(),"COMPUTED_TYPE");groupIndex(key.value(),target);return d;
    }
    Map<String,String> sources(boolean target) {
        var result=new TreeMap<String,String>();long total=0;
        for(int d=0;d<documents;d++) {
            var xml=new StringBuilder("<items>\n");
            for(int i=d;i<physical;i+=documents) xml.append("<item id='").append(identity(i)).append("' tone='").append(value(i%groups,target)).append("'/>\n");
            int padding=documentBytes-xml.length()-"<!---->\n</items>\n".length();check(padding>=0,"NEGATIVE_PADDING");
            xml.append("<!--").append("P".repeat(padding)).append("-->\n</items>\n");
            String source=xml.toString();equal(documentBytes,source.length(),"DOCUMENT_UTF16");equal(documentBytes,source.getBytes(StandardCharsets.UTF_8).length,"DOCUMENT_UTF8");
            check(result.put(document(d),source)==null,"DUPLICATE_DOCUMENT");total+=source.getBytes(StandardCharsets.UTF_8).length;
        }
        equal((long)documents*documentBytes,total,"TOTAL_SOURCE_BYTES");return Collections.unmodifiableMap(result);
    }
    NativeCompilationResult.Checked definition() {
        var fields = List.of(new Field("id", ValueType.TEXT, true, Classification.STRUCTURAL, Sensitivity.PUBLIC, true, false),
                new Field("tone", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true),
                new Field("optional", ValueType.TEXT, false, Classification.ENVIRONMENT, Sensitivity.PUBLIC, true, true));
        var types = new ArrayList<NativeDefinition.ComputedType>();
        var derivations = new ArrayList<NativeDefinition.Derivation>();
        for (int i = 0; i < 6; i++) {
            types.add(new NativeDefinition.ComputedType("c" + i, "Invented group " + i));
            derivations.add(new NativeDefinition.Derivation("d" + i, "item", i == 5 ? "optional" : "tone", "c" + i, "m" + i));
        }
        var pairs = new ArrayList<NativeDefinition.Cooccurrence>();
        for (int i = 0; i < 32; i++) pairs.add(new NativeDefinition.Cooccurrence("zero-" + String.format(Locale.ROOT, "%02d", i),
                "d0", "d5", BigInteger.ZERO, BigInteger.ONE));
        var logical = new NativeDefinition.Logical(List.of(new EntityType("item", "Invented item", fields, new Identity("id"))),
                List.of(), List.of(), List.of(Operation.RETAIN_ENTITY, Operation.BIND_FIELD), types, derivations, pairs, List.of());
        var declarations = new ArrayList<Document>();
        for (int i = 0; i < documents; i++) declarations.add(new Document(document(i), Integer.toString(i + 1), List.of(
                new Projection(projection(i), "item", List.of(new ExpandedName("", "items"), new ExpandedName("", "item")),
                        List.of(new FieldMapping("id", new ExpandedName("", "id")), new FieldMapping("tone", new ExpandedName("", "tone")),
                                new FieldMapping("optional", new ExpandedName("", "optional"))), List.of()))));
        var binding = new Binding("mock-pg", Engine.POSTGRESQL, Storage.TEXT, "mock_schema", "mock_table", "mock_key", "mock_xml", KeyType.INT64, declarations);
        var result = new NativeDefinitionCompiler().compile(new NativeDefinition("mock-capacity", BigInteger.ONE, logical, List.of(binding)));
        System.out.println("COMPILER_RESULT=" + result.getClass().getSimpleName() + " CODES=" + result.diagnostics().stream().map(d -> d.code()).toList());
        check(result instanceof NativeCompilationResult.Incomplete, "ACTUAL_COMPILER_MUST_REMAIN_INCOMPLETE");
        check(!result.diagnostics().isEmpty() && result.diagnostics().stream().allMatch(d -> d.code().equals("MECHANISM_UNQUALIFIED")), "UNEXPECTED_COMPILER_BLOCKER");
        return ((NativeCompilationResult.Incomplete) result).checked();
    }

    DerivedInput.Pin pin(NativeCompilationResult.Checked definition,Map<String,String> sources,String revision) throws Exception {
        var hashes=new TreeMap<String,String>();for(var source:sources.entrySet())hashes.put(source.getKey(),digest(source.getValue()));
        return new DerivedInput.Pin(revision,definition.logicalDigest(),"mock-pg",definition.bindingDigests().get("mock-pg"),hashes);
    }
    static DerivedGraphProjectionAdapter.Snapshot snapshot(DerivedInput.Pin pin,Map<String,String> sources) {
        return new DerivedGraphProjectionAdapter.Snapshot(pin.revisionToken(),pin.logicalDigest(),pin.bindingId(),pin.bindingDigest(),sources.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new DocumentSource(e.getKey(),e.getValue())).toList());
    }
    ObservedGraph.Origin origin(int i,Map<String,String> hashes) { int d=i%documents;return new ObservedGraph.Origin(document(d),projection(d),hashes.get(document(d)),i/documents+1,List.of(0)); }
    DerivedInput.Ref.Observed reference(int i,Map<String,String> hashes) { return new DerivedInput.Ref.Observed(new ObservedGraph.Key("item",identity(i)),origin(i,hashes)); }
    void proof(DerivedInput.Proof supplied,int i,boolean target,Map<String,String> sources,Map<String,String> hashes) {
        check(supplied instanceof DerivedInput.Proof.Observed,"OBSERVED_PROOF");var location=((DerivedInput.Proof.Observed)supplied).location();
        check(location.selector().isEmpty(),"DIRECT_SELECTOR");String doc=document(i%documents),xml=sources.get(doc),text=value(i%groups,target);
        String prefix="<item id='"+identity(i)+"' tone='";int row=xml.indexOf(prefix);check(row>=0,"EXPECTED_ROW");int start=row+prefix.length();
        var expected=new DerivedInput.AttributePin(doc,hashes.get(doc),i/documents+1,new ExpandedName("","tone"),"tone",text,start,start+text.length(),'\'');
        equal(expected,location.value(),"COMPLETE_ATTRIBUTE_PIN");equal(text,xml.substring(start,start+text.length()),"PIN_LEXICAL_VALUE");
    }
    int contributor(ComputedGraph.Contributor contributor,boolean target,Map<String,String> sources,Map<String,String> hashes) {
        check(contributor.physical() instanceof DerivedInput.Ref.Observed,"OBSERVED_CONTRIBUTOR");var ref=(DerivedInput.Ref.Observed)contributor.physical();int i=physicalIndex(ref.key().identity());
        equal(reference(i,hashes),ref,"COMPLETE_CONTRIBUTOR_ORIGIN");equal(1,contributor.roles().size(),"ROLE_COUNT");var role=contributor.roles().getFirst();equal("tone",role.field(),"ROLE_FIELD");proof(role.proof(),i,target,sources,hashes);return i;
    }
    void verify(DerivedGraphProjectionAdapter.Complete projection,Map<String,String> sources,boolean target) throws Exception {
        var hashes=new TreeMap<String,String>();for(var e:sources.entrySet())hashes.put(e.getKey(),digest(e.getValue()));
        equal(hashes,projection.input().pin().documentDigests(),"COMPLETE_DOCUMENT_PINS");equal(documents,projection.sources().documents().size(),"DOCUMENT_COUNT");
        var seenDocuments=new HashSet<String>();for(var doc:projection.sources().documents()){check(seenDocuments.add(doc.documentId()),"DUPLICATE_SOURCE");equal(sources.get(doc.documentId()),doc.source(),"EXACT_XML");equal(hashes.get(doc.documentId()),doc.digest(),"SOURCE_DIGEST");}equal(sources.keySet(),seenDocuments,"COMPLETE_SOURCES");
        equal(physical,projection.physical().entities().size(),"PHYSICAL_COUNT");equal(List.of(),projection.physical().edges(),"PHYSICAL_EDGES");
        var seenPhysical=new BitSet();for(var entity:projection.physical().entities()){int i=physicalIndex(entity.key().identity());check(!seenPhysical.get(i),"DUPLICATE_PHYSICAL");seenPhysical.set(i);equal(new ObservedGraph.Key("item",identity(i)),entity.key(),"PHYSICAL_TYPE");equal(Map.of("id",identity(i),"tone",value(i%groups,target)),entity.fields(),"COMPLETE_PHYSICAL_FIELDS");equal(origin(i,hashes),entity.origin(),"COMPLETE_PHYSICAL_ORIGIN");}
        equal(physical,seenPhysical.cardinality(),"COMPLETE_PHYSICAL");equal(DerivedInput.Kind.OBSERVED,projection.input().kind(),"INPUT_KIND");equal(physical,projection.input().entities().size(),"INPUT_COUNT");equal(List.of(),projection.input().edges(),"INPUT_EDGES");
        var seenInputs=new BitSet();for(var entity:projection.input().entities()){check(entity.reference() instanceof DerivedInput.Ref.Observed,"OBSERVED_INPUT");var ref=(DerivedInput.Ref.Observed)entity.reference();int i=physicalIndex(ref.key().identity());check(!seenInputs.get(i),"DUPLICATE_INPUT");seenInputs.set(i);equal(reference(i,hashes),ref,"COMPLETE_INPUT_REFERENCE");equal(Set.of("tone","optional"),entity.fields().keySet(),"COMPLETE_INPUT_FIELDS");check(entity.fields().get("optional") instanceof DerivedInput.FieldState.Absent,"OPTIONAL_ABSENT");check(entity.fields().get("tone") instanceof DerivedInput.FieldState.Present,"TONE_PRESENT");var present=(DerivedInput.FieldState.Present)entity.fields().get("tone");equal(value(i%groups,target),present.text(),"INPUT_VALUE");proof(present.proof(),i,target,sources,hashes);}equal(physical,seenInputs.cardinality(),"COMPLETE_INPUTS");
        var graph=projection.derived().graph();equal(groups*5,graph.nodes().size(),"COMPUTED_COUNT");equal(physical*5,graph.memberships().size(),"MEMBERSHIP_COUNT");equal(List.of(),graph.cooccurrences(),"PAIR_EDGES");
        var seenNodes=new BitSet();long bytes=0;int links=0;
        for(var node:graph.nodes()) {int d=derivationIndex(node.key(),target),k=groupIndex(node.key().value(),target),index=d*groups+k;check(!seenNodes.get(index),"DUPLICATE_COMPUTED");seenNodes.set(index);bytes+=node.key().value().getBytes(StandardCharsets.UTF_8).length;links+=node.contributors().size();
            var expected=new ArrayList<Integer>();for(int n=0;n<5;n++)expected.add(k+n*groups);expected.sort(Comparator.comparingInt((Integer i)->i%documents).thenComparingInt(i->i/documents));
            equal(5,node.contributors().size(),"NODE_CONTRIBUTOR_COUNT");var actual=new ArrayList<Integer>();for(var c:node.contributors())actual.add(contributor(c,target,sources,hashes));equal(expected,actual,"COMPLETE_ORDERED_NODE_CONTRIBUTORS");}
        equal(groups*5,seenNodes.cardinality(),"COMPLETE_COMPUTED_KEYS");equal(5L*(longValues*longLength+(groups-longValues)*(longLength-1)),bytes,"IDENTITY_UTF8_BYTES");
        var seenMembers=new BitSet();for(var member:graph.memberships()){int d=derivationIndex(member.computed(),target),k=groupIndex(member.computed().value(),target);check(member.physical() instanceof DerivedInput.Ref.Observed,"OBSERVED_MEMBER");var ref=(DerivedInput.Ref.Observed)member.physical();int i=physicalIndex(ref.key().identity());equal(reference(i,hashes),ref,"COMPLETE_MEMBER_REFERENCE");equal(i%groups,k,"MEMBER_GROUP");equal("m"+d,member.relation(),"MEMBER_RELATION");int index=d*physical+i;check(!seenMembers.get(index),"DUPLICATE_MEMBER");seenMembers.set(index);equal(1,member.contributors().size(),"MEMBER_CONTRIBUTOR_COUNT");equal(i,contributor(member.contributors().getFirst(),target,sources,hashes),"MEMBER_CONTRIBUTOR_REFERENCE");links+=member.contributors().size();}
        equal(physical*5,seenMembers.cardinality(),"COMPLETE_MEMBERSHIPS");equal(physical*10,links,"COMPLETE_LINKS");
        equal(groups*32,projection.derived().rules().size(),"RULE_COUNT");var seenRules=new BitSet();for(var rule:projection.derived().rules()){equal(DerivedResult.RuleKind.COOCCURRENCE,rule.kind(),"RULE_KIND");equal(BigInteger.ZERO,rule.actual(),"RULE_ACTUAL");equal(BigInteger.ZERO,rule.minimum(),"RULE_MIN");equal(BigInteger.ONE,rule.maximum(),"RULE_MAX");equal(Outcome.PASS,rule.outcome(),"RULE_OUTCOME");var key=rule.source().orElseThrow();equal(0,derivationIndex(key,target),"RULE_SOURCE_DERIVATION");int k=groupIndex(key.value(),target),r=Integer.parseInt(rule.declaration().substring(5));check(r>=0&&r<32,"RULE_RANGE");equal("zero-"+number(r,2),rule.declaration(),"RULE_DECLARATION");int index=r*groups+k;check(!seenRules.get(index),"DUPLICATE_RULE");seenRules.set(index);}equal(groups*32,seenRules.cardinality(),"COMPLETE_RULES");
    }
    void run(boolean fixtureOnly) throws Exception {
        var definition=definition();var sources=sources(false);var expected=sources(true);var current=pin(definition,sources,"current-1");
        long values=5L*(longValues*longLength+(groups-longValues)*(longLength-1));
        if(fixtureOnly){System.out.println("SHAPE_FIXTURE_PASS documents="+documents+" sourceBytesPerSide="+((long)documents*documentBytes)+" physical="+physical+" projectedComputed="+(groups*5)+" projectedMemberships="+(physical*5)+" projectedLinks="+(physical*10)+" projectedValueBytes="+values+" projectedRules="+(groups*32)+" compiler=INCOMPLETE execution=NOT_RUN");return;}
        var snapshot=snapshot(current,sources);var projected=new DerivedGraphProjectionAdapter().project(definition,current,snapshot,()->false);
        check(projected instanceof DerivedGraphProjectionAdapter.Complete,"CURRENT_PROJECTION_REFUSED");verify((DerivedGraphProjectionAdapter.Complete)projected,sources,false);
        var target=pin(definition,sources,"target-2");var decisions=new ArrayList<EntityDecision>();for(int i=0;i<physical;i++)decisions.add(new EntityDecision.Retain(new Ref.Existing(new ObservedGraph.Key("item",identity(i))),Map.of("id",new FieldValue.KeepObserved(),"tone",new FieldValue.Entered(value(i%groups,true)),"optional",new FieldValue.KeepObserved()),Map.of()));
        var bases=new TreeMap<String,Optional<String>>();for(String id:sources.keySet())bases.put(id,Optional.empty());
        var result=new DerivedTargetMaterializer().materialize(definition,current,snapshot,target,new DerivedTargetInputAdapter.Decisions(target,new TargetIntent(decisions,List.of())),bases,List.of(),()->false);
        check(result instanceof DerivedTargetMaterializer.Complete,"TARGET_MATERIALIZATION_REFUSED");var complete=(DerivedTargetMaterializer.Complete)result;verify(complete.finalProjection(),expected,true);verify(complete.preliminary().current(),sources,false);
        var finalHashes=new TreeMap<String,String>();for(var e:expected.entrySet())finalHashes.put(e.getKey(),digest(e.getValue()));equal(physical,complete.provenance().size(),"PROVENANCE_COUNT");for(int i=0;i<physical;i++){var key=new ObservedGraph.Key("item",identity(i));equal(reference(i,finalHashes),complete.provenance().get(new Ref.Existing(key)),"COMPLETE_RETAINED_PROVENANCE");}
        equal(current,complete.preliminary().current().input().pin(),"ORIGINAL_PIN");equal(pin(definition,expected,"target-2"),complete.finalProjection().input().pin(),"COMPLETE_FINAL_PIN");check(!current.documentDigests().equals(finalHashes),"UNCHANGED_TARGET");
        System.out.println("SHAPE_CURRENT_TARGET_PASS documents="+documents+" sourceBytesPerSide="+((long)documents*documentBytes)+" physical="+physical+" computed="+(groups*5)+" memberships="+(physical*5)+" links="+(physical*10)+" valueBytes="+values+" rules="+(groups*32)+" compiler=INCOMPLETE");
    }
    public static void main(String[] args) throws Exception { check(args.length==2,"EXPLICIT_MODES_REQUIRED");check(Set.of("small","large").contains(args[0]),"SHAPE_MODE");check(Set.of("fixture","execute").contains(args[1]),"EXECUTION_MODE");new CapacityShapeProbe(args[0].equals("large")).run(args[1].equals("fixture")); }
}
