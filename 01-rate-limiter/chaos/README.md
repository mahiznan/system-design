# Chaos test — deferred

Not implemented yet. Phase 1 has no distributed failure modes to inject (single
in-memory instance) beyond killing/restarting the process to confirm counters
reset as expected (NFR4's accepted tradeoff). Real chaos scenarios (Redis
unavailability, network partition between instances, clock skew) apply once
phase 2 is built — see DESIGN.md section 6, currently a template.
