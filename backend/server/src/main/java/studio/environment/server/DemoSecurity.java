package studio.environment.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class DemoSecurity {
    @Bean SecurityFilterChain security(HttpSecurity http, @Value("${studio.mode}") String mode) throws Exception {
        if (!"demo".equals(mode)) throw new IllegalStateException("UNIMPLEMENTED_RUNTIME_MODE");
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/brand/**",
                        "/api/v1/capabilities", "/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().denyAll());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
                + "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }
}
