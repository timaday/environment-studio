package studio.environment.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EnvironmentStudioApplication {
    public static void main(String[] args) {
        if (java.util.Arrays.stream(args).anyMatch(arg -> arg.startsWith("--initialize-workspace"))) {
            boolean v3 = args.length == 1 && args[0].startsWith("--initialize-workspace-v3=/");
            String prefix = v3 ? "--initialize-workspace-v3=" : "--initialize-workspace=";
            if (args.length != 1 || !args[0].startsWith(prefix + "/")) throw new IllegalArgumentException("INVALID_INITIALIZER_ARGUMENTS");
            try {
                var directory = java.nio.file.Path.of(args[0].substring(prefix.length()));
                if (v3) studio.environment.server.workspace.SqliteDraftStore.initializeV3(directory);
                else studio.environment.server.workspace.SqliteDraftStore.initialize(directory);
            }
            catch (java.nio.file.InvalidPathException invalid) { throw new IllegalArgumentException("INVALID_INITIALIZER_ARGUMENTS"); }
            return;
        }
        if(java.util.Arrays.stream(args).anyMatch(arg->arg.startsWith("--upgrade-workspace"))) {
            boolean v3 = args.length == 1 && args[0].startsWith("--upgrade-workspace-v3=/");
            String prefix = v3 ? "--upgrade-workspace-v3=" : "--upgrade-workspace=";
            if(args.length!=1||!args[0].startsWith(prefix + "/"))throw new IllegalArgumentException("INVALID_UPGRADE_ARGUMENTS");
            try {
                var directory = java.nio.file.Path.of(args[0].substring(prefix.length()));
                if (v3) studio.environment.server.workspace.SqliteDraftStore.upgradeV3(directory);
                else studio.environment.server.workspace.SqliteDraftStore.upgrade(directory);
            }
            catch(java.nio.file.InvalidPathException invalid){throw new IllegalArgumentException("INVALID_UPGRADE_ARGUMENTS");}
            return;
        }
        SpringApplication.run(EnvironmentStudioApplication.class, args);
    }
}
