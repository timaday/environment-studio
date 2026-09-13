package studio.environment.server.plan;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import studio.environment.core.derived.DerivedResult;
import studio.environment.core.plan.*;
import studio.environment.core.profile.*;
import static studio.environment.server.plan.PlanViewProjection.object;

/** Closed v3 workflow projections. Full evaluation precedes bounded lazy page encoding. */
final class V3PlanWorkflowEncoding {
    private V3PlanWorkflowEncoding() { }
    static Map<String,Object> capture(HostedPlanService.ViewAdmission admission,PlanViewRequest.Capture request,Runnable verify){
        var mappings=new ArrayList<ProfileCapture.SlotMapping>();
        for(var mapping:request.mappings()){
            verify.run();mappings.add(new ProfileCapture.SlotMapping(admission.observed(mapping.handle()),mapping.slotId(),mapping.label()));
        }
        var captured=admission.capture(new ProfileCapture.Command(request.profileId(),new BigInteger(request.profileRevision()),mappings));
        verify.run();return object("revision",request.revision(),"definition",reference(captured.definition()),"format","json","source",captured.draft().source());
    }
    static Map<String,Object> preview(HostedPlanService.CompositionPreview value,PlanViewRequest.Preview request,Runnable verify){
        verify.run();var versioned=value.v3().orElseThrow(()->new PlanRefusal(PlanRefusal.Code.STALE_PREVIEW));
        if(!versioned.physical().equals(value.dependencies()))throw new PlanRefusal(PlanRefusal.Code.STALE_PREVIEW);
        var dependencies=value.dependencies();List<Map<String,Object>> items;
        int offset=request.offset(),limit=request.limit(),total;
        switch(request.section()){
            case INCLUDED->{
                var rows=sorted(dependencies.included(),Comparator.comparing(Profile.Entity::id),verify);total=rows.size();
                items=page(rows,offset,limit,verify,e->object("slotId",e.id(),"typeId",e.type(),"label",e.label(),"requiredInputs",sorted(e.requiredInputs(),Comparator.naturalOrder(),verify)));
            }
            case DEPENDENCIES->{
                var rows=sorted(dependencies.dependencies(),Comparator.comparing(ProfileComposer.Dependency::slot).thenComparing(ProfileComposer.Dependency::causedBy).thenComparing(ProfileComposer.Dependency::relation).thenComparing(d->d.reason().name()),verify);total=rows.size();
                items=page(rows,offset,limit,verify,d->object("slotId",d.slot(),"causedBy",d.causedBy(),"relationId",d.relation(),"reason",d.reason().name().toLowerCase(Locale.ROOT).replace('_','-')));
            }
            case RELATIONS->{
                var rows=sorted(dependencies.relations(),Comparator.comparing(Profile.Relation::type).thenComparing(Profile.Relation::from).thenComparing(Profile.Relation::to),verify);total=rows.size();
                items=page(rows,offset,limit,verify,r->object("relationId",r.type(),"fromSlot",r.from(),"toSlot",r.to()));
            }
            case CONFLICTS->{
                if(dependencies.conflicts().size()>=256)throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);
                var rows=sorted(dependencies.conflicts(),Comparator.comparing(ProfileComposer.Conflict::code).thenComparing(ProfileComposer.Conflict::slot).thenComparing(ProfileComposer.Conflict::relation),verify);total=rows.size();
                items=page(rows,offset,limit,verify,V3PlanWorkflowEncoding::conflict);
            }
            default->throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
        }
        verify.run();String digest=HostedPlanService.compositionPreviewDigest(value);verify.run();
        return object("revision",value.revision(),"total",total,"offset",offset,"nextOffset",next(total,offset,limit),"items",items,
                "previewDigest",digest,"pins",object("planId",value.planId(),"revision",value.revision(),"observationFingerprint",value.observationFingerprint(),
                        "profile",reference(value.profile()),"publicationDigest",value.publicationDigest(),"selectedRoots",value.roots(),"rootsDigest",value.rootsDigest(),"closureDigest",value.closureDigest()),
                "section",request.section().name().toLowerCase(Locale.ROOT),"affectedDerivations",versioned.affectedDerivations());
    }
    static Map<String,Object> validation(V3PlanWorkflowReader.Validation request,HostedPlanService.V3Validation value,Runnable verify){
        verify.run();
        if(request instanceof V3PlanWorkflowReader.Validation.Page page){
            if(!page.inputFingerprint().equals(value.inputFingerprint()))throw new PlanRefusal(PlanRefusal.Code.CONFLICT);
            var rules=value.computedRules().orElseThrow(()->new PlanRefusal(PlanRefusal.Code.INCOMPLETE_TARGET));
            return object("revision",page.revision(),"inputFingerprint",value.inputFingerprint(),"targetComplete",true,"total",rules.size(),"offset",page.offset(),
                    "nextOffset",next(rules.size(),page.offset(),page.limit()),"items",page(rules,page.offset(),page.limit(),verify,V3PlanWorkflowEncoding::rule));
        }
        var checks=new ArrayList<Map<String,Object>>();
        for(var check:value.checks()){verify.run();checks.add(object("check",check.check().name(),"outcome",check.outcome().name(),"inputFingerprint",check.inputFingerprint()));}
        var physical=new ArrayList<Map<String,Object>>();
        for(var entry:value.applicationRules().entrySet()){verify.run();physical.add(object("ruleId",entry.getKey(),"outcome",entry.getValue().name()));}
        return object("revision",request.revision(),"inputFingerprint",value.inputFingerprint(),"checks",List.copyOf(checks),"applicationRules",List.copyOf(physical),
                "targetComplete",value.targetComplete(),"computedRuleCount",value.computedRules().map(List::size).orElse(null),"exportAvailable",value.exportAvailable());
    }
    private static Map<String,Object> rule(DerivedResult.RuleCheck rule){
        return object("kind",rule.kind().name(),"declaration",rule.declaration(),"source",rule.source().map(key->object("computedType",key.computedType(),"derivation",key.derivation(),"value",key.value())).orElse(null),
                "actual",rule.actual().toString(),"minimum",rule.minimum().toString(),"maximum",rule.maximum().toString(),"outcome",rule.outcome().name());
    }
    private static Integer next(int total,int offset,int limit){int end=Math.min(offset,total)+Math.min(limit,total-Math.min(offset,total));return end<total?end:null;}
    private static <T> List<Map<String,Object>> page(List<T> rows,int offset,int limit,Runnable verify,Function<T,Map<String,Object>> map){
        int start=Math.min(offset,rows.size()),size=Math.min(limit,rows.size()-start);
        return Collections.unmodifiableList(new AbstractList<>(){
            public int size(){verify.run();return size;}
            public Map<String,Object> get(int index){Objects.checkIndex(index,size);verify.run();var result=map.apply(rows.get(start+index));verify.run();return result;}
        });
    }
    private static <T> List<T> sorted(Collection<T> values,Comparator<? super T> comparator,Runnable verify){
        var result=new ArrayList<T>();for(var value:values){verify.run();result.add(value);}
        result.sort((a,b)->{verify.run();return comparator.compare(a,b);});verify.run();return List.copyOf(result);
    }
    private static Map<String,Object> conflict(ProfileComposer.Conflict conflict){
        String slot=conflict.slot().isEmpty()?null:conflict.slot(),relation=conflict.relation().isEmpty()?null:conflict.relation(),rule=null;
        switch(conflict.code()){
            case "RELATION_CARDINALITY","MULTIPLE_CONTAINMENT_PARENTS","CONTAINMENT_PARENT_MISSING"->{if(relation==null)throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);}
            case "INVALID_TARGET_RELATION","CONTAINMENT_CYCLE"->{if(slot!=null || relation!=null)throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);}
            case "ENTITY_COUNT"->{if(slot!=null || relation==null)throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);rule=relation;relation=null;}
            default->throw new PlanRefusal(PlanRefusal.Code.PROJECTION_REFUSED);
        }
        return object("code",conflict.code(),"slotId",slot,"relationId",relation,"ruleId",rule);
    }
    private static Map<String,Object> reference(studio.environment.core.workspace.NativeCommand.Reference reference){return object("objectId",reference.objectId(),"workspaceRevision",reference.workspaceRevision());}
}
