package org.apache.ofbiz.modern.adapter;
import static org.junit.jupiter.api.Assertions.*;
import com.auth0.jwt.JWT;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class OfbizAdapterApplicationTest {
 @Test void rejectsOpenRedirectAndTraversalShapes() { assertEquals("/webtools/control/main",OfbizAdapterApplication.safeLegacyPath("https://evil")); assertEquals("/webtools/control/main",OfbizAdapterApplication.safeLegacyPath("//evil")); assertEquals("/webtools/control/main",OfbizAdapterApplication.safeLegacyPath("/webtools/control/main")); }
 @Test void tokenIsShortLivedAndScopedToOFBizIssuer() { String key="x".repeat(64); Instant now=Instant.now(); var jwt=JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC512(key)).withIssuer("ApacheOFBiz").build().verify(OfbizAdapterApplication.token("admin",key,now)); assertEquals("admin",jwt.getClaim("userLoginId").asString()); assertEquals(30,jwt.getExpiresAtAsInstant().getEpochSecond()-jwt.getIssuedAtAsInstant().getEpochSecond()); }
 @Test void legacyCookieIsConstrainedToProxyPath() { String value=OfbizAdapterApplication.rewriteCookie("JSESSIONID=id; Path=/; Domain=legacy"); assertTrue(value.contains("Path=/legacy/")); assertFalse(value.contains("Domain=")); assertTrue(value.contains("HttpOnly")); }
 @Test void proxyPrefixIsPreserved() { assertEquals("http://shell:8080/legacy/webtools/control/main", OfbizAdapterApplication.legacyRequestUri(java.net.URI.create("http://shell:8080/legacy"), "/webtools/control/main").toString()); }
 @Test void catalogExportAllowsOnlyReviewedTypesAndBoundsPages() { assertEquals("products",OfbizAdapterApplication.catalogRecordType("products")); assertNull(OfbizAdapterApplication.catalogRecordType("Product")); assertNull(OfbizAdapterApplication.catalogRecordType("../products")); assertEquals(200,OfbizAdapterApplication.catalogPageSize(null)); assertEquals(500,OfbizAdapterApplication.catalogPageSize("999")); assertEquals(-1,OfbizAdapterApplication.catalogPageSize("many")); }
}
