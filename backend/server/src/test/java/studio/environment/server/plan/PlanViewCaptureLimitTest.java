package studio.environment.server.plan;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.profile.ProfileCapture;
import studio.environment.core.definitionv2.NativeCompilationResult;
import studio.environment.core.observation.ObservationResult;
import studio.environment.server.definition.*;
import studio.environment.server.xml.*;
class PlanViewCaptureLimitTest {
    @Test void portableByteLimitRefusesLargeInventedCaptureWithoutReturningPartialSource() throws Exception {
        var fixture=new PlanContentAdapterTest();var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var tree=json.readTree(Files.readAllBytes(Path.of("../../fixtures/native-v2/definition.json")));
        for(var rule:tree.get("logical").get("rules"))if(rule.get("type").asString().equals("palette"))((tools.jackson.databind.node.ObjectNode)rule).put("maximum",10000);
        var definition=new PublishedDefinition(fixture.definition().reference(),"independent-capture-limit",assertInstanceOf(NativeCompilationResult.ReadyToPublish.class,new NativeDefinitionBytesCompiler().compile(json.writeValueAsBytes(tree),DefinitionBytesCompiler.Format.JSON)),List.of());
        for(int rows:new int[]{1000,6000}){
            var source=new StringBuilder("<tiles xmlns='urn:mock:tiles'><palette id='shared' shade='warm'/>");
            for(int i=1;i<rows;i++)source.append("<palette id='p").append(i).append("' shade='invented'/>");source.append("</tiles>");
            var documents=new ArrayList<>(fixture.observation(definition).documents());
            var xml=assertInstanceOf(XmlResult.Accepted.class,new LosslessXmlAdapter().project(source.toString())).document();
            documents.set(1,new ObservationResult.Document("palette-sheet",new ObservationResult.Key("INT64","2"),source.toString(),source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,source.length(),xml.digest()));
            var observed=new ObservationResult.Observation("a".repeat(64),definition.compiled().checked().logicalDigest(),definition.compiled().checked().bindingDigests().get("mock-pg"),documents,Map.of());
            var current=assertInstanceOf(ContentResult.Complete.class,fixture.adapter.project(definition,"mock-pg",observed)).content();
            var mappings=new ArrayList<ProfileCapture.SlotMapping>();int index=0;
            for(var entity:current.graph().entities())mappings.add(new ProfileCapture.SlotMapping(entity.key(),"neutral-"+index++,"Neutral "+"n".repeat(120)));
            var command=new ProfileCapture.Command("neutral-limit",java.math.BigInteger.ONE,mappings);
            if(rows==1000){var capture=fixture.adapter.capture(definition,"mock-pg",current,command);assertTrue(capture.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<1_048_576);}
            else assertEquals(PlanRefusal.Code.PROFILE_REFUSED,assertThrows(PlanRefusal.class,()->fixture.adapter.capture(definition,"mock-pg",current,command)).code());
        }
    }
}
