package studio.environment.server.plan;

import java.util.Map;
import java.util.function.BiFunction;
import studio.environment.core.plan.*;
import studio.environment.core.observation.ObservationPort.Cancellation;

/** Explicit internal v3 presentation. No HTTP registration or runtime qualification. */
public final class V3PlanPhysicalViews {
    private V3PlanPhysicalViews() { }
    private static <T> T read(HostedPlanService.ViewAdmission admission,BiFunction<HostedPlanService.ViewSnapshot,Cancellation,T> render) {
        return admission.read((snapshot,control)->{
            if(!(snapshot.definition().model() instanceof PlanDefinition.V3))throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
            V3PlanReadContent.verify(snapshot,snapshot.target().isPresent(),control);
            var result=render.apply(snapshot,control);V3PlanReadContent.live(control);return result;
        });
    }
    public static Map<String,Object> documents(HostedPlanService.ViewAdmission admission){return read(admission,(snapshot,control)->PlanViewProjection.documents(snapshot,control));}
    public static Map<String,Object> entities(HostedPlanService.ViewAdmission admission,boolean target,int offset,int limit){return read(admission,(snapshot,control)->PlanViewProjection.entities(snapshot,target,offset,limit,control));}
    public static Map<String,Object> relations(HostedPlanService.ViewAdmission admission,boolean target,int offset,int limit){return read(admission,(snapshot,control)->PlanViewProjection.relations(snapshot,target,offset,limit,control));}
    public static Map<String,Object> draft(HostedPlanService.ViewAdmission admission,int offset,int limit){return read(admission,(snapshot,control)->PlanViewProjection.draft(snapshot,offset,limit,control));}
    public static Map<String,Object> containment(HostedPlanService.ViewAdmission admission,int offset,int limit){return read(admission,(snapshot,control)->PlanViewProjection.containment(snapshot,offset,limit,control));}
    public static Map<String,Object> placements(HostedPlanService.ViewAdmission admission,String document,String projection,int offset,int limit){return read(admission,(snapshot,control)->PlanViewProjection.placements(snapshot,document,projection,offset,limit,control));}
    public static Map<String,Object> bindings(HostedPlanService.ViewAdmission admission,PlanCommand.Ref ref,int offset,int limit) {
        return read(admission,(snapshot,control)->PlanBindingViews.bindings(snapshot,ref,offset,limit,control));
    }
    public static Map<String,Object> locations(HostedPlanService.ViewAdmission admission,PlanCommand.Ref ref,String field,boolean target,int offset,int limit,boolean disclosed) {
        if(!disclosed)throw new PlanRefusal(PlanRefusal.Code.DISCLOSURE_REQUIRED);
        return read(admission,(snapshot,control)->PlanBindingViews.locations(snapshot,ref,field,target,offset,limit,true,control));
    }
}
