package studio.environment.server.plan;

import java.util.*;
import java.util.function.Consumer;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.plan.PlanPorts.Content;
import studio.environment.server.projection.*;
import studio.environment.server.xml.*;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.xml.XmlDocument;

/** Operation-scoped complete mapping scan; the consumer retains only its bounded page/counts. */
final class PlanBindingLocations {
    record Location(String documentId,String sourceDigest,String projectionId,int elementIndex,
            XmlDocument.ExpandedName attribute,XmlDocument.Span span,String role,String declarationId) {
        @Override public String toString(){return "BindingLocation[redacted]";}
    }
    record Occurrence(TargetIntent.Ref entity,String fieldId,Location location) {
        @Override public String toString(){return "BindingOccurrence[redacted]";}
    }
    static void scan(HostedPlanService.ViewSnapshot snapshot,boolean target,Consumer<Occurrence> consumer) {
        if(snapshot.definition().model() instanceof PlanDefinition.V3)throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        scan(snapshot,target,consumer,new studio.environment.core.observation.ObservationPort.Cancellation());
    }
    static void scan(HostedPlanService.ViewSnapshot snapshot,boolean target,Consumer<Occurrence> consumer,studio.environment.core.observation.ObservationPort.Cancellation cancellation) {
        V3PlanReadContent.live(cancellation);
        var content=snapshot.selected(target);
        if(snapshot.definition().model() instanceof PlanDefinition.V3)V3PlanReadContent.verify(snapshot,target,cancellation);
        else {
            var projected=new GraphProjectionAdapter().project(snapshot.definition().compiled(),snapshot.binding(),content.sources().stream().map(s->new DocumentSource(s.documentId(),s.xml())).toList());
            if(!(projected instanceof ProjectionResult.Accepted complete) || !complete.graph().equals(content.graph()))throw refused();
            var digests=new HashMap<String,String>();complete.projection().documents().forEach(d->digests.put(d.documentId(),d.digest()));
            for(var source:content.sources())if(!source.digest().equals(digests.get(source.documentId())))throw refused();
        }
        var provenance=new HashSet<TargetIntent.Ref>();var byDocument=new HashMap<String,TreeMap<Integer,ObservedGraph.Entity>>();
        for(var entity:content.graph().entities()) {
            V3PlanReadContent.live(cancellation);
            var ref=reference(snapshot,content,entity.key());
            if(!provenance.add(ref) || !ref.type().equals(entity.key().type()))throw refused();
            if(byDocument.computeIfAbsent(entity.origin().documentId(),ignored->new TreeMap<>()).putIfAbsent(entity.origin().elementIndex(),entity)!=null)throw refused();
        }
        if(provenance.size()!=content.provenance().size())throw refused();
        var binding=snapshot.definition().model().bindings().stream().filter(b->b.id().equals(snapshot.binding())).findFirst().orElseThrow(PlanBindingLocations::refused);
        var documents=new HashMap<String,NativeDefinition.Document>();binding.documents().forEach(d->documents.put(d.id(),d));
        var types=new HashMap<String,NativeDefinition.EntityType>();snapshot.definition().model().physical().entityTypes().forEach(t->types.put(t.id(),t));
        var referenceRelations=new HashSet<String>();
        snapshot.definition().model().physical().relations().stream().filter(r->r.kind()==studio.environment.core.definition.DefinitionDraft.RelationKind.REFERENCE).forEach(r->referenceRelations.add(r.id()));
        var edges=new HashMap<ObservedGraph.Key,Map<String,ObservedGraph.Key>>();
        for(var edge:content.graph().edges())if(referenceRelations.contains(edge.relation()) && edges.computeIfAbsent(edge.source(),ignored->new HashMap<>()).putIfAbsent(edge.relation(),edge.target())!=null)throw refused();
        for(var source:content.sources().stream().sorted(Comparator.comparing(PlanPorts.Source::documentId)).toList()) {
            var parsed=new LosslessXmlAdapter().project(source.xml());
            V3PlanReadContent.live(cancellation);
            if(!(parsed instanceof XmlResult.Accepted accepted) || !accepted.document().digest().equals(source.digest()))throw refused();
            var xml=accepted.document();var locator=new FieldLocatorResolver(xml);var declaration=documents.get(source.documentId());if(declaration==null)throw refused();
            var projections=new HashMap<String,NativeDefinition.Projection>();declaration.entities().forEach(p->projections.put(p.id(),p));
            if(locator.validate(declaration).isPresent())throw refused();
            var occurrences=new TreeMap<Integer,Occurrence>();
            var entities=byDocument.get(source.documentId());if(entities==null)continue;
            for(var entry:entities.entrySet()) {
                V3PlanReadContent.live(cancellation);
                int index=entry.getKey();if(index<0 || index>=xml.elements().size())throw refused();
                var entity=entry.getValue();var element=xml.elements().get(index);var projection=projections.get(entity.origin().projectionId());
                if(projection==null || !entity.origin().sourceDigest().equals(source.digest()))throw refused();
                var attributes=new HashMap<XmlDocument.ExpandedName,XmlDocument.AttributeRef>();element.attributes().forEach(a->attributes.put(a.name(),a));
                var own=reference(snapshot,content,entity.key());
                for(var mapping:projection.fields()) {
                    var located=locator.resolve(element,mapping.locator());
                    if(located instanceof FieldLocatorResolver.Refused)throw refused();
                    var attribute=located instanceof FieldLocatorResolver.Located found?found.attribute():null;
                    String value=entity.fields().get(mapping.field());
                    if(attribute==null){if(value!=null)throw refused();continue;}
                    if(!attribute.value().equals(value))throw refused();
                    add(occurrences,source,projection.id(),xml.elements().get(attribute.elementIndex()),attribute,own,mapping.field(),"field",mapping.field());
                }
                for(var mapping:projection.references()) {
                    var attribute=attributes.get(name(mapping.attribute()));var linked=edges.getOrDefault(entity.key(),Map.of()).get(mapping.relation());
                    if(attribute==null){if(linked!=null)throw refused();continue;}
                    if(linked==null || !attribute.value().equals(linked.identity()))throw refused();
                    var targetType=types.get(linked.type());if(targetType==null)throw refused();
                    add(occurrences,source,projection.id(),element,attribute,reference(snapshot,content,linked),targetType.identity().field(),"reference",mapping.relation());
                }
            }
            for(var occurrence:occurrences.values()){V3PlanReadContent.live(cancellation);consumer.accept(occurrence);}
        }
        V3PlanReadContent.live(cancellation);
    }
    private static void add(Map<Integer,Occurrence> occurrences,PlanPorts.Source source,String projectionId,XmlDocument.ElementRef element,
            XmlDocument.AttributeRef attribute,TargetIntent.Ref ref,String field,String role,String declarationId) {
        var span=attribute.valueSpan();
        if(attribute.elementIndex()!=element.index() || !attribute.sourceDigest().equals(source.digest()) || span.start()<0 || span.end()<span.start() || span.end()>source.xml().length())throw refused();
        var location=new Location(source.documentId(),source.digest(),projectionId,element.index(),attribute.name(),span,role,declarationId);
        if(occurrences.putIfAbsent(span.start(),new Occurrence(ref,field,location))!=null)throw refused();
    }
    private static TargetIntent.Ref reference(HostedPlanService.ViewSnapshot snapshot,Content content,ObservedGraph.Key key) {
        var ref=content.provenance().get(key);if(ref==null)throw refused();snapshot.displayHandle(ref);return ref;
    }
    private static XmlDocument.ExpandedName name(NativeDefinition.ExpandedName name){return new XmlDocument.ExpandedName(name.namespaceUri(),name.localName());}
    private static PlanRefusal refused(){return new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);}
}
