package org.apache.ofbiz.modern.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.entra.client-id=test-client",
        "spring.security.oauth2.client.registration.entra.client-secret=test-secret",
        "spring.security.oauth2.client.provider.entra.authorization-uri=https://login.example/authorize",
        "spring.security.oauth2.client.provider.entra.token-uri=https://login.example/token",
        "spring.security.oauth2.client.provider.entra.jwk-set-uri=https://login.example/keys",
        "spring.security.oauth2.client.provider.entra.user-name-attribute=sub",
        "legacy-bridge.signing-key=01234567890123456789012345678901",
        "identity.allowed-tenants=tenant-a",
        "identity.expected-issuer=https://login.example/tenant-a/v2.0"
})
@AutoConfigureMockMvc
class ShellBffSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousShellRequestStartsLogin() throws Exception {
        mvc.perform(get("/modern/"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/bff/internal/legacy-assertion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadSessionWithoutTokens() throws Exception {
        mvc.perform(get("/bff/session").with(oidcLogin()
                        .idToken(token -> token.claim("tid", "tenant-a").claim("name", "Ada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.displayName").value("Ada"))
                .andExpect(jsonPath("$.tenant").value("tenant-a"))
                .andExpect(jsonPath("$.csrf.headerName").isNotEmpty())
                .andExpect(jsonPath("$.csrf.token").isNotEmpty())
                .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    @Test
    void unsafeRequestRequiresCsrfAndLegacyRole() throws Exception {
        var legacyUser = oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_LEGACY_USER"));
        mvc.perform(post("/bff/logout").with(legacyUser))
                .andExpect(status().isForbidden());
        mvc.perform(get("/bff/internal/legacy-assertion").with(oidcLogin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyAssertionIsSignedAudienceBoundAndShortLived() throws Exception {
        String assertion = mvc.perform(get("/bff/internal/legacy-assertion")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_LEGACY_USER"))))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader("X-Legacy-Identity-Assertion");
        var key = new SecretKeySpec(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var jwt = NimbusJwtDecoder.withSecretKey(key).build().decode(assertion);
        assertThat(jwt.getAudience()).containsExactly("ofbiz-legacy");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(jwt.getExpiresAt().minusSeconds(61)).isBefore(jwt.getIssuedAt());
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void loginUsesPkce() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/entra"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).contains("code_challenge=").contains("code_challenge_method=S256");
    }
}
