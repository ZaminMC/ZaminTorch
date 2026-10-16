# Sections 27/28 Audit — the Native API Exposure + the Plugin Concurrency Contract vs the Code

The §27 (native API) and §28 (plugin concurrency) audit of
`Torch-Arcetecture.txt` against the `develop` tree at Slice 67
(torch-1.8.8-51). Evidence-based: every row names the code that exists,
the test that proves it, or the honest blocker.

## §27 — the native API exposure

### What the audit found standing

1. **The §27.10 capability ledger EXISTS and is current.**
   `NATIVE_API_CAPABILITIES.md` tracks every subsystem row with the
   engine implementation, the public entry point, the threading
   contract, the hooks, the stability class, the tests, and the honest
   omissions — sections 1-8 (server core, world, entities, mechanics,
   concurrency, events, registries, protocol). The Slice 66/67 rows
   landed with their slices (the five variant carts, the hopper cart's
   cargo, the fishing system, the giant).
2. **The §27.5 "engine object IS the handle" pattern holds** across the
   entity family (`LivingEntity`, `DroppedItem`, `Projectile`,
   `Vehicle` + `Vehicle.Cargo`), the containers, and the gamerule table
   — the handles resolve the authoritative state with no second game
   state.
3. **The §27.6 threading contract is on every ledger row** —
   owner-context mutations, any-thread snapshot reads, the loud
   off-lane refusals tested per family.
4. **The §27.7 hooks exist at the named decision points**: the
   pre-commit spawn gates (`VehicleSpawnEvent`, `MobSpawnEvent`, the
   projectile gate), the post-commit `BlockChangedEvent`, the chat
   event, the death event — each with cancellation at the documented
   point and exception isolation in the bus.

### The honest gaps (recorded, not hidden)

- ~~**The variant carts' internal state has no read surface.**~~
  **CLOSED, Slice 68:** `Vehicle.variantState()` → `VariantState`
  (Furnace/Tnt/Spawner/Command views) — the engine object IS the
  handle; the read view carries
  `VariantStateApiAcceptanceTest`. The variant write verbs (the fuel
  feed, the prime, the type/delay rows, the command rows) stay
  engine-side for the ownership-gated write wave.
- ~~**The fishing system exposes the engine facade** (`Server.fishing()`)
  — the cast/retrieve verbs and the bobber reads — but no loot-table
  override and no catch intercept.~~ **CLOSED, Slice 69:** the
  `LootSource` override (per-band, null falls through to the vanilla
  roll) + the ordered `CatchInterceptor` chain (observe/replace/cancel —
  a cancel answers the bare 0) landed with `FishingOverrideTest` (7).
- ~~**The protocol section stays internal**~~ **CORRECTED, Slice 70's
  pass:** the audit note was stale — the interception surface has been
  public since Slices 15c (outbound) + 23g (inbound) at
  `Server.protocolHooks()`, and the ledger row was current. The row's
  genuinely missing leg was **packet construction**, closed in Slice 70:
  `ProtocolHooks.send(Player, packetId, payload)` composes a PLAY-state
  frame through the same pipeline and the same interception walk as
  engine traffic (`PacketSendApiTest`).

## §28 — the plugin concurrency contract

### The engine-side halves: IMPLEMENTED and adversarially tested

| §28 requirement | Evidence on `develop` |
|---|---|
| 28.1 execution categories (simulation / compute / blocking I/O / event) | the `server/concurrent/` lanes: `OwnershipDomain` + `SimulationScheduler` (simulation), `ComputeSubsystem` (compute, workers with zero mutation authority), `BlockingIoLane` (I/O never touches simulation workers), `EngineEventBus` (tick-lane dispatch) |
| 28.2 reciprocal owner requests never block | `AdversarialContractTest.reciprocalOwnerRequestsNeitherBlocksTheOther` |
| 28.2 same-pool self-wait detected/rejected | `AdversarialContractTest.samePoolSelfWaitIsDetectedAndRejectedLoudly` |
| 28.3 wrong-owner mutation fails loudly with context | `OwnershipDomainTest.wrongContextMutationFailsWithDomainAndOperationContext` + the live engine's `OwnershipViolationException` carries the domain/operation |
| 28.3 stale async results never apply | `ComputeSubsystemTest` (the fresh-applies/stale-discards pair) + `OwnershipDomainTest.staleGenerationResultIsRejectedFreshOneApplies` |
| 28.3 migration during work (no two writers) | `OwnershipMigrationTest` (the §9 phase machine, fault-injection tested; cancelled tasks never resurrect) |
| 28.4 event ordering/cancellation/exception/reentrancy | `EventBusUnitTest` (priority-then-registration order, cancellation visible to the firing caller, `anEscapingExceptionDoesNotStopTheDispatch`, `reentrantFireNestsLikeMethodCalls`, unsubscribe mid-dispatch) |
| 28.4 cross-owner transactions without duplication/loss | the §11 boundary scope family — `RegionBoundaryTest` (the piston push AND sticky pull across the seam, the redstone decay ladder, the explosion, the hopper transfer — all same-tick, vanilla-exact) |
| 28.8 case 9 queue saturation / noisy owner | `AdversarialContractTest.theNoisyOwnerOverloadsItselfWhileUnrelatedOwnersRetainProgress` + the scheduler's loud admission refusals |
| 28.8 case 10 blocking I/O pressure | `AdversarialContractTest.blockingIoPressureNeverConsumesSimulationWorkers` |
| 28.8 case 12 shutdown | `AdversarialContractTest.theFullStackShutsDownWithWorkInFlightAndQueued` (total, idempotent, no dependency-cycle wait) |
| 28.9 observability | the scheduler's §17 telemetry floor (queue depth, execution time, the admission counters) |

### The plugin-side halves: the honest blocker

- **28.5 the plugin lifecycle state machine (DISCOVERED → LOADED →
  ENABLING → ENABLED → DISABLING → DISABLED → UNLOADED) with
  resource-scope cleanup: NOT STARTED.** The engine has no plugin host —
  no discovery, no classloading, no per-plugin resource ledger. The
  §27.1 order places the plugin host after the native API matures; the
  adversarial case 6 (disable during execution) is BLOCKED on it.
- **28.6 plugin-created concurrency quotas: NOT STARTED** (same
  dependency — the plugin host owns the quota surface; the engine's
  bounded lanes it would ride exist and are tested).
- **28.7 the legacy main-thread compatibility strategy: NOT
  INVESTIGATED** — the Bukkit/Spigot/Paper adapter does not exist yet
  (the engineering table's own row); the native API deliberately does
  NOT inherit its constraints (§28.7's closing rule).

### The audit's verdict

The §28 **runtime contract** — the lanes, the enforcement, the
deadlock-proof cross-owner protocols, the event semantics — is
implemented and carries direct adversarial tests for ten of the twelve
§28.8 cases (cases 3, 4 ride their subsystem suites; case 11 rides the
boundary scope suite). The two blocked cases (6 disable-during-execution,
and the plugin-half of 9's quota enforcement) await the plugin host
slice, which is the §27.1-ordered next major wave after the vanilla
mechanics coverage closes.
