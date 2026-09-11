package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.HostedPlanService;

class V3PlanReviewReaderTest {
    private static final String UUID="c0000000-0000-0000-0000-000000000001";
    private static final String BODY="{\"expectedRevision\":\"2\",\"requestId\":\""+UUID+"\",\"inputFingerprint\":\""+"a".repeat(64)+"\",\"destinationId\":\"mock-destination\",\"artifactIntent\":\"protected-self-contained\"}";
    private static HostedPlanService.ReviewCommand read(String source){return new PlanMetadataReader().review(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));}
    @Test void exactFlatCommandRetainsEveryIdentityField() {
        var result=read(BODY);assertEquals(new HostedPlanService.Mutation("2",UUID),result.mutation());
        assertEquals("a".repeat(64),result.inputFingerprint());assertEquals("mock-destination",result.destinationId());
        assertEquals(HostedPlanService.ArtifactIntent.PROTECTED_SELF_CONTAINED,result.artifactIntent());
        assertEquals("9".repeat(1024),read(BODY.replace("\"2\"","\""+"9".repeat(1024)+"\"")).mutation().expectedRevision());
    }
    @Test void malformedAndClaimedAuthorityCannotEnterTheCommand() {
        var cases=new ArrayList<>(List.of("{}",BODY+" {}",BODY.replace("\"2\"","2"),BODY.replace("\"2\"","\"02\""),BODY.replace("\"2\"","\"0\""),BODY.replace("\"2\"","\""+"9".repeat(1025)+"\""),BODY.replace(UUID,UUID.toUpperCase(Locale.ROOT)),BODY.replace(UUID,"1-1-1-1-1"),BODY.replace("a".repeat(64),"A".repeat(64)),BODY.replace("mock-destination","mock_destination"),BODY.replace("protected-self-contained","masked-preview"),BODY.replace("\"artifactIntent\":\"protected-self-contained\"","\"artifactIntent\":null"),BODY.replace("\"2\"","\"2\",\"expectedRevision\":\"2\""),BODY.replace("mock-destination","\\ud800")));
        for(String extra:List.of("\"outcome\":\"PASS\"","\"policies\":{}","\"xml\":\"mock\"","\"mutation\":{}"))cases.add(BODY.substring(0,BODY.length()-1)+","+extra+"}");
        for(String bad:cases)assertEquals(PlanBodyFailure.Code.MALFORMED_BODY,assertThrows(PlanBodyFailure.class,()->read(bad)).code());
        assertEquals(PlanBodyFailure.Code.BODY_TOO_LARGE,assertThrows(PlanBodyFailure.class,()->read(" ".repeat(16385)+BODY)).code());
        assertEquals(PlanBodyFailure.Code.MALFORMED_BODY,assertThrows(PlanBodyFailure.class,()->new PlanMetadataReader().review(new ByteArrayInputStream(new byte[]{'{','"',(byte)0xc0,(byte)0xaf,'"',':','1','}'}))).code());
    }
}
