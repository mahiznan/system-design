# System Design Mastery — Learn by Building

A hands-on curriculum for going from "I can whiteboard a system design interview" to
"I have personally built, load-tested, and broken every major distributed-systems
pattern on my own laptop." Built for a backend engineer with 15 years in the Java
ecosystem who wants depth, not slides.

## Philosophy

- **You design first, then we build together, then we break it on purpose.** Every
  problem ends with a load test and a chaos test — a green test suite alone does not
  count as understanding.
- **Every folder is a complete, working, runnable system**, not a design doc. Six
  months from now you should be able to `cd` into any folder, run three scripts, and
  have a real system running in front of you to re-study.
- **No shared/ folder at root.** Each problem folder is fully self-contained —
  duplicate infra setup (Docker, observability, load-test scaffolding) on purpose.
  Rebuilding the boring plumbing every time is part of how it becomes muscle memory
  instead of a copy-pasted template you never really understood.
- **Progressive complexity.** Each tier assumes everything in the tiers before it.
  Don't skip ahead — the later distributed-systems problems lean hard on intuition
  built in the earlier ones.

## Tech stack decision (and why)

**Java 25 (LTS) + Spring Boot is the default for every problem, application-layer
code included.** No language is swapped in "for variety." If a problem seems to
demand something Java can't do well, that's a design smell worth noticing, not a
reason to reach for another language:

- High-concurrency simulations (thousands of simulated clients/drivers/connections)
  → **Java 25 virtual threads (Project Loom)**, not a switch to Go. This is itself a
  first-class learning objective: understanding when virtual threads remove the
  need for reactive/callback gymnastics, and where they don't.
- Consensus (Raft), gossip protocols, CRDTs → implemented in plain Java. The
  algorithm is the point, not the host language.

**Infrastructure is real, via Docker Compose, from problem 1 onward** — Postgres,
Redis, Kafka, Prometheus, Grafana, Toxiproxy. These are *tools we run in containers*,
not application code we write, so they don't count as "polyglot" — but you should
know what each one is doing, not just that `docker-compose up` makes it work.

Tools used as infrastructure (not written by us, run as containers):

| Purpose | Tool |
|---|---|
| Relational store | PostgreSQL |
| Cache / fast KV | Redis |
| Event streaming | Kafka (KRaft mode, no ZooKeeper) |
| Search tier reference point | OpenSearch (for comparison in problem 15 only) |
| Metrics | Prometheus + Micrometer |
| Dashboards | Grafana |
| Load testing | k6 |
| Network fault injection (chaos) | Toxiproxy + `docker network`/`docker pause` |

## Running this on a MacBook Pro M1 (16GB / 10-core)

You explicitly want realistic traffic and failure simulation, not toy demos — that's
achievable on this hardware, but only if you're deliberate about resource usage:

