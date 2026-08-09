# Problem 01 — Rate Limiter

> Status: **LOCKED — confirmed 2026-08-10.**

## Initial ask (as received)

> "Our public API is getting hammered by a few clients, and it's affecting
> everyone else. We need a rate limiter in front of it so no single client can
> overwhelm the system. Can you build that?"

That vague ask was refined into the requirements below through discussion.

## Functional Requirements

- **FR1.** Limit requests **per API key** (the client identifier) — not per
  user, not per IP.
- **FR2.** Each API key belongs to exactly one tier:
  - Free: 60 req/min
  - Standard: 600 req/min
  - Premium: 6,000 req/min
- **FR3.** A request exceeding its key's limit is rejected with **HTTP 429
  Too Many Requests** and a `Retry-After` header.
- **FR4.** Every response (200 or 429) includes standard rate-limit headers:
  `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- **FR5.** The rate limiter is a **standalone service in front of** the API
  (gateway-style), not a library embedded in each service.
- **FR6.** A minimal stub "echo" backend is built and proxied to on allowed
  requests, so the whole system is demoable end-to-end (curl it, watch 200s
  turn into 429s).
- **FR7.** API key → tier mapping is updatable **without restarting the
  service**:
  - Phase 1 (single instance): static config file, hot-reloaded via a file
    watcher on change.
  - Phase 2 (multi-instance): mapping moves into Redis (a hash) alongside
    the counters — any instance reads the latest mapping instantly, no
    file, no watcher, and this is what actually scales past a handful of
    clients.
- **FR8.** The limit is a **single time window** (requests per minute) per
  key — no separate burst + sustained dual-window enforcement in this
  exercise.
- **FR9.** Implement and compare, side by side, under **identical load**:
  Fixed Window Counter, Sliding Window Log, Sliding Window Counter, Token
  Bucket, Leaky Bucket.

## Non-Functional Requirements

- **NFR1.** Scale target: ~1,000 distinct API keys, ~5,000 requests/sec peak
  aggregate traffic.
- **NFR2.** The rate limiter's own decision latency (excluding the backend
  call) must be **p99 < 5ms**.
- **NFR3.** Two build phases:
  - Phase 1: single instance, in-memory counters.
  - Phase 2: multiple instances sharing counters via Redis.
- **NFR4.** Phase 1's in-memory counters are **not durable** — a restart
  resets them. Accepted tradeoff, not a defect (real rate limiters routinely
  behave this way; restarts are rare/brief). Phase 2's Redis-backed counters
  persist across app restarts (only a Redis restart resets them).
- **NFR5.** Observability: Prometheus metrics (requests allowed/rejected per
  key and per tier, decision-latency histogram) + a Grafana dashboard.

## Explicit Non-Goals

- Multi-region rate limiting / cross-region consistency — that's problem 12.
- Burst + sustained dual-window limits — single window only here.
- Per-client custom limit overrides beyond the three fixed tiers.
- Authentication/authorization of the config/admin surface itself.
- API key issuance/signup flow — keys are pre-provisioned (file or Redis),
  not created through this system.
- Durability of phase-1 in-memory counters across restarts.

## Open Questions Log

1. **Limit key?** → API key / client ID.
2. **Over-limit behavior?** → Reject with 429 + `Retry-After`.
3. **Tiered limits?** → Yes, three tiers. **Limit shape?** → Single window
   (req/min), not burst+sustained — chosen after comparing all five
   algorithms' tradeoffs (see discussion; captured in FR9 for the actual
   implementation/comparison work).
4. **Deployment model?** → Standalone service in front of the API.
   **Single vs multi-instance?** → Single instance first (phase 1), then
   Redis-backed multi-instance (phase 2).
5. **Concrete scale numbers?** → ~1,000 API keys, ~5,000 req/sec peak.
   **Concrete tier limits?** → Free 60/min, Standard 600/min, Premium
   6,000/min.
6. **Rate limiter's own latency budget?** → p99 < 5ms. **Need a real
   backend to demo end-to-end?** → Yes, minimal stub.
7. **How is key→tier config managed without a restart, given a flat file
   doesn't scale to real client volumes?** → Static file (phase 1, explicitly
   documented as scale-limited) with hot-reload via file watcher; migrates to
   Redis in phase 2, which is the pattern that actually scales to millions
   of keys.
8. **Response headers?** → Yes, standard `X-RateLimit-*` + `Retry-After`.
9. **Counters survive restart?** → No — reset on restart is an accepted
   tradeoff in phase 1; Redis in phase 2 persists naturally.

---

Confirmed. Next: `DESIGN.md` — capacity estimate, architecture, API/data
contracts, and the concrete tradeoffs for choosing/comparing the five
algorithms.
