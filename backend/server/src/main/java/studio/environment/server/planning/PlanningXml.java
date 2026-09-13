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
    private record Group(NativeDefinition.ExpandedName element,NativeDefinition.ExpandedName discriminator,String value) { }
    static String create(NativeDefinition.Projection projection, ExpectedTarget.Entity entity, Map<String, String> references) {
        Map<NativeDefinition.ExpandedName, String> attributes = new TreeMap<>(NAMES);
        var groups=new TreeMap<Group,Map<NativeDefinition.ExpandedName,String>>(Comparator.comparing(Group::element,NAMES).thenComparing(Group::discriminator,NAMES).thenComparing(Group::value,UTF8));
        for(var mapping:projection.fields()) {
            if(!entity.fields().containsKey(mapping.field()))continue;
            String value=entity.fields().get(mapping.field());
            switch(mapping.locator()) {
                case NativeDefinition.DirectAttribute direct -> put(attributes,direct.attribute(),value);
                case NativeDefinition.ChildProperty child -> {
                    var group=new Group(child.element(),child.discriminatorAttribute(),child.discriminatorValue());
                    var values=groups.computeIfAbsent(group,k->{var map=new TreeMap<NativeDefinition.ExpandedName,String>(NAMES);map.put(k.discriminator(),k.value());return map;});
                    put(values,child.valueAttribute(),value);
                }
            }
        }
        projection.references().forEach(mapping -> { if (references.containsKey(mapping.relation())) put(attributes,mapping.attribute(),references.get(mapping.relation())); });
        var name = projection.path().getLast();
        TreeSet<String> namespaces = new TreeSet<>(UTF8); namespaces.add(name.namespaceUri()); attributes.keySet().forEach(n -> namespaces.add(n.namespaceUri()));
        groups.forEach((group,values)->{namespaces.add(group.element().namespaceUri());values.keySet().forEach(n->namespaces.add(n.namespaceUri()));});
        namespaces.remove(""); namespaces.remove(XML);
        Map<String, String> prefixes = new TreeMap<>(UTF8); int index = 0;
        for (String namespace : namespaces) prefixes.put(namespace, "ns" + index++);
        prefixes.put(XML, "xml");
        Bounded out = new Bounded(); out.add("<"); out.add(qualified(name, prefixes));
        if (name.namespaceUri().isEmpty()) out.add(" xmlns=\"\"");
        for (String namespace : namespaces) { out.add(" xmlns:"); out.add(prefixes.get(namespace)); out.add("=\""); escape(namespace, out); out.add("\""); }
        attributes(attributes,prefixes,out);
        if(groups.isEmpty()) {out.add("/>");return out.value();}
        out.add(">");
        groups.forEach((group,values)->{
            out.add("<");out.add(qualified(group.element(),prefixes));
            if(group.element().namespaceUri().isEmpty())out.add(" xmlns=\"\"");
            attributes(values,prefixes,out);out.add("/>");
        });
        out.add("</");out.add(qualified(name,prefixes));out.add(">");return out.value();
    }
    private static void put(Map<NativeDefinition.ExpandedName,String> attributes,NativeDefinition.ExpandedName name,String value) {
        if(attributes.putIfAbsent(name,value)!=null)fail("ATTRIBUTE_ALIAS");
    }
    private static void attributes(Map<NativeDefinition.ExpandedName,String> attributes,Map<String,String> prefixes,Bounded out) {
        for(var attribute:attributes.entrySet()) {out.add(" ");out.add(qualified(attribute.getKey(),prefixes));out.add("=\"");escape(attribute.getValue(),out);out.add("\"");}
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
