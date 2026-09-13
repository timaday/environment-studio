package studio.environment.server.plan;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.Function;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import studio.environment.core.derived.ComputedGraph;
import studio.environment.core.plan.PlanCommand;

/** Closed streaming selectors; exact public values remain transient and bounded. */
final class V3PlanComputedReader {
    enum Route { NODES, MEMBERSHIPS, COOCCURRENCES, RULES, CONTRIBUTORS }
    record Request(String revision, boolean target, int offset, int limit,
                   Optional<V3PlanComputedViews.ResultKey> selector) {
        @Override public String toString() { return "ComputedRequest[redacted]"; }
    }
    private JsonParser parser;
    Request read(Route route, InputStream input) {
        boolean contributors = route == Route.CONTRIBUTORS;
        var constraints = StreamReadConstraints.builder().maxNestingDepth(4).maxTokenCount(128)
                .maxNameLength(64).maxStringLength(contributors ? 1_048_576 : 2048).build();
        var factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(constraints).build();
        try (var reader = new InputStreamReader(new Limited(input, contributors ? 16_777_216 : 16_384),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)); var parsed = factory.createParser(reader)) {
            parser = parsed; parser.nextToken();
            var data = object(name -> switch (name) {
                case "revision" -> text(1024);
                case "side" -> text(7);
                case "offset", "limit" -> integer();
                case "selector" -> { if (!contributors) throw invalid(); yield selector(); }
                case "completeDocumentDisclosure" -> {
                    if (!contributors || parser.currentToken() != JsonToken.VALUE_TRUE) throw invalid();
                    yield Boolean.TRUE;
                }
                default -> throw invalid();
            });
            if (parser.nextToken() != null) throw invalid();
            if (contributors) keys(data, "revision", "side", "selector", "offset", "limit", "completeDocumentDisclosure");
            else keys(data, "revision", "side", "offset", "limit");
            String revision = string(data, "revision");
            if (!revision.matches("[1-9][0-9]{0,1023}")) throw invalid();
            boolean target = switch (string(data, "side")) { case "current" -> false; case "target" -> true; default -> throw invalid(); };
            int limit = (Integer) data.get("limit"); if (limit < 1 || limit > 100) throw invalid();
            return new Request(revision, target, (Integer) data.get("offset"), limit,
                    contributors ? Optional.of((V3PlanComputedViews.ResultKey) data.get("selector")) : Optional.empty());
        } catch (PlanBodyFailure failure) { throw failure; }
        catch (IOException | RuntimeException failure) { throw invalid(); }
        finally { parser = null; }
    }
    private V3PlanComputedViews.ResultKey selector() {
        var data = object(name -> switch (name) {
            case "kind" -> text(12);
            case "relation" -> id(text(64));
            case "key", "computed", "source", "target" -> key();
            case "physical" -> physical();
            default -> throw invalid();
        });
        return switch (string(data, "kind")) {
            case "node" -> { keys(data, "kind", "key"); yield new V3PlanComputedViews.ResultKey.NodeKey((ComputedGraph.Key) data.get("key")); }
            case "membership" -> {
                keys(data, "kind", "relation", "physical", "computed");
                yield new V3PlanComputedViews.ResultKey.MembershipKey(string(data, "relation"),
                        (PlanCommand.Ref) data.get("physical"), (ComputedGraph.Key) data.get("computed"));
            }
            case "cooccurrence" -> {
                keys(data, "kind", "relation", "source", "target");
                yield new V3PlanComputedViews.ResultKey.CooccurrenceKey(string(data, "relation"),
                        (ComputedGraph.Key) data.get("source"), (ComputedGraph.Key) data.get("target"));
            }
            default -> throw invalid();
        };
    }
    private ComputedGraph.Key key() {
        var data = object(name -> switch (name) {
            case "computedType", "derivation" -> id(text(64));
            case "value" -> xml(text(1_048_576));
            default -> throw invalid();
        });
        keys(data, "computedType", "derivation", "value");
        return new ComputedGraph.Key(string(data, "computedType"), string(data, "derivation"), string(data, "value"));
    }
    private PlanCommand.Ref physical() {
        var data = object(name -> switch (name) {
            case "kind" -> text(8); case "handle" -> text(36); case "slotId", "typeId" -> id(text(64));
            default -> throw invalid();
        });
        return switch (string(data, "kind")) {
            case "existing" -> {
                keys(data, "kind", "handle"); String handle = string(data, "handle");
                if (!UUID.fromString(handle).toString().equals(handle)) throw invalid();
                yield new PlanCommand.Ref.Existing(handle);
            }
            case "fresh" -> { keys(data, "kind", "slotId", "typeId"); yield new PlanCommand.Ref.Fresh(string(data, "slotId"), string(data, "typeId")); }
            default -> throw invalid();
        };
    }
    private Map<String,Object> object(Function<String,Object> read) {
        if (parser.currentToken() != JsonToken.START_OBJECT) throw invalid();
        var result = new LinkedHashMap<String,Object>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME || result.size() >= 8) throw invalid();
            String name = parser.currentName(); parser.nextToken();
            if (result.putIfAbsent(name, read.apply(name)) != null) throw invalid();
        }
        return result;
    }
    private String text(int maximum) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) throw invalid();
        String value = parser.getString(); if (value.length() > maximum) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid(); }
            else if (Character.isLowSurrogate(c)) throw invalid();
        }
        return value;
    }
    private static String xml(String value) {
        if (value.isEmpty()) throw invalid();
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i); i += Character.charCount(cp);
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xd7ff
                    || cp >= 0xe000 && cp <= 0xfffd || cp >= 0x10000 && cp <= 0x10ffff)) throw invalid();
        }
        return value;
    }
    private int integer() {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) throw invalid();
        String value = parser.getString(); if (!value.matches("0|[1-9][0-9]{0,9}")) throw invalid();
        return Integer.parseInt(value);
    }
    private static String id(String value) { if (!value.matches("[a-z][a-z0-9.-]{0,63}")) throw invalid(); return value; }
    private static String string(Map<String,Object> data, String key) { if (!(data.get(key) instanceof String value)) throw invalid(); return value; }
    private static void keys(Map<String,Object> data, String... keys) { if (!data.keySet().equals(Set.of(keys))) throw invalid(); }
    private static PlanBodyFailure invalid() { return new PlanBodyFailure(PlanBodyFailure.Code.MALFORMED_BODY); }
    private static final class Limited extends FilterInputStream {
        private final int maximum; private int count;
        Limited(InputStream input, int maximum) { super(input); this.maximum = maximum; }
        private void counted(int n) { if (n > 0 && (count += n) > maximum) throw new PlanBodyFailure(PlanBodyFailure.Code.BODY_TOO_LARGE); }
        @Override public int read() throws IOException { int value = in.read(); if (value >= 0) counted(1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int n = in.read(bytes, offset, Math.min(length, maximum + 1 - count)); counted(n); return n;
        }
    }
}
