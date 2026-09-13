package studio.environment.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class RuntimeConfiguration {
    @Bean public RuntimeMode runtimeMode(@Value("${studio.mode}") String mode) {
        return switch (mode) {
            case "demo" -> RuntimeMode.DEMO;
            case "hosted" -> RuntimeMode.HOSTED;
            default -> throw new IllegalStateException("UNSUPPORTED_RUNTIME_MODE");
        };
    }
    // Neither mode supports local passwords; prevent generated credential logging.
    @Bean UserDetailsService noLocalPasswords() { return new InMemoryUserDetailsManager(); }
    public enum RuntimeMode { DEMO, HOSTED }
}
