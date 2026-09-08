package studio.environment.server.xml;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import static studio.environment.server.xml.XmlDocument.Span;

/** Bounded positions only. XML validity and decoded semantics are independently checked by the qualified StAX parser. */
final class XmlLexicalScanner {
    static final int MAX_CHARS = 1_048_576;
    static final int MAX_TOKENS = 100_000;
    record Attribute(String qualifiedName, Span valueSpan, char quote) { }
    static final class Element {
        final int index;
        final String qualifiedName;
        final List<Integer> ancestry;
        final int start;
        final int startTagEnd;
        final boolean selfClosing;
        final List<Attribute> attributes;
        int endTagStart;
        int end;
        Element(int index, String qualifiedName, List<Integer> ancestry, int start, int startTagEnd,
                boolean selfClosing, List<Attribute> attributes) {
            this.index = index; this.qualifiedName = qualifiedName; this.ancestry = List.copyOf(ancestry);
            this.start = start; this.startTagEnd = startTagEnd; this.selfClosing = selfClosing;
            this.attributes = List.copyOf(attributes);
            if (selfClosing) { this.endTagStart = startTagEnd - 2; this.end = startTagEnd; }
        }
    }
    private final String source;
    private int position;
    private int tokens;
    private final List<Element> elements = new ArrayList<>();
    private final ArrayDeque<Element> stack = new ArrayDeque<>();
    XmlLexicalScanner(String source) { this.source = source; }

    List<Element> scan() {
        validateCharacters(source);
        while (position < source.length()) {
            token();
            if (source.charAt(position) != '<') {
                int end = source.indexOf('<', position);
                if (end < 0) end = source.length();
                references(position, end); position = end;
            } else if (source.startsWith("<!--", position)) terminated("-->", 4);
            else if (source.startsWith("<![CDATA[", position)) terminated("]]>", 9);
            else if (source.startsWith("<?", position)) terminated("?>", 2);
            else if (source.startsWith("<!", position)) throw new XmlRefusal("UNSUPPORTED_XML");
            else if (source.startsWith("</", position)) close();
            else open();
        }
        if (!stack.isEmpty() || elements.isEmpty()) throw new XmlRefusal("INVALID_XML");
        return List.copyOf(elements);
    }
    static void validateCharacters(String source) {
        if (source.length() > MAX_CHARS) throw new XmlRefusal("RESOURCE_LIMIT");
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == source.length() || !Character.isLowSurrogate(source.charAt(i))) throw new XmlRefusal("INVALID_XML");
            } else if (Character.isLowSurrogate(c) || !(c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xfffd))
                throw new XmlRefusal("INVALID_XML");
        }
    }
    private void open() {
        int start = position++;
        String name = name();
        List<Attribute> attributes = new ArrayList<>();
        while (true) {
            whitespace();
            if (position >= source.length()) throw new XmlRefusal("INVALID_XML");
            if (source.charAt(position) == '>' || source.charAt(position) == '/') break;
            token();
            if (attributes.size() >= 256) throw new XmlRefusal("RESOURCE_LIMIT");
            String attributeName = name(); whitespace(); expect('='); whitespace();
            if (position >= source.length()) throw new XmlRefusal("INVALID_XML");
            char quote = source.charAt(position++);
            if (quote != '\'' && quote != '"') throw new XmlRefusal("INVALID_XML");
            int valueStart = position;
            int valueEnd = source.indexOf(quote, position);
            if (valueEnd < 0) throw new XmlRefusal("INVALID_XML");
            references(valueStart, valueEnd);
            attributes.add(new Attribute(attributeName, new Span(valueStart, valueEnd), quote));
            position = valueEnd + 1;
        }
        boolean selfClosing = source.charAt(position) == '/';
        if (selfClosing) position++;
        expect('>');
        if (elements.size() >= 20_000 || stack.size() >= 128) throw new XmlRefusal("RESOURCE_LIMIT");
        List<Integer> ancestry = stack.stream().map(element -> element.index).toList();
        Element element = new Element(elements.size(), name, ancestry, start, position, selfClosing, attributes);
        elements.add(element);
        if (!selfClosing) stack.addLast(element);
    }
    private void close() {
        int start = position; position += 2;
        String name = name(); whitespace(); expect('>');
        if (stack.isEmpty() || !stack.getLast().qualifiedName.equals(name)) throw new XmlRefusal("INVALID_XML");
        Element element = stack.removeLast(); element.endTagStart = start; element.end = position;
    }
    private String name() {
        int start = position;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (isWhitespace(c) || c == '/' || c == '>' || c == '=' || c == '<' || c == '\'' || c == '"') break;
            position++;
        }
        if (start == position) throw new XmlRefusal("INVALID_XML");
        return source.substring(start, position);
    }
    private void terminated(String delimiter, int prefixLength) {
        int end = source.indexOf(delimiter, position + prefixLength);
        if (end < 0) throw new XmlRefusal("INVALID_XML");
        position = end + delimiter.length();
    }
    private void references(int start, int end) {
        for (int i = start; i < end; i++) if (source.charAt(i) == '&') token();
    }
    private void whitespace() { while (position < source.length() && isWhitespace(source.charAt(position))) position++; }
    private static boolean isWhitespace(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
    private void expect(char c) {
        if (position >= source.length() || source.charAt(position++) != c) throw new XmlRefusal("INVALID_XML");
    }
    private void token() { if (++tokens > MAX_TOKENS) throw new XmlRefusal("RESOURCE_LIMIT"); }
}
