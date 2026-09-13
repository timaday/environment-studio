package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.export.*;

class V3GuardedPackageCandidateTest {
    private static SharedV3PlanXmlTest fixture() {
        var f = new SharedV3PlanXmlTest();
        f.definitionLookup = value -> new PlanPorts.PublishedDefinition(value.reference(), "d".repeat(64), value.model(),
                List.of(new NativeCommand.Policy("mock-pg", "sheet", "protected-self-contained")));
        return f;
    }
    @Test void preparesPinnedPostgres16PackageInputAndRefusesDestinationMismatch() throws Exception {
        var f = fixture(); var service = f.service(); String plan = f.inspected(service);
        assertTrue(service.materialize(f.lease, plan, "2").complete());
        var validation = service.validateV3(f.lease, plan, "2");
        assertFalse(validation.exportAvailable());
        try (var admission = service.reserveView(f.lease, plan)) {
            admission.run(() -> {
                admission.pin("2");
                var prepared = assertInstanceOf(V3GuardedPackageCandidate.Result.Prepared.class,
                        new V3GuardedPackageCandidate().prepare(admission, target("destination", identity()), validation.inputFingerprint()));
                var out = new ByteArrayOutputStream();
                var written = assertInstanceOf(GuardedPackageAssembler.Result.Candidate.class,
                        new V3GuardedPackageCandidate().write(prepared, out, new studio.environment.core.observation.ObservationPort.Cancellation()));
                assertEquals(out.size(), written.bytes());
                assertFalse(written.qualified());
                var archive = out.toByteArray(); assertEquals('P', archive[0]); assertEquals('K', archive[1]);
                assertTrue(new String(archive, StandardCharsets.ISO_8859_1).contains("postgresql16-text-v1"));
                assertEquals(new V3GuardedPackageCandidate.Result.Rejected("CONFLICT"),
                        new V3GuardedPackageCandidate().prepare(admission, target("other-destination", identity()), validation.inputFingerprint()));
                return true;
            });
        }
    }
    @Test void refusesUnconfiguredClientTupleBeforeAdmittingPackage() {
        var f = fixture(); var service = f.service(); String plan = f.inspected(service);
        assertTrue(service.materialize(f.lease, plan, "2").complete());
        var validation = service.validateV3(f.lease, plan, "2");
        try (var admission = service.reserveView(f.lease, plan)) {
            admission.run(() -> {
                admission.pin("2");
                assertEquals(new V3GuardedPackageCandidate.Result.Rejected("EXPORT_UNAVAILABLE"),
                        new V3GuardedPackageCandidate().prepare(admission, new V3GuardedPackageCandidate.Target("destination", "postgresql", "invented.invalid", 5432,
                                "invented_db", "verified-tls", "c".repeat(64), "mock-v1", identity(),
                                "16.10", "psql", "16.10", "linux-amd64", "postgresql16-text-v1"), validation.inputFingerprint()));
                return true;
            });
        }
    }
    private static Map<String, String> identity() {
        return Map.of("systemIdentifier", "731", "databaseOid", "19", "databaseName", "invented_db");
    }
    private static V3GuardedPackageCandidate.Target target(String id, Map<String,String> identity) {
        return new V3GuardedPackageCandidate.Target(id, "postgresql", "invented.invalid", 5432, "invented_db", "verified-tls", "c".repeat(64),
                "mock-v1", identity, "16.11", "psql", "16.11", "linux-amd64", "postgresql16-text-v1");
    }
}
