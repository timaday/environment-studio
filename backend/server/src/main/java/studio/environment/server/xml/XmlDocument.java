package studio.environment.server.xml;

import java.util.List;
import java.util.Objects;

/** Exact source and tool-owned references; not application identity or export authority. */
public final class XmlDocument {
    public record Span(int start, int end) {
        public Span { if (start < 0 || end < start) throw new IllegalArgumentException("Invalid source span."); }
    }
    public record ExpandedName(String namespaceUri, String localName) {
        public ExpandedName { Objects.requireNonNull(namespaceUri); Objects.requireNonNull(localName); }
    }
    public record AttributeRef(String sourceDigest, int elementIndex, ExpandedName name,
            String qualifiedName, String value, Span valueSpan, char quote) { }
    public record ElementRef(String sourceDigest, int index, ExpandedName name, String qualifiedName,
            List<Integer> ancestry, Span span, Span startTag, int endTagStart, boolean selfClosing,
            List<AttributeRef> attributes) {
        public ElementRef { ancestry = List.copyOf(ancestry); attributes = List.copyOf(attributes); }
    }
    private final String source;
    private final String digest;
    private final List<ElementRef> elements;
    XmlDocument(String source, String digest, List<ElementRef> elements) {
        this.source = source; this.digest = digest; this.elements = List.copyOf(elements);
    }
    public String source() { return source; }
    public String digest() { return digest; }
    public List<ElementRef> elements() { return elements; }
}
