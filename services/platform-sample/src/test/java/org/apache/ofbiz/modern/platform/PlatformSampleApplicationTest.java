package org.apache.ofbiz.modern.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformSampleApplicationTest {
    @Test void responseCarriesCorrelationAndPlatformIdentity() {
        String response = PlatformSampleApplication.response("test-correlation", Instant.EPOCH);
        assertTrue(response.contains("platform-sample"));
        assertTrue(response.contains("test-correlation"));
        assertTrue(response.contains("1970-01-01T00:00:00Z"));
    }
}
