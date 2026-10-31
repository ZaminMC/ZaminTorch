# The wall-clock test-deadline census (ML-001)

Provenance: `docs/CYCLE3_AUDIT.md` Phase 4's post-PR addendum — release runs
#361/#362 tripped three DISTINCT timing tests across two runs
(`FishingApiAcceptanceTest` two methods, `BukkitEventBridgeGroupThreeTest`
one racy MONITOR assert, `RconProtocolTest` a 5-second FIN-WAIT drain), and
the verdict generalized: the suite carries a POPULATION of wall-clock
deadlines, and each loaded CI run trips a different member. PR #37 hardened
the two OBSERVED engine-side members; this census is the population audit
the verdict demanded, and ML-002 (the conversions) lands against it.

Scope: EVERY wall-clock dependency across ALL five module test trees —
deadline-bounded poll loops, standalone fixed sleeps, latch/future timeouts,
`@Timeout` annotations, `nanoTime` budgets, and elapsed-time assertions.
436 test files scanned; 644 independent deadline sites enumerated below.

Method: `scripts/test_deadline_census.py` extracts every site (kind, value,
line, snippet) into a TSV; `scripts/test_deadline_census_doc.py` renders this
document from that TSV. Poll-interval sleeps (216) and bare
`currentTimeMillis`/`nanoTime` reads (410) are the loop MACHINERY of the
deadline-bounded poll loops (272) and the condition-bounded polls
(`while (cond) { ... Thread.sleep(n); }` — already the gold form: an
unbounded await on an engine-visible condition) — machinery rows are folded
into their parent loop and carry no independent deadline. Loop membership
is decided by a brace-depth walk (strings and comments blanked, do-while and
multiline headers handled), not by a proximity heuristic. The classification of every non-mechinery site is
below; the audit lists name the hand-adjudicated rows.

## The classification vocabulary

- **C-A — unjustified deadline.** The bound cannot cover the awaited
  cadence: either the deadline is below the product's own guaranteed
  tick-cadence (the fishing 20s vs the 100–900-tick bite cadence was the
  observed instance), or the bound has no product basis at all (the RCON
  5s FIN-WAIT drain). These MUST convert (ML-002).
- **C-B — missing engine-visible condition.** A fixed wall-clock sleep
  standing where the engine's own tick clock is the honest clock: waits for
  submitted work to drain onto the tick lane, tick-cadence windows ("~12
  ticks", "18 ticks nominal"), negative-proof windows ("nothing may arrive
  late"), and mining/physical floors ("grass by hand: 18 ticks nominal,
  floor 546ms"). The engine is rate-limited to 20 Hz with NO burst
  catch-up (`EngineTicker.runLoop`), so under load a wall sleep observes
  FEWER engine ticks than intended — the drift the census exists to retire.
  These convert to `EngineAwaits.awaitTicks(...)` (ML-002).
- **C-C — genuine product timeout / justified liveness bound.** Either the
  deadline IS the product's own wall-clock behavior (SO_REUSEADDR drain,
  no-hang bounds, parallel-speedup measurements), or a generous liveness
  backstop on a poll that ALREADY awaits an engine-visible condition
  (delivery-bound submissions: 3s = 60 nominal ticks of headroom on a
  next-tick read). These stay, documented.

## The disposition of the three observed failures (#361/#362)

1. `FishingApiAcceptanceTest` (both methods) — the deadline-below-cadence
   member. HARDENED by PR #37 (`retrieveInWindow`, the 90-second
   `awaitBite`). The census rows below show the post-#37 form: 20s/8s/90s
   liveness bounds over engine-visible conditions — C-C.
2. `BukkitEventBridgeGroupThreeTest` (the MONITOR assert) — the racy
   instant-assert member. HARDENED by PR #37 (awaits the observer). C-C.
3. `RconProtocolTest.theEmptyOutputAnswersOneEmptyPacket` (the 5s FIN-WAIT
   drain, lines 333-340) — NOT hardened by #37. The 5-second bound has no
   product basis (FIN_WAIT is an OS cadence; the engine's restart law only
   requires the rebind to eventually succeed; the 60s TIME_WAIT tail is
   deliberately never waited out). **C-A — converted in ML-002**: the
   drain window rises to 30s (600 beats at 50ms) with the #362 evidence
   recorded; the detection (`restarted.isRunning()` MUST answer true) is
   unchanged.

## The conversion ledger (ML-002's mandate set)

