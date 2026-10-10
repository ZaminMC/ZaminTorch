# Torch Concurrency Decisions and Rollout (Proposal)

**Status: proposed; awaiting architecture review.** Companion to [`CONCURRENCY_ARCHITECTURE.md`](CONCURRENCY_ARCHITECTURE.md) (the design), [`FOLIA_FORENSIC_AUDIT.md`](FOLIA_FORENSIC_AUDIT.md) (the Folia evidence), and [`MINESTOM_CONCURRENCY_BASELINE.md`](MINESTOM_CONCURRENCY_BASELINE.md) (the Torch baseline). This document carries the three deliverables that belong outside the design core: the explicit comparison, the architecture decision records, and the phased rollout.

Every decision below names its evidence and the experiment that would reopen it. No decision describes implemented behavior; Torch currently runs the single-domain engine documented in the baseline.

## 1. Explicit comparison: Minestom/Torch baseline, Folia 26.2.x, proposed Torch

| Dimension | Torch baseline (verified source) | Folia 26.2.x (verified patches) | Proposed Torch |
|---|---|---|---|
| Mutable state ownership | One recorded owner thread for `EngineWorld`; nothing for entities/protocol | Region owning the containing chunk section; global region for non-spatial state | Explicit ownership domains; start world-wide, grow spatial only after protocols exist |
| Physical worker assignment | Single simulation loop thread | Stateless pool threads; region tasks any worker, one at a time (`tryMarkTicking`) | Bounded shared pool; one active task per domain; reassignment only between tasks |
| Partition model | None (single world domain) | Sections → regions, merge radius, structure lock | World-wide first; then region-style partitions behind an interface |
| Tick semantics | One loop, 50 ms target, skipped (not burst) missed ticks | Per-region catch-up (`getPeriodsAhead`), per-region TPS | Preserve current semantics; later local deadlines + explicit global coordination |
| Load balancing | None needed (one domain) | Region merge/split under structure lock | Migration disabled at first; enabled only after drain-handoff tests pass |
| Busy-owner scaling | Whole server stalls | Other regions progress; one region serializes internally | Snapshot compute off-domain (pathfinding, chunkgen, serialization); intra-domain parallelism is research |
| Parallel calculation | None | moonrise chunk-system pools outside tick threads | Bounded compute pools, immutable inputs, versioned results, owner validates |
| Cross-owner mutation | Deferred work queue (`ConcurrentLinkedQueue`) drained at tick start | Intents/updates routed to target region's tick | Immutable intents → target authority validates; exactly-once admission |
| Entity transfer | Not modeled (single domain) | Section re-membership + `EntityScheduler` retarget + generation bump | Same protocol: drain scope → transfer state → publish generation → resume |
| Block/redstone boundaries | N/A | Follow-up patch 0004 drops non-owned updates; ordered owner-queue otherwise | Never silently drop: ordered messages; document any deliberate limitation |
| Chunk generation | Synchronous under world owner | moonrise pools; install under owner | Compute pool over detached chunk data; owner validates + installs |
| Plugin context | Single-threaded assumption throughout | `folia-supported` gate; region/entity/global/async contexts | Owner-context callbacks + explicit entity/location/global/async schedulers; no auto-safety promise |
| Enforcement | Owner-thread check on `EngineWorld` mutations | `TickThread.isTickThreadFor` assertions across hot paths | Ownership tokens + generation checks, debug diagnostics, explicit failures |
| Diagnostics | Rolling TPS, last overrun | Per-region TPS, chunk throughput counters, region profiler, watchdog | Per-domain lag/queue metrics; stalled-domain detection; no aggregate-only TPS |
| Shutdown | Stop the loop | `RegionShutdownThread` drains regions in order | Same drain-order protocol, global last-to-first, quiescent before save |
| Correctness testing | 518 green behavioral tests, none concurrency-specific | None in archive (tests removed patch) | The §11 test list in CONCURRENCY_ARCHITECTURE.md, incl. sequential reference mode |
| Remaining bottlenecks | Single thread (by construction) | One dense region; global-state APIs | Same dense-region limit; mitigated by snapshot compute; measured, not promised |

Unknowns are labeled: Folia's runtime behavior beyond the patches (e.g. actual merge-frequency under load) is **not** claimed; Torch's column describes the proposal, not shipped code.

## 2. Architecture Decision Records

Format: decision, alternatives rejected, rationale, operational consequence, risk, reopening evidence. Status is `Proposed` for all — none are implemented.

### ADR-1. Authoritative mutation unit = ownership domain; initial scope world-wide
- **Rejected:** per-chunk ownership (boundary storms, vanilla cross-chunk semantics break); per-entity (no block world); unrestricted concurrent mutation (races).
- **Rationale:** Folia's region unit exists *because* Minecraft state is block-adjacent; but Folia's own inventory shows the cost (62% of paths are entity/bookkeeping extraction). Starting world-wide preserves today's verified behavior while the enforcement layer proves itself.
- **Consequence:** no parallelism gain until domains split; acceptable — correctness first.
- **Risk:** the world-wide domain becomes a long-lived plateau.
- **Reopen when:** cross-owner protocols + tests are green and a measured workload needs spatial split.

