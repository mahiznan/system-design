# Load test — deferred

Not implemented yet. Build once an algorithm is running end-to-end (Fixed Window
Counter first).

Traffic shape should be modeled on DESIGN.md section 1's capacity estimate:
~5,000 req/sec aggregate across 1,000 keys, split 700 Free / 200 Standard /
100 Premium (60/600/6,000 req/min respectively per PROBLEM.md FR2). Use k6,
hitting `rate-limiter-service` through `X-API-Key`, and confirm p99 decision
latency stays under 5ms (NFR2).
