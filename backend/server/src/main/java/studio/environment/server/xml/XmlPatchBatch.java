package studio.environment.server.xml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import static studio.environment.server.xml.XmlDocument.*;
import static studio.environment.server.xml.XmlEdit.*;

/** One source-bound batch, one bounded assembly, then independent full-document validation. */
final class XmlPatchBatch {
    private final HardenedXmlProjection projection;
    XmlPatchBatch(HardenedXmlProjection projection) { this.projection = projection; }
    private record Insertion(XmlDocument fragment) { }
    private static final class Patch {
        final int start;
        final int end;
        final String text;
        final List<Insertion> insertions;
        final int insertionPrefix;
        int targetStart;
        Patch(int start, int end, String text, List<Insertion> insertions, int insertionPrefix) {
            this.start = start; this.end = end; this.text = text;
            this.insertions = List.copyOf(insertions); this.insertionPrefix = insertionPrefix;
        }
    }
    private static final class InsertGroup {
        final ElementRef parent;
        final int position;
        final List<Insertion> insertions = new ArrayList<>();
        InsertGroup(ElementRef parent, int position) { this.parent = parent; this.position = position; }
    }
    XmlDocument apply(XmlDocument source, List<XmlEdit> edits) {
        if (edits.size() > XmlLexicalScanner.MAX_TOKENS) throw new XmlRefusal("RESOURCE_LIMIT");
        List<Patch> patches = new ArrayList<>();
        Map<Integer, InsertGroup> groups = new LinkedHashMap<>();
        Map<AttributeRef, String> replacements = new HashMap<>();
        List<ElementRef> removals = new ArrayList<>();
        long replacementChars = 0;
        for (XmlEdit edit : edits) {
            if (edit instanceof ReplaceAttribute replace) {
                AttributeRef attribute = attribute(source, replace.attribute());
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.name().namespaceUri()))
                    throw new XmlRefusal("NAMESPACE_ATTRIBUTE");
                if (!attribute.value().equals(replace.expectedValue())) throw new XmlRefusal("EXPECTED_VALUE_MISMATCH");
                if (replacements.putIfAbsent(attribute, replace.replacement()) != null) throw new XmlRefusal("CONFLICTING_EDITS");
                String escaped = escape(replace.replacement(), attribute.quote());
                patches.add(new Patch(attribute.valueSpan().start(), attribute.valueSpan().end(), escaped, List.of(), 0));
                replacementChars += escaped.length();
            } else if (edit instanceof RemoveElement remove) {
                ElementRef element = element(source, remove.element());
                if (element.ancestry().isEmpty()) throw new XmlRefusal("ROOT_REMOVAL");
                removals.add(element);
                patches.add(new Patch(element.span().start(), element.span().end(), "", List.of(), 0));
            } else if (edit instanceof InsertElement insert) {
                ElementRef parent = element(source, insert.parent());
                int position = parent.endTagStart();
                if (insert.before().isPresent()) {
                    ElementRef before = element(source, insert.before().get());
                    if (before.ancestry().isEmpty() || before.ancestry().getLast() != parent.index())
                        throw new XmlRefusal("INVALID_ANCHOR");
                    position = before.span().start();
                }
                XmlDocument fragment = projection.project(insert.markup());
                ElementRef root = fragment.elements().getFirst();
                if (root.span().start() != 0 || root.span().end() != insert.markup().length()) throw new XmlRefusal("INVALID_INSERTION");
                InsertGroup group = groups.get(position);
                if (group == null) { group = new InsertGroup(parent, position); groups.put(position, group); }
                if (!group.parent.equals(parent)) throw new XmlRefusal("CONFLICTING_EDITS");
                group.insertions.add(new Insertion(fragment));
                replacementChars += insert.markup().length();
            }
            // A valid target cannot insert more than its bound plus all removable source characters.
            if (replacementChars > 2L * XmlLexicalScanner.MAX_CHARS) throw new XmlRefusal("RESOURCE_LIMIT");
        }
        conflicts(source, edits, removals);
        for (InsertGroup group : groups.values()) {
            StringBuilder text = new StringBuilder();
            if (group.parent.selfClosing()) text.append('>');
            for (Insertion insertion : group.insertions) text.append(insertion.fragment.source());
            if (group.parent.selfClosing()) text.append("</").append(group.parent.qualifiedName()).append('>');
            patches.add(new Patch(group.position, group.parent.selfClosing() ? group.parent.span().end() : group.position,
                    text.toString(), group.insertions, group.parent.selfClosing() ? 1 : 0));
        }
        patches.sort(Comparator.comparingInt(patch -> patch.start));
        Patch previous = null;
        long targetLength = source.source().length();
        for (Patch patch : patches) {
            if (previous != null && (patch.start < previous.end || patch.start == previous.start))
                throw new XmlRefusal("CONFLICTING_EDITS");
            targetLength += patch.text.length() - (patch.end - patch.start);
            previous = patch;
        }
        if (targetLength > XmlLexicalScanner.MAX_CHARS) throw new XmlRefusal("RESOURCE_LIMIT");
        StringBuilder target = new StringBuilder((int) targetLength);
        int cursor = 0;
        for (Patch patch : patches) {
            target.append(source.source(), cursor, patch.start);
            patch.targetStart = target.length(); target.append(patch.text); cursor = patch.end;
        }
        target.append(source.source(), cursor, source.source().length());
        XmlDocument result = projection.project(target.toString());
        validateOutcomes(source, result, patches, replacements, removals);
        return result;
    }
    private static void conflicts(XmlDocument source, List<XmlEdit> edits, List<ElementRef> removals) {
        Set<Integer> removed = new HashSet<>();
        for (ElementRef element : removals)
            if (!removed.add(element.index())) throw new XmlRefusal("CONFLICTING_EDITS");
        for (XmlEdit edit : edits) {
            if (edit instanceof RemoveElement remove && remove.element().ancestry().stream().anyMatch(removed::contains))
                throw new XmlRefusal("CONFLICTING_EDITS");
            if (edit instanceof ReplaceAttribute replace && removed(source.elements().get(replace.attribute().elementIndex()), removed))
                throw new XmlRefusal("CONFLICTING_EDITS");
            if (edit instanceof InsertElement insert && (removed(insert.parent(), removed)
                    || insert.before().filter(before -> removed(before, removed)).isPresent()))
                throw new XmlRefusal("CONFLICTING_EDITS");
        }
    }
    private static boolean removed(ElementRef element, Set<Integer> removed) {
        return removed.contains(element.index()) || element.ancestry().stream().anyMatch(removed::contains);
    }
    private static ElementRef element(XmlDocument source, ElementRef reference) {
        if (reference.index() < 0 || reference.index() >= source.elements().size()
                || !source.elements().get(reference.index()).equals(reference)) throw new XmlRefusal("STALE_REFERENCE");
        return reference;
    }
    private static AttributeRef attribute(XmlDocument source, AttributeRef reference) {
        if (reference.elementIndex() < 0 || reference.elementIndex() >= source.elements().size()
                || !source.elements().get(reference.elementIndex()).attributes().contains(reference)) throw new XmlRefusal("STALE_REFERENCE");
        return reference;
    }
    private static String escape(String value, char quote) {
        XmlLexicalScanner.validateCharacters(value);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> output.append("&amp;");
                case '<' -> output.append("&lt;");
                case '\r' -> output.append("&#13;");
                case '\n' -> output.append("&#10;");
                case '\t' -> output.append("&#9;");
                case '\'' -> output.append(quote == '\'' ? "&apos;" : "'");
                case '"' -> output.append(quote == '"' ? "&quot;" : "\"");
                default -> output.append(c);
            }
            if (output.length() > XmlLexicalScanner.MAX_CHARS) throw new XmlRefusal("RESOURCE_LIMIT");
        }
        return output.toString();
    }
    private static void validateOutcomes(XmlDocument source, XmlDocument target, List<Patch> patches,
            Map<AttributeRef, String> replacements, List<ElementRef> removals) {
        Map<Integer, ElementRef> targetByStart = new HashMap<>();
        target.elements().forEach(element -> targetByStart.put(element.span().start(), element));
        int expectedElements = 0;
        Set<Integer> removed = new HashSet<>();
        removals.forEach(element -> removed.add(element.index()));
        int patchIndex = 0;
        int delta = 0;
        for (ElementRef original : source.elements()) {
            if (removed(original, removed)) continue;
            while (patchIndex < patches.size() && patches.get(patchIndex).end <= original.span().start()) {
                Patch patch = patches.get(patchIndex++);
                delta += patch.text.length() - (patch.end - patch.start);
            }
            expectedElements++;
            ElementRef actual = targetByStart.get(original.span().start() + delta);
            if (actual == null || !original.name().equals(actual.name()) || original.attributes().size() != actual.attributes().size())
                throw new XmlRefusal("OUTCOME_MISMATCH");
            for (int i = 0; i < original.attributes().size(); i++) {
                AttributeRef attribute = original.attributes().get(i);
                AttributeRef changed = actual.attributes().get(i);
                if (!attribute.name().equals(changed.name()) || !replacements.getOrDefault(attribute, attribute.value()).equals(changed.value()))
                    throw new XmlRefusal("OUTCOME_MISMATCH");
            }
        }
        for (Patch patch : patches) {
            int offset = patch.targetStart + patch.insertionPrefix;
            for (Insertion insertion : patch.insertions) {
                XmlDocument fragment = insertion.fragment;
                expectedElements += fragment.elements().size();
                for (ElementRef expected : fragment.elements()) {
                    ElementRef actual = targetByStart.get(offset + expected.span().start());
                    if (actual == null || !sameSemantics(expected, actual)) throw new XmlRefusal("INSERTION_NAMESPACE_MISMATCH");
                }
                offset += fragment.source().length();
            }
        }
        if (target.elements().size() != expectedElements) throw new XmlRefusal("OUTCOME_MISMATCH");
    }
    private static boolean sameSemantics(ElementRef expected, ElementRef actual) {
        if (!expected.name().equals(actual.name()) || expected.attributes().size() != actual.attributes().size()) return false;
        for (int i = 0; i < expected.attributes().size(); i++)
            if (!expected.attributes().get(i).name().equals(actual.attributes().get(i).name())
                    || !expected.attributes().get(i).value().equals(actual.attributes().get(i).value())) return false;
        return true;
    }
}
