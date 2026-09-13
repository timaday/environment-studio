package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Invalid response controls prove the actual socket assertion cannot silently skip v3. */
class V3WorkflowWireAssertionsTest {
    private static final String PLAN = "/api/v3/plans/11111111-1111-4111-8111-111111111111/";

    @Test void malformedSuccessfulResponsesFailForEveryWorkflowFamily() {
        assertAll(List.of("profile-captures", "profile-previews", "validations", "reviews").stream()
                .flatMap(route -> List.of("{}", "").stream().map(body ->
                        (org.junit.jupiter.api.function.Executable) () -> assertThrows(AssertionError.class,
                                () -> V3WorkflowWireAssertions.verify(PLAN + route, 200, body)))));
    }

    @Test void ruleRowsRequireExactDecimalStringsAndAllDeclaredFields() {
        String valid = """
                {"revision":"2","inputFingerprint":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "targetComplete":true,"total":1,"offset":0,"nextOffset":null,
                 "items":[{"kind":"ENTITY_COUNT","declaration":"mock-count","source":null,
                   "actual":"1","minimum":"0","maximum":"2","outcome":"PASS"}]}
                """;
        assertDoesNotThrow(() -> V3WorkflowWireAssertions.verify(PLAN + "validations", 200, valid));
        assertAll(
                () -> assertThrows(AssertionError.class, () -> V3WorkflowWireAssertions.verify(PLAN + "validations", 200,
                        valid.replace("\"actual\":\"1\"", "\"actual\":1"))),
                () -> assertThrows(AssertionError.class, () -> V3WorkflowWireAssertions.verify(PLAN + "validations", 200,
                        valid.replace("\"maximum\":\"2\",", ""))),
                () -> assertThrows(AssertionError.class, () -> V3WorkflowWireAssertions.verify(PLAN + "validations", 200,
                        valid.replace("\"actual\":\"1\"", "\"actual\":\"01\""))));
    }

    @Test void refusalsUseTheirOwnContract() {
        for (String route : List.of("profile-captures", "profile-previews", "validations", "reviews"))
            assertDoesNotThrow(() -> V3WorkflowWireAssertions.verify(PLAN + route, 409, "{\"code\":\"CONFLICT\"}"));
    }

    @Test void computedKeysPreserveXmlUnicodeBoundsIncludingSupplementaryCharacters() {
        for (int point : new int[]{9, 10, 13, 32, 0xd7ff, 0xe000, 0xfffd, 0x10000, 0x10ffff})
            assertDoesNotThrow(() -> V3WorkflowWireAssertions.verify(PLAN + "validations", 200, keyPage(point)));
        for (int point : new int[]{0, 8, 11, 12, 14, 31, 0xd800, 0xdfff, 0xfffe, 0xffff})
            assertThrows(AssertionError.class, () -> V3WorkflowWireAssertions.verify(PLAN + "validations", 200, keyPage(point)));
    }

    private static String keyPage(int point) {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        tools.jackson.databind.node.ObjectNode page = mapper.valueToTree(Map.of(
                "revision", "2", "inputFingerprint", "a".repeat(64), "targetComplete", true,
                "total", 1, "offset", 0,
                "items", List.of(Map.of("kind", "COOCCURRENCE", "declaration", "mock-pair",
                        "source", Map.of("computedType", "mock-group", "derivation", "mock-key", "value", new String(Character.toChars(point))),
                        "actual", "1", "minimum", "0", "maximum", "2", "outcome", "PASS"))));
        page.putNull("nextOffset");
        return mapper.writeValueAsString(page);
    }
}
