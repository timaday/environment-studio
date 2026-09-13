package studio.environment.server.workspace;

import studio.environment.core.plan.*;
import studio.environment.core.plan.PlanPorts.*;
import studio.environment.core.session.Owner;
import studio.environment.core.workspace.*;
import studio.environment.core.definitionv2.NativeCompilationResult.ReadyToPublish;
import studio.environment.server.profile.ProfileBytesAdapter;
import studio.environment.server.definition.BoundedDocumentParser;

/** Read-only, owned immutable publication lookup. No current recompilation, draft promotion or writes. */
public final class PlanWorkspaceBridge implements Workspace {
    private final NativeStore store;
    private final ProfileBytesAdapter profiles = new ProfileBytesAdapter();
    public PlanWorkspaceBridge(WorkspaceRuntime runtime) { this.store=runtime.nativeStore(); }
    public PlanWorkspaceBridge(NativeStore store) { this.store=store; }
    @Override public PublishedDefinition definition(Owner owner,NativeCommand.Reference reference) {
        var revision=store.read(owner,reference.objectId(),java.util.Optional.of(reference.workspaceRevision()),false);
        if(revision.publication().isEmpty() || !(revision.content() instanceof NativeRevision.Definition definition)) throw new PlanRefusal(PlanRefusal.Code.PUBLICATION_REQUIRED);
        if(!revision.compilerVersion().equals("native-compiler-v2") || !revision.schemaVersion().equals("2")) throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        try { NativeWorkspace.eligibleDefinition(definition); NativeWorkspace.validatePolicies(definition,revision.publication().orElseThrow().exportPolicies()); }
        catch(WorkspaceRejection invalid) { throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION); }
        return new PublishedDefinition(reference,revision.publication().orElseThrow().digest(),new ReadyToPublish(definition.checked()),revision.publication().orElseThrow().exportPolicies());
    }
    @Override public PublishedProfile profile(Owner owner,NativeCommand.Reference reference,PublishedDefinition definition) {
        var revision=store.read(owner,reference.objectId(),java.util.Optional.of(reference.workspaceRevision()),true);
        if(revision.publication().isEmpty() || !(revision.content() instanceof NativeRevision.Profile profile)) throw new PlanRefusal(PlanRefusal.Code.PUBLICATION_REQUIRED);
        if(!revision.compilerVersion().equals("profile-compiler-v2") || !revision.schemaVersion().equals("2")) throw new PlanRefusal(PlanRefusal.Code.UNSUPPORTED_DEFINITION);
        var encoded=profiles.write(definition.compiled(),profile.checked());
        if(!(encoded instanceof ProfileBytesAdapter.ExportResult.Encoded bytes)) throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
        var portable=profiles.read(definition.compiled(),bytes.bytes(),BoundedDocumentParser.Format.JSON);
        if(!(portable instanceof ProfileBytesAdapter.Result.Accepted accepted) || !accepted.checked().equals(profile.checked())) throw new PlanRefusal(PlanRefusal.Code.PROFILE_REFUSED);
        return new PublishedProfile(reference,revision.publication().orElseThrow().digest(),accepted.checked());
    }
}
