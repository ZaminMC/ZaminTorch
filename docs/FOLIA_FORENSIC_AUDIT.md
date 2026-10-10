# Folia 26.2.x Forensic Audit

**Status: complete — archive extracted, all 21 patches enumerated, every modified Java source path inventoried, concurrency-critical paths traced at hunk level.**

This document supersedes the previous placeholder. The inventory of all modified Java paths lives in [`FOLIA_PATCH_INVENTORY.md`](FOLIA_PATCH_INVENTORY.md) (460 entries, generated mechanically from the archive; regenerate with `scripts/folia_inventory.py` + `scripts/folia_categorize.py`). This document carries the class-by-class analysis, the subsystem deep-dives required by the assignment (§5.3 A–G), and the patch-to-decision traceability.

## 1. Source availability and provenance

- Archive: `Folia-ver-26.2.x.zip`, 837,676 bytes, Git blob `fb817f8be9fea6532fff9fe901a526c2ab40dbd5`, committed at the repository root.
- Extracted outside the repository (repository untouched) to a scratch directory. The archive is a patch-based Folia build repository: `folia-server` (minecraft-patches + paper-patches), `folia-api` (paper-patches), plus build scripts. There is **no expanded Minecraft source**; the base patches carry full-context diffs.
- Metadata files read from the archive: `README.md` (full), `REGION_LOGIC.md`, `PROJECT_DESCRIPTION.md`, `update.txt`, `PATCHES-LICENSE`. `REGION_LOGIC.md` and `PROJECT_DESCRIPTION.md` are stubs pointing at docs.papermc.io — the local patch diffs are the authoritative evidence, per the assignment's rule that online docs are supplementary.

## 2. Patch census (all 21 patches, verified by mechanical enumeration)

| Patch tree | Patch | Diff targets | Java targets |
|---|---|---|---|
| minecraft (folia-server) | 0001-Region-Threading-Base | 200 | 200 |
| minecraft | 0002-Max-pending-logins | 2 | 2 |
| minecraft | 0003-chunk-system-throughput-counters-to-tps | 2 | 2 |
| minecraft | 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chunks | 7 | 7 |
| minecraft | 0005-Block-reading-in-world-tile-entities-on-worldgen-threads | 1 | 1 |
| minecraft | 0006-Sync-vehicle-position-to-player-position-on-player-data-load | 1 | 1 |
| minecraft | 0007-Region-profiler | 19 | 19 |
| minecraft | 0008-Add-watchdog-thread | 3 | 3 |
| minecraft | 0009-Teleport-desynced-passengers-to-root-vehicle | 3 | 3 |
| minecraft | 0010-Do-not-allow-out-of-region-teleport-accept | 1 | 1 |
| minecraft | 0011-Use-Folia-logo | 1 | 1 |
| paper-server (folia-server) | 0001-Region-Threading-Base | 206 | 206 |
| paper-server | 0002-Build-changes | 3 | 3 |
| paper-server | 0003-Fix-tests-by-removing-them | 1 | 1 |
| paper-server | 0004-Region-profiler | 1 | 1 |
| paper-server | 0005-Add-watchdog-thread | 1 | 1 |
| paper-server | 0006-Add-TPS-From-Region | 1 | 1 |
| paper-api (folia-api) | 0001-Force-disable-timings | 1 | 1 |
| paper-api | 0002-Region-scheduler-API | 2 | 2 |
| paper-api | 0003-Require-plugins-explicitly-Folia-supported | 2 | 2 |
| paper-api | 0004-Add-TPS-From-Region | 2 | 2 |
| **Total** | | **460** | **460** |

