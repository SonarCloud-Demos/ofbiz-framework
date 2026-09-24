package org.apache.ofbiz.modern.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class TenantValidatedOidcUserServiceTest {
    private final TenantValidatedOidcUserService service = new TenantValidatedOidcUserService(
            "tenant-a,tenant-b", "https://login.example/tenant-a/v2.0");

    @Test
    void acceptsExactTenantAndIssuer() {
        OidcUser user = user("tenant-a", "https://login.example/tenant-a/v2.0");
        assertThat(service.validateUser(user)).isSameAs(user);
    }

    @Test
    void rejectsAnotherTenantOrIssuer() {
        assertThatThrownBy(() -> service.validateUser(user(
                "tenant-c", "https://login.example/tenant-a/v2.0")))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> service.validateUser(user(
                "tenant-a", "https://attacker.example/tenant-a/v2.0")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private OidcUser user(String tenant, String issuer) {
        OidcUser user = mock(OidcUser.class);
        when(user.getClaimAsString("tid")).thenReturn(tenant);
        when(user.getClaimAsString("iss")).thenReturn(issuer);
        return user;
    }
}