1. **Prefer [OrbStack](https://orbstack.dev/) over Docker Desktop.** Same Docker API,
   dramatically lower idle memory/CPU overhead on Apple Silicon — matters when you're
   running Postgres + Redis + Kafka + Prometheus + Grafana simultaneously.
2. **Run one problem's stack at a time.** `docker-compose down -v` the previous
   problem before `run-local.sh` on the next. Nothing here is designed to run
   concurrently across folders.
3. **Every `docker-compose.yml` sets explicit memory/CPU limits** per container
   (`deploy.resources.limits`), sized for a 16GB machine — typically capping the
   whole stack around 6–8GB so your IDE and browser survive.
4. **Cluster sizes are realistic-but-small**: 3–5 node clusters for
   consensus/replication problems, not 20. You'll see the same failure modes as a
   large cluster (split brain, quorum loss, partition) at a fraction of the RAM.
5. **k6 for load testing** — a single lightweight Go binary, not a JVM-based load
   tool competing with your own JVM app for the same cores.

## Standard folder structure

Every problem folder — once you start it — looks like this:

```
NN-problem-slug/
├── PROBLEM.md              # The problem statement itself. Starts as a deliberately
│                           # incomplete/ambiguous prompt (like a real stakeholder ask
│                           # or interview question), then gets edited in place as we
│                           # discuss and clarify — until it's locked and confirmed.
├── DESIGN.md              # Written AFTER PROBLEM.md is confirmed. Capacity estimate,
│                           # architecture, API/data contracts, tradeoffs considered,
│                           # failure modes, and a retro checklist filled in at the end.
├── docker-compose.yml      # Full local topology: app + datastores + observability
├── build-local.sh          # Builds the app (Maven/Gradle) and any Docker images
├── run-local.sh             # Brings the stack up, seeds data, prints endpoints/ports
├── test-local.sh            # Default: unit + integration tests
│                           #   --load  : runs the k6 load-test scenario
│                           #   --chaos : runs the fault-injection scenario
├── src/                     # Application source
├── load-test/               # k6 scripts (traffic shape modeled on the DESIGN.md capacity estimate)
├── chaos/                   # Fault scenarios: kill a node, add latency, partition the network
└── observability/            # Prometheus scrape config + Grafana dashboard JSON
```

## Workflow per problem

1. **I give you an initial problem statement**, written into `NN-problem-slug/PROBLEM.md`.
   It's deliberately high-level and a little ambiguous at first — the way a real
   stakeholder ask or an interview prompt actually arrives, not a fully-specified
   spec.
2. **We discuss it.** You ask the clarifying questions a senior engineer should ask
   before designing anything: expected scale, read/write ratio, consistency needs,
   latency targets, what's explicitly out of scope, edge cases. I answer as the
   stakeholder/interviewer would.
3. **I update `PROBLEM.md` in place** as we converge — functional requirements,
   non-functional requirements (with real numbers, not "high scale"), constraints,
   and explicit non-goals. This keeps going until you confirm it's locked.
4. **Only once `PROBLEM.md` is confirmed** do you write your own first draft of
   `DESIGN.md` — capacity estimate (QPS, storage, bandwidth) derived from the
   confirmed requirements, API contracts, data model, architecture, and the
   tradeoffs you're choosing between.
5. We review your design together — I'll push on edge cases and scale numbers like
   a staff engineer in a design review, not hand you a finished architecture.
6. We implement it — you write it, I pair on it, catch correctness/production
   issues as they come up.
7. `build-local.sh` → `run-local.sh` → `test-local.sh` (functional first).
8. `test-local.sh --load` — does it hold up at the traffic level your own capacity
   estimate predicted?
9. `test-local.sh --chaos` — kill a node / add latency / partition the network. Does
   it fail the way you designed it to fail, or does it fail some other way?
10. Fill in the **retro checklist** at the bottom of `DESIGN.md`:
    - Could this handle 10x the traffic? What breaks first?
    - How would you shard, replicate, or cache this further?
    - What did the chaos test reveal that the design didn't anticipate?
11. Check it off below. Move to the next problem.

---

## Progress Tracker

### Tier 1 — Foundations (single node)
- [ ] 01 — Rate Limiter
- [ ] 02 — URL Shortener
- [ ] 03 — Embedded Key-Value Store

### Tier 2 — Single-Service Depth (caching, async, resilience)
- [ ] 04 — Distributed Cache with Consistent Hashing
- [ ] 05 — Distributed Job Queue / Scheduler
- [ ] 06 — Pub/Sub Notification Fan-Out
- [ ] 07 — API Gateway with Rate Limiting & Circuit Breaking

### Tier 3 — Distributed Systems Core
- [ ] 08 — Distributed Lock Manager
- [ ] 09 — Raft Consensus (from scratch)
- [ ] 10 — Sharded Database Proxy
- [ ] 11 — Event Sourcing + CQRS

### Tier 4 — Production-Grade Distributed Concerns
- [ ] 12 — Multi-Region Rate Limiter (Gossip + CRDTs)
- [ ] 13 — Change Data Capture + Streaming Pipeline
- [ ] 14 — Distributed Blob Storage (mini S3/GFS)
- [ ] 15 — Search Engine / Inverted Index with Sharding

### Tier 5 — Expert / Capstone
- [ ] 16 — Real-Time Chat System
- [ ] 17 — Real-Time Geo-Matching (Uber-dispatch style)
- [ ] 18 — Capstone: Multi-Region E-Commerce Platform

---

## The Problems

### Tier 1 — Foundations

#### 01 — Rate Limiter
Build a rate limiter as a standalone service, single node, in-memory first, then
Redis-backed. Implement and compare token bucket, leaky bucket, fixed window, and
sliding-window-log algorithms head-to-head under the same load test.
**Core concepts:** algorithmic tradeoffs (memory vs precision vs burst tolerance),
Redis atomic operations (Lua scripts / `INCR`+`EXPIRE`), clock skew.
**Prerequisites:** none.
**You'll be strong in:** the exact mechanics of every rate-limiting algorithm you've
ever namedropped in an interview, and *why* a real system picks one over another.

#### 02 — URL Shortener
A classic for a reason: REST API design, ID-generation strategy (base62 counter vs
hash vs Snowflake-style ID), read-heavy caching, and basic schema/index design under
a read:write ratio you define yourself.
**Core concepts:** ID generation at scale, cache-aside pattern, DB indexing,
idempotency on writes.
**Prerequisites:** none.
**You'll be strong in:** the full lifecycle of a simple CRUD service done *properly*
— caching strategy, index design, and capacity math, not just a working endpoint.

#### 03 — Embedded Key-Value Store
Build your own single-node KV store from raw bytes on disk: write-ahead log for
durability, an in-memory memtable, periodic flush to an SSTable-like structure,
crash recovery on restart.
**Core concepts:** WAL, LSM-tree fundamentals, durability vs throughput tradeoff,
crash recovery.
**Prerequisites:** none.
**You'll be strong in:** what's actually happening inside every database you've ever
operated — this demystifies Postgres/RocksDB/Cassandra internals permanently.

### Tier 2 — Single-Service Depth

#### 04 — Distributed Cache with Consistent Hashing
Extend problem 03's storage knowledge into a 3–5 node cache cluster. Implement
consistent hashing with virtual nodes, replication factor, and a client-side or
proxy-based router.
**Core concepts:** consistent hashing, virtual nodes, hot-key handling, replication.
**Prerequisites:** 03 (storage engine basics).
**You'll be strong in:** exactly how Memcached/Redis Cluster/DynamoDB distribute
data, and why rebalancing is expensive.

#### 05 — Distributed Job Queue / Scheduler
A mini Sidekiq/Celery: multiple workers pull jobs from a shared queue, with retries,
exponential backoff, dead-letter queue, and exactly-once-ish processing via
idempotency keys.
**Core concepts:** at-least-once delivery, idempotency, backoff/retry policy,
worker pool sizing (a natural fit for Java 25 virtual threads).
**Prerequisites:** none directly, but easier after 01–03.
**You'll be strong in:** the delivery-guarantee vocabulary (at-most-once /
at-least-once / effectively-once) as something you've implemented, not memorized.

