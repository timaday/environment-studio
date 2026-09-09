package studio.environment.server.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.graph.GraphValidationResult;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.planning.ExpectedTarget;
import studio.environment.core.planning.TargetCompilationResult;
import studio.environment.core.planning.TargetIntent;
import studio.environment.core.planning.TargetIntentCompiler;
import studio.environment.server.projection.DocumentSource;
import studio.environment.server.projection.FieldLocatorResolver;
import studio.environment.server.projection.GraphProjectionAdapter;
import studio.environment.server.projection.ProjectionResult;
import studio.environment.server.xml.LosslessXmlAdapter;
import studio.environment.server.xml.XmlDocument;
import studio.environment.server.xml.XmlEdit;
import studio.environment.server.xml.XmlResult;
import static studio.environment.server.planning.PlanningXml.fail;

/** Reprojects all sources, resolves semantics independently, and verifies the complete materialized target. */
public final class StructuralTargetAdapter {
    public MaterializationResult materialize(ReadyToPublish definition, String bindingId, List<TargetSource> sources, TargetIntent intent, List<TargetPlacement> placements) {
        if (definition == null || bindingId == null || sources == null || intent == null || placements == null) return MaterializationResult.reject("INVALID_INPUT");
        try { return new Work(definition, bindingId, sources, intent, placements).run(); }
        catch (PlanningXml.Refusal refused) { return MaterializationResult.reject(refused.code); }
    }
    private static final class Work {
        final ReadyToPublish definition; final String bindingId; final TargetIntent intent;
        final Map<String, TargetSource> sources = new TreeMap<>();
        final Map<XmlAssembly.Symbol.Original, XmlDocument.ElementRef> sourceElements = new HashMap<>();
        final Map<XmlAssembly.Symbol.Original, List<NativeDefinition.ExpandedName>> sourcePaths = new HashMap<>();
        final Set<TargetPlacement.Parent.Existing> admittedParents = new HashSet<>();
        final Map<TargetIntent.Ref.Existing, MoveNamespaceQualification.Context> moveSourceContexts = new HashMap<>();
        final Map<TargetIntent.Ref, TargetPlacement> placements = new LinkedHashMap<>();
        final Map<String, NativeDefinition.Projection> projections = new HashMap<>();
        final Map<String, String> projectionDocuments = new HashMap<>();
        final Map<TargetIntent.Ref.Existing, ObservedGraph.Entity> original = new HashMap<>();
        final Map<TargetIntent.Ref, ExpectedTarget.Entity> target = new HashMap<>();
        final LosslessXmlAdapter xml = new LosslessXmlAdapter(); final GraphProjectionAdapter graph = new GraphProjectionAdapter();
        final XmlAssembly assembly = new XmlAssembly();
        final Set<TargetIntent.Ref.Existing> moves = new HashSet<>();
        final Map<TargetIntent.Ref.Existing, Destination> destinations = new HashMap<>();
        final Map<XmlAssembly.Symbol, Position> positions = new HashMap<>();
        final Set<XmlAssembly.Symbol> tracked = new HashSet<>();
        private record Destination(String document, String projection) { }
        private record Position(String document, int index) { }
        ExpectedTarget expected;
        Work(ReadyToPublish definition, String bindingId, List<TargetSource> inputs, TargetIntent intent, List<TargetPlacement> requested) {
            this.definition = definition; this.bindingId = bindingId; this.intent = intent;
            if (inputs.size() > 128 || requested.size() > 20_000) fail("RESOURCE_LIMIT");
            long bytes = 0;
            for (var source : inputs) {
                if (source == null || source.source() == null || source.documentId() == null || source.documentBase() == null) fail("INVALID_INPUT");
                if (source.source().length() > PlanningXml.MAX_CHARS) fail("RESOURCE_LIMIT");
                bytes += PlanningXml.utf8(source.source()); if (bytes > PlanningXml.MAX_BYTES) fail("RESOURCE_LIMIT");
                if (source.documentBase().isPresent() && (source.documentBase().get().length() > PlanningXml.MAX_CHARS || !studio.environment.core.definitionv2.NativeLexicalRules.validValue(studio.environment.core.definition.DefinitionDraft.ValueType.URI, source.documentBase().get()))) fail("INVALID_DOCUMENT_BASE");
                if (sources.putIfAbsent(source.documentId(), source) != null) fail("INVENTORY_MISMATCH");
            }
            for (var placement : requested) if (placement == null || placement.entity() == null || placements.putIfAbsent(placement.entity(), placement) != null) fail("CONFLICTING_PLACEMENTS");
        }
        MaterializationResult run() {
            var observed = project(sources.values().stream().toList());
            var resolved = new TargetIntentCompiler().compile(definition, new GraphValidationResult.Accepted(observed.graph()), intent);
            if (resolved instanceof TargetCompilationResult.Rejected refused) fail(refused.codes().getFirst());
            expected = ((TargetCompilationResult.Expected)resolved).target(); expected.entities().forEach(e -> target.put(e.reference(), e));
            observed.graph().entities().forEach(e -> original.put(new TargetIntent.Ref.Existing(e.key()), e));
            var binding = definition.checked().definition().bindings().stream().filter(b -> b.id().equals(bindingId)).findFirst().orElseThrow();
            for (var document : binding.documents()) for (var projection : document.entities()) { projections.put(projection.id(), projection); projectionDocuments.put(projection.id(), document.id()); }
            collectSourceMetadata();
            validatePlacements();
            List<TargetSource> result = new ArrayList<>(); Set<String> affectedDocuments = new HashSet<>(); long bytes = 0;
            for (var entry : sources.entrySet()) {
                var source = assembly.original(entry.getKey(), parsed(xml.project(entry.getValue().source())));
                var edits = edits(source, entry.getKey(), null, new HashSet<>());
                long inserted = 0;
                for (var edit : edits) if (edit.insertion().isPresent()) { inserted += PlanningXml.utf8(edit.insertion().get().document().source()); if (inserted > PlanningXml.MAX_BYTES) fail("RESOURCE_LIMIT"); }
                var changed = assembly.apply(source, edits); recordPositions(entry.getKey(), changed);
                verifyFinalMoveContexts(entry.getKey(), changed.document());
                bytes += PlanningXml.utf8(changed.document().source()); if (bytes > PlanningXml.MAX_BYTES) fail("RESOURCE_LIMIT");
                if (!changed.document().source().equals(entry.getValue().source())) affectedDocuments.add(entry.getKey());
                result.add(new TargetSource(entry.getKey(), changed.document().source(), sources.get(entry.getKey()).documentBase()));
            }
            var projected = project(result); compare(projected.graph());
            Set<TargetIntent.Ref> affected = new HashSet<>(expected.affected());
            for (var entity : expected.entities()) if (entity.reference() instanceof TargetIntent.Ref.Existing old && containingMove(original.get(old).origin().documentId(), element(old)) != null) affected.add(old);
            return new MaterializationResult.Complete(result, projected.graph(), affected, affectedDocuments);
        }
        /** Extract only modeled origins and explicitly selected parents; each full tree dies in this iteration. */
        private void collectSourceMetadata() {
            Map<String, Set<Integer>> selected = new HashMap<>();
            for (var entity : original.values()) selected.computeIfAbsent(entity.origin().documentId(), ignored -> new HashSet<>()).add(entity.origin().elementIndex());
            for (var placement : placements.values()) if (placement.parent() instanceof TargetPlacement.Parent.Existing parent) {
                if (!sources.containsKey(parent.documentId())) fail("STALE_PARENT");
                selected.computeIfAbsent(parent.documentId(), ignored -> new HashSet<>()).add(parent.elementIndex());
            }
            for (var source : sources.values()) {
                var document = parsed(xml.project(source.source()));
                for (int index : selected.getOrDefault(source.documentId(), Set.of())) {
                    if (index < 0 || index >= document.elements().size()) fail("STALE_PARENT");
                    var symbol = new XmlAssembly.Symbol.Original(source.documentId(), index); var element = document.elements().get(index);
                    sourceElements.put(symbol, element); sourcePaths.put(symbol, PlanningXml.path(document, element)); tracked.add(symbol);
                }
                for (var placement : placements.values()) if (placement.parent() instanceof TargetPlacement.Parent.Existing parent && parent.documentId().equals(source.documentId())) {
                    if (!document.digest().equals(parent.sourceDigest())) fail("STALE_PARENT");
                    admittedParents.add(parent);
                }
                for (var assignment : intent.containment()) if (assignment.child() instanceof TargetIntent.Ref.Existing old) {
                    var origin = original.get(old).origin();
                    if (origin.documentId().equals(source.documentId())) {
                        var root = document.elements().get(origin.elementIndex()); if (root.ancestry().isEmpty()) fail("ROOT_MOVE_UNSUPPORTED");
                        moveSourceContexts.put(old, MoveNamespaceQualification.context(document, document.elements().get(root.ancestry().getLast()), source.documentBase()));
                    }
                }
            }
            for (var entity : expected.entities()) if (entity.reference() instanceof TargetIntent.Ref.Fresh fresh) tracked.add(new XmlAssembly.Symbol.Created(fresh));
        }
        private void recordPositions(String document, XmlAssembly.Fragment fragment) {
            for (int index = 0; index < fragment.symbols().size(); index++) {
                var symbol = fragment.symbols().get(index);
                if (tracked.contains(symbol) && positions.putIfAbsent(symbol, new Position(document, index)) != null) fail("DUPLICATE_MATERIALIZATION");
            }
        }
        private void validatePlacements() {
            Set<TargetIntent.Ref> required = new HashSet<>();
            for (var decision : intent.entities()) if (decision instanceof TargetIntent.EntityDecision.Create create) required.add(create.entity());
            for (var move : intent.containment()) if (move.child() instanceof TargetIntent.Ref.Existing old) { moves.add(old); required.add(old); }
            if (!required.equals(placements.keySet())) fail("PLACEMENT_INCOMPLETE");
            for (var move : moves) {
                if (element(move).ancestry().isEmpty()) fail("ROOT_MOVE_UNSUPPORTED");
                if (!definition.checked().definition().logical().operationCapabilities().contains(NativeDefinition.Operation.MOVE_RELATION)) fail("OPERATION_NOT_DECLARED");
                for (var other : moves) if (!move.equals(other) && inside(move, original.get(other).origin().documentId(), element(other))) fail("NESTED_MOVE_SOURCES");
            }
            for (var placement : placements.values()) {
                var projection = projections.get(placement.projectionId());
                if (projection == null || !projectionDocuments.get(placement.projectionId()).equals(placement.documentId()) || !projection.type().equals(placement.entity().type()) || projection.path().size() < 2) fail("INVALID_PROJECTION");
            }
            for (var placement : placements.values()) {
                List<NativeDefinition.ExpandedName> parentPath; String parentDocument;
                if (placement.parent() instanceof TargetPlacement.Parent.Existing old) {
                    var parent = parent(old); var moved = containingMove(old.documentId(), parent);
                    parentPath = targetPath(old.documentId(), parent); parentDocument = moved == null ? old.documentId() : placements.get(moved).documentId();
                    if (unconditionallyRemoved(old.documentId(), parent)) fail("CONFLICTING_EDITS");
                } else if (placement.parent() instanceof TargetPlacement.Parent.Created created) {
                    var ancestor = placements.get(created.entity()); if (ancestor == null) { fail("INVALID_PARENT"); return; }
                    parentPath = projections.get(ancestor.projectionId()).path(); parentDocument = ancestor.documentId();
                } else { fail("INVALID_PARENT"); return; }
                if (!placement.documentId().equals(parentDocument) || !projections.get(placement.projectionId()).path().subList(0, projections.get(placement.projectionId()).path().size() - 1).equals(parentPath)) fail("PARENT_PATH_MISMATCH");
                if (placement.entity() instanceof TargetIntent.Ref.Existing) {
                    var anchor = anchor(placement.parent(), new HashSet<>());
                    if (containingMove(anchor.documentId(), parent(anchor)) != null || unconditionallyRemoved(anchor.documentId(), parent(anchor))) fail("MOVE_SOURCE_DESTINATION_OVERLAP");
                }
            }
            for (var entity : expected.entities()) if (entity.reference() instanceof TargetIntent.Ref.Existing old) {
                var origin = original.get(old).origin(); var element = element(old);
                if (unconditionallyRemoved(origin.documentId(), element)) fail("DESCENDANT_DISPOSITION_REQUIRED");
                var move = containingMove(origin.documentId(), element);
                if (move == null) destinations.put(old, new Destination(origin.documentId(), origin.projectionId()));
                else {
                    String document = placements.get(move).documentId(); var path = targetPath(origin.documentId(), element);
                    var matches = projections.values().stream().filter(p -> projectionDocuments.get(p.id()).equals(document) && p.type().equals(old.type()) && p.path().equals(path)).toList();
                    if (matches.size() != 1) fail("MOVED_DESCENDANT_PROJECTION_UNSUPPORTED");
                    destinations.put(old, new Destination(document, matches.getFirst().id()));
                }
            }
        }
        private TargetPlacement.Parent.Existing anchor(TargetPlacement.Parent parent, Set<TargetIntent.Ref> path) {
            if (parent instanceof TargetPlacement.Parent.Existing old) return old;
            if (parent instanceof TargetPlacement.Parent.Created created && path.add(created.entity()) && path.size() <= 128 && placements.containsKey(created.entity())) return anchor(placements.get(created.entity()).parent(), path);
            fail("PLACEMENT_CYCLE"); return null;
        }
        private boolean inside(TargetIntent.Ref.Existing root, String document, XmlDocument.ElementRef element) {
            var origin = original.get(root).origin(); return origin.documentId().equals(document) && (origin.elementIndex() == element.index() || element.ancestry().contains(origin.elementIndex()));
        }
        private TargetIntent.Ref.Existing containingMove(String document, XmlDocument.ElementRef element) {
            for (var move : moves) if (inside(move, document, element)) return move; return null;
        }
        private boolean unconditionallyRemoved(String document, XmlDocument.ElementRef element) {
            var move = containingMove(document, element);
            for (var removed : expected.removed()) if (inside(removed, document, element) && (move == null || !inside(removed, original.get(move).origin().documentId(), element(move)))) return true;
            return false;
        }
        private List<NativeDefinition.ExpandedName> targetPath(String document, XmlDocument.ElementRef element) {
            var oldPath = sourcePaths.get(new XmlAssembly.Symbol.Original(document, element.index())); var move = containingMove(document, element);
            if (move == null) return oldPath;
            var root = element(move); var path = new ArrayList<>(projections.get(placements.get(move).projectionId()).path());
            path.addAll(oldPath.subList(root.ancestry().size() + 1, oldPath.size())); return List.copyOf(path);
        }
        private void verifyFinalMoveContexts(String documentId, XmlDocument document) {
            var ordered = moves.stream().filter(move -> placements.get(move).documentId().equals(documentId)).sorted(
                java.util.Comparator.comparing((TargetIntent.Ref.Existing move) -> original.get(move).origin().documentId())
                    .thenComparingInt(move -> original.get(move).origin().elementIndex())).toList();
            for (var move : ordered) {
                var origin = original.get(move).origin();
                var position = positions.get(new XmlAssembly.Symbol.Original(origin.documentId(), origin.elementIndex()));
                if (position == null || !position.document().equals(documentId)) fail("ELEMENT_PROVENANCE_MISMATCH");
                var root = document.elements().get(position.index());
                if (root.ancestry().isEmpty()) fail("ROOT_MOVE_UNSUPPORTED");
                var parent = document.elements().get(root.ancestry().getLast());
                var finalContext = MoveNamespaceQualification.context(document, parent, sources.get(documentId).documentBase());
                MoveNamespaceQualification.verifyContext(document, root, moveSourceContexts.get(move), finalContext);
            }
        }
        private XmlAssembly.Fragment fragment(TargetIntent.Ref ref, Set<TargetIntent.Ref> path) {
            if (!path.add(ref) || path.size() > 128) { fail("PLACEMENT_CYCLE"); return null; }
            var placement = placements.get(ref); XmlAssembly.Fragment fragment;
            if (ref instanceof TargetIntent.Ref.Fresh fresh) {
                var document = parsed(xml.project(PlanningXml.create(projections.get(placement.projectionId()), target.get(ref), references(ref))));
                var symbols=new ArrayList<XmlAssembly.Symbol>();
                symbols.add(new XmlAssembly.Symbol.Created(fresh));
                for(int i=1;i<document.elements().size();i++)symbols.add(new XmlAssembly.Symbol.CreatedChild(fresh,i));
                fragment = new XmlAssembly.Fragment(document, symbols);
                var nested = new XmlAssembly.Edits();
                for (var child : placements.values()) if (child.parent() instanceof TargetPlacement.Parent.Created parent && parent.entity().equals(ref)) nested.add(XmlAssembly.Edit.insert(fragment.document().elements().getFirst(), fragment(child.entity(), path)));
                fragment = assembly.apply(fragment, nested.values());
            } else {
                var old = (TargetIntent.Ref.Existing)ref; var origin = original.get(old).origin(); var source = parsed(xml.project(sources.get(origin.documentId()).source())); var root = element(old);
                var context = moveSourceContexts.get(old);
                String markup = MoveNamespaceQualification.qualify(source, root, context, context);
                var document = parsed(xml.project(markup)); List<XmlAssembly.Symbol> symbols = new ArrayList<>();
                for (var element : source.elements()) if (inside(old, origin.documentId(), element)) symbols.add(new XmlAssembly.Symbol.Original(origin.documentId(), element.index()));
                fragment = new XmlAssembly.Fragment(document, symbols);
                fragment = assembly.apply(fragment, edits(fragment, origin.documentId(), old, path));
            }
            path.remove(ref); return fragment;
        }
        private List<XmlAssembly.Edit> edits(XmlAssembly.Fragment source, String document, TargetIntent.Ref.Existing movedRoot, Set<TargetIntent.Ref> path) {
            var edits = new XmlAssembly.Edits(); var locator=new FieldLocatorResolver(source.document()); Map<XmlAssembly.Symbol, XmlDocument.ElementRef> elements = new HashMap<>();
            for (var element : source.document().elements()) elements.put(source.symbols().get(element.index()), element);
            for (var entity : expected.entities()) if (entity.reference() instanceof TargetIntent.Ref.Existing old) {
                var origin = original.get(old).origin();
                if (!origin.documentId().equals(document) || !java.util.Objects.equals(containingMove(document, element(old)), movedRoot)) continue;
                var selected = elements.get(new XmlAssembly.Symbol.Original(document, origin.elementIndex())); if (selected == null) fail("ELEMENT_PROVENANCE_MISMATCH");
                List<XmlEdit> replacements = new ArrayList<>(); scalarEdits(entity, selected, locator, replacements); replacements.forEach(e -> edits.add(XmlAssembly.Edit.simple(e)));
            }
            Set<TargetIntent.Ref.Existing> removals = new HashSet<>(expected.removed()); if (movedRoot == null) removals.addAll(moves);
            List<TargetIntent.Ref.Existing> localRemovals = removals.stream().filter(r -> elements.containsKey(new XmlAssembly.Symbol.Original(original.get(r).origin().documentId(), original.get(r).origin().elementIndex()))).toList();
            for (var removed : localRemovals) {
                var origin = original.get(removed).origin(); var element = elements.get(new XmlAssembly.Symbol.Original(origin.documentId(), origin.elementIndex()));
                boolean covered = localRemovals.stream().anyMatch(parent -> !parent.equals(removed) && inside(parent, document, element(removed)));
                if (!covered) edits.add(XmlAssembly.Edit.simple(new XmlEdit.RemoveElement(element)));
            }
            for (var placement : placements.values()) if (placement.parent() instanceof TargetPlacement.Parent.Existing parent && parent.documentId().equals(document)
                    && java.util.Objects.equals(containingMove(document, parent(parent)), movedRoot)) {
                var selected = elements.get(new XmlAssembly.Symbol.Original(document, parent.elementIndex())); if (selected == null) fail("ELEMENT_PROVENANCE_MISMATCH");
                edits.add(XmlAssembly.Edit.insert(selected, fragment(placement.entity(), path)));
            }
            return edits.values();
        }
        private Map<String, String> references(TargetIntent.Ref ref) {
            Map<String, String> values = new TreeMap<>();
            for (var edge : expected.edges()) if (edge.source().equals(ref)) values.put(edge.relation(), target.get(edge.target()).identity().identity());
            return values;
        }
        private void scalarEdits(ExpectedTarget.Entity entity, XmlDocument.ElementRef selected, FieldLocatorResolver locator, List<XmlEdit> edits) {
            var old = (TargetIntent.Ref.Existing)entity.reference(); var before = original.get(old); var projection = projections.get(before.origin().projectionId());
            for (var mapping : projection.fields()) {
                String previous = before.fields().get(mapping.field()), replacement = entity.fields().get(mapping.field());
                if (!java.util.Objects.equals(previous, replacement)) {
                    var found=locator.resolve(selected,mapping.locator());
                    if(found instanceof FieldLocatorResolver.Refused refused)fail(refused.code());
                    if(!(found instanceof FieldLocatorResolver.Located) || previous==null || replacement==null)fail("ATTRIBUTE_PRESENCE_UNSUPPORTED");
                    var attribute=((FieldLocatorResolver.Located)found).attribute();
                    if(!attribute.value().equals(previous))fail("STALE_SOURCE");
                    edits.add(new XmlEdit.ReplaceAttribute(attribute,previous,replacement));
                }
            }
            var referenceValues = references(old);
            for (var mapping : projection.references()) {
                var attribute = attribute(element(old), mapping.attribute()); String previous = attribute == null ? null : attribute.value(), replacement = referenceValues.get(mapping.relation());
                if (!java.util.Objects.equals(previous, replacement)) replace(edits, selected, mapping.attribute(), previous, replacement);
            }
        }
        private static void replace(List<XmlEdit> edits, XmlDocument.ElementRef element, NativeDefinition.ExpandedName name, String expected, String replacement) {
            var attribute = attribute(element, name); if (attribute == null || expected == null || replacement == null) fail("ATTRIBUTE_PRESENCE_UNSUPPORTED");
            edits.add(new XmlEdit.ReplaceAttribute(attribute, expected, replacement));
        }
        private static XmlDocument.AttributeRef attribute(XmlDocument.ElementRef element, NativeDefinition.ExpandedName name) {
            return element.attributes().stream().filter(a -> a.name().namespaceUri().equals(name.namespaceUri()) && a.name().localName().equals(name.localName())).findFirst().orElse(null);
        }
        private XmlDocument.ElementRef element(TargetIntent.Ref.Existing ref) { var origin = original.get(ref).origin(); return sourceElements.get(new XmlAssembly.Symbol.Original(origin.documentId(), origin.elementIndex())); }
        private XmlDocument.ElementRef parent(TargetPlacement.Parent.Existing parent) {
            if (!admittedParents.contains(parent)) { fail("STALE_PARENT"); return null; }
            return sourceElements.get(new XmlAssembly.Symbol.Original(parent.documentId(), parent.elementIndex()));
        }
        private ProjectionResult.Accepted project(List<TargetSource> sources) {
            var result = graph.project(definition, bindingId, sources.stream().map(s -> new DocumentSource(s.documentId(), s.source())).toList());
            if (result instanceof ProjectionResult.Rejected refused) fail(refused.diagnostics().getFirst().code());
            return (ProjectionResult.Accepted)result;
        }
        private static XmlDocument parsed(XmlResult result) { if (result instanceof XmlResult.Rejected refused) fail(refused.diagnostics().getFirst().code()); return ((XmlResult.Accepted)result).document(); }
        private void compare(ObservedGraph actual) {
            Map<ObservedGraph.Key, Map<String, String>> expectedEntities = new HashMap<>(), actualEntities = new HashMap<>();
            expected.entities().forEach(e -> expectedEntities.put(e.identity(), e.fields())); actual.entities().forEach(e -> actualEntities.put(e.key(), e.fields()));
            Set<ObservedGraph.Edge> expectedEdges = new HashSet<>(); expected.edges().forEach(e -> expectedEdges.add(new ObservedGraph.Edge(e.relation(), target.get(e.source()).identity(), target.get(e.target()).identity())));
            if (!expectedEntities.equals(actualEntities) || !expectedEdges.equals(new HashSet<>(actual.edges()))) fail("SEMANTIC_OUTCOME_MISMATCH");
            Map<ObservedGraph.Key, ObservedGraph.Entity> entities = new HashMap<>(); actual.entities().forEach(e -> entities.put(e.key(), e));
            for (var entity : expected.entities()) {
                XmlAssembly.Symbol symbol = entity.reference() instanceof TargetIntent.Ref.Existing old ? new XmlAssembly.Symbol.Original(original.get(old).origin().documentId(), original.get(old).origin().elementIndex()) : new XmlAssembly.Symbol.Created((TargetIntent.Ref.Fresh)entity.reference());
                var position = positions.get(symbol); var actualEntity = entities.get(entity.identity());
                if (position == null || !position.document().equals(actualEntity.origin().documentId()) || position.index() != actualEntity.origin().elementIndex()) fail("ENTITY_POSITION_MISMATCH");
                if (entity.reference() instanceof TargetIntent.Ref.Existing old) {
                    var destination = destinations.get(old);
                    if (!destination.document().equals(actualEntity.origin().documentId()) || !destination.projection().equals(actualEntity.origin().projectionId())) fail("MOVED_DESCENDANT_PROJECTION_UNSUPPORTED");
                }
            }
            for (var placement : placements.values()) {
                var entity = entities.get(target.get(placement.entity()).identity());
                if (!entity.origin().documentId().equals(placement.documentId()) || !entity.origin().projectionId().equals(placement.projectionId())) fail("PLACEMENT_OUTCOME_MISMATCH");
                XmlAssembly.Symbol parent = placement.parent() instanceof TargetPlacement.Parent.Existing old ? new XmlAssembly.Symbol.Original(old.documentId(), old.elementIndex()) : new XmlAssembly.Symbol.Created(((TargetPlacement.Parent.Created)placement.parent()).entity());
                var expectedParent = positions.get(parent);
                if (expectedParent == null || !expectedParent.document().equals(entity.origin().documentId()) || entity.origin().ancestry().isEmpty() || expectedParent.index() != entity.origin().ancestry().getLast()) fail("PARENT_OUTCOME_MISMATCH");
            }
        }
    }
}
