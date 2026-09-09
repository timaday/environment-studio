package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.plan.HostedPlanService;
import studio.environment.core.plan.PlanBindings;
import studio.environment.server.xml.*;
import studio.environment.server.projection.FieldLocatorResolver;

/** Display transformations use qualified attribute spans, never global replacement or writer authority. */
final class PlanDocumentViews {
    static DocumentView render(HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode) {
        if(snapshot.definition().model() instanceof studio.environment.core.plan.PlanDefinition.V3)throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        return render(snapshot,target,documentId,mode,new studio.environment.core.observation.ObservationPort.Cancellation());
    }
    static DocumentView render(HostedPlanService.ViewSnapshot snapshot,boolean target,String documentId,ViewMode mode,studio.environment.core.observation.ObservationPort.Cancellation cancellation) {
        V3PlanReadContent.live(cancellation);
        var source=snapshot.selected(target).sources().stream().filter(s->s.documentId().equals(documentId)).findFirst().orElseThrow(()->new PlanRefusal(PlanRefusal.Code.NOT_FOUND));
        if(mode!=ViewMode.PLACEHOLDERS){
            if(snapshot.definition().model() instanceof studio.environment.core.plan.PlanDefinition.V3)V3PlanReadContent.verify(snapshot,target,cancellation);
            return concrete(snapshot.definition(),snapshot.binding(),source,mode,cancellation);
        }
        var text=new StringBuilder(source.xml().length());int[] previous={0};
        PlanBindingLocations.scan(snapshot,target,occurrence->{
            if(!occurrence.location().documentId().equals(documentId))return;
            var span=occurrence.location().span();if(span.start()<previous[0])throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
            var token=PlanBindings.token(snapshot,occurrence.entity(),occurrence.fieldId());
            bounded((long)text.length()+span.start()-previous[0]+token.length());
            text.append(source.xml(),previous[0],span.start()).append(token);previous[0]=span.end();
        },cancellation);
        bounded((long)text.length()+source.xml().length()-previous[0]);text.append(source.xml(),previous[0],source.xml().length());
        V3PlanReadContent.live(cancellation);return new DocumentView(documentId,mode,text.toString(),false,true,true,List.of());
    }
    private static DocumentView concrete(PublishedDefinition definition,String bindingId,Source source,ViewMode mode,studio.environment.core.observation.ObservationPort.Cancellation cancellation) {
        var parsed=new LosslessXmlAdapter().project(source.xml());
        V3PlanReadContent.live(cancellation);
        if(!(parsed instanceof XmlResult.Accepted accepted) || !accepted.document().digest().equals(source.digest())) throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
        var xml=accepted.document();
        var binding=definition.model().bindings().stream().filter(item->item.id().equals(bindingId)).findFirst().orElseThrow();
        var declaration=binding.documents().stream().filter(document->document.id().equals(source.documentId())).findFirst().orElseThrow();
        var locator=new FieldLocatorResolver(xml);
        if(locator.validate(declaration).isPresent())throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
        var types=new HashMap<String,studio.environment.core.definitionv2.NativeDefinition.EntityType>();
        definition.model().physical().entityTypes().forEach(type->types.put(type.id(),type));
        for(var element:xml.elements()) for(var projection:declaration.entities()) {
            V3PlanReadContent.live(cancellation);
            var path=new ArrayList<XmlDocument.ExpandedName>(); element.ancestry().forEach(index->path.add(xml.elements().get(index).name())); path.add(element.name());
            if(path.size()!=projection.path().size()) continue;
            boolean matches=true;
            for(int index=0;index<path.size();index++) if(!path.get(index).namespaceUri().equals(projection.path().get(index).namespaceUri()) || !path.get(index).localName().equals(projection.path().get(index).localName())) { matches=false; break; }
            if(!matches) continue;
            var fields=new HashMap<String,studio.environment.core.definitionv2.NativeDefinition.Field>(); types.get(projection.type()).fields().forEach(field->fields.put(field.id(),field));
            for(var mapping:projection.fields()) {
                var located=locator.resolve(element,mapping.locator());
                if(located instanceof FieldLocatorResolver.Refused)throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
                if(located instanceof FieldLocatorResolver.Located && !fields.get(mapping.field()).readable())throw new PlanRefusal(PlanRefusal.Code.DISCLOSURE_REQUIRED);
            }
        }
        if(mode==ViewMode.RAW) return new DocumentView(source.documentId(),mode,source.xml(),true,false,true,List.of());
        var text=new StringBuilder(source.xml().length()+Math.min(1_048_576,xml.elements().size()*8));
        int previous=0;
        var breaks=new TreeMap<Integer,Integer>();
        for(var element:xml.elements()) {
            V3PlanReadContent.live(cancellation);
            if(element.startTag().start()>0) breaks.put(element.startTag().start(),element.ancestry().size());
            if(!element.selfClosing() && element.endTagStart()>element.startTag().end()) breaks.put(element.endTagStart(),element.ancestry().size());
        }
        for(var boundary:breaks.entrySet()) {
            V3PlanReadContent.live(cancellation);
            text.append(source.xml(),previous,boundary.getKey()).append('\n').append("  ".repeat(Math.min(128,boundary.getValue()))); previous=boundary.getKey();
            if(text.length()>4_194_304) throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);
        }
        text.append(source.xml(),previous,source.xml().length());
        bounded(text.length());V3PlanReadContent.live(cancellation);return new DocumentView(source.documentId(),mode,text.toString(),false,false,true,List.of());
    }
    private static void bounded(long size){if(size>4_194_304)throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);}
}
