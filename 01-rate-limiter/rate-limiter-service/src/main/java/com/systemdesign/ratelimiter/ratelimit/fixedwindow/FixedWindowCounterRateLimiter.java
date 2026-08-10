package com.systemdesign.ratelimiter.ratelimit.fixedwindow;

import com.systemdesign.ratelimiter.Tier;
import com.systemdesign.ratelimiter.ratelimit.Decision;
import com.systemdesign.ratelimiter.ratelimit.RateLimiter;
import org.springframework.stereotype.Component;

/**
 * Fixed Window Counter (PROBLEM.md FR9) — YOUR IMPLEMENTATION.
 *
 * Per-key state, concurrency strategy, and window-boundary handling are the
 * exercise itself, not a scaffolding detail — decide and implement them here.
 * The GatewayController already calls checkLimit() with the caller's API key
 * and resolved Tier, and wires the returned Decision into the response headers
 * and 200/429 status — no changes needed there to plug this in.
 */
@Component
public class FixedWindowCounterRateLimiter implements RateLimiter {

    @Override
    public Decision checkLimit(String apiKey, Tier tier) {
        throw new UnsupportedOperationException(
                "FixedWindowCounterRateLimiter.checkLimit() not implemented yet");
    }
}
