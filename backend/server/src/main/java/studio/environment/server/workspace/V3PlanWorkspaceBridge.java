package studio.environment.server.workspace;

import java.util.*;
import studio.environment.core.plan.PlanRefusal;
import studio.environment.core.plan.V3PlanWorkspace;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;

/** Read-only v3 publication lookup with fresh compiler qualification on every request. */
public final class V3PlanWorkspaceBridge implements V3PlanWorkspace {
    private final java.util.function.Supplier<V3NativeStore> store;
    private final V3NativeWorkspace.Compiler definitions;
    private final V3ProfileWorkspace.Compiler profiles;
    public V3PlanWorkspaceBridge(WorkspaceRuntime runtime) {
        this.store=Objects.requireNonNull(runtime)::v3Store;
        this.definitions=new V3NativeWorkspaceCompiler();this.profiles=new V3ProfileWorkspaceCompiler();
    }
    public V3PlanWorkspaceBridge(V3NativeStore store) {
        this(store, new V3NativeWorkspaceCompiler(), new V3ProfileWorkspaceCompiler());
    }
    V3PlanWorkspaceBridge(V3NativeStore store, V3NativeWorkspace.Compiler definitions, V3ProfileWorkspace.Compiler profiles) {
        var fixed=Objects.requireNonNull(store);this.store=()->fixed;this.definitions = Objects.requireNonNull(definitions);
        this.profiles = Objects.requireNonNull(profiles);
    }
    @Override public Definition definition(Owner owner, NativeCommand.Reference reference) {
        requireInput(owner, reference);
        var revision = read(owner, reference, false);
        if (!(revision.content() instanceof V3NativeRevision.Definition historical)) throw unavailable();
        if (!historical.diagnostics().isEmpty()) throw unsupported();
        var publication = revision.publication().orElseThrow();
        var expectedPolicies = new HashSet<List<String>>();
        historical.checked().definition().bindings().forEach(binding -> binding.documents().forEach(document ->
                expectedPolicies.add(List.of(binding.id(), document.id()))));
        var suppliedPolicies = new HashSet<List<String>>();
        for (var policy : publication.exportPolicies()) if (!suppliedPolicies.add(List.of(policy.bindingId(), policy.documentId()))) throw unsupported();
        if (!suppliedPolicies.equals(expectedPolicies)) throw unsupported();
        V3NativeRevision.Definition current;
        try {
            current = definitions.definition(new NativeCommand.SaveDefinition(revision.objectId(), revision.workspaceRevision(),
                    UUID.randomUUID().toString(), revision.format(), revision.source()));
        } catch (WorkspaceRejection refused) { throw unsupported(); }
        if (current == null) throw unavailable();
        if (!current.diagnostics().isEmpty() || !current.checked().equals(historical.checked())) throw unsupported();
        return new Definition(reference, publication.digest(), current.checked(), publication.exportPolicies());
    }
    @Override public Profile profile(Owner owner, NativeCommand.Reference reference, Definition selectedDefinition) {
        requireInput(owner, reference);
        if (selectedDefinition == null) throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
        var selected = definition(owner, selectedDefinition.reference());
        if (!selected.equals(selectedDefinition)) throw unsupported();
        var revision = read(owner, reference, true);
        if (!(revision.content() instanceof V3NativeRevision.Profile historical)) throw unavailable();
        var original = historical.definition().equals(selected.reference()) ? selected : definition(owner, historical.definition());
        if (!original.checked().logicalDigest().equals(selected.checked().logicalDigest())) throw profileRefused();
        var command = new NativeCommand.SaveProfile(revision.objectId(), revision.workspaceRevision(), UUID.randomUUID().toString(),
                revision.format(), revision.source(), historical.definition());
        for (var definition : List.of(original, selected)) {
            V3NativeRevision.Profile current;
            try { current = profiles.profile(command, definition.checked()); }
            catch (WorkspaceRejection refused) { throw profileRefused(); }
            if (current == null) throw unavailable();
            if (!current.equals(historical)) throw profileRefused();
        }
        return new Profile(reference, revision.publication().orElseThrow().digest(), historical.checked());
    }
    private V3NativeRevision read(Owner owner, NativeCommand.Reference reference, boolean profile) {
        var revision = store.get().read(owner, reference.objectId(), Optional.of(reference.workspaceRevision()), profile);
        if (revision == null || !revision.objectId().equals(reference.objectId()) || !revision.workspaceRevision().equals(reference.workspaceRevision())
                || revision.profile() != profile) throw unavailable();
        if (revision.publication().isEmpty()) throw new PlanRefusal(PlanRefusal.Code.PUBLICATION_REQUIRED);
        if (!"3".equals(revision.schemaVersion()) || !(profile ? "profile-compiler-v3" : "native-compiler-v3").equals(revision.compilerVersion())) throw unsupported();
        return revision;
    }
    private static void requireInput(Owner owner, NativeCommand.Reference reference) {
        if (owner == null || reference == null) throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
    }
    private static PlanRefusal unsupported() { return new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION); }
    private static PlanRefusal profileRefused() { return new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED); }
    private static WorkspaceRefusal unavailable() { return new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
}
