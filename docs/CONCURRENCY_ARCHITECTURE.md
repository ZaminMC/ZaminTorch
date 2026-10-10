# Torch Concurrency Architecture (Proposal)

**Status: proposed; awaiting architecture review.** This document records the first evidence-based design direction, not implemented behavior. It is intentionally conservative. The complete Folia patch inventory and method-by-method audit remain outstanding; see the worklog.

## 1. Verified baseline

The inspected `develop` branch is a Java 21 Gradle multi-module project:

- `zamin-api`: public API, with no implementation dependencies.
- `zamin-core`: authoritative world and simulation logic.
- `zamin-protocol-v1_8_8`: protocol-47 networking on Netty.
- `zamin-launcher`: runnable distribution and update check.

The current build does **not** declare Minestom. `docs/ENGINEERING_PLAN.md` records Minestom as deferred (ADR-0001), citing the mismatch between its modern protocol assumptions and the 1.8.8 protocol, plus the risk of creating a second authoritative world model. Any future Minestom integration must be treated as a separate, evidence-driven decision.

Current execution facts from source:

- `EngineTicker` owns one simulation loop and calls deferred work, world time advancement, and the tick handler sequentially.
- `EngineTicker.submit` accepts cross-thread submissions into a `ConcurrentLinkedQueue`; work is drained at the start of a tick.
- `EngineWorld` records an owner `Thread` and checks ownership on mutation paths. World reads and chunk-map publication use concurrent structures, but the mutable `EngineChunk` objects are not thereby safe for concurrent writes.
- `EngineWorld.getOrGenerate` currently generates and prepares chunks on the caller's owner thread, then publishes the completed chunk.
- `EngineTicker` skips missed ticks rather than burst-catching up. Its aggregate TPS metric is not enough to diagnose a future multi-owner scheduler.

These facts describe the inspected source, not a claim that all paths are already race-free. A complete call-site audit is still required.

## 2. Recommendation

Adopt **single-writer ownership domains, a shared bounded simulation worker pool, explicit asynchronous messages, and snapshot-based compute tasks**. Begin with one domain per world matching today's single-owner behavior. Introduce spatial ownership only after cross-owner protocols exist and have tests.

Do not start by allowing multiple workers to mutate one domain. Parallelism must first come from independent domains and calculations that have no authority to mutate live state.

### Vocabulary

- **Worker:** physical JVM thread executing a task.
- **Ownership domain:** logical authority allowed to mutate a defined set of live state.
- **Simulation task:** ordered state transition executed by its authoritative domain.
- **Compute task:** calculation over immutable input; it cannot mutate live state.
- **Service task:** storage, compression, or other supporting work.
- **Ownership generation:** monotonically increasing version changed whenever ownership transfers.
- **Intent:** immutable request to perform a mutation through the authority that owns the target.
- **Snapshot:** bounded immutable data captured for computation or observation.

A worker is not an owner. A domain may execute on different workers over time, but it must never have two simultaneous mutation authorities.

## 3. Non-negotiable invariants

1. Every mutable live object has one authoritative mutation domain at a time.
2. A Java reference is not permission to read or mutate an object.
3. Concurrent collections protect collection operations only, not mutable values stored inside them.
4. Compute workers receive immutable DTOs/snapshots, never unrestricted live entity, chunk, inventory, or block-entity objects.
5. A result is applied only by its authority after validating owner generation and relevant input versions.
6. Cross-domain gameplay is not silently dropped because it crosses a boundary.
7. Simulation workers never block waiting for work that requires another simulation worker to progress.
8. Queues are bounded or have explicit overload behavior. Authoritative gameplay intents must not be silently discarded.
9. Task ordering is explicit within each owner; physical worker assignment is not part of game semantics.
10. Ownership transfer has a single-writer handoff point; old-generation tasks and results are rejected or safely rerouted.
11. Failure is observable. A timeout is not permission to forget a gameplay operation.
12. No performance claim is accepted without reproducible workload measurements.

## 4. State ownership map