| family | sites | files | action |
| --- | --- | --- | --- |
| C-B fixed sleeps: unannotated settles | 211 | — | `awaitTicks(ceil(ms/50))` |
| C-B fixed sleeps: tick-cadence (annotated) | 34 | — | `awaitTicks(annotated ticks)` |
| C-B fixed sleeps: submit-drain settles | 11 | — | `awaitTicks(ceil(ms/50))` |
| C-B fixed sleeps: negative-proof windows | 19 | — | `awaitTicks(window ticks)` |
| C-A dispenser negative windows (300ms–2s) | 9 | 1 | `awaitTicks` windows |
| C-A RCON FIN-WAIT drain | 1 | 1 | bound → 30s, detection unchanged |
| **total conversion sites** | **285** | **110** | one shared utility |

The keeps, with their reasons (the census's C-C population):

| family | sites | reason |
| --- | --- | --- |
| poll-loop liveness deadlines (≥3s) | 245 | backstop over an engine-visible condition; 30–600x headroom over the awaited settle |
| short poll bounds audited individually (<3s) | 18 | delivery-bound probes / retry beats inside outer bounds / already-tick-clock forms — see the audit list |
| latch/future timeouts | 94 | delivery-bound (next-tick scheduler drains, loopback wire frames); 3–20s bounds on sub-100ms cadences |
| elapsed-time assertions (nanoTime) | 3 | the wall-clock behavior IS the product property (parallel speedup ≥100ms, no-hang <5s) |
| `@Timeout` (AdversarialContractTest 60s) | 1 | adversarial deadlock/starvation bound — genuine |
| launcher terminal sleeps | 2 | no engine in scope; dumb-terminal pipe echo — no engine-visible condition exists |

The workflow's documented-flake retry list (LifecycleEvent / HopperSystem /
HopperWindow) is CI-side mitigation, not a census conversion: their asserts
are engine-condition polls already; the census rows show their bounds and
they stay as-is.

## The audit list (hand-adjudicated rows)

The 27 sub-3-second poll bounds, the 3 timed assertions, and the @Timeout
were read line-by-line. Adjudication:

- **Converted (C-A)**: `DispenserSystemTest` lines 72/205/379 — the
  three re-dispense NEGATIVE windows (a forbidden re-dispense must be
  watched for a tick-exact window, not a wall-approximate one; converted
  to `awaitTicks(8/6/6)`) and `RconProtocolTest:333` (above).
- **Kept (C-C), corrected by the landing**: `DispenserSystemTest` lines
  393/414/425/451/479 turned out on closer reading to be DELIVERY-bound
  probe polls (`result.get() == null` / `!ran.get()` over submitted
  reads, 1-2s bounds on next-tick deliveries — the same generous family
  as the ArmorStand inner beats), not negative windows; they stay.
- **Kept (C-C), with evidence**: `ArmorStandSystemTest:385`,
  `DaylightDetectorSystemTest:174`, `DecorationBlastTest:201`,
  `DecorationSystemTest:528`, `JukeboxSystemTest:356`,
  `NoteBlockSystemTest:342` — the 200ms `inner` bounds are retry BEATS
  inside 30s outer loops (a beat that expires falls through to the next
  attempt; no assertion fires on the inner bound). `ThornsAcceptanceTest:185`,
  `ThornsMobVictimAcceptanceTest:132` — 2s bounds on the same-tick thorns
  reflect walk (40 ticks of headroom). `LeadSystemTest:411` — a submitted
  probe's delivery. `AnvilImportApiAcceptanceTest:232` — ALREADY the
  tick-clock form (polls `apiWorld.totalTicks()` with a 2s backstop; the
  precedent EngineAwaits generalizes). `PlayerSaveSplitTest:385`,
  `ContainerSavePipelineTest:191` — the blocking-I/O lane's own
  `failedCount()` is the polled condition. `FishingApiAcceptanceTest:275` —
  the hammer thread's 2s stress window is itself the product surface
  (concurrent hammering duration). `PacketHookApiTest:123` /
  `PacketHookInboundApiTest:125` — 1.5s bounds on loopback wire frames
  (delivery-bound, not tick-cadence-bound). `HopperSystemTest:274/296/313/332`
  — submitted read probes with 2s bounds (40 ticks of headroom on a
  next-tick read).

## The loopback-delivery family (the landing's own catch)

