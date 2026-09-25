package org.apache.ofbiz.modern.identity;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
class IdentityAccessApplicationTest {
 @Test void mappingsUseImmutableSubject() throws Exception { var values = IdentityAccessApplication.parseMappings("{\"tenant/subject\":{\"principalId\":\"p1\",\"partyId\":\"admin\",\"userLoginId\":\"admin\"}}"); assertEquals("admin", values.get("tenant/subject").partyId()); }
 @Test void exchangesAreSingleUseAndExpire() { var map = new HashMap<String, IdentityAccessApplication.Exchange>(); var value = new IdentityAccessApplication.Exchange(new IdentityAccessApplication.Mapping("p","party","user"), Instant.now().plusSeconds(1)); map.put("code", value); assertNotNull(IdentityAccessApplication.redeem(map,"code",Instant.now())); assertNull(IdentityAccessApplication.redeem(map,"code",Instant.now())); map.put("old", new IdentityAccessApplication.Exchange(value.mapping(), Instant.EPOCH)); assertNull(IdentityAccessApplication.redeem(map,"old",Instant.now())); }
 @Test void workloadTokenComparisonFailsClosed() { assertTrue(IdentityAccessApplication.authorized("secret","secret")); assertFalse(IdentityAccessApplication.authorized(null,"secret")); assertFalse(IdentityAccessApplication.authorized("wrong","secret")); }
}