| State | Initial authority | Cross-domain rule |
|---|---|---|
| World clock, weather, game rules | World-level coordinator initially | Publish immutable observations; changes enter a defined world-level command queue |
| Chunk block states and block entities | Domain owning the chunk | All writes route to owner; block-entity inventories follow the block entity's owner |
| Entity lifecycle and mutable entity state | Domain currently owning the entity | Transfer protocol changes ownership generation and prevents double ticking |
| Player inventory, menu and player state | Player/entity owner | Container operations involving a second owner use an explicit inventory transaction |
| Entity collision and damage | Authoritative simulation coordinator for the interaction; initially same-owner only | Cross-owner hits become validated intents; never mutate target directly |
| Neighbor updates, redstone, pistons, fluids | Owner of each affected block position | Ordered propagation messages; mechanics must preserve required update order |
| Scheduled block/fluid ticks | Owner of target block/chunk | Deadline and sequence metadata migrate with ownership; stale generations reroute or reject safely |
| Entity tracking and replication | Derived observation state; owner produces authoritative updates | Network output is queued from immutable packets/snapshots; no packet thread mutates simulation |
| AI goal state and navigation state | Entity owner | Off-thread path calculation uses snapshots and returns a versioned candidate path |
| Chunk generation and lighting calculation | Bounded compute pool over detached chunk data | Owner validates world/chunk generation and installs the complete result |
| Persistence | Owner captures a consistent immutable save snapshot; bounded I/O service writes it | Save completion cannot overwrite newer live state |
| Plugin-owned mutable data | Plugin contract; no implicit cross-thread safety | Callbacks inherit documented owner context; shared plugin state is plugin responsibility |
| Global registries (players, teams, scoreboards) | Explicit service/coordinator chosen per subsystem | Avoid a universal global lock; use immutable snapshots or narrowly scoped transactions |

## 5. Scheduling model

Use a bounded pool sized from effective CPU availability, not merely the host's reported logical CPU count. A simulation task runs to a defined scheduling boundary and then yields; arbitrary preemption in the middle of a vanilla state transition is not safe.

Initial scheduler policy:

- One runnable simulation task per ownership domain at a time.
- Ready tasks are scheduled fairly across domains; an overloaded domain cannot occupy every worker with a chain of non-yielding tasks.
- Delayed work is stored by logical deadline and stable sequence number, then made runnable by its owner.
- Compute and blocking-I/O work use separate bounded admission queues/pools so storage stalls cannot consume all simulation workers.
- Backpressure is explicit and measured. Non-authoritative diagnostics may be sampled or dropped; authoritative state transitions may not be silently dropped.
- Worker reassignment is allowed only between tasks, never as a substitute for ownership transfer.
- Work stealing may take an eligible *domain task* from a shared ready queue, but cannot steal live objects or execute two tasks for the same domain concurrently.

The first implementation should retain the current single world owner until the scheduler and ownership assertions are independently testable. Do not combine scheduler replacement, spatial partitioning, and gameplay rewrites in one step.

## 6. Heavy-domain parallelism

Two strategies were considered:

### A. Parallel calculation, serialized authoritative application (recommended first)

Capture a bounded immutable snapshot, compute independently, then send a result to the owning domain. The owner validates the ownership generation and relevant versions before applying the result in a deterministic order.

Useful first candidates are pathfinding, navigation candidate generation, chunk generation, lighting over detached chunk data, serialization, and compression. Each candidate must be audited individually.

This does **not** parallelize order-dependent live mutations, collision resolution, or redstone propagation. A stale result is discarded and recomputed or handled by a documented fallback; it is never blindly applied.

### B. Conflict-aware parallel mutation (future research)

Allow multiple calculations to propose writes with explicit read/write sets, then validate conflicts and commit disjoint changes. This is only viable for systems whose effects are genuinely independent and whose ordering semantics are defined. It is not the default for entity movement, block updates, inventories, or redstone. The validation/commit cost must be measured against serialized execution.

Do not introduce speculative multi-writer simulation until a sequential reference mode and conflict tests exist.

## 7. Cross-owner protocol

A cross-owner request carries: operation ID, source domain/generation, target identity/position, target generation when known, immutable payload, logical sequence/deadline, and cancellation/lifecycle metadata.

1. The source creates an immutable intent; it does not mutate target state.
2. The router resolves the current target owner and queues the intent.
3. The target validates target existence, ownership generation, permissions, and current gameplay preconditions.
4. The target applies the mutation in its own order, or starts a narrowly scoped asynchronous transaction if atomicity genuinely requires multiple owners.
5. The result is sent asynchronously to the source. No simulation worker blocks for it.
6. Delivery is at-least-once only if the operation has an idempotency key and duplicate handling; otherwise the router must provide exactly-once admission within its documented lifecycle.
7. Stale messages are rerouted only when semantics permit. They are not blindly replayed after a target changes.
8. Queues have limits and diagnostics. Saturation must apply explicit backpressure or fail the originating action with a visible, mechanics-specific result, never silently lose a block update.

### Required hard cases

- **Hopper inventory transfer:** each inventory has an owner. The transfer is an atomic debit/credit transaction with stable operation ID and reservations or a deterministic coordinator. Never remove an item in owner A and hope owner B accepts it later.
- **Piston across a boundary:** discover and validate the complete moved-block set before mutation. Route required changes to affected owners under a coordinated ordered operation; commit or abort as a unit. Do not silently truncate the push at a partition edge.
- **Entity crossing while AI computes:** the entity transfer increments its generation. A path result tagged with the old generation is rejected. The new owner decides whether to recompute against a fresh snapshot.
- **Redstone/neighbor updates:** preserve source-defined ordering and delayed-tick semantics with sequenced owner messages. Cross-domain propagation cannot be replaced by “skip if not owned.”
- **Projectile hits:** the projectile owner submits a hit intent; the target owner validates the target and impact preconditions. Damage, knockback, and projectile consumption must have a single operation identity to prevent duplicates.

