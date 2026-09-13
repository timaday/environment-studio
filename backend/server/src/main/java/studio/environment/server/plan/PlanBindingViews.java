package studio.environment.server.plan;

import java.util.*;
import studio.environment.core.plan.*;
import studio.environment.core.planning.TargetIntent;
import static studio.environment.server.plan.PlanViewProjection.object;

/** Closed value/location pages are rendered only inside an owned revision admission. */
final class PlanBindingViews {
    static Map<String,Object> bindings(HostedPlanService.ViewSnapshot snapshot,PlanCommand.Ref ref,int offset,int limit){
        if(snapshot.definition().model() instanceof PlanDefinition.V3)throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        return bindings(snapshot,ref,offset,limit,new studio.environment.core.observation.ObservationPort.Cancellation());
    }
    static Map<String,Object> bindings(HostedPlanService.ViewSnapshot snapshot,PlanCommand.Ref ref,int offset,int limit,studio.environment.core.observation.ObservationPort.Cancellation control){
        V3PlanReadContent.live(control);bounds(offset,limit,256);var entity=PlanBindings.resolve(snapshot,ref,control);
        boolean fresh=entity.reference() instanceof TargetIntent.Ref.Fresh;
        var current=fresh?Map.<String,Integer>of():counts(snapshot,false,entity.reference(),control);
        var target=snapshot.target().isPresent()?counts(snapshot,true,entity.reference(),control):Map.<String,Integer>of();
        int total=entity.fields().size(),end=Math.min(total,offset+limit);var items=new ArrayList<Map<String,Object>>();
        for(var field:entity.fields().subList(Math.min(offset,total),end)) {
            V3PlanReadContent.live(control);var currentCount=fresh?unavailable("CURRENT_ENTITY_ABSENT"):complete(current.getOrDefault(field.fieldId(),0));
            var targetCount=snapshot.target().isEmpty()?unavailable("INCOMPLETE_TARGET"):complete(target.getOrDefault(field.fieldId(),0));
            items.add(object("fieldId",field.fieldId(),"token",field.token(),"current",value(field.current()),"target",value(field.target()),"change",field.change().name().toLowerCase(Locale.ROOT),"currentLocations",currentCount,"targetLocations",targetCount));
        }
        return object("revision",snapshot.revision(),"total",total,"offset",offset,"nextOffset",end<total?end:null,"items",List.copyOf(items));
    }
    static Map<String,Object> locations(HostedPlanService.ViewSnapshot snapshot,PlanCommand.Ref ref,String field,boolean target,int offset,int limit,boolean disclosed){
        if(snapshot.definition().model() instanceof PlanDefinition.V3)throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        return locations(snapshot,ref,field,target,offset,limit,disclosed,new studio.environment.core.observation.ObservationPort.Cancellation());
    }
    static Map<String,Object> locations(HostedPlanService.ViewSnapshot snapshot,PlanCommand.Ref ref,String field,boolean target,int offset,int limit,boolean disclosed,studio.environment.core.observation.ObservationPort.Cancellation control){
        V3PlanReadContent.live(control);if(!disclosed)throw new PlanRefusal(PlanRefusal.Code.DISCLOSURE_REQUIRED);
        bounds(offset,limit,Integer.MAX_VALUE);var entity=PlanBindings.resolve(snapshot,ref,control);
        if(entity.fields().stream().noneMatch(f->f.fieldId().equals(field)))throw new PlanRefusal(PlanRefusal.Code.NOT_FOUND);
        if(!target && entity.reference() instanceof TargetIntent.Ref.Fresh)throw new PlanRefusal(PlanRefusal.Code.NOT_FOUND);
        snapshot.selected(target);var page=new LocationPage(offset,limit);
        PlanBindingLocations.scan(snapshot,target,occurrence->{if(occurrence.entity().equals(entity.reference()) && occurrence.fieldId().equals(field))page.add(occurrence.location());},control);
        long next=(long)offset+page.items.size();
        return object("revision",snapshot.revision(),"total",page.total,"offset",offset,"nextOffset",next<page.total?(int)next:null,"items",List.copyOf(page.items));
    }
    private static final class LocationPage {
        final int offset,limit;int total;final List<Map<String,Object>> items=new ArrayList<>();
        LocationPage(int offset,int limit){this.offset=offset;this.limit=limit;}
        void add(PlanBindingLocations.Location location){
            if(total==Integer.MAX_VALUE)throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);
            if(total>=offset && items.size()<limit)items.add(object("documentId",location.documentId(),"sourceDigest",location.sourceDigest(),"projectionId",location.projectionId(),"elementIndex",Integer.toString(location.elementIndex()),"attribute",object("namespaceUri",location.attribute().namespaceUri(),"localName",location.attribute().localName()),"span",object("start",location.span().start(),"end",location.span().end()),"role",location.role(),"declarationId",location.declarationId()));
            total++;
        }
    }
    private static Map<String,Integer> counts(HostedPlanService.ViewSnapshot snapshot,boolean target,TargetIntent.Ref entity,studio.environment.core.observation.ObservationPort.Cancellation control){
        var counts=new HashMap<String,Integer>();
        PlanBindingLocations.scan(snapshot,target,occurrence->{if(occurrence.entity().equals(entity))counts.compute(occurrence.fieldId(),(field,total)->{
            if(total!=null && total==Integer.MAX_VALUE)throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);return total==null?1:total+1;
        });},control);return counts;
    }
    private static Map<String,Object> value(PlanBindings.Value value){return value.state()==PlanBindings.State.VALUE?object("state","value","text",value.text().orElseThrow()):object("state",value.state().name().toLowerCase(Locale.ROOT));}
    private static Map<String,Object> unavailable(String code){return object("state","unavailable","code",code);}
    private static Map<String,Object> complete(int total){return object("state","complete","total",total);}
    private static void bounds(int offset,int limit,int maximum){if(offset<0 || offset>maximum || limit<1 || limit>100)throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);}
}