The conversion sweep's first protocol-suite run caught a real distinction
the census now records: a wait that follows `client.send*` /
`consoleCommand` guards the LOOPBACK NETTY DELIVERY of that frame — a
thread that is not the tick lane — so `awaitTicks` does not order it
(`HorseIntegrationTest`'s wheat ladder failed exactly this way: four
engine ticks elapsed before the `/give` frame reached the command walk).
Disposition: the dig-DURATION waits are tick-cadence and stay converted
(a dig FINISH can only arrive late enough — the floor is a minimum); the
give/chat/slot/position delivery waits (23 sites across 8 protocol
integration tests) are REVERTED to their wall sleeps and reclassified
C-C — loopback delivery, no engine-visible tick ordering (where a clean
engine condition exists, the conversion awaits it instead: the wheat
ladder now awaits the wheat reaching the inventory).

## The shared utility's contract (ML-002)

`zamin-core/src/testFixtures/java/net/zaminmc/torch/testing/EngineAwaits.java`
—one small utility, the repo's established harness style, used everywhere:

- `awaitTicks(EngineWorld world, long ticks, String what)` — awaits the
  engine's own tick clock (`world.totalTicks()`, the volatile counter every
  tick advances, `EngineWorld.java:793`) until `ticks` more ticks COMPLETE.
  The wall-clock backstop is a liveness guard for a STALLED engine
  (60x the nominal window, floor 30s), never a performance assertion: a
  slow runner changes WHEN the ticks complete, never HOW MANY the wait
  covers. This is the load-immune form the audit prescribed.
- `awaitCondition(EngineWorld world, BooleanSupplier condition, long tickBudget, String what)`
  — polls a condition and fails only when the engine COMPLETED
  `tickBudget` ticks without it: the honest tick-scaled deadline (failure
  semantics stay meaningful — the budget is the product's own cadence, not
  a wall guess). Same stalled-engine backstop.

Sleep→ticks mapping: `ceil(milliseconds / 50)` — the nominal 20 Hz the
engine rate-limits itself to. Where the site's own comment states the tick
intent ("~12 ticks", "26 ticks at 20 Hz", "18 ticks nominal"), the stated
count wins over the division.


## zamin-core

### `AchievementsApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 281 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 212 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `AnvilImportApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 232 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 325 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 189 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `AsyncComputeApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 68 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 128 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `BeaconSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 59 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 200 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 71 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `BiomeDataApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 343 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 300 | fixed sleep | 750ms (~15t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(15)` |

### `BlockEntityApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 158 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `BlockInteractionAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 54 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |

### `BlockingIoApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 72 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 130 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `BodySurvivalAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 356 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 174 | fixed sleep | 4500ms (~90t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(90)` |

### `BonemealArmsAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 66 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 90 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(3)` |
| 112 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `BrewingStandApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 60 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `BuildingBlocksAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 55 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 79 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 118 | fixed sleep | 80ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |

### `CaneAndCactusAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 57 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 80 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 85 | fixed sleep | 80ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 101 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `CauldronAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 60s | C-C liveness bound (engine-visible condition polled) | keep |
| 131 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `CaveSpiderEntityTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 68 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `ChunkGenerationOffloadTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 122 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `ConsoleCommandTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 27 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 30 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |

### `ContainerApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 119 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `ContainerLockAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `ContainerOpenEventTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 74 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `CreeperRowsAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 59 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 143 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 149 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `CritParityAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 220 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `DecorationsApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 330 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `DigHandlerGatesAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 80 | poll loop deadline | 45s | C-C liveness bound (engine-visible condition polled) | keep |
| 101 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(4)` |
| 124 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(18)` |
| 146 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(18)` |
| 148 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 176 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 179 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(18)` |
| 187 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 189 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(18)` |
| 217 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(18)` |
| 245 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 270 | fixed sleep | 100ms (~2t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(2)` |
| 295 | fixed sleep | 100ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 297 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 299 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 305 | fixed sleep | 700ms (~14t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(14)` |
| 325 | fixed sleep | 100ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 329 | fixed sleep | 1200ms (~24t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(24)` |
| 331 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `DimensionTimeSyncTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 153 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `DimensionWalkTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 52, 59, 124, 128, 69, 91 | poll loop deadline | 20s / 30s wall | C-B wall budget over a tick-denominated condition (the 80-tick portal stand clock: a 5x wall margin the hosted runner's tick stretch burned — run 532 failed BOTH attempts on it, passing at nominal speed in isolation) | converted → `EngineAwaits.awaitCondition` with 100/400-tick budgets (the stand clock's own 5x), run 532's double failure root-caused and retired |

### `DispenseBehaviorApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 63 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 173 | fixed sleep | 30ms (~1t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(1)` |
| 119 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `DoublePlantReplaceableAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 67 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 108 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `DyeFamilyPlacementAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 59 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 91 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 120 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(12)` |

### `EnchantmentEffectAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 243 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 107 | fixed sleep | 1600ms (~32t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(32)` |
| 157 | fixed sleep | 1s (~20t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(20)` |
| 170 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `EnderChestAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 59 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 71 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `EnderExitAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 162 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 108 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(6)` |

### `EngineWorldDomainTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 74 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 98 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `EntityApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 60 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 241 | fixed sleep | 60ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 148 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 219 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `EntityFamilyApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 67 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 214 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `EntitySpawnEventAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `EquipmentApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 63 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 218 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(6)` |
| 227 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `EventApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 66 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 111 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(6)` |
| 152 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 222 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(4)` |

### `ExplosionTntRedstoneApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 170 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `EyeInsertionFamilyTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 85 | poll loop deadline | 45s | C-C liveness bound (engine-visible condition polled) | keep |
| 122 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 101 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `FarmingAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 58 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 82 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(3)` |
| 95 | fixed sleep | 60ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 110 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 136 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 202 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `FireAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 94 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 108 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `FishingApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 275 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 404 | poll loop deadline | 90s | C-C liveness bound (engine-visible condition polled) | keep |
| 434 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 451 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 467 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 201 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |

### `FurnaceHopperEventAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 304 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 61 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(12)` |
| 149 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(12)` |

### `FurnaceRestartAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 119 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |

### `FurnaceTakeXpAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 116 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `GameRuleAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 73 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 225 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(4)` |
| 268 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(4)` |
| 290 | fixed sleep | 1s (~20t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(20)` |
| 294 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 366 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 383 | fixed sleep | 6s (~120t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(120)` |

### `GoalApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 63 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 154 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(12)` |
| 168 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |
| 192 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |
| 203 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 130 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `GrowthApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 222 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `HopperWindowAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 140 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 163 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `InWallPlayerAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 53 | poll loop deadline | 60s | C-C liveness bound (engine-visible condition polled) | keep |
| 100 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(3)` |
| 110 | fixed sleep | 1500ms (~30t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(30)` |

### `InteractionEventAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 65 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `InventoryApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 242 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `ItemBoxPhysicsAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 282 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 96 | fixed sleep | 1s (~20t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(20)` |

### `LeashApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 182 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `LifecycleEventAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 66 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 126 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `LitFurnaceAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 57 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 120 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `MiningXpAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 57 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 82 | fixed sleep | 80ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 99 | fixed sleep | 1100ms (~22t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(22)` |
| 122 | fixed sleep | 60ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 130 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `MissingCommandsWaveOneTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 727 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 251 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 268 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 294 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 338 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 345 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 378 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 436 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 443 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 471 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 500 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 520 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(12)` |
| 584 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 590 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 628 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 650 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 666 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 670 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 673 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 691 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 698 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `MobCombatAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 181 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 83 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `MobEventBridgeAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 90s | C-C liveness bound (engine-visible condition polled) | keep |

### `MountSeatsApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 369 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `NbtCommandsTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 109 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 147 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 154 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 175 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 196 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `NetherDimensionTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 121 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `OpenWindowApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 53 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |

### `PeacefulDifficultyTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 140 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(10)` |

### `PersistenceAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 105 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 116 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |

### `PistonPushApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 171 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `PlacementHandlerGatesAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 108 | poll loop deadline | 45s | C-C liveness bound (engine-visible condition polled) | keep |
| 125 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(4)` |
| 401 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `PlacementReachLawAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 88 | poll loop deadline | 60s | C-C liveness bound (engine-visible condition polled) | keep |
| 178 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 204 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `PlacementTileAndPlayerRowsTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 114 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `PlayerApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 56 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |

### `PlayerDataPersistenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 58 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |

### `RangedCombatAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 394 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 57 | fixed sleep | 1300ms (~26t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(26)` |
| 106 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 281 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(18)` |
| 352 | fixed sleep | 1100ms (~22t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(22)` |

### `ScheduledTickPersistenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 44 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |

### `ScheduledUpdatesApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 167 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 127 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 147 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `SchedulerApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 51 | fixed sleep | 250ms (~5t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(5)` |
| 87 | fixed sleep | 1500ms (~30t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(30)` |
| 91 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 103 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(12)` |
| 112 | fixed sleep | 1100ms (~22t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(22)` |
| 50 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 72 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `ScoreboardCommandTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 287 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |

### `SilverfishEntityTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 245 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `SlabStairsAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 54 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 83 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 175 | fixed sleep | 4s (~80t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(80)` |

### `SleepAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 60 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 83 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 125 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `SpawnEggTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 75 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 269 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(3)` |

### `SpawnProtectionAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 59 | poll loop deadline | 60s | C-C liveness bound (engine-visible condition polled) | keep |
| 130 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `SurvivalMiningAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 58 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 77 | poll loop deadline | 45s | C-C liveness bound (engine-visible condition polled) | keep |
| 96 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(18)` |
| 114 | fixed sleep | 900ms (~18t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(18)` |
| 135 | fixed sleep | 5400ms (~108t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(108)` |
| 172 | fixed sleep | 100ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 174 | fixed sleep | 700ms (~14t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(14)` |
| 206 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 229 | fixed sleep | 1200ms (~24t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(24)` |
| 257 | fixed sleep | 5400ms (~108t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(108)` |
| 272 | fixed sleep | 1200ms (~24t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(24)` |
| 296 | fixed sleep | 1200ms (~24t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(24)` |

### `TabListHeaderFooterApiTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 107 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `TabListServiceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 243 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |

### `ThornsAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 185 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 324 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `ThornsMobVictimAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 132 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 276 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `TwoStageUseLawAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 91 | poll loop deadline | 60s | C-C liveness bound (engine-visible condition polled) | keep |
| 157 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 188 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 251 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 302 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 322 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 324 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 330 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 355 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 391 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 421 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `VanillaMiningTimingTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 3s | C-C liveness bound (engine-visible condition polled) | keep |
| 158 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |
| 208 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 232 | fixed sleep | 1500ms (~30t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(30)` |

### `VehicleSpawnEventAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 63 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `VillageApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 246 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 235 | fixed sleep | 1500ms (~30t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(30)` |

### `WeatherSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 216 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 327 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(6)` |

### `WitchAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 64 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `WorldApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 199 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `WorldBorderTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 290 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |
| 146 | fixed sleep | 60ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 228 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(8)` |

### `ArmorStandSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 360 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 381 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 385 | poll loop deadline | 200ms | C-A candidate — audit list | audited below |
| 654 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(3)` |

### `DoubleStoneSlabFamilyTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 58 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 142 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 147 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 164 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 167 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `FenceGateBlocksTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 325 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 340 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `RailBlocksTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 280 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 295 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `StairsCollisionGeometryTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 375 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 340 | fixed sleep | 1s (~20t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(20)` |

### `StairsHalfBitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 57 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 77 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `TrapdoorBlocksTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 278 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 293 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `WoodDoorFamilyTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 383 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 398 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `WorldBorderPersistenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `BrewingStandSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 359 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 154 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `ContainerPersistenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 107 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 117 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |
| 63 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `ChestFacingTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 58 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 76 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `CommandStatsPersistenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 68 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `TargetSelectorTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 262 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `TitleTellRawCommandTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 178 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 160 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |

### `CommandBlockBlockTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 49 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `CommandBlockDataStoreSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 145 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 146 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 156 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `AdversarialContractTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 268 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 347 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 54 | @Timeout | value = 60, unit = TimeUnit.SECONDS | audit list | audited below |

### `BlockingIoLaneTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 132 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 190 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 206 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 244 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `ComputeSubsystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 47 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 77 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 123 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 160 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 201 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 258 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 50 | fixed sleep | 20ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 80 | fixed sleep | 20ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 108 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 114 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 184 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 190 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 242 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `CrossOwnerApiAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 359 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 373 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 86 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 123 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 276 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 309 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `CrossOwnerRouterTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 111 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 196 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `DomainWorkerPoolTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 69 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `OwnershipDomainTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 44 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 155 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 177 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `RandomTickLaneTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 279 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |

### `RegionBoundaryTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 440 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 472 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 142 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 172 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 225 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `SimulationSchedulerParallelTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 78 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 86 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `DaylightDetectorSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 170 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 174 | poll loop deadline | 200ms | C-A candidate — audit list | audited below |

### `DecorationBlastTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 164 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 178 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 197 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 201 | poll loop deadline | 200ms | C-A candidate — audit list | audited below |

### `DecorationSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 505 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 524 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 528 | poll loop deadline | 200ms | C-A candidate — audit list | audited below |

### `TileExtraSaveSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 287 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 288 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 289 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 290 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 300 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 301 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 302 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `DispenserSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 72 | poll loop deadline | 400ms | C-A candidate — audit list | audited below |
| 205 | poll loop deadline | 300ms | C-A candidate — audit list | audited below |
| 379 | poll loop deadline | 300ms | C-A candidate — audit list | audited below |
| 398 | poll loop deadline | 1s | C-A candidate — audit list | audited below |
| 419 | poll loop deadline | 1s | C-A candidate — audit list | audited below |
| 430 | poll loop deadline | 1s | C-A candidate — audit list | audited below |
| 456 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 484 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 510 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `AnvilGuiTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 473 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `DimensionAwareMobQueryTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 136 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 150 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 140 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |

### `EntityFamilySaveSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 588 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 589 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 590 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 591 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 601 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 602 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 603 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `GiantMobTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 126 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `ItemPersistenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 236 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 239 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |

### `ItemThrowerPickupTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 190 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `LavaWalkTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 289 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `MobArmorFamilyTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 725 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 729 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |

### `SummonEquipmentTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 44 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `SummonNbtTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 44 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 102 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 109 | fixed sleep | 1s (~20t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(20)` |
| 125 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `WitherSkeletonTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 264 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `PathRegionContractTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `ChestMinecartTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 192 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 289 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `CommandBlockMinecartTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 230 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `HopperMinecartTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 280 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `RidingFollowTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 255 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `SpawnerMinecartTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 315 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `VehicleDamageSourceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 269 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `VehiclePersistenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 252 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 262 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |
| 67 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(12)` |
| 210 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(8)` |

### `VehicleWorldDamageTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 166 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `EventBusUnitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 154 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `FurnaceFacingTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 56 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 69 | fixed sleep | 120ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `HopperSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 266 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 274 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 296 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 313 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 332 | poll loop deadline | 1s | C-A candidate — audit list | audited below |
| 355 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 427 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `AdventureGatesTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 93 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 134 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(8)` |
| 144 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 188 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(12)` |
| 190 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 201 | fixed sleep | 600ms (~12t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(12)` |

### `WrittenBookFlowTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 460 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 473 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |
| 284 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 291 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 304 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 345 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 353 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 362 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `JukeboxSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 352 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 356 | poll loop deadline | 200ms | C-A candidate — audit list | audited below |

### `LeadSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 411 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 437 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `MapItemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 760 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 773 | poll loop deadline | 8s | C-C liveness bound (engine-visible condition polled) | keep |

### `BlockEntityTagPlacementTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 159 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `TileNbtTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `NoteBlockSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 338 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 342 | poll loop deadline | 200ms | C-A candidate — audit list | audited below |
| 216 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 251 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `NoteDataStoreSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 106 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 107 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 117 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `PistonEntityPushTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 554 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `PistonFluidEventAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 370 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 396 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 68 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(8)` |
| 82 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(4)` |
| 260 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 313 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `PistonSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 263 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 274 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 299 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 152 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |
| 228 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |

### `AntiCheatTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `PlayerSaveSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 385 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 402 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 388 | fixed sleep | 10ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 348 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 378 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 412 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `PlayerTitleChatApiTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 98 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `PluginHostTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 367 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 163 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 316 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 326 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 363 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |
| 440 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `PluginLiveEngineAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 46 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 150 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |

### `DebugProfilerTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 71 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `DenseOwnerProfilingTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 86 | poll loop deadline | 60s | C-C liveness bound (engine-visible condition polled) | keep |
| 296 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `SystemSectionProfilingTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 82 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `RconEngineRouteIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 62 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 65 | fixed sleep | 20ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |

### `RconProtocolTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 333 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |

### `RedstoneComparatorTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 362 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 387 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `RedstoneInteractionTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 193 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 201 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 229 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 82 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |

### `RedstoneLampTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 121 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `RedstoneSourcesTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 177 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 202 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `RedstoneSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 213 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 234 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `TripwireSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 178 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 208 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `SignDataStoreSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 164 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 165 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 175 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `AchievementCommandTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 108 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `AchievementFamilyTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 712 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |
| 343 | fixed sleep | 700ms (~14t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(14)` |
| 347 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 549 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 632 | fixed sleep | 1500ms (~30t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(30)` |

### `TntSystemTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 93 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 118 | poll loop deadline | 30s | C-C liveness bound (engine-visible condition polled) | keep |

### `WeatherGameRuleSaveSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 148 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 149 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 150 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 160 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 161 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `BoardBorderSaveSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 44 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 45 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 150 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 151 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `ContainerSavePipelineTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 57 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 191 | poll loop deadline | 2s | C-A candidate — audit list | audited below |
| 194 | fixed sleep | 10ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 58 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 84 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 157 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 184 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `EffectTileSaveSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 42 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 43 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 86 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `TileStateSaveSplitTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 45 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 46 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 196 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 197 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `WorldSavePipelineTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 124 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 218 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 125 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 135 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 219 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |
| 229 | latch await | TimeUnit.MILLISECONDS | C-C candidate — audit list | audited below |

### `AnvilImportAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 133 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 255 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 385 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 649 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 965 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 1140 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 136 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 259 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 388 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 652 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 968 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |
| 1143 | fixed sleep | 50ms (~1t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(1)` |

### `LightBatchEquivalenceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `LightRegionContractTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `UnloadedBorderStormTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `MineshaftStructureTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 172 | poll loop deadline | 120s | C-C liveness bound (engine-visible condition polled) | keep |


## zamin-protocol-v1_8_8

### `AttributeMeleeIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 31 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |

### `ChestIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 142 | fixed sleep | 3800ms (~76t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(76)` |

### `ChestLidAndHeldItemIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 51 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(4)` |

### `DonkeyChestIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 76 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 84 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 86 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 94 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 104 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 106 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `DyeFamilyAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 73 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 88 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 90 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `DyeFamilyWireAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 61 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 68 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 107 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 111 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `EnchantingTableIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `FallingBlockIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 93 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |
| 60 | fixed sleep | 1200ms (~24t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(24)` |
| 139 | fixed sleep | 1400ms (~28t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(28)` |

### `FurnaceIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `HorseIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 129 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 92 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 105 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 107 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 118 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 147 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |

### `JoinChunkReproTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 52 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 89 | fixed sleep | 800ms (~16t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(16)` |

### `JoinFlowIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 224 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 236 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 161 | fixed sleep | 100ms (~2t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(2)` |
| 169 | fixed sleep | 950ms (~19t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(19)` |

### `MetadataIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 107 | fixed sleep | 850ms (~17t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(17)` |
| 123 | fixed sleep | 850ms (~17t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(17)` |

### `MobEquipmentWireOrderTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 67 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |

### `MobIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 53 | poll loop deadline | 60s | C-C liveness bound (engine-visible condition polled) | keep |
| 84 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 105 | poll loop deadline | 15s | C-C liveness bound (engine-visible condition polled) | keep |

### `PacketHookApiTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 123 | poll loop deadline | 1500ms | C-A candidate — audit list | audited below |
| 86 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `PacketHookInboundApiTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 125 | poll loop deadline | 1500ms | C-A candidate — audit list | audited below |
| 77 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `PacketSendApiTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 121 | latch await | TimeUnit.SECONDS | C-C candidate — audit list | audited below |

### `ParticleDisplayAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 77 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `ProtocolTestBase.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 65 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |

### `PvpIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 34 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 169 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 150 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `ScoreboardIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 41 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `ShutdownAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 50 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 91 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |

### `SignIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 28 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 98 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |

### `SurvivalFlowIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 62 | fixed sleep | 950ms (~19t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(19)` |
| 102 | fixed sleep | 950ms (~19t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(19)` |
| 152 | fixed sleep | 1200ms (~24t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(24)` |

### `TabListIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 127 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |

### `TestClient18.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 243 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 395 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `VanillaParityIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 34 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |

### `VehicleIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 152 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 316 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 60 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 110 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 112 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 189 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |
| 191 | fixed sleep | 200ms (~4t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(4)` |

### `VillagerTradeIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 50 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |
| 148 | poll loop deadline | 20s | C-C liveness bound (engine-visible condition polled) | keep |

### `WindowResyncIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 193 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 199 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |
| 245 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(6)` |
| 249 | fixed sleep | 150ms (~3t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(3)` |


## zamin-launcher

### `ConsoleRouteIntegrationTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 52 | poll loop deadline | 10s | C-C liveness bound (engine-visible condition polled) | keep |
| 55 | fixed sleep | 50ms (~1t) | C-C — no engine in scope (terminal pipe) | keep (documented) |

### `TerminalLogBridgeTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |

### `TorchConsoleTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 89 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 99 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 219 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 248 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |
| 92 | fixed sleep | 50ms (~1t) | C-C — no engine in scope (terminal pipe) | keep (documented) |
| 102 | fixed sleep | 50ms (~1t) | C-C — no engine in scope (terminal pipe) | keep (documented) |
| 155 | fixed sleep | 150ms (~3t) | C-C — no engine in scope (terminal pipe) | keep (documented) |
| 190 | fixed sleep | 200ms (~4t) | C-C — no engine in scope (terminal pipe) | keep (documented) |


## zamin-bukkit

### `BukkitAdversarialTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 129 | fixed sleep | 250ms (~5t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(5)` |
| 175 | fixed sleep | 500ms (~10t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(10)` |

### `BukkitCommandBridgeTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 78 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(6)` |
| 232 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — tick-cadence | convert → `awaitTicks(6)` |

### `BukkitEventBridgeGroupThreeTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 114 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(6)` |
| 190 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(6)` |

### `BukkitEventBridgeGroupTwoTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 421 | poll loop deadline | 40s | C-C liveness bound (engine-visible condition polled) | keep |
| 80 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(6)` |
| 143 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(6)` |

### `BukkitInventoryBridgeTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 177 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(8)` |
| 237 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(6)` |
| 358 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(8)` |
| 380 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — negative-proof window | convert → `awaitTicks(6)` |

### `BukkitLifecycleAcceptanceTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 99 | fixed sleep | 300ms (~6t) | C-B missing engine-visible condition — submit-drain settle | convert → `awaitTicks(6)` |

### `BukkitSchedulerBridgeTest.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 99 | fixed sleep | 400ms (~8t) | C-B missing engine-visible condition — unannotated settle | convert → `awaitTicks(8)` |

### `BukkitTestHarness.java`

| line | form | value | classification | action |
| --- | --- | --- | --- | --- |
| 138 | poll loop deadline | 5s | C-C liveness bound (engine-visible condition polled) | keep |

## The phase-2 pass (2026-10-23, the run #375/#376 evidence)

The release pipeline's first completed suite runs on the final wave-3
state surfaced four wall-clock families the census's core-module scope
missed, plus a contract flaw in the shared utility itself:

- **The EngineAwaits liveness guard, reworked two-sided**: the old
  single total-wall backstop (10x nominal, capped 300s) could not tell a
  STALLED engine from a SLOW one — a big-budget await (the fishing
  bite's 2400 ticks) false-fired "stalled" on any engine averaging under
  ~8 TPS, violating the utility's own contract ("fails only when the
  engine itself stopped ticking"). The guard is now a 60s no-progress
  STALL window (resets on every completed tick — a slow-but-alive runner
  never trips it) beside a PATIENCE cap (15x nominal, floored 30s,
  capped 15 min) that bounds the suite against a persistently crawling
  engine with its own honestly-named verdict. A new expected-miss form
  (`awaitConditionWithin` — polls, answers boolean, throws only on the
  liveness guard) covers the probabilistic outcomes a throwing await
  never could (the thorns roll's 30% no-bite arm, the fishing window's
  budget exhaustion inside a retry structure).
- **ProtocolTestBase.awaitCondition** (the whole protocol module rode
  this 5s wall): converted to the engine-tick await at the established
  400-tick budget — the module was outside the census's core scope, and
  the horse wire test's documented root cause ("wall-clock sleeps racing
  awaits", deferred to the coordinator's flake ledger) was exactly this
  helper. First converted run surfaced the tight-boundary family the
  wall form had been masking (the wheat-ladder interaction backlog, the
  drop settle) at the nominal-equivalent 100 ticks — the budget sized to
  the established generous form instead.
- **The core acceptance classes' local 20s wall helpers**
  (RangedCombatAcceptanceTest, ThornsMobVictimAcceptanceTest,
  FishingApiAcceptanceTest): converted to the shared engine-tick await
  (400 ticks — the nominal equivalent); the thorns proc window (2s wall)
  became the expected-miss poll (40 ticks); the melee i-frame settle
  (600ms wall) became its tick equivalent (12 ticks).
- **The fishing retry design repaired**: retrieveInWindow's four-attempt
  re-cast structure was defeated by its own throwing bite await — a
  budget exhaustion killed the test instead of counting as the failed
  attempt the loop exists to retry. The per-attempt window await is now
  the expected-miss form (a dead attempt retrieves its line, awaits the
  census drain, and re-casts; the final assert carries the story).
- **The horse wire test's settle sleeps** (the op/clear/saddle command
  settles): converted to the engine-visible consequences they were
  waiting for (the op level, the emptied hand, the delivered saddle).

Evidence: run #375 (the RCON/Query family — the RconBase.start()
flag/thread race, fixed separately) and run #376 (the fishing bite via
the throwing await, the ranged/thorns wall helpers, the horse wire test
via the 5s wall helper). Verification: the protocol module 148/0 twice,
the converted core families 60/0 (fishing 7, thorns 5, ranged 10, rcon
38) with the fishing interceptor's local boundary failure absorbed by
the repaired retry structure.
