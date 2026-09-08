package studio.environment.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EnvironmentStudioApplication {
    public static void main(String[] args) {
        if (java.util.Arrays.stream(args).anyMatch(arg -> arg.startsWith("--initialize-workspace"))) {
            if (args.length != 1 || !args[0].startsWith("--initialize-workspace=/")) throw new IllegalArgumentException("INVALID_INITIALIZER_ARGUMENTS");
            try { studio.environment.server.workspace.SqliteDraftStore.initialize(java.nio.file.Path.of(args[0].substring("--initialize-workspace=".length()))); }
            catch (java.nio.file.InvalidPathException invalid) { throw new IllegalArgumentException("INVALID_INITIALIZER_ARGUMENTS"); }
            return;
        }
        SpringApplication.run(EnvironmentStudioApplication.class, args);
    }
}
