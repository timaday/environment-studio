package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class V3PlanComputedReaderTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static Map<String,Object> key(String value) { return Map.of("computedType", "tones", "derivation", "by-tone", "value", value); }
    static Map<String,Object> contributor(Object selector) { return Map.of("revision", "2", "side", "current", "offset", 0, "limit", 100, "completeDocumentDisclosure", true, "selector", selector); }
    static V3PlanComputedReader.Request read(V3PlanComputedReader.Route route, String body) {
        return new V3PlanComputedReader().read(route, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
    static String node(String value) { return JSON.writeValueAsString(contributor(Map.of("kind", "node", "key", key(value)))); }
    static void malformed(V3PlanComputedReader.Route route, String body) {
        assertEquals(PlanBodyFailure.Code.MALFORMED_BODY, assertThrows(PlanBodyFailure.class, () -> read(route, body)).code());
    }
    @Test void twoMaximumEscapedKeysRoundTripWithoutSmallBodyOrStringLimits() {
        String value = "\t".repeat(1_048_576);
        String body = JSON.writeValueAsString(contributor(Map.of("kind", "cooccurrence", "relation", "pair", "source", key(value), "target", key(value))))
                .replace("\\t", "\\u0009");
        assertTrue(body.length() > 12_582_912); assertTrue(body.length() < 16_777_216);
        var request = read(V3PlanComputedReader.Route.CONTRIBUTORS, body);
        var selector = assertInstanceOf(V3PlanComputedViews.ResultKey.CooccurrenceKey.class, request.selector().orElseThrow());
        assertEquals(value, selector.source().value()); assertEquals(value, selector.target().value());
        assertEquals("ComputedRequest[redacted]", request.toString());
    }
    @Test void utf16LimitPreservesSupplementaryValuesAndExactWhitespace() {
        String value = "𐀀".repeat(524_288);
        var selector = assertInstanceOf(V3PlanComputedViews.ResultKey.NodeKey.class, read(V3PlanComputedReader.Route.CONTRIBUTORS, node(value)).selector().orElseThrow());
        assertEquals(value, selector.key().value());
        malformed(V3PlanComputedReader.Route.CONTRIBUTORS, node(value + "x"));
        for (String exact : List.of(" alpha ", "\r\n\t", "é", "é", "\ue000", "𐀀")) {
            var key = assertInstanceOf(V3PlanComputedViews.ResultKey.NodeKey.class, read(V3PlanComputedReader.Route.CONTRIBUTORS, node(exact)).selector().orElseThrow());
            assertEquals(exact, key.key().value());
        }
    }
    @Test void rejectsNonXmlUnicodeAndMalformedUtf8WithoutDiagnosticValues() {
        for (String raw : List.of("", "\\u0000", "\\u001f", "\\ud800", "\\udc00", "\\uffff", "\\ufffe"))
            malformed(V3PlanComputedReader.Route.CONTRIBUTORS, node("REPLACE").replace("REPLACE", raw));
        var bytes = node("REPLACE").getBytes(StandardCharsets.UTF_8);
        int index = new String(bytes, StandardCharsets.UTF_8).indexOf("REPLACE"); bytes[index] = (byte) 0xff;
        assertEquals(PlanBodyFailure.Code.MALFORMED_BODY, assertThrows(PlanBodyFailure.class,
                () -> new V3PlanComputedReader().read(V3PlanComputedReader.Route.CONTRIBUTORS, new ByteArrayInputStream(bytes))).code());
    }
    @Test void consentAndClosedSelectorsAreRequiredBeforeAnyLookup() {
        var base = new LinkedHashMap<String,Object>(contributor(Map.of("kind", "node", "key", key("missing"))));
        for (Object consent : List.of(false, "true", 1)) { base.put("completeDocumentDisclosure", consent); malformed(V3PlanComputedReader.Route.CONTRIBUTORS, JSON.writeValueAsString(base)); }
        base.remove("completeDocumentDisclosure"); malformed(V3PlanComputedReader.Route.CONTRIBUTORS, JSON.writeValueAsString(base));
        for (Object selector : List.of(Map.of("kind", "node", "key", key("alpha"), "physical", Map.of()),
                Map.of("kind", "node", "key", Map.of("computedType", "tones", "derivation", "by-tone", "value", "alpha", "digest", "f".repeat(64))),
                Map.of("kind", "label", "key", key("alpha")), Map.of("kind", "membership", "relation", "pair", "computed", key("alpha"))))
            malformed(V3PlanComputedReader.Route.CONTRIBUTORS, JSON.writeValueAsString(contributor(selector)));
        malformed(V3PlanComputedReader.Route.CONTRIBUTORS, node("alpha").replace("\"value\":\"alpha\"", "\"value\":\"alpha\",\"value\":\"beta\""));
        malformed(V3PlanComputedReader.Route.CONTRIBUTORS, node("alpha") + "{}");
    }
    @Test void canonicalIntegersAndClosedCollectionShapesPreserveIntmax() {
        for (var route : V3PlanComputedReader.Route.values()) {
            String body = route == V3PlanComputedReader.Route.CONTRIBUTORS ? node("alpha") : "{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":100}";
            assertEquals(Integer.MAX_VALUE, read(route, body.replace("\"offset\":0", "\"offset\":2147483647")).offset());
            for (String bad : List.of("2147483648", "-0", "-1", "01", "1.0", "1e0", "\"1\"", "null", "true"))
                malformed(route, body.replace("\"offset\":0", "\"offset\":" + bad));
            for (String bad : List.of("0", "101")) malformed(route, body.replace("\"limit\":100", "\"limit\":" + bad));
            for (String bad : List.of("0", "02", "2\n", "9".repeat(1025))) malformed(route, body.replace("\"revision\":\"2\"", "\"revision\":\"" + bad + "\""));
            malformed(route, body.substring(0, body.length()-1) + ",\"reveal\":true}");
            if (route != V3PlanComputedReader.Route.CONTRIBUTORS) malformed(route, node("alpha"));
        }
    }
    @Test void exactBodyCeilingsIncludeTrailingWhitespaceAndRejectOneOver() {
        for (var route : List.of(V3PlanComputedReader.Route.NODES, V3PlanComputedReader.Route.CONTRIBUTORS)) {
            int maximum = route == V3PlanComputedReader.Route.CONTRIBUTORS ? 16_777_216 : 16_384;
            String request = route == V3PlanComputedReader.Route.CONTRIBUTORS ? node("alpha") : "{\"revision\":\"2\",\"side\":\"current\",\"offset\":0,\"limit\":1}";
            String body = request + " ".repeat(maximum-request.length());
            assertEquals("2", read(route, body).revision());
            assertEquals(PlanBodyFailure.Code.BODY_TOO_LARGE, assertThrows(PlanBodyFailure.class, () -> read(route, body + " ")).code());
        }
    }
    @Test void physicalSelectorsKeepOriginalExistingAndExplicitFreshForms() {
        var handle = "00000000-0000-4000-8000-000000000001";
        for (var physical : List.of(Map.of("kind", "existing", "handle", handle), Map.of("kind", "fresh", "slotId", "new", "typeId", "item"))) {
            var request = read(V3PlanComputedReader.Route.CONTRIBUTORS, JSON.writeValueAsString(contributor(Map.of("kind", "membership", "relation", "has-tone", "physical", physical, "computed", key("alpha")))));
            var selected = assertInstanceOf(V3PlanComputedViews.ResultKey.MembershipKey.class, request.selector().orElseThrow());
            assertEquals(JSON.valueToTree(physical), JSON.valueToTree(PlanViewProjection.reference(selected.physical())));
        }
        malformed(V3PlanComputedReader.Route.CONTRIBUTORS, JSON.writeValueAsString(contributor(Map.of("kind", "membership", "relation", "has-tone", "physical", Map.of("kind", "existing", "handle", "1-1-1-1-1"), "computed", key("alpha")))));
    }
}
