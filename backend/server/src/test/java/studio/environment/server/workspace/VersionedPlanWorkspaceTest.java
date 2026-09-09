package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Independently invented SQLite history; qualified publications below are test witnesses only. */
class VersionedPlanWorkspaceTest {
    static final Workspace NO_V2=new Workspace(){
        public PublishedDefinition definition(Owner owner,NativeCommand.Reference ref){throw new AssertionError("WRONG_VERSION");}
        public PublishedProfile profile(Owner owner,NativeCommand.Reference ref,PublishedDefinition d){throw new AssertionError("WRONG_VERSION");}
    };
    @Test void actualVersionedPublicationLookupKeepsExactPinsAndActualCompilerRefusal() throws Exception {
        var f=new V3PlanWorkspaceBridgeTest();f.setup();
        try {
            var original=V3ProfileHttpFixtures.definition(f.directory,f.owner,false);
            var profile=f.profile(original,DraftCommand.Format.JSON);var before=f.counts();
            var bridge=new VersionedPlanWorkspace(NO_V2,f.bridge());
            var definition=assertDoesNotThrow(()->bridge.definitionV3(f.owner,V3PlanWorkspaceBridgeTest.reference(original)));
            assertEquals(V3PlanWorkspaceBridgeTest.reference(original),definition.reference());
            assertEquals(original.publication().orElseThrow().digest(),definition.publicationDigest());
            assertEquals(original.publication().orElseThrow().exportPolicies(),definition.policies());
            assertEquals(((V3NativeRevision.Definition)original.content()).checked(),assertInstanceOf(PlanDefinition.V3.class,definition.model()).checked());
            var selected=bridge.profileV3(f.owner,V3PlanWorkspaceBridgeTest.reference(profile),definition);
            assertEquals(V3PlanWorkspaceBridgeTest.reference(profile),selected.reference());assertEquals(profile.publication().orElseThrow().digest(),selected.publicationDigest());
            assertEquals(((V3NativeRevision.Profile)profile.content()).checked(),selected.checked());
            var actual=new VersionedPlanWorkspace(NO_V2,new V3PlanWorkspaceBridge(f.store));
            assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->actual.definitionV3(f.owner,definition.reference())).code());
            assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->actual.profileV3(f.owner,selected.reference(),definition)).code());
            var changed=new PublishedDefinition(definition.reference(),"changed-publication",definition.model(),definition.policies());
            assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->bridge.profileV3(f.owner,selected.reference(),changed)).code());
            assertThrows(WorkspaceRefusal.class,()->bridge.definitionV3(new Owner(f.owner.issuer(),"another-owner"),definition.reference()));
            assertEquals(before,f.counts());
        }finally{f.cleanup();}
    }
}
