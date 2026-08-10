package com.systemdesign.ratelimiter;

/** Fixed tiers per PROBLEM.md FR2 — no custom per-client overrides (explicit non-goal). */
public enum Tier {
    FREE(60),
    STANDARD(600),
    PREMIUM(6000);

    private final int limitPerMinute;

    Tier(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    public int limitPerMinute() {
        return limitPerMinute;
    }
}
