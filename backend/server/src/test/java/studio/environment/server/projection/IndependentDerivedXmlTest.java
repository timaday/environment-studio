package studio.environment.server.projection;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import studio.environment.core.derived.DerivedInput;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.definitionv2.NativeDefinitionCompiler;
import studio.environment.core.definitionv2.NativeCompilationResult;
class IndependentDerivedXmlTest {
 @Test void actualEscapedDiscriminatorAndNonBmpPrefixRetainExactUtf16Spans(){
  var d=DerivedGraphProjectionAdapterTest.definition(true,1,false);
  String xml=DerivedGraphProjectionAdapterTest.CHILD.replace("<m:item", "<!--𐀀--><m:item").replace("p:key=\"tone\"","p:key=\"to&#x6e;e\"").replace("al&#x70;ha","x&#xD;y");
  var sources=List.of(new DocumentSource("sheet-0",xml));var pin=DerivedGraphProjectionAdapterTest.pin(d,sources);
  var result=assertInstanceOf(DerivedGraphProjectionAdapter.Complete.class,new DerivedGraphProjectionAdapter().project(d,pin,DerivedGraphProjectionAdapterTest.snapshot(pin,sources),()->false));
  var present=assertInstanceOf(DerivedInput.FieldState.Present.class,result.input().entities().getFirst().fields().get("tone"));
  assertEquals("x\ry",present.text());var location=((DerivedInput.Proof.Observed)present.proof()).location();
  assertEquals(xml.indexOf("x&#xD;y"),location.value().valueStart());assertEquals("x&#xD;y",xml.substring(location.value().valueStart(),location.value().valueEnd()));
  var selector=location.selector().orElseThrow().discriminator();assertEquals("tone",selector.decodedValue());assertEquals("to&#x6e;e",xml.substring(selector.valueStart(),selector.valueEnd()));
 }
 @Test void legacyPhysicalDelegationPreservesBothDirectAndChildGraphs(){
  for(boolean child:List.of(false,true)){
   var v3=DerivedGraphProjectionAdapterTest.definition(child,1,false);var d=v3.definition();var l=d.logical();
   var physical=new NativeDefinition.Logical(l.entityTypes(),l.relations(),l.rules(),l.operationCapabilities());
   var v2=assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionCompiler().compile(new NativeDefinition(d.id(),d.revision(),physical,d.bindings())));
   var sources=List.of(new DocumentSource("sheet-0",child?DerivedGraphProjectionAdapterTest.CHILD:DerivedGraphProjectionAdapterTest.DIRECT));var pin=DerivedGraphProjectionAdapterTest.pin(v3,sources);
   var old=assertInstanceOf(ProjectionResult.Accepted.class,new GraphProjectionAdapter().project(v2,"mock-pg",sources));
   var result=assertInstanceOf(DerivedGraphProjectionAdapter.Complete.class,new DerivedGraphProjectionAdapter().project(v3,pin,DerivedGraphProjectionAdapterTest.snapshot(pin,sources),()->false));
   assertEquals(old.graph(),result.physical());assertEquals(old.projection(),result.sources());assertNotEquals(old.logicalDigest(),pin.logicalDigest());
  }
 }
}
