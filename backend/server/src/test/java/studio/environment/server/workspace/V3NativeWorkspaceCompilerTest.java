package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.workspace.*;
import studio.environment.server.definition.*;

class V3NativeWorkspaceCompilerTest {
    static NativeCommand.SaveDefinition command(String source, DraftCommand.Format format) {
        return new NativeCommand.SaveDefinition("11111111-1111-4111-8111-111111111111", "0",
                "22222222-2222-4222-8222-222222222222", format, source);
    }
    @Test void actualV3CompilerPreservesIncompleteCheckedModelAndAllDiagnostics() throws Exception {
        String source = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        var expected = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeV3DefinitionBytesCompiler().compile(source.getBytes(StandardCharsets.UTF_8), DefinitionBytesCompiler.Format.JSON));
        for (var format : DraftCommand.Format.values()) {
            var result = assertDoesNotThrow(() -> new V3NativeWorkspaceCompiler().definition(command(source, format)));
            assertEquals(expected.checked(), result.checked()); assertEquals(expected.diagnostics(), result.diagnostics());
            assertFalse(result.historicalReady());
        }
    }

    private static final class Store implements V3NativeStore {
        int appends;
        public java.util.Optional<V3NativeRevision> replay(studio.environment.core.session.Owner owner, NativeCommand command) {
            return java.util.Optional.empty();
        }
        public V3NativeRevision append(studio.environment.core.session.Owner owner, NativeCommand command, V3NativeRevision revision) {
            appends++; return revision;
        }
        public V3NativeRevision read(studio.environment.core.session.Owner owner, String id, java.util.Optional<String> revision, boolean profile) {
            throw new AssertionError("Unexpected read.");
        }
        public java.util.List<V3NativeRevision> list(studio.environment.core.session.Owner owner, boolean profile) {
            throw new AssertionError("Unexpected list.");
        }
    }
    static final studio.environment.core.session.Owner OWNER = new studio.environment.core.session.Owner("https://mock.invalid", "owner");
    @Test void actualDraftServiceRetainsExactUnicodeSourceAndRemainsHistoricalIncomplete() throws Exception {
        String source = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        var tree = V3NativeSnapshotCodec.JSON.readTree(source);
        ((tools.jackson.databind.node.ObjectNode) tree.at("/logical/entityTypes/0")).put("label", "Neutral é 😀");
        source = V3NativeSnapshotCodec.JSON.writeValueAsString(tree) + "\r\n";
        var store = new Store(); var service = new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler());
        var revision = service.saveDefinition(OWNER, command(source, DraftCommand.Format.JSON));
        assertEquals(source, revision.source()); assertEquals("1", revision.workspaceRevision());
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8))), revision.sourceDigest());
        assertTrue(revision.publication().isEmpty());
        assertFalse(((V3NativeRevision.Definition) revision.content()).historicalReady());
        var codec = new V3NativeSnapshotCodec(); assertEquals(revision, codec.decode(codec.encode(revision)));
        assertEquals(1, store.appends);
    }
    @Test void actualRejectedSourcesNeverAppendAndRetainOrdinaryDiagnostics() {
        for (String source : java.util.List.of("{}", "not JSON", "{\"schemaVersion\":\"2\"}")) {
            var rejected = assertInstanceOf(NativeCompilationResult.Rejected.class,
                    new NativeV3DefinitionBytesCompiler().compile(source.getBytes(StandardCharsets.UTF_8), DefinitionBytesCompiler.Format.JSON));
            var store = new Store(); var service = new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler());
            assertEquals(rejected.diagnostics(), assertThrows(WorkspaceRejection.class,
                    () -> service.saveDefinition(OWNER, command(source, DraftCommand.Format.JSON))).diagnostics());
            assertEquals(0, store.appends);
        }
    }
    @Test void excessiveRejectedDiagnosticsRefuseWithoutTruncationOrAppend() throws Exception {
        var tree = V3NativeSnapshotCodec.JSON.readTree(Files.readString(Path.of("../../fixtures/native-v3/definition.json")));
        var rules = ((tools.jackson.databind.node.ObjectNode) tree.get("logical")).putArray("rules");
        for (int i = 0; i < 300; i++) rules.addObject().put("id", "missing-" + i).put("kind", "entity-count")
                .put("type", "missing-type").put("minimum", 0).put("maximum", 1);
        String source = V3NativeSnapshotCodec.JSON.writeValueAsString(tree);
        var rejected = assertInstanceOf(NativeCompilationResult.Rejected.class,
                new NativeV3DefinitionBytesCompiler().compile(source.getBytes(StandardCharsets.UTF_8), DefinitionBytesCompiler.Format.JSON));
        assertTrue(rejected.diagnostics().size() > 256);
        var store = new Store(); var service = new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler());
        assertEquals(WorkspaceRefusal.Code.TOO_LARGE, assertThrows(WorkspaceRefusal.class,
                () -> service.saveDefinition(OWNER, command(source, DraftCommand.Format.JSON))).code());
        assertEquals(0, store.appends);
    }
    @Test void largeIncompleteDiagnosticHistoryIsPreservedRatherThanResponseTruncated() throws Exception {
        var tree = V3NativeSnapshotCodec.JSON.readTree(Files.readString(Path.of("../../fixtures/native-v3/definition.json")));
        for (var entity : tree.at("/logical/entityTypes")) {
            var fields = (tools.jackson.databind.node.ArrayNode) entity.get("fields");
            for (int i = 0; i < 150; i++) fields.addObject().put("id", "unmapped-" + i).put("valueType", "text")
                    .put("required", false).put("classification", "environment").put("sensitivity", "public")
                    .put("readable", false).put("editable", false);
        }
        String source = V3NativeSnapshotCodec.JSON.writeValueAsString(tree);
        var incomplete = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeV3DefinitionBytesCompiler().compile(source.getBytes(StandardCharsets.UTF_8), DefinitionBytesCompiler.Format.JSON));
        assertTrue(incomplete.diagnostics().size() > 256);
        var store = new Store(); var service = new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler());
        var revision = service.saveDefinition(OWNER, command(source, DraftCommand.Format.JSON));
        assertEquals(incomplete.diagnostics(), ((V3NativeRevision.Definition) revision.content()).diagnostics());
        assertEquals(revision, new V3NativeSnapshotCodec().decode(new V3NativeSnapshotCodec().encode(revision)));
        assertTrue(revision.publication().isEmpty()); assertEquals(1, store.appends);
    }
    @Test void malformedScalarAndSourceByteBudgetRemainRefusalsWithoutAppend() {
        var store = new Store(); var service = new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler());
        assertEquals(WorkspaceRefusal.Code.INVALID_REQUEST, assertThrows(WorkspaceRefusal.class,
                () -> service.saveDefinition(OWNER, command("bad" + (char) 0xd800, DraftCommand.Format.JSON))).code());
        assertEquals("RESOURCE_LIMIT", assertThrows(WorkspaceRejection.class,
                () -> service.saveDefinition(OWNER, command("x".repeat(1_048_577), DraftCommand.Format.JSON)))
                .diagnostics().getFirst().code());
        assertEquals(0, store.appends);
    }
}
