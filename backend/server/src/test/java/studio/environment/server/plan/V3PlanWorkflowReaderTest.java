package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class V3PlanWorkflowReaderTest {
    private static V3PlanWorkflowReader.Validation read(String text){return new V3PlanWorkflowReader().validation(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));}
    private static final String PAGE="{\"revision\":\"2\",\"section\":\"computed-rules\",\"inputFingerprint\":\""+"a".repeat(64)+"\",\"offset\":2147483647,\"limit\":100}";
    @Test void summaryAndFingerprintBoundPagesAreDistinctClosedCommands(){
        assertEquals(new V3PlanWorkflowReader.Validation.Summary("2"),read("{\"revision\":\"2\"}"));
        assertEquals(new V3PlanWorkflowReader.Validation.Page("2","a".repeat(64),Integer.MAX_VALUE,100),read(PAGE));
        for(String bad:List.of(PAGE.replace("2147483647","2147483648"),PAGE.replace("2147483647","-0"),PAGE.replace("2147483647","1.0"),PAGE.replace("2147483647","1e0"),PAGE.replace("2147483647","\"1\""),PAGE.replace("100}","0}"),PAGE.replace("100}","101}"),PAGE.replace("computed-rules","current-rules"),PAGE.replace("a".repeat(64),"A".repeat(64)),PAGE.replace("\"section\":\"computed-rules\",",""),PAGE.replace("\"revision\":\"2\"","\"revision\":\"2\",\"revision\":\"2\""),"{\"revision\":\"2\",\"owner\":\"invented\"}",PAGE+" {}"))
            assertEquals(PlanBodyFailure.Code.MALFORMED_BODY,assertThrows(PlanBodyFailure.class,()->read(bad)).code());
        assertThrows(PlanBodyFailure.class,()->new PlanViewReader().read(PlanViewRequest.Route.VALIDATION,new ByteArrayInputStream(PAGE.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void byteBudgetAndMalformedUtf8CannotBeCoerced(){
        assertEquals(PlanBodyFailure.Code.BODY_TOO_LARGE,assertThrows(PlanBodyFailure.class,()->read(" ".repeat(16385)+"{\"revision\":\"2\"}")).code());
        assertEquals(PlanBodyFailure.Code.MALFORMED_BODY,assertThrows(PlanBodyFailure.class,()->new V3PlanWorkflowReader().validation(new ByteArrayInputStream(new byte[]{'{','"',(byte)0xc0,(byte)0xaf,'"',':','1','}'}))).code());
    }
}
