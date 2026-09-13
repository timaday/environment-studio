package studio.environment.core.workspace;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import studio.environment.core.session.Owner;

/** Versioned publication application boundary; qualification remains separate. */
public final class V3PublicationWorkspace {
    private final V3NativeStore store;
    private final V3NativeWorkspace.Compiler definitions;
    private final V3ProfileWorkspace.Compiler profiles;
    private final Predicate<Owner> publishers;
    public V3PublicationWorkspace(V3NativeStore store, V3NativeWorkspace.Compiler definitions,
            V3ProfileWorkspace.Compiler profiles, Predicate<Owner> publishers) {
        this.store = Objects.requireNonNull(store); this.definitions = Objects.requireNonNull(definitions);
        this.profiles = Objects.requireNonNull(profiles); this.publishers = Objects.requireNonNull(publishers);
    }
    public V3NativeRevision publishDefinition(Owner owner, NativeCommand.PublishDefinition command) {
        requireInput(owner, command); requirePublisher(owner);
        var replay = store.replay(owner, command); if (replay.isPresent()) return replay.orElseThrow();
        var current = currentDraft(owner, command);
        if (!(current.content() instanceof V3NativeRevision.Definition definition)) throw unavailable();
        validatePolicies(definition, command.exportPolicies());
        currentDefinition(current, command.requestId());
        var result = published(current, command.exportPolicies());
        requirePublisher(owner);
        return store.append(owner, command, result);
    }
    public V3NativeRevision publishProfile(Owner owner, NativeCommand.PublishProfile command) {
        requireInput(owner, command);
        var replay = store.replay(owner, command); if (replay.isPresent()) return replay.orElseThrow();
        var current = currentDraft(owner, command);
        if (!(current.content() instanceof V3NativeRevision.Profile profile)) throw unavailable();
        var reference = profile.definition();
        var definition = store.read(owner, reference.objectId(), Optional.of(reference.workspaceRevision()), false);
        if (definition.publication().isEmpty()) throw WorkspaceRejection.publication("DEFINITION_NOT_PUBLISHED");
        var checked = currentDefinition(definition, command.requestId()).checked();
        var compiled = profiles.profile(new NativeCommand.SaveProfile(current.objectId(), current.workspaceRevision(), command.requestId(),
                current.format(), current.source(), reference), checked);
        if (compiled == null) throw unavailable();
        if (!compiled.equals(profile)) throw WorkspaceRejection.publication("CURRENT_COMPILATION_MISMATCH");
        return store.append(owner, command, published(current, List.of()));
    }
    private V3NativeRevision currentDraft(Owner owner, NativeCommand command) {
        var current = store.read(owner, command.objectId(), Optional.empty(), command.profile());
        if (current == null || !current.objectId().equals(command.objectId()) || current.profile() != command.profile()) throw unavailable();
        if (!current.workspaceRevision().equals(command.expectedRevision()) || current.publication().isPresent())
            throw new WorkspaceRefusal(WorkspaceRefusal.Code.CONFLICT);
        supported(current);
        next(current.workspaceRevision());
        return current;
    }
    private V3NativeRevision.Definition currentDefinition(V3NativeRevision revision, String requestId) {
        supported(revision);
        if (!(revision.content() instanceof V3NativeRevision.Definition definition)) throw unavailable();
        if (!definition.diagnostics().isEmpty()) throw WorkspaceRejection.publication("DEFINITION_INCOMPLETE");
        var compiled = definitions.definition(new NativeCommand.SaveDefinition(revision.objectId(), revision.workspaceRevision(), requestId,
                revision.format(), revision.source()));
        if (compiled == null) throw unavailable();
        if (!compiled.diagnostics().isEmpty()) throw WorkspaceRejection.publication("DEFINITION_INCOMPLETE");
        if (!compiled.checked().equals(definition.checked())) throw WorkspaceRejection.publication("CURRENT_COMPILATION_MISMATCH");
        return compiled;
    }
    private static void supported(V3NativeRevision revision) {
        if (!"3".equals(revision.schemaVersion()) || !(revision.profile() ? "profile-compiler-v3" : "native-compiler-v3").equals(revision.compilerVersion()))
            throw WorkspaceRejection.publication("UNSUPPORTED_COMPILER");
    }
    private static void validatePolicies(V3NativeRevision.Definition definition, List<NativeCommand.Policy> policies) {
        var expected = new HashSet<List<String>>();
        definition.checked().definition().bindings().forEach(binding -> binding.documents().forEach(document -> expected.add(List.of(binding.id(), document.id()))));
        var supplied = new HashSet<List<String>>();
        for (var policy : policies) if (!supplied.add(List.of(policy.bindingId(), policy.documentId())))
            throw WorkspaceRejection.publication("DUPLICATE_DOCUMENT_POLICY");
        if (!supplied.equals(expected)) throw WorkspaceRejection.publication("INCOMPLETE_DOCUMENT_POLICY");
    }
    private static String next(String revision) {
        String value = new BigInteger(revision).add(BigInteger.ONE).toString();
        if (!NativeCommand.revision(value, false)) throw new WorkspaceRefusal(WorkspaceRefusal.Code.TOO_LARGE);
        return value;
    }
    private static V3NativeRevision published(V3NativeRevision current, List<NativeCommand.Policy> policies) {
        var next = new V3NativeRevision(current.objectId(), next(current.workspaceRevision()), current.format(), current.source(), current.sourceDigest(),
                current.compilerVersion(), current.schemaVersion(), current.content(), Optional.empty());
        var publication = new V3NativeRevision.Publication(V3NativeWorkspaceDigests.publication(next, current.workspaceRevision(), policies), current.workspaceRevision(), policies);
        return new V3NativeRevision(next.objectId(), next.workspaceRevision(), next.format(), next.source(), next.sourceDigest(),
                next.compilerVersion(), next.schemaVersion(), next.content(), Optional.of(publication));
    }
    private void requirePublisher(Owner owner) {
        if (!publishers.test(owner)) throw new WorkspaceRefusal(WorkspaceRefusal.Code.FORBIDDEN);
    }
    private static void requireInput(Owner owner, NativeCommand command) {
        if (owner == null || command == null) throw new WorkspaceRefusal(WorkspaceRefusal.Code.INVALID_REQUEST);
    }
    private static WorkspaceRefusal unavailable() { return new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
}