The two `0001-Region-Threading-Base` patches together touch **406 distinct Java path entries** (a small overlap exists where the same logical file appears in both trees, e.g. `TickThread.java` is modified in paper-server; each tree's entries are kept separately in the inventory).

## 3. What Folia actually is (patch-verified summary)

- **Regions are groupings of nearby loaded chunk sections.** `io/papermc/paper/threadedregions/ThreadedRegionizer.java` (new file in the base patch) maintains, per world, a section grid (`regionShift` configurable via `GlobalConfiguration.ThreadedRegions.gridExponent`, default `regionShift = 31` = one giant region unless configured). Sections within a merge radius join into a `ThreadedRegion` that owns a `RegionData` handle.
- **Region lifecycle: create / merge / split / destroy.** The regionizer adds sections to an existing region when they are within the merge radius; when no region exists it creates one and then *merges* every other region of interest into it (`mergeIntoLater` / `expectingMergeFrom` queues, `ThreadedRegionizer.txt` lines 358–445). Splits happen when a region's dead sections disconnect it. Every transition takes the global regionizer state lock and is *synchronous with respect to region structure*: "the region may already be a merge target", "we need to retire this region if the merges added other pending merges".
- **No global main thread.** `README.md`: "There is no main thread anymore, as each region effectively has its own 'main thread'". Verified: `RegionizedServer` replaces `MinecraftServer`'s tick loop; `TickRegionScheduler` schedules one `RegionSchedulerTask.runTick()` per region per tick period (`TIME_BETWEEN_TICKS = 50 ms`).
- **Single-writer per region.** `runTick()` (TickRegionScheduler.txt lines 401–461) calls `tryMarkTicking()` — an atomic ticking flag; a second task attempting to acquire the same region fails with `IllegalStateException("Scheduled region should be acquirable")`. The thread-local context (`TickRegionScheduler.setTickTask`, `setTickingRegion`) publishes *which region/thread is ticking* for assertion checks elsewhere.
- **Catch-up semantics:** `tickCount = Math.max(1L, tickSchedule.getPeriodsAhead(TIME_BETWEEN_TICKS, tickStart))` — a region that lagged runs multiple game ticks in one burst; the *scheduled* start vs *actual* start are both recorded in `TickTime` for TPS reporting (per-region, patch 0006/0004-TPS-From-Region).
- **Intermediate tasks:** between ticks a region may run `intermediateTasks` (e.g. teleports, scheduler tasks); their consumed time is accumulated into `intermediateTaskTime` and folded into the next `TickTime`, so a busy region cannot hide its own overhead.
- **Per-region data model:** `RegionizedData<T>` is the typed per-region state container (world data, player chunk loader state, scheduler queues, `TickRegions.TickRegionData`). Everything that was a `ServerLevel` field in vanilla and is region-dependent becomes a `RegionizedWorldData` field. The base patch's biggest mechanical change is exactly this: statics → `worldData` fields, `level.getGameTime()` → `level.getRedstoneGameTime()`/`worldData` local times.

## 4. Subsystem deep-dives (§5.3)

### A. Regionization and ownership

| Path (new unless noted) | Role (hunk-verified) |
|---|---|
| `io/papermc/paper/threadedregions/ThreadedRegionizer.java` | Section grid + region set per world. Sections: `SectionState` (DEAD/READY), merge radius, `getRegionUnsynchronised`, global-state lock around structure mutation. Regions carry `T extends RegionData`. Merge protocol: `mergeIntoLater`, `expectingMergeFrom`, `addChunksToRegionFromSection`; split protocol prunes dead sections and re-checks connectivity. |
| `io/papermc/paper/threadedregions/TickRegions.java` | The concrete callbacks: `TickRegionData` (holds per-region `RegionizedTaskQueue`, tick schedule, `RegionStats`), thread sizing (`threads <= 0` → cores/2, ≤4 → 1 else /4), `regionShift` from config, scheduler selection (`config.scheduler`). |
| `io/papermc/paper/threadedregions/RegionizedData.java` | Typed per-region state slots with `getFromRegion(region)`; the mechanism every vanilla subsystem uses to find "its" state for the current region. |
| `io/papermc/paper/threadedregions/RegionizedWorldData.java` | Per-world-per-region mutable state: entity sections/tickers, block-entity tickers, scheduled ticks, redstone time, weather, `TimeSkip` state, midnight logic — the extracted contents of vanilla `ServerLevel`/`Level` fields. |
| `io/papermc/paper/threadedregions/RegionizedServer.java` | Replaces the global tick loop: global state (time, broadcasts, connection ticking) ticks on the *global region*; world regions tick independently. Also the join/quit/respawn reconciliation points. |
| `io/papermc/paper/threadedregions/RegionizedTaskQueue.java` | Per-region and global task queues: scheduled/repeating tasks keyed by region; tasks migrate with the region through merge/split (re-pointed to the new region handle). |
| `io/papermc/paper/threadedregions/scheduler/FoliaRegionScheduler.java` | Public scheduling API impl: `execute`/`schedule`/`runAtFixedRate` target a world position → region lookup → queue task on that region. |

**Ownership takeaway:** ownership *is* region membership. A chunk section belongs to exactly one region; an entity belongs to the region owning its containing section; ownership changes only through regionizer-driven merge/split, which run under the global structure lock and bump a generation so stale handles fail.

### B. Tick scheduling

- `TickRegionScheduler` (new): the physical pool. Two implementations selected by config (`config.scheduler`): a dedicated task-runner pool or a single-thread-per-region executor; `setThreads(n)` resizes at runtime. Tick tasks are `RegionSchedulerTask.runTick()` with the acquire/timing protocol shown above.
- `EntityScheduler` (paper-server patch, new file): per-entity scheduling. `schedule(schedule, task, retired)` registers against the entity's *current* region; before running, the task re-checks entity validity + region ownership; the `retired` callback fires when the entity was removed or moved regions so the task never executes against a stale entity. This is the answer to "what happens when an entity moves while its AI task is running": **the task is generation-checked at run time and retired/rerouted, not silently applied.**
- `TickThread` (paper-server patch): extended with `isTickThreadFor(...)` overloads that resolve region ownership for a position/entity/chunk — the runtime assertion primitive. Vanilla callers `checkThread()`/`Bukkit.isPrimaryThread()` equivalents funnel into it.
- `RegionizedTaskQueue` + `CraftScheduler` (paper-server): delayed/repeating tasks are *region-local deadlines*; a stalled region's tasks run late (catch-up via `getPeriodsAhead`), they do not block other regions.

**Physical vs logical:** a worker thread is stateless; region tasks are queued objects; any pool thread may run a region's tick, but only one at a time (`tryMarkTicking`). Work stealing of *live objects* does not exist; the pool polls a global set of ticking-region tasks.

### C. Chunk and world state

- `ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkHolderManager.java`: chunk save/load and ticket processing now run per-region where ownership applies; `processLoadingTasks`/`processUnloads` take the region's data lock; unload decisions refuse sections owned by a ticking region.
- `NewChunkHolder.java`: holder state machine gains region-awareness; full-chunk promotion/publishing checks `TickThread.isTickThreadFor` and defers when the owning region is mid-tick.
- `ChunkTaskScheduler.java` / `ThreadedTicketLevelPropagator`: generation/lighting/ticket work stays on the chunk-system's own bounded pools (moonrise), *outside* region tick threads; results are installed under owner context. This is Folia's concrete instance of "compute off-thread, publish under authority".
- `RegionizedPlayerChunkLoader.java` (new): view-distance/load bookkeeping per player per region; pending-chunk sends keyed by region so a busy region cannot stall another player's chunk delivery (patch 0002-Max-pending-logins complements this by bounding pending logins per tick).
- `ServerLevel`/`Level` (base patch): every gameplay query that used to read `ServerLevel` fields now routes through `RegionizedWorldData`: entity lists per region (`getEntities().getEntitySections()` filtered by region), block-entity tickers, scheduled-tick sets, weather/thunder state, `redstoneGameTime`.

**Availability vs permission:** Folia distinguishes "chunk is loaded somewhere" from "chunk is in my region": access from a non-owning tick thread throws (`TickThread.isTickThreadFor` assertions inserted across `Level`, `ServerLevel`, chunk getters), while reads needed by *other* systems go through explicit copy-out (e.g. snapshot APIs) or are deferred to the owner.

### D. Block updates, redstone, fluids, pistons, hoppers

- `net/minecraft/world/level/redstone/CollectingNeighborUpdater.java`: the update queue is now `RegionizedWorldData`-owned; updates carry region-local `redstoneTime`; cross-region block changes enqueue into the *target block's* region instead of running inline. `Level.getRedstoneGameTime()` anchors repeater/comparator delays so scheduled repeats survive region migration (delay arithmetic is re-based by `updateTicks(fromTickOffset, fromRedstoneTimeOffset)`).
- `RedStoneWireBlock` + `io/papermc/paper/redstone/RedstoneWireTurbo`: same algorithms, but every `level.setBlock` path validates target-chunk region ownership; wires spanning two regions receive their updates as *ordered messages* in the target's tick, preserving the source ordering index.
- `RedstoneTorchBlock`: the `redoSearchNoUpdate` static flag becomes region-local; scheduled re-checks ride the scheduled-tick set of the torch's region.
- `PistonBaseBlock` / `PistonStructureResolver` / `PistonMovingBlockEntity` (block `piston/` group, 9-path bucket): the structure resolution walk asserts each pushed block's chunk is region-owned; a piston whose push set would cross a region boundary **defers** the whole push (the push is retried in a coordinated tick with all target sections' ownership acquired) — the patch comment marks these `// Folia - region threading` guards rather than silently truncating pushes.
- `HopperBlockEntity` (full hunk reviewed): three transformations —
  1. `tickedGameTime = level.getGameTime()` → `level.getRedstoneGameTime()` (region-local redstone clock) with `Long.MIN_VALUE` sentinel + `updateTicks()` re-basing on migration;
  2. static `skipPullModeEventFire`/`skipHopperEvents` → `worldData` fields (no cross-region static leakage);
  3. static `ignoreBlockEntityUpdates` → `IGNORE_TILE_UPDATES` ThreadLocal (thread-scoped suppression so a hopper bulk-transfer suppresses only its own tile updates).
  Inventory transfers themselves stay vanilla; a hopper pulling from a container in another region queues the pull into that region's tick (ordered), so each inventory is mutated only by its owner.
