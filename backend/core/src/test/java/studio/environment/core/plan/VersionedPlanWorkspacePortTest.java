package studio.environment.core.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.NativeCommand;
import static studio.environment.core.plan.PlanPorts.*;

/** Delegate controls use independently invented checked metadata, never runtime qualification. */
class VersionedPlanWorkspacePortTest {
    final Owner owner=new Owner("https://invented.invalid","owner");
    final NativeCommand.Reference reference=new NativeCommand.Reference("00000000-0000-4000-8000-000000000001","2");
    final NativeCommand.Reference profileRef=new NativeCommand.Reference("00000000-0000-4000-8000-000000000002","3");
    final PlanDefinition.V3 model=new PlanDefinition.V3(VersionedPlanCompositionTest.definition());
    final PublishedDefinition legacy=legacy();
    final PublishedProfile legacyPortable=legacyProfile();
    final PublishedProfile portable=new PublishedProfile(profileRef,"profile-digest",VersionedPlanCompositionTest.profile(model.checked()));
    final List<NativeCommand.Policy> policies=List.of(new NativeCommand.Policy("mock-binding","sheet","deny"));
    PublishedDefinition v2Definition=legacy;
    PublishedProfile v2Profile=legacyPortable;
    V3PlanWorkspace.Definition v3Definition=new V3PlanWorkspace.Definition(reference,"publication-v3",model.checked(),policies);
    V3PlanWorkspace.Profile v3Profile=new V3PlanWorkspace.Profile(profileRef,portable.publicationDigest(),portable.checked());
    V3PlanWorkspace.Definition received;
    int v2Calls,v3Calls;
    PlanRefusal refusal;
    final Workspace v2=new Workspace(){
        public PublishedDefinition definition(Owner o,NativeCommand.Reference r){v2Calls++;assertEquals(owner,o);assertEquals(reference,r);if(refusal!=null)throw refusal;return v2Definition;}
        public PublishedProfile profile(Owner o,NativeCommand.Reference r,PublishedDefinition d){v2Calls++;assertEquals(owner,o);assertEquals(profileRef,r);assertSame(legacy,d);if(refusal!=null)throw refusal;return v2Profile;}
    };
    final V3PlanWorkspace v3=new V3PlanWorkspace(){
        public Definition definition(Owner o,NativeCommand.Reference r){v3Calls++;assertEquals(owner,o);assertEquals(reference,r);if(refusal!=null)throw refusal;return v3Definition;}
        public Profile profile(Owner o,NativeCommand.Reference r,Definition d){v3Calls++;assertEquals(owner,o);assertEquals(profileRef,r);received=d;if(refusal!=null)throw refusal;return v3Profile;}
    };
    final VersionedPlanWorkspace bridge=new VersionedPlanWorkspace(v2,v3);
    private PublishedDefinition legacy(){
        var source=new studio.environment.core.definitionv2.NativeDefinition("mock-v2",java.math.BigInteger.ONE,model.physical(),model.bindings());
        var ready=assertInstanceOf(studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish.class,new studio.environment.core.definitionv2.NativeDefinitionCompiler().compile(source));
        return new PublishedDefinition(reference,"publication-v2",ready,List.of());
    }
    private PublishedProfile legacyProfile(){
        var mappings=List.of(new studio.environment.core.profile.ProfileCapture.SlotMapping(VersionedPlanCompositionTest.FIRST,"first","First"),new studio.environment.core.profile.ProfileCapture.SlotMapping(VersionedPlanCompositionTest.SECOND,"second","Second"),new studio.environment.core.profile.ProfileCapture.SlotMapping(VersionedPlanCompositionTest.HUB,"dependency","Dependency"));
        var command=new studio.environment.core.profile.ProfileCapture.Command("neutral",java.math.BigInteger.ONE,mappings);
        var result=new studio.environment.core.profile.ProfileCapture().capture(legacy.compiled(),new studio.environment.core.graph.GraphValidationResult.Accepted(VersionedPlanCompositionTest.graph()),command);
        return new PublishedProfile(profileRef,"profile-v2",assertInstanceOf(studio.environment.core.profile.ProfileResult.StructurallyValid.class,result).checked());
    }
    @Test void explicitVersionKeepsAllPinsAndNeverConvertsLegacyResults() {
        assertSame(legacy,bridge.definition(owner,reference));assertSame(legacyPortable,bridge.profile(owner,profileRef,legacy));
        var selected=bridge.definitionV3(owner,reference);
        assertEquals(new PublishedDefinition(reference,"publication-v3",model,policies),selected);
        assertEquals(portable,bridge.profileV3(owner,profileRef,selected));assertEquals(v3Definition,received);
        assertEquals(2,v2Calls);assertEquals(2,v3Calls);
    }
    @Test void versionMismatchRefusesBeforeEitherProfileDelegate() {
        var selected=new PublishedDefinition(reference,"publication-v3",model,policies);
        assertThrows(PlanRefusal.class,()->bridge.profile(owner,profileRef,selected));
        assertThrows(PlanRefusal.class,()->bridge.profileV3(owner,profileRef,legacy));
        assertEquals(0,v2Calls);assertEquals(0,v3Calls);
        v2Definition=selected;assertThrows(PlanRefusal.class,()->bridge.definition(owner,reference));assertEquals(0,v3Calls);
    }
    @Test void wrongReferencesAndMissingResultsNeverPassTheAdapterBoundary() {
        v3Definition=new V3PlanWorkspace.Definition(profileRef,"publication-v3",model.checked(),policies);
        assertThrows(PlanRefusal.class,()->bridge.definitionV3(owner,reference));v3Definition=null;
        assertThrows(PlanRefusal.class,()->bridge.definitionV3(owner,reference));
        v2Definition=new PublishedDefinition(profileRef,"publication-v2",legacy.model(),legacy.policies());
        assertThrows(PlanRefusal.class,()->bridge.definition(owner,reference));v2Definition=null;
        assertThrows(PlanRefusal.class,()->bridge.definition(owner,reference));
        var selected=new PublishedDefinition(reference,"publication-v3",model,policies);
        v3Profile=new V3PlanWorkspace.Profile(reference,"profile-v3",portable.checked());
        assertThrows(PlanRefusal.class,()->bridge.profileV3(owner,profileRef,selected));v3Profile=null;
        assertThrows(PlanRefusal.class,()->bridge.profileV3(owner,profileRef,selected));
        v2Profile=new PublishedProfile(reference,"profile-v2",portable.checked());
        assertThrows(PlanRefusal.class,()->bridge.profile(owner,profileRef,legacy));v2Profile=null;
        assertThrows(PlanRefusal.class,()->bridge.profile(owner,profileRef,legacy));
    }
    @Test void originalRefusalPropagatesOnceWithoutFallbackOrRetry() {
        refusal=new PlanRefusal(PlanRefusal.Code.NOT_FOUND);
        assertSame(refusal,assertThrows(PlanRefusal.class,()->bridge.definitionV3(owner,reference)));assertEquals(1,v3Calls);assertEquals(0,v2Calls);
        assertSame(refusal,assertThrows(PlanRefusal.class,()->bridge.definition(owner,reference)));assertEquals(1,v2Calls);
        assertEquals(PlanRefusal.Code.INVALID_REQUEST,assertThrows(PlanRefusal.class,()->bridge.definitionV3(null,reference)).code());
        assertEquals(PlanRefusal.Code.INVALID_REQUEST,assertThrows(PlanRefusal.class,()->bridge.profile(owner,null,legacy)).code());
        assertEquals(1,v2Calls);assertEquals(1,v3Calls);
    }
}
