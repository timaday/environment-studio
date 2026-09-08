package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.definition.DefinitionDraft.Sensitivity;
import studio.environment.core.definitionv2.NativeDefinition;
import studio.environment.core.graph.ObservedGraph;
import studio.environment.core.plan.*;
import studio.environment.core.plan.HostedPlanService.ViewSnapshot;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.planning.TargetIntent;
import studio.environment.server.xml.*;

/** Presentation only: complete selected scope, declared masking, opaque references and source-qualified coordinates. */
final class PlanViewProjection {
    static Map<String,Object> object(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(result);}
    static Map<String,Object> reference(PlanCommand.Ref ref){return switch(ref){case PlanCommand.Ref.Existing e->object("kind","existing","handle",e.handle());case PlanCommand.Ref.Fresh f->object("kind","fresh","slotId",f.slotId(),"typeId",f.typeId());};}
    private static String order(PlanCommand.Ref ref){return switch(ref){case PlanCommand.Ref.Existing e->"existing:"+e.handle();case PlanCommand.Ref.Fresh f->"fresh:"+f.slotId()+":"+f.typeId();};}
    private static PlanCommand.Ref reference(ViewSnapshot snapshot,Content content,ObservedGraph.Key key){var provenance=content.provenance().get(key);if(provenance==null)throw refused();return snapshot.reference(provenance);}
    static <T> Map<String,Object> page(String revision,List<T> items,int offset,int limit){
        if(offset<0 || offset>50000 || limit<1 || limit>100)throw refused();int end=Math.min(items.size(),offset+limit);
        return object("revision",revision,"total",items.size(),"offset",offset,"nextOffset",end<items.size()?end:null,"items",items.subList(Math.min(offset,items.size()),end));
    }
    static Map<String,Object> documents(ViewSnapshot snapshot){
        var current=snapshot.selected(false);var targets=new HashMap<String,Source>();snapshot.target().ifPresent(t->t.sources().forEach(s->targets.put(s.documentId(),s)));
        var documents=current.sources().stream().sorted(Comparator.comparing(Source::documentId)).map(source->{var target=targets.get(source.documentId());
            if(snapshot.target().isPresent() && target==null)throw refused();return object("documentId",source.documentId(),"currentDigest",source.digest(),"targetDigest",target==null?null:target.digest(),"changed",target==null?null:!source.digest().equals(target.digest()));}).toList();
        return object("revision",snapshot.revision(),"documents",documents);
    }
    static Map<String,Object> entities(ViewSnapshot snapshot,boolean target,int offset,int limit){
        var content=snapshot.selected(target);var types=types(snapshot);
        var sorted=content.graph().entities().stream().sorted(Comparator.comparing(e->order(reference(snapshot,content,e.key())))).toList();
        if(offset<0 || offset>50000 || limit<1 || limit>100)throw refused();int end=Math.min(sorted.size(),offset+limit);
        var items=new ArrayList<Map<String,Object>>();
        for(var entity:sorted.subList(Math.min(offset,sorted.size()),end)){
            var type=types.get(entity.key().type());if(type==null)throw refused();var fields=type.fields().stream().sorted(Comparator.comparing(NativeDefinition.Field::id)).map(field->{
                String value=entity.fields().get(field.id());boolean masked=masked(field);return object("fieldId",field.id(),"present",value!=null,"masked",masked,"value",masked?null:value);}).toList();
            items.add(object("entity",reference(reference(snapshot,content,entity.key())),"typeId",entity.key().type(),"fields",fields));
        }
        return object("revision",snapshot.revision(),"total",sorted.size(),"offset",offset,"nextOffset",end<sorted.size()?end:null,"items",List.copyOf(items));
    }
    static Map<String,Object> relations(ViewSnapshot snapshot,boolean target,int offset,int limit){
        var content=snapshot.selected(target);var keys=new HashSet<ObservedGraph.Key>();content.graph().entities().forEach(e->keys.add(e.key()));
        for(var edge:content.graph().edges())if(!keys.contains(edge.source()) || !keys.contains(edge.target()))throw refused();
        var items=content.graph().edges().stream().sorted(Comparator.comparing(ObservedGraph.Edge::relation).thenComparing(e->order(reference(snapshot,content,e.source()))).thenComparing(e->order(reference(snapshot,content,e.target()))))
            .map(e->object("relationId",e.relation(),"from",reference(reference(snapshot,content,e.source())),"to",reference(reference(snapshot,content,e.target())))).toList();return page(snapshot.revision(),items,offset,limit);
    }
    static Map<String,Object> draft(ViewSnapshot snapshot,int offset,int limit){
        var types=types(snapshot);var decisions=snapshot.draft().intent().entities().stream().sorted(Comparator.comparing(e->order(snapshot.reference(e.entity())))).toList();
        if(offset<0 || offset>50000 || limit<1 || limit>100)throw refused();int end=Math.min(decisions.size(),offset+limit);var items=new ArrayList<Map<String,Object>>();
        for(var decision:decisions.subList(Math.min(offset,decisions.size()),end)){
            String disposition;Map<String,TargetIntent.FieldValue> values;Map<String,TargetIntent.ReferenceValue> links;
            switch(decision){case TargetIntent.EntityDecision.Remove ignored->{disposition="remove";values=Map.of();links=Map.of();}case TargetIntent.EntityDecision.Retain r->{disposition="retain";values=r.fields();links=r.references();}case TargetIntent.EntityDecision.Create c->{disposition="create";values=c.fields();links=c.references();}}
            var declaration=types.get(decision.entity().type());if(declaration==null)throw refused();var fieldsById=new HashMap<String,NativeDefinition.Field>();declaration.fields().forEach(f->fieldsById.put(f.id(),f));
            var fields=values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry->{var field=fieldsById.get(entry.getKey());if(field==null)throw refused();boolean mask=masked(field);String kind=switch(entry.getValue()){case TargetIntent.FieldValue.Unresolved ignored->"unresolved";case TargetIntent.FieldValue.Entered ignored->"entered";case TargetIntent.FieldValue.KeepObserved ignored->"keep-observed";case TargetIntent.FieldValue.ExplicitlyAbsent ignored->"absent";};return object("fieldId",entry.getKey(),"kind",kind,"masked",mask,"value",!mask && entry.getValue() instanceof TargetIntent.FieldValue.Entered e?e.text():null);}).toList();
            var references=links.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry->{String kind=switch(entry.getValue()){case TargetIntent.ReferenceValue.Unresolved ignored->"unresolved";case TargetIntent.ReferenceValue.To ignored->"to";case TargetIntent.ReferenceValue.KeepObserved ignored->"keep-observed";case TargetIntent.ReferenceValue.ExplicitlyAbsent ignored->"absent";};return object("referenceId",entry.getKey(),"kind",kind,"target",entry.getValue() instanceof TargetIntent.ReferenceValue.To to?reference(snapshot.reference(to.target())):null);}).toList();
            var placements=disposition.equals("remove")?List.of():snapshot.draft().placements().stream().filter(p->p.entity().equals(decision.entity())).sorted(Comparator.comparing(Placement::documentId).thenComparing(Placement::projectionId)).map(p->placement(snapshot,p)).toList();
            items.add(object("entity",reference(snapshot.reference(decision.entity())),"disposition",disposition,"fields",fields,"references",references,"placements",placements));
        }
        return object("revision",snapshot.revision(),"total",decisions.size(),"offset",offset,"nextOffset",end<decisions.size()?end:null,"items",items);
    }
    static Map<String,Object> containment(ViewSnapshot snapshot,int offset,int limit){var items=snapshot.draft().intent().containment().stream().sorted(Comparator.comparing(TargetIntent.Containment::relation).thenComparing(c->order(snapshot.reference(c.child())))).map(c->object("relationId",c.relation(),"parent",reference(snapshot.reference(c.parent())),"child",reference(snapshot.reference(c.child())))).toList();return page(snapshot.revision(),items,offset,limit);}
    static Map<String,Object> placement(ViewSnapshot snapshot,Placement placement){Object parent=switch(placement.parent()){case Parent.Existing p->object("kind","existing","documentId",p.documentId(),"sourceDigest",p.sourceDigest(),"elementIndex",Integer.toString(p.elementIndex()));case Parent.Created p->object("kind","fresh","entity",reference(snapshot.reference(p.entity())));};return object("entity",reference(snapshot.reference(placement.entity())),"documentId",placement.documentId(),"projectionId",placement.projectionId(),"parent",parent);}
    static Map<String,Object> placements(ViewSnapshot snapshot,String documentId,String projectionId,int offset,int limit){
        var source=snapshot.selected(false).sources().stream().filter(s->s.documentId().equals(documentId)).findFirst().orElseThrow(PlanViewProjection::refused);
        var binding=snapshot.definition().compiled().checked().definition().bindings().stream().filter(b->b.id().equals(snapshot.binding())).findFirst().orElseThrow(PlanViewProjection::refused);
        var declaration=binding.documents().stream().filter(d->d.id().equals(documentId)).findFirst().orElseThrow(PlanViewProjection::refused);
        var projection=declaration.entities().stream().filter(p->p.id().equals(projectionId)).findFirst().orElseThrow(PlanViewProjection::refused);
        if(projection.path().size()<2)throw refused();var parentPath=projection.path().subList(0,projection.path().size()-1);
        var result=new LosslessXmlAdapter().project(source.xml());if(!(result instanceof XmlResult.Accepted accepted) || !accepted.document().digest().equals(source.digest()))throw refused();
        var xml=accepted.document();var items=new ArrayList<Map<String,Object>>();
        for(var element:xml.elements()){
            if(element.ancestry().size()+1!=parentPath.size())continue;var path=new ArrayList<XmlDocument.ExpandedName>();element.ancestry().forEach(i->path.add(xml.elements().get(i).name()));path.add(element.name());boolean matches=true;
            for(int i=0;i<path.size();i++)if(!path.get(i).namespaceUri().equals(parentPath.get(i).namespaceUri()) || !path.get(i).localName().equals(parentPath.get(i).localName())){matches=false;break;}
            if(matches)items.add(object("documentId",documentId,"sourceDigest",source.digest(),"elementIndex",Integer.toString(element.index())));
        }
        return page(snapshot.revision(),items,offset,limit);
    }
    private static Map<String,NativeDefinition.EntityType> types(ViewSnapshot snapshot){var result=new HashMap<String,NativeDefinition.EntityType>();snapshot.definition().compiled().checked().definition().logical().entityTypes().forEach(t->result.put(t.id(),t));return result;}
    private static boolean masked(NativeDefinition.Field field){return !field.readable() || field.sensitivity()==Sensitivity.SECRET || field.sensitivity()==Sensitivity.UNKNOWN;}
    private static PlanRefusal refused(){return new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);}
}
