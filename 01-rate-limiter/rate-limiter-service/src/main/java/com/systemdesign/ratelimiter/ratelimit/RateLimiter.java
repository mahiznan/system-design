package com.systemdesign.ratelimiter.ratelimit;

import com.systemdesign.ratelimiter.Tier;

/** The seam every algorithm from PROBLEM.md FR9 implements. */
public interface RateLimiter {
    Decision checkLimit(String apiKey, Tier tier);
}
