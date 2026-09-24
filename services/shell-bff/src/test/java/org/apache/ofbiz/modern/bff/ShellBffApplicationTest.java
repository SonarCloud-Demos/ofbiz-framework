package org.apache.ofbiz.modern.bff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.util.Set;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ShellBffApplicationTest {
    @Test void acceptsOnlyExplicitTrustedHosts() {
        assertTrue(ShellBffApplication.trustedHost("localhost:8080", Set.of("localhost")));
        assertFalse(ShellBffApplication.trustedHost("attacker.example", Set.of("localhost")));
        assertFalse(ShellBffApplication.trustedHost(null, Set.of("localhost")));
    }

    @Test void rejectsUnsafeCorrelationValues() {
        assertEquals("trace-123", ShellBffApplication.correlationId("trace-123"));
        assertFalse(ShellBffApplication.correlationId("bad\r\nheader").contains("\r"));
    }

    @Test void returnLocationsCannotBecomeOpenRedirects() {
        assertEquals("/modern/platform", ShellBffApplication.safeReturnPath("https://attacker.example"));
        assertEquals("/modern/platform", ShellBffApplication.safeReturnPath("//attacker.example"));
        assertEquals("/modern/platform", ShellBffApplication.safeReturnPath("/modern/platform"));
    }

    @Test void csrfRequiresTheSessionSecret() {
        var session = new ShellBffApplication.Session("subject", "user", Set.of("development-user"), "secret", Instant.MAX);
        assertTrue(ShellBffApplication.csrfMatches(session, "secret"));
        assertFalse(ShellBffApplication.csrfMatches(session, "wrong"));
        assertFalse(ShellBffApplication.csrfMatches(null, "secret"));
    }

    @Test void productionCookieHasBrowserSecurityAttributes() {
        String cookie = ShellBffApplication.cookie("opaque", true, 1800);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Secure"));
        assertFalse(cookie.contains("Bearer"));
    }

    @Test void routeCohortsAreStableAndKillSwitchWins() {
        assertTrue(ShellBffApplication.cohortEnabled("subject", 100, false));
        assertFalse(ShellBffApplication.cohortEnabled("subject", 0, false));
        assertFalse(ShellBffApplication.cohortEnabled("subject", 100, true));
        assertEquals(ShellBffApplication.cohortEnabled("subject", 25, false),
                ShellBffApplication.cohortEnabled("subject", 25, false));
    }

    @Test void catalogProxyAllowsOnlyReviewedPathsAndQueries() {
        URI base = URI.create("http://catalog:8080/api/catalog/v1");
        assertEquals("http://catalog:8080/api/catalog/v1/categories/100/products?limit=20&sort=name",
                ShellBffApplication.catalogUri(base, URI.create("/bff/catalog/categories/100/products?limit=20&sort=name")).toString());
        assertEquals("http://catalog:8080/api/catalog/v1/search?q=tiny+gizmo",
                ShellBffApplication.catalogUri(base, URI.create("/bff/catalog/search?q=tiny%20gizmo")).toString());
        assertEquals(null, ShellBffApplication.catalogUri(base, URI.create("/bff/catalog/admin")));
        assertEquals(null, ShellBffApplication.catalogUri(base, URI.create("/bff/catalog/search?q=x&unknown=y")));
        assertEquals(null, ShellBffApplication.catalogUri(base, URI.create("/bff/catalog/search?q=x&limit=1000")));
    }
}
