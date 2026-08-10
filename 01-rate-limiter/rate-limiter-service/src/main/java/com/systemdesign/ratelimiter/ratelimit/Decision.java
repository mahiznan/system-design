package com.systemdesign.ratelimiter.ratelimit;

/**
 * Result of a rate-limit check (PROBLEM.md FR3/FR4).
 *
 * @param allowed            whether the request should be let through
 * @param limit              the caller's tier limit (X-RateLimit-Limit)
 * @param remaining          requests remaining in the current window (X-RateLimit-Remaining), never negative
 * @param resetEpochSeconds  when the current window resets, epoch seconds (X-RateLimit-Reset)
 * @param retryAfterSeconds  seconds until the caller may retry; only meaningful when !allowed (Retry-After)
 */
public record Decision(
        boolean allowed,
        int limit,
        int remaining,
        long resetEpochSeconds,
        long retryAfterSeconds
) {
}
