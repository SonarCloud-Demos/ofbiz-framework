package org.apache.ofbiz.modern.shell;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.http.HttpStatus;

@Configuration
public class SecurityConfiguration {
    private final boolean secureCookies;
    private final String csrfCookieName;
    private final String sessionCookieName;

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName(csrfCookieName);
        csrf.setHeaderName("X-XSRF-TOKEN");
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Strict"));

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/bff/catalog/**").hasRole("CATALOG")
                        .requestMatchers("/modern/**", "/bff/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(authorizationRequestResolver))
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(oidcUserService)
                                .userAuthoritiesMapper(entraAuthoritiesMapper()))
                        .defaultSuccessUrl("/modern/", true))
                .oauth2Client(Customizer.withDefaults())
                .csrf(csrfConfig -> csrfConfig.csrfTokenRepository(csrf))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
                .logout(logout -> logout
                        .logoutUrl("/bff/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(sessionCookieName, csrfCookieName))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; connect-src 'self'; img-src 'self'; object-src 'none'; "
                                        + "base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalLegacyBridgeSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/bff/internal/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("LEGACY_USER"))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    private final OAuth2AuthorizationRequestResolver authorizationRequestResolver;
    private final OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;

    public SecurityConfiguration(
            ClientRegistrationRepository registrations,
            TenantValidatedOidcUserService oidcUserService,
            @Value("${security.cookies.secure:true}") boolean secureCookies,
            @Value("${security.cookies.csrf-name:__Host-XSRF-TOKEN}") String csrfCookieName,
            @Value("${server.servlet.session.cookie.name:__Host-OFBIZ_SESSION}") String sessionCookieName) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        this.authorizationRequestResolver = resolver;
        this.oidcUserService = oidcUserService;
        this.secureCookies = secureCookies;
        this.csrfCookieName = csrfCookieName;
        this.sessionCookieName = sessionCookieName;
    }

    @Bean
    GrantedAuthoritiesMapper entraAuthoritiesMapper() {
        return authorities -> {
            var mapped = new HashSet<GrantedAuthority>(authorities);
            authorities.stream()
                    .filter(OidcUserAuthority.class::isInstance)
                    .map(OidcUserAuthority.class::cast)
                    .flatMap(authority -> Optional.ofNullable(
                                    authority.getIdToken().getClaimAsStringList("roles"))
                            .stream().flatMap(java.util.Collection::stream))
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(mapped::add);
            return mapped;
        };
    }

    @Bean
    JwtEncoder legacyJwtEncoder(@Value("${legacy-bridge.signing-key}") String signingKey) {
        byte[] bytes = signingKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("legacy bridge signing key must be at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
