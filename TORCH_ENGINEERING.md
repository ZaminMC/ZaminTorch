# Torch Engineering Ledger

**Status date:** 2026-10-10 (updated; Phase 4 protocol layer)
**Branch inspected:** `develop`  
**Purpose:** record verified repository state and separate implemented behavior from proposals and unverified work.

## Current repository

- Build: Gradle Kotlin DSL, Java 21 toolchain, JUnit 5.
- Modules: `:zamin-api`, `:zamin-core`, `:zamin-protocol-v1_8_8`, `:zamin-launcher`.
- Current build distribution version in `zamin-launcher/build.gradle.kts`: `0.2.0-dev.20`.
- Latest release: [v0.2.0-dev.20](https://github.com/ZaminMC/ZaminTorch/releases/tag/v0.2.0-dev.20), with a server JAR and distribution ZIP. 595 tests green at the build's commit (the full suite ran on the same tree).
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
- **Phase 4 remaining + Phase 5-7 — spatial activation, cross-boundary gameplay completeness, heavy-owner optimization, adaptive scheduling: NOT STARTED** (the §11 boundary test list grows with each ported mechanic: hopper/piston/redstone are unported; the activation follows the rollout's dependency gates).

## Next steps, ordered

1. **Phase 4 completion — the spatial activation** (gated): bind chunks to their region domain once the first boundary-bearing mechanic ports (hopper or piston) with its §11 boundary tests; until then the single-domain run stays the live shape.
2. **Phase 5+ — cross-boundary completeness, heavy-owner optimization, adaptive scheduling** per the rollout table (each phase gated on the previous phase's green tests and the recorded evidence).
3. The vanilla 1.8.8 slices continue in parallel per `VANILLA_1_8_8_COMPATIBILITY.md`. Slices 7g (Unbreaking), 7h (Respiration + the Depth Strider boundary) and 8a (the nether portal's frame/ignition/block/stand clock) LANDED at dev.21 (620 green). **Next: Slice 8b, sub-sliced** — (i) the nether generator port (reference/1.8.8 world/gen/chunk/NetherChunkGenerator.java 415 lines + NetherCaveCarver.java 220: the PerlinNoise stack, the trilinear terrain shape, the lava sea, the quartz/glowstone/fire/mushroom features), (ii) the second EngineWorld (the nether dimension: its own domain `simulation:nether`, its own chunk map + persistence directory), (iii) the changeDimension walk (the PlayerManager.changeDimension port: the 8:1 coordinate scaling clamped to the world border, the PortalForcer's find/generate portal walks, the Respawn-packet wire sequence re-pointed at dimension -1 — the sendRespawnSequence machinery exists and takes a destination). Redstone/potions/leads/structures/spawning behind them.
4. Releases ride every dev build with a changelog (the established convention since dev.17).

## Security and repository safety

- Never put GitHub credentials in source, commits, issues, or worklogs. A credential pasted into chat should be revoked and replaced.
- No runtime source was changed by the 2026-10-10 documentation pass.
- All changes in this pass were committed directly to `develop`; no destructive Git operations or branch switches were performed.
