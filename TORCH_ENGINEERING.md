# Torch Engineering Ledger

**Status date:** 2026-10-11 (updated; Slice 10b the hoppers landed)
**Branch inspected:** `develop`  
**Purpose:** record verified repository state and separate implemented behavior from proposals and unverified work.

## Current repository

- Build: Gradle Kotlin DSL, Java 21 toolchain, JUnit 5.
- Modules: `:zamin-api`, `:zamin-core`, `:zamin-protocol-v1_8_8`, `:zamin-launcher`.
- Current build distribution version in `zamin-launcher/build.gradle.kts`: `0.2.0-dev.25`.
- Latest release: [v0.2.0-dev.25](https://github.com/ZaminMC/ZaminTorch/releases/tag/v0.2.0-dev.25), with a server JAR and distribution ZIP. 667 tests green at the build's commit (the full suite ran on the same tree; the full pass on 2-core hosts uses `--max-workers=1`).
- The committed 1.8.8 reference tree is `reference/1.8.8/`, described by the repository as 1,634 decompiled classes.
- `Folia-ver-26.2.x.zip` is committed at the repository root.
- The requested `1.8.8 - mechanics.zip` is not present in the inspected `develop` tree. Recover it before claiming it was inspected or using it as the required implementation source.
- No Minestom dependency is declared in the inspected Gradle build. `docs/ENGINEERING_PLAN.md` records Minestom as deferred under ADR-0001.
- This pass used GitHub repository access, not the developer's local checkout. Local uncommitted changes, local build status, and local environment state are therefore unknown.

## Architecture that exists today

- `EngineTicker` is a single simulation loop. It drains a cross-thread deferred-work queue at tick start, advances world time, and calls the configured tick handler.
- Missed ticks are skipped instead of burst-caught-up.
- `EngineWorld` is the authoritative world state. Mutations check the recorded owner thread; chunks are published through concurrent map operations, but mutable chunk values do not become thread-safe through that map.
- Chunk generation and chunk-load preparation currently execute synchronously before publication.
- The 1.8.8 protocol adapter uses Netty; network event-loop execution is distinct from world mutation ownership.
- Native API, engine, protocol, and launcher are separate Gradle modules. A Bukkit/Spigot/Paper binary compatibility runtime is not present in the inspected module list.

## Engineering status

Statuses: `NOT INVESTIGATED`, `INVESTIGATED`, `IN PROGRESS`, `IMPLEMENTED`, `VERIFIED`, `BLOCKED`.

| Area | Status | Evidence / next work |
|---|---|---|
| Build and module structure | VERIFIED (repository metadata) | Gradle settings and module build files inspected; actual build not rerun in this environment |
| Current world owner checks | INVESTIGATED | `EngineWorld` owner checks and `EngineTicker` context inspected; audit every mutable access path |
| Ownership primitives (Phase 1) | IMPLEMENTED | `server/concurrent/OwnershipDomain` + `OwnershipViolationException` + `ComputeTicket`/`StaleResultException` + `DomainTask`; `EngineWorld.attachDomain` wired, the boot loop binds the domain; 8 ownership tests green |
| Minestom integration | BLOCKED / deferred | No dependency currently present; see `docs/ENGINEERING_PLAN.md` ADR-0001 |
| Folia source audit | VERIFIED (audit complete) | Archive extracted; all 21 patches read; 460 modified Java paths inventoried (`docs/FOLIA_PATCH_INVENTORY.md`); the subsystem deep-dives + the four hard-case verdicts in `docs/FOLIA_FORENSIC_AUDIT.md` |
| Vanilla 1.8.8 reference source | VERIFIED (present) | `reference/1.8.8/`; source must be inspected per mechanic before porting |
| `1.8.8 - mechanics.zip` | BLOCKED | Not found in inspected `develop` tree |
| Vanilla compatibility coverage | IN PROGRESS | Detailed per-mechanic state is in `VANILLA_1_8_8_COMPATIBILITY.md` |
| Native API | IN PROGRESS | `zamin-api`; inspect each public contract for ownership, nullability, lifecycle, and mutation semantics |
| Bukkit/Spigot/Paper compatibility | NOT INVESTIGATED | No compatibility modules in current settings; do not claim plugin compatibility |
| Release pipeline | IN PROGRESS | Manual dev.16 release exists; no verified automated build-and-release workflow was found in the inspected tree |
| Runtime performance baseline | NOT INVESTIGATED | Need reproducible workload definitions and fresh measurements |

## Current concurrency state: the doc set AND the Phase 1-3 runtime

Documents:

- `docs/CONCURRENCY_ARCHITECTURE.md` — the design core
- `docs/MINESTOM_CONCURRENCY_BASELINE.md` — the verified Torch baseline
- `docs/FOLIA_FORENSIC_AUDIT.md` — the completed patch-level Folia audit
- `docs/FOLIA_PATCH_INVENTORY.md` — the 460-path traceable inventory
- `docs/CONCURRENCY_DECISIONS_AND_ROLLOUT.md` — the 20 ADRs + the 8-phase rollout + the comparison table
- `docs/CONCURRENCY_DIAGRAMS.md` — the component graph + the six lifecycle sequences

Runtime implementation (the rollout phases, on `develop`):

- **Phase 1 — ownership primitives and enforcement: IMPLEMENTED.** `server/concurrent/OwnershipDomain` (dynamic executor binding with counted re-entrancy, monotonic generations via `transferOwnership`, the single-writer tick gate, STRICT/DIAGNOSTIC enforcement), `OwnershipViolationException` (the diagnosable failure, still an `IllegalStateException`), `ComputeTicket`/`StaleResultException` (the versioned result envelope), `DomainTask` (the domain-bound task: cancel never resurrects, mid-run cancel refused). `EngineWorld.attachDomain` delegates `requireOwnership` when attached; the boot loop binds `simulation:<world>` for its whole run. Live behavior identical, now enforced.
- **Phase 2 — scheduler integration: IMPLEMENTED.** `SimulationScheduler` over the domains: per-domain ready FIFO (submission order preserved), the delayed store keyed (dueTick, stable sequence), `runDue` walking domains in stable name order with each task under its own gate + binding, per-domain bounded admission with loud refusal, permanent cancellation, total idempotent shutdown, the §17 telemetry floor. `EngineTicker.attachScheduler` routes `submit` through it and `tickOnce` runs due tasks before the time advance. Identical gameplay.
- **Phase 3 — the compute subsystem: IMPLEMENTED.** `ComputeSubsystem`: bounded admission + a conservative worker pool (max(1, cores/4) cap 4), workers with zero mutation authority (assertion-pinned), typed handles closing the apply/stale arms, the owner's `drainResults` at the tick boundary applying fresh results in-context and discarding stale ones through the fallback (never blindly applied), failures surfaced and never worker-killing, shutdown total and idempotent. `EngineTicker.attachCompute` + the tick-edge drain live.
- **Phase 4 — partitioned simulation, the protocol layer: IMPLEMENTED.** The `DomainWorkerPool` (the shared bounded simulation pool, max(1, cores/2) cap 4, parked workers, the enforced tick-edge join, failure isolation, total shutdown); the scheduler's `runDueParallel` (caller-bound domains drain inline — the live single-domain run byte-identical — foreign domains dispatch and join; FIFO + the gate survive); the `RegionPartition` (the fixed R×R-chunk grid, deterministic floor-division mapping seamless across the origin, one stable domain per region, static); the `OwnershipMigration` protocol (the §9 phase machine with the scheduler's admission holds and `drainDomain`, fault-injection tested); the `CrossOwnerRouter` riding the live tick (`attachCrossOwnerRouter`, the drain after the scheduler walk, before the compute drain; booted + shut with the server). The live run is still single-domain by design — the region activation is gated on the per-mechanic boundary protocols landing with their vanilla mechanics.
- **Phase 4 — the spatial activation: LANDED (Slice 10a, 675 green).** `EngineWorld.attachRegionPartition` + the position-aware `requireOwnership(op, position)` (every block/chunk mutation resolves the owning region's domain; the credential passes through the region itself OR the ambient simulation domain on the single tick lane; every other context fails through the region's own STRICT/DIAGNOSTIC enforcement — the boot-lane construction window keeps the historical owner-thread check while no ambient exists); `EngineConfig.regionSizeChunks` (the `region-size-chunks` property — ADR-6's default huge: the whole world one region until configured); the boundary scope `executeInRegions(box, body)` (the design §10.3 larger exclusive ownership — every region the box touches entered in the one global packed-id acquisition order and released in reverse, concurrent scopes deadlock-free by construction, a held region refuses the enter and the scope releases its acquisitions); the seam-crossing mechanics under scopes: the piston's whole event (the 14-block reach wrap around doEvent, plus the landing cells' own region) and the detonation blast walk (the generalized `detonateAt` destruction under the power*2+margin box); the per-region intent drain at the tick head (`drainRegionIntents` — every live region entered, its inbound §7 intents applied in the target's own context, held regions skipped with their intents staying queued — ordered, never dropped); `RegionPartition.domainsBetween` (the sorted-id box walk). The §11 boundary tests (RegionBoundaryTest, 8): the scope carries the region credential with the ambient never entered, the held region's exclusivity, the drain's held-region skip + after-release apply, the live foreign write failing against the position's own region, the piston push AND the sticky pull across the one-chunk seam (vanilla-exact, same-tick), the redstone decay ladder walking the seam (15-7 → 15-8 across x=15/16), the explosion consuming both sides of the seam, and the region-targeted router intent applying at the target's boundary. **The parallel multi-domain execution lane (the scheduler's per-region dispatch of gameplay systems) remains future work, gated on measurement per the rollout's Phase 4 evidence rule; the redstone cascade and the fluid walks ride the ambient authority on the single lane (ledgered limitation).**
- **Phase 5 partial — the hopper transfer system LANDED (Slice 10b, 682 green)**: the reference port of HopperBlock + HopperBlockEntity — the flattened family (legacy 154, five FACING values × the ENABLED pair, the nibble facing | disabled<<3, the item 410), the 8-tick transfer cadence (ONE item per transfer with the whole-stack restore on failure), the push toward the FACING container (the chest's 27 slots), the pull from the container above, the furnace's sided slots (top input / bottom output-then-fuel / sides fuel, the bucket-only fuel pull gate), the item-entity suck from the box above, the powered disable through the neighbor bus (the ENABLED pair flip), the receiving-hopper cooldown reset, the placement's clicked-face-opposite facing (UP clamps DOWN), the survival break spill + the comparator wake, the comparator's container-fullness read (5 slots), and every transfer one same-tick unit under the boundary scope (the §11 hopper row — the cross-seam push pinned vanilla-exact). The 5-slot hopper GUI window and the hopper-contents ZCD persistence ride the protocol/persistence slices (ledgered).
- **Phase 5-7 — cross-boundary gameplay completeness (the remaining §10.3 mechanics), heavy-owner optimization, adaptive scheduling: NOT STARTED** (the §11 boundary test list grows with each ported mechanic: hopper/tripwire/dispenser are unported; the activation follows the rollout's dependency gates).

## Next steps, ordered

1. **Phase 4 completion — the spatial activation: LANDED (Slice 10a)** — the regionized ownership (position-aware assertions), the boundary scopes (piston + explosion), the per-region intent drain, and the §11 boundary tests over the seam-crossing piston/redstone/explosion. The parallel per-region execution lane (gameplay systems dispatched per region through the worker pool) stays gated on measurement.
2. **Phase 5+ — cross-boundary completeness, heavy-owner optimization, adaptive scheduling** per the rollout table (each phase gated on the previous phase's green tests and the recorded evidence).
3. The vanilla 1.8.8 slices continue in parallel per `VANILLA_1_8_8_COMPATIBILITY.md`. Slice 8 (nether portals) is COMPLETE including 8c. Slices 9a-9f (the redstone family) LANDED. **Slice 10a (the Phase 4 spatial activation) LANDED at 675 green; Slice 10b (the hoppers) LANDED at 682 green.** **Next: dispensers/droppers, tripwire, then potions, leads, structures, natural spawn cycles.** The suite is load-sensitive on 2-core hosts: run the full pass with `--max-workers=1`.
4. Releases ride every dev build with a changelog (the established convention since dev.17).

## Security and repository safety

- Never put GitHub credentials in source, commits, issues, or worklogs. A credential pasted into chat should be revoked and replaced.
- No runtime source was changed by the 2026-10-10 documentation pass.
- All changes in this pass were committed directly to `develop`; no destructive Git operations or branch switches were performed.
