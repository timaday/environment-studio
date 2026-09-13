package studio.environment.server.export;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
/** Mechanical package metadata only; no client or export qualification. */
class ChildPropertyPackageTest {
 @Test void closedPackageMetadataRecognizesExactNewDependencyWithoutChangingPayload() throws Exception {
  var json=JsonMapper.builder().build();var manifest=json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json")));
  var execution=(ObjectNode)manifest.get("execution").deepCopy();
  var payload=Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json"));
  var admission=new PackageAdmission();var before=assertInstanceOf(PackageAdmission.Result.Accepted.class,admission.read(json.writeValueAsBytes(execution),payload));
  ((ObjectNode)execution.get("mechanisms")).put("xml-child-property-v1","1");
  var changed=assertInstanceOf(PackageAdmission.Result.Accepted.class,admission.read(json.writeValueAsBytes(execution),payload));
  assertArrayEquals(before.canonicalPayload(),changed.canonicalPayload());assertNotEquals(before.programDigest(),changed.programDigest());
  ((ObjectNode)execution.get("mechanisms")).put("xml-child-property-v1","2");
  assertEquals("SCHEMA_VIOLATION",assertInstanceOf(PackageAdmission.Result.Rejected.class,admission.read(json.writeValueAsBytes(execution),payload)).code());
 }
 @Test void pinnedAdmissionRejectsWrongBindingsDigestsAndUnneededDependency() throws Exception {
  var json=JsonMapper.builder().build();
  var source=Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json"));
  var definition=assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,
   new studio.environment.server.definition.NativeDefinitionBytesCompiler().compile(source,studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON));
  var manifest=json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json")));
  var baseline=(ObjectNode)manifest.get("execution").deepCopy();
  baseline.put("bindingId","mock-pg").put("logicalDigest",definition.checked().logicalDigest()).put("bindingDigest",definition.checked().bindingDigests().get("mock-pg"));
  var payload=(ObjectNode)json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json")));payload.put("bindingId","mock-pg");
  var admission=new PackageAdmission();byte[] payloadBytes=json.writeValueAsBytes(payload);
  assertInstanceOf(PackageAdmission.Result.Accepted.class,admission.readPinned(definition,"mock-pg",json.writeValueAsBytes(baseline),payloadBytes));
  for(String problem:java.util.List.of("logical","binding","extra-mechanism","selected-binding")) {
   var changed=baseline.deepCopy();String selected="mock-pg";
   switch(problem) {
    case "logical" -> changed.put("logicalDigest","0".repeat(64));
    case "binding" -> changed.put("bindingDigest","0".repeat(64));
    case "extra-mechanism" -> ((ObjectNode)changed.get("mechanisms")).put("xml-child-property-v1","1");
    case "selected-binding" -> selected="mock-oracle";
    default -> throw new AssertionError();
   }
   assertInstanceOf(PackageAdmission.Result.Rejected.class,admission.readPinned(definition,selected,json.writeValueAsBytes(changed),payloadBytes),problem);
  }
 }

 @Test void selectedBindingUsesItsOwnDependenciesInsideAMixedDefinition() throws Exception {
  var json=JsonMapper.builder().build();
  for(int childBinding:java.util.List.of(0,1)) {
   var source=(ObjectNode)json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
   var mapping=(ObjectNode)source.at("/bindings/"+childBinding+"/documents/0/entities/0/fields/1");mapping.remove("attribute");
   var child=mapping.putObject("childProperty");child.putObject("element").put("namespaceUri","urn:mock:properties").put("localName","entry");
   child.putObject("discriminatorAttribute").put("namespaceUri","").put("localName","key");child.put("discriminatorValue","tone");
   child.putObject("valueAttribute").put("namespaceUri","").put("localName","value");
   var definition=assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,
    new studio.environment.server.definition.NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(source),studio.environment.server.definition.DefinitionBytesCompiler.Format.JSON));
   var manifest=json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/manifest.json")));
   var execution=(ObjectNode)manifest.get("execution").deepCopy();
   execution.put("bindingId","mock-pg").put("logicalDigest",definition.checked().logicalDigest()).put("bindingDigest",definition.checked().bindingDigests().get("mock-pg"));
   var versions=(ObjectNode)execution.get("mechanisms");if(childBinding==0)versions.put("xml-child-property-v1","1");
   var payload=(ObjectNode)json.readTree(Files.readAllBytes(Path.of("../../fixtures/guarded-package-v1/payload.json")));payload.put("bindingId","mock-pg");
   var admission=new PackageAdmission();var bytes=json.writeValueAsBytes(payload);
   assertInstanceOf(PackageAdmission.Result.Accepted.class,admission.readPinned(definition,"mock-pg",json.writeValueAsBytes(execution),bytes));
   if(childBinding==0)versions.remove("xml-child-property-v1");else versions.put("xml-child-property-v1","1");
   assertEquals("MECHANISM_MISMATCH",assertInstanceOf(PackageAdmission.Result.Rejected.class,
    admission.readPinned(definition,"mock-pg",json.writeValueAsBytes(execution),bytes)).code());
  }
 }

}
