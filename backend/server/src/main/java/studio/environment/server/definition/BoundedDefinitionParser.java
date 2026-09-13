package studio.environment.server.definition;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.*;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.schema.JsonSchema;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/** Consumes one bounded tree without YAML constructors or object binding. */
final class BoundedDefinitionParser {
    static final int MAX_BYTES = 1_048_576;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_NODES = 20_000;
    private static final int MAX_STRING = 16_384;
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final JsonFactory JSON = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_DEPTH)
                    .maxStringLength(MAX_STRING * 2).maxNameLength(MAX_STRING * 2)
                    .maxNumberLength(256).maxDocumentLength(MAX_BYTES).maxTokenCount(MAX_NODES * 2L).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final LoadSettings YAML = LoadSettings.builder().setSchema(new JsonSchema())
            .setAllowDuplicateKeys(false).setAllowRecursiveKeys(false).setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(0).setCodePointLimit(MAX_BYTES).setUseMarks(false).build();

    JsonNode parse(byte[] bytes, DefinitionBytesCompiler.Format format) {
        if (bytes.length > MAX_BYTES) throw new Refusal("RESOURCE_LIMIT");
        String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) { throw new Refusal("INVALID_UTF8"); }
        Budget budget = new Budget();
        try {
            JsonNode result;
            if (format == DefinitionBytesCompiler.Format.JSON) {
                try (JsonParser parser = JSON.createParser(source)) {
                    result = json(parser, parser.nextToken(), 0, budget);
                    if (parser.nextToken() != null) throw new Refusal("INVALID_SYNTAX");
                }
            } else {
                Events events = new Events(new Parse(YAML).parseString(source).iterator());
                events.expect(Event.ID.StreamStart);
                Event start = events.take();
                if (!(start instanceof DocumentStartEvent document) || !document.getTags().isEmpty())
                    throw new Refusal("INVALID_SYNTAX");
                result = yaml(events, events.take(), 0, budget);
                events.expect(Event.ID.DocumentEnd);
                events.expect(Event.ID.StreamEnd);
                if (events.hasNext()) throw new Refusal("INVALID_SYNTAX");
            }
            if (!result.isObject()) throw new Refusal("INVALID_SYNTAX");
            return result;
        } catch (StreamConstraintsException exception) { throw new Refusal("RESOURCE_LIMIT");
        } catch (StreamReadException | YamlEngineException exception) { throw new Refusal("INVALID_SYNTAX"); }
    }
    private JsonNode json(JsonParser parser, JsonToken token, int depth, Budget budget) {
        budget.node();
        if (token == null) throw new Refusal("INVALID_SYNTAX");
        return switch (token) {
            case START_OBJECT -> {
                budget.depth(depth + 1);
                var object = NODES.objectNode();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw new Refusal("INVALID_SYNTAX");
                    budget.node();
                    String key = string(parser.currentName());
                    object.set(key, json(parser, parser.nextToken(), depth + 1, budget));
                }
                yield object;
            }
            case START_ARRAY -> {
                budget.depth(depth + 1);
                var array = NODES.arrayNode();
                while (parser.nextToken() != JsonToken.END_ARRAY)
                    array.add(json(parser, parser.currentToken(), depth + 1, budget));
                yield array;
            }
            case VALUE_STRING -> NODES.stringNode(string(parser.getString()));
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> number(parser.getString());
            case VALUE_TRUE -> NODES.booleanNode(true);
            case VALUE_FALSE -> NODES.booleanNode(false);
            case VALUE_NULL -> NODES.nullNode();
            default -> throw new Refusal("INVALID_SYNTAX");
        };
    }
    private JsonNode yaml(Events events, Event event, int depth, Budget budget) {
        budget.node();
        if (event instanceof NodeEvent node && node.getAnchor().isPresent()) throw new Refusal("INVALID_SYNTAX");
        if (event instanceof ScalarEvent scalar) return scalar(scalar);
        if (!(event instanceof CollectionStartEvent collection) || collection.getTag().isPresent())
            throw new Refusal("INVALID_SYNTAX");
        budget.depth(depth + 1);
        if (event.getEventId() == Event.ID.MappingStart) {
            var object = NODES.objectNode();
            Event next = events.take();
            while (next.getEventId() != Event.ID.MappingEnd) {
                budget.node();
                if (!(next instanceof ScalarEvent key) || key.getAnchor().isPresent()) throw new Refusal("INVALID_SYNTAX");
                JsonNode keyNode = scalar(key);
                if (!keyNode.isString()) throw new Refusal("INVALID_SYNTAX");
                String name = keyNode.asString();
                if (name.equals("<<") || object.has(name)) throw new Refusal("INVALID_SYNTAX");
                object.set(name, yaml(events, events.take(), depth + 1, budget));
                next = events.take();
            }
            return object;
        }
        if (event.getEventId() != Event.ID.SequenceStart) throw new Refusal("INVALID_SYNTAX");
        var array = NODES.arrayNode();
        Event next = events.take();
        while (next.getEventId() != Event.ID.SequenceEnd) {
            array.add(yaml(events, next, depth + 1, budget));
            next = events.take();
        }
        return array;
    }
    private JsonNode scalar(ScalarEvent scalar) {
        if (scalar.getTag().isPresent()) throw new Refusal("INVALID_SYNTAX");
        String value = string(scalar.getValue());
        if (!scalar.isPlain()) return NODES.stringNode(value);
        return switch (value) {
            case "true" -> NODES.booleanNode(true);
            case "false" -> NODES.booleanNode(false);
            case "null" -> NODES.nullNode();
            default -> {
                if (java.util.Set.of(".inf", "-.inf", "+.inf", ".nan", "nan", "infinity", "-infinity", "+infinity")
                        .contains(value.toLowerCase(Locale.ROOT))) throw new Refusal("INVALID_SYNTAX");
                yield NUMBER.matcher(value).matches() ? number(value) : NODES.stringNode(value);
            }
        };
    }
    private static JsonNode number(String token) {
        if (token.length() > 256) throw new Refusal("RESOURCE_LIMIT");
        int exponentIndex = Math.max(token.indexOf('e'), token.indexOf('E'));
        int exponent = 0;
        String mantissa = exponentIndex < 0 ? token : token.substring(0, exponentIndex);
        if (exponentIndex >= 0) {
            String digits = token.substring(exponentIndex + 1);
            boolean negative = digits.startsWith("-");
            int start = digits.startsWith("-") || digits.startsWith("+") ? 1 : 0;
            for (int i = start; i < digits.length(); i++) {
                exponent = exponent * 10 + (digits.charAt(i) - '0');
                if (exponent > 1024) throw new Refusal("RESOURCE_LIMIT");
            }
            if (negative) exponent = -exponent;
        }
        int sign = mantissa.startsWith("-") ? 1 : 0;
        int dot = mantissa.indexOf('.');
        int integerDigits = (dot < 0 ? mantissa.length() : dot) - sign;
        int fractionDigits = dot < 0 ? 0 : mantissa.length() - dot - 1;
        int expanded = Math.max(1, integerDigits + exponent) + Math.max(0, fractionDigits - exponent);
        if (expanded > 1024) throw new Refusal("RESOURCE_LIMIT");
        BigDecimal value = new BigDecimal(token);
        // Draft-07 integer semantics are mathematical, including 1.0 and 1e0.
        if (value.stripTrailingZeros().scale() <= 0) return NODES.numberNode(value.toBigIntegerExact());
        return NODES.numberNode(value);
    }
    private static String string(String value) {
        if (value.codePointCount(0, value.length()) > MAX_STRING) throw new Refusal("RESOURCE_LIMIT");
        // Escaped lone surrogates are not Unicode scalar values.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new Refusal("INVALID_SYNTAX");
            } else if (Character.isLowSurrogate(c)) throw new Refusal("INVALID_SYNTAX");
        }
        return value;
    }
    private static final class Budget {
        private int nodes;
        void node() { if (++nodes > MAX_NODES) throw new Refusal("RESOURCE_LIMIT"); }
        void depth(int depth) { if (depth > MAX_DEPTH) throw new Refusal("RESOURCE_LIMIT"); }
    }
    private record Events(Iterator<Event> iterator) {
        Event take() { if (!iterator.hasNext()) throw new Refusal("INVALID_SYNTAX"); return iterator.next(); }
        void expect(Event.ID id) { if (take().getEventId() != id) throw new Refusal("INVALID_SYNTAX"); }
        boolean hasNext() { return iterator.hasNext(); }
    }
    static final class Refusal extends RuntimeException {
        private final String code;
        Refusal(String code) { super(null, null, false, false); this.code = code; }
        String code() { return code; }
    }
}