Until those protocols exist and pass integration tests, keep affected mechanics under a larger exclusive ownership scope.

## 8. Tick semantics

Do not impose a global barrier every tick. Initially, one world-level domain retains the current logical tick behavior. When independent domains are introduced, choose a documented hybrid: local simulation deadlines plus explicit global-state coordination. Do not claim all domains are on one perfectly synchronized tick unless the implementation actually enforces it.

For each owner, record scheduled deadline, queue wait, execution duration, completion lag, missed/late ticks, and oldest queued authoritative intent. Aggregate TPS must not hide a single stalled domain. Wall-clock time, logical game time, and asynchronous completion time are separate concepts.

Before splitting a world into independently progressing domains, audit vanilla ordering dependencies involving time/weather, entity and block ticks, random ticks, redstone, and packet publication. If a mechanic requires a global ordering guarantee, preserve that guarantee locally or coordinate the affected operation explicitly.

## 9. Ownership transfer

Migration is a protocol, not a map update:

1. Mark the old owner as draining for the transferred scope.
2. Stop admitting new tasks for that scope; queued tasks are held or tagged with the old generation.
3. Finish or cancel in-flight authoritative work at a defined safe point.
4. Capture and transfer the complete mutable state plus scheduled work and identity/index membership.
5. Publish the new owner and increment generation atomically from the router's perspective.
6. Route new messages to the new generation; reject or revalidate stale compute results.
7. Resume scheduling only after the new owner confirms installation.
8. Keep failure recovery explicit: do not allow both owners to resume after an uncertain transfer.

Start with migration disabled. Enable it only after fault-injection tests prove single-writer safety and after profiling shows a measurable benefit.

## 10. Plugin contract

Native callbacks should run in the owner context associated with the event or entity and may mutate only state owned by that context. APIs for entity-, location-, and global-scope tasks must make the target context explicit. Long-running calculations receive immutable data and return results through an owner task.

Do not promise automatic thread safety for Minestom, Bukkit, or Paper plugins. Legacy compatibility callbacks may require a serialized compatibility context with explicit limits; that compatibility behavior is separate from native Torch semantics.

## 11. Observability and acceptance

Minimum metrics: per-domain tick execution and lag, ready-queue depth/wait, per-pool saturation, cross-domain intent rate/latency/rejection, stale compute results, migration duration/failures, snapshot bytes/time, generation/load/save latency, and blocked-wait detection.

Required initial tests:

- wrong-owner mutation fails with domain and operation context;
- stale ownership generation is rejected;
- one domain cannot execute two simulation tasks concurrently;
- cancellation and shutdown do not resurrect tasks;
- queues cannot grow without bound;
- a stale path result is never applied after movement/transfer;
- hopper transfers cannot duplicate or lose items when the receiver rejects or changes generation;
- piston and redstone boundary tests preserve ordering and do not drop updates;
- overloaded domains do not prevent unrelated ready domains from progressing;
- no simulation worker blocks on a future that requires the simulation pool.

Benchmark single-hotspot, distributed players, redstone, entity-heavy, chunk-generation, mixed I/O, and mostly-idle workloads before making scalability claims.

## 12. Decision status

| Decision | Recommendation | Status |
|---|---|---|
| Authoritative mutation unit | Explicit ownership domain; start world-wide | Proposed |
| Physical scheduling | Bounded shared simulation pool, one active task per domain | Proposed |
| Work stealing | Only eligible ready tasks between boundaries | Proposed |
| Tick model | Current single-domain semantics first; later hybrid local deadlines + explicit global coordination | Proposed |
| Cross-owner state changes | Immutable intents and target-owner validation | Proposed |
| Heavy-owner scaling | Snapshot compute first; conflict-aware writes remain research | Proposed |
| Migration | Disabled until handoff tests pass | Proposed |
| Minestom | No dependency today; reconsider only with concrete protocol/world integration evidence | Verified baseline; future decision open |

## Evidence gaps

- The supplied Folia archive is present in the repository, but its binary contents have not yet been exhaustively enumerated in this pass. The mandatory Java-path inventory and subsystem-by-subsystem patch analysis are not complete.
- `1.8.8 - mechanics.zip` does not appear in the inspected `develop` tree. The committed `reference/1.8.8/` source tree is present.
- No local build or test run was available through the connected GitHub inspection environment. Existing release notes report the prior dev.16 test results; those are not newly rerun here.
