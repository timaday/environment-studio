package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.derived.*;
import studio.environment.core.definitionv2.NativeDefinition.ExpandedName;
import studio.environment.core.graph.ObservedGraph;
import static studio.environment.server.plan.PlanViewProjection.object;
import static studio.environment.server.plan.PlanViewProjection.reference;

/** Explicit transient wire fields; internal proof types are never serialized directly. */
final class V3PlanComputedEncoding {
    private V3PlanComputedEncoding() { }
    static Map<String,Object> page(V3PlanComputedViews.Page<?> page, Runnable verify) {
        var items = new ArrayList<Map<String,Object>>();
        for (var value : page.items()) {
            verify.run();
            items.add(switch (value) {
                case V3PlanComputedViews.Node node -> object("key", key(node.key()), "contributorTotal", node.contributorTotal());
                case V3PlanComputedViews.Membership edge -> object("relation", edge.relation(), "physical", reference(edge.physical()),
                        "computed", key(edge.computed()), "contributorTotal", edge.contributorTotal());
                case V3PlanComputedViews.Cooccurrence edge -> object("relation", edge.relation(), "source", key(edge.source()),
                        "target", key(edge.target()), "contributorTotal", edge.contributorTotal());
                case DerivedResult.RuleCheck rule -> object("kind", rule.kind().name(), "declaration", rule.declaration(),
                        "source", rule.source().map(V3PlanComputedEncoding::key).orElse(null), "actual", rule.actual().toString(),
                        "minimum", rule.minimum().toString(), "maximum", rule.maximum().toString(), "outcome", rule.outcome().name());
                case V3PlanComputedViews.Contributor contributor -> contributor(contributor, verify);
                default -> throw new IllegalStateException("COMPUTED_VIEW_UNAVAILABLE");
            });
        }
        verify.run();
        return object("revision", page.revision(), "total", page.total(), "offset", page.offset(),
                "nextOffset", page.nextOffset().isPresent() ? page.nextOffset().getAsInt() : null, "items", List.copyOf(items));
    }
    private static Map<String,Object> key(ComputedGraph.Key key) { return object("computedType", key.computedType(), "derivation", key.derivation(), "value", key.value()); }
    private static Map<String,Object> name(ExpandedName name) { return object("namespaceUri", name.namespaceUri(), "localName", name.localName()); }
    private static Map<String,Object> origin(ObservedGraph.Origin origin, Runnable verify) {
        var ancestry = new ArrayList<String>(); for (int index : origin.ancestry()) { verify.run(); ancestry.add(Integer.toString(index)); }
        return object("documentId", origin.documentId(), "projectionId", origin.projectionId(), "sourceDigest", origin.sourceDigest(),
                "elementIndex", Integer.toString(origin.elementIndex()), "ancestry", List.copyOf(ancestry));
    }
    private static Map<String,Object> attribute(DerivedInput.AttributePin pin) {
        return object("documentId", pin.documentId(), "sourceDigest", pin.sourceDigest(), "elementIndex", Integer.toString(pin.elementIndex()),
                "name", name(pin.name()), "qualifiedName", pin.qualifiedName(), "decodedValue", pin.decodedValue(),
                "valueStart", pin.valueStart(), "valueEnd", pin.valueEnd(), "quote", Character.toString(pin.quote()));
    }
    private static Map<String,Object> location(DerivedInput.Location location) {
        return object("value", attribute(location.value()), "selector", location.selector().map(selector -> object(
                "parentElementIndex", Integer.toString(selector.parentElementIndex()), "element", name(selector.element()),
                "discriminator", attribute(selector.discriminator()))).orElse(null));
    }
    private static Map<String,Object> contributor(V3PlanComputedViews.Contributor contributor, Runnable verify) {
        var roles = new ArrayList<Map<String,Object>>();
        for (var role : contributor.roles()) { verify.run(); roles.add(object("field", role.field(), "location", location(role.location()))); }
        return object("physical", reference(contributor.physical()), "origin", origin(contributor.origin(), verify), "roles", List.copyOf(roles));
    }
}
