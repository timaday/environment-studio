package studio.environment.server.planning;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.definitionv3.NativeDefinition;
import studio.environment.core.observation.ObservationResult;
import static studio.environment.core.definitionv2.NativeDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual XML source limits and actual compiler: many zero-outgoing rules, not fabricated target proofs. */
public final class V3WorkflowLargeHttpWitnesses {
    private V3WorkflowLargeHttpWitnesses() { }
    public static NativeCompilationResult.Checked definition(boolean wide){
        var base=V3WorkflowHttpWitnesses.definition().definition();var l=base.logical();var b=base.bindings().getFirst();
        var pairs=new ArrayList<NativeDefinition.Cooccurrence>();for(int i=0;i<32;i++)pairs.add(new NativeDefinition.Cooccurrence("pair-"+i,"by-tone","by-finish",BigInteger.ZERO,BigInteger.ONE));
        var logical=new NativeDefinition.Logical(l.entityTypes(),l.relations(),l.rules(),l.operationCapabilities(),l.computedTypes(),l.derivations(),pairs,l.computedRules());
        var projection=b.documents().getFirst().entities().getFirst();var docs=new ArrayList<Document>();for(int i=0;i<3;i++)docs.add(new Document("part-"+i,Integer.toString(i+1),List.of(new Projection("items-"+i,projection.type(),projection.path(),projection.fields(),projection.references()))));
        var binding=new Binding(b.id(),b.engine(),b.storage(),b.schema(),b.table(),b.keyColumn(),b.xmlColumn(),b.keyType(),docs);
        var result=assertInstanceOf(NativeCompilationResult.Incomplete.class,new studio.environment.core.definitionv3.NativeDefinitionCompiler().compile(new NativeDefinition(wide?"mock-wide":"mock-tail",BigInteger.ONE,logical,List.of(binding))));
        assertTrue(result.diagnostics().stream().allMatch(d->d.code().equals("MECHANISM_UNQUALIFIED")));return result.checked();
    }
    public static ObservationResult observation(boolean wide){
        var checked=definition(wide);var docs=new ArrayList<ObservationResult.Document>();
        for(int part=0;part<3;part++){
            var xml=new StringBuilder("<items>");
            if(wide)xml.append("<item id='n").append(part).append("' tone='").append("\\".repeat(900000)).append(part).append("'/>");
            else for(int i=part;i<2000;i+=3)xml.append("<item id='n").append(i).append("' tone='").append("k".repeat(800)).append(String.format(Locale.ROOT,"%04d",i)).append("'/>");
            xml.append("</items>");String source=xml.toString();assertTrue(source.length()<=1048576);
            docs.add(new ObservationResult.Document("part-"+part,new ObservationResult.Key("int64",Integer.toString(part+1)),source,source.getBytes(StandardCharsets.UTF_8).length,source.length(),DerivedTargetInputAdapterTest.digest(source)));
        }
        var base=((ObservationResult.Complete)V3WorkflowHttpWitnesses.observation()).observation();
        return new ObservationResult.Complete(new ObservationResult.Observation(base.fingerprint(),checked.logicalDigest(),checked.bindingDigests().get("mock-pg"),docs,base.evidence()));
    }
}
