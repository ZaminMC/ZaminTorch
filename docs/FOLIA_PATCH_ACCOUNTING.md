# Folia 26.2.x Patch Accounting — the complete per-patch classification

Cycle-4 exhaustive-audit deliverable. Companion to `FOLIA_PATCH_INVENTORY.md`
(the mechanical 460-path inventory) and `FOLIA_FORENSIC_AUDIT.md` (the
subsystem deep-dives): this index gives every discovered patch file in the
supplied tree exactly one primary status against Torch, per the audit's
classification contract.

## Provenance (from source, reverified this cycle)

- Tree: PaperMC/Folia `ver/26.2.x`; `mcVersion=26.2`, `paperRef=5d4f9bd0e4f…`,
  `channel=BETA`, Java 25, paperweight-patcher 2.0.0-beta.21. NOT a 1.8.8
  server (see `CYCLE4_EXHAUSTIVE_AUDIT.md` Phase 0).
- Layer model (from `build.gradle.kts` patchDir config): `folia-api/paper-patches`
  apply over upstream **Paper-API**; `folia-server/paper-patches` apply over
  upstream **Paper-server** (vanilla + CraftBukkit + Spigot + Paper already
  folded by upstream); `folia-server/minecraft-patches` apply over the
  **Mojang-mapped vanilla server**. The upstream Paper tree itself is NOT in
  the archive — Folia's delta only. Bukkit/CraftBukkit/Spigot/Paper layers
  are therefore out of inspectable scope for this archive; their
  Torch-relevance is governed by the deferred §27.9/§28.7 adapter decision,
  not by this tree.
- Patch files discovered: **24** (21 feature patches + 3 build-file patches).
  All 24 examined and classified below. Nothing uninspected.

## Classification legend

`Equivalent` (behavior + test evidence) · `Partial` · `Missing` ·
`Different by design` (cite the decision) · `Not applicable` (reason) ·
`Unverified` (evidence insufficient; resolution procedure given).

