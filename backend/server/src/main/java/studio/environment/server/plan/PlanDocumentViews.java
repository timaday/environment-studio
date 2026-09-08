package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.server.xml.*;

/** Display transformations use qualified attribute spans, never global replacement or writer authority. */
final class PlanDocumentViews {
    static DocumentView render(PublishedDefinition definition,String bindingId,Source source,ViewMode mode) {
        var parsed=new LosslessXmlAdapter().project(source.xml());
        if(!(parsed instanceof XmlResult.Accepted accepted) || !accepted.document().digest().equals(source.digest())) throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
        var xml=accepted.document();
        var binding=definition.compiled().checked().definition().bindings().stream().filter(item->item.id().equals(bindingId)).findFirst().orElseThrow();
        var declaration=binding.documents().stream().filter(document->document.id().equals(source.documentId())).findFirst().orElseThrow();
        var types=new HashMap<String,studio.environment.core.definitionv2.NativeDefinition.EntityType>();
        definition.compiled().checked().definition().logical().entityTypes().forEach(type->types.put(type.id(),type));
        var replacements=new TreeMap<Integer,Replacement>();
        int slot=0;
        for(var element:xml.elements()) for(var projection:declaration.entities()) {
            var path=new ArrayList<XmlDocument.ExpandedName>(); element.ancestry().forEach(index->path.add(xml.elements().get(index).name())); path.add(element.name());
            if(path.size()!=projection.path().size()) continue;
            boolean matches=true;
            for(int index=0;index<path.size();index++) if(!path.get(index).namespaceUri().equals(projection.path().get(index).namespaceUri()) || !path.get(index).localName().equals(projection.path().get(index).localName())) { matches=false; break; }
            if(!matches) continue;
            var fields=new HashMap<String,studio.environment.core.definitionv2.NativeDefinition.Field>(); types.get(projection.type()).fields().forEach(field->fields.put(field.id(),field));
            for(var mapping:projection.fields()) for(var attribute:element.attributes()) if(attribute.name().namespaceUri().equals(mapping.attribute().namespaceUri()) && attribute.name().localName().equals(mapping.attribute().localName())) {
                if(!fields.get(mapping.field()).readable() && mode!=ViewMode.PLACEHOLDERS) throw new PlanRefusal(PlanRefusal.Code.DISCLOSURE_REQUIRED);
                replacements.put(attribute.valueSpan().start(),new Replacement(attribute.valueSpan().end(),"[[field-"+(++slot)+"]]"));
            }
            for(var mapping:projection.references()) for(var attribute:element.attributes()) if(attribute.name().namespaceUri().equals(mapping.attribute().namespaceUri()) && attribute.name().localName().equals(mapping.attribute().localName()))
                replacements.put(attribute.valueSpan().start(),new Replacement(attribute.valueSpan().end(),"[[reference-"+(++slot)+"]]"));
        }
        if(mode==ViewMode.RAW) return new DocumentView(source.documentId(),mode,source.xml(),true,false,true,List.of());
        var text=new StringBuilder(source.xml().length()+Math.min(1_048_576,xml.elements().size()*8));
        int previous=0;
        if(mode==ViewMode.PLACEHOLDERS) {
            for(var replacement:replacements.entrySet()) {
                text.append(source.xml(),previous,replacement.getKey()).append(replacement.getValue().text); previous=replacement.getValue().end;
            }
        } else {
            var breaks=new TreeMap<Integer,Integer>();
            for(var element:xml.elements()) {
                if(element.startTag().start()>0) breaks.put(element.startTag().start(),element.ancestry().size());
                if(!element.selfClosing() && element.endTagStart()>element.startTag().end()) breaks.put(element.endTagStart(),element.ancestry().size());
            }
            for(var boundary:breaks.entrySet()) {
                text.append(source.xml(),previous,boundary.getKey()).append('\n').append("  ".repeat(Math.min(128,boundary.getValue()))); previous=boundary.getKey();
                if(text.length()>4_194_304) throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);
            }
        }
        text.append(source.xml(),previous,source.xml().length());
        return new DocumentView(source.documentId(),mode,text.toString(),false,mode==ViewMode.PLACEHOLDERS,true,List.of());
    }
    private record Replacement(int end,String text) { }
}
