package com.systemdesign.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context-load smoke test only. Endpoint-level tests belong with the
 * FixedWindowCounterRateLimiter implementation, since checkLimit() currently
 * throws UnsupportedOperationException and any request through GatewayController
 * would fail.
 */
@SpringBootTest
class RateLimiterServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
