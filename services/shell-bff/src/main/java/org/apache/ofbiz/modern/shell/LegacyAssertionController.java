package org.apache.ofbiz.modern.shell;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff/internal/legacy-assertion")
public class LegacyAssertionController {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String audience;

    @Autowired
    public LegacyAssertionController(
            JwtEncoder encoder,
            @Value("${legacy-bridge.audience}") String audience) {
        this(encoder, Clock.systemUTC(), audience);
    }

    LegacyAssertionController(JwtEncoder encoder, Clock clock, String audience) {
        this.encoder = encoder;
        this.clock = clock;
        this.audience = audience;
    }

    @GetMapping
    ResponseEntity<Void> issue(Authentication authentication) {
        var issuedAt = clock.instant();
        var expiresAt = issuedAt.plus(Duration.ofSeconds(60));
        var claims = JwtClaimsSet.builder()
                .issuer("ofbiz-shell-bff")
                .subject(authentication.getName())
                .audience(java.util.List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("authorities", authentication.getAuthorities().stream()
                        .map(Object::toString).sorted().toList())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String assertion = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return ResponseEntity.noContent()
                .header("X-Legacy-Identity-Assertion", assertion)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
