package studio.environment.server.workspace;

import java.nio.file.Path;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import studio.environment.core.workspace.*;
import studio.environment.server.definition.DefinitionBytesCompiler;
import studio.environment.server.security.RuntimeConfiguration.RuntimeMode;

@Component
public final class WorkspaceRuntime {
    private final Optional<SqliteDraftStore> store;
    private final DefinitionPublishers publishers;
    public WorkspaceRuntime(Environment environment, RuntimeMode mode) {
        publishers=new DefinitionPublishers(environment);
        String directory = environment.getProperty("studio.workspace.directory");
        store = mode == RuntimeMode.HOSTED && directory != null
                ? Optional.of(open(directory)) : Optional.empty();
    }
    private static SqliteDraftStore open(String directory) {
        try { return new SqliteDraftStore(Path.of(directory)); }
        catch (java.nio.file.InvalidPathException invalid) { throw new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE); }
    }
    public boolean enabled() { return store.isPresent(); }
    boolean canPublish(studio.environment.core.session.Owner owner) {return publishers.test(owner);}
    NativeStore nativeStore() {return new NativeSqliteStore(store.orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)));}
    NativeWorkspace nativeService() {return new NativeWorkspace(nativeStore(),new NativeWorkspaceCompiler(),publishers);}
    NativeWorkspace nativeService(WorkspaceCommit commit) {
        return new NativeWorkspace(new NativeSqliteStore(store.orElseThrow(() -> new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)).withCommit(commit)), new NativeWorkspaceCompiler(), publishers);
    }
    private final V3WorkspaceOperations v3Operations=new V3WorkspaceOperations();
    V3WorkspaceOperations v3Operations(){return v3Operations;}
    V3NativeStore v3Store(){return new V3NativeSqliteStore(store.orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)));}
    V3NativeWorkspace v3Service(WorkspaceCommit commit){return new V3NativeWorkspace(new V3NativeSqliteStore(store.orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)).withCommit(commit)),new V3NativeWorkspaceCompiler());}
    V3ProfileWorkspace v3ProfileService(WorkspaceCommit commit){return new V3ProfileWorkspace(new V3NativeSqliteStore(store.orElseThrow(()->new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)).withCommit(commit)),new V3ProfileWorkspaceCompiler());}
    V3PublicationWorkspace v3PublicationService(WorkspaceCommit commit) {
        return new V3PublicationWorkspace(new V3NativeSqliteStore(store.orElseThrow(() -> new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)).withCommit(commit)),
                new V3NativeWorkspaceCompiler(), new V3ProfileWorkspaceCompiler(), publishers);
    }
    public void cleanupV3(studio.environment.core.session.SessionLedger.Lease lease){v3Operations.invalidate(lease);}
    public boolean awaitingV3CleanupWork(studio.environment.core.session.SessionLedger.Lease lease){return v3Operations.awaitingWork(lease);}
    DraftWorkspace service(WorkspaceCommit commit) {
        return new DraftWorkspace(store.orElseThrow(() -> new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)).withCommit(commit),
            command -> new DefinitionBytesCompiler().compile(StrictUtf8.encode(command.source()), DefinitionBytesCompiler.Format.valueOf(command.format().name())));
    }
    DraftStore store() { return store.orElseThrow(() -> new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)); }
    DraftWorkspace service() {
        return new DraftWorkspace(store(), command -> new DefinitionBytesCompiler().compile(StrictUtf8.encode(command.source()),
                DefinitionBytesCompiler.Format.valueOf(command.format().name())));
    }
}