#### 06 — Pub/Sub Notification Fan-Out
Given one event (e.g., "order placed"), fan out to email/SMS/push simulators via
Kafka. Handle slow consumers, backpressure, and partial failure of one downstream
channel without blocking the others.
**Core concepts:** Kafka partitioning, consumer groups, backpressure, fan-out
patterns.
**Prerequisites:** 05 (async processing mental model).
**You'll be strong in:** designing decoupled, backpressure-safe event-driven
systems instead of ad-hoc "call three APIs and hope" fan-out code.

#### 07 — API Gateway with Rate Limiting & Circuit Breaking
Build a gateway in front of two or three of your earlier services: routing, your
own rate limiter (problem 01) wired in for real, circuit breaker + bulkhead pattern
for a downstream that's deliberately made to fail/slow down in chaos tests.
**Core concepts:** circuit breaker, bulkhead isolation, timeout budgets, request
routing.
**Prerequisites:** 01, ideally 05/06 as the downstream services.
**You'll be strong in:** resilience patterns as running code you can point to,
not just a Netflix Hystrix diagram you've seen once.

### Tier 3 — Distributed Systems Core

#### 08 — Distributed Lock Manager
Build a lock service (think: a minimal ZooKeeper/etcd lock) with leases, fencing
tokens, and automatic release on client/node failure. Prove the classic "lock
without fencing token = data corruption" failure mode, then fix it.
**Core concepts:** leases, fencing tokens, split-brain, session-based locks.
**Prerequisites:** 04 (distributed node thinking).
**You'll be strong in:** why "just use a distributed lock" is never the whole
answer, and how real systems (etcd, Chubby, ZooKeeper) actually make it safe.

#### 09 — Raft Consensus (from scratch)
The deep one. Implement leader election, log replication, and safety guarantees of
Raft, from the paper, in Java. Test it by killing leaders mid-write and partitioning
the cluster.
**Core concepts:** leader election, log replication, quorum, term numbers, safety
vs liveness.
**Prerequisites:** 08 (leader/lease intuition).
**You'll be strong in:** consensus — the single most load-bearing concept in
distributed systems (etcd, Kafka's controller, CockroachDB, Kubernetes all run on
this). Building it yourself is the difference between citing Raft and understanding it.

#### 10 — Sharded Database Proxy
Build a proxy layer over multiple Postgres instances that shards by key, using your
consistent-hashing knowledge from problem 04. Support resharding without downtime
and detect/handle hot shards.
**Core concepts:** horizontal partitioning strategies, resharding, hot-shard
detection, cross-shard query limitations.
**Prerequisites:** 04, 08.
**You'll be strong in:** the real operational pain of sharding — why it's a last
resort in system design interviews, and what breaks (joins, transactions, secondary
indexes) when you do it.

