package studio.environment.server.plan;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.PlanCommand;
import studio.environment.core.planning.TargetIntent;
import static org.junit.jupiter.api.Assertions.*;

class PlanCommandReaderTest {
    private static final String PREFIX="\"expectedRevision\":\"9007199254740993\",\"requestId\":\"00000000-0000-4000-8000-000000000001\"";
    @Test void preservesExactCaseAndRevisionWhenDiscriminantAppearsLast() {
        var command=read("{"+PREFIX+",\"entity\":{\"handle\":\"00000000-0000-4000-8000-000000000002\",\"kind\":\"existing\"},\"fieldId\":\"tone\",\"state\":{\"text\":\"MiXeD 𐀀\\tValue\",\"kind\":\"entered\"},\"kind\":\"bind-field\"}");
        assertEquals("9007199254740993",command.mutation().expectedRevision());
        var action=assertInstanceOf(PlanCommand.Action.BindField.class,command.action());
        assertEquals("MiXeD 𐀀\tValue",assertInstanceOf(TargetIntent.FieldValue.Entered.class,action.state()).text());
        assertFalse(command.toString().contains("MiXeD"));
    }
    @Test void refusesUnknownDuplicateAndLoneSurrogate() {
        for(String json:new String[]{"{"+PREFIX+",\"kind\":\"discard\",\"unknown\":true}","{"+PREFIX+",\"kind\":\"discard\",\"kind\":\"discard\"}","{"+PREFIX+",\"kind\":\"discard\"}{}","{"+PREFIX+",\"kind\":\"bind-field\",\"entity\":{\"kind\":\"fresh\",\"slotId\":\"one\",\"typeId\":\"sample\"},\"fieldId\":\"tone\",\"state\":{\"kind\":\"entered\",\"text\":\"\\uD800\"}}"})
            assertThrows(PlanBodyFailure.class,()->read(json));
    }
    @Test void transportLimitAndDeadlineFailuresKeepTheirTypedStatusThroughStreamingParser() {
        for(var code:new PlanBodyFailure.Code[]{PlanBodyFailure.Code.BODY_TOO_LARGE,PlanBodyFailure.Code.BODY_DEADLINE,PlanBodyFailure.Code.CANCELLED}) {
            var source=new java.io.InputStream() { public int read() { throw new PlanBodyFailure(code); } };
            assertEquals(code,assertThrows(PlanBodyFailure.class,()->new PlanCommandReader().read(source)).code());
        }
    }
    @Test void allClosedSemanticCommandsDecodeWithoutInferringEnteredValues() {
        String fresh="{\"kind\":\"fresh\",\"slotId\":\"one\",\"typeId\":\"sample\"}";
        String existing="{\"kind\":\"existing\",\"handle\":\"00000000-0000-4000-8000-000000000002\"}";
        String create="{\"kind\":\"create\",\"entity\":"+fresh+",\"fields\":{\"tone\":{\"kind\":\"unresolved\"}},\"references\":{}}";
        String move="{\"relationId\":\"children\",\"parent\":"+existing+",\"child\":"+fresh+"}";
        String[] bodies={
            "\"kind\":\"replace-draft\",\"draft\":{\"entities\":[],\"containment\":[],\"placements\":[]}",
            "\"kind\":\"batch-upsert\",\"changes\":[{\"decision\":"+create+",\"placements\":[]}],\"containment\":[]",
            "\"kind\":\"upsert-entity\",\"decision\":"+create+",\"placements\":[]",
            "\"kind\":\"forget-entity-decision\",\"entity\":"+fresh,
            "\"kind\":\"bind-field\",\"entity\":"+fresh+",\"fieldId\":\"tone\",\"state\":{\"kind\":\"keep-observed\"}",
            "\"kind\":\"bind-reference\",\"entity\":"+fresh+",\"relationId\":\"links\",\"state\":{\"kind\":\"to\",\"target\":"+existing+"}",
            "\"kind\":\"move-containment\",\"decision\":"+move+",\"placements\":[]",
            "\"kind\":\"compose-profile\",\"profile\":{\"objectId\":\"00000000-0000-4000-8000-000000000003\",\"workspaceRevision\":\"1\"},\"previewDigest\":\""+"a".repeat(64)+"\",\"selectedRoots\":[\"one\"],\"decisions\":[]",
            "\"kind\":\"discard\""};
        var kinds=new java.util.HashSet<Class<?>>();
        for(String body:bodies) kinds.add(read("{"+PREFIX+","+body+"}").action().getClass());
        assertEquals(9,kinds.size());
    }
    private PlanCommand read(String json) { return new PlanCommandReader().read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))); }
}
