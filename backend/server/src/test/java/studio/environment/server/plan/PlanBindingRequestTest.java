package studio.environment.server.plan;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.environment.core.plan.PlanCommand;
import static studio.environment.server.plan.PlanViewRequest.*;
class PlanBindingRequestTest {
    static final String EXISTING="{\"kind\":\"existing\",\"handle\":\"00000000-0000-4000-8000-000000000001\"}";
    static final String FRESH="{\"kind\":\"fresh\",\"slotId\":\"new-glyph\",\"typeId\":\"glyph\"}";
    static PlanViewRequest read(Route route,String source){return new PlanViewReader().read(route,new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));}
    static String body(String entity,String offset){return "{\"revision\":\"9\",\"entity\":"+entity+",\"offset\":"+offset+",\"limit\":100}";}
    static String locations(String entity,String offset){return body(entity,offset).replace("\"limit\":100","\"limit\":100,\"fieldId\":\"tag\",\"side\":\"target\",\"completeDocumentDisclosure\":true");}
    @Test void fullLocationOffsetAndExistingReferenceAreReadExactly(){var request=assertInstanceOf(BindingLocations.class,read(Route.BINDING_LOCATIONS,locations(EXISTING,"2147483647")));assertEquals(Integer.MAX_VALUE,request.offset());assertEquals(Side.TARGET,request.side());assertTrue(request.disclosed());assertInstanceOf(PlanCommand.Ref.Existing.class,request.entity());}
    @Test void coordinatesRequireExplicitDocumentDisclosure(){
        String source=locations(EXISTING,"0").replace(",\"completeDocumentDisclosure\":true","");
        assertThrows(PlanBodyFailure.class,()->read(Route.BINDING_LOCATIONS,source));
        for(String value:new String[]{"false","null","1","\"true\""})assertThrows(PlanBodyFailure.class,()->read(Route.BINDING_LOCATIONS,source.replace("\"limit\":100","\"limit\":100,\"completeDocumentDisclosure\":"+value)));
    }
    @Test void freshBindingReferenceUsesExplicitProvenance(){var request=assertInstanceOf(Bindings.class,read(Route.BINDINGS,body(FRESH,"256")));assertEquals(new PlanCommand.Ref.Fresh("new-glyph","glyph"),request.entity());assertEquals(256,request.offset());}
    @Test void boundAndIntegerExceptionsAreRouteSpecific(){
        for(var offset:new String[]{"-1","-0","0e0","0.0","\"0\"","2147483648","99999999999999999999"})assertThrows(PlanBodyFailure.class,()->read(Route.BINDING_LOCATIONS,locations(EXISTING,offset)));
        for(var offset:new String[]{"257","50000","2147483647"})assertThrows(PlanBodyFailure.class,()->read(Route.BINDINGS,body(EXISTING,offset)));
        assertThrows(PlanBodyFailure.class,()->read(Route.ENTITIES,"{\"revision\":\"9\",\"side\":\"current\",\"offset\":50001,\"limit\":1}"));
    }
    @Test void entityAndFieldShapesAreClosed(){
        for(var entity:new String[]{"null", "{}", EXISTING.replace("existing","fresh"), FRESH.replace("glyph\"}","glyph\",\"handle\":\"00000000-0000-4000-8000-000000000001\"}"), FRESH.replace("new-glyph","BAD"), EXISTING.replace("00000000-0000-4000-8000-000000000001","1-1-1-1-1")})assertThrows(PlanBodyFailure.class,()->read(Route.BINDINGS,body(entity,"0")));
        assertThrows(PlanBodyFailure.class,()->read(Route.BINDING_LOCATIONS,locations(EXISTING,"0").replace("\"tag\"","\"tag/extra\"")));
        assertThrows(PlanBodyFailure.class,()->read(Route.BINDINGS,body(EXISTING,"0").replace("\"limit\":100","\"limit\":100,\"side\":\"target\"")));
    }
}
