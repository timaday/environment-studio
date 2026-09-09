package studio.environment.server.workspace;

import java.nio.file.*;
import java.util.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Invented historical records only; never current publication admission. */
public final class V3ProfileHttpFixtures {
    public static V3NativeRevision definition(Path directory, Owner owner, boolean unsupported) throws Exception {
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        String source = Files.readString(Path.of("../../fixtures/native-v3/definition.json"));
        var command = new NativeCommand.SaveDefinition(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON, source);
        var checked = new V3NativeWorkspaceCompiler().definition(command).checked();
        if (unsupported) {
            var versions = new TreeMap<>(checked.mechanisms());
            versions.replaceAll((name, version) -> java.math.BigInteger.valueOf(9));
            checked = new studio.environment.core.definitionv3.NativeCompilationResult.Checked(checked.definition(), checked.logicalDigest(), checked.bindingDigests(), versions);
        }
        var content = new V3NativeRevision.Definition(checked, List.of());
        var one = new V3NativeRevision(command.objectId(), "1", command.format(), source, V3NativeWorkspaceDigests.source(source), "native-compiler-v3", "3", content, Optional.empty());
        store.append(owner, command, one);
        var policies = checked.definition().bindings().stream().flatMap(binding -> binding.documents().stream()
                .map(document -> new NativeCommand.Policy(binding.id(), document.id(), "deny"))).toList();
        var two = new V3NativeRevision(one.objectId(), "2", one.format(), source, one.sourceDigest(), one.compilerVersion(), "3", content, Optional.empty());
        var publication = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(two, "1", policies), "1", policies);
        return store.append(owner, new NativeCommand.PublishDefinition(one.objectId(), "1", UUID.randomUUID().toString(), policies),
                new V3NativeRevision(two.objectId(), "2", two.format(), source, two.sourceDigest(), two.compilerVersion(), "3", content, Optional.of(publication)));
    }
    public static String source() throws Exception { return Files.readString(Path.of("../../fixtures/native-v3/profile.json")); }
    public static byte[] body(String source, String format, String expected, String request, NativeCommand.Reference reference) {
        return V3NativeSnapshotCodec.JSON.writeValueAsBytes(Map.of("source", source, "format", format,
                "expectedRevision", expected, "requestId", request, "definition", reference));
    }
    public static V3NativeRevision save(Path directory, Owner owner, V3NativeRevision definition, String source) {
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        return new V3ProfileWorkspace(store, new V3ProfileWorkspaceCompiler()).saveProfile(owner,
                new NativeCommand.SaveProfile(UUID.randomUUID().toString(), "0", UUID.randomUUID().toString(), DraftCommand.Format.JSON,
                        source, new NativeCommand.Reference(definition.objectId(), definition.workspaceRevision())));
    }
    public static void laterDefinition(Path directory, Owner owner, V3NativeRevision definition) {
        var store = new V3NativeSqliteStore(new SqliteDraftStore(directory));
        new V3NativeWorkspace(store, new V3NativeWorkspaceCompiler()).saveDefinition(owner,
                new NativeCommand.SaveDefinition(definition.objectId(), definition.workspaceRevision(), UUID.randomUUID().toString(), definition.format(), definition.source()));
    }
}
