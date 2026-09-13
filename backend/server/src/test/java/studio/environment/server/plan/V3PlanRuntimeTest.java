package studio.environment.server.plan;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import studio.environment.core.plan.*;
import studio.environment.core.session.*;
import studio.environment.core.workspace.*;
import studio.environment.server.session.HostedSessions;
import studio.environment.server.workspace.*;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;

/** Real composition/storage/compilers with independently invented local history; no observation. */
class V3PlanRuntimeTest {
    @TempDir Path temporary;
    static MockEnvironment configured(Path directory) {
        String p="studio.plans.destinations[0].";
        return new MockEnvironment().withProperty("studio.workspace.directory",directory.toString())
                .withProperty(p+"id","mock-reader").withProperty(p+"engine","postgresql")
                .withProperty(p+"host","mock-db.invalid").withProperty(p+"port","5432").withProperty(p+"database","mock_database")
                .withProperty(p+"trust-material",Path.of("../../fixtures/plan-http-tls/mock-ca.pem").toAbsolutePath().normalize().toString())
                .withProperty(p+"transport-identity","a".repeat(64)).withProperty(p+"provisioning-policy-version","mock-policy-v1")
                .withProperty(p+"operation-policy-version","postgresql-read-operation-v1")
                .withProperty(p+"expected-physical-identity.systemIdentifier","123").withProperty(p+"expected-physical-identity.databaseOid","456")
                .withProperty(p+"expected-physical-identity.databaseName","mock_database")
                .withProperty(p+"owners[0].issuer","https://mock-issuer.invalid").withProperty(p+"owners[0].subject","MockOwner");
    }
    static SessionLedger.Lease lease(HostedSessions sessions,String subject) {
        var request=new MockHttpServletRequest();assertTrue(sessions.reserveLogin(request));
        var now=Instant.now();var token=new OidcIdToken("mock-token",now,now.plusSeconds(3600),Map.of("iss","https://mock-issuer.invalid","sub",subject));
        var principal=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),token);
        return assertInstanceOf(SessionLedger.Accepted.class,sessions.authenticated(request.getSession(),principal)).lease();
    }
    static PlanRuntime runtime(Path directory,HostedSessions sessions) {
        var env=configured(directory);var beans=new DefaultListableBeanFactory();beans.registerSingleton("sessions",sessions);
        return new PlanRuntime(env,RuntimeMode.HOSTED,new WorkspaceRuntime(env,RuntimeMode.HOSTED),beans.getBeanProvider(HostedSessions.class));
    }
    @Test void actualRuntimeReachesOwnedV3HistoryInsteadOfTheDefaultUnsupportedPort() {
        Path directory=temporary;SqliteDraftStore.initializeV3(directory);
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());var lease=lease(sessions,"MockOwner");
        var runtime=runtime(directory,sessions);assertTrue(runtime.inspectionApiConfigured());
        var ref=new NativeCommand.Reference(UUID.randomUUID().toString(),"1");
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND,assertThrows(WorkspaceRefusal.class,()->runtime.service().createV3(lease,UUID.randomUUID().toString(),ref,"mock-pg","mock-reader")).code());
        assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->runtime.service().view(lease,Optional.empty())).code());
    }
    @Test void explicitSchema2AndUpgradeKeepLegacyCreationAndReplayWithoutMigration()throws Exception {
        SqliteDraftStore.initialize(temporary);
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());var lease=lease(sessions,"MockOwner");
        var publication=V3PlanRuntimeFixtures.v2(temporary,lease.owner());
        for(boolean upgraded:List.of(false,true)) {
            if(upgraded)SqliteDraftStore.upgradeV3(temporary);
            var before=V3PlanRuntimeFixtures.counts(temporary,upgraded);
            var runtime=assertDoesNotThrow(()->runtime(temporary,sessions));
            var command=new PlanMetadataReader.Create(UUID.randomUUID().toString(),new NativeCommand.Reference(publication.objectId(),"2"),"mock-pg","mock-reader");
            assertEquals(upgraded?WorkspaceRefusal.Code.NOT_FOUND:WorkspaceRefusal.Code.UNAVAILABLE,
                    assertThrows(WorkspaceRefusal.class,()->runtime.service().createV3(lease,command.requestId(),command.definition(),command.bindingId(),command.destinationId())).code());
            var ack=runtime.create(lease,command);assertEquals("1",ack.revision());assertEquals(ack,runtime.create(lease,command));
            assertEquals(command.definition(),runtime.service().view(lease,Optional.empty()).definition());
            assertEquals(before,V3PlanRuntimeFixtures.counts(temporary,upgraded));
            runtime.service().discard(lease,ack.planId(),new HostedPlanService.Mutation("1",UUID.randomUUID().toString()));
        }
    }
    @Test void actualRuntimeDistinguishesUnpublishedFromCurrentQualificationAndForeignHistory()throws Exception {
        SqliteDraftStore.initializeV3(temporary);
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());var lease=lease(sessions,"MockOwner");
        var draft=V3PlanRuntimeFixtures.draft(temporary,lease.owner(),UUID.randomUUID().toString(),"0");
        // This helper writes explicitly test-produced historical-ready records, never runtime qualification.
        var publication=V3ProfileHttpFixtures.definition(temporary,lease.owner(),false);
        V3PlanRuntimeFixtures.draft(temporary,lease.owner(),publication.objectId(),"2");
        var before=V3PlanRuntimeFixtures.counts(temporary,true);
        for(int restart=0;restart<2;restart++) {
            var service=runtime(temporary,sessions).service();
            assertEquals(PlanRefusal.Code.PUBLICATION_REQUIRED,assertThrows(PlanRefusal.class,()->service.createV3(lease,UUID.randomUUID().toString(),
                    new NativeCommand.Reference(draft.objectId(),"1"),"mock-pg","mock-reader")).code());
            assertEquals(PlanRefusal.Code.UNSUPPORTED_DEFINITION,assertThrows(PlanRefusal.class,()->service.createV3(lease,UUID.randomUUID().toString(),
                    new NativeCommand.Reference(publication.objectId(),"2"),"mock-pg","mock-reader")).code());
            assertEquals(PlanRefusal.Code.PUBLICATION_REQUIRED,assertThrows(PlanRefusal.class,()->service.createV3(lease,UUID.randomUUID().toString(),
                    new NativeCommand.Reference(publication.objectId(),"3"),"mock-pg","mock-reader")).code());
            assertEquals(PlanRefusal.Code.NOT_FOUND,assertThrows(PlanRefusal.class,()->service.view(lease,Optional.empty())).code());
        }
        var foreign=lease(sessions,"ForeignOwner");var service=runtime(temporary,sessions).service();
        assertEquals(WorkspaceRefusal.Code.NOT_FOUND,assertThrows(WorkspaceRefusal.class,()->service.createV3(foreign,UUID.randomUUID().toString(),
                new NativeCommand.Reference(publication.objectId(),"2"),"mock-pg","mock-reader")).code());
        assertEquals(before,V3PlanRuntimeFixtures.counts(temporary,true));
    }
    @Test void actualRuntimeStillAppliesDestinationOwnerAndOriginalLeaseBeforeInstallation()throws Exception {
        SqliteDraftStore.initializeV3(temporary);
        var sessions=new HostedSessions(Clock.systemUTC(),List.of());var lease=lease(sessions,"ForeignOwner");
        var publication=V3PlanRuntimeFixtures.v2(temporary,lease.owner());var runtime=runtime(temporary,sessions);
        var command=new PlanMetadataReader.Create(UUID.randomUUID().toString(),new NativeCommand.Reference(publication.objectId(),"2"),"mock-pg","mock-reader");
        assertTrue(runtime.visible(lease.owner()).isEmpty());
        assertThrows(PlanRuntime.DestinationDenied.class,()->runtime.create(lease,command));
        sessions.quarantine(lease);
        assertEquals(PlanRefusal.Code.SESSION_REQUIRED,assertThrows(PlanRefusal.class,()->runtime.service().createV3(lease,UUID.randomUUID().toString(),
                new NativeCommand.Reference(UUID.randomUUID().toString(),"2"),"mock-pg","mock-reader")).code());
    }
}
