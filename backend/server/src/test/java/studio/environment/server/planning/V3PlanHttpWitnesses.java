package studio.environment.server.planning;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.observation.ObservationResult;

/** Explicit invented HTTP qualification witness; never a runtime publication authority. */
public final class V3PlanHttpWitnesses {
    private V3PlanHttpWitnesses() {}
    public static NativeCompilationResult.Checked definition() {
        return DerivedTargetInputAdapterTest.definition(false);
    }
    public static ObservationResult observation() {
        var checked = definition();
        String xml = DerivedTargetInputAdapterTest.XML;
        var identity = Map.of("systemIdentifier", "731", "databaseOid", "19", "databaseName", "invented_db");
        Map<String, Object> evidence = Map.of("engine", "postgresql", "cleanup", "complete",
                "destination", Map.of("id", "mock-destination", "host", "invented.invalid", "port", 5432,
                        "database", "invented_db", "transportIdentity", "c".repeat(64),
                        "provisioningPolicyVersion", "mock-v1", "observedPhysicalIdentity", identity,
                        "expectedPhysicalIdentity", identity),
                "metadata", Map.of("adapterVersion", "jdbc-observation-v3", "operationPolicyVersion", "postgresql-read-operation-v1",
                        "visibility", "complete", "readOnlyOperation", "verified", "snapshot", "repeatable-read-read-only"));
        return new ObservationResult.Complete(new ObservationResult.Observation("c".repeat(64),
                checked.logicalDigest(), checked.bindingDigests().get("mock-pg"),
                List.of(new ObservationResult.Document("sheet", new ObservationResult.Key("int64", "1"), xml,
                        xml.getBytes(StandardCharsets.UTF_8).length, xml.length(), DerivedTargetInputAdapterTest.digest(xml))), evidence));
    }
}