Applicability is set by the Phase-1 contract: Folia is engineering evidence,
not a Torch compatibility target (§0 rule 9). So a patch can be
`Different by design` (Torch's own model covers the same ground) or
`Not applicable` (threading/branding with no 1.8.8-observable contract)
while still carrying an Adopt/Adapt lesson, or feeding a Torch-native row.

## The index

### folia-api (Paper-API layer)

| # | Patch | What it does (hunk-verified) | Torch status | Evidence / ledger link |
|---|---|---|---|---|
| A1 | `paper-patches/features/0001-Force-disable-timings` | Disables the Bukkit `Timings` API machinery on the API side | **Not applicable** — Bukkit-line API, deferred with §28.7; Torch has no timings API | Decision item DEC-002: when the adapter activates, Timings surface must be stubbed or implemented — record now, decide then |
| A2 | `paper-patches/features/0002-Region-scheduler-API` | Adds `RegionScheduler`/`EntityScheduler`/`GlobalRegionScheduler`/`AsyncScheduler` public scheduling contracts | **Different by design** — Torch's §7 scheduler contract + §28.1 execution categories are the native analogue; Folia's API shape is not owed | zamin-api scheduler family + `CONCURRENCY_ARCHITECTURE.md`; §28.1's five categories cover the same ground (authoritative / snapshot / async compute / blocking I/O / lifecycle) |
| A3 | `paper-patches/features/0003-Require-plugins-to-be-explicitly-marked-as-Folia-supported` | `plugin.yml` must declare `folia-supported: true` or the plugin refuses to load | **Different by design** — Torch's plugin contract is §28.1 execution categories, not a manifest gate; the *intent* (make legacy main-thread assumptions explicit) is adopted by §28.7's own inventory requirement | DEC-002 (adapter-era plugin gating question) |
| A4 | `paper-patches/features/0004-Add-TPS-From-Region` | Per-region TPS read on the API | **Different by design** — Torch has aggregate `/tps` + the §20 per-owner tick-time commitment; regions are not Torch's ownership unit | ML-006 (the /debug section table) + ML-007 (attribution baseline) carry the Torch-native observability remainder |

### folia-server / paper-patches (Paper-server layer)

| # | Patch | What it does | Torch status | Evidence / ledger link |
|---|---|---|---|---|
| P1 | `features/0001-Region-Threading-Base` (206 paths) | The threading retrofit over Paper: `TickThread`, `EntityScheduler`, regionized `CraftScheduler`, ownership assertions | **Different by design** — Torch's §6–§16 ownership model + implemented scheduler/owners are the native answer; the forensic audit's §7 traceability table maps each lesson to its Torch decision and test | `CONCURRENCY_ARCHITECTURE.md`, `CONCURRENCY_DECISIONS_AND_ROLLOUT.md`, ownership tests (§22 Phase 1-2 records) |
| P2 | `features/0002-Build-changes` | Fork build metadata | **Not applicable** — fork packaging | — |
| P3 | `features/0003-Fix-tests-by-removing-them` | Removes Paper tests incompatible with region threading | **Not applicable** — Torch keeps its tests; no equivalent debt | — |
| P4 | `features/0004-Region-profiler` | Profiler command wiring over Paper's infra | **Different by design** — `EngineProfiler` (vanilla-shaped /debug) is Torch's profiler; per-owner metrics is the §20 model | ML-006: the /debug stop section table is missing — the exact gap that blocks attribution |
| P5 | `features/0005-Add-watchdog-thread` | Watchdog thread over the Paper layer | **Covered by the native capability** — ML-013 landed the REFERENCE's own 1.8.8 ServerWatchdog (not the Folia port): max-tick-time row, daemon "Server Watchdog" thread, heartbeat watch, thread dump + crash report + force exit on a wedged tick, plus the reference's keep-up warn law | ML-013 (RESOLVED, landed 2026-10-26; ServerWatchdogTest 6/0) |
| P6 | `features/0006-Add-TPS-From-Region` | Per-region TPS command | **Different by design** — see A4 | ML-006 / ML-007 |

### folia-server / minecraft-patches (vanilla-server layer)

| # | Patch | What it does | Torch status | Evidence / ledger link |
|---|---|---|---|---|
| M1 | `features/0001-Region-Threading-Base` (200 paths) | The state extraction: regionizer, `RegionizedWorldData`/`RegionizedData`/`RegionizedServer`, region tick scheduler, per-region entity sections | **Different by design** — same verdict as P1 at the vanilla layer; Torch's owner model + cross-owner protocol (§10) are the counterpart, with the four hard cases already answered (forensic audit §5) | `CONCURRENCY_ARCHITECTURE.md` §4/§7 |
| M2 | `features/0002-Max-pending-logins` | Bounds pending logins per tick so a login flood cannot stall a region | **Unverified** — no pending-login bound found by search, but the Torch login path was not audited end-to-end this cycle; a login-flood's tick-lane cost is unknown | ML-014: the login-flood boundary check (bounded verification: N concurrent logins against a booting world; measure tick-lane cost; compare against the reference's own login handling — 1.8.8 vanilla queues logins too) |
| M3 | `features/0003-Add-chunk-system-throughput-counters-to-tps` | Chunk-system throughput counters on /tps | **Different by design** — Torch's /tps is its own; chunk-throughput visibility is part of the attribution remainder | ML-006 / ML-007 |
| M4 | `features/0004-Prevent-block-updates-in-non-loaded-or-non-owned-chunks` | setBlock/neighbor-update entry points guard unloaded/non-owned chunks; drops or defers | **Unverified** — the non-owned half is moot on Torch's current single gameplay lane, but the *unloaded-chunk* half IS 1.8.8-observable (vanilla's neighbor walk skips unloaded chunks) and Torch's guard was not verified | ML-015: neighbor-update-at-unloaded-boundary parity check (test: redstone/neighbor update at a loaded/unloaded chunk edge; reference drops the update) |
| M5 | `features/0005-Block-reading-in-world-tile-entities-on-worldgen-threads` | Blocks worldgen threads from reading live tile entities | **Different by design** — §27.2 keeps worldgen off the api lanes and §15 requires publish-under-authority; Torch's generation lanes already follow this | §22 Phase 3-4 records; the S-wave persistence lanes |
| M6 | `features/0006-Sync-vehicle-position-to-player-position-on-player-data-load` | Fixes vehicle/passenger position desync after relog | **Unverified** — Torch persists `ridingVehicleId` (PlayerSession) but the relog remount + position sync walk was not verified this cycle; 1.8.8 itself restores the mount from player NBT | ML-016: the relog-while-riding parity check (quit riding a boat/minecart/pig → rejoin → reference remounts at the vehicle; assert Torch's remount + positions) |
| M7 | `features/0007-Region-profiler` (19 paths) | `RegionizedProfiler` machinery, spike formatting, region stats | **Different by design** — see P4 | ML-006 |
| M8 | `features/0008-Add-watchdog-thread` | The watchdog implementation at the vanilla layer | **Covered by the native capability** — same verdict as P5: ML-013's reference-shaped watchdog serves the same diagnostics aim at the engine's own tick lane | ML-013 (RESOLVED) |
| M9 | `features/0009-Teleport-desynced-passengers-to-root-vehicle` | Cross-region vehicle teleport keeps the passenger tree consistent | **Not applicable** — a threading-consistency fix with no 1.8.8 contract; 1.8.8's own teleport-while-riding semantics (dismount-on-teleport) are the parity question, tracked in the parity matrix (domain D) not as a patch debt | Parity matrix D note; no ledger row needed |
| M10 | `features/0010-Do-not-allow-out-of-region-teleport-accept` | Rejects stale teleport acks targeting another region | **Not applicable** — threading guard, no 1.8.8 observable | — |
| M11 | `features/0011-Use-Folia-logo` | Branding | **Not applicable** | — |

### Build patches

| # | Patch | What it does | Torch status |
|---|---|---|---|
| B1 | `folia-api/build.gradle.kts.patch` | Fork coordinates/deps for folia-api | **Not applicable** — fork packaging |
| B2 | `folia-server/build.gradle.kts.patch` | Fork coordinates/deps for folia-server | **Not applicable** |
| B3 | `folia-checkstyle/build.gradle.kts.patch` | Fork coordinates for checkstyle module | **Not applicable** |

## Layer reconstruction (combined-effect notes)

- The watchdog, region-profiler, and TPS-from-region features each appear on
  **two or three layers** (api + paper + minecraft) and only compose as a
  unit: e.g. `A4`+`P6` expose per-region TPS that `M7`'s TickTime machinery
  feeds. No single-layer classification above is meaningful without the
  others — they are one capability each, classified once per file and linked
  to one ledger row each (ML-006 for profiler/TPS, ML-013 for watchdog).
- `M1`+`P1` are one logical patch (the region threading base) split across
  layers by paperweight; 406 of the 460 inventoried paths are theirs.
- `A2` (scheduler API) depends on `M1`/`P1`'s `EntityScheduler`/region
  machinery at runtime — an API-addition-depends-on-runtime-change case; the
  API file alone does not deliver the capability. Torch's counterpart
  pairing is §28.1's categories with the scheduler's owner-context
  enforcement.
- `M4`'s unloaded-chunk guard *modifies* behavior vanilla 1.8.8 also guards
  (the neighbor walk skips unloaded chunks) but Folia additionally guards
  *non-owned* chunks — a superset Torch does not need until multiple
  gameplay lanes exist (§22 Phase 6 gates).

## Accounting summary

| Measure | Count |
|---|---|
| Patch files discovered | 24 |
| Examined and classified | 24 |
| Equivalent | 0 |
| Partial | 0 |
| Missing (Torch-native capability, not compat debt) | 2 files (P5, M8 — one feature: the watchdog → ML-013) |
| Different by design | 10 (A2, A3, A4, P1, P4, P6, M1, M3, M5, M7) |
| Not applicable | 9 (A1→DEC-002, P2, P3, M9, M10, M11, B1, B2, B3) |
| Unverified | 3 (M2→ML-014, M4→ML-015, M6→ML-016) |
| Files/layers not inspectable | 0 within this archive; the Paper/Spigot/CraftBukkit/Bukkit layers below `paperRef` are not in the archive at all (recorded as a scope boundary, not a coverage gap of the supplied tree) |

Coverage statement: every patch file in the supplied tree is accounted for
above. The 460-path hunk-level detail lives in `FOLIA_PATCH_INVENTORY.md`
and is not duplicated here; the forensic subsystem analysis lives in
`FOLIA_FORENSIC_AUDIT.md`. No partial scan is presented as complete.
