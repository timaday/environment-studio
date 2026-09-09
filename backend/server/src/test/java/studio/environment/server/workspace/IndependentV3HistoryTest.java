package studio.environment.server.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.environment.core.definitionv3.NativeCompilationResult;
import studio.environment.core.workspace.*;
import studio.environment.server.definition.*;

class IndependentV3HistoryTest {
    @Test void bindingIdsThatResembleNumericPropertyNamesRemainLegalHistoricalKeys() throws Exception {
        String original = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        for (String id : new String[]{"minimum", "maximum", "revision"}) {
            var sourceTree = V3NativeSnapshotCodec.JSON.readTree(original);
            ((tools.jackson.databind.node.ObjectNode) sourceTree.get("bindings").get(0)).put("id", id);
            String source = V3NativeSnapshotCodec.JSON.writeValueAsString(sourceTree);
            var compiled = assertInstanceOf(NativeCompilationResult.Incomplete.class,
                    new NativeV3DefinitionBytesCompiler().compile(source.getBytes(StandardCharsets.UTF_8),
                            DefinitionBytesCompiler.Format.JSON));
            assertTrue(compiled.checked().bindingDigests().containsKey(id));
            var revision = new V3NativeRevision("11111111-1111-4111-8111-111111111111", "1",
                    DraftCommand.Format.JSON, source, V3NativeWorkspaceDigests.source(source),
                    "native-compiler-v3", "3", new V3NativeRevision.Definition(compiled.checked(), compiled.diagnostics()),
                    Optional.empty());
            var codec = new V3NativeSnapshotCodec();
            assertEquals(revision, assertDoesNotThrow(() -> codec.decode(codec.encode(revision)), id));
        }
    }
}