#### 11 — Event Sourcing + CQRS
Build an order/inventory system where the event log is the source of truth, and
read models are projections rebuilt from that log. Separate write path (command)
from read path (query) fully.
**Core concepts:** event store, projections, eventual consistency, replaying
history, command/query separation.
**Prerequisites:** 06 (event-driven fan-out), 09 helps but isn't required.
**You'll be strong in:** designing systems where "what happened" is durable and
auditable by construction — a pattern that shows up constantly in finance, e-commerce,
and audit-heavy domains.

### Tier 4 — Production-Grade Distributed Concerns

#### 12 — Multi-Region Rate Limiter (Gossip + CRDTs)
Take problem 01 and make it correct across regions with only eventually-consistent
counters. Implement a gossip protocol for state propagation and a CRDT (e.g.,
PN-Counter) so nodes converge without a central coordinator.
**Core concepts:** gossip protocols, CRDTs, eventual consistency,
convergence guarantees.
**Prerequisites:** 01, 09 (consensus contrast — CRDTs are the "no coordination
needed" alternative to Raft).
**You'll be strong in:** the CAP-theorem tradeoff as something you've built both
sides of — strongly consistent (Raft, problem 09) vs eventually consistent (CRDTs,
here) — and when each is the right call.

#### 13 — Change Data Capture + Streaming Pipeline
Wire Debezium to a Postgres instance to capture row-level changes into Kafka, then
build a consumer that keeps a search index / cache in sync in near-real-time.
Handle out-of-order events and exactly-once semantics.
**Core concepts:** CDC, log-based replication, exactly-once processing, schema
evolution.
**Prerequisites:** 06 (Kafka fundamentals).
**You'll be strong in:** how modern data pipelines keep multiple systems in sync
without dual writes — the pattern behind most "keep Elasticsearch in sync with
Postgres" production architectures.

#### 14 — Distributed Blob Storage (mini S3/GFS)
Build a simplified object store: chunking large files, a metadata service tracking
chunk locations, replication across storage nodes, and reconstruction on node
failure.
**Core concepts:** chunking, metadata/data separation, replication vs erasure
coding tradeoff, rebalancing on node loss.
**Prerequisites:** 03 (storage engine), 09 (replication/consensus for metadata).
**You'll be strong in:** the architecture underneath S3/GFS/HDFS — why metadata and
data are split, and how large-file storage differs fundamentally from your KV store
in problem 03.

#### 15 — Search Engine / Inverted Index with Sharding
Build your own inverted index (tokenize, index, rank via TF-IDF/BM25) as a sharded
service, then run the identical query workload against OpenSearch and compare.
**Core concepts:** inverted index, relevance ranking, index sharding, query
fan-out/scatter-gather.
**Prerequisites:** 10 (sharding), 03 (storage).
**You'll be strong in:** what's actually inside Elasticsearch/Solr/Lucene, and
scatter-gather query patterns used by every sharded search system.

### Tier 5 — Expert / Capstone

#### 16 — Real-Time Chat System
WebSocket-based chat with guaranteed message ordering per conversation, delivery
receipts, multi-device sync, and offline message delivery on reconnect.
**Core concepts:** WebSocket connection management at scale, ordering guarantees,
presence, offline-sync design.
**Prerequisites:** 05, 06, 11 (event log as the sync mechanism).
**You'll be strong in:** the hard real-time-messaging problems (ordering, presence,
multi-device) that sit behind WhatsApp/Slack/Messenger.

#### 17 — Real-Time Geo-Matching (Uber-dispatch style)
Match riders to nearby drivers in real time: geospatial indexing (geohash/quadtree),
real-time location updates at scale (Java 25 virtual threads simulating thousands of
moving drivers), and a matching algorithm under load.
**Core concepts:** geospatial indexing, real-time state management, matching
algorithms, high-concurrency simulation.
**Prerequisites:** 04, 10, 12.
**You'll be strong in:** geo-distributed real-time systems — a category system
design interviews love and most candidates have never actually built.

#### 18 — Capstone: Multi-Region E-Commerce Platform
Integrate the building blocks from every prior tier into one deployable system:
gateway (07) in front of a sharded catalog (10), cache (04), search (15), order
processing via event sourcing (11), CDC keeping search in sync (13), and
notifications (06) — deployed across simulated regions with chaos testing across
the whole system, not just one service.
**Core concepts:** everything above, integrated; failure isolation across service
boundaries; end-to-end capacity planning.
**Prerequisites:** all of tiers 1–4.
**You'll be strong in:** taking isolated patterns you now know cold and composing
them into one coherent, production-shaped system — the actual job of a system
architect.

---

## Next step

Tell me when you're ready to start **01 — Rate Limiter**, and I'll open with an
initial `01-rate-limiter/PROBLEM.md` for us to discuss and lock down before any
design or code work begins.
