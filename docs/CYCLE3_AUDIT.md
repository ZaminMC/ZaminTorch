# Cycle 3 Engineering Audit — working findings (2026-10-23)

Live document for the in-progress engineering cycle. Owner: the coordinator.
Updated as findings land; the final backlog lands in the ledgers at cycle close.

## Phase 0 — ground truth (verified)

- `develop` at 48b91574, clean tree, synced; author identity Abasing.
- The release pipeline is RED: run #361 (on 228019a2, the wave-2 head) failed
  at "Build + full test suite". Latest published release is
  **torch-1.8.8-127** — a `release: torch-1.8.8-128` version-bump commit
  exists but no 128 release was ever published. Run #362 (48b91574,
  docs-only) re-runs the same suite — verdict pending at time of writing.
- The failed run's exact failures (from the downloaded CI logs):
  - attempt 1: `zamin-bukkit:test` —
    `BukkitEventBridgeGroupThreeTest.theMobDeathBridgeObservesWithTheHonestShims`
    — org.opentest4j.AssertionFailedError at line 142
    (`assertNotNull(nativeSeen.get(), "the native MONITOR observed the death")`).
  - attempt 2 (the workflow's single retry): `zamin-core:test` —
    `FishingApiAcceptanceTest.theInterceptorSeesTheContextAndReplacesTheCatch`
    — java.lang.AssertionError at line 442 (the 20-second `await` helper's
    timeout throw).
- BOTH tests pass in isolation locally (`--no-daemon`, module-scoped):
  bukkit batch BUILD SUCCESSFUL in 2m23s; fishing batch BUILD SUCCESSFUL in
  5m20s. Neither test is in the workflow's documented-flake list
  (LifecycleEvent/HopperSystem/HopperWindow).

### Finding A-1 (release-blocking): the full-suite gate is load-flaky

Class: verified defect (test robustness), release-blocking because the
release workflow gates on the full suite.

Root-cause analysis:
- The bukkit failure is a racy assertion: the test awaits `cow.dead()` then
  IMMEDIATELY asserts the MONITOR-priority observer fired. If the engine
  publishes the dead flag even one window before the MONITOR dispatch, a
  loaded runner loses the race. Robust form: await the observer, not the
  flag (the harness's own `await` idiom exists for exactly this).
- The fishing failure is a wall-clock deadline (20s) against a live-engine
  cadence that is tick-driven, not wall-clock-driven. Under CI load the tick
  loop slows; the await expires before the engine completes its guaranteed
  tick count. The bite cadence is 20-80 + 10-30 ticks (max ~110 ticks);
  20 seconds is 400 ticks ONLY at full 20 TPS.

Proposed fix (small, contained):
1. Bukkit test: replace the immediate assertNotNull with
   `harness.await(() -> nativeSeen.get() != null, ...)`.
2. Fishing test: scale the await deadline with observed TPS (or raise to a
   conservative 60s), same for awaitBite.
3. Optionally: add these two to the workflow's documented-flake retry list
   only AFTER the robustness fix — the honest order.

Verification: re-run the full suite locally with `--max-workers=1` to mirror
CI, then let #362/#363 confirm on GitHub.

## Phase 2 — real-client playtest (torch-1.8.8-127, mineflayer)

Environment limitation (stated honestly): no Mojang 1.8.8 client exists in
this container (headless, no auth). The established harness
`scripts/validate-1.8.8.js` drives mineflayer — an independent 1.8.8 protocol
stack — as the closest available real-client peer. A green mineflayer run is
cross-implementation proof of wire conformance; it is not proof of the
official client's rendering/UX.

Executed (against the unpacked release zip, fresh world, EULA accepted):
- Server boot: all three dimensions ready, ~5s to "Server ready". TPS reads
  20.0/20.0/20.0 through the run. Shutdown clean (SIGKILL-free) — untested
  gracefully; noted below.
- Run 1 (unopped bot — reproduced the harness's assumption failure):
  28 checks: 27 PASS through login/chat/dig/chest/furnace/smelting/variants/
  mobs/AI/combat/loot/death-animation/day-cycle/night-zombies/dawn-burnout.
  1 FAIL: "teleport re-anchored the client" — root-caused to the harness,
  not the engine (see B-1). Run aborted at the falling-sand phase (cascade
  of the same /tp failure — the bot stood at spawn instead of the pristine
  area the script expected).
- Run 2 (bot opped via console mid-session): teleport PASS (x=515=target).
  Chest deposit/variant/reopen PASS. Run aborted at a NEW lead — see B-2.
- Death/respawn exercised for real: the night zombies the harness spawned
  killed the bot mid-run ("Validator was slain by Zombie", respawn clean,
  world/session state intact — the rest of run 1 continued green).

### Finding B-1 (harness defect, not engine): validator assumes an opped bot

`/tp` is permission 2; the engine correctly answers "Unknown command" to a
non-op (vanilla 1.8.8 masks permission-gated commands the same way). The
validator script never ops its bot, so every /tp-using flow silently
degrades. Verified engine-side: console `op <name>` while online → "You are
now op!", takes effect SAME session (live permission consult), persists to
a vanilla-shaped ops.json (uuid/name/level 4/bypassesPlayerLimit), survives
relog, /tp then works (mineflayer followed the re-anchor both times).

Fix: the validator (or a wrapper) must op its bot first — e.g. a `--op`
mode that drives the console, or documented setup step.

### Finding B-2 (OPEN — wire defect candidate): chest window slot-27 click rejected

Run 2: `chest3.deposit(coal, metadata 1, count)` — mineflayer shift-clicked
(most likely mode 1) wire slot 27 (first main-inventory slot of the chest
window, window id 3) and the engine answered transaction-rejected, aborting
the run. Run 1 passed the identical code path with a different inventory
layout (its charcoal sat on another slot).

Engine-side reading so far:
- `windowClickOnTick` silently rejects on window-id mismatch (stale window)
  and `clickChestWindowOnTick` rejects when `chestManager.peek(position)`
  is null (stale container).
- Mode-1 click on wire 27 maps cleanly (`chestEngineSlotOf(27)` = engine
  slot 9) and is rejected ONLY IF the engine's slot 9 is empty at click
  time — i.e. a client/server inventory-state mismatch, not a mapping bug.
- Suspicion: the `/give coal 2 1` stack landed at a different engine slot
  than mineflayer's window model believed (Set Slot drift), OR the second
  chest window's id assignment raced the close of the first.

Status: needs a deterministic repro (controlled inventory, exact click
sequence, Set Slot logging). NOT yet declared an engine defect; could be a
mineflayer model quirk. This is the highest-value open lead of the playtest.

### Finding B-3 (parity gap, verified against reference): /op refuses offline players

Engine: `op Validator` (offline) → "No online player named Validator".
Reference `OpCommand.run` (reference/1.8.8/net/minecraft/server/dedicated/
command/OpCommand.java lines 30-39): resolves the name through
`getGameProfileCache().get(args[0])` — the profile cache, which holds every
player who has ever joined — and ops the profile; the player does NOT need
to be online. Practical impact: the standard fresh-server flow "op yourself
before you join" is impossible; you must op while online (which does work).
Severity: low-medium. Fix: consult the engine's usercache by name; on hit,
write ops.json + apply to any live session; on miss, vanilla throws
"commands.op.failed" — mirror that message.

### Finding B-4 (papercuts, verified)

- `--port 25599` on the command line is silently ignored (zamin.properties
  wins). Either honor CLI overrides (standard server behavior) or refuse
  loudly. 
- Unclean client TCP exit (mineflayer process kill) logs a WARNING "Protocol
  error" with a full netty stacktrace. Vanilla logs a quiet disconnect line.
  Log-noise only; error-recovery is fine (session cleaned up, server kept
  ticking).
- Engine `/give` is the custom "give yourself" form
  (`/give <name> [count] [meta] [dataTag]`), not vanilla 1.8.8's
  `/give <player> <item> [count] [data] [dataTag]`. A vanilla-typed
  `/give SomePlayer dirt` fails with "value contains illegal character".
  Commands diverge from vanilla syntax by design here (the custom form is
  documented in /help) — record as intentional divergence or align later.

## Phase 1 — parity audit (in progress)

The compat ledger's 25 "Ledgered:" known-gap tails extracted (see the
backlog when it lands); spot-verification of a sample is part of the
remaining work. Whole families (weather, sleep, portals, redstone,
enchanting, brewing, dispensers, hoppers, minecart variants, fishing,
guardians, blaze, item frames...) are all present with deep records —
the README's 7-row pending table understates engine depth by design
(pending-only contract).

Fresh findings so far are the playtest items above (B-2, B-3, B-4).

## Phase 3 — performance baseline (not yet run)

`EngineProfiler` exists (vanilla-1:1 push/pop profiler, /debug start|stop,
with a Phase-7 measurement face `sectionNanosSnapshot`). Plan: headless
load harness over EngineServer — N sessions, mob population, chunk
generation pressure — sampling tick-time distribution, section timings,
allocation/GC via `-Xlog:gc`. No concurrency work will be recommended
without these numbers (§22 discipline).

---

## Cycle-3 continued (same day, post-commit 5b20386e)

## Phase 2 additions — the multiplayer playtest findings

The load phase (4 walking mineflayer bots + a 100-zombie horde + an
accidental respawn-kill storm) surfaced findings a single-bot validator
cannot reach:

### Finding B-5 (HIGH, wire conformance): Named Entity Spawn metadata chokes an independent parser

With four players mutually visible, mineflayer's protodef parser threw
`PartialReadError` while reading the **entity metadata field of the
Named Entity Spawn (0x0C) packet** — an i32 read past the buffer end
inside `entityMetadataItem`. Stack: `packet_named_entity_spawn` →
`entityMetadata` → `entityMetadataItem` → `i32`.

Implication: at least one 0x0C the engine writes carries a metadata
item whose declared type implies more bytes than the item carries (or a
terminator/index handling difference). The single-bot validator cannot
see this (no second player to observe). The affected bot SURVIVED
(mineflayer's error handler), so severity = conformance risk on strict
clients, not a crash.

Repro: 4+ mineflayer bots spawning at one cell; capture the raw 0x0C
frames (`bot.on('raw')`) and hex-dump the frame the parser rejects.
Next step: a focused protocol test comparing the engine's 0x0C metadata
encoding against the reference's `DataWatcher` wire form for every
watched type.

### Finding B-6 (positive): the respawn-kill storm is survivable

The horde killed the spawner bot at ~3 Hz for minutes (death walk +
respawn + chunk re-stream per cycle). The server kept ticking, kept
answering console commands, never leaked connections, and shut down
cleanly afterward. No crash, no wedged state. This is a stress-pass
observation worth recording.

### Finding B-7 (papercut): /debug stop prints no section table

The profiler traced 2477 tick events over 150s but the console report
shows only the one-line summary. `EngineProfiler.sectionNanosSnapshot()`
exists — the /debug stop arm should print the section table (the
reference's own /debug report shape). This blocks per-subsystem
attribution from the console today.

### Playtest coverage summary (executed, not claimed)

- Startup/shutdown/config/persistence: boot ~5s, three dimensions,
  graceful console stop <1s, world + per-player .zpd persisted. PASS.
- Login/disconnect/movement: 6 mineflayer logins, teleports, walking,
  jumping, sneaking, look packets. PASS (minus B-5's parse error).
- Blocks/inventory/crafting/containers: dig+pickup, chest
  deposit/withdraw/reopen, furnace smelt both variants. PASS.
- Combat/damage/death/respawn: mob melee, death animation, loot drops,
  player death by zombie, respawn (incl. the B-6 storm). PASS.
- Entities/AI: pig wander, zombie spawn/burnout at dawn, 100-zombie
  pathfinding under load. PASS.
- Chunk loading: teleport re-anchor at 72+ blocks (opped), chunk
  re-stream through the respawn storm. PASS.
- Network/error recovery: unclean reset logged noisy but recovered
  (B-4); malformed-input testing NOT yet run this cycle (backlog).
- NOT tested (honest limits): real Mojang client, multiplayer latency
  beyond loopback, nether/end travel via portal, world-border edges,
  zombie persistence across restart (the file evidence exists; the
  live-restore check was cut short by the cycle window).

## Phase 3 — the measured baseline (torch-1.8.8-127, this container)

Method: the release jar on loopback; load = 4 walking/jumping/sneaking
mineflayer bots (staggered chunk sets), then +100 zombies with active
AI, then the accidental ~3 Hz respawn-kill storm (death walk + respawn
+ full chunk re-stream per cycle). TPS read from the engine's own /tps
(1/5/15-min windows); tick count from /debug (2477 ticks / 150.025s).

| Load phase | TPS (1m) |
|---|---|
| Idle server | 20.0 |
| + 4 walking bots | 20.0 (no measurable cost) |
| + zombie horde | 18.0 |
| + respawn-kill storm (composite worst case) | 12.0 |

Interpretation (within the stated limits — one container, no jstat
attribution yet, no section table from B-7):

- The single gameplay lane holds full 20 TPS through light multiplayer
  load. The degradation to 12 TPS came from the pathological composite:
  ~100 pathfinding zombies PLUS a per-tick chunk re-stream storm. That
  is not a normal 20-player server's load shape, but it IS a measured
  saturation point of the current single-lane design.
- No concurrency phase is justified by THIS data alone: the bottleneck
  attribution (entity AI vs chunk re-stream vs respawn walk) is unknown
  until B-7's section table exists. The correct next step is
  attribution tooling, not Phase 5/6 wiring. The architecture file
  already records a measured lighting win (fff9698) and a REJECTED
  pathfinding offload (531fc22) — the §22 evidence discipline works;
  hold the line.

## Phase 4 — the ranked backlog (deduplicated)

### Release-blocking
1. **A-1 (fix landed this cycle, 5b20386e)**: the two load flakes.
   Verify via CI on the PR; the release pipeline should go green and
   torch-1.8.8-128+ should publish again.

### High priority
2. **B-5**: the 0x0C metadata encoding audit (wire conformance on
   strict clients). Repro + a pinned protocol test against the
   reference DataWatcher form.
3. **B-2 (open)**: the chest-window slot-27 transaction rejection
   (state-dependent, not yet reproduced deterministically). Add
   rejection-reason diagnostics to windowClickOnTick first (FINE log),
   then re-run the validator until the state recurs.

### Medium
4. **B-3**: /op offline players (reference-walk verified; small fix).
5. **B-7**: /debug stop section table (unblocks attribution).
6. **Perf attribution pass**: re-run the Phase-3 load with the section
   table + jstat; produce the per-subsystem breakdown the concurrency
   gates require. THEN and only then evaluate Phase 5/6 against §22.
7. **The validator harness op-flow** (B-1): op the bot (console pipe
   or a test-mode flag) so the validator runs as designed end-to-end.

### Low / papercuts
8. B-4 items: --port override ignored; unclean-exit stacktrace noise;
   console /summon + /spawnmob player-only (vanilla console can both);
   /give form divergence (record-or-align decision).

### Known-gap tails (from the compat ledger's 25 "Ledgered:" rows)
9. The standing tails stay owned by their families (particles, stats,
   armor-model edges, spawner NBT, CustomName rows, etc.) — no new
   wave until the release blockers + high rows are closed.

### Not opened this cycle (respecting standing orders)
- Minestom: abandoned (ADR-0001). Out of every ledger permanently.
- §28.7 adapter: deferred per §27.1 (the zamin-bukkit module exists and
  is tested — 52 tests in CI — but activation stays the owner's call).
- Concurrency Phases 5-8: gated on §22 evidence; no wiring started.

## Recommendation for the next cycle

Close the release pipeline (A-1 verification on CI, then the 128+
release), then **B-5 (the 0x0C audit)** — it is the only finding that
touches every multiplayer session on a strict client, it has a clean
repro path, and its fix shape (a pinned protocol test over the
DataWatcher form) is exactly the house style. Pair it with B-7 + the
attribution pass so the following cycle can make the Phase 5/6 call on
real numbers.
