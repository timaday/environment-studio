package studio.environment.server.export;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class Postgres16TemplateTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void exact16TupleGeneratesItsOwnCatalogGuardsWithoutChanging18() throws Exception {
        var existing = TransactionTemplatesTest.input();
        var execution = (ObjectNode) json.readTree(existing.canonicalExecution());
        execution.put("serverVersion", "16.11").put("templateVersion", "postgresql16-text-v1");
        ((ObjectNode) execution.get("client")).put("version", "16.11");
        var admitted = assertInstanceOf(PackageAdmission.Result.Accepted.class,
                new PackageAdmission().read(json.writeValueAsBytes(execution), existing.canonicalPayload()));
        var candidate = assertInstanceOf(TransactionTemplates.Result.Candidate.class, new TransactionTemplates().generate(admitted));
        String sql = new String(candidate.bytes(), StandardCharsets.US_ASCII);
        assertTrue(sql.contains("current_setting('server_version_num') <> '160011'"));
        assertTrue(sql.contains("c.contype NOT IN ('p','u') OR c.condeferrable OR NOT c.convalidated"));
        assertFalse(sql.contains("conenforced"));
        assertFalse(sql.contains("180006"));
        assertFalse(candidate.qualified());
        var original = assertInstanceOf(TransactionTemplates.Result.Candidate.class, new TransactionTemplates().generate(existing));
        String oldSql = new String(original.bytes(), StandardCharsets.US_ASCII);
        assertTrue(oldSql.contains("180006"));
        assertTrue(oldSql.contains("NOT c.conenforced"));
        assertNotEquals(existing.programDigest(), admitted.programDigest());
    }

    @Test void mismatchedAndUnassignedVersionTuplesRefuse() throws Exception {
        var input = TransactionTemplatesTest.input();
        for (String[] tuple : java.util.List.of(
                new String[]{"16.11", "18.6", "postgresql16-text-v1"},
                new String[]{"18.6", "16.11", "postgresql-text-v1"},
                new String[]{"16.11", "16.11", "postgresql-text-v1"},
                new String[]{"18.6", "18.6", "postgresql16-text-v1"},
                new String[]{"16.10", "16.10", "postgresql16-text-v1"})) {
            var execution = (ObjectNode) json.readTree(input.canonicalExecution());
            execution.put("serverVersion", tuple[0]).put("templateVersion", tuple[2]);
            ((ObjectNode) execution.get("client")).put("version", tuple[1]);
            assertEquals("SCHEMA_VIOLATION", assertInstanceOf(PackageAdmission.Result.Rejected.class,
                    new PackageAdmission().read(json.writeValueAsBytes(execution), input.canonicalPayload())).code());
        }
    }
}
