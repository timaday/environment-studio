package studio.environment.server.planning;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import studio.environment.server.xml.XmlDocument;
import static studio.environment.server.planning.PlanningXml.fail;

/** Exact context comparison, including unused bindings and the complete inherited base chain. */
final class MoveNamespaceQualification {
    record Context(Map<String, String> namespaces, Optional<String> language, Optional<String> space,
                   Optional<String> documentBase, List<String> baseChain) {
        Context { namespaces = Map.copyOf(namespaces); baseChain = List.copyOf(baseChain); }
        @Override public String toString() { return "MoveContext[redacted]"; }
    }
    private MoveNamespaceQualification() { }
    static Context context(XmlDocument document, XmlDocument.ElementRef parent, Optional<String> documentBase) {
        Context context = new Context(Map.of("", "", "xml", PlanningXml.XML), Optional.empty(), Optional.empty(), documentBase, List.of());
        for (int index : parent.ancestry()) context = extend(context, document.elements().get(index));
        return extend(context, parent);
    }
    static Context extend(Context context, XmlDocument.ElementRef element) {
        Map<String, String> namespaces = new TreeMap<>(context.namespaces()); Optional<String> language = context.language(), space = context.space();
        List<String> bases = new ArrayList<>(context.baseChain());
        for (var attribute : element.attributes()) {
            if (attribute.name().namespaceUri().equals(PlanningXml.XMLNS)) namespaces.put(prefix(attribute), attribute.value());
            if (attribute.name().namespaceUri().equals(PlanningXml.XML)) switch (attribute.name().localName()) {
                case "lang" -> language = Optional.of(attribute.value()); case "space" -> space = Optional.of(attribute.value()); case "base" -> bases.add(attribute.value()); default -> { }
            }
        }
        return new Context(namespaces, language, space, context.documentBase(), bases);
    }
    static void verifyContext(XmlDocument source, XmlDocument.ElementRef root, Context sourceParent, Context targetParent) {
        if (!sourceParent.namespaces().equals(targetParent.namespaces())) fail("MOVE_NAMESPACE_CONTEXT_MISMATCH");
        if (!sourceParent.language().equals(targetParent.language()) || !sourceParent.space().equals(targetParent.space())) fail("MOVE_INHERITED_XML_CONTEXT_MISMATCH");
        if (!sourceParent.documentBase().equals(targetParent.documentBase()) || !sourceParent.baseChain().equals(targetParent.baseChain())) fail("MOVE_BASE_CONTEXT_MISMATCH");
        boolean known = sourceParent.documentBase().isPresent();
        if (!sourceParent.baseChain().isEmpty()) {
            if (!known && !absolute(sourceParent.baseChain().getFirst())) fail("MOVE_BASE_CONTEXT_UNKNOWN");
            known = true;
            for (String base : sourceParent.baseChain()) absolute(base);
        }
        Map<Integer, Boolean> resolution = new HashMap<>();
        for (var element : source.elements()) {
            if (element.index() != root.index() && !element.ancestry().contains(root.index())) continue;
            boolean localKnown = element.index() == root.index() ? known : resolution.get(element.ancestry().getLast());
            for (var attribute : element.attributes()) if (attribute.name().namespaceUri().equals(PlanningXml.XML) && attribute.name().localName().equals("base")) {
                if (absolute(attribute.value())) localKnown = true; else if (!localKnown) fail("MOVE_BASE_CONTEXT_UNKNOWN");
            }
            resolution.put(element.index(), localKnown);
        }
    }
    static String qualify(XmlDocument source, XmlDocument.ElementRef root, Context sourceParent, Context targetParent) {
        verifyContext(source, root, sourceParent, targetParent);
        Map<String, String> local = new HashMap<>();
        for (var attribute : root.attributes()) if (attribute.name().namespaceUri().equals(PlanningXml.XMLNS)) local.put(prefix(attribute), attribute.value());
        int point = root.startTag().end() - (root.selfClosing() ? 2 : 1);
        PlanningXml.Bounded result = new PlanningXml.Bounded(); result.add(source.source().substring(root.span().start(), point));
        for (var entry : new TreeMap<>(sourceParent.namespaces()).entrySet()) {
            if (entry.getKey().equals("xml") || local.containsKey(entry.getKey())) continue;
            result.add(entry.getKey().isEmpty() ? " xmlns=\"" : " xmlns:" + entry.getKey() + "=\""); PlanningXml.escape(entry.getValue(), result); result.add("\"");
        }
        result.add(source.source().substring(point, root.span().end())); return result.value();
    }
    private static String prefix(XmlDocument.AttributeRef attribute) { return attribute.qualifiedName().equals("xmlns") ? "" : attribute.qualifiedName().substring("xmlns:".length()); }
    private static boolean absolute(String value) {
        try { return new URI(value).isAbsolute(); } catch (URISyntaxException invalid) { fail("MOVE_BASE_CONTEXT_UNSUPPORTED"); return false; }
    }
}
