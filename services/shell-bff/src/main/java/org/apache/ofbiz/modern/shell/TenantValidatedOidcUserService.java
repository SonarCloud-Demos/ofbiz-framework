package org.apache.ofbiz.modern.shell;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class TenantValidatedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();
    private final Set<String> allowedTenants;
    private final String expectedIssuer;

    public TenantValidatedOidcUserService(
            @Value("${identity.allowed-tenants}") String tenants,
            @Value("${identity.expected-issuer}") String expectedIssuer) {
        this.allowedTenants = Arrays.stream(tenants.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (allowedTenants.isEmpty()) {
            throw new IllegalArgumentException("at least one allowed Entra tenant is required");
        }
        this.expectedIssuer = expectedIssuer;
        if (expectedIssuer.isBlank()) {
            throw new IllegalArgumentException("expected Entra issuer is required");
        }
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        return validateUser(delegate.loadUser(request));
    }

    OidcUser validateUser(OidcUser user) {
        String tenant = user.getClaimAsString("tid");
        String issuer = user.getClaimAsString("iss");
        if (tenant == null || !allowedTenants.contains(tenant)
                || issuer == null || !expectedIssuer.equals(issuer)) {
            throw new OAuth2AuthenticationException(new OAuth2Error("tenant_not_allowed"));
        }
        return user;
    }
}
