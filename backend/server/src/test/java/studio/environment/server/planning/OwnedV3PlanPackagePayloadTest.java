package studio.environment.server.planning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import studio.environment.core.plan.*;
import studio.environment.core.workspace.NativeCommand;
import studio.environment.server.export.V3PlanPackagePayload;

/** Actual shared owner and XML proof path; publication/observation remain test witnesses. */
class OwnedV3PlanPackagePayloadTest {
    private static SharedV3PlanXmlTest fixture() {
        var f = new SharedV3PlanXmlTest();
        f.definitionLookup = value -> new PlanPorts.PublishedDefinition(value.reference(), value.publicationDigest(), value.model(),
                List.of(new NativeCommand.Policy("mock-pg", "sheet", "protected-self-contained")));
        return f;
    }
    @Test void preparesExactPayloadUnderOriginalAdmissionWithoutClaimingExportAuthority() {
        var f = fixture(); var service = f.service(); String plan = f.inspected(service);
        assertTrue(service.materialize(f.lease, plan, "2").complete());
        var validation = service.validateV3(f.lease, plan, "2");
        assertFalse(validation.exportAvailable());
        try (var admission = service.reserveView(f.lease, plan)) {
            admission.run(() -> {
                admission.pin("2");
                var expected = admission.read((snapshot, control) -> new V3PlanPackagePayload().prepare(snapshot, control));
                var actual = assertInstanceOf(V3PlanPackagePayload.Result.Candidate.class,
                        new V3PlanPackagePayload().prepare(admission, validation.inputFingerprint()));
                assertArrayEquals(assertInstanceOf(V3PlanPackagePayload.Result.Candidate.class, expected).bytes(), actual.bytes());
                assertFalse(actual.qualified()); admission.verify(); return true;
            });
        }
        // Owner scratch is released by the caller, allowing a subsequent operation.
        assertEquals(validation.inputFingerprint(), service.validateV3(f.lease, plan, "2").inputFingerprint());
    }
    @Test void refusesDifferentFingerprintAndAbortedAdmission() {
        var f = fixture(); var service = f.service(); String plan = f.inspected(service);
        service.materialize(f.lease, plan, "2");
        var fingerprint = service.validateV3(f.lease, plan, "2").inputFingerprint();
        try (var admission = service.reserveView(f.lease, plan)) {
            admission.run(() -> {
                admission.pin("2");
                assertEquals(new V3PlanPackagePayload.Result.Rejected("CONFLICT"),
                        new V3PlanPackagePayload().prepare(admission, "0".repeat(64)));
                admission.abortReview();
                assertInstanceOf(V3PlanPackagePayload.Result.Rejected.class, new V3PlanPackagePayload().prepare(admission, fingerprint));
                return true;
            });
        }
    }
    @Test void refusesPublicationReplacementOrLeaseClosureDuringFreshLookup() {
        for (boolean close : List.of(false, true)) {
            var f = fixture(); var service = f.service(); String plan = f.inspected(service);
            service.materialize(f.lease, plan, "2");
            var fingerprint = service.validateV3(f.lease, plan, "2").inputFingerprint();
            var original = f.definitionLookup;
            f.definitionLookup = value -> {
                var selected = original.apply(value);
                if (close) { f.ledger.close(f.lease.id()); return selected; }
                return new PlanPorts.PublishedDefinition(selected.reference(), "changed-publication", selected.model(), selected.policies());
            };
            try (var admission = service.reserveView(f.lease, plan)) {
                admission.run(() -> {
                    admission.pin("2");
                    assertEquals(new V3PlanPackagePayload.Result.Rejected(close ? "SESSION_REQUIRED" : "UNSUPPORTED_DEFINITION"),
                            new V3PlanPackagePayload().prepare(admission, fingerprint));
                    return true;
                });
            }
        }
    }
}
