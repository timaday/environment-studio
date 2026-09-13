package studio.environment.core.definitionv2;

import java.util.*;
import studio.environment.core.definition.DefinitionDiagnostic;
import static studio.environment.core.definition.DefinitionDiagnostic.Phase.*;
import static studio.environment.core.definitionv2.NativeDefinition.*;

/** Declaration-only conflicts and budgets; actual XML cardinality belongs to observation. */
final class NativeChildMappings {
    private NativeChildMappings() { }
    private record Selector(ExpandedName element, ExpandedName discriminator, String value) { }
    private record Attribute(List<ExpandedName> path, ExpandedName name) { }
    private record ValueSite(Attribute attribute, Optional<Selector> selector, String diagnosticPath) { }

    static Set<String> projection(Projection projection, Map<String, Field> fields, boolean create,
            String path, List<DefinitionDiagnostic> diagnostics) {
        var requiredGroups = new HashMap<Selector, Set<ExpandedName>>();
        var namespaces = new HashSet<String>();
        for (int index = 0; index < projection.fields().size(); index++) {
            var mapping = projection.fields().get(index);
            if (!(mapping.locator() instanceof ChildProperty child)) continue;
            String at = path + "/fields/" + index + "/childProperty";
            name(child.element(), false, at + "/element", diagnostics);
            name(child.discriminatorAttribute(), true, at + "/discriminatorAttribute", diagnostics);
            name(child.valueAttribute(), true, at + "/valueAttribute", diagnostics);
            if (!xmlText(child.discriminatorValue())) reject("INVALID_XML_VALUE", at + "/discriminatorValue", diagnostics);
            if (projection.path().size() >= 128) incomplete("XML_DEPTH_UNSUPPORTED", at + "/element", diagnostics);
            if (!NativeXmlCapabilities.supportsElementNamespace(child.element().namespaceUri()))
                incomplete("XML_NAMESPACE_UNSUPPORTED", at + "/element/namespaceUri", diagnostics);
            var field = fields.get(mapping.field());
            if (field != null && field.required()) {
                var selector = new Selector(child.element(), child.discriminatorAttribute(), child.discriminatorValue());
                var attributes = requiredGroups.computeIfAbsent(selector, ignored -> new HashSet<>());
                attributes.add(child.discriminatorAttribute()); attributes.add(child.valueAttribute());
                if (create) {
                    namespaces.add(child.element().namespaceUri());
                    namespaces.add(child.discriminatorAttribute().namespaceUri());
                    namespaces.add(child.valueAttribute().namespaceUri());
                }
            }
        }
        for (var group : requiredGroups.entrySet()) {
            int reset = create && group.getKey().element().namespaceUri().isEmpty() ? 1 : 0;
            if (group.getValue().size() + reset > 256) incomplete("XML_ATTRIBUTES_UNSUPPORTED", path + "/fields", diagnostics);
        }
        namespaces.remove(""); namespaces.remove("http://www.w3.org/XML/1998/namespace");
        return Set.copyOf(namespaces);
    }
    static void document(Document document, boolean create, String path, List<DefinitionDiagnostic> diagnostics) {
        if (document.entities().stream().flatMap(p -> p.fields().stream()).noneMatch(f -> f.locator() instanceof ChildProperty)) return;
        var projectedPaths = new HashSet<List<ExpandedName>>();
        document.entities().forEach(projection -> projectedPaths.add(projection.path()));
        var values = new HashMap<Attribute, List<ValueSite>>();
        var discriminators = new HashSet<Attribute>();
        for (int p = 0; p < document.entities().size(); p++) {
            var projection = document.entities().get(p); String at = path + "/entities/" + p;
            for (int f = 0; f < projection.fields().size(); f++) {
                var mapping = projection.fields().get(f); String fieldPath = at + "/fields/" + f;
                ValueSite site = switch (mapping.locator()) {
                    case DirectAttribute direct -> new ValueSite(new Attribute(projection.path(), direct.attribute()), Optional.empty(), fieldPath);
                    case ChildProperty child -> {
                        var childPath = new ArrayList<>(projection.path()); childPath.add(child.element());
                        var exactPath = List.copyOf(childPath);
                        if (create && projectedPaths.contains(exactPath))
                            incomplete("CHILD_ENTITY_CREATION_UNSUPPORTED", fieldPath + "/childProperty/element", diagnostics);
                        discriminators.add(new Attribute(exactPath, child.discriminatorAttribute()));
                        yield new ValueSite(new Attribute(exactPath, child.valueAttribute()),
                                Optional.of(new Selector(child.element(), child.discriminatorAttribute(), child.discriminatorValue())), fieldPath);
                    }
                };
                values.computeIfAbsent(site.attribute(), ignored -> new ArrayList<>()).add(site);
            }
            for (int r = 0; r < projection.references().size(); r++) {
                var reference = projection.references().get(r);
                var attribute = new Attribute(projection.path(), reference.attribute());
                values.computeIfAbsent(attribute, ignored -> new ArrayList<>()).add(new ValueSite(attribute, Optional.empty(), at + "/references/" + r));
            }
        }
        for (var entry : values.entrySet()) {
            var sites = entry.getValue();
            if (discriminators.contains(entry.getKey()))
                sites.forEach(site -> reject("DISCRIMINATOR_MAPPING_CONFLICT", site.diagnosticPath(), diagnostics));
            boolean priorSite = false, priorDirect = false;
            var selectors = new HashSet<Selector>();
            for (var site : sites) {
                if (priorSite && (priorDirect || site.selector().isEmpty() || selectors.contains(site.selector().orElseThrow())))
                    reject("ATTRIBUTE_COLLISION", site.diagnosticPath(), diagnostics);
                priorSite = true;
                if (site.selector().isPresent()) selectors.add(site.selector().orElseThrow());
                else priorDirect = true;
            }
        }
    }
    private static boolean xmlText(String value) {
        if (value.length() > 1_048_576) return false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset); offset += Character.charCount(cp);
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xd7ff || cp >= 0xe000 && cp <= 0xfffd
                    || cp >= 0x10000 && cp <= 0x10ffff)) return false;
        }
        return true;
    }
    private static void name(ExpandedName name, boolean attribute, String path, List<DefinitionDiagnostic> diagnostics) {
        if (!NativeLexicalRules.validName(name, attribute)) reject("INVALID_XML_NAME", path, diagnostics);
    }
    private static void reject(String code, String path, List<DefinitionDiagnostic> diagnostics) {
        diagnostics.add(new DefinitionDiagnostic(SEMANTIC, code, path, "Use valid, disjoint field locations with immutable discriminators."));
    }
    private static void incomplete(String code, String path, List<DefinitionDiagnostic> diagnostics) {
        diagnostics.add(new DefinitionDiagnostic(PUBLICATION, code, path, "Select declarations supported by the registered XML mechanism."));
    }
}
