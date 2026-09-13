package studio.environment.server.plan;

import java.math.BigInteger;
import java.util.*;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.ViewMode;
import studio.environment.core.profile.*;
import studio.environment.core.workspace.NativeCommand;
import static studio.environment.server.plan.PlanViewProjection.object;
import static studio.environment.server.plan.PlanViewRequest.*;

/** Safe closed wire projections; callers cannot supply checks, graph evidence or export capability. */
final class PlanViewResults {
    static Object render(HostedPlanService.ViewAdmission admission,Route route,PlanViewRequest request){
        return switch(route){
            case BINDINGS->{var page=(Bindings)request;yield PlanBindingViews.bindings(admission.snapshot(),page.entity(),page.offset(),page.limit());}
            case BINDING_LOCATIONS->{var page=(BindingLocations)request;yield PlanBindingViews.locations(admission.snapshot(),page.entity(),page.fieldId(),page.side()==Side.TARGET,page.offset(),page.limit(),page.disclosed());}
            case MATERIALIZATION->{var result=admission.materialize();yield object("revision",request.revision(),"complete",result.complete(),"diagnostics",result.diagnostics());}
            case DOCUMENTS->PlanViewProjection.documents(admission.snapshot());
            case ENTITIES->{var page=(GraphPage)request;yield PlanViewProjection.entities(admission.snapshot(),page.side()==Side.TARGET,page.offset(),page.limit());}
            case RELATIONS->{var page=(GraphPage)request;yield PlanViewProjection.relations(admission.snapshot(),page.side()==Side.TARGET,page.offset(),page.limit());}
            case DRAFT->{var page=(DraftPage)request;yield PlanViewProjection.draft(admission.snapshot(),page.offset(),page.limit());}
            case CONTAINMENT->{var page=(DraftPage)request;yield PlanViewProjection.containment(admission.snapshot(),page.offset(),page.limit());}
            case PLACEMENTS->{var page=(Placements)request;yield PlanViewProjection.placements(admission.snapshot(),page.documentId(),page.projectionId(),page.offset(),page.limit());}
            case DOCUMENT->{var doc=(Document)request;var result=admission.document(doc.side()==Side.TARGET,doc.documentId(),ViewMode.valueOf(doc.mode().name()),doc.disclosed());
                yield object("revision",request.revision(),"documentId",result.documentId(),"side",doc.side().name().toLowerCase(Locale.ROOT),"mode",doc.mode().name().toLowerCase(Locale.ROOT),"text",result.text(),"exact",result.exact(),"redacted",result.redacted(),"unmappedConcreteMayRemain",result.unmappedConcreteMayRemain(),"omissions",result.omissions());}
            case CAPTURE->{var capture=(Capture)request;var mappings=capture.mappings().stream().map(m->new ProfileCapture.SlotMapping(admission.observed(m.handle()),m.slotId(),m.label())).toList();
                var result=admission.capture(new ProfileCapture.Command(capture.profileId(),new BigInteger(capture.profileRevision()),mappings));
                yield object("revision",request.revision(),"definition",reference(result.definition()),"format","json","source",result.draft().source());}
            case PREVIEW->{var preview=(Preview)request;yield preview(admission.preview(preview.profile(),preview.all()?List.of():preview.roots()),preview.section(),preview.offset(),preview.limit());}
            case VALIDATION->{var result=admission.validation();yield object("revision",request.revision(),"inputFingerprint",result.inputFingerprint(),"checks",result.checks().stream().map(c->object("check",c.check().name(),"outcome",c.outcome().name(),"inputFingerprint",c.inputFingerprint())).toList(),"applicationRules",result.applicationRules().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->object("ruleId",e.getKey(),"outcome",e.getValue().name())).toList(),"exportAvailable",result.exportAvailable());}
        };
    }
    static Map<String,Object> preview(HostedPlanService.CompositionPreview preview,Section section,int offset,int limit){
        var dependencies=preview.dependencies();List<Map<String,Object>> items;
        switch(section){
            case INCLUDED->items=dependencies.included().stream().sorted(Comparator.comparing(Profile.Entity::id)).map(e->object("slotId",e.id(),"typeId",e.type(),"label",e.label(),"requiredInputs",e.requiredInputs().stream().sorted().toList())).toList();
            case DEPENDENCIES->items=dependencies.dependencies().stream().sorted(Comparator.comparing(ProfileComposer.Dependency::slot).thenComparing(ProfileComposer.Dependency::causedBy).thenComparing(ProfileComposer.Dependency::relation).thenComparing(d->d.reason().name())).map(d->object("slotId",d.slot(),"causedBy",d.causedBy(),"relationId",d.relation(),"reason",d.reason().name().toLowerCase(Locale.ROOT).replace('_','-'))).toList();
            case RELATIONS->items=dependencies.relations().stream().sorted(Comparator.comparing(Profile.Relation::type).thenComparing(Profile.Relation::from).thenComparing(Profile.Relation::to)).map(r->object("relationId",r.type(),"fromSlot",r.from(),"toSlot",r.to())).toList();
            case CONFLICTS->{
                if(dependencies.conflicts().size()>=256)throw new PlanRefusal(PlanRefusal.Code.RESOURCE_LIMIT);
                items=dependencies.conflicts().stream().sorted(Comparator.comparing(ProfileComposer.Conflict::code).thenComparing(ProfileComposer.Conflict::slot).thenComparing(ProfileComposer.Conflict::relation)).map(PlanViewResults::conflict).toList();
            }
            default->throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
        }
        var page=PlanViewProjection.page(preview.revision(),items,offset,limit);var result=new LinkedHashMap<>(page);
        result.put("previewDigest",HostedPlanService.compositionPreviewDigest(preview));result.put("pins",object("planId",preview.planId(),"revision",preview.revision(),"observationFingerprint",preview.observationFingerprint(),"profile",reference(preview.profile()),"publicationDigest",preview.publicationDigest(),"selectedRoots",preview.roots(),"rootsDigest",preview.rootsDigest(),"closureDigest",preview.closureDigest()));result.put("section",section.name().toLowerCase(Locale.ROOT));return Collections.unmodifiableMap(result);
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
    private static Map<String,Object> reference(NativeCommand.Reference reference){return object("objectId",reference.objectId(),"workspaceRevision",reference.workspaceRevision());}
}
