package studio.environment.server.plan;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import studio.environment.core.session.Owner;
import static org.junit.jupiter.api.Assertions.*;

class PlanDestinationsTest {
    @org.junit.jupiter.api.io.TempDir Path workspaceDirectory;
    private MockEnvironment configured() {
        String prefix="studio.plans.destinations[0].";
        return new MockEnvironment()
            .withProperty(prefix+"id","mock-reader").withProperty(prefix+"engine","postgresql")
            .withProperty(prefix+"host","mock-db.invalid").withProperty(prefix+"port","5432").withProperty(prefix+"database","mock_database")
            .withProperty(prefix+"trust-material",Path.of("../../fixtures/plan-http-tls/mock-ca.pem").toAbsolutePath().normalize().toString())
            .withProperty(prefix+"transport-identity","a".repeat(64)).withProperty(prefix+"provisioning-policy-version","mock-policy-v1")
            .withProperty(prefix+"operation-policy-version","postgresql-read-operation-v1")
            .withProperty(prefix+"expected-physical-identity.systemIdentifier","123").withProperty(prefix+"expected-physical-identity.databaseOid","456")
            .withProperty(prefix+"expected-physical-identity.databaseName","mock_database")
            .withProperty(prefix+"owners[0].issuer","https://mock-issuer.invalid").withProperty(prefix+"owners[0].subject","MockOwner");
    }
    @Test void onlyExactOwnersSeeValidatedTlsDestinations() {
        var destinations=new PlanDestinations(configured());
        assertEquals(1,destinations.visible(new Owner("https://mock-issuer.invalid","MockOwner")).size());
        assertTrue(destinations.visible(new Owner("https://mock-issuer.invalid","mockowner")).isEmpty());
        assertTrue(new PlanDestinations(new MockEnvironment()).visible(new Owner("https://mock-issuer.invalid","MockOwner")).isEmpty());
    }
    @Test void inspectionApiConfigurationRequiresHostedWorkspaceAndDestinationsWithoutConnecting() {
        var workspace=org.mockito.Mockito.mock(studio.environment.server.workspace.WorkspaceRuntime.class);
        var sessions=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        sessions.registerSingleton("mockSessions",org.mockito.Mockito.mock(studio.environment.server.session.HostedSessions.class));
        var provider=sessions.getBeanProvider(studio.environment.server.session.HostedSessions.class);
        var hosted=studio.environment.server.security.RuntimeConfiguration.RuntimeMode.HOSTED;
        var demo=studio.environment.server.security.RuntimeConfiguration.RuntimeMode.DEMO;
        org.mockito.Mockito.when(workspace.enabled()).thenReturn(true);
        var missing=new PlanRuntime(new MockEnvironment(),hosted,workspace,provider);
        assertFalse(missing.inspectionApiConfigured());
        assertThrows(PlanRuntime.Unavailable.class,missing::service);
        assertFalse(new PlanRuntime(configured(),demo,workspace,provider).inspectionApiConfigured());
        org.mockito.Mockito.when(workspace.enabled()).thenReturn(false);
        assertFalse(new PlanRuntime(configured(),hosted,workspace,provider).inspectionApiConfigured());
        org.mockito.Mockito.when(workspace.enabled()).thenReturn(true);
        studio.environment.server.workspace.SqliteDraftStore.initializeV3(workspaceDirectory);
        var environment=configured().withProperty("studio.workspace.directory",workspaceDirectory.toString());
        var actualWorkspace=new studio.environment.server.workspace.WorkspaceRuntime(environment,hosted);
        var composed=new PlanRuntime(environment,hosted,actualWorkspace,provider);
        assertTrue(composed.inspectionApiConfigured());
        assertNotNull(composed.service());
        assertTrue(composed.visible(new Owner("https://mock-issuer.invalid","foreign-owner")).isEmpty());
    }
    @Test void refusesUnknownMalformedAndNoncontiguousConfigurationWithoutEcho() {
        for(var env:new MockEnvironment[]{configured().withProperty("studio.plans.destinations[0].password","SyntheticNeverEcho"),configured().withProperty("studio.plans.destinations[0].engine","other"),configured().withProperty("studio.plans.destinations[2].id","gap"),configured().withProperty("studio.plans.destinations[0].owners[1].issuer","https://mock-issuer.invalid").withProperty("studio.plans.destinations[0].owners[1].subject","MockOwner")}) {
            var error=assertThrows(IllegalStateException.class,()->new PlanDestinations(env));
            assertEquals("INVALID_PLAN_DESTINATIONS",error.getMessage()); assertNull(error.getCause());
        }
    }
    @Test void trustBoundaryRejectsSymlinksOversizeMixedMaterialAndFormatMismatch(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path fixture=Path.of("../../fixtures/plan-http-tls/mock-ca.pem").toAbsolutePath().normalize();
        Path link=directory.resolve("link.pem"); Files.createSymbolicLink(link,fixture);
        Path oversized=directory.resolve("oversized.pem"); Files.write(oversized,new byte[1_048_577]);
        Path mixed=directory.resolve("mixed.pem"); Files.writeString(mixed,Files.readString(fixture)+"UNQUALIFIED_TRAILING_MATERIAL");
        Path empty=directory.resolve("empty.pem"); Files.writeString(empty,"");
        for(Path path:new Path[]{link,oversized,mixed,empty,directory.resolve("sub/../missing.pem")}) {
            assertSafeRefusal(configured().withProperty("studio.plans.destinations[0].trust-material",path.toString()));
        }
        java.security.cert.Certificate certificate;
        try(var input=Files.newInputStream(fixture)) { certificate=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input); }
        var store=java.security.KeyStore.getInstance("JKS"); store.load(null,null); store.setCertificateEntry("independent-mock",certificate);
        Path jks=directory.resolve("mock.jks");
        try(var output=Files.newOutputStream(jks)) { store.store(output,new char[0]); }
        assertEquals(1,new PlanDestinations(oracle(jks)).configured().size());
        Path wrong=directory.resolve("mock.p12"); Files.copy(jks,wrong);
        assertSafeRefusal(oracle(wrong));
        Path suffix=directory.resolve("mock.JKS"); Files.copy(jks,suffix); assertSafeRefusal(oracle(suffix));
        var emptyStore=java.security.KeyStore.getInstance("JKS"); emptyStore.load(null,null);
        Path emptyJks=directory.resolve("empty.jks"); try(var output=Files.newOutputStream(emptyJks)) { emptyStore.store(output,new char[0]); }
        assertSafeRefusal(oracle(emptyJks));
    }
    @Test void exactIdentityAndOwnerPolicyRejectUnqualifiedValues() {
        String prefix="studio.plans.destinations[0].";
        for(String[] mutation:new String[][]{{"expected-physical-identity.databaseOid","01"},{"expected-physical-identity.extra","1"},{"transport-identity","A".repeat(64)},{"operation-policy-version","unknown"},{"owners[0].issuer","http://mock-issuer.invalid"},{"owners[0].subject","control\nvalue"},{"host","host.invalid/path"}})
            assertSafeRefusal(configured().withProperty(prefix+mutation[0],mutation[1]));
    }
    @Test void retiredAccountPolicyCannotSilentlyCertifyOperationPolicy() {
        assertSafeRefusal(configured().withProperty("studio.plans.destinations[0].account-policy-version", "postgresql-read-only-v1"));
        assertSafeRefusal(configured().withProperty("studio.plans.destinations[0].operation-policy-version", "postgresql-read-only-v1"));
    }
    private MockEnvironment oracle(Path path) {
        var env=configured(); String p="studio.plans.destinations[0].";
        var source=(org.springframework.core.env.MapPropertySource)env.getPropertySources().get("mockProperties");
        source.getSource().keySet().removeIf(key->key.startsWith(p+"expected-physical-identity."));
        return env.withProperty(p+"engine","oracle").withProperty(p+"trust-material",path.toString())
            .withProperty(p+"operation-policy-version","oracle-read-operation-v1")
            .withProperty(p+"expected-physical-identity.dbid","1").withProperty(p+"expected-physical-identity.conId","2")
            .withProperty(p+"expected-physical-identity.conUid","3").withProperty(p+"expected-physical-identity.dbUniqueName","MOCK")
            .withProperty(p+"expected-physical-identity.conName","MOCKPDB").withProperty(p+"expected-physical-identity.pdbGuid","b".repeat(32));
    }
    private static void assertSafeRefusal(MockEnvironment environment) {
        var failure=assertThrows(IllegalStateException.class,()->new PlanDestinations(environment));
        assertEquals("INVALID_PLAN_DESTINATIONS",failure.getMessage()); assertNull(failure.getCause());
    }
}
