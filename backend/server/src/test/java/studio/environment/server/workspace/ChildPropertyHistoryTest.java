package studio.environment.server.workspace;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.workspace.*;
import tools.jackson.databind.node.ObjectNode;
class ChildPropertyHistoryTest {
 @Test void frozenPreExtensionPublicationRemainsReadableAndByteIdentical() throws Exception {
  byte[] old=Files.readAllBytes(Path.of("../../fixtures/native-v2/historical-direct-snapshot.json"));
  var codec=new NativeSnapshotCodec();
  var restored=assertDoesNotThrow(()->codec.decode(old));
  assertEquals("5e953fca9954ca69b6dd2b179978f245dbc17c74de06ab0cdeefe07808272ba7",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(old)));
  assertEquals("e8de0efc4cee2573e225060ccd7ff1f372ff48a1d96ae8a7ada34896e2d2dfc2",restored.publication().orElseThrow().digest());
  assertEquals("published",restored.state());
  assertArrayEquals(old,codec.encode(restored));
  assertDoesNotThrow(()->NativeWorkspace.eligibleDefinition((NativeRevision.Definition)restored.content()));
 }
 @Test void childDraftRoundTripsThroughClosedHistoricalCodecWithoutPublicationAuthority() throws Exception {
  String text=childSource();
  var command=new NativeCommand.SaveDefinition("00000000-0000-4000-8000-000000000083","0","00000000-0000-4000-8000-000000000084",DraftCommand.Format.JSON,text);
  var content=new NativeWorkspaceCompiler().definition(command);
  var revision=new NativeRevision(command.objectId(),"1",command.format(),text,NativeWorkspaceDigests.source(text),"native-compiler-v2","2",content,Optional.empty());
  var codec=new NativeSnapshotCodec();byte[] encoded=codec.encode(revision);
  assertEquals(revision,assertDoesNotThrow(()->codec.decode(encoded)));
  var model=codec.model(revision);assertTrue(model.at("/bindings/0/documents/0/entities/0/fields/1/childProperty").isObject());
  assertFalse(model.at("/bindings/0/documents/0/entities/0/fields/1").has("locator"));
 }
 private static String childSource() throws Exception {
  var json=NativeSnapshotCodec.JSON;
  var source=(ObjectNode)json.readTree(Files.readString(Path.of("../../fixtures/native-v2/definition.json")));
  var mapping=(ObjectNode)source.at("/bindings/0/documents/0/entities/0/fields/1");mapping.remove("attribute");
  var child=mapping.putObject("childProperty");child.putObject("element").put("namespaceUri","urn:mock:properties").put("localName","entry");
  child.putObject("discriminatorAttribute").put("namespaceUri","").put("localName","key");child.put("discriminatorValue","tone");
  child.putObject("valueAttribute").put("namespaceUri","").put("localName","value");
  return json.writeValueAsString(source);
 }
 @Test void persistedIncompleteChildHistoryNeedsExplicitRecompileAndPublicationAfterQualification() throws Exception {
  var owner=new studio.environment.core.session.Owner("https://independent.invalid","mock-child-history");
  var directory=Files.createTempDirectory("es-child-history-",java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
  SqliteDraftStore.initialize(directory);
  var store=new NativeSqliteStore(new SqliteDraftStore(directory));var compiler=new NativeWorkspaceCompiler();
  String id="00000000-0000-4000-8000-000000000085";String source=childSource();
  var original=new NativeCommand.SaveDefinition(id,"0",UUID.randomUUID().toString(),DraftCommand.Format.JSON,source);
  var checked=compiler.definition(original).checked();
  var blocker=new studio.environment.core.definition.DefinitionDiagnostic(studio.environment.core.definition.DefinitionDiagnostic.Phase.PUBLICATION,
   "MECHANISM_UNQUALIFIED","/bindings/0","Select qualified server mechanisms.");
  var historical=new NativeRevision(id,"1",original.format(),source,NativeWorkspaceDigests.source(source),"native-compiler-v2","2",new NativeRevision.Definition(checked,List.of(blocker)),Optional.empty());
  store.append(owner,original,historical);
  store=new NativeSqliteStore(new SqliteDraftStore(directory));
  var workspace=new NativeWorkspace(store,compiler,o->true);
  assertEquals(historical,store.read(owner,id,Optional.of("1"),false));
  assertEquals(historical,workspace.mutate(owner,original));
  var policies=new ArrayList<NativeCommand.Policy>();checked.definition().bindings().forEach(b->b.documents().forEach(d->policies.add(new NativeCommand.Policy(b.id(),d.id(),"deny"))));
  var premature=new NativeCommand.PublishDefinition(id,"1",UUID.randomUUID().toString(),policies);
  assertThrows(WorkspaceRejection.class,()->workspace.mutate(owner,premature));
  var resave=new NativeCommand.SaveDefinition(id,"1",UUID.randomUUID().toString(),original.format(),source);
  var compiled=workspace.mutate(owner,resave);
  assertTrue(((NativeRevision.Definition)compiled.content()).ready(),"A new explicit compilation must use the now-qualified child mechanism.");
  var publish=new NativeCommand.PublishDefinition(id,"2",UUID.randomUUID().toString(),policies);
  var publication=workspace.mutate(owner,publish);assertEquals("3",publication.workspaceRevision());assertEquals("published",publication.state());
  var reopenedStore=new NativeSqliteStore(new SqliteDraftStore(directory));var reopened=new NativeWorkspace(reopenedStore,compiler,o->true);
  assertEquals(historical,reopened.mutate(owner,original));assertEquals(compiled,reopened.mutate(owner,resave));assertEquals(publication,reopened.mutate(owner,publish));
  assertEquals(historical,reopenedStore.read(owner,id,Optional.of("1"),false));
  assertThrows(WorkspaceRejection.class,()->NativeWorkspace.eligibleDefinition((NativeRevision.Definition)historical.content()));
  assertEquals(WorkspaceRefusal.Code.CONFLICT,assertThrows(WorkspaceRefusal.class,()->reopened.mutate(owner,
   new NativeCommand.SaveDefinition(id,"1",UUID.randomUUID().toString(),original.format(),source))).code());
 }
 @Test void anExistingPlanKeepsItsPublishedDirectMappingsAfterANewChildPublication() throws Exception {
  var fixture=new NativeWorkspaceTest();fixture.setup();
  var directDraft=fixture.workspace.mutate(fixture.owner,fixture.save("0"));var direct=fixture.workspace.mutate(fixture.owner,fixture.publish(directDraft));
  var reference=new NativeCommand.Reference(fixture.id,direct.workspaceRevision());
  var authority=new studio.environment.core.session.SessionLedger(java.time.Clock.systemUTC(),ignored->{});
  var lease=((studio.environment.core.session.SessionLedger.Accepted)authority.admit("invented-old-plan",fixture.owner)).lease();
  var received=new ArrayList<studio.environment.core.observation.ObservationPort.Selection>();
  var port=new studio.environment.core.observation.ObservationPort() {
   public studio.environment.core.observation.ObservationResult observe(Selection selection,studio.environment.core.observation.TransientCredentials credentials,Cancellation cancellation){throw new AssertionError("No database observation is authorized by this pin control.");}
   public Reservation reserve(Selection selection){received.add(selection);return new Reservation.Refused(studio.environment.core.observation.ObservationResult.Code.DESTINATION_UNQUALIFIED);}
  };
  var bridge=new PlanWorkspaceBridge(fixture.store);
  var plans=new studio.environment.core.plan.HostedPlanService(authority::guard,bridge,
   Map.of("invented-destination",new studio.environment.core.plan.PlanPorts.Destination("invented-destination",studio.environment.core.definitionv2.NativeDefinition.Engine.POSTGRESQL,port)),
   new studio.environment.server.plan.PlanContentAdapter(),System::nanoTime);
  var oldPlan=plans.create(lease,UUID.randomUUID().toString(),reference,"mock-pg","invented-destination");
  var childDraft=fixture.workspace.mutate(fixture.owner,new NativeCommand.SaveDefinition(fixture.id,"2",UUID.randomUUID().toString(),DraftCommand.Format.JSON,childSource()));
  var child=fixture.workspace.mutate(fixture.owner,fixture.publish(childDraft));var newReference=new NativeCommand.Reference(fixture.id,child.workspaceRevision());
  assertEquals(reference,plans.view(lease,Optional.of(oldPlan.planId())).definition());
  assertEquals(studio.environment.core.plan.PlanRefusal.Code.CONFLICT,assertThrows(studio.environment.core.plan.PlanRefusal.class,
   ()->plans.reserve(lease,oldPlan.planId(),new studio.environment.core.plan.HostedPlanService.Mutation("2",UUID.randomUUID().toString()))).code());
  assertTrue(received.isEmpty());
  assertEquals(studio.environment.core.plan.PlanRefusal.Code.OBSERVATION_REFUSED,assertThrows(studio.environment.core.plan.PlanRefusal.class,
   ()->plans.reserve(lease,oldPlan.planId(),new studio.environment.core.plan.HostedPlanService.Mutation("1",UUID.randomUUID().toString()))).code());
  assertEquals(bridge.definition(fixture.owner,reference).compiled(),received.getFirst().compiled());assertEquals(4,received.getFirst().compiled().checked().mechanisms().size());
  plans.discard(lease,oldPlan.planId(),new studio.environment.core.plan.HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
  var newPlan=plans.create(lease,UUID.randomUUID().toString(),newReference,"mock-pg","invented-destination");
  assertEquals(studio.environment.core.plan.PlanRefusal.Code.NOT_FOUND,assertThrows(studio.environment.core.plan.PlanRefusal.class,()->plans.summary(lease,oldPlan.planId())).code());
  assertThrows(studio.environment.core.plan.PlanRefusal.class,()->plans.reserve(lease,newPlan.planId(),new studio.environment.core.plan.HostedPlanService.Mutation("1",UUID.randomUUID().toString())));
  assertEquals(2,received.size());assertEquals(5,received.getLast().compiled().checked().mechanisms().size());
  assertNotEquals(received.getFirst().compiled().checked().bindingDigests().get("mock-pg"),received.getLast().compiled().checked().bindingDigests().get("mock-pg"));
  assertEquals(received.getFirst().compiled().checked().logicalDigest(),received.getLast().compiled().checked().logicalDigest());
 }
}
