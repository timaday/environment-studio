package studio.environment.server.security;

import jakarta.servlet.http.*;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import studio.environment.core.session.SessionLedger;
import studio.environment.server.session.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "studio.mode", havingValue = "hosted")
public class HostedSecurity {
    @Bean HostedSettings hostedSettings(Environment environment) { return new HostedSettings(environment); }
    @Bean Clock sessionClock() { return Clock.systemUTC(); }
    @Bean HostedSessions hostedSessions(Clock clock, List<SessionCleanup> cleanup) { return new HostedSessions(clock, cleanup); }
    @Bean SessionExpiry sessionExpiry(HostedSessions sessions) { return new SessionExpiry(sessions); }
    static final class SessionExpiry {
        private final HostedSessions sessions;
        SessionExpiry(HostedSessions sessions) { this.sessions = sessions; }
        @Scheduled(fixedDelay = 1000) void expire() { sessions.expire(); }
    }
    @Bean ClientRegistrationRepository registrations(HostedSettings settings) {
        ClientRegistration registration;
        try {
            registration = ClientRegistrations.fromOidcIssuerLocation(settings.issuer()).registrationId("studio")
                    .clientId(settings.clientId()).clientSecret(settings.clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).scope("openid")
                    .redirectUri(settings.origin() + "/login/oauth2/code/studio").build();
        } catch (RuntimeException exception) { throw new IllegalStateException("OIDC_DISCOVERY_UNAVAILABLE"); }
        settings.validateProviderEndpoint(registration.getProviderDetails().getAuthorizationUri());
        settings.validateProviderEndpoint(registration.getProviderDetails().getTokenUri());
        settings.validateProviderEndpoint(registration.getProviderDetails().getJwkSetUri());
        return new InMemoryClientRegistrationRepository(registration);
    }
    @Bean SecurityFilterChain hostedFilterChain(HttpSecurity http, HostedSettings settings,
            ClientRegistrationRepository registrations, HostedSessions sessions) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        var users = new OidcUserService();
        users.setRetrieveUserInfo(request -> false);
        http.addFilterBefore(new HostedBoundaryFilter(settings, sessions), CsrfFilter.class);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/brand/**", "/api/v1/capabilities",
                        "/actuator/health", "/actuator/health/**", "/oauth2/authorization/studio", "/login/oauth2/code/studio").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/session").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/session/logout").authenticated()
                .anyRequest().denyAll());
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> SafeResponses.refuse(response, 401, "AUTHENTICATION_REQUIRED"))
                .accessDeniedHandler((request, response, exception) -> SafeResponses.refuse(response, 403, "REQUEST_DENIED")));
        http.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
        http.oauth2Login(login -> login.loginPage("/oauth2/authorization/studio")
                .oidcSessionRegistry(new NoProviderSessionRegistry())
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .userInfoEndpoint(endpoint -> endpoint.oidcUserService(users))
                .authorizedClientRepository(new DiscardProviderCredentials())
                .successHandler((request, response, authentication) -> {
                    var admission = sessions.authenticated(request.getSession(), (OidcUser) authentication.getPrincipal());
                    if (admission instanceof SessionLedger.Denied denied) {
                        request.getSession().invalidate();
                        SecurityContextHolder.clearContext();
                        SafeResponses.refuse(response, 403, "SESSION_" + denied.reason().name());
                    } else response.sendRedirect(settings.origin() + "/");
                })
                .failureHandler((request, response, exception) -> {
                    var session = request.getSession(false);
                    if (session != null) session.invalidate();
                    SecurityContextHolder.clearContext();
                    SafeResponses.refuse(response, 401, "LOGIN_FAILED");
                }));
        http.logout(logout -> logout.logoutUrl("/api/v1/session/logout").invalidateHttpSession(false)
                .addLogoutHandler((request, response, authentication) -> sessions.logout(request)
                        .ifPresent(report -> request.setAttribute("studio.logoutCleanup", report)))
                .logoutSuccessHandler((request, response, authentication) -> {
                    if (request.getAttribute("studio.logoutCleanup") instanceof SessionLedger.CleanupReport report
                            && report.state() != SessionLedger.CleanupState.COMPLETE) {
                        SafeResponses.refuse(response, 503, "SESSION_CLEANUP_INCONCLUSIVE");
                    } else {
                        response.setHeader("Cache-Control", "no-store");
                        response.setStatus(204);
                    }
                }).deleteCookies("JSESSIONID"));
        http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; "
                + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }
    /** D02a implements local logout only; retain no extra provider back-channel session/token index. */
    private static final class NoProviderSessionRegistry implements org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry {
        @Override public void saveSessionInformation(org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation information) { }
        @Override public org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation removeSessionInformation(String id) { return null; }
        @Override public Iterable<org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation> removeSessionInformation(
                org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken token) { return List.of(); }
    }
    /** Login needs no downstream provider API; deliberately retain no access/refresh tokens. */
    private static final class DiscardProviderCredentials implements OAuth2AuthorizedClientRepository {
        @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication principal, HttpServletRequest request) { return null; }
        @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, HttpServletRequest request, HttpServletResponse response) { }
        @Override public void removeAuthorizedClient(String id, Authentication principal, HttpServletRequest request, HttpServletResponse response) { }
    }
}
