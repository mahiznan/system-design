# Design — 01 Rate Limiter

> Status: **DRAFT.** Written against the locked requirements in `PROBLEM.md`.

## 1. Capacity Estimate

<!--
Back-of-envelope math, derived from PROBLEM.md's NFR1 (~1,000 keys, ~5,000 req/sec
peak) and the tier limits (FR2). Think through things like:
- Peak vs average QPS the rate-limiter service itself must handle
- Memory footprint per key for each of the 5 algorithms (FR9) at 1,000 keys —
  this is where sliding-window-log's O(n) cost should show up as a real number
- Redis ops/sec once phase 2 introduces it as the shared store
- Network/bandwidth if relevant
-->

## 2. Architecture

<!--
High-level component diagram (ASCII or Mermaid) and a short walkthrough.
Should cover both phases from NFR3:
- Phase 1: single instance, in-memory counters
- Phase 2: multiple instances, Redis-backed shared counters
Show where the stub backend (FR6) sits relative to the rate limiter.
-->

## 3. API Contracts

<!--
The externally visible contract: request format, response codes (FR3),
response headers (FR4), and the admin/config surface if any (see FR7 —
does phase 1 need an endpoint, or is the file watcher fully sufficient?).
-->

## 4. Data Model

<!--
Per-key state each algorithm needs to track (FR9), and how that maps onto:
- Phase 1: in-process data structures
- Phase 2: Redis data structures (keys, types, TTLs)
Also cover the key→tier mapping's shape (FR7) in both phases.
-->

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
