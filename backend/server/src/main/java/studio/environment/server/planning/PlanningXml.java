package studio.environment.server.planning;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.planning.ExpectedTarget;
import studio.environment.server.xml.XmlDocument;

/** Bounded lexical generation for new markup only. Existing document spelling is never serialized. */
final class PlanningXml {
    static final int MAX_CHARS = 1_048_576;
    static final long MAX_BYTES = 16L * 1024 * 1024;
    static final String XML = "http://www.w3.org/XML/1998/namespace";
    static final String XMLNS = "http://www.w3.org/2000/xmlns/";
    static final Comparator<String> UTF8 = (a, b) -> java.util.Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    static final Comparator<NativeDefinition.ExpandedName> NAMES = Comparator.comparing(NativeDefinition.ExpandedName::namespaceUri, UTF8).thenComparing(NativeDefinition.ExpandedName::localName, UTF8);
    private PlanningXml() { }
    static String create(NativeDefinition.Projection projection, ExpectedTarget.Entity entity, Map<String, String> references) {
        Map<NativeDefinition.ExpandedName, String> attributes = new TreeMap<>(NAMES);
        projection.fields().forEach(mapping -> { if (entity.fields().containsKey(mapping.field())) attributes.put(mapping.attribute(), entity.fields().get(mapping.field())); });
        projection.references().forEach(mapping -> { if (references.containsKey(mapping.relation())) attributes.put(mapping.attribute(), references.get(mapping.relation())); });
        var name = projection.path().getLast();
        TreeSet<String> namespaces = new TreeSet<>(UTF8); namespaces.add(name.namespaceUri()); attributes.keySet().forEach(n -> namespaces.add(n.namespaceUri()));
        namespaces.remove(""); namespaces.remove(XML);
        Map<String, String> prefixes = new TreeMap<>(UTF8); int index = 0;
        for (String namespace : namespaces) prefixes.put(namespace, "ns" + index++);
        prefixes.put(XML, "xml");
        Bounded out = new Bounded(); out.add("<"); out.add(qualified(name, prefixes));
        if (name.namespaceUri().isEmpty()) out.add(" xmlns=\"\"");
        for (String namespace : namespaces) { out.add(" xmlns:"); out.add(prefixes.get(namespace)); out.add("=\""); escape(namespace, out); out.add("\""); }
        for (var attribute : attributes.entrySet()) { out.add(" "); out.add(qualified(attribute.getKey(), prefixes)); out.add("=\""); escape(attribute.getValue(), out); out.add("\""); }
        out.add("/>"); return out.value();
    }
    private static String qualified(NativeDefinition.ExpandedName name, Map<String, String> prefixes) { return name.namespaceUri().isEmpty() ? name.localName() : prefixes.get(name.namespaceUri()) + ":" + name.localName(); }
    static List<NativeDefinition.ExpandedName> path(XmlDocument document, XmlDocument.ElementRef element) {
        List<NativeDefinition.ExpandedName> path = new ArrayList<>();
        for (int index : element.ancestry()) { var name = document.elements().get(index).name(); path.add(new NativeDefinition.ExpandedName(name.namespaceUri(), name.localName())); }
        path.add(new NativeDefinition.ExpandedName(element.name().namespaceUri(), element.name().localName())); return List.copyOf(path);
    }
    static long utf8(String text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) fail("INVALID_UNICODE"); bytes += 4; }
            else if (Character.isLowSurrogate(c)) fail("INVALID_UNICODE"); else bytes += c < 128 ? 1 : c < 2048 ? 2 : 3;
        }
        return bytes;
    }
    static void escape(String text, Bounded out) {
        for (int i = 0; i < text.length(); i++) switch (text.charAt(i)) {
            case '&' -> out.add("&amp;"); case '<' -> out.add("&lt;"); case '"' -> out.add("&quot;");
            case '\t' -> out.add("&#9;"); case '\n' -> out.add("&#10;"); case '\r' -> out.add("&#13;");
            default -> out.add(text.substring(i, i + 1));
        }
    }
    static final class Bounded {
        private final StringBuilder builder = new StringBuilder();
        void add(String text) { if ((long)builder.length() + text.length() > MAX_CHARS) fail("RESOURCE_LIMIT"); builder.append(text); }
        String value() { return builder.toString(); }
    }
    static void fail(String code) { throw new Refusal(code); }
    static final class Refusal extends RuntimeException { final String code; Refusal(String code) { super(null, null, false, false); this.code = code; } }
}
