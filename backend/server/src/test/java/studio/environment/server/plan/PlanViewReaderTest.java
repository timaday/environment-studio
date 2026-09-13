package studio.environment.server.plan;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
class PlanViewReaderTest {
    private PlanViewRequest read(PlanViewRequest.Route route,String source){return new PlanViewReader().read(route,new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));}
    @Test void canonicalPageAndLargeRevisionRemainExact(){
        var page=assertInstanceOf(PlanViewRequest.GraphPage.class,read(PlanViewRequest.Route.ENTITIES,"{\"revision\":\"9007199254740993\",\"side\":\"target\",\"offset\":50000,\"limit\":100}"));
        assertEquals("9007199254740993",page.revision());assertEquals(50000,page.offset());assertEquals(PlanViewRequest.Side.TARGET,page.side());
    }
    @Test void consentAndCanonicalIntegerTokensAreNotDefaults(){
        for(String offset:new String[]{"-0","0.0","0e0","\"0\"","50001"})
            assertThrows(PlanBodyFailure.class,()->read(PlanViewRequest.Route.ENTITIES,"{\"revision\":\"1\",\"side\":\"current\",\"offset\":"+offset+",\"limit\":1}"));
        assertThrows(PlanBodyFailure.class,()->read(PlanViewRequest.Route.DOCUMENT,"{\"revision\":\"1\",\"side\":\"current\",\"documentId\":\"one\",\"mode\":\"raw\",\"completeDocumentDisclosure\":false}"));
    }
    @Test void uppercaseViewEnumsAreNotSilentlyNormalized(){
        assertThrows(PlanBodyFailure.class,()->read(PlanViewRequest.Route.DOCUMENT,"{\"revision\":\"1\",\"side\":\"current\",\"documentId\":\"one\",\"mode\":\"RAW\",\"completeDocumentDisclosure\":true}"));
    }
}
