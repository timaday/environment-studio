package studio.environment.core.plan;

import java.util.Objects;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.NativeCommand;
import static studio.environment.core.plan.PlanPorts.*;

/** Explicit delegate selection; runtime qualification remains with the original versioned ports. */
public final class VersionedPlanWorkspace implements Workspace {
    private final Workspace v2;
    private final V3PlanWorkspace v3;
    public VersionedPlanWorkspace(Workspace v2,V3PlanWorkspace v3){this.v2=Objects.requireNonNull(v2);this.v3=Objects.requireNonNull(v3);}
    public PublishedDefinition definition(Owner owner,NativeCommand.Reference reference){
        input(owner,reference);var result=v2.definition(owner,reference);
        if(result==null || !reference.equals(result.reference()) || !(result.model() instanceof PlanDefinition.V2))throw unsupported();
        return result;
    }
    public PublishedDefinition definitionV3(Owner owner,NativeCommand.Reference reference){
        input(owner,reference);var result=v3.definition(owner,reference);
        if(result==null || !reference.equals(result.reference()))throw unsupported();
        return new PublishedDefinition(result.reference(),result.publicationDigest(),new PlanDefinition.V3(result.checked()),result.policies());
    }
    public PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PublishedDefinition definition){
        input(owner,reference);
        if(definition==null || !(definition.model() instanceof PlanDefinition.V2))throw unsupported();
        var result=v2.profile(owner,reference,definition);profileResult(reference,result);return result;
    }
    public PublishedProfile profileV3(Owner owner,NativeCommand.Reference reference,PublishedDefinition definition){
        input(owner,reference);
        if(definition==null || !(definition.model() instanceof PlanDefinition.V3 model))throw unsupported();
        var selected=new V3PlanWorkspace.Definition(definition.reference(),definition.publicationDigest(),model.checked(),definition.policies());
        var result=v3.profile(owner,reference,selected);
        if(result==null || !reference.equals(result.reference()))throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
        return new PublishedProfile(result.reference(),result.publicationDigest(),result.checked());
    }
    private static void input(Owner owner,NativeCommand.Reference reference){
        if(owner==null || reference==null)throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
    }
    private static void profileResult(NativeCommand.Reference reference,PublishedProfile profile){
        if(profile==null || !reference.equals(profile.reference()))throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
    }
    private static PlanRefusal unsupported(){return new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);}
}