### ADR-2. Chunks, entities, and domains
- **Decision:** domain = contiguous loaded-section set (Folia's shape). Entities belong to the domain owning their containing section; block entities to theirs; entity membership changes only via the transfer protocol (generation bump).
- **Evidence:** Folia `ThreadedRegionizer` §4-A of the audit; `Entity`/`EntityScheduler` hunks.
- **Risk:** passenger/leash groups crossing boundaries; mitigated by group transfer as a unit (Folia patches 0006/0009 evidence).

### ADR-3. Physical scheduling = bounded shared pool, one active task per domain
- **Rejected:** thread-per-domain (violates constraint §2.2); unbounded pools (oversubscription).
- **Rationale:** Folia's own default sizing (cores/2, ÷4 further) treats tick threads as the scarce resource; a shared pool with single-active-task-per-domain gives fairness without ownership transfer.
- **Consequence:** a domain's tick may migrate workers between ticks — legal, since state is domain-owned, not thread-owned.
- **Reopen when:** pool contention is measured between large numbers of runnable domains.

### ADR-4. Work stealing — only whole ready tasks, never live objects
- **Rationale:** Folia runs no live-object stealing; its pool polls region tick tasks. Adopting task-level stealing preserves the single-writer invariant.
- **Risk:** stealing between boundaries could double-tick if tracking is buggy; test: two workers cannot both acquire one domain (audit §7 row 1).

### ADR-5. Tick timing — single-domain semantics first; hybrid later; per-region catch-up
- **Decision:** keep today's skip-missed-ticks loop; when domains split, each domain runs its own 50 ms schedule with `getPeriodsAhead`-style catch-up; global coordination only for world-time/weather/broadcasts.
- **Evidence:** Folia's regional catch-up is the patch-verified behavior (`runTick`); a perfect global barrier is absent even in Folia.
- **Risk:** cross-domain mechanics observe slightly skewed phases — exactly Folia's accepted behavior; vanilla-ordering audit required before enabling (per constraint §2.9 no silent behavior change).

### ADR-6. Initial partition granularity = config-exposed section shift, default huge
- **Evidence:** Folia's `regionShift = 31` default makes the whole world one region until configured; the mechanism is exercised, the default is conservative. Torch mirrors that conservatism.
- **Reopen when:** representative benchmarks show benefit from 2^(31-n) regions.

### ADR-7. Migration protocol = drain → transfer → publish → resume, generation-bumped
- **Evidence:** Folia's shutdown thread and merge/split sequencing (§4-A, §4-G of audit).
- **Risk:** both owners resuming after uncertain transfer; test: fault-injection between every step (§11 list).

### ADR-8. Cross-owner messaging = immutable intents, target-authority validation, exactly-once admission
- **Evidence:** hopper pull-into-owner-region; neighbor-updater target queue (audit §5).
- **Rejected:** distributed two-phase commit as the default (Folia doesn't; latency + deadlock risk); fire-and-forget with implicit retries (duplicate mutations).
- **Consequence:** cross-domain operations carry one-tick latency at boundary — same as Folia.

### ADR-9. Block updates/redstone/physics at boundaries = ordered owner-queue, never skip
- **Evidence:** Folia patch 0004 *does* drop non-owned updates (a deliberate limitation, logged); Torch's stricter rule is ordered delivery because we must not silently change vanilla behavior (constraint §2.9). Where proving ordered delivery for a mechanic is impractical initially, that mechanic stays under a larger exclusive scope instead of being dropped.

### ADR-10. Parallel AI/pathfinding = snapshot compute, versioned results, owner-applied
- **Evidence:** Folia runs AI on region threads (not offloaded); Torch's heavier offload is a deliberate difference enabled by our compute-task contract; the EntityScheduler stale-task evidence covers the same stale-result hazard.
- **Risk:** snapshot staleness; mitigated by generation validation + recompute fallback.

### ADR-11. Intra-domain parallelism (heavy region) = NOT in initial phases
- **Decision:** conflict-aware parallel mutation stays future research (strategy B in the design doc).
- **Rationale:** the assignment's required rule ("parallel calculation does not grant parallel mutation rights") plus the absence of any Folia mechanism for it (their dense region is simply serial) means any claim would be speculative.
- **Reopen when:** sequential reference mode + conflict tests exist and a measured dense-region workload demands it.

### ADR-12. Chunk generation / I/O isolation = separate bounded pools
- **Evidence:** moonrise pools are separate from tick threads in Folia; baseline shows generation currently synchronous (a future Phase-3 target).

### ADR-13. Global state = global domain (not global lock)
- **Evidence:** Folia routes commands/scoreboards/teams/bossbars to the global region — the acknowledged bottleneck they did not partition. Torch adopts the same shape and documents the same limitation rather than pretending partitioning exists.

### ADR-14. Plugin contract = owner-context callbacks + four explicit schedulers
- **Evidence:** Folia's `folia-supported` gate proves legacy assumptions are unsafe; Torch's native API will expose entity/location/global/async scheduling with explicit contexts. Legacy compatibility layers, if built, run in a serialized compatibility context with documented limits.

### ADR-15. Enforcement = ownership tokens + generations + assertions at stated points
- Where: every `EngineWorld` mutation entry, entity state access from outside owner context, block-entity inventory ops, compute-result application, scheduled-task execution. Debug mode enables full checks; production keeps generation checks (cheap) and samples the rest.
- **Evidence:** Folia's `TickThread.isTickThreadFor` placements are the reference for "where assertions belong".

### ADR-16. Diagnostics = per-domain metrics + watchdog thread
- **Evidence:** Folia's per-region TPS, chunk throughput counters, region profiler, watchdog thread (patches 0003/0007/0008, api 0004). Aggregate-only TPS is explicitly insufficient (constraint §11).

### ADR-17. Shutdown = ordered drain (global last), quiescent-before-save
- **Evidence:** `RegionShutdownThread`.

### ADR-18. Determinism = deterministic within a domain; cross-domain ordering defined per mechanic; RNG owned by the domain
- No shared global RNG (constraint §16); per-domain random streams owned by that domain; cross-domain conflict resolution defined per mechanic (first-ordered-intent-wins at the router).

### ADR-19. Performance measurement = workload matrix before claims
- The eight workloads from the design doc (§11) each get a reproducible scenario; no scalability claim ships without its measurement. This ADR exists to make "no speculative performance promises" enforceable in review.

### ADR-20. Minestom compatibility = no dependency today (ADR-0001 stands)
- The baseline verified no Minestom dependency; the concurrency architecture does not change that. Reconsider only with concrete protocol/world integration evidence, as recorded in the design doc's decision table.

## 3. Engineering rollout (§21)

| Phase | Goal | Prerequisites | Design components | Expected behavior | Tests to pass | Evidence required | Failure criteria / rollback | Excluded |
|---|---|---|---|---|---|---|---|---|
| 0 — Architecture & evidence | This document set: audits, inventory, diagrams, decisions, test plan | none | all docs | reviewable proposal | — | Folia archive analysis (done), baseline (done) | docs rejected → revise | any runtime change |
| 1 — Ownership primitives | Ownership tokens, generations, assertions, task context | Phase 0 approved | enforcement layer; `EngineWorld` owner checks formalized | existing single-domain behavior, now enforced | wrong-owner mutation fails w/ context; stale generation rejected | assertion coverage report | behavior change vs today → revert assertions to log-only | spatial split |
| 2 — Scheduler integration | Task-based simulation loop on bounded pool; one-active-task-per-domain | Phase 1 tests green | scheduling model (design §5) | identical gameplay on the new loop | all 518 behavioral tests + cancellation/shutdown/no-resurrect tests | tick-latency metrics vs old loop | TPS regression → rollback to loop | multi-domain |
| 3 — Supporting computation | Pathfinding + chunk-gen + serialization off-thread | Phase 2 green | compute subsystem (design §6) | offloaded work returns versioned results; stale discarded | stale-result tests; pool-starvation tests | before/after workload measurement for each offload | stale-result misapplication → disable offload flag | redstone/piston offload |
| 4 — Partitioned simulation | Spatial domains with migration + cross-owner protocol | Phase 3 + migration fault-injection green | partition model; transfer protocol; router | multiple domains progress in parallel; boundary mechanics correct | §11 cross-boundary test list (hopper, piston, redstone, projectile, entity transfer, explosion) | measured multi-domain tick independence | any vanilla-mechanic divergence → scope mechanic to larger domain (ADR-9) | dynamic auto-balancing |
| 5 — Cross-boundary completeness | Full mechanic coverage across domains | Phase 4 | per-mechanic protocols | all §10.3 mechanics specified and tested | mechanic-by-mechanic inventory grows to full §10.3 list | per-mechanic test map | gap → mechanic stays under exclusive scope, ledgered | plugin API freeze |
| 6 — Heavy-owner optimization | Measured safe parallelism inside dense domains | Phase 5 green + dense-region benchmark shows need | compute strategies A→B (design §6) | dense region's compute overlaps with authoritative apply | conflict-detection tests; sequential-reference equivalence | benchmark delta ≥ threshold w/o correctness failures | any ordering violation → strategy B stays research | speculative multi-writer |
| 7 — Adaptive scheduling | Evidence-gated migration/split/merge | Phase 6 | migration protocol live | load-driven, oscillation-guarded rebalancing | migration-with-in-flight-tasks tests; oscillation damping | benchmark comparison vs Phase 5 baseline | instability → default granularity (ADR-6) | perf promises |

Dependencies are strictly ordered; no phase activates systems whose boundary protocols are untested (assignment §21 rule). Each phase's rollback is the previous phase's configuration.
