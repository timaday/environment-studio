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
    DraftStore store() { return store.orElseThrow(() -> new WorkspaceRefusal(WorkspaceRefusal.Code.UNAVAILABLE)); }
    DraftWorkspace service() {
        return new DraftWorkspace(store(), command -> new DefinitionBytesCompiler().compile(StrictUtf8.encode(command.source()),
                DefinitionBytesCompiler.Format.valueOf(command.format().name())));
    }
}
