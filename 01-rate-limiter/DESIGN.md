# Design — 01 Rate Limiter

> Status: **DRAFT.** Written against the locked requirements in `PROBLEM.md`.

## 1. Capacity Estimate

**Decision throughput:** 5,000 req/sec aggregate (NFR1), identical across all five
algorithms — every request requires a decision, there is no read/write split for
this service (an earlier draft incorrectly modeled this as an 80/20 read/write
split with per-request payload storage; dropped — the rate limiter has no such
concept, and the correct dimensions are throughput, per-key memory footprint, and
Redis ops/sec, not bytes-written-over-time).

**Key population assumption:** 1,000 keys (NFR1), split **700 Free / 200 Standard /
100 Premium** — skewed toward the cheap tier to reflect a realistic client base
(inverted-pyramid tier distributions are typical of real API products). This is an
assumption, not a given requirement — override the counts below if the real split
differs; the calculation method stays the same.

Note: the *sum* of per-key limits at this split (`700×60 + 200×600 + 100×6000 =
762,000 req/min ≈ 12,700 req/sec`) legitimately exceeds the 5,000 req/sec aggregate
peak (NFR1). That is expected, not a contradiction to resolve — tier limits are
provisioned per-client ceilings, not a guarantee of traffic; real systems always
oversubscribe quotas relative to observed aggregate demand. Per-key memory must
still be sized against the **worst case** (every key simultaneously at its own
limit), because the rate limiter's job is to survive exactly that scenario, not the
average case.

**Per-key state and memory footprint** (JVM in-process for phase 1, Redis for
phase 2), at the 1,000-key population above:

| Algorithm | Per-key fields | JVM (phase 1) total | Redis (phase 2) total |
|---|---|---|---|
| Fixed Window Counter | counter + window_start (16B raw) | ~180 KB | ~100–120 KB |
| Token Bucket | tokens + last_refill_ts (16B raw) | ~180 KB | ~100–120 KB |
| Leaky Bucket | level + last_leak_ts (16B raw) | ~180 KB | ~100–120 KB |
| Sliding Window Counter | current + previous + window_start (24B raw) | ~190 KB | ~110–130 KB |
| **Sliding Window Log** | 1 timestamp per accepted request **in that key's own trailing window** (worst case per key = its own tier limit) | **~6.1 MB** (primitive `long[]` ring buffer/key) or **~24 MB** if implemented naively as `Deque<Long>` (boxing cost) | **~68.6 MB** (`ZSET`, skiplist-encoded — every Premium key exceeds the ~128-entry listpack threshold) |

Sliding Window Log worst case: `700×60 + 200×600 + 100×6000 = 762,000` timestamp
entries live simultaneously across all keys. JVM optimized: `762,000 × 8B ≈
6.1 MB`. JVM naive: `762,000 × 32B (boxed `Long` + `Deque` node overhead) ≈
24.4 MB`. Redis: `762,000 × ~90B (skiplist node + dict entry + `robj` wrapper) ≈
68.6 MB`. **Sliding Window Log is ~500–700x every other algorithm** — the single
biggest differentiator in FR9's comparison, and the primitive-array-vs-boxed-`Deque`
implementation choice alone swings the phase-1 number 4x.

**Redis ops/sec (phase 2):** all five algorithms are ~5,000 ops/sec at the
network/round-trip level, provided each is implemented as a single atomic Lua
script (read + decide + update, server-side, one round trip per decision). They
diverge in **Redis-side CPU cost per op**, not round-trip count: Fixed
Window/Token Bucket/Leaky Bucket/Sliding Window Counter are O(1) per script;
Sliding Window Log's script does `ZREMRANGEBYSCORE` (prune) + `ZCARD`/`ZADD`, which
is O(log n) against up to 6,000 members for a Premium key — meaningfully more
Redis-side CPU per request at identical QPS.

**Bandwidth:** not material — a rate-limit decision request/response is a handful
of bytes; at 5,000/sec that's noise. Not sized further.

## 2. Architecture

**Phase 1** (confirmed 2026-08-10):

