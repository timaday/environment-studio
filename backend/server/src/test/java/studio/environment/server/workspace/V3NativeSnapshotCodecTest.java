package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.workspace.*;
import studio.environment.server.definition.*;

class V3NativeSnapshotCodecTest {
    private final V3NativeSnapshotCodec codec = new V3NativeSnapshotCodec();
    static V3NativeRevision draft() throws Exception {
        String source = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        var compiled = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                new NativeV3DefinitionBytesCompiler().compile(source.getBytes(java.nio.charset.StandardCharsets.UTF_8), DefinitionBytesCompiler.Format.JSON));
        return new V3NativeRevision("11111111-1111-4111-8111-111111111111", "1", DraftCommand.Format.JSON,
                source, V3NativeWorkspaceDigests.source(source), "native-compiler-v3", "3",
                new V3NativeRevision.Definition(compiled.checked(), compiled.diagnostics()), Optional.empty());
    }
    @Test void incompleteHistoryRoundTripsExactlyWithoutGrantingReadiness() throws Exception {
        var revision = draft();
        var bytes = codec.encode(revision);
        assertTrue(bytes.length > 0);
        var decoded = codec.decode(bytes);
        assertEquals(revision, decoded);
        assertArrayEquals(bytes, codec.encode(decoded));
        assertFalse(((V3NativeRevision.Definition) decoded.content()).historicalReady());
        assertTrue(decoded.publication().isEmpty());
    }

    static V3NativeRevision changed(V3NativeRevision original, String source, V3NativeRevision.Content content,
            String revision, Optional<V3NativeRevision.Publication> publication) {
        return new V3NativeRevision(original.objectId(), revision, original.format(), source,
                V3NativeWorkspaceDigests.source(source), original.compilerVersion(), original.schemaVersion(), content, publication);
    }
    static V3NativeRevision.Definition historical(V3NativeRevision revision) {
        var content = (V3NativeRevision.Definition) revision.content();
        var versions = new TreeMap<>(content.checked().mechanisms());
        versions.replaceAll((name, value) -> new java.math.BigInteger("9".repeat(1024)));
        var checked = new NativeCompilationResult.Checked(content.checked().definition(), "a".repeat(64),
                content.checked().bindingDigests(), versions);
        return new V3NativeRevision.Definition(checked, content.diagnostics());
    }
    @Test void historicalValuesAndOriginalDiagnosticOrderNeverRecompileOrBecomeCurrent() throws Exception {
        var original = draft();
        var historical = historical(original);
        var diagnostics = List.of(
                new studio.environment.core.definition.DefinitionDiagnostic(studio.environment.core.definition.DefinitionDiagnostic.Phase.PUBLICATION, "RETIRED_Z", "/z", "Historical blocker."),
                new studio.environment.core.definition.DefinitionDiagnostic(studio.environment.core.definition.DefinitionDiagnostic.Phase.PUBLICATION, "RETIRED_A", "/a", "Historical blocker."));
        var content = new V3NativeRevision.Definition(historical.checked(), diagnostics);
        var revision = changed(original, "not current compilable JSON", content, "1", Optional.empty());
        var decoded = codec.decode(codec.encode(revision));
        assertEquals(revision, decoded);
        assertEquals(diagnostics, ((V3NativeRevision.Definition) decoded.content()).diagnostics());
        assertEquals("a".repeat(64), ((V3NativeRevision.Definition) decoded.content()).checked().logicalDigest());
        assertFalse(decoded.content().toString().contains("Historical blocker"));
    }
    @Test void diagnosticHistoryIsBoundedBySnapshotNotAnInventedCountAndPreservesDuplicates() throws Exception {
        var original = draft();
        var one = ((V3NativeRevision.Definition) original.content()).diagnostics().getFirst();
        var diagnostics = java.util.Collections.nCopies(257, one);
        var content = new V3NativeRevision.Definition(((V3NativeRevision.Definition) original.content()).checked(), diagnostics);
        var revision = changed(original, original.source(), content, "1", Optional.empty());
        assertEquals(diagnostics, ((V3NativeRevision.Definition) assertDoesNotThrow(() -> codec.decode(codec.encode(revision))).content()).diagnostics());
    }
    @Test void historicalPublicationRequiresExactPolicyCoverageAndPrecedingSourceRevision() throws Exception {
        var original = draft();
        var content = new V3NativeRevision.Definition(historical(original).checked(), List.of());
        var revision = changed(original, original.source(), content, "2", Optional.empty());
        var policies = content.checked().definition().bindings().stream().flatMap(binding -> binding.documents().stream()
                .map(document -> new NativeCommand.Policy(binding.id(), document.id(), "deny"))).toList();
        var publication = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(revision, "1", policies), "1", policies);
        var published = changed(original, original.source(), content, "2", Optional.of(publication));
        assertEquals(published, codec.decode(codec.encode(published)));
        for (var wrong : List.of(List.<NativeCommand.Policy>of(), List.of(policies.getFirst(), policies.getFirst()))) {
            var record = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(revision, "1", wrong), "1", wrong);
            unavailable(() -> codec.encode(changed(original, original.source(), content, "2", Optional.of(record))));
        }
        for (String wrong : List.of("0", "2", "3", "01")) {
            var record = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(revision, wrong, policies), wrong, policies);
            unavailable(() -> codec.encode(changed(original, original.source(), content, "2", Optional.of(record))));
        }
        var incomplete = new V3NativeRevision.Definition(content.checked(), ((V3NativeRevision.Definition) original.content()).diagnostics());
        unavailable(() -> codec.encode(changed(original, original.source(), incomplete, "2", Optional.of(publication))));
        var wrongDigest = new V3NativeRevision.Publication("0".repeat(64), "1", policies);
        unavailable(() -> codec.encode(changed(original, original.source(), content, "2", Optional.of(wrongDigest))));
    }
    @Test void portableProfileHistoryPreservesVersionReferenceAndDigestWithoutRevalidation() throws Exception {
        var definition = ((V3NativeRevision.Definition) draft().content()).checked();
        var accepted = assertInstanceOf(studio.environment.server.profile.V3ProfileBytesAdapter.Result.Accepted.class,
                new studio.environment.server.profile.V3ProfileBytesAdapter().read(definition,
                        Files.readAllBytes(Path.of("../../fixtures/native-v3/profile.json")), BoundedDocumentParser.Format.JSON));
        var profile = new studio.environment.core.profile.ProfileResult.Checked(accepted.checked().profile(), "f".repeat(64));
        var content = new V3NativeRevision.Profile(profile,
                new NativeCommand.Reference("33333333-3333-4333-8333-333333333333", "19"));
        var draft = new V3NativeRevision("11111111-1111-4111-8111-111111111111", "2", DraftCommand.Format.YAML,
                "historical source", V3NativeWorkspaceDigests.source("historical source"), "profile-compiler-v3", "3", content, Optional.empty());
        var publication = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(draft, "1", List.of()), "1", List.of());
        var published = changed(draft, draft.source(), content, "2", Optional.of(publication));
        assertEquals(published, codec.decode(codec.encode(published)));
        var bad = new V3NativeRevision.Publication(publication.digest(), "1", List.of(new NativeCommand.Policy("mock-pg", "glyph-sheet", "deny")));
        unavailable(() -> codec.encode(changed(draft, draft.source(), content, "2", Optional.of(bad))));
    }
    @Test void closedShapeVersionDigestsDependenciesAndDiagnosticsRefuseTampering() throws Exception {
        var bytes = codec.encode(draft());
        for (String pointer : List.of("/kind", "/compilerVersion", "/schemaVersion", "/sourceDigest", "/objectId", "/workspaceRevision",
                "/content/checked/logicalDigest", "/content/checked/bindingDigests/mock-pg", "/content/diagnostics/0/phase", "/content/diagnostics/0/code")) {
            var tree = V3NativeSnapshotCodec.JSON.readTree(bytes);
            int slash = pointer.lastIndexOf('/');
            ((tools.jackson.databind.node.ObjectNode) tree.at(pointer.substring(0, slash))).put(pointer.substring(slash + 1), "tampered");
            unavailable(() -> codec.decode(V3NativeSnapshotCodec.JSON.writeValueAsBytes(tree)));
        }
        for (String pointer : List.of("", "/content", "/content/checked", "/content/checked/definition/logical")) {
            var tree = V3NativeSnapshotCodec.JSON.readTree(bytes);
            ((tools.jackson.databind.node.ObjectNode) tree.at(pointer)).put("unknown", true);
            unavailable(() -> codec.decode(V3NativeSnapshotCodec.JSON.writeValueAsBytes(tree)));
        }
        for (String value : List.of("0", "01", "-1", "9".repeat(1025))) {
            var tree = V3NativeSnapshotCodec.JSON.readTree(bytes);
            ((tools.jackson.databind.node.ObjectNode) tree.at("/content/checked/mechanisms")).put("derived-graph-v1", value);
            unavailable(() -> codec.decode(V3NativeSnapshotCodec.JSON.writeValueAsBytes(tree)));
        }
        var tree = V3NativeSnapshotCodec.JSON.readTree(bytes);
        ((tools.jackson.databind.node.ObjectNode) tree.at("/content/checked/mechanisms")).remove("derived-graph-v1");
        unavailable(() -> codec.decode(V3NativeSnapshotCodec.JSON.writeValueAsBytes(tree)));
    }
    @Test void malformedDuplicateNoncanonicalAndOversizedBytesNeverDecode() throws Exception {
        byte[] bytes = codec.encode(draft());
        String wire = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        for (String invalid : List.of(wire + " ", wire + "{}", " " + wire,
                wire.replaceFirst("\\{", "{\"kind\":\"definition-v3\","),
                wire.replace("native-compiler-v3", "native-compiler-v2")))
            unavailable(() -> codec.decode(invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        unavailable(() -> codec.decode(new byte[]{(byte) 0xc0, (byte) 0xaf}));
        unavailable(() -> codec.decode(new byte[2_097_153]));
        unavailable(() -> codec.decode(null));
        var revision = draft();
        unavailable(() -> codec.encode(changed(revision, "x".repeat(1_048_577), revision.content(), "1", Optional.empty())));
        var tree = V3NativeSnapshotCodec.JSON.readTree(bytes);
        ((tools.jackson.databind.node.ObjectNode) tree.at("/content/diagnostics/0")).put("message", "bad" + (char)0xd800);
        unavailable(() -> codec.decode(V3NativeSnapshotCodec.JSON.writeValueAsBytes(tree)));
    }
    @Test void everyTypedDefinitionIntegerPathRejectsNoncanonicalAndOversizedStrings() throws Exception {
        byte[] bytes = codec.encode(draft());
        for (String pointer : List.of("/content/checked/definition/revision",
                "/content/checked/definition/logical/relations/0/minimum",
                "/content/checked/definition/logical/rules/0/maximum",
                "/content/checked/definition/logical/cooccurrences/0/maximum",
                "/content/checked/definition/logical/computedRules/0/minimum")) {
            for (String value : List.of("01", "9".repeat(1025))) {
                var tree = V3NativeSnapshotCodec.JSON.readTree(bytes);
                int slash = pointer.lastIndexOf('/');
                ((tools.jackson.databind.node.ObjectNode) tree.at(pointer.substring(0, slash))).put(pointer.substring(slash + 1), value);
                unavailable(() -> codec.decode(V3NativeSnapshotCodec.JSON.writeValueAsBytes(tree)));
            }
        }
    }
    @Test void originalV2SnapshotRemainsByteExactAndCannotEnterV3() throws Exception {
        byte[] old = Files.readAllBytes(Path.of("../../fixtures/native-v2/historical-direct-snapshot.json"));
        var oldCodec = new NativeSnapshotCodec();
        assertArrayEquals(old, oldCodec.encode(oldCodec.decode(old)));
        unavailable(() -> codec.decode(old));
        byte[] current = codec.encode(draft());
        unavailable(() -> oldCodec.decode(current));
    }
    private static void unavailable(org.junit.jupiter.api.function.Executable action) {
        assertEquals(WorkspaceRefusal.Code.UNAVAILABLE, assertThrows(WorkspaceRefusal.class, action).code());
    }
}
