package studio.environment.core.definitionv2;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.definition.DefinitionDiagnostic;
import studio.environment.server.definition.*;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;
import static studio.environment.core.definitionv2.NativeDefinition.*;
class ChildDiagnosticBudgetTest {
 @Test void admittedCollisionInputMustNotAllocatePairwiseDuplicateDiagnostics() throws Exception {
  int count=200;var json=JsonMapper.builder().build();
  var source=(ObjectNode)json.readTree(Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
  var projection=(ObjectNode)source.at("/bindings/0/documents/0/entities/0");var original=projection.get("fields").get(0).deepCopy();
  var fields=projection.putArray("fields");for(int i=0;i<count;i++)fields.add(original.deepCopy());
  var child=fields.addObject().put("field","tone").putObject("childProperty");
  child.putObject("element").put("namespaceUri","").put("localName","entry");child.putObject("discriminatorAttribute").put("namespaceUri","").put("localName","key");child.put("discriminatorValue","tone");child.putObject("valueAttribute").put("namespaceUri","").put("localName","value");
  byte[] bytes=json.writeValueAsBytes(source);
  var compiled=assertInstanceOf(NativeCompilationResult.Rejected.class,new NativeDefinitionBytesCompiler().compile(bytes,DefinitionBytesCompiler.Format.JSON));
  assertTrue(compiled.diagnostics().stream().allMatch(d->d.phase()==DefinitionDiagnostic.Phase.SEMANTIC));
  var values=new ArrayList<FieldMapping>();for(int i=0;i<count;i++)values.add(new FieldMapping("tag",new ExpandedName("","id")));
  values.add(new FieldMapping("tone",new ChildProperty(new ExpandedName("","entry"),new ExpandedName("","key"),"tone",new ExpandedName("","value"))));
  var doc=new Document("mock","1",List.of(new Projection("items","glyph",List.of(new ExpandedName("","items"),new ExpandedName("","item")),values,List.of())));
  class Count extends ArrayList<DefinitionDiagnostic> {int allocations;@Override public boolean add(DefinitionDiagnostic d){allocations++;return true;}}
  var diagnostics=new Count();NativeChildMappings.document(doc,false,"/mock",diagnostics);
  assertTrue(diagnostics.allocations<=count,"sourceBytes="+bytes.length+" mappings="+count+" diagnosticAllocations="+diagnostics.allocations);
 }
}
