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