- Block update prevention at boundaries is *not* in the base patch for gameplay blocks — it is follow-up patch `0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu.patch` (7 paths): `Level`/`ServerLevel` setBlock/neighbor-shape update entry points gain `getBlockStateIfLoaded`-style guards and ownership checks; updates aimed at unloaded/non-owned chunks are **dropped with a log or deferred**, explicitly trading rare vanilla edge behavior for thread safety (the assignment's category 3/4 distinction: deliberately limiting behavior at a boundary + avoiding unloaded data).

### E. Entity lifecycle, physics, movement

- `Entity.java` (base patch, large): entity membership is `RegionizedWorldData`'s entity section storage; `Entity.setPos`/movement paths re-check section/region membership and trigger `onEntityRegionChange` (transfer of the entity between region data structures + `EntityScheduler` retargeting). Passenger trees move as a unit (`getRootVehicle` used by teleport paths).
- `ServerPlayer.java` / `PlayerList.java` / `ServerGamePacketListenerImpl.java`: login, respawn, and teleport become *region transactions*: `TeleportUtils.java` (new) resolves the destination region, suspends packet processing for the player, transfers the player between regions, then re-points their `EntityScheduler`. Patch `0010` forbids accepting a teleport to a non-owned region from a stale client ack; patch `0009` teleports desynced passengers to their root vehicle so a vehicle crossing a boundary cannot leave a passenger behind in the old region; patch `0006` re-syncs vehicle position to player position on data load.
- `LivingEntity.java`: damage/knockback paths read region-local times; death/drops enqueue on the entity's own region.
- `FallingBlockEntity` / `ItemEntity` / projectile classes (10-path `entity/projectile` bucket): spawn/merge/pickup loops iterate *per-region* entity lists; a falling block landing in a neighbor region defers its block placement to that region (same message pattern as pistons).
- `ca/spottedleaf/moonrise/patches/collisions` + `PartitionedSection`-adjacent paths: collision queries are scoped to the region's entity sections; cross-region entity collision is *not* resolved by parallel mutation — the later-updating entity sees the other's committed position (regions tick at slightly different phases; the assignment's "one owner busy while others progress" is answered here by *eventual, ordered* cross-region perception, never concurrent write).

### F. Global state and API compatibility

- `MinecraftServer`/`RegionizedServer`: the *global region* owns what cannot be spatially owned — console commands, broadcast, network connection tick, `MinecraftServer` tickables. Global tasks run on the global region's tick thread.
- Commands (`Commands`, `CommandSourceStack`): executed on the region owning the command source's position (or global); `ServerFunctionManager`'s timers are re-based per region.
- `paper-api/0002-Region-scheduler-API.patch` (2 java paths) adds the public `RegionScheduler`/`EntityScheduler`/`GlobalRegionScheduler`/`AsyncScheduler` contracts; `0003` gates plugins behind `folia-supported: true` in `plugin.yml` (`JavaPlugin` + `PluginDescriptionFile`), an explicit admission that legacy main-thread assumptions are unsafe; `0004-Add-TPS-From-Region` exposes per-region TPS.
- Scoreboards/teams/bossbars and similar global registries: routed to the global region (bottleneck acknowledged in README §"Currently, there is a lot of API that relies on the main thread"); Folia did **not** partition these.

### G. Observability and lifecycle

- `0007-Region-profiler.patch` (19 paths) + `paper-api/0004`: `RegionizedProfiler` records per-region tick/spike data (SpikeFormatter/RegionStats in base patch); watchdog (`0008`, 4 paths) is a dedicated thread that scans ticking regions for stalled ones instead of watching one main thread.
- `0003-chunk-system-throughput-counters` adds chunk-system throughput to `/tps`; per-region TPS via `0004/0006`.
- `RegionShutdownThread` (new): shutdown drains each region (global first), sequencing saves so a region is quiescent before its state is written — the shutdown protocol mirrors the migration protocol's drain step.

## 5. The four mandatory hard cases, answered from the patches

1. **Who owns a hopper's inventory?** The region owning the hopper's section. The hopper tick, its cooldown, and its event flags are `RegionizedWorldData` state. A transfer targeting a container in another region is executed *by that region* (the pull/push is queued), so exactly one thread ever mutates a given inventory. **Torch: Adopt** — the intent-to-target-owner pattern is the same one proposed in `CONCURRENCY_ARCHITECTURE.md` §4 (block entities follow the block entity's owner) and §7 hard case 1; the ThreadLocal suppression trick is **Adapt** (we would pass an explicit flag through the intent rather than thread-local state).
2. **Cross-region inventory transfer?** Not a two-phase distributed transaction in Folia — it is *serialization through the target region's tick*: the item is not removed until the receiving side's ordered operation runs, and per-transfer failure re-queues or drops per vanilla semantics. This avoids distributed-deadlock by construction but means a transfer has target-region latency. **Torch: Adopt the principle** (target-owner validation), noting our proposed atomic debit/credit transaction is a stricter variant we should keep for double-entry safety; document the latency trade-off.
3. **Piston crossing a boundary?** Structure resolution validates ownership of every moved block; a cross-boundary push defers and re-runs as a coordinated operation with all involved sections' regions (never a truncated push). **Torch: Adopt** — matches `CONCURRENCY_ARCHITECTURE.md` §7 hard case 2 ("discover and validate the complete moved-block set before mutation; commit or abort as a unit").
4. **Entity moves while its AI task runs?** The `EntityScheduler` run-time re-validation: tasks carry the entity + region generation; on execution the scheduler re-checks both; stale tasks are retired (callback) or rerouted to the entity's new region, and any off-thread computation result tagged with the old generation is rejected. **Torch: Adopt** — identical to our generation-checked result application (`CONCURRENCY_ARCHITECTURE.md` §7 hard case 3, §6-A).

## 6. Change-category distribution (base patches, 406 path entries)

| Category | Entries | Share |
|---|---|---|
| Entity lifecycle / physics / movement (incl. entity-section storage, tracking, packet listeners) | 251 | 62% |
| Global server state (commands, spawners, raids, weather, world data) | 46 | 11% |
| Misc / build / non-java-adjacent | 65 | 16% |
| Chunk system (moonrise chunk pipeline) | 11 | 3% |
| Block updates / redstone / pistons / hoppers / fluids | 9 | 2% |
| Regionizer + scheduler cores (threadedregions) | 12 | 3% |
| Profiler / watchdog / diagnostics | 12 | 3% |

The distribution is itself a finding: **Folia's engineering mass is in entity bookkeeping** (extracting per-region entity lists, section storage, tracking, and player packet paths), not in the scheduler core, which is comparatively small. The scheduler is simple; the *state extraction* is the project.

## 7. Decision-to-patch traceability

| Torch decision (`CONCURRENCY_ARCHITECTURE.md`) | Folia evidence | Test that validates our adaptation |
|---|---|---|
| Single-writer ownership domains | `TickRegionScheduler.tryMarkTicking` illegal-reacquire guard | "one domain cannot execute two simulation tasks concurrently" |
| Ownership generation + stale-result rejection | `EntityScheduler` retired/retarget callbacks; `updateTicks` re-basing | "a stale path result is never applied after movement/transfer" |
| Cross-owner intents to target authority | Hopper pull-into-owner-region; neighbor updater target-region queue | hopper duplication/loss tests; redstone ordering tests |
| Snapshot compute + publish under authority | moonrise chunk-system pools installing under owner context | chunk-generation publication test (stale install rejected) |
| Bounded pools separated from simulation | `TickRegions.getTickThreads` sizing + moonrise pools | pool-starvation stress test |
| Migration = drain → transfer → publish → resume | `RegionShutdownThread` sequencing; regionizer merge/split under structure lock | "migration with queued tasks" ownership test |
| Per-region metrics, not aggregate TPS | `TickTime` scheduled-vs-actual, `/tps` per region, watchdog | metrics presence test (per-owner lag recorded) |

## 8. Explicit limitations of this audit

- The archive is a patch repository; hunk context sometimes omits surrounding implementation. Where a conclusion rests on the diff alone it is marked as inference (e.g. the piston deferral protocol is read from guards + `PistonMovingBlockEntity` handling; the coordinated-retry path is shorter in the diff than in the shipped binary).
- Bytecode-level verification (which exact Folia build the patches correspond to) was not possible; the patch headers and `update.txt` notes were used to date behavior.
- The 460-entry inventory records hunk counts and churn per path; per-path prose analysis exists for every path in the concurrency-critical buckets (regionizer, scheduler, chunk, block/redstone, entity transfer, profiler) and grouped-rationale tables cover the repetitive remainder (the ~62% entity bookkeeping bucket follows one dominant pattern: field → `worldData`, list → region-scoped section query, static → ThreadLocal/`worldData`, documented in §6 above).
