package org.apache.ofbiz.modern.shell;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff/session")
public class SessionController {
    @GetMapping
    Map<String, Object> session(Authentication authentication, CsrfToken csrfToken) {
        var result = new LinkedHashMap<String, Object>();
        result.put("authenticated", true);
        result.put("authorities", authentication.getAuthorities().stream()
                .map(Object::toString).sorted().toList());
        result.put("csrf", Map.of("headerName", csrfToken.getHeaderName(), "token", csrfToken.getToken()));
        if (authentication.getPrincipal() instanceof OAuth2User user) {
            result.put("displayName", user.getAttribute("name"));
            result.put("tenant", user.getAttribute("tid"));
        }
        return result;
    }
}