```
client --(X-API-Key header)--> [rate-limiter-service :8080]
                                       |  allow? proxy through
                                       |  reject? 429 direct
                                       v
                                [echo-backend :8081]

rate-limiter-service also reads:
  - config/api-keys.yaml  (key -> tier, hot-reloaded on file change, FR7 phase 1)
  - exposes /actuator/prometheus for scraping (NFR5)
```

Two separate Spring Boot processes (Maven multi-module: `rate-limiter-service`,
`echo-backend`), matching FR5's "standalone service in front of the API," not a
library embedded in the backend. `docker-compose.yml` runs both plus Prometheus and
Grafana. No Redis in phase 1 — that's introduced in phase 2 (NFR3), not before.

**Phase 2** (not yet built): multiple `rate-limiter-service` instances behind a
load balancer, sharing counter state via Redis instead of each holding its own
in-memory map; key→tier mapping also moves from the file into Redis (FR7 phase 2).
To be detailed here once phase 1 is implemented, load-tested, and chaos-tested.

## 3. API Contracts

**Phase 1** (confirmed 2026-08-10):

- The rate limiter is a transparent proxy — it accepts any method/path and
  forwards allowed requests to the echo backend unchanged; it does not expose a
  fixed set of its own endpoints.
- Caller identity: `X-API-Key` request header (not specified in PROBLEM.md FR1;
  fixed here as part of this design).
- **200 (allowed):** the echo backend's response, with `X-RateLimit-Limit`,
  `X-RateLimit-Remaining`, `X-RateLimit-Reset` added (FR4).
- **429 (rejected):** empty body, the same three `X-RateLimit-*` headers
  (`Remaining: 0`) plus `Retry-After` (FR3).
- **401 (unauthorized):** missing or unrecognized `X-API-Key` — not covered by any
  locked FR; adopted here as the standard gateway behavior for unrecognized
  credentials. Flagged as a gap in the locked requirements, not silently assumed.
- No admin API in phase 1 — the file watcher is the entire config-update surface
  (FR7 explicitly scopes phase 1 to file-based config).

## 4. Data Model

**Phase 1** (confirmed 2026-08-10):

- `config/api-keys.yaml` — key→tier mapping, hot-reloaded via a filesystem watch:
  ```yaml
  key-free-1: FREE
  key-standard-1: STANDARD
  key-premium-1: PREMIUM
  ```
- Tier→limit mapping (FREE=60, STANDARD=600, PREMIUM=6000) is a fixed constant in
  code, not configurable — matches the PROBLEM.md non-goal ruling out per-client
  limit overrides.
- Per-key algorithm state lives behind a single seam:
  ```java
  public interface RateLimiter {
      Decision checkLimit(String apiKey, Tier tier);
  }
  ```
  `FixedWindowCounterRateLimiter` (first implementation) decides its own internal
  state shape and concurrency strategy — that decision is the actual exercise, not
  a scaffolding detail; the `ConcurrentHashMap`-keyed-by-API-key shape used in the
  section 1 capacity estimate was for sizing purposes only, not a binding
  implementation constraint.
- Phase 2 data model (Redis key shapes, TTLs) — to be detailed once phase 1 is
  built and phase 2 starts.

## 5. Algorithm Tradeoffs

<!--
For each of the 5 algorithms from FR9 (Fixed Window, Sliding Window Log,
Sliding Window Counter, Token Bucket, Leaky Bucket): how it's implemented here
specifically, and which one you'd actually recommend shipping for this
problem's requirements — and why, backed by the capacity estimate above,
not just the general theory.
-->

## 6. Failure Modes

<!--
What breaks and how, specifically for phase 2 (Redis-backed, multi-instance):
- Redis unavailable — fail open (allow) or fail closed (reject)? Why?
- Race conditions on concurrent increments across instances
- Clock skew across instances for window-boundary algorithms
- Config file watcher missing an update / Redis key→tier lookup failing
-->

## 7. Retro Checklist

<!-- Fill this in only after build + load test + chaos test are done. -->

- [ ] Could this handle 10x the traffic (NFR1 x10)? What breaks first?
- [ ] How would you shard, replicate, or cache this further?
- [ ] What did the chaos test reveal that the design didn't anticipate?
