# Vanilla Conformance Audit — the continuous verifier's report

The twenty-first and later verifier passes (the investigation stream that
began with PR #54). This file is the persistent record for findings whose
families have no findings doc yet: each finding carries a stable VT-NNN id,
its verification-pass history, and its disposition. When a finding
graduates to a fix, its family's own record (the findings doc, the Wave row
in `Torch-Arcetecture.txt` §29, the WORKLOG entry) takes over and the row
here closes with the PR link. The predecessor records: the fourteenth
pass's farming family (PRs #69-#71), the fifteenth's fall-path family
(PRs #72-#76, `FALL_PATH_FAMILY_AUDIT_FINDINGS.md`), the sixteenth's
item/orb envelope, the seventeenth's vine family (PR #79), the eighteenth's
hopper & container family (PRs #82/#86, `CHEST_FAMILY_AUDIT_FINDINGS.md`),
the nineteenth's explosion family (PR #85,
`EXPLOSION_FAMILY_AUDIT_FINDINGS.md`), and the twentieth's double-chest
correction (PR #86) with F-TC1 confirmed for its own lane.

Baseline for the twenty-first pass: `origin/develop` at `dcd2e139`
(2026-10-30, Waves FL4 + FL4-F1 landed — the per-dimension fluid
simulation the pass-20 queue statement had named "F-FL4"; the house
landed it as an architecture wave). The reference is `reference/1.8.8/`
(the Mojang-mapped decompile; the same tree the mechanics zip carries).

## The twenty-first pass — the FL4 wave's three demand-named tails, confirmed

The FL4 wave's own ledger named its discoveries: "the fire family's
per-dimension scheduling, the placement families' use-path world routing,
the ice family's melt arms" (each its own future pass). This pass took all
three up, verified each through the full three-pass discipline, and found a
fourth, larger shape underneath the second: the whole dig/use/place surface
— not just the placement families — is overworld-bound.

- **VT-001** — the fire family never ticks in the nether or the End
  (CONFIRMED, three passes complete, fix-ready).
- **VT-002** — the entire dig/use/place surface reads and writes the
  overworld field: nether gameplay is dead and nether actions corrupt the
  overworld (CONFIRMED, three passes complete; the use-path family arms
  landed as Wave FL4-F3, the remaining dig/place GENERIC slice landed as
  PR #90 — see the twenty-second pass's record below).
- **VT-003** — the ice family's melt and break-to-water arms are absent
  (CONFIRMED, three passes complete, fix-ready).

---

## VT-001 — the fire family never ticks in the nether or the End

**Subsystem:** block family scheduling / fire (all dimensions). **Status:**
CONFIRMED — three verification passes complete, fix-ready.

**Affected engine files and methods:**
- `server/EngineServer.java` — the fire wiring block (~lines 1139-1166):
  `FireBlocks.wire(..., world, endWorld, scheduledTickSystem, gameplayRandom)`
  and the sixteen AGE-state `registerTicker` calls, all against the
  OVERWORLD's `scheduledTickSystem`.
- `server/block/FireBlocks.java` — `PlacementListener.onBlockReplaced` (the
  `ticks.scheduleTick(position, newId, 30 + onAddedRolls.nextInt(10))` arm)
  and `tick` (the re-arm at the reference's line 165).
- `server/EngineServer.java` ~lines 2152-2156 — the nether/End
  `ScheduledTickSystem`s carry the FLUID families' tickers alone; no fire
  ticker is registered on either.

**Vanilla reference:** `reference/1.8.8/net/minecraft/block/FireBlock.java`
— `onAdded` (lines 318-326) schedules through `worldIn.scheduleTick` (the
world the flame stands in), and the reference's `ServerWorld` machinery
(`tickUpdates`, one queue per world) dispatches `tick(world, pos, state,
random)` in THAT world. `tick` lines 144-226: the age progression
(`i + random.nextInt(3) / 2`), the rain douse, the fuel-less death
(`!hasFlammableNeighbor && (!hasSolidTop || i > 3)` — lines 166-170), the
max-age 1/4 roll, the six burn walks, the spread box, and the End's
eternal-bedrock arm (lines 150-154: the `world.dimension` consult — the
engine's own `endWorld == world` check at `FireBlocks.tick`).

**Observed engine behavior:**
1. A flame placed in the nether (lava ignition, flint and steel, any arm)
   fires `PlacementListener.onBlockReplaced` on the nether's change
   listener — but the listener schedules through `FireBlocks.scheduledTicks`,
   the seam wired to the OVERWORLD's queue.
2. The overworld queue's entry then dies in `doScheduledTicks`'s
   same-family gate: at pop time the system reads `world.getBlock(pos)` —
   the OVERWORLD's cell at the nether's coordinates — which is not fire, so
   the entry is dropped (or, when the overworld cell happens to hold the
   same fire state, the OVERWORLD's flame receives a phantom extra tick).
3. The nether flame never ticks at all: no aging, no spread, no fuel
   consumption, no death, forever. The End is the same; the eternal-bedrock
   check is unreachable dead code there (it only runs inside a tick).

**Verification history:**
- **Pass 1 (source, 2026-10-31):** the wiring block read whole — the seam
  (`scheduledTickSystem`, constructed over the overworld at EngineServer
  ~1139), the sixteen overworld-only `registerTicker` calls, the
  `netherScheduledTicks`/`endScheduledTicks` registration sites (fluids
  only, ~2152-2156), and the listener's schedule arm. The reference's
  per-world schedule shape read beside it (`ServerWorld.tickUpdates`,
  `FireBlock.onAdded`'s `worldIn`).
- **Pass 2 (behavioral, 2026-10-31):** the discovery seat
  `DimensionTailsDiscoveryTest.theNetherFireOnStoneDiesOnItsFirstScheduledTick`
  — an `fire_age5` flame on a bare nether stone floor (no flammable
  neighbor, age > 3) must die on its first scheduled tick (the reference's
  lines 166-170 death); 240 completed nether ticks later the engine still
  holds `minecraft:fire_age5` — `expected: <minecraft:air> but was:
  <minecraft:fire_age5>`. Red on develop, exactly the frozen-flame law.
- **Pass 3 (independent re-verification, 2026-10-31):** the queue-level
  probe (run 2026-10-31, output verbatim): after the nether placement,
  `server.scheduledBlockTicks().pendingAt((64,101,64), fire_age5)` answers
  `BlockPosition[x=64, y=101, z=64], block=minecraft:fire_age5, due=37`
  with the OVERWORLD queue holding exactly that one entry — the nether
  flame's schedule physically sits in the overworld's queue. The
  compensating-behavior hunt found none: no fire ticker on the nether/End
  systems, no other dispatch path, and the mis-scheduled entry's
  same-family gate outcome traced in `ScheduledTickSystem.doScheduledTicks`
  (the drop). The engine's own javadoc claims the family is "FireBlock 1:1"
  — the misroute is not an intentional divergence; the FL4 wave's ledger
  itself names the tail as owed work.

**Proposed minimal correction:** parameterize the fire family per dimension
the FL4 fluid shape: register the sixteen AGE tickers on the nether's and
the End's `ScheduledTickSystem`s beside the overworld's, and route the
placement listener's schedule (and `tick`'s re-arm) through the CHANGED
world's own system (a `Map<EngineWorld, ScheduledTickSystem>` seam or the
per-world consult). The `endWorld == world` eternal check then becomes a
plain `world.dimension() == 1` read. The nether lava ignition path already
writes the flame into the nether world (FL4's FluidWorld adapters), so the
only moving parts are the seam and the registrations.

**Regression risks:** the overworld fire family's seats
(`FarmlandTrample`, the fire family's own tests, `BlockUpdateSystem`) must
stay green — the overworld wiring itself is untouched; the seats that pin
the nether/End flame's behavior are new.

---

## VT-002 — the dig/use/place surface is overworld-bound (the FL4 "use-path world routing" tail, whole)

**Subsystem:** player interaction (dig, use, place) across dimensions.
**Status:** CONFIRMED — three verification passes complete;
architecture-scoped (the bounded correction is specified below; its
implementation is its own pass, per the FL4 wave's own ledgering of the
placement-families tail).

**Affected engine files and methods:**
- `server/interaction/BlockInteractionService.java` — the single
  `private final EngineWorld world` (line 73, constructed at EngineServer
  ~3361 over the overworld) behind every dig/place walk:
  `miningStartOnTick`, `miningFinishOnTick`, `tryMineBlock`, `creativeBreakOnTick`,
  `placeOnTick`, `publishDrops` (the three-arg `spawnDropAtBlock` — the
  TF-9 dimension row's own "honest 0" javadoc names this class of tail),
  `tickMining`, `perTickProgress`, `isSubmerged`.
- `server/EngineServer.java` — the use-dispatch arms
  (`useItemArmOnTick`, `useBlockArmOnTick` and the ~40 family arms they
  call: `useFlintOnTick`, `useFarmingOnTick` (27 bare world consults),
  `placeSignOnTick`, `placeDoorOnTick`, `placeRailOnTick`,
  `toggleDoorOnTick`, `placeChestFacingOnTick`, `placeRedstoneTorchOnTick`,
  `placeBedOnTick`/`sleepOnTick`, `useDragonEggOnTick`, `useCakeOnTick`,
  ... — the full enumerated list was captured by a method-level scan:
  every arm consults the bare `world` field; only the FL4-migrated arms
  (`useBucketOnTick`, `useSpawnEggOnBlock`, `useEnderEyeInAir`,
  `useSpawnEggInAir`) read `worldFor(session.dimension())`.

**Vanilla reference:** `ServerPlayerInteractionManager` is constructed per
world and its `this.world` is the player's own; `handlePlayerDig` /
`handleItemUse` (ServerPlayNetworkHandler) operate on `player.getEntityWorld()`
— the dig, the use, and the placement all read and write the world the
player stands in, in every dimension.

**Observed engine behavior (the dig, through the real packet path):**
1. A player standing in the nether submits a survival dig at a nether
   target. Every gate and the break commit run against the OVERWORLD cell
   at the same coordinates: `miningStartOnTick` reads the overworld block
   to arm the session, `miningFinishWalk` reads it again, `tryMineBlock`
   commits `world.setBlock(target, air)` on the overworld.
2. Two observable outcomes, both wrong: the NETHER block never breaks
   (nether mining through the client path is impossible — the cell the
   engine consults is a different world's), and the OVERWORLD cell at the
   same coordinates is destroyed (persistent cross-dimension corruption
   through the ordinary dig packet: drops spawn, the block shatter FX
   plays, the overworld terrain is edited by a nether action).
3. The same law covers placement and use (the family arms above): a nether
   player's chest/sign/door/rail/bed placement and every block use write
   the overworld. The flint and steel arm lights a fire IN THE OVERWORLD at
   the nether's coordinates (and consults the overworld portal builder).

**Verification history:**
- **Pass 1 (source, 2026-10-31):** the service's single-world binding read
  whole (constructor, every `*OnTick` body); the method-level scan of the
  EngineServer use arms (bare `world.` vs `worldFor(session.dimension())`)
  enumerated the affected families; the protocol dig dispatch
  (`V18Connection.handleDigging` → `submitMining*`) confirmed no dimension
  routing exists upstream of the service either.
- **Pass 2 (behavioral, 2026-10-31):** the discovery seat
  `DimensionTailsDiscoveryTest.theNetherDigBreaksTheNetherBlockNotTheOverworldCell`
  — a stably-nether player (portal crossing, arrival frame cleared, the
  body grounded beside the target) digs a nether dirt cell with an
  overworld dirt probe at the same coordinates. BOTH halves red on
  develop: `expected: <minecraft:air> but was: <minecraft:dirt>` (the
  nether cell survives its own dig) and `expected: <minecraft:dirt> but
  was: <minecraft:air>` (the overworld probe is consumed by the nether
  dig — the corruption). The seat's design note: the probe holds the SAME
  block as the target, so every dig gate (digability, progress, the 0.7
  finish floor, the anti-cheat budget) passes identically against either
  cell — the WORLD CHOICE is the only variable.
- **Pass 3 (independent re-verification, 2026-10-31):** the
  compensating-behavior hunt across the whole path — the dig dispatch has
  no dimension routing (protocol → service), the service has no per-world
  state, and the only dimension-aware interaction arms are the FL4
  migrations (bucket, spawn egg, ender eye) — the pattern exists but was
  never applied to dig/place. The FL4 wave's own commit names "the
  placement families' use-path world routing" as the owed tail (not an
  intentional divergence). Reachability confirmed: nether players exist
  (the portal walk), the dig path is the ordinary client path, and the
  corrupted overworld cell persists through the delta storage.

**Bounded correction (the proposed shape, its own pass):** the reference's
per-world interaction manager, adapted: route every dig/use/place walk
through `worldFor(session.dimension())` at the service boundary — the
cheapest faithful shape is a per-call world resolution inside the
`*OnTick` arms (the session's dimension read once per walk, the same
`worldFor` consult the FL4 arms already use), with `publishDrops` taking
the four-arg TF-9 `spawnDropAtBlock` stamp. The family arms in
EngineServer migrate arm by arm (the FL4 precedent: the bucket path first,
the rest in their own passes). The full correction is too broad for a
single verifier PR; the dig path + the flint arm are the bounded first
slice (they unblock VT-001's flint-and-steel nether arm and the
frozen-nether-gameplay symptom), with the remaining families ledgered.

**Regression risks:** the overworld seats must stay green (the overworld
dimension resolves through the same consult); the anti-cheat budget and
the reach band are dimension-blind and unaffected; the nether/End arms
have no existing seats to break.

---

## VT-003 — the ice family's melt and break-to-water arms are absent

**Subsystem:** random ticks + the survival break walk (ice, snow layer).
**Status:** RESOLVED-BY-PR — the correction landed on
`fix/ice-family-parity-audit` and PR #89 (2026-10-31): the melt arms (the
BLOCK-light consult with the ice's own opacity-3 gate `> 8` and the snow
layer's `> 11`, the nether's vaporizing profile, the source-form write) and
the break's FLOWING_WATER write over a solid-or-liquid floor (the silk
negative, the over-air negative). `IceFamilyParityTest` 11/0 with the
stash verification (exactly the six positive seats fail pre-fix). The
fix's own find recorded for the seats: the settle law — the level-0
flowing form converts to the source on its own flow tick
(FlowingLiquidBlock.tick lines 91-94), so the break seats pin both
moments.

**Affected engine files and methods:**
- `server/interaction/BlockInteractionService.java` — `tryMineBlock`: the
  break commits plain air (`commit(target, world.airType())`); no ice arm.
- `server/block/RandomTickSystem.java` — no ice or snow-layer family arm
  (the random-tick families: grass, leaves, crops, stems, cocoa, mycelium,
  the lit redstone ore, the cane/cactus, the nether wart, the fluids).

**Vanilla reference:**
- `reference/1.8.8/net/minecraft/block/IceBlock.java` — `tick` (lines
  66-77): block light `> 11 - getOpacity()` (0 for the translucent ice)
  melts the cell — `yeetsWater` dimensions (the nether) remove it, others
  drop (nothing — `getBaseDropCount` 0) and write `WATER`.
  `afterMinedByPlayer` (lines 33-60): the silk arm takes the cube; else
  `yeetsWater` removes; else `dropItems` (nothing) and, when the block
  below `blocksMovement() || isLiquid()`, the cell becomes
  `FLOWING_WATER`.
- `reference/1.8.8/net/minecraft/block/SnowLayerBlock.java` — `tick`
  (lines 115-119): block light `> 11` melts the layer away (the drops walk
  yields nothing — `getBaseDropCount` 0); `afterMinedByPlayer` drops
  `LAYERS + 1` snowballs (the engine's single-layer state's 2 — already
  landed by the mining-speed audit's DropService row).

**Observed engine behavior:** ice broken over a solid floor leaves AIR
(no water ever); ice and snow never melt beside torches or lava (no
random-tick arm); a frozen lake cut through never refills through ice
breaks; the nether's ice (placed or imported) never sublimates.

**Verification history:**
- **Pass 1 (source, 2026-10-31):** the reference pair read whole (both
  classes); the engine's break walk read end to end (`tryMineBlock` — the
  unconditional air commit, the afterMinedByPlayer hook surface) and the
  RandomTickSystem's family list enumerated — no ice/snow arm exists
  anywhere.
- **Pass 2 (behavioral, 2026-10-31):** the discovery seat
  `DimensionTailsDiscoveryTest.theBrokenIceBecomesWaterOverASolidFloor`
  — ice over stone, dug through the real survival path, must leave water
  (the reference's FLOWING_WATER write); the engine leaves air:
  `VT-003: the broken ice cell becomes water over the solid floor (got
  minecraft:air)`. Red on develop.
- **Pass 3 (independent re-verification, 2026-10-31):** the
  compensating-behavior hunt — the `SurvivalMinedListener` (EngineServer
  ~3576) handles the banner tile alone; the block-broken listener handles
  container spill; `DropService.dropsFor`'s ice row is the silk/cube
  drop split (cannot write blocks); nothing in the commit feedback path
  writes water. The FL4 wave's ledger names "the ice family's melt arms"
  as the owed tail. The engine's own `BuiltinBlocks` comment ("its bare
  hand melts it to nothing") describes the DROP law, not the cell law —
  the cell's water write is simply absent.

**Proposed minimal correction:** two arms, both overworld-shaped with the
per-dimension gate the reference carries: (1) the break arm — in the
survival break walk, an ice cell (no silk) whose world
`!yeetsWater()` and whose block below is movement-blocking or liquid
commits `FLOWING_WATER` instead of air (the nether's yeetsWater consult
rides the EngineWorld dimension profile FL4 introduced); (2) the melt arms
— ice and snow_layer join the RandomTickSystem's random-tick families
with the block-light `> 11` consult (the engine's `blockLightAt`), writing
water (ice, non-yeetsWater worlds) / removing (snow, and the nether's
ice) per the reference.

**Regression risks:** the ice slipperiness consults and the silk-drop row
are untouched; the melt arm's light consult must read BLOCK light only
(the reference's `LightType.BLOCK`), not the sky-max blend the grass arm
uses — a sky-blended consult would melt every surface ice at noon.

---

## The twenty-first pass's seats

`zamin-core/src/test/java/net/zaminmc/torch/server/DimensionTailsDiscoveryTest.java`
carries the three reference-pinned seats (one per finding). They are
RED on develop by contract — each asserts the REFERENCE law, and the file's
javadoc forbids re-pinning a seat to the engine's current answer to make
it pass. The fix branch that takes a finding up lifts its seat green and
rides it as the regression seat (the fourteenth pass's own
stash-verification pattern: the seat fails with the fix stashed, passes
with it applied).

Observed red output (develop `dcd2e139`, 2026-10-31, the full class run):

```
theNetherFireOnStoneDiesOnItsFirstScheduledTick FAILED
  VT-001: the nether flame ticks in the nether and dies its fuel-less death
  ==> expected: <minecraft:air> but was: <minecraft:fire_age5>
theNetherDigBreaksTheNetherBlockNotTheOverworldCell FAILED (2 failures)
  VT-002: the nether dig breaks the NETHER block (the reference's per-world manager)
  ==> expected: <minecraft:air> but was: <minecraft:dirt>
  VT-002: the overworld cell at the same coordinates is NOT part of a nether dig
  ==> expected: <minecraft:dirt> but was: <minecraft:air>
theBrokenIceBecomesWaterOverASolidFloor FAILED
  VT-003: the broken ice cell becomes water over the solid floor (got minecraft:air)
  ==> expected: <true> but was: <false>
```

The VT-002 seat's stabilization notes (for whoever lifts it): the portal
crossing leaves the body inside the arrival frame's portal cells and the
stand clock stays at its max — the reference's own 10-tick cooldown
(PlayerEntity line 315) is the only thing holding the return, so the seat
clears the arrival frame's portal cells on the crossing tick (a body that
lingers re-crosses and drags the dig out of the reach band for unrelated
reasons); and the stand rides `applyMovement(..., onGround=true)` —
`teleportPlayer` writes `onGround=false` and nothing re-arms it without
client movement packets, which parks the dig's airborne 5x divisor
(PlayerEntity.getMiningSpeed line 695) below the finish floor.
[Twenty-second-pass correction: the re-cross this note describes is NOT
the reference's law — it is VT-004 below, a genuine divergence. The
shipped PR #90 seats use the FL4-F3 `setDimension(-1)` harness instead.]

---

## The twenty-second pass — the VT-002 remaining slice landed (PR #90) + the portal re-fire discovered (VT-004)

Baseline: `origin/develop` at `684ee7ff` (Waves FL4-F2 + FL4-F3 landed —
the fire family's per-dimension scheduling retired VT-001's fix, and the
use-path world routing retired the family arms of VT-002; the ice family's
PR #89 open). The pass took up the queue's head — VT-002's remaining
slice, the FL4-F3 ledger's "dig/place GENERIC surface" tail — carried it
through the fix discipline, and surfaced one new suspected finding under
the harness work (VT-004).

### VT-002's remaining slice — RESOLVED-BY-PR #90

**What landed** (`fix/dig-path-dimension-parity-audit`, PR #90): the
per-call world resolution over `BlockInteractionService` whole — every
`*OnTick` arm resolves the digger's own world (`worldOf(player)` over the
engine's `worldFor` consult, the reference's own
`this.server.getWorld(this.player.dimension)` per-packet shape); the
helpers thread it (`extinguishFireAt`, `miningFinishWalk`, `tryMineBlock`,
`perTickProgress`, `isSubmerged`, `canOccupy`, `fireBreak`, `firePlace`,
`commit`, `publishDrops`, `resync`, `survivalBreakAllowed`); `tickMining`
resolves per session; `publishDrops` stamps `world.dimension()` (the
four-arg TF-9 form — the coverage ledger's TF-9 row closes with it); the
`placementTagConsult`'s hardcoded dimension 0 becomes the player's; the
resync publisher seam carries the walk's world. The hooks the walk invokes
carry the world too (the redstone-ore start-light, the dragon-egg
startMining teleport, the door/bed sibling cleanups, the placement soil
gate, the banner drop's dimension stamp).

**Evidence:** `DigPathDimensionParityTest` 4/0 NEW — the survival dig seat
(the VT-002 discovery seat's law, the FL4-F3 stable harness), the creative
break seat, the generic placement seat, and the overworld control. The
stash verification (the production pair stashed): exactly the three
nether seats fail — the survival dig's BOTH halves (`expected: <air> but
was: <dirt>` on the nether cell + `expected: <dirt> but was: <air>` on the
overworld probe — the corruption), the control green on both trees.
Regressions: the interaction/dig/mining package 79/0, the
containers/banners/beds lane 26/0, the placement families 57/0, the
protocol wire lane 7/0, `BukkitEventBridgeTest` green (~173 targeted
tests, 0 failures).

**The rider fixed in passing (an FL4-F3 incomplete migration):**
`useDragonEggOnTick` gated on its threaded world but `dragonEggWorld()`
closed over the overworld field for the teleport itself — a nether dragon
egg's use passed the gate and then no-opped the teleport against the
overworld's cell. Both now take the walk's world.

**The tails this slice ledgers** (each its own pass, recorded in the
coverage ledger below): the per-dimension tile families (the
furnace/chest/skull/banner spill + cleanup ride the single-instance
managers — the FL4 fluids/fire precedent applies), the infested burst's
mob dimension stamp (`MobManager.spawnAt` takes no dimension), the FX
broadcast's dimension awareness (the FxManager position-only API), the
per-dimension world border (the reference's per-World border vs the
engine's shared one), and the zombie door-break arm's own world binding
(the mob path keeps the bare overworld-bound form — its own mob dimension
tail).

**Coordination:** PR #89 (the ice family) also edits `tryMineBlock`'s
commit region — a small textual overlap only; the changes compose (the
ice law's `world.dimension()` consults become dimension-correct once the
routing lands, exactly as #89's own comment anticipates).

## VT-004 — the portal stand clock re-fires while the body stays pinned in the arrival frame (PR #91 OPEN)

**Subsystem:** portal mechanics (the stand clock / the crossing's re-fire
law). **Status:** PR OPENED — the correction rides
`fix/portal-stand-clock-parity-audit` as **PR #91** (targeting
`develop`): `advancePortalClock` restructured to the reference's per-tick
order (the decrement, the every-touching-tick re-arm consult, the
armed-stand-alone consumption), the dead `portalOccupiedLastTick` field
removed, `DimensionWalkTest`'s round trip re-pinned (the walk out → the
cooldown expiry → the re-entry's fresh 80), `PortalFrameBuilderTest`'s
cooldown law re-pinned (the method renamed — the old seat asserted the
re-fire itself), and the discovery seat riding green. The test record:
the seat 1/0, DimensionWalk 1/0, PortalFrameBuilder 12/0, PortalForcer
5/0, EnderExit 2/0, NetherDimension 5/0, NetherDigRouting 1/0,
SpawnProtection 3/0, the world package + both portal tests 248/0, the
block lane 309/0. One honest note: EnderExit's health-carry assertion
flaked twice under fresh-compile load early in the run and did not
reproduce in six subsequent runs (three with the change, three on
pristine develop); the changed code is unreachable for that test's body
(the End arm returns before `advancePortalClock`) and the instrumented
damage path logged nothing — the signature matches the CI log's
documented load-flakes.

**The reference's law** (reference/1.8.8 `entity/Entity.java`):
`onPortalCollision` (lines 1426-1454) consults the cooldown FIRST — `if
(this.portalCooldown > 0) { this.portalCooldown = this.getPortalCooldown();
}` with no `inPortal` arm in that branch; the memory derive and
`this.inPortal = true` ride the ELSE (the expired-cooldown arm) alone. The
cooldown RE-ARMS on every collision tick while cooling, and `inPortal` is
armed ONLY when the cooldown is expired. The stand walk (`baseTick` lines
284-313) then accumulates `portalTime++` only while `inPortal` — so a body
that arrives inside the destination frame's portal cells (cooldown = 10,
the player override, PlayerEntity line 315) NEVER re-crosses while it
stands in the cells: every collision tick re-arms the cooldown and
`inPortal` stays false while `portalTime` decays (-4/tick, the not-inPortal
arm). The return requires the body to LEAVE the cells (the cooldown then
counts down to 0) and re-enter — and the fresh entry re-accumulates the
full 80-tick stand (portalTime decayed to 0 on the way).

**The engine's shape** (`PlayerSession.advancePortalClock`, lines 782-805):
the crossing gate is `old = portalTime++; if (old >= max)` with NO cooldown
consult, and `portalTime` is never reset on the dimension change
(`applyDimensionArrival` lines 893-908 clears the portal memory but not the
stand clock — it stays clamped at `max` = 80 after the crossing). The
cooldown consult exists only on the ENTRY EDGE (`portalCooldown > 0 &&
!portalOccupiedLastTick` — a re-arm), not on the standing clock. So a body
whose arrival lands inside the destination frame's portal cells re-fires
the crossing on the very next touching tick: the body ping-pongs between
the dimensions until an arrival happens to land outside cells.

**The engine's own javadoc claims the reference's shape** ("The crossing
re-fires while the body stays pinned in the portal — the reference's own
shape, because its changeDimension moves the body out of the portal on the
same tick") — a misreading: the reference's `changeDimension` →
`placeInPortal` lands the body INSIDE the destination frame's cells, and
what holds the return there is the `onPortalCollision` cooldown consult,
not the placement. `DimensionWalkTest` documents the auto-return as "the
10-tick cooldown runs out, the stand clock crosses again" — the observed
round trip IS this divergence's symptom (in vanilla the body stays in the
nether until it walks out of the arrival cells and back in).

**Observed behavior (the twenty-second pass's harness work):** the
portal-crossing form of the dig seat was seed-flaky — a boot whose random
seed rolled an arrival inside the frame's cells bounced the body back to
the overworld within a tick of the crossing (the body pinned at the
chamber's coordinates in dimension 0), while other boots (arrivals outside
the cells) held the nether stably. The flake is the divergence's own
signature: the re-fire's timing is arrival-placement-dependent, not
cooldown-gated.

**Verification history:** pass 1 (source, this pass): the reference walk
read whole (`onPortalCollision` lines 1426-1454 + `baseTick` lines 284-313
+ the player cooldown override line 315) against the engine's
`advancePortalClock` (the un-gated crossing + the entry-edge-only re-arm +
the missing stand-clock reset in `applyDimensionArrival`); the empirical
bounce observed once in four boots of the crossing harness.

**Pass 2 (behavioral, the same run):** the discovery seat
`PortalRefireDiscoveryTest.theBodyStandingInTheNetherArrivalCellsNeverReCrosses`
— the nether frame PRE-BUILT at the crossing's scaled destination (5, 20,
5) so the forcer's arrival lands deterministically inside its cells; the
body crosses, never moves again, and the dimension is sampled at 2-tick
granularity over a 120-tick window. RED on develop, deterministically
across two fresh boots, with the discriminating timeline:
`t2:dim=-1,clock=80,cool=10; t4…t20:dim=-1,clock=80,cool=10;
t22:dim=0,clock=80,cool=10` — **the cooldown HELD at 10 for the whole
window (the reference's own re-arm law: the collision re-arms it every
tick, it never expires while the body stands in the cells) and the
crossing fired anyway**. The clock stays pinned at its max (80) across
the whole window — the stand-clock carry. The 2-tick sampling's phase
shift at t22 (dim leaving -1 only mid-window, not at t2) indicates the
re-fire is an EVERY-TICK oscillation the sampling aliases: the body
ping-pongs between the dimensions each tick after an in-cells arrival
(the wire consequence: a respawn-packet storm for a real client standing
in its arrival frame). The reference's answer for the same setup: the
dimension holds -1 for the entire window (the seat's asserted law).

**Pass 3 (independent re-verification, the twenty-third pass):** the
compensating-behavior hunt found NOTHING that holds the re-fire, and both
of the engine javadoc's supporting claims are false against the reference's
own text:

1. *The dispatch cadence.* The javadoc claims `onPortalCollision` is
   "movement-driven, and a STANDING body sends no movement packets, so a
   body standing inside a portal never refreshes the cooldown". The
   reference's dispatch is `Entity.checkBlockCollisions()` (lines 667-689),
   a bounding-box scan over every intersected block, invoked from
   `moveEntity` (line 627) — which runs every tick for living bodies
   (gravity and momentum drive it; no client packet is involved). A body
   standing inside portal cells therefore fires `onPortalCollision(pos)`
   EVERY tick: the cooldown branch (lines 1429-1430) re-arms it to the
   player's 10 each tick, and `inPortal` (line 1453) — the only arm that
   arms the crossing — rides the ELSE, the expired-cooldown arm, alone.
   The standing body can never re-cross; the clock decays on the
   not-inPortal arm (-4/tick, lines 301-308).
2. *The arrival placement.* The javadoc claims the reference's
   `changeDimension` "moves the body out of the portal on the same tick".
   The reference's placement math (`PortalForcer.findNetherPortal` lines
   106-129) lands the body INSIDE the destination frame's interior — the
   width-span position `d2 + (1 - offset.x) * width * dir` and the
   height-span `topLeft.y + 1 - offset.y * height` — the same in-cells
   landing the engine's own forcer produces (FL4-F1 verified the placement
   1:1). What holds the body in the destination dimension is the cooldown
   consult, not the placement.
3. *No compensating gate in the engine.* `tickPortalStand`
   (EngineServer 6618-6666) routes the crossing straight to
   `changeDimensionOnTick` with nothing between; `setGraceTicks(100)`
   opens a movement-validation window (teleports/knockback anti-cheat,
   PlayerSession 1758-1773) that the portal clock never consults;
   `applyDimensionArrival` (PlayerSession 883-898) clears the portal
   memory but leaves `portalTime`/`portalCooldown` untouched — the stand
   clock carries its max across the crossing.
4. *`DimensionWalkTest`'s round trip is a symptom, not a design choice.*
   The test's own comment ("the 10-tick cooldown runs out, the stand clock
   crosses again") documents the same misreading — in the reference the
   cooldown NEVER runs out while the body stands in the cells; it is
   re-armed every collision tick. A deliberate design decision would carry
   a correct rationale; this one asserts "the reference's own shape"
   against a reference whose text says otherwise.
5. *The edges.* Creative: the reference's `getMaxNetherPortalTime()` =
   `abilities.invulnerable ? 0 : 80` (PlayerEntity 300-302) — the
   corrected shape still crosses on the first touching tick (cooldown 0 →
   inPortal → `0 >= 0`) and the cooldown holds the re-fire after the
   arrival, matching vanilla's instant creative crossing. The End pair's
   instant arm is a different law (EndPortalBlock's own collision) and is
   untouched. The vehicle/riding gate is genuinely ABSENT from the
   engine's nether arm — a separate divergence, ledgered as VT-005 below,
   not bundled into this correction.

**Proposed minimal correction (confirmed, fix-ready):** restructure
`advancePortalClock` to the reference's own gate shape, in the reference's
per-tick order — the cooldown decrement, then the collision consult (the
re-arm on EVERY touching tick while cooling; `inPortal` armed only when
expired), then the consumption (the crossing only on the inPortal arm; the
decay otherwise). The `portalOccupiedLastTick` field (the entry-edge
approximation of the misread theory) becomes dead and is removed. The
`DimensionWalkTest` round-trip seat re-pins to the reference's law: the
body must LEAVE the arrival cells, let the cooldown expire outside (10
non-touching ticks), and re-enter to stand the fresh 80 — the walk out and
back in is the vanilla round trip. The discovery seat
`PortalRefireDiscoveryTest` rides the fix branch and turns green (its
asserted law is the corrected shape).

**Confidence:** high — the source reading is unambiguous (the reference's
gate is a cooldown-first consult with the crossing on the expired arm
alone), the behavioral seat is deterministic across boots, and the
independent pass closed every compensating hypothesis with the reference's
own dispatch and placement text.

## VT-005 — the nether portal stand clock ignores the reference's riding gate (RESOLVED-BY-PR — **PR #94 OPEN**)

**Subsystem:** portal mechanics (the riding/vehicle gate on the collision
and the crossing). **Status:** confirmed — three verification passes
complete (source, behavioral, independent — the twenty-third and
twenty-fourth passes); the correction landed on
`fix/portal-riding-gate-parity-audit` as **PR #94** (one condition, the
End arm's own idiom; both discovery seats ride green on the fix branch;
the regression lanes whole: DimensionWalk 1/0, PortalFrameBuilder 12/0,
PortalForcer 5/0, NetherDimension 5/0, RiderSpareFall 3/0,
VehicleSpawn 2/0, DimensionTimeSync 3/0, the world package + EnderExit
+ SpawnProtection + NetherDigRouting 252/0, the block lane 309/0).

**The reference's law** (reference/1.8.8): the gate rides BOTH arms.
`PortalBlock.onEntityCollision` (lines 158-162) calls `onPortalCollision`
only `if (entity.vehicle == null && entity.rider == null)` — a riding (or
ridden) body never even arms the collision. `Entity.baseTick`'s crossing
arm (line 286) gates again: `this.vehicle == null && this.portalTime++ >=
i`. A body sitting in a boat/minecart inside a portal therefore stands
there indefinitely in vanilla — no stand clock, no crossing.

**The engine's shape:** `EngineServer.tickPortalStand` (lines 6644-6665)
computes `inPortal` from the feet cell alone — no riding consult on the
nether arm. (The END arm directly above it DOES consult
`session.ridingVehicleId() < 0 && session.ridingMobId() < 0`, so the
engine has the state and the idiom.) A riding body whose feet cell is a
portal cell accumulates the stand clock and crosses.

**Verification history:**

- **Pass 1 (source, the twenty-third pass):** both reference gates read
  (PortalBlock 158-162, Entity 286) against the engine's nether arm
  (no gate; the End arm's contrast confirms the omission is not a systemic
  constraint).
- **Pass 2 (behavioral, the twenty-fourth pass):** the discovery seat
  `PortalRidingGateDiscoveryTest` (RED on develop BY CONTRACT). The
  vanilla-reachable construction: a pig stands INSIDE the portal frame's
  interior (its feet on the bottom obsidian row), the player mounts it
  (the manager's own `mountMob` seam — the interact path's call), and the
  seat anchor (mob seat height 0.55 + the player's -0.35 ride height)
  lands the rider's feet cell INSIDE the portal cells — so the engine's
  feet-cell read names a portal cell and the riding gate is the ONLY
  thing that can hold the crossing. THE ENGINE'S ANSWER (deterministic):
  the timeline `t2:clock=3 ... t78:clock=80 ... t80:dim=-1,cool=10` with
  `riding=1100000` (the mob's entity id) THROUGH THE WHOLE WINDOW — the
  stand clock accumulated straight to the survival threshold 80 while
  mounted and the crossing fired at exactly t80, the cooldown armed, the
  rider still on the pig. THE REFERENCE'S LAW: the gated collision never
  arms `inPortal`, so the clock DECAYS 4-per-tick to 0 while mounted and
  no crossing can fire — the dimension holds 0 for the whole window.
- **Pass 3 (independent, the twenty-fourth pass):** three angles, each
  new evidence. (a) The compensating-behavior hunt: NO riding consult
  exists anywhere between `tickPortalStand`'s cell read and
  `changeDimensionOnTick` — the engine's riding consults are the
  movement-stats gate (PlayerEntity 1305's own `vehicle == null`), the
  landing reset (F-RIDER-SPARE), the step-on gate, the sprint-crit gates,
  the END arm's own consult, the death dismount, and the relog
  remember-ride walk; nothing holds the nether crossing. (b) The dismount
  control (`theDismountedBodyCrossesOnlyAfterItsFreshStand`, RED on
  develop BY CONTRACT): the same seat, but the rider steps off at the
  40-tick mark — the dismount exit lands at `mount.x + 1.1`, the frame's
  OTHER interior column, still a portal cell. THE ENGINE'S ANSWER: the
  fresh window opens at `clock=44` (the mounted window's accumulation
  CARRIED — the reference's decay would hold 0) and the crossing fires
  at the ~40-tick remainder, not the fresh 80. The control isolates the
  gate as the only variable: the seat's geometry is crossing-capable
  either way (the control's body DOES cross), so the mounted seat's
  held crossing cannot be explained by positioning. (c) The
  design-choice challenge: no test or javadoc on develop pins a mounted
  crossing as intended — and the reference's `EndPortalBlock
  .onEntityCollision` (lines 65-71) carries the IDENTICAL gate
  (`entity.vehicle == null && entity.rider == null`), which the engine's
  End arm implements faithfully; the nether arm's omission is an
  anomaly, not a systemic constraint or a choice.

**The stranded-rider consequence** (recorded from the pass-2 timeline):
  the engine's mounted crossing leaves the session in dimension -1 while
  still mounted on an OVERWORLD pig — `changeDimensionOnTick` neither
  dismounts nor carries the mount, and `tickMobRiders` keeps re-writing
  the rider's position to the overworld seat anchor every tick while
  `tickPortalStand` reads the NETHER's cells at those coordinates — a
  state vanilla cannot reach through the portal pair (the gates prevent
  the crossing; the reference's `changeDimension` is never
  portal-reached while mounted).

**Confidence:** high (the reference is unambiguous on both gates; the
behavioral seat is deterministic — the clock values are monotone and the
crossing fires exactly at the threshold; the control experiment rules
out the positioning alternative).

**The proposed minimal correction:** the riding consult on the nether
arm's `inPortal` computation in `tickPortalStand` (the reference's
collision gate translated to the engine's single-read idiom — a riding
body neither notes the portal entry nor accumulates the clock; the
4-per-tick decay holds the clock at 0 until the dismount, exactly the
reference's not-inPortal arm). One condition on the existing
`boolean inPortal = ...` line, the End arm's own idiom
(`session.ridingVehicleId() < 0 && session.ridingMobId() < 0`).

**Regression risks:** none identified beyond the portal family itself —
the gate only narrows the nether arm's arming for MOUNTED bodies; the
unmounted walk (DimensionWalkTest's round trip, the VT-004 seat's
re-fire law, PortalFrameBuilder's cooldown law) never rides. The
vehicle/mob riding lanes (RiderSpareFall, the cart-ride stats, the
relog-remount walk) never stand in portal cells.

## The twenty-third pass — VT-004's independent pass landed (CONFIRMED) + VT-005 discovered

Baseline: `origin/develop` at `3a6127a0` (PR #89's ice family and PR #90's
review rider landed since the twenty-second pass; no portal-path files
moved — the diff stat verified before the pass began). This pass took the
queue head, VT-004's pass 3, from three angles — the compensating-behavior
hunt (nothing holds the re-fire: no gate in `tickPortalStand`, `graceTicks`
is the movement-validation window the clock never consults,
`applyDimensionArrival` leaves the stand clock carrying), the challenge of
the engine javadoc's two supporting claims (both false: the reference's
collision dispatch is `checkBlockCollisions` — a per-tick bounding-box scan
off `moveEntity`, not client-packet-driven; and `findNetherPortal`'s
placement math lands INSIDE the destination cells), and the edge sweep
(creative ✓ per PlayerEntity 300-302; the End pair's instant arm is a
different law ✓; the riding gate is genuinely absent — recorded as VT-005,
suspected, one source pass). `DimensionWalkTest`'s round-trip comment
carries the same misreading as the javadoc — the seat pins the symptom, not
a design choice. **VT-004 is CONFIRMED (three passes, each with distinct
evidence); the fix branch is this run's next act.**

## VT-006 — the engine's non-player entity families carry no portal walk: mobs (and items, orbs, boats, carts, arrows) standing in portal cells never cross (CONFIRMED — three passes; the correction LANDED family by family: the mobs PR #98 MERGED, the items/orbs/vehicles **PR #112 OPEN** — the twenty-eighth pass; the arrows the remaining family)

**Subsystem:** portal mechanics (the entity-level crossing arm — the
non-player families). **Status:** confirmed — three verification passes
complete (the twenty-fourth pass: the source chain, the NoAI-cow
behavioral seat, and the independent pass's family census + origin
trace + the engine's own ledgered-gap javadoc). The correction is a
substantial architectural walk (the entity-level crossing service, the
families landed one by one) — the next run's fix head.

**The reference's law** (reference/1.8.8): the portal arm is ENTITY-level,
not player-level — every ticked body runs it.
`MobEntity.baseTick` (lines 150-151) → `LivingEntity.baseTick` (lines
189-191, the `super.baseTick()` call) → `Entity.baseTick` (lines 268-330,
the portal arm 282-313). A plain mob (not riding, not ridden) whose body
collides with portal cells: `PortalBlock.onEntityCollision` (lines
158-162) calls `onPortalCollision` (the riding gate passes); the cooldown
consult (Entity lines 1426-1454, expired) arms `inPortal`; the crossing
arm then runs `this.vehicle == null && this.portalTime++ >=
getMaxNetherPortalTime()` — the BASE Entity's override returns **0**
(lines 354-355), so a MOB CROSSES ON THE FIRST ARMED TICK (contrast the
player's 80, PlayerEntity line 300). `Entity.changeDimension` (lines
1695-1732) walks the body across: the world swap, the 8:1 reposition
(`PlayerManager.changeDimension` lines 449-480 — the same scale math as
the player's walk), the forcer's placement, the body recreated in the
destination world (`Entities.createSilently` + `copyNbtFrom` +
`addEntity`), the original removed. The cooldown arms at the BASE
`getPortalCooldown()` = **300** (lines 1457-1459; the player's own
override is 10). The NoAI pin does NOT hold the crossing: `isNoAi` gates
the AI SECTION alone (`LivingEntity.tick` line 1435's
`isLocallyControlled()` consult over `MobEntity` line 872's
`&& !this.isNoAi()`); the portal arm sits in baseTick, before and
outside the ai section. The vanilla-reachable scenarios: the zombie
chasing a player through a portal follows into the nether; any mob
pushed or wandered into portal cells teleports.

**The engine's shape:** `MobEntity.tick`'s portal row (the portalCooldown
decrement) round-trips the NBT field ALONE — its own comment says it
outright ("the engine's mobs carry no portal walk of their own (the
world's portal envelope), the row round-trips for the NBT surface").
`MobManager` has no dimension-change walk; the `setDimension` sites are
origin stamps alone (the spawner's own dimension, the persistence
restore, the zombie reinforcement's caller stamp, the natural spawn).
And the engine's OWN JAVADOC LEDGERS THE GAP — `EngineServer`'s
`leaveEndOnTick` doc (the End-exit walk): "The non-player entity arm
(Entity.changeDimension's old-1+new-1 redirect to the overworld spawn
surface) needs the mob portal-collision simulation first — mobs do not
yet collide with portal cells; ledgered." An acknowledged implementation
debt, not a divergence choice.

**Verification history:**

- **Pass 1 (source, the twenty-fourth pass):** the full reference chain
  read (the entity-level arm, the base threshold 0, the cooldown 300,
  the changeDimension walk with the 8:1 reposition and the entity
  recreation, the NoAI non-gate) against the engine (the NBT-only
  cooldown row; no crossing walk; the origin-stamp-only setDimension
  sites; the engine's own ledgered-gap javadoc).
- **Pass 2 (behavioral, the twenty-fourth pass):** the discovery seat
  `PortalMobCrossingDiscoveryTest` (RED on develop BY CONTRACT). The
  deterministic construction: a NoAI cow standing INSIDE the overworld
  frame's interior (its feet on the bottom obsidian row — the NoAI pin
  holds the mind alone, the physics still grounds the body, the animals
  never despawn, no daylight burn), the nether frame PRE-BUILT at the
  scaled destination (40/8 = 5) so the reference's forcer walk lands
  deterministically. THE ENGINE'S ANSWER: the body stood in the portal
  cells for the whole 100-tick window and NEVER crossed (the seat's
  crossing assertion failed with the body at dimension 0; the
  preconditions all held — the feet cell a portal cell). THE REFERENCE'S
  LAW: the crossing fires on the FIRST armed tick (the base threshold
  0), landing inside the pre-built frame's interior band through the
  same forcer walk the player rides.

- **Pass 3 (independent, the twenty-fourth pass):** three fresh angles.
  (a) **The family census** — the reference's portal arm is truly
  entity-level, and the census establishes the exact scope: the families
  that CROSS in vanilla (the `super.tick()` → `baseTick()` chains read
  directly): the mobs (MobEntity.baseTick 150-151), the dropped items
  (ItemEntity.tick line 60-64), the experience orbs
  (ExperienceOrbEntity.tick 61-62), the boats (BoatEntity.tick 177), the
  minecarts (MinecartEntity's OWN COPY of the whole arm, lines 203-227),
  and the arrows/projectiles (ArrowEntity.tick 150); and the families
  that DO NOT cross in vanilla — the primed TNT (PrimedTntEntity.tick
  overrides with NO super.tick() call — no baseTick, no portal arm) and
  the falling blocks (FallingBlockEntity.tick, the same shape) — for
  which the engine's omission is an ACCIDENTAL MATCH (the census
  protects the future fix from over-crossing them). The engine side:
  the ItemEntityManager, ProjectileManager, ExperienceOrbManager, and
  FallingBlockEntityManager carry ZERO portal consults; the
  VehicleManager's four "portal" mentions are the PortalCooldown NBT
  persistence alone. (b) **The nether-origin trace** — the mob
  `setDimension` sites are origin stamps only (the spawner's own
  dimension, the persistence restore, the zombie reinforcement's caller
  stamp, the natural spawn): NO site ever moves a live body between
  dimensions; the engine's nether mobs only ever ORIGINATE in the
  nether. (c) **The design-choice challenge** — the engine's own
  `leaveEndOnTick` javadoc LEDGERS the gap ("mobs do not yet collide
  with portal cells; ledgered"): an acknowledged implementation debt,
  not a divergence choice; the reference is unambiguous.

**Confidence:** high (the reference is unambiguous — the arm is
entity-level; the engine's own javadoc admits the gap; the census
establishes the exact scope including the accidental matches).

**The proposed correction's scope** (a substantial architectural walk,
its own run): the reference's own shape is an ENTITY-LEVEL service —
the crossing walk should be built once (the collision consult carrying
VT-005's riding gate — a ridden body never crosses; the threshold-0
crossing; the 8:1 reposition; the forcer's placement — the
nearest-frame law the player's walk pins; the dimension stamp swap; the
persistence move; the per-dimension wire; the 300 cooldown) and the
families landed one by one behind it: the MOBS first (the demonstrated
seat), then the items, the orbs, the boats, the carts (their own
threshold/cooldown consults), and the arrows — with the TNT and falling
families EXPLICITLY EXCLUDED (they carry no portal arm in the reference
— the census's accidental matches must not become over-crossings). The
End pair's mob arm (the old-1+new-1 redirect to the overworld spawn
surface — the engine's own ledgered note) rides the same walk.

**Regression risks:** the mob-manager tick's per-dimension walks (the
despawn window, the targeting, the natural spawn) must see the crossed
body in ITS dimension; the persistence round trip must keep the body in
the nether's store; the riding lanes must never cross (VT-005's gate).

## F-EC1 — the ender chest's solid-above gate reads the movement consult, not the reference's Block.isSolid() (RESOLVED-BY-PR — **PR #95 OPEN**)

**Subsystem:** the ender chest's use gate (the solidity consult family —
F-TC1's class). **Status:** confirmed over three verification passes
(the twenty-fourth pass) and corrected the same run — the one-consult
swap landed on `fix/ender-chest-solid-above-parity-audit` as **PR #95**.

**The reference's law** (reference/1.8.8): `EnderChestBlock.use` lines
71-90 — the open's refusal gate is line 80,
`world.getBlockState(pos.up()).getBlock().isSolid()`, and
`Block.isSolid()` (Block.java lines 274-276) is the THREE-CONJUNCT
SIGNAL consult: `material.isSolidBlocking() && isCube() &&
!isSignalSource()`. The isCube-false families (the glass pair —
GlassBlock lines 25-27; the slabs, the stairs, the chests, the leaves,
the beacon, the piston bases) and the signal sources (the redstone
block — F-TC1's census lone survivor) are NOT solid for this gate: the
ender chest OPENS under glass and under a redstone block; the stone lid
alone refuses.

**The engine's shape:** `openEnderChestOnTick` read
`WorldSolidity.isSolid` — the MOVEMENT consult (what blocks a body):
glass and the redstone block are movement-solid, so the engine REFUSED
the open under both. The site's javadoc already cited the reference
line (Slice 29a's stand-in consult predates F-TC1); F-TC1 (PR #87,
merged) introduced `BlockSolidity.isSolid` — the exact law line 80
reads — and swapped the six signal-ROUTING sites, but this USE gate sat
outside that census's scope (it is not signal routing). The swap is the
same class of repair F-TC1 already made, the site left behind.

**Verification history:**

- **Pass 1 (source, the twenty-fourth pass):** the reference's line-80
  consult identified as `Block.isSolid()` (the three-conjunct signal
  law) against the engine's `WorldSolidity.isSolid` (the movement
  consult) at the gate.
- **Pass 2 (behavioral, the twenty-fourth pass):** the discovery seat
  `EnderChestSolidAboveGateDiscoveryTest` (RED on develop BY CONTRACT)
  — the glass-lidded ender chest NEVER opened in the engine (the await
  timed out, no window id ever assigned) where the reference opens it.
  The seat's three cases: the glass lid (the isCube-false family), the
  redstone block lid (the signal-source conjunct), the stone control
  (both laws refuse — the existing acceptance test's own seat kept so
  the discovery seat cannot pass by refusing everything).
- **Pass 3 (independent, the twenty-fourth pass):** (a) the
  compensating-behavior hunt — the gate is the first statement of the
  open walk; nothing else consults the above-cell, and the refusal
  consumes the use; (b) the design-choice challenge — the site's
  javadoc cites the reference line; the consult was the pre-F-TC1
  stand-in, and F-TC1's merged helper IS the law line 80 reads; (c) the
  discriminants already test-pinned on develop by F-TC1's own seat (the
  glass never re-radiates; the redstone block's own-signal arm).

**The correction (this run's third act):** the one-consult swap at the
gate — `WorldSolidity.isSolid` → `BlockSolidity.isSolid` (with the
javadoc carrying the reference's three conjuncts). Both the glass and
redstone discovery cases ride GREEN on the fix branch; the stone
control still refuses. Evidence: the seat 1/0 GREEN with the fix;
`EnderChestAcceptanceTest` 3/0 (the stone-lid seat intact); the chest
package whole 24/0; the block + interaction lanes 313/0. **PR #95
OPEN** targeting `develop`.

## The twenty-fourth pass — VT-005's behavioral + independent passes landed (CONFIRMED)

Baseline: `origin/develop` at `817dbe74` (the FL4-F7/F8/F9 waves — the
TNT family, the block-update family, the falling family's per-dimension
rows — landed since the twenty-third pass; PR #89 and #90 reviewed
CORRECT by the maintainer and closed as superseded by the landed waves,
their unique riders adopted at `d6614da3`/`e088de73`; PR #87 merged —
F-TC1 resolved; PRs #91 (VT-004), #92 (F-EX4), #93 (F-EX7) open). This
pass took the queue head, VT-005's two owed passes, and completed the
finding's full verification: the behavioral seat (the pig-in-portal
construction — the vanilla-reachable ridden scenario; the rider's feet
cell lands inside the portal cells through the engine's own seat-anchor
law) and the independent pass's three angles (the compensating-behavior
hunt — nothing holds the crossing; the dismount control — the mounted
accumulation carries into the fresh window, isolating the gate as the
only variable; the design-choice challenge — no pins anywhere, and the
reference's own EndPortalBlock carries the identical gate the engine's
End arm already implements). **VT-005 is CONFIRMED (three passes, each
with distinct evidence); the fix branch is this run's next act.**

**The run's discovery rider (VT-006):** during the VT-005 seat's
construction the engine's mob-side portal surface came into view — the
mobs carry NO portal walk at all (the NBT-only cooldown row; no
crossing walk; the origin-stamp-only setDimension sites), and the
engine's own `leaveEndOnTick` javadoc LEDGERS the gap ("mobs do not yet
collide with portal cells"). The reference's arm is entity-level (every
ticked body runs it; the base threshold 0 — a mob crosses on the FIRST
armed tick; the cooldown 300). The discovery seat
`PortalMobCrossingDiscoveryTest` demonstrated the divergence (the NoAI
cow standing in the portal interior never crossed over the 100-tick
window). VT-006 CONFIRMED over this run's three passes (the source chain, the
behavioral seat, and the independent census — the true scope: the mobs,
the items, the orbs, the boats, the carts, the arrows; the TNT and
falling families carry no portal arm in the reference itself, their
omission an accidental match). The fix is a substantial architectural
walk — the next run's head.

**The run's third arc (F-EC1, confirmed and corrected the same run):**
the queue's standing lane taken up — the ender chest's solid-above
gate read the movement consult where the reference reads
`Block.isSolid()` (the signal consult); the glass-lidded seat RED on
develop; the one-consult swap (`BlockSolidity.isSolid`, F-TC1's merged
helper — the exact law line 80 reads) landed as **PR #95** with the
seat green, the stone control intact, and the chest + block +
interaction lanes whole.

**The correction (this run's second act):** the fix branch
`fix/portal-riding-gate-parity-audit` (based on `origin/develop`
`817dbe74`) — the riding consult on the nether arm's `inPortal`
computation (one condition: `session.ridingVehicleId() < 0 &&
session.ridingMobId() < 0`, the End arm's own idiom, with the
javadoc carrying both reference gates). Both discovery seats ride
GREEN on the fix branch (2/0: the mounted hold — the dimension pins 0
for the whole window with the clock pinned at 0; the dismount control
— no crossing before the fresh 80 past the dismount, the crossing
arriving on the fresh clock). The seats were authored RED on develop
first and lifted green by the fix alone. The regression lanes green
whole (the record above). **PR #94 OPEN** targeting `develop`.

## The twenty-fifth pass — VT-006's correction landed (the mobs family first)

Baseline: `origin/develop` at `deef2a17` (the FL4-F10 redstone
world-binding investigation recorded on develop by the house stream
with its six-point implementation plan — that stream's own next act,
not duplicated here; the CI/test-stability commits; the
release-notes body cap; torch-1.8.8-155). PRs #91 (VT-004), #94
(VT-005), #95 (F-EC1) remain open, unreviewed, mergeable; PR #54
re-inspected as the quality baseline (merged). This pass took the
queue head — **VT-006's fix, the entity-level crossing service with
the mobs family first** — and landed it as **PR #98** on
`fix/portal-entity-crossing-parity-audit` (based on `deef2a17`).

**The fix's shape (the census-informed scope):** `MobManager`'s
per-body portal arm in the tick walk — before the despawn window and
the mind, the reference's baseTick-first order — carrying the gated
collision consult (VT-005's riding gate), the entry memory's derive
(the same `notePortalEntry` math the player session carries), and the
base-entity clock (the threshold 0, the 300 cooldown, the re-arm pin,
the 4-per-tick decay); the crossing consumed the body's own walk (the
reference's removed-body shape). The **`DimensionCrossing` seam**
carries the walk itself so the remaining families (the items, the
orbs, the boats, the carts, the arrows) ride it one by one in their
own passes — the TNT and falling families EXCLUDED by the census (no
portal arm in the reference itself). `EngineServer`'s
`crossEntityDimension` is the same walk the player's own crossing
rides (the direction law, the 8:1 scale, the ±29999872 clamp, the
17×17 destination loads, the forcer selection); `MobEntity` gains the
portal clock, the entry memory, and the arrival application (the
stamp swap, the forcer's landing, the rotated yaw and motion, the
fall debt cleared); the `WorldQuery`'s `portalFrameAt` consult is the
stub-friendly seam. The wire: the old world's destroy fan first (the
TF-12 stamp read at send time), then the stamp swap, then the
destination's spawn fan; the persistence rides the stamp (the
snapshot's own dimension row).

**The seats:** `PortalMobCrossingDiscoveryTest` 3/0 GREEN (RED on
develop by contract at the crossing await) — the crossing seat (the
NoAI cow crosses on the first armed tick, lands inside the pre-built
destination frame's band, carries the nether's stamp in the live
list); the cooldown pin (the arrival standing INSIDE the destination
frame's cells never re-fires over the 350-tick window — a broken
re-arm would bounce the body back at ~300); the riding-gate pin (a
real rider's mount never crosses over the 60-tick window, the seat
intact — VT-005's law at the entity level; the window sits below the
survival rider's own 80-tick stand so the seat pins the mount's arm
alone). The regression lanes green whole: the portal+mob+world lanes
**271/0** (DimensionWalk, EnderExit, NetherDigRouting,
SpawnProtection, MobEventBridge, MobCombat, MountSeatsApi,
RiderSpareFall, PortalForcer, PortalFrameBuilder, the whole
`server.world` package); the block + interaction lanes **313/0**.

**The flake investigated and recorded (not this change's):** one
batch run's DimensionWalk failure — the homecoming landing at
(68.5, 5.1, 173.5) — root-caused to the SEED-DEPENDENT forcer
placement: the nether generate's hunt landing near its 16-block ring
edge (z=21) scales the ×8 return outside the ±128 overworld search
window. The same-seed control (the seed record pre-written at
`<dataDir>/dimwalk/data/seed.json`) proves the placement IDENTICAL on
both trees (8.5, 32.1, -5.5) — the arrival differences observed
across runs were the seed alone (the sandbox's `new Random()` rolls a
seed that is stable per code state but drifts across code states).
DimensionWalk green 5/5 solo + the full batch re-run. The test's own
lane (a pinned level-seed would retire the window) is the house's
call, not this PR's.

**VT-006's named tails:** the End-origin arm (the reference's else
branch reads the destination's forced-spawn consult, which the engine
does not carry — the crossing holds for dimension-1 bodies; the End
exit for mobs is `leaveEndOnTick`'s own ledgered lane) and the
remaining crossing families (the items, the orbs, the boats, the
carts, the arrows — each its own pass behind the seam).

**The run's closing arc — the remaining families' behavioral
evidence:** the census's source scope now demonstrated per family
with RED-on-develop seats (every seat's precondition PASSES — the
body rests inside a portal cell at dimension 0 — the RED is the
crossing await alone): `PortalItemCrossingDiscoveryTest` (the item
family — a dropped stone crosses on the first armed tick over
ItemEntity.tick line 61's super chain, the stack whole at the
arrival), `PortalOrbCrossingDiscoveryTest` (the orb family —
ExperienceOrbEntity.tick lines 61-62's super chain), and
`PortalVehicleCrossingDiscoveryTest` (2 seats — the empty boat over
BoatEntity.tick line 177's super chain, and the empty minecart over
MinecartEntity's OWN COPY of the whole arm, lines 199-232,
byte-identical law). The arrow family's seat is deferred to the fix
run (the flight-path construction needs the crossing tick's
determinism care; the census's source evidence — ArrowEntity.tick
line 150 — stands). The families' fixes ride the
`DimensionCrossing` seam once PR #98 merges.

## The twenty-sixth pass — the explosion-path dimension audit (the queue's named head): VT-007 discovered, the bed's blast arm discovered (VT-008)

The crossing families' fixes remain blocked behind PR #98 (the
`DimensionCrossing` seam's dependency — the PR is open and clean, not
merged; the families' seats ride on this branch chain). The pass took
the queue's next unblocked head: **the explosion-path dimension audit**
(the ledger row "Explosions — dimension routing", unblocked since
nether placement landed with PR #90).

The audit censused EVERY detonation call site in `zamin-core/src/main`
against the reference's own world-binding law, and found the routing
PARTIAL — the FL4-F7 wave (the primed TNT) and Slice 62 (the End
crystal) landed their sites, but five detonation sites plus one
shared-walk casualty still bind the overworld. The full three-pass
verification completed WITHIN this run (the census, the executed
seats, the independent angles):

1. **VT-007 — CONFIRMED** (three passes, all this run) — the ghast
   fireball and wither skull sinks pass the ROUTED world but the
   OVERWORLD service; the creeper, the wither birth, and the TNT cart
   pass the overworld binding outright; and the blast drop roll inside
   the shared `detonateAtIn` walk stamps dimension 0 even for the
   correctly-routed blasts. A nether ghast's routine fireball never
   holes the nether and instead tears the OVERWORLD at the same
   coordinates — cross-dimension world corruption from the nether's
   signature mob — and one detonation desyncs BOTH dimension views on
   the wire (the nether client carves phantom holes into its intact
   view; the overworld observers receive no packet for their world's
   real destruction). The passes: the source census (the verdict
   table), the executed behavioral seats (RED on develop stable
   across three runs, both families: the nether 0 holes / the
   overworld 4-24), and the independent pass (the FL4-F7 precedent
   naming the shape as the engine's own recognized defect, the empty
   compensating hunt, the wire-level consequence). The fix branch is
   the next run's head.
2. **VT-008** — the bed's out-of-dimension blast arm is absent: the
   sleep walk carries no dimension gate, so a nether or End bed sets
   the spawn and sleeps where vanilla removes both halves and blasts
   a power-5 fire-carving hole. UNDER VERIFICATION (pass 1, the
   source pass, recorded; the behavioral seat deferred to the next
   run — the nether-anchored session construction, exactly the arrow
   family's seat precedent).

## VT-007 — the blast family's dimension routing is partial: the fireball, skull, creeper, wither-birth, and TNT-cart detonations still bind the overworld, and the blast drop stamp carries dimension 0 (the CORE resolved by PR #100 — honest convergence; the CART slice RESOLVED — **PR #105 MERGED** (`278b139e`))

**Subsystem:** explosions / dimension routing (the queue's named audit)
**Affected version:** the current develop baseline (deef2a17)
**Status:** the CORE resolved by PR #100 (merged — another stream's
honest convergence, recorded); the CART slice RESOLVED — **PR #105 MERGED** (`278b139e`, the rider at `32af09f3`); PR
#103 (this stream's whole-family fix) closed as superseded with the
overlap and the remaining slice named

### The reference's law

Every vanilla detonation site calls `this.world.explode(...)` — the
explosion is created in the ENTITY'S OWN world instance, whatever
dimension it lives in:

- `FireballEntity.onHit` line 33: `this.world.explode(null, this.x,
  this.y, this.z, this.explosionPower, flag, flag)`
- `WitherSkullEntity.onHit` line 83: `this.world.explode(this, ...)`
- `CreeperEntity.explode` line 210: `this.world.explode(this, ...)`
- `WitherEntity.mobAiTick` line 217: `this.world.explode(this, this.x,
  this.y + this.getEyeHeight(), this.z, 7.0F, false, mobGriefing)`
- `TntMinecartEntity.explode` line 92: `this.world.explode(this, ...)`

`World.explode` (lines 1601-1607) constructs `new Explosion(this,
source, x, y, z, power, createFire, destructive)` over the SAME world
object — every read (`getBlockState` line 85, `getEntities` line 115,
`getBlockDensity` line 131, `playSound` line 148) and every write (the
`setBlockState` commits, the drop spawns) runs through that one world.
A ghast fireball detonating in the nether destroys NETHER blocks,
wounds NETHER bodies, and drops NETHER items; the overworld at the
same coordinates is untouched.

### The engine's shape (the census — every detonation call site)

The engine already carries the per-dimension services
(`explosionService` over the overworld, `netherExplosionService` over
the nether, `endExplosionService` over the End — EngineServer
2134/2141/2258) and the router `explosionServiceFor(int)`
(24306-24314). `ExplosionService`'s own `World` adapter (`BlastWorld`,
24222-24238) is a thin read/write pass-through over its construction
target — the service reads and commits EXACTLY the world it was built
over, no internal routing. The call-site census:

| Site | World passed | Service passed | Verdict |
|---|---|---|---|
| TntSystem's detonation callback (2036-2041) | `worldForSafe(dimension)` | `explosionServiceFor(dimension)` | correct (FL4-F7) |
| End crystal (20373) | `endWorld` | `endExplosionService` | correct |
| **ghast fireball sink (2543-2547)** | `worldForSafe(dimension)` | **`explosionService`** | **divergent** |
| **wither skull sink (2553-2557)** | `worldForSafe(dimension)` | **`explosionService`** | **divergent** |
| **creeper `explodeOnTick` (24274-24285 → `detonateAtFromMob` 24328-24332)** | `world` (the overworld field) | **`explosionService`** | **divergent** |
| **wither birth `onWitherBirth` (21954 → `detonateAtFromMob`)** | `world` | **`explosionService`** | **divergent** |
| **TNT cart blast sink (2904-2910 → `detonateAtWithSource`)** | `world` | **`explosionService`** | **divergent** |

Plus one casualty inside the shared walk itself:

- **the blast drop roll** (`detonateAtIn`, 24510-24516): the three-arg
  `itemEntities.spawnDropAtBlock(...)` — dimension 0 hardcoded
  (ItemEntityManager 186-189's three-arg form). Even the
  CORRECTLY-ROUTED blasts (the primed TNT in the nether, the End
  crystal) roll their drops into the OVERWORLD at the blast's
  coordinates.

### The observable divergence (the ghast fireball — the natural anchor)

The reachability chain is fully natural: the engine's nether spawn
table carries the ghast (EngineServer 2745-2750, Slice 16d); the
ghast's AI shoots through `projectileManager.launchGhastFireball(...,
mob.dimension())` (21866-21868); the fireball's collision fires
`explosionSink.explode(point, 1.0f, projectile.dimension())`
(ProjectileManager 1227, 1326, 1341, 1379, 1384); the sink (2543)
passes the nether world but the OVERWORLD service. The consequence,
deterministically:

1. **the nether never takes the blast hole** — the ray fan reads the
   overworld's cells (through `explosionService`'s `BlastWorld`), so
   netherrack pays nothing and is never collected; the wound walk's
   block-density read DOES read the nether
   (`woundPlayersFromBlast(blastWorld, ...)` — the routed world), so
   nether bodies are wounded by a blast that never broke a nether
   block;
2. **the OVERWORLD takes the hole at the same coordinates** —
   `commitDamaged` writes through the overworld service: a nether
   mob's routine attack destroys another dimension's terrain
   (cross-dimension world corruption, priority 1);
3. the drops from the destroyed overworld cells spawn stamped
   dimension 0 (the shared casualty above), so the overworld also
   grows the blast's item litter;
4. the fire placement itself IS dimension-routed
   (`fireSink.tryPlaceFire(..., projectile.dimension())` — the F-EX4
   arm), so the engine places fire in the nether over an intact
   nether floor while the overworld's hole grows: the two arms of one
   vanilla detonation land in DIFFERENT dimensions.

The creeper/wither-birth/TNT-cart wrappers are the stronger form of
the same defect: the whole blast (fan, wounds, drops, packet) lands
in the overworld at the body's coordinates regardless of where the
body stands — a creeper blasted in the nether wounds OVERWORLD bodies
standing at the same x/y/z.

### The design-choice challenge (preliminary — the independent pass owes its own)

The `detonateAtIn` javadoc (24334-24337) still reads "the creeper and
the primed TNT ride the overworld binding" — a STALE record: the
primed TNT was routed by Wave FL4-F7 (the callback at 2036 consults
`explosionServiceFor`), and that wave's own precedent (each
dimension's ray fan reads and commits its own world) is the engine's
own statement of the correct shape. The remaining overworld bindings
are the un-migrated remainder, not a divergence choice. (The
independent pass must re-challenge this.)

### The proposed minimal correction

One consult swap per site, the FL4-F7 idiom: the fireball and skull
sinks pass `explosionServiceFor(dimension)`; the creeper and
wither-birth wrappers thread the mob's own `dimension()` through
`detonateAtFromMob`; the TNT cart's `BlastSink` interface gains the
dimension carry and the sink consults the router; the drop roll in
`detonateAtIn` stamps the blast's own `dimension` (the four-arg
`spawnDropAtBlock`). The `worldAir()` read inside `ExplosionService`
rides the service's own world, so the routed service keeps the air
type consistent. The dead `detonateAt` (24316, no callers) may retire
in the same sweep or stand.

### Verification pass history

- **Pass 1 (source census, this run, 2026-10-31):** the five reference
  sites and `World.explode`'s construction law read; the engine's
  seven call sites censused with the verdict table above;
  `BlastWorld`'s pass-through nature verified (24222-24238); the
  drop-stamp casualty found inside the shared walk; the ghast
  reachability chain traced spawn-to-sink. CONCLUSION: the divergence
  is real at the source level for five sites plus the drop stamp.
- **Pass 2 (behavioral, this run, 2026-10-31):** the discovery seats
  AUTHORED and EXECUTED — `ExplosionDimensionRoutingDiscoveryTest`,
  RED on develop BY CONTRACT, stable across three consecutive runs.
  THE FIREBALL SEAT: a dirt wall standing at the same coordinates in
  BOTH dimensions, a carved nether flight corridor, and a ghast
  fireball launched dead-on at the nether wall with dimension -1
  (the launch seam the ghast AI itself rides; the aim baseline 10
  damping the launch's gaussian spread to a ~2-degree tilt — the
  strike deterministic). The engine's answer, three runs: **the
  nether wall 0 holed cells, the OVERWORLD wall 4-6 holed cells** —
  the fireball struck the nether wall (the collision consults the
  projectile's own dimension), and the blast carved the OVERWORLD at
  the same coordinates while the nether stood whole. THE CREEPER
  SEAT: the same two-sided construction, a creeper spawned at the
  probe with dimension -1 stamped and the 5-tick fuse forced (the
  gather test's own construction). The engine's answer, three runs:
  **the nether 0 holed cells, the OVERWORLD 21-24 holed cells** —
  the whole blast (fan, commits) landed in the overworld. The
  preconditions demonstrably pass on develop (the strike/detonation
  happen — the hole appears in exactly one dimension); the RED is
  the vanilla-answer assertion alone, the clean divergence
  demonstration. The construction notes preserved in the seat: the
  explode arm's removal sets no dead flag (the WORLD is the probe,
  not `dead()`), and an isEmpty await alone races the launch (the
  positive launch-presence await comes first).
- **Pass 3 (independent, this run, 2026-10-31): CONFIRMED** — three
  fresh angles, each adding new evidence:
  (a) **THE DESIGN-CHOICE CHALLENGE, SETTLED BY THE ENGINE'S OWN
  RECORD** — the FL4-F7 commit (`b1d997a5`, on develop) names this
  exact shape as a DEFECT it was fixing for the TNT family: "the
  pre-fix overworld-bound service flew a nether blast's rays through
  the overworld's cells and collected nothing — the blast wounded
  but never tore", and landed the per-dimension service plus the
  `explosionServiceFor` router for the primed TNT. The boot comment
  (EngineServer 2138-2141) repeats the law verbatim. The stale
  javadoc at 24334-24337 ("the creeper and the primed TNT ride the
  overworld binding") predates FL4-F7's TNT routing — a stale
  record, not a pinning decision. The five remaining sites are the
  un-migrated remainder of the engine's own recognized defect
  family, not a divergence choice.
  (b) **THE COMPENSATING-BEHAVIOR HUNT** — nothing between the sink
  call and the service consult re-routes the world: the fan (24420)
  and the commit (24478) go through the service's own `BlastWorld`
  (the pass-through, 24222-24238); no post-blast repair or
  cross-dimension sync walk exists anywhere in the detonation path
  (the drops, the events, and the packet fan all read the collected
  list alone); and the wound walks read the ROUTED world — a
  DIFFERENT world than the fan reads, the mixed state itself proof
  that no compensation exists.
  (c) **THE WIRE-LEVEL CONSEQUENCE** (the fresh execution-path
  angle): the packet fan (V18ProtocolServer 1017-1025) gates on
  `event.dimension()` — the blast's own dimension. On develop, a
  nether ghast fireball's packet carries the OVERWORLD cells'
  offsets to the NETHER observers, and the reference's own client
  law (ClientPlayNetworkHandler.handleExplosion lines 823-832)
  constructs the client-side Explosion over the client's CURRENT
  world and applies `damageBlocks(true)` LOCALLY — the nether client
  carves phantom holes into its intact nether view (a server-client
  desync that only a chunk re-send heals), while the OVERWORLD
  observers receive NO packet for their world's real destruction
  (their view keeps intact blocks over destroyed server state). One
  detonation desyncs BOTH dimension views — the corruption is
  observable on the wire, not just in the stores.
  The edge case walked: the unbooted-nether fallback
  (`worldForSafe(-1)` → the overworld) would make the world and the
  service accidentally agree, but the nether boots unconditionally
  with the engine (the boot block at 2138-2141 constructs
  `netherExplosionService` alongside `endExplosionService`), so the
  fallback never masks the defect. One narrow observation recorded
  as a named tail, not the finding's core: the End and nether
  services share the one `gameplayRandom` (the overworld service
  carries its own `new Random()` at 2258), where the reference's
  `Explosion` draws from `this.world.random` — each world its own
  stream (an RNG-parity nuance for its own pass).

**VT-007 raised to CONFIRMED (three passes, distinct evidence each).
THE CONVERGENCE: another stream landed the core the same day — PR
#100 (F-EX11 + F-EX12, MERGED) — the honest overlap recorded: my
census and theirs reached the same law and the same fix idiom
independently (their scope: `detonateAtFromMob`, the two projectile
sinks, the drop roll; their shape also carries the fire-arm
signature mine did not). PR #103 (this stream's whole-family fix,
opened on the pre-#100 baseline) was CLOSED as superseded with the
overlap and the remaining slice named in the closing note. THE
REMAINING SLICE — the census's ONE site PR #100 did not cover: the
TNT cart's `BlastSink` carried no dimension and the factory still
routed through `detonateAtWithSource`'s hardcoded overworld triple
— LANDED as **PR #105, MERGED** (`278b139e`; the branch `fix/tnt-cart-blast-dimension-parity
-audit` based on the post-#100 develop `a95e7ca6`: the BlastSink
dimension carry, the factory's routed `detonateAtIn`, the cart's
own law preserved — destructive TRUE hardcoded, createFire false —
and the callerless `detonateAtWithSource` retired). THE SEATS'
FINAL STATE: the fireball and creeper seats
(`ExplosionDimensionRoutingDiscoveryTest`) run 2/0 GREEN on the
post-#100 develop — the independent confirmation of PR #100's
coverage, authored from this stream's own construction — and ride
in PR #105 as regression pins; the cart seat
(`TntCartBlastDimensionRoutingDiscoveryTest`) is the stash
discrimination's record: UNFIXED at develop's shape the seat fails
at exactly the predicted await (observed nether=0 overworld=18 —
the whole power-4 blast in the overworld platform), FIXED green.
The regression lanes on #105's branch: the vehicle family whole
(the signature's three test implementations updated), the explosion
family, the nether lanes, the tnt + endcrystal packages,
CreeperRows 115/0 combined; the block + world packages 555/0.
On the closed #103's branch the equivalent lanes ran green whole
before the supersession (the seats 2/0 seven consecutive runs, the
construction hardened with the nether headroom carve after one
precondition flake was root-caused to the nether's seed-drifting
natural terrain; one pre-existing lava load flake retired from
consideration — passing alone on both trees, no fire path
touched).

## The twenty-seventh pass — VT-008's verification completed (the #102 coverage verified through the independent seats) + the crossing families unblocked

The baseline advanced to `a95e7ca6` (PRs #96/#97/#98/#99/#100 merged
since the twenty-sixth pass's record — **PR #98's merge unblocks the
remaining crossing families**, the queue's item 6 remainder). This pass
took the queue's head — VT-008's verification — through passes 2 and 3,
using the honest-convergence protocol: no re-fix, the independent
construction instead.

- **VT-008** — the bed's out-of-dimension blast arm: three passes
  complete; the correction is **PR #102's** (OPEN), this stream's
  independent seats verifying its coverage GREEN across every
  discriminator mounted (see the VT-008 record below for the four
  angles beyond the carrier's own seats — the portal-walk anchoring,
  the clock pin, the End seat, and the seated biome conjunct).
- **The crossing families' baseline re-verification** — the authored
  seats re-run on the CURRENT baseline a95e7ca6 (they were authored
  against deef2a17; #96-#100 landed between): the orb, boat, and
  minecart seats RED at exactly the crossing asserts (the clean
  divergence), the item seat RED at the crossing assert on the solo
  run — with ONE precondition flake observed in the combined run
  ("the item rests inside a portal cell" timed out once in two runs;
  no item-family code changed between the baselines — the resting
  poll has a probabilistic failure mode the FIX RUN must root-cause
  before relying on the seat green: the resting-height/horizontal
  boundary is the suspect, the DimensionWalk same-seed-control
  protocol the tool). The divergence demonstrations hold; the arms
  are unblocked.

### The crossing families' fix plan (the next run's head — the handoff)

The seam is on develop (PR #98): the `DimensionCrossing` interface
(MobManager's inner interface — `cross(fromDimension, x, y, z, yaw,
velocityX, velocityZ, memory)` → `Crossing(toDimension, arrival)`),
wired at EngineServer 1037 to `this::crossEntityDimension`
(EngineServer 26184 — the 8:1 walk, the loads, the forcer's
placement). The mobs' arm (MobManager `tickPortalArm` ~1497 +
`crossMobDimension` ~1539) is the reference implementation shape:
the flipped shared query (`setActiveDimension`), the gated collision
(`world.portalFrameAt(feetX, feetY, feetZ)` with the riding gate),
the entry memory derive (`notePortalEntry`), the base clock
(`advanceEntityPortalClock(match != null)` — threshold 0, the 300
cooldown, the re-arm, the -4 decay), then the crossing: the destroy
fan (`onMobRemoved(mob, "portal")`), the service cross, the arrival
apply (`applyDimensionArrival`), the spawn fan (`onMobSpawned`).

The per-family work (each mirrors the reference's own per-family
shape — the reference's MinecartEntity carries its OWN copy of the
arm):

1. **The portal primitives** — `MobEntity`'s are self-contained
   (~120 lines: `advanceEntityPortalClock`, `notePortalEntry`,
   `portalMemory`, `applyDimensionArrival` + the six fields). The
   item, orb, and vehicle families need the same law on
   `ItemEntity`, `ExperienceOrbEntity`, and `VehicleEntity`. DECISION
   POINT: duplicate per family (the reference's own shape, ~3 × 120
   lines) or extract a shared `EntityPortalState` holder (a bounded
   abstraction — four demonstrated consumers including MobEntity's
   own delegation if the maintainer prefers; the minimal-PR form is
   the duplication, the extraction a follow-up). The entities already
   carry `dimension()`/`setDimension` (TF-9/TF-10); the vehicles even
   carry a `portalCooldown` field (the NBT persistence row).
2. **The collision consult's plumbing** — each family's WorldQuery
   (`ItemEntity.WorldQuery`, `ExperienceOrbEntity.WorldQuery`,
   `VehicleEntity.WorldQuery`) needs `portalFrameAt(x, y, z)` (the
   `PortalFrameBuilder.PortalShapeMatch` consult) + the EngineServer
   adapter wirings (three sites — the mobs' adapter at the
   `MobEntity.WorldQuery` implementation is the pattern).
3. **The manager tick arms** — each manager's per-entity loop (the
   item/orb loops already flip `ground.setActiveDimension`; the
   vehicle loop at VehicleManager 675) gains the portal arm BEFORE
   the physics walk (the reference's baseTick order). The riding
   gate: the vehicles' arm consults `rider == null && vehicle ==
   null` (the VT-005 pair — the mobs' arm consults `hasRider()` alone
   because the engine's mobs never ride; the VEHICLES are the
   vehicle — the gate's other half).
4. **The wire fans** — the items: `onItemRemoved(entity, "portal")` +
   `onItemSpawned(entity)`. The orbs: `onOrbRemoved(orb, "portal")` +
   `onOrbSpawned(orb)`. The vehicles: ALREADY RIGHT — the manager's
   `removeVehicle(cursor, vehicle, reason)` (VehicleManager 909) fires
   the drops ONLY for reason "broke" and publishes `onVehicleBroken`
   (the Destroy fan) for EVERY removal — so the crossing calls
   `removeVehicle(cursor, vehicle, "portal")` (the dismount inside is
   a no-op: the riding gate guarantees an empty vehicle at the
   crossing) + `onVehicleSpawned(vehicle)` for the arrival.
5. **The persistence** — the crossing must move the body's snapshot
   between the per-dimension stores (the mobs' arm rides the stamp —
   `applyDimensionArrival`'s dimension write + the snapshot's own
   dimension row; the item/orb/vehicle data stores' save paths
   consult the entity's dimension — verify each store's restore
   lands the body in the right world).
6. **The arrows** — a SEPARATE shape (the projectile system, not a
   manager loop): the census's evidence stands (ArrowEntity.tick
   line 150's super chain); the seat is owed (the flight-path
   construction — the twenty-fifth pass's deferral). Assess whether
   the projectile system's tick owns a per-body walk where the arm
   can ride, or whether the arrows need their own consult.
7. **The regression lanes** — the four seats green 3+ consecutive
   runs (root-cause the item seat's precondition flake FIRST), the
   portal + mob + world lanes, the item/orb/vehicle packages, and
   the crossing-adjacent families (DimensionWalk, the cooldown pin).

The End-origin arm stays the named tail (the mobs' `crossMobDimension`
carries the `from == 1` hold — the engine has no forced-spawn consult
yet); the TNT and falling families stay EXCLUDED (the census: no
portal arm in the reference — the accidental matches must not become
over-crossings).

## The twenty-eighth pass — the crossing families' fix landed (the items, the orbs, the vehicles) + the item seat's flake root-caused

The baseline advanced to `a23011fa` (PRs #101/#102/#104 merged since
the twenty-seventh pass's record — VT-008's carrier and the blast
wound lane both landed; the FL4-F10 redstone world binding landed as
the house stream's own act, not duplicated here). This pass took the
queue's head — the twenty-seventh's handoff plan — through to the fix.

- **The item seat's precondition flake ROOT-CAUSED** (the handoff's
  named first act): the seat's drop rode
  `ItemEntityManager.spawnDropAtBlock` with a precise
  `Position(41.5, 20, 40.5)` — but that verb treats its argument as a
  BLOCK CELL and centers it (`+0.5` on every axis) with the ±0.1 pop
  jitter and the ±0.1 pop velocity: the body landed at
  `z ∈ [40.9, 41.1]` + slide, ON the 41.0 cell boundary of the
  one-block-thick portal plane (the plane is z=40 alone) — a coin flip
  per run whether the centre cell reads the portal or the air beside
  it. THE RE-PIN: the construction rides `spawnThrown`'s exact position
  (41.5, 20.5, 40.5) with ZERO initial velocity — the body falls
  straight onto the bottom row, its centre cell inside the plane from
  the first tick. Deterministic on both trees (the orb/vehicle spawns
  were already exact — their flake-freedom the confirmation of the
  diagnosis).
- **THE FIX LANDED** — `fix/portal-item-orb-vehicle-crossing-parity-audit`
  based on `a23011fa`, commit `05248879`, **PR #112 OPEN** targeting
  `develop`: the three manager-loop families (the items, the orbs, the
  vehicles — boats and minecarts) behind PR #98's `DimensionCrossing`
  seam, the mobs' arm the reference implementation shape. The portal
  primitives per family (the reference's own per-family duplication —
  `MinecartEntity` lines 203-227 carries its own whole copy; the
  vehicles REUSING their existing NBT-persisted `portalCooldown` row),
  the `portalFrameAt` consult (the null default on the two Ground
  interfaces + `VehicleEntity.WorldQuery`, overridden once on
  `ShapeGround` — serving items AND orbs — and on `VehicleWorldQuery`),
  the manager tick arms BEFORE each family's physics walk (the
  reference's baseTick order) with the vehicles' RIDING GATE
  (`hasPassenger()` nulls the consult — PortalBlock lines 158-162's
  rider half, the VT-005 law), and the crossing walks (the destroy fan
  first — the vehicles through `removeVehicle(cursor, "portal")`, the
  drops firing only for `"broke"` — then the seam cross, then the
  arrival apply + the spawn fan, the TF-9/10/11 wire gates carrying the
  per-dimension Destroy/Spawn; the persistence rides the dimension
  stamp both stores already carry).
- **THE SEATS' RESTRUCTURE** — all three seats (authored RED-by-contract
  on develop, the twenty-fifth and twenty-seventh passes) re-pinned to
  the mob seat's ONE-SHOT precondition: an awaited "rests in a portal
  cell" precondition can never survive the fixed tree — the crossing
  fires on the FIRST armed tick, so the poll would read the nether
  arrival's overworld cell and time out. The one-shot read right after
  the spawn await carries the in-cells state; PR #98's own seats
  established the shape.
- **THE RE-PINS the tail decrement demanded** — the reference decrements
  `portalCooldown` every tick for every entity (Entity.baseTick lines
  311-313; the engine's mobs already paid it pre-PR #98); the vehicles'
  previously inert store row went LIVE with the arm.
  `VehiclePersistenceTest` (the exact-45 round trip) and
  `AnvilImportAcceptanceTest` (the exact-12 row) take the Fire row's
  own band shape — the same re-pin the burn clock's activation took
  (the anvil test's own precedent, documented in its assert).
- **THE EVIDENCE** — the three seats GREEN 3 consecutive runs (the
  item, the orb, the boat, the minecart); the regression lanes whole:
  the vehicle package 68/0, the portal family 7/0 (the mob crossing
  with the cooldown and riding pins, the riding gate, the refire, the
  DimensionWalk round trip), the item/orb/tails lanes 51/0, the anvil
  import 6/0, the world package 246/0, the entity + block packages
  887/0.
- **THE HONEST CONVERGENCE (the closing act)** — a parallel stream
  took the same handoff independently and landed the SAME law within
  minutes: their **PR #109** (opened 12:39, eleven minutes before this
  stream's **PR #112** at 12:50) carries the identical architecture —
  the per-family primitives, the same consult plumbing, the same
  manager tick arms, the same riding gate, the same two band re-pins,
  the SAME independently-reached flake root-cause — plus a cleaner
  vehicles' removal fan (the new `onVehicleRemoved(vehicle, reason)`
  listener versus this stream's reuse of `onVehicleBroken`). Their
  stream iterated further: **#108** (12:27, superseded by their own
  #109), **#110** (12:44, the shared-`EntityPortalClock` variant
  covering the arrows too), **#113** (12:49, the arrows stacked on
  #109), beside **#107** (the mob cooldown's own decay lane). This
  stream closed **#112 as superseded by #109** (the VT-007/#100-vs-
  #103 protocol), the closing note offering the three unique
  contributions: the strictly-deterministic `spawnThrown` seat
  construction (zero velocity, no drift envelope — a one-line
  hardening #109 can adopt; #109's block-corner form rides the
  ~0.02-block boundary margin), the 3-consecutive-run stability
  record, and the arrows' source assessment below (whose
  `ShapeGround.portalFrameAt` production wiring #109 also carries).

**The remaining VT-006 family: the arrows** — the separate
projectile-system shape (the census's evidence: ArrowEntity.tick line
150's super chain). The owed seat is the flight-path construction (the
twenty-fifth pass's deferral): assess whether the projectile system's
tick owns a per-body walk where the arm can ride, or whether the arrows
need their own consult. The End-origin arm stays the named tail; the
TNT and falling families stay EXCLUDED (the census's accidental
matches).

## VT-008 — the bed's out-of-dimension blast arm is missing: the sleep walk carries no dimension gate (RESOLVED — **PR #102 MERGED** on `a23011fa`, this stream's independent seats the coverage verification; three passes complete)

**Subsystem:** block use / the bed family / dimension gating
**Affected version:** the develop baselines deef2a17 → a95e7ca6 (the arm absent on both)
**Status:** RESOLVED — another stream's F-EX6 (**PR #102 MERGED** on the
baseline `a23011fa`, head
`3e41cf7b`, based on the current develop) carries the law. This stream's
record: the independent census + source pass (the twenty-sixth), and the
independent behavioral verification of #102's coverage (the twenty-seventh)
— the seat set `BedOutOfDimensionBlastDiscoveryTest` (5 seats) is RED on
develop BY CONTRACT and GREEN 5/5 on #102's branch, two real executions
each. The honest-convergence protocol VT-007's core exercised, completed
for the bed.

### The reference's law (BedBlock.use, lines 45-84)

The sleep walk is gated: `world.dimension.hasSpawnPoint() &&
world.getBiome(pos) != Biome.HELL` (line 45) — only the overworld
dimension (the one with a spawn point) and a non-HELL biome reach the
sleep walk. The ELSE arm (lines 76-82): BOTH bed halves are removed
(`world.removeBlock(pos)` + the opposite half at the facing's
opposite) and the bed EXPLODES in its own world — `world.explode(
null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5.0F,
true, true)` — power 5, fire, smoke. The famous "don't sleep in the
nether" law.

### The engine's shape

`sleepOnTick` (EngineServer 15048+) carries NO dimension consult: the
already-sleeping refusal, the night-window gate (`world.timeOfDay()`
— the OVERWORLD's clock even for a nether body), the
monster-proximity refusal, and then the spawn-set + `enterBed`. The
use dispatch (`isBedHalf(current)` → `sleepOnTick`, 11936-11938) has
no gate upstream either. A bed placed in the nether or the End: sets
the spawn point, enters the bed, and the multiplayer morning walk
(`tickSleep`) can fire — vanilla instead removes the bed and blasts a
power-5 fire-carving hole in the dimension's own world.

### The reachability

Beds are placeable in the nether post-PR #90 (the generic placement
surface is dimension-routed); the use path consults the session's own
dimension for the block read (`worldFor(session.dimension())` — the
FL4-F3 family arm). The blast itself would ride `detonateAtIn` —
which carries the routing machinery VT-007 censused (the correction
must consult the routed service, not the overworld-bound wrappers —
the two findings' fixes compose).

### The proposed minimal correction

The dimension gate at `sleepOnTick`'s head (the session's dimension
!= 0 → the else arm): remove both halves (the `bedHeadCell`/facing
walk the engine already carries), then the power-5 fire blast through
the dimension-routed service. The HELL-biome conjunct needs the
engine's biome model consulted — the flat/nether generators' biome
answer; if the engine's nether answers HELL through its own table the
conjunct collapses to the dimension gate alone (the reference's own
effective law for the vanilla dimensions).

### Verification pass history

- **Pass 1 (source, the twenty-sixth pass, 2026-10-31):** BedBlock.use
  lines 45-84 read in full — the gate's conjuncts, the else arm's
  remove-both-halves order, the explode call's power/fire/smoke
  flags; the engine's `sleepOnTick` + the use dispatch read — no
  dimension consult anywhere on the path. CONCLUSION: the arm is
  absent at the source level.
- **Pass 2 (behavioral, the twenty-seventh pass, 2026-10-31):** the
  independent seat set `BedOutOfDimensionBlastDiscoveryTest`, 5 seats,
  executed on BOTH trees. RED on develop (a95e7ca6) BY CONTRACT, two
  consecutive stable runs: the four wrong-world seats fail at exactly
  the vanilla-answer awaits ("the pair left whole") with every
  construction precondition passing — the portal crossing crossed, the
  carved scenes landed, the dimension stamps held; the overworld guard
  GREEN. GREEN on PR #102's branch (`3e41cf7b`) 5/5, two real
  executions (the second cleanTest-forced). The four discriminators
  beyond the carrier's own seats: (a) the CORE seat portal-walk
  anchored (DimensionWalk's own construction — a body that REACHED the
  nether through the real crossing, not a stamped session) pins the
  pair's removal, power 5.0F, the blast's dimension -1, fire in the
  NETHER crater, NO sleep, and NO SPAWN SET — the pre-fix tree set the
  spawn and slept against the overworld's clock at a phantom overworld
  bed (the head normalization read the overworld field); (b) the CLOCK
  PIN — the else arm never consults the time: the nether bed explodes
  at DAY too (the pre-fix tree refused through the overworld night
  gate here, nothing happened at all); (c) the END — dimension 1
  answers hasSpawnPoint false the same way: the End bed blasts the End
  world (the carrier's source-only coverage, now seated); (d) the
  BIOME CONJUNCT — an overworld bed in a HELL-biome column (the
  anvil-import custom-map edge the carrier's pass-3 record called
  unseatable) explodes IN THE OVERWORLD: the seat writes the chunk's
  own biome rows directly (`EngineChunk.setBiome`, Biome.HELL's 1.8
  wire id 8), the conjunct its own gate independent of the dimension.
- **Pass 3 (independent, the twenty-seventh pass, 2026-10-31):** the
  execution-path and edge-case walk over #102's actual diff: (a) the
  gate translation — `session.dimension() == 0 &&
  !"hell".equals(world.biomeAt(bedHead))` against the reference's
  `world.dimension.hasSpawnPoint() && world.getBiome(pos) !=
  Biome.HELL`: the three Dimension classes' rows verified (base true,
  Nether/TheEnd false — the engine's 0/-1/1 model makes `== 0`
  equivalent), and the biome consult rides the NORMALIZED head (the
  reference's own `pos` after the foot redirect); (b) the removal
  order — head, sibling foot, THEN the blast; the four-neighbor foot
  walk (fixed order, first bed-foot) vs the reference's
  FACING-opposite walk: the engine's own bed model carries no FACING
  state (`bedHeadCell` and the break walk use the same four-neighbor
  convention), so the difference surfaces only in the multi-bed
  adversarial adjacency — an engine-model tail shared by the existing
  laws, not a #102-specific defect; the reference's
  `getBlockState(...).getBlock() == this` matches either half while
  #102 requires a foot — equivalent for legal placement states (a
  head's facing-opposite can only hold a foot or a non-bed); (c) the
  blast — `detonateAtIn(world, explosionServiceFor(dimension),
  dimension, head+0.5, 5.0F, destructive=true, createFire=true,
  PLAIN, null)`: the engine's parameter order puts destructive before
  createFire (both true here — symmetric), the source mob null matches
  the reference's null exploder, the center the head's +0.5, the
  routing the FL4-F7/VT-007 idiom (the acting world's own service);
  (d) `World.explode` lines 1601-1607 read — `new Explosion(this,
  source, ...)` over the bed's OWN world, `damageEntities()` then
  `damageBlocks(true)`; `World.removeBlock` line 281 read —
  `setBlockState(pos, AIR, 3)`: drop-less, neighbor-updating — the
  engine's `setBlock(head, airType())` equivalent; (e) the no-spawn
  law, the clock independence, the orphan-foot early return (the
  carrier's own guard seat), and the sleep walk's unchanged overworld
  arm (the old `bedHeadCell`'s overworld consult correct there — the
  gate has already established dimension 0) — all seated or
  source-verified; (f) the design-choice challenge — no ledgered debt
  anywhere in the sleep walk's records; the shape is the FL4-F3 wave's
  un-migrated remainder (the use path was routed per-dimension but the
  bed dispatch's gate was left to the sleep walk), the same family
  VT-007's sites formed; (g) the compensating hunt — the seats prove
  the develop arm runs the sleep walk (the spawn set, the body
  sleeping) and no other bed-blast site exists.

**CONCLUSION:** VT-008 CONFIRMED over three passes, distinct evidence
each. The correction is PR #102's (another stream — the honest
convergence): this stream's verification finds #102's coverage complete
across every discriminator this construction could mount, including the
four angles the carrier's own seats do not carry. The seat set rides
this audit branch as the verification record; offered to #102 as
additional coverage (the comment). Once #102 merges, the seats become
regression pins on develop (GREEN on the merged tree — the same shape
VT-007's seats took as #105's pins).

## The arrows' source assessment (the twenty-eighth pass's closing act — the next run's fix head, the handoff)

The manager-loop families landed (PR #112); this closing act took the
remaining family — the arrows/projectiles — through the SOURCE PASS and
the integration assessment. The record for the next run:

**THE REFERENCE'S CENSUS (wider than the pass-24 name):** the portal
arm rides EVERY projectile family's tick chain, not the arrows alone —
`ArrowEntity.tick` line 150 (`super.tick()`), `ThrownEntity.tick` line
123 (the snowball/egg/potion/exp-bottle base), `EnderPearlEntity.tick`
line 82 (the pearl's own override), and `ProjectileEntity.tick` line 82
(the FIREBALLS' inherited arm — `FireballEntity`/`SmallFireballEntity`/
`WitherSkullEntity` carry NO tick override of their own, so the
conditional `super.tick()` at ProjectileEntity line 81 —
`isClient || (shooter == null || !shooter.removed) && isChunkLoaded`
— gates the arm for the fireball family; a fireball whose chunk is
unloaded never ticks at all, the edge to preserve). The
`FishingBobberEntity.tick` line 173 ALSO calls `super.tick()` — the
bobber carries the arm in the reference too, but the engine's fishing
system is its own package (not the ProjectileManager); the bobber's
arm is a NAMED TAIL, not part of the projectile family's fix (the
rod-link semantics across a crossing need their own pass). The riding
gate passes vacuously for every projectile family (nothing rides, and
projectiles carry no riders).

**THE ENGINE'S SHAPE:** `ProjectileManager.tick`'s per-body loop (the
backward index walk) carries the TF-9 flip
(`solid.setActiveDimension(projectile.dimension())`) but ZERO portal
consults — the census's standing record. `ProjectileEntity` carries
`dimension()`/`setDimension` (the TF-9 stamp) and the Kind enum spans
ARROW, SNOWBALL, EGG, SMALL_FIREBALL, LARGE_FIREBALL, WITHER_SKULL,
POTION, ENDER_PEARL (+ the experience bottle) — the family the fix
must cover whole (the reference's own scope), not the arrows alone.

**THE INTEGRATION POINTS (the same shape the manager-loop families
took):**
1. `ProjectileEntity` gains the portal primitives (the per-family
   duplication: `advanceEntityPortalClock`, `notePortalEntry`,
   `portalMemory`, `applyDimensionArrival` — the position convention
   is the CENTER like the items/orbs; no yaw/pitch state to rotate,
   the wire derives them from the velocity).
2. `ProjectileManager.SolidQuery` gains the `portalFrameAt` null
   default — **the production wiring is ALREADY DONE by PR #112**:
   `ShapeGround` (the SolidQuery's production implementation) carries
   the override, so the interface's declaration is the only plumbing
   left.
3. The tick arm rides at the loop's HEAD — BEFORE the `inGround()`
   branch: the reference runs baseTick for STUCK arrows too (the arm
   fires for an arrow standing in portal cells, in ground or not —
   the edge the manager-loop placement must preserve; the arrival
   keeps the in-ground state through the reference's copyNbtFrom).
4. The crossing walk: `remove(projectile, "portal")` (the manager's
   own walk — the listener fan) → the seam cross (the same
   `crossEntityDimension`) → the arrival apply → `onProjectileSpawned`
   (the spawn fan). The fireball families' conditional (the
   chunk-loaded gate) does not bind the ARM itself in the engine's
   loop shape — the engine's loop is unconditional; the loaded-cell
   edge rides the consult's own cell read (an unloaded chunk answers
   no portal match, the same form the other families ride).
5. The seam wiring: `setDimensionCrossing(this::crossEntityDimension)`
   at the ProjectileManager's boot site.

**THE OWED SEAT (the flight-path construction — the next run's first
act):** an arrow SHOT through the portal plane — in flight, crossing
the cells mid-flight: the bow's launch toward the frame's interior,
the flight crossing the plane, the crossing firing on the first armed
tick (the threshold 0), the arrival continuing the flight in the
nether. The stuck-arrow seat beside it (an in-ground arrow in portal
cells crosses too — the inGround branch's placement the discriminator).
The RED-by-contract record on develop first (the discovery seat), the
fix branch behind the seam second, the regression lanes (the projectile
package whole + the portal family + the fishing lanes' non-interaction)
third.

## The twenty-eighth pass — VT-008 re-verified GREEN on the MERGED law (the second stream's confirmation), VT-009 discovered and confirmed whole (the break-path pair walks' world binding), VT-010 discovered (the bed pair's sibling resolution)

The baseline advanced to `7910a110` (PRs #101/#102/#104 MERGED —
VT-008's carrier #102 is on develop; the TNT-cart slice #105 remains
the one OPEN PR). This pass ran on that current baseline and took three
lanes:

- **VT-008** — re-verified GREEN through a SECOND independent
  construction (`BedWrongWorldBlastVerificationTest`: the END seat —
  the gate's second wrong world, not seated by either stream's earlier
  records — and the NETHER head-half click, the normalization identity
  path). #102's coverage holds on the merged develop; the finding stays
  closed as RESOLVED-BY-PR. The convergence: BOTH verification streams
  (the twenty-seventh's portal-walk construction and this pass's
  stamped-session construction) reached GREEN on the same law from
  different baselines.
- **VT-009** — the block-broken listener's DOOR/BED PAIR WALKS are
  overworld-bound: a nether/End break orphans the surviving half and
  destroys an overworld cell at the sibling's coordinates. CONFIRMED,
  three passes complete (the census, the RED seats both families, the
  execution-path trace through the change-listener chain). The
  correction LANDED this pass as **PR #106 OPEN**
  (`fix/pair-break-dimension-parity-audit`: the `BlockBrokenHook`
  three-argument form, the parameterized walks, the parity pin 3/0,
  the regression lanes 75/0).
- **VT-010** — the bed pair's sibling resolution is a POSITIONAL SCAN,
  not the reference's FACING-EXACT law: a foreign adjacent bed half
  wins the scan and the sleep redirect, the blast center, and the
  break-path pair death all resolve the WRONG pair. Two passes complete
  (the census, the RED seats with exactly the traced values); the third
  pass (the wire/legacy-data consistency of the facing suffix the fix
  would read) is owed before the fix.

---

## VT-009 — the block-broken listener's pair walks are overworld-bound: a nether/End bed or door break orphans the surviving half and destroys an overworld cell at the sibling's coordinates (RESOLVED — the correction is **PR #106 OPEN**; three passes complete)

**Subsystem:** the break path / the door and bed pair deaths /
dimension routing.
**Affected version:** the current develop baseline (`7910a110`) and every
earlier tree that carries the walks (the pre-#90 dig routing era, the
FL4-F5 dig routing, the post-#102 bed law alike — the walks predate and
survive all of them).

**Affected engine files and methods:**
- `server/EngineServer.java` — `doorSiblingCleanup` (~14639-14652): the
  below/above reads and the removal all through the `world` FIELD
  (the overworld). `bedSiblingCleanup` (~14680-14697): the
  four-neighbor scan, the sibling test, and the removal the same field.
  Both are invoked from the `setBlockBrokenListener` lambda (~3644-3688)
  which receives `(position, brokenType)` and carries NO world.
- `server/interaction/BlockInteractionService.java` —
  `setBlockBrokenListener` (~1100: a
  `BiConsumer<BlockPosition, BlockType>`) and its two invocation sites:
  the creative break (~477) and the survival finish walk (~928+), both
  of which have the acting `world` (the `worldOf(player)` local) in
  scope and pass only the position and type.

**Vanilla reference:** `DoorBlock.neighborChanged` (lines 156-198) and
`BedBlock.neighborChanged` (lines 156-170): the surviving half's own
`neighborChanged` fires through the per-world neighbor dispatch — the
world the pair stands in (`World.updateNeighbors` within one World
instance; the reference's `ServerWorld` machinery never crosses the
world boundary). Breaking either half of a door or bed pair in the
nether removes the OTHER half IN THE NETHER; the overworld at the same
coordinates is a different world and no walk ever reads or writes it.

**Observed engine behavior (through the real break wire):**
1. A player in the nether creative-breaks the nether bed's FOOT. The
   dig itself commits correctly (post-#90: the break lands in the
   nether), the listener fires, and `bedSiblingCleanup` scans the four
   horizontal neighbors **of the overworld field**: the overworld cell
   at the head's coordinates is consulted. With an opposite-class half
   standing there, the walk REMOVES IT IN THE OVERWORLD — a nether
   action edits another dimension's terrain. The NETHER's own head is
   never read and survives as an orphan.
2. The door walk is the same shape vertically: a nether door's lower
   break consults the OVERWORLD's above/below cells; a complete
   overworld door pair at the same coordinates loses its UPPER to the
   nether break, and the nether's own upper orphans.
3. Survival breaks fire the same listener at the finish-walk site —
   the mode does not matter, only the world binding does.

**Verification pass history:**
- **Pass 1 (source census, this run, 2026-10-31):** the listener
  signature read (`BiConsumer`, no world), both invocation sites read
  with the acting-world local in scope, both walks' bodies read against
  the `world` field. The PR #90 architecture record (Torch-Arcetecture
  §"THE NAMED REMAINDER") names this exact listener's cleanups as the
  owed remainder — the door/bed sibling walks beside the container
  spills. The reference's per-world neighborChanged read beside them.
- **Pass 2 (behavioral, this run, 2026-10-31):** the discovery seats
  `PairBreakDimensionDiscoveryTest` (3 seats) — RED on develop at
  exactly the predicted assertions, the overworld guard GREEN:
  - THE BED: the nether pair's foot broken → `expected: <minecraft:air>
    but was: <minecraft:bed_head>` (the nether head ORPHANED) AND
    `expected: <minecraft:bed_head_north> but was: <minecraft:air>`
    (the overworld probe DESTROYED by the nether break).
  - THE DOOR: the nether lower broken → `expected: <minecraft:air>
    but was: <minecraft:oak_door_upper>` (the nether upper orphaned)
    AND `expected: <minecraft:oak_door_upper> but was: <minecraft:air>`
    (the overworld pair's upper destroyed).
  - The construction laws learned and recorded: an orphan door UPPER
    probe self-destructs through the engine's own door orphan law
    (the probe must be a COMPLETE pair), and a door LOWER with air
    below dies through the support-loss law (the probe needs its own
    floor) — both flanks were root-caused before the seats ran clean.
- **Pass 3 (independent re-verification, this run, 2026-10-31):** the
  execution-path trace whole: the break commit goes through
  `EngineWorld.setBlock`, which fires BOTH the change listeners — and
  NEITHER compensates: `RedstoneSystem.onBlockChanged` (line 343)
  IGNORES its `changedWorld` parameter in the six-neighbor dispatch
  (`neighborChanged` reads the system's `world` field), and
  `BlockUpdateSystem.runUpdate` (line 424) reads the `world` field —
  so the nether door upper's neighbor wake demonstrably consults the
  OVERWORLD cells (the seats' standing nether survivors are the
  proof). The compensating-behavior hunt therefore found NOTHING for
  the pair death: no path anywhere reads the nether cells for the
  sibling. The design-choice challenge: the walks' own javadocs state
  the vanilla intent ("the pair dies as a unit", DoorBlock/BedBlock
  citations), and the PR #90 record explicitly defers these cleanups
  as the named remainder — the binding is the un-migrated remainder
  of the engine's own recognized defect family, not an intentional
  divergence. The update-wake world binding itself (the redstone
  `neighborChanged` field read, `runUpdate`'s field read) is the
  house stream's FL4-F10 lane — cross-referenced there, not duplicated
  by this stream.

**Correction (LANDED this pass — PR #106 OPEN,
`fix/pair-break-dimension-parity-audit`, commit `847387b6`):** the
acting world threaded through the break listener exactly as proposed —
the `BlockBrokenHook` three-argument form
`(position, brokenType, actingWorld)` (the engine the sole
registrant), the `worldOf(player)` local passed at both invocation
sites (creative + survival), and the parameterized
`doorSiblingCleanup`/`bedSiblingCleanup`. THE EVIDENCE: the parity pin
`PairBreakDimensionParityTest` 3/0 on the fix branch (the nether bed
pair dies whole in the nether with the overworld probe standing, the
nether door pair the same, the overworld guard unchanged) — the same
seats that run RED on develop (`PairBreakDimensionDiscoveryTest`, this
branch, the stash-discriminated record); the regression lanes 75/0
whole (Sleep, MultiplayerSleep, BedWrongWorldBlast,
BlockInteraction, DigHandlerGates, NetherDigRouting,
FurnaceHopperEvent, BlockUpdateSystem, the chest family, the hopper
family, EnderChest). THE DELIBERATE NON-CHANGES recorded: the zombie
door-break arm passes the same world its own read/removal already
consult (that lane's binding is its own ledgered row), and
`bedSpawnCleanup` stays overworld-anchored (bed spawns are overworld
beds since the #102 gate — the consult is semantically the overworld
one).

**Regression risks:** the overworld pair-death seats (the new guard
seat, the existing door/bed acceptance lanes) must stay green — the
overworld arm resolves through the same parameterized world (the
overworld is the acting world there, byte-identical behavior); the
container-manager arms of the same listener are NOT touched (their
per-dimension stores are the ledgered architectural pass).

---

## VT-010 — the bed pair's sibling resolution is a positional scan, not the reference's facing-exact law: a foreign adjacent bed half hijacks the sleep redirect, the blast center, and the pair death (RESOLVED — the correction is **PR #111 OPEN**; three passes complete)

**Subsystem:** block use / the bed family / the pair model.
**Affected version:** the current develop baseline (`7910a110`) — the
model predates every dimension fix (it is dimension-blind; the
divergence reproduces in the OVERWORLD).

**Affected engine files and methods:**
- `server/EngineServer.java` — `bedHeadCellIn` (~15128-15141, the
  dispatch's head normalization), `bedHeadCell` (~15198-15214, the
  sleep walk's redirect), `detonateBedOnTick`'s foot removal
  (~15103-15111), and `bedSiblingCleanup`'s selection (~14684-14696):
  every walk scans the four horizontal neighbors for ANY half of the
  opposite class and takes the first found (order +x, -x, +z, -z).
- The identifiers carry the pair direction (`bed_head_south`'s foot
  sits north; `bed_foot_south`'s head sits south — the placement
  `placeBedOnTick` writes the look band's suffix on BOTH halves), so
  the exact cell the reference names is computable from the id alone.

**Vanilla reference:** `BedBlock.use` lines 40-41 (the FOOT redirect:
`pos.offset(state.get(FACING))` — the foot's OWN facing), use lines
71-75 (the else arm removes the foot at
`pos.offset(state.get(FACING).getOpposite())` — the head's OWN facing),
and `BedBlock.neighborChanged` lines 156-165 (the pair walk tests the
cell the facing names). Two adjacent beds are freely placeable in
vanilla (nothing in `canBePlaced` forbids a foreign half adjacent), so
the facing law is the only thing that keeps the clicked pair's walk on
its own pair.

**Observed engine behavior (all three demonstrated RED on develop,
this run, `BedPairResolutionDiscoveryTest`):**
1. THE BREAK SELECTION: bed A's head broken with bed B's foot adjacent
   (+x wins the scan): `expected: <air> but was: <bed_foot_*>` — A's
   own foot ORPHANED; `expected: <bed_foot_east> but was: <air>` —
   B's FOREIGN foot destroyed. The reference kills A's own foot only.
2. THE SLEEP REDIRECT: bed A's foot clicked at night with bed C's
   head adjacent (+x wins): the bed spawn lands at
   `expected: <20.5>/<1.5> but was: <21.5>/<0.5>` — the sleep pose,
   the spawn, and the morning walk all land on the FOREIGN pair.
3. THE NETHER BLAST CENTER: bed A's foot clicked in the nether with
   bed D's head adjacent: the power-5 fire blast centers at
   `expected: <64.5>/<65.5> but was: <65.5>/<64.5>` — the else arm
   removed and blasted the FOREIGN pair's head, one block off the
   vanilla center (and the foreign pair's foot with it), leaving the
   clicked pair's own head standing at the moment of the blast.

**Verification pass history:**
- **Pass 1 (source census, this run, 2026-10-31):** all four walks
  read against the reference's three facing laws; the placement walk
  read to establish the suffix semantics (the look band's S/W/N/E =
  +z/-x/-z/+x for the head, both halves stamped); the reachability
  argument (adjacent foreign halves are placeable; the #102 record
  itself names "the adjacent opposite half class" as the model's own
  convention — a stand-in, not a documented intentional divergence).
- **Pass 2 (behavioral, this run, 2026-10-31):** the three seats
  above, RED on develop with EXACTLY the traced values (both the
  wrong-pair coordinates and the surviving/destroyed halves).
- **Pass 3 (independent — the wire/legacy suffix-consistency walk, this
  run, 2026-10-31, the twenty-ninth pass over develop `a23011fa`):**
  the fix-safety question this pass owed — "can any writer stamp a
  suffix that disagrees with the pair geometry?" — answered by a total
  census of every bed-id writer, with the reference's metadata law read
  fresh:
  1. **THE ID FAMILY IS THE REFERENCE'S METADATA SPACE, TOTAL:** the
     registry holds exactly eight bed ids — `bed`/`bed_west`/`bed_north`/
     `bed_east` (feet, `BuiltinBlocks` 449-456) and `bed_head`/
     `bed_head_west`/`bed_head_north`/`bed_head_east` (heads, 457-464) —
     stamped with the SAME numeric metas the reference's
     `getMetadataFromState` writes (facing bits 0-1 per
     `Direction.getIdHorizontal` — the reference's enum lines 14-17 name
     S=0, W=1, N=2, E=3 — plus the head bit 8). The suffix-less ids are
     the SOUTH variants (metas 0 and 8). `AnvilBlockIds` rows 234-241
     map `(26, 0..3) → bed..bed_east` and `(26, 8..11) → bed_head..bed_head_east`
     — the identical numbers; `LegacyBlockIds.metadataOf` (rows
     1558-1564, the wire-out table) agrees; the `/setblock` meta
     validation (EngineServer 27322: `meta != type.legacyMetadata()` →
     refuse) pins the meta to the id's own embed. The bijection means
     **no layer can carry a suffix that disagrees with the vanilla state
     it represents** — the "unrecognized id" fallback the pass-2 draft
     reserved is unreachable: every writer resolves ids through this
     registry family.
  2. **THE WRITERS:** `placeBedOnTick` (EngineServer 14926-14931) stamps
     both halves from the one lookBand — consistent by construction;
     the anvil import writes the id whose embedded meta IS the region
     nibble — faithful to whatever the region says, and the reference's
     own walks are facing-exact over exactly those states (so a
     hand-edited inconsistent region resolves under the fix EXACTLY as
     vanilla resolves it — the scan, not the facing law, is the layer
     that would diverge there); `/setblock`//`/fill`//`/clone` resolve
     registry keys — the admin can stamp any half anywhere, and the
     reference's own `/setblock` allows the same states with the same
     facing-exact walks behind them.
  3. **THE MEMBERSHIP LAW — a sub-divergence surfaced and pinned this
     pass:** the reference's redirect accepts ANY bed half at the
     facing-named cell (`use` lines 39-42: `state.getBlock() != this` —
     block-class, not part-class), and the else arm then proceeds from
     THAT cell's own state (`use` lines 71-76: the removal reads the
     state at the anchor — `pos.offset(state.get(FACING).getOpposite())`
     — whatever class it holds). The engine's scans search for the
     opposite PART specifically (a head for the redirect, a foot for
     the detonate removal), so a foreign FOOT standing at a clicked
     foot's own head cell ABORTS the engine's whole use (the scan finds
     no head → `bedHeadCellIn` null → the click consumed silently)
     while the reference redirects onto it and detonates there. The
     correction's membership is therefore CLASS-based at the
     facing-named cell (the reference's own `!= this`), with the
     detonate removal reading the anchor's own state — pinned by the
     parity pin's fourth seat (`BedPairFacingResolutionParityTest`,
     the foreign-foot corner: no blast at all on develop, the anchor
     blast on the fix).
  4. **THE COMPENSATING-BEHAVIOR HUNT:** the FL4-era comment ("the
     adjacent opposite half class — the break walk's own convention")
     names a stand-in, not a deliberate divergence — the `#102` record
     itself carried the class scan as "the engine's bed model" while
     citing the reference's facing lines beside it; no test, table, or
     doc pins the scan as intentional. The neighboring systems that
     touch bed cells (`bedSpawnCleanup` reads the anchor cell alone;
     `monstersNearBed` reads the resolved head; the sleep wake and the
     dawn walk read the session state) ride the RESOLVED cell, so they
     inherit the correction without their own edits.
  5. **THE LIMITATION SURFACED (a NEW finding — VT-011 below):** the
     reference persists OCCUPIED bed heads at metas 12-15
     (`getMetadataFromState` lines 220-231 — bit 2 written for heads
     alone) and reads them back (`getStateFromMetadata` lines 200-205 —
     the occupied bit read on the head arm; metas 4-7 load as feet with
     bit 2 ignored). `AnvilBlockIds` maps only 0-3 and 8-11, so a
     vanilla world saved mid-sleep imports its occupied head AS AIR
     (the counted isolation) — a dangling foot where vanilla loads the
     pair whole. This is an IMPORT-table gap, not a facing-law hazard:
     every half the import DOES write carries the geometrically exact
     suffix, so the facing-exact correction behaves exactly as the
     reference over every state the engine's import produces. VT-011
     records the import gap as its own finding (its passes 2-3 owed).

  **CONCLUSION: pass 3 CONFIRMS — three passes complete. The
  correction is eligible: the facing-exact resolution (the id's own
  suffix names the cell; class membership at that cell; the detonate
  removal reads the anchor's own state), the three RED seats plus the
  foreign-foot corner as the parity pin.**

**Correction (now landed on the fix branch, three passes complete):**
resolve the sibling from the identifier's own suffix through one shared
helper — a foot's head cell at `pos.offset(suffix)` (the reference's
`use` redirect), a head's foot cell at `pos.offset(suffix.opposite())`
(the reference's else arm and creative break) — with the membership
test at the named cell being BLOCK-CLASS (`isBedHalf`, the reference's
own `getBlock() != this`), and the detonate removal reading the
anchor's own state (the reference's `state.get(FACING).getOpposite()`
at the redirected cell, class-agnostic backward). All four walks
(`bedHeadCellIn`, `bedHeadCell`, `detonateBedOnTick`'s removal,
`bedSiblingCleanup`'s selection) consult the helper; the scan and its
+x-first order disappear. The foreign-adjacent-half cases then resolve
to the reference's own cells in every arm.

**Regression risks:** the plain-pair seats (the #102 blast seats, the
sleep seats, the new VT-009 seats — all built on plain pairs where
scan and facing agree) must stay green; the orphan-foot early return
(the #102 guard) must survive the new resolution shape unchanged.

---

## VT-011 — the anvil import drops the reference's OCCUPIED bed states: metas 12-15 (an occupied head, persisted by the reference whenever a world saves mid-sleep) and 4-7 (bit-2-corrupted feet) import AS AIR, corrupting the pair (RESOLVED — the correction is **PR #115 OPEN**; three passes complete)

**Subsystem:** world import / the anvil canvas / the 1.8.8 numeric
state table.
**Affected version:** every baseline that carries `AnvilBlockIds` —
the table's rows predate the pass record (`a23011fa` inspected).

**Affected engine files and methods:**
- `zamin-core/.../world/anvil/AnvilBlockIds.java` — the legacy-26
  rows (lines 234-241): only metas 0-3 (`bed`..`bed_east`) and 8-11
  (`bed_head`..`bed_head_east`) resolve; the class javadoc (lines
  8-15) names the fallback law: "a numeric state the table does not
  know imports as air with a counted isolation."

**Vanilla reference:** `BedBlock.getMetadataFromState` lines 220-231
— the metadata write: facing bits 0-1, the head bit 8, and **bit 2
written for an OCCUPIED HEAD alone** (`if (state.get(PART) == HEAD)
{ i |= 8; if (state.get(OCCUPIED)) { i |= 4; } }`) — so a world that
saves while a player sleeps (the auto-save or a mid-sleep shutdown)
persists the head at metas 12-15. `BedBlock.getStateFromMetadata`
lines 200-205 — the read: `(metadata & 8) > 0 ? ...set(OCCUPIED,
(metadata & 4) > 0) : ...` — the occupied bit is read on the head arm
alone, and metas 4-7 (bit 2 without bit 3) load as ordinary feet
with bit 2 ignored. `BedBlock.use` lines 59-60 stamps OCCUPIED on
successful sleep entry (the head cell, flag 4).

**Expected behavior:** a 1.8.8 region containing a bed head at meta
12-15 (or the editor-corrupted feet 4-7) loads the bed half whole —
vanilla reads every one of the sixteen nibbles as a valid bed state.

**Observed engine behavior (source, pass 1):** the anvil table maps
only 0-3 and 8-11; metas 12-15 and 4-7 fall to the counted-isolation
air path. An imported world saved mid-sleep therefore loses the
occupied HEAD as air while its foot imports — a dangling foot where
vanilla loads the pair whole. The corruption is persistent (the
import is the world's new truth) and silent to the importer (the
isolation is counted, not surfaced to the player).

**Verification pass history:**
- **Pass 1 (source, this run, 2026-10-31, the twenty-ninth pass):**
  the metadata law read fresh from the reference (the write lines
  220-231, the read lines 200-205, the sleep-entry stamp lines
  59-60); the engine table's rows and the class's own fallback law
  read against them; the divergence pinned to the sixteen-nibble
  coverage. **The reachability named:** the reference's own save
  path writes 12-15 whenever a chunk with an occupied head saves
  (mid-sleep auto-save or shutdown); metas 4-7 are unwritable by the
  reference's own save path (the foot never writes bit 2) — only
  external editors produce them, and the reference loads them as
  feet regardless.
- **Pass 2 (behavioral, this run, 2026-10-31, the twenty-ninth
  pass):** `BedOccupiedImportDiscoveryTest` (this audit branch), two
  seats through the REAL import wire (the fixture's own
  reference-ported region writer, the `anvilimport` command, the
  awaited finish report), **RED on develop BY CONTRACT** —
  2/2 failures with exactly the traced cells and the import's OWN
  ledger counting them:
  1. THE MID-SLEEP PAIR: the foot at (129,1,129) meta 0 imports (the
     control, GREEN both trees); the occupied head at (129,1,130)
     meta 12 — the vanilla mid-sleep save shape
     (`0 | 8 | 4`) — imports as AIR: the report's own row
     `unknown block states (imported as air): id 26 meta 12 x1`
     (the failure message carries the whole report); the seat
     asserts the reference's answer (a bed head, the pair whole).
  2. THE EDITOR-CORRUPTED FOOT: the head at (132,1,133) meta 9
     imports (the control); the corrupted foot at (133,1,133)
     meta 5 (west | the stray bit 2 — the reference's foot arm
     ignores bit 2, bits 0-1 alone the answer) imports as AIR:
     `id 26 meta 5 x1` counted; the seat asserts the reference's
     answer (a west-facing foot).
- **Pass 3 (independent, this run, 2026-10-31, the twenty-ninth
  pass):** three angles, all new evidence:
  1. **THE LIFECYCLE WALK:** the reference's wake path clears the
     flag (`PlayerEntity.wakeUp` line 1174:
     `world.setBlockState(sleepingPos, state.set(BedBlock.OCCUPIED,
     false), 4)`) — so the mid-sleep window (the sleep-entry stamp
     `use` lines 59-60 → the wake clear) is exactly the window whose
     save persists 12-15 (the auto-save's 6000-tick schedule and
     the stop/crash paths both fall inside it); a crashed server
     never runs the clear at all.
  2. **THE SIXTEEN-NIBBLE CENSUS:** the reference's read law
     covers every nibble (0-3 feet; 4-7 feet with bit 2 ignored —
     the foot arm of `getStateFromMetadata` never consults it;
     8-11 heads; 12-15 occupied heads, the head arm's
     `(metadata & 4) > 0`); the engine table's residual set is
     EXACTLY {4-7, 12-15} — both bands demonstrated RED by the
     seats, with the controls (0 and 9) GREEN in the same scenes.
     `resolveVirtualProperties` lines 208-217 (the foot's OCCUPIED
     re-read from the head cell at runtime) pins the HEAD's nibble
     as the load-bearing persistence — exactly the cell the engine
     drops to air.
  3. **THE COMPENSATION HUNT:** the nibble passes RAW to the table
     (`AnvilChunkReader` line 316 — no masking, no remap); the table
     is the single resolution point; the engine's OWN save/sync side
     never writes 12-15 (the engine's sleep model carries no
     occupied block state — the wire-out bed rows are 0-11 alone),
     so the divergence is IMPORT-ONLY: a vanilla world → the
     engine. No compensating layer exists.

  **CONCLUSION: pass 3 CONFIRMS — three passes complete. The
  correction is eligible: the eight missing rows (12-15 → the head
  ids by facing bits; 4-7 → the foot ids by facing bits, bit 2
  masked — the reference's own read law), the table's javadoc
  gap-fills list, the integrity test's expected bands + the count
  re-pin, the two seats as the parity pin.**

**Correction (eligible — three passes complete):** the eight missing
rows — `(26, 12..15) → bed_head/bed_head_west/bed_head_north/bed_head_east`
(the occupied bit is above the engine's sleep model: the engine
tracks occupancy by the sleeping session, not the block state — the
plain head ids are the correct import answers) and `(26, 4..7) →
bed/bed_west/bed_north/bed_east` (bit 2 masked, the reference's own
foot-arm read — bits 0-1 alone). The table's javadoc gap-fills list
gains the bed band; `AnvilBlockIdsIntegrityTest`'s expected map gains
both bands with the count re-pinned (1321 → 1329).

**Confidence and uncertainties:** the metadata law is pinned from
the reference's own read/write pair; the remaining uncertainty is
whether the engine's anvil canvas consults this table per-nibble
(the import path's own call site — the behavioral pass's seat).

**Regression risks:** none foreseen beyond the table's own integrity
cross-check; the rows ADD coverage (air only narrows it).

---

## The twenty-ninth pass — VT-010's third pass landed (CONFIRMED, the correction opened as PR #111), VT-011 discovered AND confirmed whole (the correction opened as PR #115) — two corrections in one pass

The baseline advance: `origin/develop` `a23011fa` (the FL4-F10
redstone world-binding wave — the house stream's act; this pass lane
merges it whole, no overlap with this stream's bed lanes). The pass's
work, in the queue's order:

1. **VT-010's pass 3 — the wire/legacy suffix-consistency walk**
   (the pass-28 record's named gate before the fix became eligible):
   the total census of every bed-id writer, with the reference's
   metadata law read fresh (`getMetadataFromState` lines 220-231,
   `getStateFromMetadata` lines 200-205, `Direction.getIdHorizontal`
   enum lines 14-17). The verdict: the engine's eight bed ids ARE the
   reference's metadata space — bijective and consistent across
   `BuiltinBlocks` (the registered metas), `AnvilBlockIds` (the
   anvil 26-rows), `LegacyBlockIds.metadataOf` (the wire-out table),
   and `/setblock`'s own meta validation — so no writer can stamp a
   suffix that disagrees with the pair geometry, and the
   "unrecognized id" fallback the pass-2 draft reserved is
   unreachable. **The class-membership sub-divergence pinned** (the
   reference's redirect accepts ANY bed half at the facing-named cell
   — `getBlock() != this`; the engine's scans search the opposite
   part specifically, so a foreign foot at the redirect cell aborts
   the whole use where the reference proceeds) — the parity pin's
   fourth seat carries it. **VT-010 CONFIRMED — three passes
   complete**; the Phase E correction follows (the facing-exact
   helper for the four walks, the fix branch, the PR).
   **The correction is now OPEN as PR #111**
   (`fix/bed-pair-facing-resolution-parity-audit`, commit 8b1eefb0:
   the shared `bedFacingBand`/`bedFacingPartner`/`bedFacingBackward`
   helpers over the reference's own laws, the four walks rewritten,
   `BedPairFacingResolutionParityTest` — 4/0 GREEN on the fix, 4/4
   RED on the pre-fix tree verified THIS RUN by the stash
   discrimination: the three discovery seats' exact wrong values plus
   the corner seat's `Condition not met within 40 engine ticks`
   — the silent no-blast abort; the regression lanes 104/0 whole:
   the bed family + the use dispatch + the placement/dig/interaction
   + the door family 48/0, the listener lanes — FurnaceHopperEvent,
   NetherBlockUpdate, the chest family, the hopper family,
   EnderChest, NetherRedstoneRouting — 52/0).
2. **VT-011 confirmed whole in the same run** (the three passes
   completed back-to-back — the source law, the behavioral seats
   through the real import wire, the lifecycle walk + the
   sixteen-nibble census + the compensation hunt): the anvil import
   drops the reference's OCCUPIED bed states — metas 12-15 import
   as AIR (a dangling foot where the reference loads the pair whole;
   the import's own ledger counts `id 26 meta 12 x1`), the
   editor-corrupted feet (4-7) the same. The correction is now OPEN
   as **PR #115** (`fix/bed-occupied-anvil-import-parity-audit`,
   commit 512b0c09: the eight rows, the javadoc band, the integrity
   test's expected bands + the count re-pin 1321 → 1329; the parity
   pin 2/0 GREEN, the anvil family 47/0, the drift guard 2/0, the
   protocol block-id lanes 15/0; the full protocol suite cannot run
   in this environment — SIGKILL 137 at the memory ceiling, verified
   identical on the clean develop tree — the affected surfaces
   covered lane-by-lane instead).
3. **The items family's pass 3** (the crossing arms' head): the
   independent walk over the current tree + the fresh reference read
   + the negative compensation and design-choice challenges —
   CONFIRMED; the fix (the item arm through the `DimensionCrossing`
   seam, a real architectural slice) is deliberately deferred to the
   next run rather than risked half-done at the environment's edge.
   The orbs/boats/carts/arrows walks follow it.

## The thirty-first pass — the #109/#113 coverage VERIFIED through the independent seats (the honest-convergence protocol), VT-013 discovered (the stuck arrow's crossing: the stick placement + the box-scan collision against the position-cell consult)

The baseline advanced to `d26a1ebd` (PR #105 the TNT-cart slice and
#107 the mob cooldown's decay lane MERGED, the FL4-GAP2 Slice A chest
wave, the two build-blocker flakes retired). This lane — renamed twice
under the concurrent streams (the twenty-ninth and thirtieth names
were both taken mid-run; the pass-29 lane's records are merged whole
above) — took the handoff's named head: **the #109/#113 coverage
verification through this stream's independent seat constructions**
plus the stuck-arrow edge as the discriminator.

**THE VERIFICATION (the VT-008 protocol — independent constructions,
not the re-fix).** Two new seats, both RED on develop `d26a1ebd` at
exactly the crossing asserts (the preconditions green) and both GREEN
on the #113 union tree `0e3151fe` (= #109 + the arrows):

1. **THE ARC FLIGHT-PATH SEAT** (`PortalArrowArcCrossingDiscoveryTest`)
   — the handoff's owed construction: an arrow launched FOUR blocks
   out, pitch 10 down, speed 1.4 — the flight crossing the plane
   MID-FLIGHT on the third tick while the arrow still descends at
   ~0.2 blocks/tick (the vertical motion the facing quarter-turn
   rotation never touches, carried whole through the reference's NBT
   copy — `PortalForcer.findNetherPortal` lines 149-152 rotate X/Z
   alone). The seat discriminates beyond #113's own face shot: the
   crossing fires with non-zero vertical motion and the arrival's
   continued flight lands inside the pre-built destination frame's
   band before the catcher set (the lid + floor + walls, this seat's
   own wider form) ends it. GREEN on `0e3151fe` — the mid-flight law,
   the arrival band, the continued flight, and the nether stamp all
   hold.
2. **THE FAMILY-WIDE SEAT** (`PortalThrownCrossingDiscoveryTest`) —
   the far-side snowball: thrown from BELOW the plane (the -Z face,
   the mirror approach) at the interior's second column, the
   `ThrownEntity` super-chain arm (the reference's own scope: the
   snowball/egg/potion/exp-bottle family — `ThrownEntity.tick` line
   123's `super.tick()`). GREEN on `0e3151fe` — the arm covers a
   NON-ARROW kind: the family-wide claim of the #113 census (every
   `Kind` body walks the same per-body arm) verified through the
   second family.

The regression set on the union tree: the five family seats whole —
the item 1/0, the orb 1/0, the vehicle 2/0 (the seats as they ride on
#109's tree), the arrow 1/0 (#113's own face shot), and the mob 3/0
(PR #98's regression seats). **#109's and #113's coverage is
VERIFIED.**

**THE STUCK-ARROW EDGE — VT-013 DISCOVERED** (the handoff's named
discriminator, taken to its root; renumbered from the draft VT-012 —
the second parallel lane's own VT-012 is the NetherBlastRouting flake
record, the collision resolved by this renumber). The record:

- **ID and title:** VT-013 — the stuck arrow never crosses: the stick
  placement (the post-step endpoint) and the position-cell consult
  compose against the reference's face+0.05 placement and box-scan
  collision.
- **Subsystem:** the projectile family's block-hit walk + the
  entity-level portal arm (every crossing family shares the consult
  form).
- **Status:** UNDER VERIFICATION — passes 1-2 complete (source +
  behavioral); the independent pass 3 owed.
- **Affected engine files and methods:**
  `ProjectileManager.resolveCollisions` (the solid arm calls
  `landInGround()` without touching the position — the body rests at
  the post-step ENDPOINT, up to a full step inside the block);
  `ProjectileEntity.landInGround` (the flag + velocity zeroing alone);
  the per-family `tickPortalArm` consults (`portalFrameAt(feet/pos
  cell)` — the position cell ALONE).
- **Vanilla reference:** `ArrowEntity.tick` lines 288-309 — the
  block-hit arm sets the velocity to the segment TO THE FACE, backs
  the position off 0.05 along the approach, then advances by the full
  segment: the final position is 0.05 OUTSIDE the hit face, in the
  cell the arrow was flying through (a floor hit inside a portal frame
  rests the arrow INSIDE the portal cell above the floor);
  `Entity.checkBlockCollisions` lines 667-688 — a full BOX scan:
  every cell the body's shape overlaps receives `onEntityCollision`;
  `PortalBlock.onEntityCollision` arms `inPortal` for the stuck arrow
  (a projectile never rides); `ArrowEntity.tick` line 150's
  `super.tick()` runs baseTick's portal arm BEFORE the inGround branch
  — the threshold-0 crossing fires for a STUCK arrow on the first
  armed tick. **A portal that LIGHTS UP around an already-stuck arrow
  crosses it on the very next tick.**
- **Observed (the seat, `PortalStuckArrowCrossingDiscoveryTest`):**
  the empty-frame construction (the interior AIR) — the arrow shot
  steeply down into the interior sticks in the floor; the observed
  stuck position `y=19.947` against the floor's face at `y=20`, the
  cell `(41, 19, 40)` = the OBSIDIAN floor row; the portal then lit
  around it (the flint-and-steel result) — the crossing NEVER fires:
  RED on develop `d26a1ebd` (no arm at all) AND RED on the #113 union
  tree `0e3151fe` (the arm's position-cell consult reads the obsidian
  cell forever — `portalFrameAt` answers null for a non-portal cell).
  The vanilla answer: the position rests at `y=20.05` inside the
  interior cell `(41, 20, 40)`, the box scan overlaps the new portal
  cell, and the crossing fires within a tick of the lighting.
- **The exact mismatch (two stacked rows):** (a) the PLACEMENT — the
  engine leaves the position at the integrate endpoint (inside the
  solid cell, up to a full step deep) while the reference backs off to
  the face+0.05 (the cell the arrow flew through); (b) the CONSULT —
  the engine's per-family arms read the position cell alone while the
  reference scans every cell the body's shape overlaps. The flying
  families compose these safely (a flying body's position cell enters
  the portal plane before its box leaves it); the STUCK body composes
  them into a permanent miss.
- **The second observable of row (a):** the stick placement itself
  diverges for EVERY stuck arrow — the reference's arrow rests 0.05
  outside the hit face (the rendered shaft stands out of the block,
  the pickup/shake sampling sits at the face); the engine's rests
  inside the block's cell (up to a full step deep for a fast shot).
  The thin-wall shape follows from the same row: a step whose MIDPOINT
  sample lands inside a one-block wall sticks the arrow at the
  ENDPOINT — in the AIR past the wall (the reference's ray hits the
  near face and rests there). Not seated this pass; the record names
  it as the same root.
- **Proposed minimal correction (the fix shape, for the pass-3 run):**
  the placement repair at the block-hit arm — back the position off
  to the hit sample's face (the midpoint/endpoint solid consult gains
  the face-landing form: the position lands 0.05 outside the crossed
  face along the step's approach) — composes with the existing
  position-cell consult to restore the vanilla observable for the
  stuck arrow (the position cell becomes the interior cell). The
  box-scan consult (the reference's own form) is the WIDER law — it
  would also cover edge-overlap bodies (a large mob straddling a
  boundary) — but it is a five-family architectural change; the
  placement repair alone restores the named observable. The pass-3
  run owns the choice.
- **Regression risks:** the stick placement is consumed by the pickup
  walk (the grown-box overlap), the shake/particle sampling, the
  persistence rows (the anvil import's `restoreGrounded`), and the
  stuck-arrow seats' geometry — the re-pins ride the fix's review.

**THE SECOND PARALLEL LANE (the union deferred):** the pass-30 lane
(the #110 carrier — the shared-holder `EntityPortalClock` variant)
carries its ENGINE IMPLEMENTATION inside the audit lane (the
`EntityPortalClock.java` + the five managers' arms over `a23011fa`);
merging it whole onto this lane (develop `d26a1ebd` + seats) would
layer a half-merged engine implementation beside the FL4-GAP2 chest
wave's engine changes — the unsafe union this protocol exists to
avoid. The lane's own close-out says "the union with the pass-29 lane
and the develop advance is the NEXT RUN'S HEAD" — the deferral is
mutual and recorded. THE VT NUMBERING: that lane's VT-012 is the
NetherBlastRouting flake record; this lane's stuck-arrow finding is
VT-013 (renumbered this pass, the collision resolved).

**The queue after this pass:** (1) VT-013's pass 3 (the independent
re-verification: the execution-path walk over the pickup/persistence
consumers, the thin-wall shape's own seat, the fix-shape choice
between the placement repair and the box-scan consult) then the fix
branch if it holds; (2) the pass-30 lane's union (the docs-only
union, the engine files staying on the #110 branch); (3) the standing
lanes (F-SP1/F-DS1) and the dimension tails.

**THE PASS-3 CHALLENGE — VT-013's ORIGINAL HYPOTHESIS REJECTED (the
same run's closing act, the protocol's own demand).** The independent
re-verification walked the reference's arrow tick chain END-TO-END and
found the error in this pass's own pass-1 reading: the box scan
(`checkBlockCollisions`, Entity lines 667-688) is called from
`ArrowEntity.tick` line 381 — **INSIDE THE FLIGHT BRANCH ALONE**. The
`inGround` branch (lines 172-186) runs only the stick clock and the
tracked-block check; a STUCK arrow never re-arms `inPortal`. (The
move()-based families — the mobs, the items, the vehicles — DO scan
every tick: their tick's `Entity.move()` tail is the same scan. The
arrow's custom tick advances its position directly and scans only
while flying.) The original seat's asserted vanilla answer — "a portal
that lights up around an already-stuck arrow crosses it on the very
next tick" — **does not exist in the reference**. The true reference
law for an arrow stuck in an ALREADY-LIT portal's floor: the stick
tick is itself a flight-branch tick (the branch is chosen by inGround
at the tick's START), so the stick tick's line-381 scan runs over the
resting face+0.05 position (inside the portal cell) and arms the
crossing that fires on the NEXT tick — the arrow shot into a lit
portal crosses one tick after sticking, mid-flight-equivalent. The
lights-LATER construction arms nothing: no crossing, in the reference
or in the engine. THE SEAT CORRECTED:
`PortalStuckArrowCrossingDiscoveryTest` now pins the honest law —
`theStuckArrowStaysWhenThePortalLightsAroundIt` asserts NO crossing
and the stuck state — **GREEN on develop `d26a1ebd` AND on the #113
union tree `0e3151fe`** (the engine's answer matches the reference
here for its own reasons: no arm on develop; the arm's position-cell
consult reading the obsidian floor cell on #113). The rejection is
the record — the engine needs NO fix for this construction.

**VT-013's honest residue (re-recorded after the rejection — each row
its own finding, pass 1 complete, the seats owed):**

- **VT-014 (suspected) — the stick PLACEMENT diverges:** the reference
  rests a stuck arrow at the hit face **+0.05** (ArrowEntity lines
  288-309: the velocity-to-face, the 0.05 backoff, the advance — the
  final position 0.05 OUTSIDE the face, in the cell the arrow flew
  through); the engine rests it at the post-step **ENDPOINT**
  (`resolveCollisions`'s solid arm calls `landInGround()` without
  touching the position — up to a full step INSIDE the block; this
  pass's observed sample: stuck at `y=19.947` against the floor's face
  at `y=20`, the cell the obsidian floor row). Observables: the
  rendered shaft's position, the pickup box's sampling, the
  still-in-block containment check, the thin-wall air-stick (a step
  whose midpoint sample lands inside a one-block wall sticks the arrow
  at the endpoint — in the AIR past the wall; the reference's ray hits
  the near face and rests there). SEAT OWED: the plain stuck-position
  seat (a known face height, the position's side of the face pinned)
  + the thin-wall seat.
- **VT-015 (suspected) — the tracked-block UN-STICK law is missing:**
  the reference's `inGround` branch (lines 173-186) checks the TRACKED
  cell every tick — when the block there changes (a piston moves it, a
  blast clears it, the arrow's own TNT-ignite arm opens the cell), the
  arrow UN-STICKS: `inGround = false`, the velocity re-arms with the
  ~0.2-randomized components, the clocks reset — the arrow falls out
  and flies again. The engine's `inGround` branch (the manager loop's
  stick-clock arm) carries NO block-change consult — the engine's
  stuck arrow stays stuck until the 1200-tick expiry regardless of
  what happens to the block it rests in. Observable: break the block
  an arrow is stuck in — the reference's arrow pops out and falls; the
  engine's hangs in the air. SEAT OWED: the block-break/blast
  un-stick seat (a stuck arrow, the tracked block removed, the
  fall asserted).
- **The grazing-consult edge (named, folded into VT-014's record):**
  the reference arms `inPortal` on BOX overlap (a body whose shape
  grazes a portal cell while its center stays outside); the engine's
  five-family arms consult the position cell alone. A body grazing a
  portal cell's boundary without its center entering — vanilla
  crosses it, the engine does not. A narrow edge; its seat folds into
  VT-014's pass-2 run. NOT REACHED this pass (the thirty-third): the
  grazing edge rides the five-family arms that live on the OPEN #109/
  #113 branches; its seat belongs to a union-tree pass (the
  thirty-first's protocol), deferred behind VT-014/VT-015's own
  corrections — the edge is RECORDED, not forgotten.


## The thirty-fifth pass (this lane — first numbered the thirty-second, renumbered twice mid-flight: the fourth stream's lane took the thirty-second's name, the sibling lane the thirty-third and the thirty-fourth; the established concurrent-stream protocol) — VT-014 and VT-015 verified whole (the seats + the independent walks: both CONFIRMED, three passes each, fix-ready), VT-018 discovered (the stick's bowhit sound), the pass-30 lane's docs union landed

The baseline advanced to `6b29c46f` (the vine family's wire rows —
the Nov 1 production crash repair; `539afa62` the release bump beside
it). The open-PR set re-fetched at the run's head: #106, #108, #109,
#110, #111, #113, #114, #115 — all still awaiting the maintainer, no
new merges since the thirty-first pass's fetch. This pass took the
queue's named head: **VT-014's and VT-015's seats** (the honest residue
of VT-013's rejection, each carrying pass 1 from the thirty-first
pass's source walk), completed both findings' verification whole, and
discovered the stick path's third gap beside them.

### VT-014 — PASS 2 (the behavioral seats, both RED on develop `6b29c46f`)

`ArrowStickPlacementDiscoveryTest` (this audit branch, commit below) —
two constructions, both failing at EXACTLY the placement asserts (the
preconditions green: the platforms stood, the arrows launched and
stuck):

1. **THE FLOOR SHOT** (`theStuckArrowRestsOutsideTheHitFace`) — a 5x5
   stone platform at cell row 20 (the face at y=21), an arrow falling
   steeply onto it (pitch 80, speed 0.8, from y=25.5 — the
   thirty-first pass's own construction). THE OBSERVED ENGINE REST:
   `y=20.919` — BELOW the face at 21.0, the post-step ENDPOINT inside
   the platform row (cell y=20, the platform's own row). The
   reference's answer: `21 + 0.05·sin(80°) ≈ 21.049` — above the face,
   the AIR row (cell 21), the cell the arrow flew through.
2. **THE THIN WALL** (`theThinWallSticksAtTheNearFaceNotPastIt`) — a
   one-block wall at cell z=40 (3 wide, 3 tall), an arrow flying
   straight at it (yaw 0, pitch 0, speed 2.0) from z=39.5: the first
   step's midpoint lands inside the wall (≈ 40.49) and the endpoint
   past it (≈ 41.48). THE OBSERVED ENGINE REST: `z=41.486` — PAST the
   far face at 41, **hanging in the AIR** (cell z=41, one full block
   beyond the wall): the midpoint solidity consult fired
   `landInGround()` while the position already stood at the post-step
   endpoint beyond the wall. The reference's answer: the ray stops at
   the NEAR face (z=40) and rests at `z ≈ 39.95`, the approach cell
   (z=39).

### VT-014 — PASS 3 (the independent walk: the intent hunt NEGATIVE, the consumers walk, the hypothesis challenged)

1. **THE INTENT HUNT (negative):** neither `Torch-Arcetecture.txt`,
   `WORKLOG.md`, nor the parity matrix ledgers the endpoint placement
   as a design choice — the sampling's own comment
   (ProjectileManager.tick, the "midpoint halves the worst-case
   tunneling window" note) documents an ANTI-TUNNELING approximation,
   never a placement decision; the architecture file's arrow mentions
   are all dimension/persistence/pickup rows. The placement divergence
   is unacknowledged, not intentional.
2. **THE CONSUMERS WALK:** the wire publishes NOTHING on the stick —
   `V18ProtocolServer.onProjectileLanded` is an explicit no-op ("the
   clients hold the last position until the destroy arrives"), and the
   stick tick's `continue` skips the moved publication, so the
   CLIENT-visible rest is the LAST FLIGHT POSITION (one full step
   before the block) while the ENGINE holds the endpoint inside/past
   the block — neither layer is the reference's face+0.05. The pickup
   pass samples the grown box around the endpoint (a player standing
   on the face still collects — the 1.0/0.5 growth covers the buried
   body); the anvil import restores saved positions whole; the #113
   tree's crossing consult reads the endpoint's cell (the OBSIDIAN row
   for a floor stick — the thirty-first pass's own record). No
   consumer compensates; every consumer inherits the displacement.
3. **THE HYPOTHESIS CHALLENGED:** could the endpoint form be correct
   under some reading? The 1.8.8 reference is unambiguous — the
   velocity-to-face (lines 296-298), the 0.05 backoff (300-302), the
   closing advance (327-329) net to `facePos − 0.05·unit(flight)`;
   the seats' margins (0.081 below the face; 1.486 past the wall's
   near face) are orders past any sampling or noise reading. The
   engine's own solidity model agrees the thin-wall rest stands in
   AIR — the arrow is "stuck in the ground" while its own cell is
   sky. CONFIRMED.

### VT-015 — PASS 2 (the behavioral seat, RED on develop `6b29c46f`)

`ArrowUnstickDiscoveryTest.theStuckArrowUnsticksWhenItsBlockBreaks` —
a 3x3 one-block pedestal at cell row 25 (the flat terrain ends at
y=4; the fall below the face is observable for dozens of ticks), an
arrow stuck in it (the same steep construction), then the WHOLE
pedestal row broken to air (whichever cell holds the tracked block
under either placement model, it is gone). THE OBSERVED ENGINE ANSWER
after ten ticks: the arrow is **STILL STUCK at the identical position**
(`y=25.978`, its cell now air — the body hangs in mid-air where the
pedestal was) — `inGround` never flips, the body never moves. The
reference's answer: the tracked-cell consult (ArrowEntity lines
172-186) fires on the first stuck tick after the change — `inGround`
false, the velocity re-armed `× (random·0.2)` per axis, the clocks
reset — and the arrow falls out from the next tick.

### VT-015 — PASS 3 (the independent walk: the no-false-writer census, the acknowledgment javadocs, the compensating hunt NEGATIVE)

1. **THE CENSUS (the execution path whole):** `ProjectileEntity
   .inGround` has NO false writer anywhere in the engine — the field's
   only assignments are `landInGround()` (line 255) and
   `restoreGrounded()` (line 271), both `= true`; the manager's stuck
   arm runs `integrate` (the stick clock) and the 1200-tick expiry
   ALONE. The engine cannot un-stick an arrow by any path.
2. **THE ACKNOWLEDGMENT JAVODOCS (the design-choice challenge,
   negative):** the engine's own `pushProjectile` javadoc CITES the
   reference's revalidation ("the arrow's own revalidation
   (ProjectileEntity.tick lines 84-92) reads the STICK CELL, which the
   push never changes, so the displaced arrow keeps its stick") — the
   authors read the tracked-cell law and implemented only the
   unchanged-cell case; `flingVelocity`'s javadoc names the damping
   they never wired ("the reference's own damped 0.2 factor applies to
   them only if the stick cell's block ever changes" — it never can,
   no consult exists). A known, unimplemented gap — not an intentional
   divergence claim.
3. **THE COMPENSATING HUNT (negative):** the piston push keeps the
   stick (correctly, per the reference — but it never consults the
   tracked cell); the slime fling and the blast scatter write dead
   velocity onto the grounded body; the block-broken hooks
   (HopperSystem, BrewingStandSystem, BeaconSystem, DaylightDetector
   System, FurnaceManager) touch tile families alone, never
   projectiles. No path compensates.
4. **THE UN-STICK'S OWN CLOCK SEMANTICS (the fix's fidelity row):**
   the reference's un-stick resets `inAirTicks = 0` — and `inAirTicks`
   IS the arrow's shooter-immunity clock (line 204: `entity1 !=
   this.shooter || this.inAirTicks >= 5`), so the reference RE-ARMS
   the shooter window on un-stick; the engine's `age` maps exactly
   (`THROWER_IMMUNITY_TICKS = 5`, the same five) — the reset is
   faithful, not a regression. CONFIRMED.

### VT-018 DISCOVERED (pass 1 complete, the source walk): the stick's `random.bowhit` sound is absent

The reference plays `random.bowhit` at the stick (ArrowEntity line
303, `1.0F, 1.2F / (rand·0.2 + 0.9)`); the engine's stick arm fires
the `onProjectileLanded` fan alone and NO listener plays any sound —
`random.bowhit` appears in the engine at the tripwire alone
(TripwireSystem line 313). The seats owed (pass 2): a stick with a
listener-observed FX assert. Recorded as its own finding; NOT folded
into VT-014's correction (its own verification passes owed).

### THE FIX SHAPES (Phase E, the next runs' heads)

- **VT-014:** the stick arm gains the reference's ray-stick placement
  — a small cell-boundary walk (the reference's `rayTrace` in
  miniature) from the step's start to the sampled point, stopping at
  the FIRST face crossed into a solid cell; the rest lands
  `facePos − 0.05·unit(step)`, the tracked cell + block recorded
  beside it (the reference's lines 289-293), and the stick-time
  velocity KEEPS the face vector (`facePos − from`, the reference's
  lines 296-298 — the dead state the un-stick later damps). The wire's
  `onProjectileLanded` publishes the rest position (the teleport form
  the moved publication already uses) — the client-observable layer of
  the same root.
- **VT-015:** the stuck branch gains the tracked-cell consult — the
  block at the recorded cell read each stuck tick against the recorded
  block; a change un-sticks (`inGround = false`, the velocity
  `× (nextFloat()·0.2)` per axis, `ticksInGround = 0`, `age = 0`), and
  the un-stick tick runs no physics (the reference's branch order).
  The consult needs one SolidQuery method (the block type at a cell —
  the shape-aware ground already answers it). The IMPORT tail named:
  the anvil import derives the tracked rows away (its own ledger's
  "xTile/yTile/zTile/inTile/inData" row), so a restored arrow carries
  no tracked cell — the consult skips it (the residual documented,
  the reference's own readCustomNbt restores the rows whole).
- **THE STACKING:** VT-015's re-arm fidelity (the face vector × the
  random factor) is only restorable on top of VT-014's kept-velocity
  stick — the two corrections land as two PRs, the second stacked on
  the first (the #113-on-#109 precedent), each with its own parity
  pins.


### THE CONVERGENCE (three lanes on the same queue head — the record union)

The pass-31 queue's named head (VT-014/VT-015's seats) was taken by
THREE lanes in the same window: this lane (the pass-2 seats below +
the pass-3 walks — the completion), the sibling lane (their
thirty-third pass: the pass-2 seats at the same predicted geometries
— their floor shot's observed rest `y=19.841`, their thin wall's
`x=51.480`, their un-stick seat's identical-rest observation), and
their own continuation (the thirty-fourth: the SECOND-CONSTRUCTION
seats through `spawnRestored`'s exact flights — the 0.001-tolerance
face±0.05 pins, the observed rests `y=19.4079402396` and `x=61.97`).
THE FILE RESOLUTION (the rename protocol): the sibling lanes own the
canonical names — `ArrowStickPlacementDiscoveryTest` +
`ArrowUnstickDiscoveryTest` (their constructions) and
`ArrowStickPlacementVerificationTest` + `ArrowUnstickVerificationTest`
(the exact-flight pins) stand as the surviving files; THIS lane's
constructions (the pitch-80 floor shot, the z-axis thin wall, the
pedestal break — the observed numbers in the records below) ran in
this lane's commit and stand as the third independent construction.
The evidence is TRIPLY backed: three constructions, one law, all RED
on develop `6b29c46f` at exactly the predicted geometries. THIS lane's
pass-3 walks complete both findings — the only lane carrying pass 3 —
so Phase E (the corrections) proceeds from this lane, the other lanes'
records standing as the independent seat evidence.

### THE RENUMBER (VT-016 -> VT-018, the concurrent-stream collision)

This lane's bowhit finding was first numbered VT-016 in this pass's
working record — but the fourth concurrent stream's pass-32 lane
(`audit/verifier-pass-32-discovery`, pushed before this lane's push)
claimed VT-016 (the piston push table's DESTROY/BLOCK membership) and
VT-017 (the grass-decay shade consult) for its own orphaned findings
AFTER reading this stream's pass-31 records (VT-013/014/015 taken).
Their claims are public; this lane's finding renumbers to **VT-018**
(the pass-31 precedent: the lower-pushed claim stands, the collision
recorded). This lane also renamed twice mid-flight (pass-32 -> pass-33 ->
pass-35): the fourth stream's lane took the thirty-second's name,
the sibling lane (this finding family's other worker) the
thirty-third and the thirty-fourth — the established concurrent-
stream protocol.


## The second parallel lane's implementation record — the docs-only union the thirty-first pass deferred (their twenty-eighth pass: the crossing families' arms landed over the pass-27 lane's fix plan — the #110 carrier)

**THE UNION (the thirty-second pass, this run):** the thirty-first
pass deferred this lane's records because their audit branch carries
the #110 ENGINE IMPLEMENTATION (the `EntityPortalClock.java` + the five
managers' arms over `a23011fa`) — merging whole onto develop `d26a1ebd`
would layer a half-merged engine beside the FL4-GAP2 chest wave. This
run took the DOCS ALONE (their engine files stay on the #110 branch,
the maintainer's pick among the convergent carriers #108/#109/#110
unchanged); their record lands below WHOLE, provenance-marked. The
open-PR landscape this union rides on: #106/#108/#109/#110/#111/#113/
#114/#115 all still OPEN (fetched this run); #109/#113's coverage was
verified by the thirty-first pass's independent seats.

**THE CONCURRENT-STREAM CONVERGENCE**: the pass-27 lane (pushed
mid-flight of this run — the two streams worked the same queue head in
parallel, its merge at 11:40 vs this lane's at 11:52) closed VT-008's
verification with its own five-seat set AND recorded the crossing
families' fix plan as "the next run's head". This lane (the
twenty-eighth by the pushed numbering) took the plan's head and
IMPLEMENTED it in the same window — the plan's seven work items each
resolved below, with the deltas the implementation discovered. VT-008's
own verification arc (this lane's independent set — the End arm and the
head-half click seats) stands recorded beside the pass-27 lane's
five-seat set in VT-008's record above; both green on the merged
develop.

### VT-008 — RESOLVED (verified coverage; the full union record in VT-008's section above)

- **Pass 2 (behavioral, this pass):** the landed
  `BedWrongWorldBlastAcceptanceTest` re-run on the current develop —
  **3/0 GREEN** (the nether foot-click's pair removal + power-5 fire
  blast in the nether, the overworld sleep guard, the orphan-foot
  guard). The pass-27 lane's independent five-seat set (its own pass 2
  and 3) and this lane's two-seat set BOTH stand — the concurrent
  verification of the same merged fix.
- **Pass 3 (independent, this pass):** the two rows the landed seat
  does not cover, authored as
  `BedWrongWorldEndArmVerificationTest` (**2/0 GREEN**):
  1. **THE END ARM** — `TheEndDimension.hasSpawnPoint()` answers false
     (line 54, the override; the base `Dimension` answers true at line
     130 and the overworld inherits it), so an End bed takes the else
     arm exactly like a nether bed. The End's biome is `the_end` (id 9,
     the FixedBiomeSource constant), NOT `hell` — so the engine's
     dimension conjunct (`session.dimension() == 0`) is the SOLE row
     routing the End bed to the blast arm: a `!= -1`-shaped gate or a
     biome-only gate would SLEEP the End bed. The seat pins the gate's
     exact form: the pair leaves whole, the blast carries dimension 1,
     FIRE lands in the End crater, the overworld's same-coordinate
     marker stands, no sleep walk.
  2. **THE HEAD-HALF CLICK** — the normalization's other path (use
     lines 37-43: a clicked HEAD passes straight through with no
     redirect); the same wrong-world blast arm fires (pair removal,
     power 5, dimension -1).
  Also verified at the source level: `sleepOnTick`'s own
  `bedHeadCell(clicked)` normalization means the dispatch's
  `sleepOnTick(session, clicked)` passing the RAW clicked cell is
  correct (the pre-existing internal normalize), and the neighbor-scan
  pair walk (`bedHeadCellIn`) vs the reference's FACING-pointed sibling
  is equivalent for every well-formed pair (a malformed pair — two feet
  adjacent to one head — is unreachable through the placement surface;
  noted as a known, unreachable shape difference, not a defect).

### VT-006 — the remaining crossing families LANDED (this branch; the fix branch `fix/portal-remaining-families-parity-audit`)

The four families' arms ride the seam exactly as the record's own
proposed-correction scope planned ("the families landed one by one
behind it: the MOBS first … then the items, the orbs, the boats, the
carts … and the arrows"). The behavioral discrimination re-established
on the CURRENT develop first (post-#98): all four authored seats RED
(4/4: the item, the orb, the boat, the cart — the mobs' fix did not
cover them, the divergence stands), then the arrow seat authored with
the determinism care the record named (the `launchVector` variation-0
exact trajectory), RED unfixed / GREEN fixed via the stash
discrimination. All five family seats GREEN on the fix branch (items
1/0, orbs 1/0, vehicles 2/0, arrows 1/0, the mobs' own 3/0 re-run).

The engine shape (the minimal correction):

- **`EntityPortalClock`** (new, `server/world`): the shared per-body
  portal clock + entry memory — the base threshold-0 crossing, the 300
  cooldown with the live-cooldown re-arm, the out-of-cells decay, and
  the frame-offset memory derive (the same math `MobEntity`'s and
  `PlayerSession`'s own walks carry; composed into the four new
  families rather than refactoring the merged mobs' code).
- **The per-family arm** at the head of each manager's per-body walk:
  the gated collision consult through the family's own query interface
  (`ItemEntity.Ground` / `ExperienceOrbEntity.Ground` /
  `VehicleEntity.WorldQuery` / `ProjectileManager.SolidQuery` — each
  gains a `portalFrameAt` default, mapped live by the `ShapeGround` and
  the `VehicleWorldQuery` over the ACTIVE dimension), the entry
  memory's note, and the clock's advance; the crossing consumes the
  body's remaining tick (the reference's recreated body never runs the
  source tick's remainder).
- **The crossing walk** per family: the destroy fan BEFORE the stamp
  swap (the wire's per-dimension gate reaches the source world's
  observers — `onItemRemoved(…, "portal")` /
  `onOrbRemoved(…, "portal")` / `onProjectileRemoved(…, "portal")` /
  the vehicles' NEW `onVehicleCrossedDimension` event, distinct from
  `onVehicleBroken`'s documented drop contract — plumbed through the
  engine dispatch and the protocol layer's `sendVehicleRemoved`), then
  the seam consult (`crossEntityDimension`, the same service the mobs
  ride), then `applyDimensionArrival` (the stamp swap + the forcer's
  landing + the quarter-turned motion; the vertical motion keeps its
  own row — the reference's rotation matrix touches the horizontal
  pair alone), then the spawn fan to the destination's observers.
- **The riding gate** for the vehicles: a SEATED body's collision never
  arms (the reference's `entity.rider == null` conjunct — the same law
  VT-005's riding gate carries for the players); the cart's own-copy
  arm (MinecartEntity lines 199-232) carries the same `vehicle == null`
  consult the base arm does.
- **The stuck arrow consults too**: the arm runs BEFORE the
  `inGround` branch (the reference's baseTick runs for the grounded
  arrow); this also moves the shared query's dimension flip ahead of
  the stuck branch's consult.

**The pass-27 fix plan's seven items vs the implementation** (the
deltas the implementation discovered):

1. *The portal primitives' decision point* — RESOLVED to the
   extraction: the shared `EntityPortalClock` (this lane's shape),
   composed into the four new families WITHOUT touching `MobEntity`'s
   own merged copy (the plan's "MobEntity's own delegation" option left
   as a maintainer's follow-up; the extraction avoids the plan's
   3 × 120-line duplication while keeping the merged mobs' code
   byte-identical).
2. *The collision consult's plumbing* — the plan named three adapters;
   the implementation carries FOUR consult sites (the items, the orbs,
   the projectiles through the shared `ShapeGround`; the vehicles
   through the `VehicleWorldQuery`) — the projectiles were the plan's
   item 6's "separate shape" and landed the same arm form.
3. *The manager tick arms* — as planned (the arm before the physics
   walk; the vehicles' riding gate consults `hasPassenger()` — the
   plan's `rider == null && vehicle == null` pair with the vehicle
   conjunct vacuously true for the family).
4. *The wire fans* — **THE PLAN'S NAMED FORM FOR THE VEHICLES WAS
   WRONG**: the plan said "the crossing calls
   `removeVehicle(cursor, vehicle, 'portal')`" — but `removeVehicle`
   does `cursor.remove()` (the body LEAVES the manager's list
   entirely) and fires `onVehicleBroken` (whose documented contract
   carries the drop semantics). A crossing must KEEP the body (it
   lives on in the destination) and drop nothing: the implementation
   fires a NEW `onVehicleCrossedDimension` event (no list removal, no
   drop path, plumbed through the dispatch and the protocol layer's
   `sendVehicleRemoved` — the same per-dimension Destroy the plan
   wanted, without the removal).
5. *The persistence* — verified: the engine persists the MOBS, the
   ITEMS, and the VEHICLES (the shutdown save at EngineServer
   30576-30601), each store saving the body's own dimension row and
   the restore stamping it back (`spawnRestored(..., dimension)` /
   the vehicle row's own dimension read); the crossing's
   `applyDimensionArrival` writes the stamp, so a crossed item or
   vehicle saves into the destination world with no store-level move
   owed. The ORBS and the PROJECTILES carry no persistence at all in
   the engine (no store exists — a pre-existing design outside this
   finding's scope; the reference persists every entity in its
   chunk). The `PortalCooldown` field's own round-trip is the named
   tail below.
6. *The arrows* — landed with the plan's owed seat (the
   `launchVector` variation-0 exact trajectory; the seventh-tick
   interior consult), RED unfixed / GREEN fixed via the stash
   discrimination.
7. *The regression lanes* — the plan demanded the item seat's
   precondition flake be root-caused FIRST: this lane did not
   reproduce it (the seat green solo AND under the five-class combined
   load, twice each — the flake was observed on the PRE-fix baseline
   in a combined run; the pre-fix tree's item never crosses, so the
   resting poll's window is longer there). The observation stands in
   the pass-27 record; the post-fix seat's stability is this lane's
   evidence. The block package lane 309/0 and the family seats green
   whole; the main server package lane ran on this tree.

**The correction's PR**: **#110 OPEN**
(`fix/portal-remaining-families-parity-audit`, based on the current
develop 7910a110; the item + orb seats' determinism row rides the
branch — the flake root-caused below). **THE CONCURRENT-STREAM
CONVERGENCE, fourth-fold**: three other lanes opened the manager-loop
families in the same window — **#108** (12:27, the first), **#109**
(12:39), **#112** (12:47) — the items/orbs/vehicles from the same
pass-27 fix plan, the law identical. **#110 alone carries the ARROWS**
(the projectile family's separate shape, the plan's item 6 — the owed
seat, the stuck-arrow consult, the `SolidQuery` plumbing), the
determinism root-cause row, and the vehicles' dedicated crossed-dimension
event (the plan's `removeVehicle("portal")` form would have
cursor-removed the body — a crossing must keep it). The overlap note
posted on #110 names the resolution paths (this PR whole, or an earlier
manager-loop PR + this branch's arrow slice rebased); the maintainer's
call. The audit lanes' numbering converged the same way: this lane
pushed as the thirtieth pass (the twenty-seventh/twenty-eighth/
twenty-ninth names taken mid-flight by the parallel lanes). **The branch's final lanes**
(after the determinism row): the crossing + family lanes whole — the
five family seats, the `Portal*` family, the `Dimension*` family, the
sleep family, the creeper rows, the item/orb physics, the vehicles,
the entity APIs, the projectile users — **80/0 GREEN** (including
`NetherBlastRoutingAcceptanceTest` — the VT-012 flake did not fire);
the block package on the branch directly **309/0**.

**VT-012 DISCOVERED (this pass, the lanes' run — renumbered from the
VT-011 this lane first used: the pushed pass-29 lane claimed VT-011
for the anvil import's occupied-nibble drop first): a pre-existing
flaky test on develop — `NetherBlastRoutingAcceptanceTest`'s
blast-event read race.** The signature: the NPE
`blast.get().dimension()` — the seat's await passes (the discriminator
cell reads air) but the explosion listener's reference is still null.
THE MECHANISM (the detonation walk's own order): the cells tear EARLY
in `detonateAtIn` and the listener fan fires LATE (the damagedCells
loop + the event bus + the copies between) — the test thread's 25ms
poll can observe the torn cell inside that window and read the
reference before the fan lands. BOTH seats carry the race (the creeper
seat failed once in this lane's combined run; the ghast seat failed
once on a forced re-run). **THE CLEAN-DEVELOP DISCRIMINATION** (the
worktree at unmodified 7910a110, `--rerun-tasks` forced): 1 failure in
3 forced runs — THE FLAKE PREDATES THIS BRANCH; it is not the crossing
families' regression. The fix is the seat's own read order (await the
event reference, not the cell alone) — a test-infrastructure pass, not
a vanilla-conformance row; recorded here because the lanes' evidence
must name every non-green row. The main server package lane did NOT
complete this session (the background run died silently under the
worktree builds' contention — the targeted lanes + the block package
309/0 are the recorded evidence; the full lane is the next run's
chore).

Named tails recorded (each its own pass, not owed by this correction):

1. **The End-origin arm** holds at `from == 1` for ALL families exactly
   like the mobs' (the reference's else branch reads the destination's
   forced-spawn consult — `PlayerManager.changeDimension` lines
   473-484's `getForcedSpawnPoint()` — which the engine does not
   carry).
2. **The box-scan nuance**: the reference's `checkBlockCollisions`
   (Entity lines 667-689) scans EVERY cell the body's box touches; the
   engine's consults read the body's bottom/feet cell alone (the
   merged mobs' arm's own shape). For the standard frame geometry the
   two agree (the interior cells sit directly above the bottom row); a
   falling body straddling a cell boundary arms a tick later in the
   engine. A box-scan refinement would be its own family-wide pass
   (the mobs' arm carries the same nuance).
3. **The `PortalCooldown` persistence**: the reference's base
   `Entity.writeNbt` (line 1120) saves the field for EVERY body; the
   engine's item/orb/vehicle/projectile stores do not round-trip it
   (a reloaded body's cooldown resets). The mobs' own NBT row
   round-trips (the pre-existing surface). A persistence pass owes the
   four families' stores.
4. **The vehicle yaw**: the engine's vehicle model carries no yaw row;
   the crossing consult passes 0.0f (the arrival's yaw is unused by
   the wire — the objects' spawn carries no yaw in 1.8.8). The
   reference's boats/carts DO carry a rendered heading; the modeling
   gap predates this arm (the seat does not pin the arrival yaw).

## The twenty-seventh pass (the bed-coverage lane) — VT-008's verification completed (#102's coverage independently confirmed) + the multi-part pair-integrity arms discovered (VT-009)

PR #98 has MERGED (the mobs family's entity-level crossing); the
remaining crossing families remain blocked behind the seam's review.
The pass took the queue's named head: **VT-008's coverage verification
of PR #102** (the other stream's F-EX6 carrier — the honest-convergence
protocol, no re-fix). The verification ran with THIS stream's own seat
construction over the REAL use wire — `BedWrongWorldNightWalkVerificationTest`,
three independently authored seats with new angles over #102's own
trio: the HEAD cell clicked directly (their seat drives the foot
redirect), the overworld clock held INSIDE the night window (the
pre-fix tree's deepest wrong — the sleep walk runs all the way, the
spawn anchor lands), the END dimension driven (the gate's third
answer, their trio never covered it), the spawn-anchor and epicenter
pins (never pinned by their seats). Both wrong-world seats ran RED on
develop (a95e7ca6) at exactly the predicted awaits — the scratch RED
probe documenting develop's actual behavior (the nether bed click at
night SETS A NETHER BED SPAWN, no blast) — and all three GREEN on
#102's head (3e41cf7b). The neighboring lanes re-ran independently:
the bed/sleep family whole green. **VT-008's three passes stand
COMPLETE from this stream; #102's coverage CONFIRMED.**

The pass's independent angle (the reference's synchronous
neighbor-update path — World.removeBlock's flags-3 walk) surfaced the
adjacent discovery **VT-009**: vanilla's BedBlock.neighborChanged
carries the pair-integrity arm (an orphaned half self-removes; the
foot's departure DROPS the bed item) that runs from ANY sibling
removal path — the engine carries NO bed arm anywhere, so a clipped
half leaves the survivor FLOATING where vanilla clears it. The
behavioral seats ran the same run: the two BED seats RED on the
unguarded tree BY CONTRACT (`MultiPartPairIntegrityDiscoveryTest`),
the DOOR seat GREEN — refuting the door half of the first hypothesis
(the compensating path: `RedstoneSystem.doorNeighborChanged`,
DoorBlock lines 157-198, 1:1) and riding on as the regression guard.
VT-009 stands UNDER VERIFICATION: the bed half's source + behavioral
passes done, the independent pass owed.

## VT-009 (the bed-coverage lineage's claim — PR #114; distinct from the pass-28 lane's VT-009 pair-break walks, PR #106, and the develop-merged VT-009 mob portal cooldown, PR #107) — the BED family's neighbor-changed pair-integrity arm is absent: a clipped bed half FLOATS where vanilla removes it (CONFIRMED — three passes; the correction LANDED on `fix/bed-pair-integrity-parity-audit`)

**Subsystem:** block updates / the bed family
**Affected version:** the current develop baseline (a95e7ca6) and #102's
head (3e41cf7b) alike
**Status:** CONFIRMED over three passes (the source census, the
behavioral seat matrix RED on the unguarded tree, the independent
challenge below); the correction LANDED on
`fix/bed-pair-integrity-parity-audit` (**PR #114 OPEN**, based on
develop a95e7ca6): one
family arm in `BlockUpdateSystem.runUpdate` — the class law from
`bedSiblingCleanup` (the HEAD ids carry the `bed_head` prefix, the FOOT
ids are `bed` + the direction forms), the orphan removes itself, the
FOOT arm alone dropping the bed item. The four discovery seats GREEN
(both orphan directions + the drop law + the door regression guard);
the regression lanes green (the block package 309/0, the interaction +
world packages 250/0, the update/sleep family 24/0 combined); one
pre-existing test's CONSTRUCTION repaired in the open (see the fix
record below — its orphan-foot cell was never a vanilla-stable bed).
THE DOOR HALF IS REJECTED: the pass's first door seat PASSED on the
unguarded tree and the source hunt found the compensating path —
`RedstoneSystem.doorNeighborChanged` (DoorBlock.neighborChanged lines
157-198, 1:1: the orphaned upper removes itself silently, the lower's
floor-support failure pops the pair with the item, the signal edge
flips POWERED/OPEN). The original hypothesis's "no door arm" was an
artifact of censusing only `BlockUpdateSystem.runUpdate` — the
neighborChanged surface is SPLIT across the update queue AND the
redstone system's own dispatch; the census method is the lesson.

### The reference's law (BedBlock.neighborChanged lines 109-121 + DoorBlock.neighborChanged lines 45-80)

A bed half whose SIBLING departs removes ITSELF — and a FOOT half's
self-removal DROPS the bed item (`dropItems(world, pos, state, 0)`, line
118). A door's UPPER removes itself when its lower departs; a door's
LOWER removes itself when its upper departs or when the floor below
loses its solid top (both halves leave, the drop fires). These arms run
from ANY sibling removal path — a blast clipping one half, a fluid
wash, a piston, a plugin write — because the removal's neighbor updates
fire SYNCHRONOUSLY (World.removeBlock = setBlockState(flags 3) →
onBlockChanged → updateNeighbors → the six neighbors' neighborChanged).

### The engine's shape

`BlockUpdateSystem.runUpdate` — the scheduled neighbor-update drain —
carries the arms for the torch, the standing/wall signs, the rail
families, the vine, the banners, the carpets, the cocoa, the lily (and
the gravity families through the fall seam) — but NO bed arm; the
redstone system's own neighbor-changed dispatch carries the door, the
plates, the gates, the trapdoors, the rails' signal arm — but NO bed
arm either (the bed is no redstone component). The bed pair's integrity
rides the BREAK walk alone (`bedSiblingCleanup` — one half MINED takes
the sibling with no drop) and the wrong-world blast arm (#102's
`detonateBedOnTick` removes both halves directly). A NON-BREAK removal
of one half (an explosion whose ray fan happens to take exactly one
cell of the pair, a fluid wash, a piston, a plugin write) leaves the
surviving half STANDING in the engine — vanilla clears it (and the
foot's departure drops a bed item). The removal's commit DOES wake the
six neighbors (the update queue's listener fires on every setBlock) —
the wake lands but no arm runs for the bed family.

### The drop-law note (the mining path — NOT a defect)

Vanilla mining EITHER bed half yields exactly ONE bed item (the foot's
own dropItems when the foot is mined; the FOOT's neighborChanged drop
when the head is mined — the head itself never drops). The engine's
break walk yields exactly ONE bed item for either half (the behavior
table's `minecraft:bed` ×1 row on every half id, the sibling cleanup
dropping nothing) — observably equivalent. The orphan edge differs
(mining an orphan head: vanilla nothing — it self-removed — vs the
engine 1 bed), but the orphan head itself is this finding's subject.

### The proposed minimal correction

One family arm in `BlockUpdateSystem.runUpdate`: the bed arm (a half
whose adjacent opposite-class sibling is gone removes itself, the FOOT
arm dropping the bed item per the reference's line 118), using the
pair-resolution convention the engine already carries
(`bedHeadCell`/`bedSiblingCleanup`'s class-adjacency). The door family
needs nothing — its arm lives in the redstone system.

### Verification pass history

- **Pass 1 (source, the twenty-seventh pass, 2026-10-31):** the
  reference's BedBlock.neighborChanged (lines 109-121) and
  DoorBlock.neighborChanged (lines 157-198) read in full; the engine's
  update queue censused (runUpdate's family arms enumerated — no bed,
  no door THERE); the break-walk pair cleanups and #102's blast arm
  read (the only bed pair-integrity sites); the 47-block
  neighborChanged override census bounded. CONCLUSION (as first
  stated): both arms absent — INCORRECT for the door half, see pass 2.
- **Pass 2 (behavioral, the twenty-seventh pass, 2026-10-31):**
  `MultiPartPairIntegrityDiscoveryTest` — the seats drive the trigger
  through the engine's own commit wire (`world.setBlock` on the tick
  thread; the §466 listener wakes the six neighbors exactly as any
  removal path does):
  - THE BED SELF-REMOVAL SEAT: the head removed; the foot must leave —
    RED on the unguarded tree at exactly the predicted await (the foot
    FLOATS). The gap confirmed behaviorally.
  - THE BED ITEM DROP SEAT: the foot's departure must drop the bed
    item (line 118) — RED on the unguarded tree (nothing drops; the
    survivor is mined later for its behavior-row drop instead).
  - THE DOOR SEAT (originally the third discovery seat): the orphaned
    door upper self-removes — PASSED on the unguarded tree, which
    REFUTED the door half of the hypothesis. The follow-up scratch
    probe (never committed) pinned the timeline (the upper gone within
    5 ticks of the lower's removal) and the source hunt found the
    compensating path: `RedstoneSystem.doorNeighborChanged`
    (DoorBlock lines 157-198, 1:1). THE DOOR HALF REJECTED — and the
    seat rides on as the regression guard (the bed correction must not
    touch the door's law).
  CONCLUSION: the BED family's arm is absent (two RED seats, the
  source census, the compensating hunt's negative result); the DOOR
  family's arm exists (one green seat + the named compensating path).
- **Pass 3 (independent, the twenty-seventh pass, 2026-10-31):** the
  hypothesis challenged from every remaining angle:
  (1) THE ORPHAN-DIRECTION SEAT — the reference's HEAD arm (lines
  110-114: the foot departs → the HEAD self-removes with NO drop; the
  FOOT arm alone drops, line 118) got its own seat
  (`theOrphanedBedHeadSelfRemovesWithNoDrop`): RED on the unguarded
  tree at exactly the predicted await (the head FLOATS) — the
  behavioral matrix now covers BOTH directions plus the drop law:
  head-gone → foot leaves + bed item; foot-gone → head leaves, no
  item.
  (2) THE OTHER REMOVAL PATHS (source) — the blast's
  `ExplosionService.commitDamaged` commits every destroyed cell through
  `world.setBlock` (the §466 listener wakes the six neighbors — the
  wake lands, no arm runs for the bed); the fluid wash's cell
  replacement rides the same commit wire; the PISTON path turned out
  to be its own finding (VT-010 below — vanilla breaks pushed beds
  outright, the engine MOVES them, compounding this gap).
  (3) THE COMPENSATING CENSUS completed — the engine's neighborChanged
  surface is SPLIT: `BlockUpdateSystem.runUpdate` (the full arm list
  enumerated — torch, signs, rails, vine, banners, carpets, cocoa,
  lily, flowers, mushrooms, nether wart, double plants, pots, cake,
  fire, portal, cane, cactus, farmland crops, farmland, grass — NO
  bed) and `RedstoneSystem`'s own dispatch (plates, gates, doors,
  trapdoors, the rails' signal arm — NO bed); the TilePlacement/
  PublicBlockChange bridges carry no bed arm; the four seats' negative
  results close the loop. No path cleans the orphan bed half.
  (4) THE INTENTIONALITY CHALLENGE — the engine's own precedent ports
  these arms (the door's 1:1 port in the redstone system; the update
  queue's arm-per-family shape; `bedSiblingCleanup`'s javadoc itself
  citing "the vanilla unit rule" — the author knew the rule and
  implemented the break-path half alone); nothing marks the non-break
  arm's absence intentional.
  (5) THE FIX'S REGRESSION SURFACE bounded — the arm's natural home is
  `runUpdate` beside the other families (the class-adjacency convention
  `bedSiblingCleanup` already carries); the wake coalescing's addressee
  pin preserves the foot's own type for its wake (the LATEST addressee
  wins); the arm must NOT drop for the HEAD direction (the seat's
  no-drop pin); and the `bedSpawnCleanup` interaction is the fix's one
  design note (the anchor invalidation rides the break walk today —
  vanilla validates lazily at respawn; the fix branch should decide
  whether the arm calls it too).
  CONCLUSION: the bed family's pair-integrity arm is absent and
  UNCOMPENSATED engine-wide; the finding is CONFIRMED. The pass also
  surfaced **VT-010** (the piston push table's membership — see below).

### The correction (landed the same run — `fix/bed-pair-integrity-parity-audit`)

THE ARM: one family arm in `BlockUpdateSystem.runUpdate`, placed after
the double-plant family (the pair-integrity locality): a minecraft bed
half (the identifier `bed` or `bed_*`) whose four horizontal neighbors
hold NO opposite-class bed half removes itself — the FOOT arm dropping
the bed item (line 118), the HEAD arm silent (lines 111-114). The class
law is the engine's own (`bedSiblingCleanup`'s — the HEAD ids carry the
`bed_head` prefix; the FOOT ids are `bed`, `bed_west`, `bed_north`,
`bed_east`).

THE FIRST DRAFT'S DEFECT (caught by the seats before any commit): the
draft matched the foot class by `bed_foot*` — the engine's FOOT ids are
`bed` and the direction forms, so the foot-direction seats stayed RED
until the class law was corrected. The four-seat matrix caught it in
one run.

THE CONSTRUCTION REPAIR (in the open — the one pre-existing test the
arm exposed): `PlayerDataPersistenceTest
.bedSpawnSurvivesARestartAndABrokenBedResetsIt` laid a single orphan
FOOT cell as "the bed" and expected it to persist across a restart.
Under the restored law an orphan half pops on the first neighbor
change — exactly vanilla — and the scene's own engine-side trigger
arrived within ticks (the grass under the bed dying to dirt, its commit
waking the foot: the traced evidence
`[2,4,2=dirt]`). The test's INTENT (the anchor survives a restart; a
broken bed resets it) is unchanged and its assertions untouched; the
scene now lays the WHOLE PAIR (the MultiplayerSleepAcceptanceTest
convention) — a vanilla-stable bed. The grass-die trigger itself is
NOT vanilla (see VT-011 below) but the arm's law stands independent of
it: ANY neighbor change pops an orphan half in vanilla.

THE REGRESSION LANES: the four discovery seats 4/0; the persistence
class 5/0 (the repaired construction); Sleep 1/0 + MultiplayerSleep
3/0 + BlockUpdateSystem 6/0 + NetherBlockUpdateRouting 3/0 +
ScheduledUpdatesApi 2/0; the block package whole 309/0; the
interaction + world packages 250/0. The one batch-level
NetherBlastRouting failure reproduced GREEN in isolation on BOTH trees
(the known load-flake class — passing alone, no bed path touched).

THE REBASE INTERACTION (develop moved mid-run — the #101/#102/#104
wave + FL4-F10 landed; the branch rebased onto a23011fa cleanly and
re-verified): #102's ORPHAN-FOOT GUARD seat staged its orphan through
an update-bearing `setBlock` — under the restored law the orphan foot
pops the same tick (exactly vanilla — the flags-3 removal fires the
neighbor updates synchronously in the reference too). The seat's LAW
(the orphan use is consumed with neither arm, BedBlock.use lines
40-43) is unchanged and vanilla-reachable; its staging now rides the
FLAG-4 SILENT write (`setBlockStateSilently` — the reference's
update-free `setBlockState(pos, state, 4)` family, the one path that
leaves an orphan standing to be clicked). Their trio 3/0, the
discovery seats 4/0, the persistence class 5/0, the update/sleep
lanes, and the block + interaction packages 313/0 on the rebased tree.
The facing-exact resolution PR (#111, another stream's lane over the
same pair walks) is COMPLEMENTARY: it makes the walks facing-exact;
this arm's sibling scan rides the engine's current class-adjacency
convention and would follow #111's law in a small follow-up (the
reference's neighborChanged reads the facing-named cell).

## VT-017 — the grass-decay shade consult is the 3-block allowlist, not the reference's opacity law: grass dies under beds, flowers, slabs and every non-solid-render block where vanilla keeps it (SUSPECTED — one source pass + observed evidence)

**Subsystem:** block updates / the grass family / the shade consult
**Affected version:** the current develop baseline (a95e7ca6)
**Status:** SUSPECTED — the source pass complete (the reference's
opacity law + the engine's allowlist read) with OBSERVED behavioral
evidence (the dirt-under-bed commit caught by the VT-009 fix run's
trace); the behavioral seats owed; the independent pass owed.

### The reference's law (GrassBlock.tick lines 49-52)

The grass dies to dirt only when
`world.getRawBrightness(pos.up()) < 4 && world.getBlockState(pos.up())
.getBlock().getOpacity() > 2` — the OPACITY consult: every
non-solid-render block answers opacity 0 (Block's constructor line 216:
`this.opacity = this.isSolidRender() ? 255 : 0` — the bed's
`isSolidRender()` is FALSE, BedBlock lines 99-102; flowers, slabs,
stairs, fences, torches, glass likewise) — the grass SURVIVES under
them all. Only true solid-render blocks (opacity 255) shade the grass
to death (plus the brightness arm).

### The engine's shape

`BlockUpdateSystem.isOpaque` (line 1053): opaque unless the id is air,
torch or glass — a 3-block allowlist standing in for the opacity law.
The grass-decay arm (line 996-999) consults it: the grass under a BED,
a FLOWER, a SAPLING, a CROP, a SLAB, STAIRS, a FENCE... dies to dirt
where vanilla keeps it green. The observed evidence: the VT-009 fix
run's trace caught the grass at (2,4,2) dying to DIRT under the bed
foot within ~4 ticks of the placement (the commit that woke the orphan
half). The scope is every non-solid-render block the allowlist misses.

### The proposed minimal correction

The consult swaps to the engine's own light-opacity table
(`BlockLightTable.filter(type) > 2` — the same table the light engine
already rides, the vanilla `getOpacity` port) with the brightness arm
consulted beside it; the allowlist retires.

### Verification pass history

- **Pass 1 (source, the twenty-seventh pass, 2026-10-31):** the
  reference's GrassBlock.tick read (the two-conjunct death gate); the
  opacity derivation traced (Block's constructor: isSolidRender ? 255 :
  0; BedBlock's isSolidRender false); the engine's isOpaque allowlist
  read; the observed dirt-under-bed caught by the VT-009 trace.
  CONCLUSION: the consult diverges at the source level with observed
  behavior.
- Pass 2 (behavioral): owed — the seats: grass under a bed/flower/slab
  survives (vanilla) vs dies (engine today); the positive control (the
  grass under a stone block still dies).
- Pass 3 (independent): owed.

## VT-016 — the piston push table's DESTROY/BLOCK membership diverges from the reference's material census: beds, doors, weighted plates, and cocoa are PUSHED where vanilla destroys them; anvils are PUSHED where vanilla refuses (SUSPECTED — one source pass)

**Subsystem:** redstone / the piston families / the pushability table
**Affected version:** the current develop baseline (a95e7ca6)
**Status:** SUSPECTED — the source pass complete (the reference's
material/override census + `canMoveBlock` + the toBreak drop law; the
engine's table + gate read); the behavioral seat owed (the
piston-push construction); the independent pass owed.

### The reference's law

`PistonBaseBlock.canMoveBlock` (lines 247-271): obsidian hardcoded
immovable; the world border; `getMiningTime == -1` (the unbreakables);
`getPistonMoveBehavior() == 2` (BLOCK — the ANVIL, PORTAL, PISTON,
BARRIER materials) → immovable; `== 1` (DESTROY) → breakable under
`allowBreaking`. The behavior value comes from the MATERIAL
(`Material.setDestroyOnPistonMove` → 1: WATER, LAVA, LEAVES, PLANT,
REPLACEABLE_PLANT, FIRE, DECORATION, CORAL, SNOW_LAYER, CACTUS,
PUMPKIN, EGG, CAKE) or the BLOCK OVERRIDE (**BedBlock line 175: 1**;
**DoorBlock: 1**; **AbstractPressurePlateBlock: 1** — all plates;
AbstractRailBlock: 0 pushable; IceBlock: 0). The toBreak consumption
(`move`, lines 303-309): **`block.dropItems(world, pos, state, 0)` +
`world.removeBlock`** — the destroyed blocks DROP their items (a pushed
bed: the pushed half's dropItems — the FOOT's bed item alone — then
the survivor's neighborChanged self-removal with ITS drop: exactly ONE
bed item, both halves gone, the piston extends).

### The engine's shape

`PistonSystem.pistonMoveBehavior` — the BLOCK family lists the portal,
the piston head, the moving block (the barrier via the hardness -1
gate); the DESTROY family lists the torch, the redstone components,
the saplings, the grasses, the flowers, the wheat, the cane, the
cactus, the pumpkin, the snow layer, the fire, the liquids, the
leaves, the cake, the egg — **NO bed, NO door family, NO weighted
pressure plates, NO cocoa, NO anvil**: all five answer BEHAVIOR_PUSH.
`canMoveBlock`'s gate is otherwise a faithful port (obsidian, the
bounds, the -1 hardness, the block-entity-backed containers). The
observable for a pushed bed: the half MOVES to the next cell (the pair
splits, the survivor FLOATS — VT-009's own gap compounding), NO drop,
a displaced bed half at the landing cell. Vanilla: the pair gone,
exactly one bed item, the piston extends. The door family's push
observably self-compensates through the redstone pair arm (the moved
lower pops with the door item, the orphaned upper goes silently —
roughly vanilla's destroy-on-push outcome); the bed has no such arm.

### The proposed minimal correction

The table's membership rows: the bed family → DESTROY (the four
`bed_head_*`/`bed_foot_*` ids... the engine's bed ids are
direction-suffixed — `value.startsWith("bed_")`), the door family →
DESTROY (`WoodDoorFamily.isDoor`), the weighted plates → DESTROY, the
cocoa → DESTROY, the anvil → BLOCK. The DESTROY arm already carries
the reference's drop law (the engine's toBreak consumption — to verify
in the fix pass).

### Verification pass history

- **Pass 1 (source, the twenty-seventh pass, 2026-10-31):** the
  reference's Material piston setters read (setDestroyOnPistonMove →
  1, setBlocksPistonMove → 2, the doc's value semantics); the block
  overrides censused (BedBlock 1, DoorBlock 1, all pressure plates 1,
  rails 0, ice 0); `canMoveBlock`'s gate chain read; the toBreak
  consumption read (dropItems + removeBlock — the drops DO land); the
  engine's `pistonMoveBehavior`/`canMoveBlock` read with the
  membership diff enumerated. CONCLUSION: five membership divergences
  at the source level (bed, door, weighted plates, cocoa, anvil), the
  bed's the most observable (it compounds VT-009).
- Pass 2 (behavioral): owed — the piston-push seat (a piston extending
  into a bed pair: vanilla's observable is the pair gone + one bed
  item; the engine's today is the displaced half + the floating
  survivor). The construction needs the piston extension path (the
  redstone wire or the direct system API).
- Pass 3 (independent): owed.


## The thirty-second pass — the bed-coverage lineage's records unioned in (VT-016/VT-017 renumbered), the fourth lane's report whole

**The baseline:** `origin/develop` at `6b29c46f` (2026-11-01) — the same
tip the thirty-first pass closed against (the vine wire-row repair on
top). The open PR set is unchanged: #106, #108, #109, #110, #111,
#113, #114, #115 — all clean.

**This lane's act:** the bed-coverage lineage — the stream whose
VT-009 record rides **PR #114** (the bed family's neighbor-changed
pair-integrity arm) — unions its report copy into the pushed pass-31
lane's (the most current union). The bed-coverage lane's own
twenty-seventh-pass section and the VT-009 record ride in whole; its
two suspected findings, orphaned by the concurrent numbering, renumber
to the next free ids: **VT-016 (the piston push table's DESTROY/BLOCK
membership)** and **VT-017 (the grass-decay shade consult)** — VT-013
(taken: the pass-31 lane's rejected stuck-arrow crossing), VT-014
(taken: the stick placement residue), VT-015 (taken: the un-stick law
residue) all stand in the pushed lane. **This pass's heads:
VT-017's behavioral + independent passes** (the grass-decay seats —
the observed dirt-under-bed evidence came from the VT-009 fix run's
own trace; the reference's opacity law vs the engine's 3-block
allowlist), **then VT-016's** (the piston-push bed seat — the pair
gone + one bed item in vanilla, the displaced half + floating
survivor today).

## The thirty-third pass — VT-014's and VT-015's pass-2 seats (the honest residue's behavioral verification): both findings RED on develop at exactly the predicted geometries

The baseline `6b29c46f` (this lane merged the vine wire-row repair in
before any work; the fourth lane's union arrived mid-run and rides
whole beneath this merge — the rename protocol: their docs-only lane
owns the thirty-second name, this lane the thirty-third). The pass-31
handoff named this run's head: **VT-014's and VT-015's seats** —
VT-013's honest residue after the rejection, both findings carrying
pass 1 (the source walk) from the thirty-first pass's record. This
pass adds pass 2 (the behavioral seats) to both — the fourth lane's
queue item 3 named this exact act for this lane, not a duplication.

**VT-014's pass 2 — the stick placement's two observables, both RED on
develop `6b29c46f`** (`ArrowStickPlacementDiscoveryTest`):

1. **THE STUCK-POSITION SEAT** (`theStuckArrowRestsOutsideTheHitFace`):
   a 5x5 stone platform, top face y=20 (the cells at y=19), an arrow
   shot straight down (pitch 90, speed 0.8) from four and a half
   blocks above. THE PIN: the rest must sit ON THE APPROACH SIDE of
   the hit face (y >= 20.0 — the reference's exact rest is face+0.05
   along the approach; a near-vertical shot answers within
   [20.0, 20.05]). RED at exactly that assert with the observed rest
   `Position[x=42.512, y=19.841, z=42.490]` (cell (42, 19, 42)) — the
   post-step ENDPOINT placement INSIDE the hit block's cell, 0.159
   BELOW the face. The preconditions green (the platform stands, the
   launch lands, the stick fires) — the failure is the placement law
   alone.
2. **THE THIN-WALL AIR-STICK SEAT**
   (`theThinWallShotRestsAtTheNearFaceNotInTheAirBeyond`): a
   one-block-thick wall (the column x=50, five tall, five deep), a
   flat shot (yaw 270 = +x, pitch 0, speed 2.0) whose ~1.96-block
   second step has its MIDPOINT inside the wall's single cell while
   its ENDPOINT clears the far face (x=51). THE PIN: the rest must sit
   on the APPROACH side of the near face (x < 50.0 — the reference's
   exact rest: 49.95). RED at exactly that assert with the observed
   rest `Position[x=51.480, y=22.333, z=47.499]` (cell (51, 22, 47))
   — the arrow HANGING IN THE AIR beyond the wall, half a block past
   its far face. The engine's midpoint consult sticks the body at the
   already-integrated endpoint; the reference's ray hits the near face
   and the 0.05 backoff (ArrowEntity 296-302) composes with the
   replaced-velocity advance (328-330) to rest 0.05 before it.

**VT-015's pass 2 — the block-break un-stick seat, RED on develop
`6b29c46f`** (`ArrowUnstickDiscoveryTest.theBrokenBlockUnderAStuckArrowUnsticksIt`):
the same straight-down construction, the stuck arrow's tracked cell
(the platform's top cell at the arrow's x/z — the reference's
recorded `blockX/blockY/blockZ` and, at the engine's endpoint
placement, the embedded cell; the same cell for this construction)
BROKEN to air, forty ticks observed. THE PIN: the arrow must LEAVE its
rest (the reference's else arm at ArrowEntity 179-186 — `inGround =
false`, the per-axis `random * 0.2` velocity re-arm, the clocks
reset, the fall resumes; the flat world's terrain sits at y<=4, so
ANY movement is the un-stick's signature). RED at exactly that assert
with the observed rest `Position[x=42.508, y=19.886, z=42.528]` —
IDENTICAL to the pre-break rest, the stick clock alone running. The
complementary pin (never inGround AT the broken cell again) fails
with it. The engine's `inGround` branch (ProjectileManager.tick lines
~853-858) carries no block consult anywhere.

**The regression check:** VT-013's corrected seat
(`PortalStuckArrowCrossingDiscoveryTest`) re-run GREEN on this tree —
the no-crossing law the rejection established holds unchanged beside
the two new findings.

**The build environment note:** Maven Central answered Gradle's
dependency resolution with HTTP 429 (the rate limit) while plain curl
succeeded — the pass's runs went through an Aliyun mirror init script
(`.gradle-init/mirror.gradle`, uncommitted: environment scaffolding,
not engine work). The test lanes themselves are the standard Gradle
test task.

**The tally:** two findings at pass 2 of 3 (the seats complete, the
evidence recorded in the failure messages); the pass-3 independent
walks — the execution-path challenge (any other mover or un-stick
path in the engine), the design-choice challenge (any javadoc naming
the placement or the missing consult as deliberate), and the edge
walk (the metadata-change arm, the head-of-tick re-stick at lines
157-166, the NBT persistence surface) — are this lane's next head,
beside the fix-scope mapping (the tracked-cell model the VT-015
correction must add, its persistence rows included).

**VT-014's and VT-015's pass 3 — the independent walks, COMPLETE this
same run (both findings CONFIRMED, three passes each):**

1. **THE EXECUTION-PATH CHALLENGE — NEGATIVE for compensation.** The
   complete census of every writer of a projectile's position or
   `inGround` state engine-wide: the flight step
   (`ProjectileEntity.integrate` line 432, gated by `!inGround`); the
   piston/blast push (`pushProjectile` line 579's `teleport` — the
   javadoc's own law: the push DISPLACES without un-sticking, the
   reference-matching shape, so it cannot compensate a tracked-cell
   change); the anvil import's `restoreGrounded` (load-time, sets
   `inGround = true` alone); `landInGround` (the stick site — sets
   the flag, zeroes the velocity, NEVER touches the position); the
   constructor and the two mob/player `teleport` sites in
   EngineServer (the spread-pile landing and the /summon override —
   not projectile surfaces). **`inGround` is set to true at exactly
   two sites (lines 255, 271) and cleared NOWHERE** — no un-stick
   exists anywhere in the engine, and no mover adjusts a stuck
   arrow's rest.
2. **THE DESIGN-CHOICE CHALLENGE — the gaps are LEDGERED omissions,
   never parity claims.** Three independent citations in the engine's
   own documentation: (a) `pushProjectile`'s javadoc cites the
   reference's revalidation ("the arrow's own revalidation
   (ProjectileEntity.tick lines 84-92) reads the STICK CELL, which
   the push never changes") — the stick-cell concept was known;
   (b) `flingVelocity`'s javadoc names the un-stick law itself:
   "the reference's own damped 0.2 factor applies to them only if
   the stick cell's block ever changes" — the engine "carries the
   set components as dead state", the law acknowledged and left
   unimplemented; (c) the anvil import's ledger COUNTS the
   xTile/yTile/zTile/inTile/inData rows as "projectile rows without
   an engine surface (the point model derives the cell, the block
   and the damage from the motion)" — and the derivation never
   happens (no runtime consult exists). The VT-006 precedent's
   shape: a ledgered planned gap is still a genuine vanilla
   mismatch; none of these javadocs claims parity for the divergent
   behavior.
3. **THE EDGE WALK.** (a) The metadata arm: the reference's ARROW
   override checks `block == this.block && j == this.blockData`
   (ArrowEntity 173-174) — a metadata-only change (a door opened, a
   repeater re-delayed in the tracked cell) also un-sticks; the base
   ProjectileEntity's family-wide copy (lines 84-99) is
   identity-only with a 600 expiry — dead code for the fireballs
   (they explode on hit, never stick), so the engine's arrow-only
   sticking surface matches where sticking happens. (b) The
   head-of-tick re-stick arm (ArrowEntity 157-166): re-arms
   `inGround` when the tracked cell's block is non-air AND its
   collision box contains the arrow's position — a safety net (the
   fresh-arrow field init is `-1, -1, -1`, the void cell, so it
   no-ops for fresh bodies); a narrow tail for the fix scope. (c)
   The NBT surface: the reference persists the tracked cell whole
   (writeCustomNbt 388-394: xTile/yTile/zTile shorts, the inTile
   key, the inData byte; readCustomNbt 402-414 restores them beside
   life/inGround) — the engine restores `inGround` + `life` alone
   (AnvilWorldImport 1150-1157), the tracked-cell rows isolated in
   its ledger. The VT-015 fix scope must add the tracked-cell model
   AND its persistence rows; the un-stick's re-arm reads the SAVED
   velocity (the reference's stick site sets it to the face vector
   before the drag/gravity tail — ArrowEntity 296-298 + 377-380 —
   so the velocity-save belongs to VT-015's scope, not VT-014's).

**THE VERDICTS: VT-014 CONFIRMED — three passes** (pass 1 the
thirty-first pass's source walk; pass 2 this pass's two RED seats;
pass 3 this run's independent walks). **VT-015 CONFIRMED — three
passes** (pass 1 the source walk; pass 2 the RED seat; pass 3 the
independent walks). Both findings are Phase E eligible. The
corrections are separable roots — the placement (a position
computation at the stick site) and the un-stick law (a per-tick
state consult + the tracked-cell model + the persistence rows) —
one PR each. **The fix-scope mapping for VT-014:** the stick site
( ProjectileManager.resolveCollisions' ARROW solid arm, line ~1195)
computes the segment-from-`from`-to-`point`'s entry face into the
solid cell and rests the arrow at face − 0.05·unit (the reference's
composed law); the shape-aware surface (WorldSolidity's partial
shapes — a slab's true face is NOT the cell boundary) and the
from-inside-the-cell degenerate are the scope's two named edges.
**For VT-015:** ProjectileEntity gains the tracked cell + identity
(the block type and its metadata), the stick site records them, the
manager's inGround branch consults them per tick and un-sticks on
any change (the per-axis `random * 0.2` velocity re-arm, both
clocks reset), the save/import walks persist the five rows, and the
stick site's velocity save (the face vector damped) feeds the
re-arm.



## The thirty-fifth pass (this stream's lane, renamed a THIRD time — the fourth concurrent lane's pushed pass-34 branch owns the thirty-fourth name; the QUADRUPLE convergence on VT-014/VT-015 now spans four constructions) — VT-014 and VT-015 confirmed whole through THIS lane's independent seats and walks, VT-019 discovered (the stuck burning arrow's TNT re-prime; TWICE renumbered — this pass's first VT-016 draft lost 16/17 to the pass-32 lane's piston/grass claims, and the VT-018 draft lost 18 to the pass-35 lane's pushed bowhit-sound claim — the VT-012->VT-013 precedent twice over). THE PASS-34 LANE'S SECOND-CONSTRUCTION SEATS (merged in whole this pass: ArrowStickPlacementVerificationTest + ArrowUnstickVerificationTest, 97c82bde — every body through spawnRestored, the reference's rest pinned to the EXACT face±0.05 with 0.001 tolerance): a THIRD independent construction of the same laws, RED on the union tree (the vertical descent's observed rest y=19.4079402396 at velocity exactly -1.0, 0.592 below the face; the thin-wall seat's observed rest x=61.97 at velocity exactly 3.0, 0.97 past the far face in the open air — this lane's own spawnRestored seats read y=20.2143 / x=61.77 at their own velocities, the pass-33 lane's launch-based seats y=19.841 / x=51.480: three constructions, one law, all RED on develop 6b29c46f)

The thirty-first pass's queue head taken in order (the same head the third stream's pass-33 lane took in the same window — the two lanes' seats are INDEPENDENT constructions: theirs launch-based with observed rests y=19.841 / x=51.480, this lane's spawnRestored-deterministic with observed rests y=20.2143 / x=61.77; both RED on develop at their own predicted values — the cross-stream confirmation of the same laws): the two honest
residue findings' pass-2 seats and pass-3 independent walks, both over
the freshly-fetched develop `6b29c46f` (the vine wire repair merged
into this pass's base through `d26a1ebd`). The seats:
`ArrowStuckStateParityDiscoveryTest` (this audit branch) — three
deterministic constructions over `spawnRestored` (the EXACT
position/velocity landing, no gaussian spread — the pass-29
strictly-deterministic construction precedent), every number
pre-computed against the engine's own constants (drag 0.99, arrow
gravity 0.05) and recorded in the failure messages. **All three RED
on develop at exactly the predicted values** — the run's evidence
record:

- **VT-014 seat 1 (the plain stuck position):** a 5x5 stone platform
  at y=20 (the top face y=21), an arrow dropped perfectly vertically
  from (50.5, 44.5, 50.5) at 0.8/tick — the 20th tick's pre-step
  position y=21.7791, endpoint y=20.2143. The engine rests the arrow
  at **y=20.2143, cell (50,20,50) — 0.786 INSIDE the floor block**
  (observed `Position[x=50.5, y=20.214295347071392, z=50.5]`). The
  reference's ray from 21.7791 hits the face at 21 and rests the
  arrow at **21.05 — the AIR cell above the floor** (the rendered
  shaft stands ON the surface; the pickup box overlaps a player
  standing on the face).
- **VT-014 seat 2 (the thin-wall air-stick):** a one-block-thick wall
  at x=60 (rows y=40..42), an arrow fired horizontally at 3.0/tick
  from (58.8, 41.5, 51.5) — the midpoint sample (60.285) lands INSIDE
  the wall, the endpoint (61.77) CROSSES the whole wall into open
  air. The engine rests the arrow at **x=61.77, cell (61,41,51) —
  hanging in OPEN AIR 0.77 past the wall's far face** (observed
  `Position[x=61.769999999999996, y=41.45, z=51.5]`). The reference's
  ray hits the near face at x=60 and rests the arrow at **59.95 —
  before the wall, in the cell it was flying through**.
- **VT-015 seat (the block-break un-stick):** the same vertical drop
  sticks the arrow in the platform's center block; the block is then
  REMOVED; fifteen ticks later the engine's arrow is **STILL inGround,
  FROZEN at the stuck endpoint** — the stick-clock arm consults
  nothing. The reference's inGround branch un-sticks the arrow on the
  very next tick (the tracked cell reads AIR, the identity check
  fails) and gravity alone carries it ~5.6 blocks below the floor
  row's bottom face within the window (even the zero-random draw).

THE PASS-3 INDEPENDENT WALKS (both findings challenged from the
execution path, the compensating behavior hunted, the design-choice
question asked):

- **VT-014's pass 3:** (a) the CALL-ORDER INVERSION pinned — the
  reference resolves the hit BEFORE integrating (ArrowEntity's stick
  arm at 288-309 sets velocity = face − pos and backs the position off
  0.05 behind the PRE-STEP position, then the flight branch's TAIL
  integration at 328-330 advances it by that same vector — the
  composition lands the arrow exactly 0.05 SHORT of the face, in the
  cell it flew through; verified across both the axial and the
  horizontal constructions); the engine integrates FIRST and detects
  the hit at the midpoint/endpoint samples, never rewriting the
  position. (b) The compensation hunt NEGATIVE: nothing rewrites the
  stuck position — the wire sends it whole (`onProjectileMoved`'s
  teleport passthrough), the pickup band reads it directly
  (`withinArrowPickupRange`: [y−0.25, y+0.25] vs
  [playerY−0.5, playerY+2.3] — a player standing on the face collects
  vanilla's 21.05 arrow but NOT the engine's 20.21 one, and a
  full-draw burial is ~2 blocks deep), and the render reads the same
  wire. (c) The design-choice challenge NEGATIVE: the VII1 counted
  note derives away the SHAKE row alone (Torch-Arcetecture.txt 3398);
  the anvil import's own ledger records the stuck cell/block rows as
  "projectile rows WITHOUT an engine surface — the point model
  derives the cell, the block and the damage from the motion"
  (AnvilWorldImport 1165-1190) — but the claimed derivation DOES NOT
  EXIST (no tracked-cell field, no consumer), and the derivation the
  ledger gestures at (floor of the rest position) only coincides with
  the hit cell BECAUSE of this very divergence: under the reference's
  face+0.05 rest the floor of the position is the AIR cell, not the
  hit block. The point model is a collision-modeling choice; the rest
  position's geometry is not derived by it.
- **VT-015's pass 3:** (a) the reference re-read fresh — the consult
  runs at the HEAD of every tick (ArrowEntity 157-169 reads the
  tracked cell, 172-186 the identity check on block+metadata), the
  un-stick re-arms the velocity with a FRESH
  `random.nextFloat() * 0.2F` factor PER COMPONENT over the STORED
  face-relative vector (the reference never zeroes it at stick time),
  and resets BOTH clocks (`inBlockTicks = 0`, `inAirTicks = 0`).
  (b) The compensation hunt NEGATIVE: no system consults projectiles
  on a block change — `WorldSolidity` is the only shared surface, the
  block-break and blast paths never touch the manager, and no
  listener exists for "the block under a stuck arrow changed".
  (c) The FAMILY walk: the reference's FishingBobberEntity carries
  the SAME law (lines 218-235 — the tracked cell, the identity check,
  the randomized re-arm, the clock resets) and the engine's
  FishingSystem bobber LACKS it too (the stick clock alone at
  FishingSystem 666-674) — the law is family-wide in the reference,
  absent family-wide in the engine. (d) The import walk: the
  reference SAVES the tracked cell (writeCustomNbt:
  xTile/yTile/zTile/inTile/inData) and restores it (readCustomNbt
  400-424); the engine's import DROPS those rows with the ledger's
  own "without an engine surface" isolation — the gap is RECORDED by
  the engine itself, never chosen as behavior.

**VT-014 CONFIRMED — three passes complete** (the thirty-first's
source walk; this pass's two behavioral seats at the predicted
deterministic values; this pass's call-order + compensation +
design-choice walk). **VT-015 CONFIRMED — three passes complete**
(the thirty-first's source walk; this pass's behavioral seat; this
pass's reference re-read + compensation + family/import walk). Both
eligible for correction (Phase E).

**VT-019 DISCOVERED (suspected, pass 1 complete — the source walk;
the seat owed; TWICE renumbered — the VT-016 draft lost 16/17 to the
pass-32 lane's piston/grass claims, the VT-018 draft lost 18 to the
pass-35 lane's pushed bowhit-sound claim, the VT-012->VT-013
precedent twice over):** the STUCK BURNING ARROW's TNT re-prime. The
reference consults `onEntityCollision` for an arrow from exactly two
sites: the FLIGHT branch's tail box scan (ArrowEntity line 382 —
inside the `else` opened at line 188; the inGround branch at 172-187
has NO scan) and the stick arm's own single-hit-block consult (lines
311-313). A burning arrow STUCK beside a TNT block therefore primes
NOTHING in the reference after the stick tick. The engine's
`burningTntPass` (ProjectileManager.tick's tail, lines 1004-1037) has
NO inGround gate: every burning arrow — flying OR STUCK — walks the
0.25 band around its position EVERY tick and primes any TNT found. A
stuck burning arrow adjacent to TNT: vanilla leaves the TNT standing,
the engine detonates it on the next tick. The vertical band also
differs in flight (the engine's symmetric [y−0.25, y+0.25] against
the reference's box [y, y+0.5] — the 1.8.8 setPosition feet law) —
the grazing half-cell edges, the same class as the portal consults'
shape. SEAT OWED (pass 2): a burning arrow stuck beside TNT (not in
it), the TNT's survival asserted against the engine's detonation.


## VT-014 — the arrow's stick placement is the post-step endpoint, not the reference's face−0.05 rest (CONFIRMED — three passes; the correction OWED)

- **Status:** CONFIRMED (three passes complete; eligible for Phase E).
- **Subsystem:** projectile physics — the arrow family's block-hit
  resting geometry (affects the rendered shaft, the pickup band, the
  thin-wall air-stick).
- **Affected engine files/methods:**
  `ProjectileManager.resolveCollisions` (the solid arm's
  `projectile.landInGround()` at ~line 1195 — the position untouched)
  + `ProjectileManager.tick` (the integrate-then-sample order at
  877-889); `ProjectileEntity.landInGround` (254-259 — the flag + the
  velocity zeroing, no position write).
- **Vanilla reference:** `ArrowEntity.tick`'s stick arm (lines
  288-309: velocity := face − pos, the 0.05 backoff behind the
  pre-step position, the tracked cell recorded) composed with the
  flight branch's tail integration (lines 328-330) — the final rest
  is exactly 0.05 SHORT of the hit face along the flight path, in the
  cell the arrow was flying through.
- **Expected behavior:** a stuck arrow rests 0.05 outside the hit
  face; the rendered shaft stands on the surface; a player standing
  on the face can collect it; a fast arrow never rests past a wall it
  crossed.
- **Observed behavior (the seats' deterministic values):** the rest is
  the post-step endpoint — 0.786 INSIDE a slow (0.8/tick) floor hit
  (y=20.2143 against the face 21, the reference's 21.05); a
  3.0/tick horizontal arrow sticks 0.77 past a one-block wall IN
  OPEN AIR (x=61.77 against the reference's 59.95); the pickup band
  consult fails for a player standing on the face once the burial
  exceeds ~0.25.
- **Reproduction:** `ArrowStuckStateParityDiscoveryTest` (this audit
  branch) — `theStuckArrowRestsOutsideTheHitFace`,
  `theFastArrowSticksAtTheWallNotPastIt`; both RED on develop
  `6b29c46f` with the observed values in the failure messages.
- **Verification history:** pass 1 (the thirty-first): the source
  walk. Pass 2 (this pass): the two behavioral seats at the predicted
  values. Pass 3 (this pass): the call-order inversion, the
  compensation hunt (negative), the design-choice challenge (negative
  — the import ledger's own "without an engine surface" record).
- **Confidence:** high — deterministic constructions, exact reference
  arithmetic, no compensating behavior.
- **Proposed minimal correction:** in `resolveCollisions`' arrow arm,
  walk the segment from the pre-tick origin to the sample (the
  engine's sampled-ray primitive — the fishing `blockHit` form) to
  the FIRST solid-entering sample, derive the crossed face (the cell
  boundary the segment crosses into the hit cell along), rest the
  arrow at face − 0.05·û (û the unit flight vector), record the hit
  cell as the tracked cell (VT-015's surface), and keep the velocity
  as the face-relative vector (the reference's stored form — the
  un-stick law's re-arm input).
- **Regression risks:** the wire's stuck-position consumers (the
  teleport packet now carries the surface rest — the CLIENT-visible
  change, the point of the fix); the pickup band (now reachable from
  the face — vanilla's answer); the midpoint-sample thin-wall case
  must route to the WALL's near face (not the midpoint's cell face);
  the anvil import's isolated-rows ledger note must be re-worded when
  the tracked-cell surface lands.


## VT-015 — the stuck arrow's tracked-block un-stick law is missing (the arrow; the fishing bobber beside it) (CONFIRMED — three passes; the correction OWED)

- **Status:** CONFIRMED (three passes complete; eligible for Phase E).
- **Subsystem:** projectile state machine — the inGround branch's
  per-tick consult (affects block-break/blast/piston interactions
  with stuck arrows, the anvil import's tracked-cell rows).
- **Affected engine files/methods:** `ProjectileManager.tick`'s
  inGround arm (lines ~851-858 — the stick clock ALONE);
  `ProjectileEntity` (no tracked-cell field);
  `AnvilWorldImport.importProjectile` (the xTile/yTile/zTile/inTile/
  inData rows isolated at 1165-1190); `FishingSystem`'s bobber stick
  clock (666-674 — the same law's second family member).
- **Vanilla reference:** `ArrowEntity.tick` 157-186 (the head-of-tick
  tracked-cell read + the identity check on block AND metadata; the
  un-stick: `inGround = false`, per-component
  `velocity *= random.nextFloat() * 0.2F` over the STORED
  face-relative vector, both clocks zeroed); `FishingBobberEntity`
  218-235 (the same law); `writeCustomNbt`/`readCustomNbt` (the
  tracked cell persisted: xTile/yTile/zTile/inTile/inData).
- **Expected behavior:** when the tracked cell's block changes
  identity or metadata (broken, blasted, piston-moved, the arrow's
  own TNT-ignite arm opening the cell), the arrow un-sticks on the
  next tick and falls out — the velocity re-armed with the randomized
  0.2 factors, the clocks reset.
- **Observed behavior:** the engine's stuck arrow stays stuck through
  ANY change to the block it rests in — frozen at the stuck endpoint
  in mid-air where the block used to be, until the 1200-tick expiry
  (the seat's record).
- **Reproduction:** `ArrowStuckStateParityDiscoveryTest
  .theStuckArrowFallsWhenItsBlockIsRemoved` — RED on develop
  `6b29c46f` (the arrow STILL inGround, the position FROZEN).
- **Verification history:** pass 1 (the thirty-first): the source
  walk. Pass 2 (this pass): the behavioral seat. Pass 3 (this pass):
  the reference re-read (the exact re-arm semantics), the
  compensation hunt (negative), the family walk (the bobber), the
  import walk (the isolated rows).
- **Confidence:** high — the law is stated twice in the reference
  (the arrow and the bobber), the engine's own ledger names the
  missing surface.
- **Proposed minimal correction:** add the tracked cell + the tracked
  block identity to `ProjectileEntity` (recorded at
  `landInGround`/the stick arm, restored by `restoreGrounded` from
  the import's xTile/inTile/inData rows); in `tick`'s inGround arm,
  consult the tracked cell each tick — identity match advances the
  clock, mismatch un-sticks with the reference's randomized re-arm
  and clock resets.
- **Regression risks:** the un-stick's randomized velocity consumes
  THREE random draws per un-stick (the shared `gameplayRandom`'s
  sequence — the same class of RNG-consumption sensitivity the
  project already tracks); the consult must read the arrow's OWN
  dimension (the TF-9 law); the bobber's parallel law is a SEPARATE
  fix (the family-member protocol — the mobs-then-items precedent);
  the import's isolated-row note shrinks when the rows land.


## Coverage ledger (updated by the thirty-sixth pass — the FIVE-LANE UNION: the pass-32 refresh + the pass-33/34/35/36 projectile rows, the Phase E corrections’ records unioned)

| Area | Status |
|---|---|
| Fire family — overworld tick/spread/burn (FireBlocks vs FireBlock) | inspected, verified 1:1 on the overworld path (the fourteenth-pass-era migration); the per-dimension arm LANDED by Wave FL4-F2 (VT-001 resolved) |
| Fire family — nether/End scheduling | LANDED by Wave FL4-F2 (the VT-001 correction; `PerDimensionFireAcceptanceTest` is the record) |
| Fluid family — per-dimension simulation | landed by FL4; not re-audited (the FL4 seats are the record) |
| Dig path — dimension routing | RESOLVED — PR #90 closed as superseded (Wave FL4-F5 landed the identical design first; the unique riders adopted at e088de73; `DigPathDimensionParityTest` the record) |
| Use/place families — dimension routing | the family arms LANDED by Wave FL4-F3; the generic surface resolved (PR #90's riders adopted — see the dig-path row) |
| Ice family — break-to-water, melt | RESOLVED — PR #89 closed as superseded with the corrections ADOPTED as riders on develop (the FL4-F4/F5/F6 waves) |
| Snow layer — placement support/melt/drops | drops landed (the mining-speed audit); melt + the 7-layer state surface recorded as VT-003's tail |
| Packed ice — melt/break laws | inspected: the reference's PackedIceBlock carries no melt arm; nothing owed |
| Portal mechanics (flag1, forcer) | landed by FL4-F1; the stand clock's re-fire law is VT-004 (RESOLVED — **PR #91 MERGED**) |
| Portal mechanics — the non-player crossing (the entity-level arm) | the MOBS family LANDED by **PR #98**; the REMAINING families (items, orbs, vehicles, arrows) carried by the open PRs #108/#109/#110/#113 (the fourth-fold concurrent convergence recorded in the pass-30/31 lanes; the coverage verified through the independent arc + snowball seats on the union tree `0e3151fe`). The named tails: the End-origin arm, the box-scan nuance (VT-013's rejection record), the four families' `PortalCooldown` persistence, the vehicle yaw modeling, the fishing bobber |
| Bed use — the out-of-dimension blast arm | RESOLVED: PR #102 (F-EX6) MERGED (b6718ab3) — the coverage verified by BOTH streams' independent seats |
| Portal mechanics — the riding gate | VT-005 RESOLVED — **PR #94 MERGED** |
| Portal mechanics — the mob re-entry cooldown decay | RESOLVED — **PR #107 MERGED** (98ded357): the cooldown decays ONE per tick (Entity.baseTick lines 311-313) |
| Drops — the TF-9 dimension stamp | RESOLVED (PR #90's riders adopted; the tile-family spill sites remain the per-dimension tile tail) |
| Tile families — per-dimension stores | the CHEST family LANDED by **Wave FL4-GAP2 Slice A** (`2d0f74af`: three ChestManager instances, the ZCD v3 dimension stamp, the consult sites routed — `ChestDimensionOwnershipAcceptanceTest` 4/0 the record); Slice B's honest tail: the hopper system, the ContainerViews, the furnace/dispenser/brewing families stay single-instance |
| Block updates — the multi-part families' pair-integrity arms | TWO arms in flight, ONE landed: the BED family's neighborChanged self-removal arm (**VT-009 the bed-coverage lineage's claim — CONFIRMED, three passes, PR #114 OPEN**); the break-path pair walks' world binding (**VT-009 the pass-28 lane's claim — RESOLVED — PR #106 MERGED** `becf84b0`, its review correction carried); the bed pair's sibling resolution (**VT-010 — CONFIRMED, PR #111 OPEN**). The DOOR family's neighborChanged arm EXISTS (`RedstoneSystem.doorNeighborChanged`, DoorBlock lines 157-198 — the door half REJECTED by its own green seat, now the regression guard) |
| Grass decay — the shade consult | **VT-017 SUSPECTED** (the bed-coverage lane's twenty-seventh pass, renumbered): the engine's `isOpaque` 3-block allowlist (air/torch/glass) stands in for the reference's opacity law (`getOpacity() > 2` — every non-solid-render block answers 0) — the grass dies under beds, flowers, slabs, stairs where vanilla keeps it; one source pass + the OBSERVED dirt-under-bed evidence from the VT-009 trace; this pass takes the seats up |
| Piston pushability — the behavior table's membership | **VT-016 SUSPECTED** (the bed-coverage lane's twenty-seventh pass, renumbered): the engine's `pistonMoveBehavior` DESTROY/BLOCK lists omit the bed family, the door family, the weighted plates, the cocoa (vanilla DESTROY) and the anvil (vanilla BLOCK) — a pushed bed MOVES where vanilla breaks it with its drop; one source pass done, the piston-push seat this pass's second head |
| Projectile family — the stick placement (the resting position's geometry) | **VT-014 CONFIRMED — FOUR INDEPENDENT CONSTRUCTIONS, FIVE LANES** (three passes each; the quadruple convergence the thirty-fifth/thirty-sixth passes recorded): pass 1 the thirty-first's source walk; pass 2 the seats — the pass-33 lane's launch-based pair (the observed rest y=19.841 INSIDE the hit cell, x=51.480 hanging past the thin wall), the pass-34 lane's spawnRestored-deterministic pair (y=19.4079402396 at velocity exactly -1.0, x=61.97 past the wall in open air — the exact face±0.05 pins at 0.001), the pass-35 lane's pair (y=20.919 below the face at 21.0, z=41.486 one full block past the wall), the pass-36 lane's own pair (y=20.2143 / x=61.77) — all RED on develop `6b29c46f` at exactly the placement asserts; pass 3 the walks (the writer census — `inGround` set true at exactly two sites and cleared NOWHERE; the design-choice challenges negative on both lanes; the consumers walk; the import-coherence edge). **THE CORRECTION OPEN: PR #117** (`fix/arrow-stick-placement-parity-audit` over develop `6b29c46f`: the reference's rayTrace face walk at the engine's cell granularity + the 0.05 backoff + the landed wire publish; both the pass-33 and pass-34 lanes' seats GREEN 4/0 on the fix tree, the projectile package 48/0, the neighbor lanes 34/0; **the #113 interaction reviewed** — the `!inGround` gate note posted, the union's lights-later regression named) — **but the construction leaves the PARTIAL-SHAPE face unfixed: VT-021 CONFIRMED against it — the thirty-eighth pass's record below** (the slab seat RED on the #117 tree, observed rest y=18.8268 THROUGH the slab's surface against the reference's 19.55; the record in the thirty-eighth pass's section below; the b-lane's box-view branch `237611d3` carries all three seats GREEN — the review note on #117 the channel, the construction pick the maintainer's) |
| Projectile family — the tracked-block un-stick law (the arrow; the bobber beside it) | **VT-015 CONFIRMED — FOUR INDEPENDENT CONSTRUCTIONS, FIVE LANES** (three passes each): pass 1 the thirty-first's source walk; pass 2 the seats — the pass-33 lane's (the rest IDENTICAL through the broken cell), the pass-34 lane's (STILL inGround, FROZEN at the endpoint), the pass-35 lane's pedestal break, the pass-36 lane's own construction — all RED on develop; pass 3 the walks (the reference re-read — the per-component fresh `random*0.2` re-arm over the STORED face-relative vector, both clocks reset; the compensation hunts negative; the family walk — the bobber the same law's second member; the import walk — the xTile/inTile/inData rows the engine's own recorded gap; the javadoc citations — `flingVelocity` names the un-stick's 0.2 factor as reference-only). **THE CORRECTIONS OPEN — the convergence:** **PR #120** (`fix/arrow-unstick-parity-audit` over develop `6b29c46f`: the tracked cell + the per-tick identity consult + the exact damped re-arm + the import's xTile/inTile rows landing — the reference's readCustomNbt 402-411 restores them; both lanes' seats GREEN, the anvil family 47/0) and **PR #119** (`fix/arrow-unstick-tracked-block-parity-audit`: the same live law 1:1, the import surface deferred as its named tail — the convergence note posted, the carrier pick the maintainer's, the unique coverage worth absorbing either way); the bobber's parallel arm the named follow-up (the family-member protocol) |
| Projectile family — the stuck burning arrow's TNT consult | **VT-019 CONFIRMED — three passes complete, Phase E eligible** (the thirty-sixth's source walk + the thirty-ninth's seat and independent challenge): the reference consults `onEntityCollision` from the FLIGHT branch's tail scan + the stick arm's single-hit-block consult alone (ArrowEntity 382, 311-313 — `Entity.move()`'s own scan never runs for arrows, they never call move(); `Entity.tick` is baseTick alone); the engine's `burningTntPass` walks STUCK burning arrows every tick with no `inGround` gate — the seat RED on develop `777b7c86` at the pre-computed geometry (the stuck arrow at `Position[60.147, 41.1, 51.5]`, the adjacent TNT at (60,40,51) primed on the stick tick itself; the control green — the landing-on-TNT ignition live), the placement-independent construction RED on PR #117's tree too; the fire clock FREEZES while stuck in the engine (the reference's baseTick decays it) — the detonation is eternal; the fix shape: the consult moves into the per-projectile flight walk's tail (runs on the stick tick too — the reference's own structure) over the feet-anchored box `[y, y+0.5]` (the symmetric band's grazing edges retired), the unit seat `aBurningArrowPrimesTheTntItOverlaps` re-pinned beside |
| Block-update wake (BlockUpdateSystem.runUpdate, RedstoneSystem.neighborChanged) — world binding | SURFED as VT-009's pass-3 non-compensation evidence: BOTH reads consult the overworld field (the changed-world parameter ignored in the six-neighbor dispatch) — the house stream's FL4-F10 lane landed the redstone world binding (`0328e92f`); the block-update arm's coverage rides that wave's seats |
| Block identity — the legacy wire rows | the vine family's gap LANDED by `6b29c46f` (the house's own production-crash repair; `LegacyBlockIdsIntegrityTest`'s skip set now EMPTY and pinned) |
| Mob spawns — the dimension stamp | **VT-012 CONFIRMED — three passes complete, fix-ready** (`audit/vt-012-spawn-dimension-stamp`: fourteen unstamped sites censused — the eggs, /summon, the wither, the spawner cart, the mooshroom shear, the infection/cure, the slime split, breeding, the golems, the infested burst; the wrong-stamped nether body invisible to the nether player, its physics reading the overworld's cells, a permanent ghost on the despawn walk) — the fix branch the next run's head |
| Natural spawn — the floor consult (`hasSolidTop`) | **VT-020 CONFIRMED — three passes complete, fix-ready** (the spawn-floor lane's record, provenance `fix/spawn-floor-solid-top-parity-audit` `0f4bbfb4`, pushed 06:04:09 — the lane's report record owed): `NaturalSpawnSystem.isPartialShape`'s two suffix arms (`endsWith(_slab)` / `endsWith(_stairs)`) approximated `World.hasSolidTop`'s structured law (lines 1740-1752) and missed in BOTH directions — the ten double-slab floors REFUSED a spawn vanilla hosts (the first arm's `isCube()` accepts them), the stored bottom-stair and stone_slab2 floors HOSTED 1966 night spawns vanilla refuses (`HALF == TOP` alone passes), the same family refusing the spawn under ten names and hosting it under three; three RED seats at exactly the predicted values, 3/3 GREEN on the fix tree, the five-arm re-read + the 56-identifier census + the compensation hunt (negative) the independent pass; the spawn suite 16/0, the spawn-adjacent lanes 70/0, the block package 311/0 — the PR owed |
| FX broadcasts — the dimension awareness | NOT INSPECTED (the FxManager position-only API; the PR #90 ledger) |
| World border — per-dimension | NOT INSPECTED (the reference's per-World border vs the engine's shared one) |
| Zombie/mob arms — world binding | NOT INSPECTED (the zombie door-break consults the overworld field) |
| Explosions — dimension routing | AUDITED (the twenty-sixth pass): the CORE resolved by PR #100; the CART slice RESOLVED — **PR #105 MERGED** (`278b139e`, the review rider at `32af09f3`). The named tail stands: the End/nether services' shared `gameplayRandom` vs the reference's per-world `world.random` |
| Item entities / pickup — dimension | the TF-9 stamped sites are the record; the pickup consult un-audited |
| Projectile family — the stuck arrow's crossing (the portal-lights-later construction) | **VT-013 REJECTED** (the thirty-first pass's pass-3 challenge): the original hypothesis — a portal lighting around an already-stuck arrow crosses it — is disproven by the reference's own tick chain (`ArrowEntity.tick` line 381 calls the box scan from the FLIGHT branch alone; the `inGround` branch at 172-186 runs the stick clock and the tracked-block check only). The corrected seat (`PortalStuckArrowCrossingDiscoveryTest.theStuckArrowStaysWhenThePortalLightsAroundIt`) pins the true law — NO crossing — GREEN on develop AND on the #113 tree. No fix owed. The honest residue re-recorded as VT-014 (the stick placement) and VT-015 (the un-stick law), both below |
| Projectile family — the stick's bowhit sound | **VT-018 CONFIRMED — three passes complete, Phase E eligible** (the pass-35 lane's source walk + the thirty-ninth's seat and independent challenge): the reference plays `random.bowhit` at BOTH impact arms — the BLOCK stick (ArrowEntity line 303, at the rest position, the pitch `1.2F/(rand·0.2+0.9)` band [1.0909, 1.3333], one draw per stick) and the ENTITY hit (line 276, before the remove); the engine plays NEITHER — the stick arm fires the no-op `onProjectileLanded` fan alone, `hitMob`/`hitPlayer`/`hitVehicle` soundless, `random.bowhit` at the tripwire alone. The seat RED on develop `777b7c86`: the stick in COMPLETE SILENCE (the recorder holding one event — the control pulse), the stuck position matching the known geometry; the POP CONTRAST decisive — the reference's third arrow sound (the pickup pop, line 436) IS wired with the exact pitch formula (the `onProjectileCollected` dispatch), the family's sounds implemented one by one, the bowhit pair alone missing. The fix: both arms play the sound (the arrow kind alone), the manager's `random` the draw source, the seat's pitch-band assert the parity pin |

## The queue for the next pass

0a. ~~THE FIVE-LANE UNION~~ — **DONE (this merge, the thirty-seventh pass's
   first act)**: the pass-35 lane's branch (a8c79f5f) unioned into the
   pass-36 lane — their seat set (y=20.919 / z=41.486 / the pedestal
   break), their pass-3 walks, their VT-018 (the bowhit sound), and
   the pass-30 docs union all ride whole; the conflict resolution was
   bookkeeping-only (the queue rewrite, the ledger rows, the pass
   sections), no engine files at stake. This lane (the thirty-seventh)
   carries the union forward.

0. ~~THE PHASE E CORRECTIONS~~ — **DONE, THE PRs OPEN (the pass-34
   lane's act, the record unioned above)**: VT-014's correction is
   **PR #117** (`fix/arrow-stick-placement-parity-audit`, the face
   walk + the 0.05 backoff + the landed wire publish); VT-015's is
   **PR #120** (`fix/arrow-unstick-parity-audit`, the tracked cell +
   the consult + the re-arm + the import's rows) with **PR #119** the
   parallel lane's same-law carrier (the convergence note posted on
   both; the maintainer's pick). The seats GREEN on the fix trees;
   the maintainer chooses among the convergent carriers — the next
   runs verify, never re-fix (the VT-008 protocol). The bobber's
   parallel un-stick arm stays VT-015's named follow-up.

1. **VT-017's passes** — **the correction CARRIED by PR #116 OPEN**
   (`fix/grass-shade-parity-audit`, the thirty-second lane's carrier):
   the remaining act is the review verification (the seats' re-run on
   the fix tree, the independent confirmation), never a re-fix.
2. **VT-016's passes**: the piston-push behavioral seat — a piston
   extending into a bed pair: vanilla's observable is the pair gone +
   exactly one bed item; the engine's today is the displaced half +
   the floating survivor. Then the independent pass over the remaining
   membership rows (doors, weighted plates, cocoa, anvil).

2a. ~~VT-019's seat~~ — **DONE (the thirty-ninth pass, this lane)**:
   the seat + the pass-3 challenge complete, **VT-019 CONFIRMED
   whole — three passes, Phase E eligible** (the record in the
   thirty-ninth pass's section; the fix shape mapped: the consult
   into the flight walk's tail over the feet-anchored box, the
   unit seat's re-pin beside). The Phase E fix branch the next
   run's head — behind the #117/#119/#120 carrier resolutions
   (the consult's window and the placement rest interact: the
   fixed rest's band is the seat's RED on both trees, the fix
   branch must re-run the five discovery seats green).
3. ~~VT-014's and VT-015's seats~~ — **DONE BY FOUR LANES IN THE SAME
   WINDOW** (the quadruple convergence, item 0's PRs the carriers).
3a. ~~VT-018's passes 2-3~~ — **DONE (the thirty-ninth pass, this
   lane)**: the FX-assert seat RED in complete silence (the control
   heard) + the pass-3 walk that found the SECOND SITE (the
   entity-hit bowhit, line 276 — the engine's hit paths soundless)
   and the pop contrast — **VT-018 CONFIRMED whole, three passes,
   Phase E eligible** (the record in the thirty-ninth pass's second
   act; the fix covers both arms, the arrow kind alone).
4. The merge watch: #106 MERGED (becf84b0), #115 MERGED (e17c538b);
   the OPEN set: #108, #109, #110, #111, #113 (the `!inGround` gate
   note posted), #114, #116 (VT-017), #117 (VT-014), #118 (F-DS1),
   #119/#120 (VT-015's convergent carriers) — after the crossing PRs
   merge, the End-origin arm (the forced-spawn consult,
   `PlayerManager.changeDimension` lines 473-484) becomes the portal
   family's one remaining architectural tail, and the four families'
   `PortalCooldown` persistence pass opens behind it.
5. **F-SP1's owed passes** (the twentieth pass's residue, the last
   uncarried standing lane): the support-consult family's passes not
   begun. **F-DS1 is CARRIED by PR #118 OPEN** (the double slab's
   collision row — the review verification the remaining act).
5a. **VT-012's own pass** (the pass-30 lane's discovery): the
   `NetherBlastRoutingAcceptanceTest` flake's bounded fix — the
   seat's read order (await the event reference, not the cell
   alone); a test-infrastructure correction.
6. The dimension tails (the PR #90 ledger): the mob spawn's dimension
   stamp (`MobManager.spawnAt`), the FX dimension awareness, the
   per-dimension world border, the zombie arm's world binding. The
   furnace/dispenser/brewing tile families' per-dimension stores ride
   the FL4-GAP2 Slice B tail. The blast RNG tail (VT-007's named
   tail): the End/nether services' shared `gameplayRandom` vs the
   reference's per-world `world.random`.
7. The watch items (the thirty-ninth pass's fetch): **MERGED onto
   develop `777b7c86`**: #106 (`becf84b0`), #115 (`e17c538b`). The
   OPEN set: **#108, #109, #110, #111, #113 (the `!inGround` gate
   note posted), #114, #116 (VT-017), #117 (VT-014), #118 (F-DS1),
   #119/#120 (VT-015's convergent carriers)** — #109/#113's coverage
   VERIFIED (the thirty-first pass's independent seats); VT-021's
   correction rides the b-branch `237611d3` offered through #117's
   review; VT-020's (the spawn floor) PR owed on
   `fix/spawn-floor-solid-top-parity-audit`; VT-012's fix branch owed
   on `audit/vt-012-spawn-dimension-stamp`. The maintainer owns the
   pick among the convergent carriers. The pass-30 lane's docs union
   is DONE (the pass-35 lane's thirty-fifth pass); their engine files
   stay on the #110 branch.



## The thirty-eighth pass — the recovery reconciliation + the partial-shape stick face discovered and confirmed whole against PR #117's landed construction (first numbered VT-020; RENUMBERED VT-021 by the thirty-ninth pass — the spawn-floor lane's `hasSolidTop` claim took the number publicly first, `fix/spawn-floor-solid-top-parity-audit` pushed 06:04:09 vs this record's 06:05:09; the VT-012->VT-013 precedent)

**The recovery.** The environment reset with the pass-33 b-lane's VT-014
fix commit (`237611d3`, the box-view construction) committed but UNPUSHED,
while the pass-34 lane had pushed its own VT-014 fix (`30650d74`, the
rayTrace cell walk) and opened **PR #117**. The two commits are SIBLINGS
on `6b29c46f` — two independent constructions of the same law. The
b-lane's branch is renamed `fix/arrow-stick-placement-parity-audit-b`
(preserved; the remote name belongs to #117's head — no force-push, no
duplicate PR against the same finding without new evidence). This pass's
first act: **independently verify the landed construction** — the
verifier's duty to an open PR, not just to develop.

**The discovery (VT-021's pass 1 — the source walk).** PR #117's
`stickRestPosition` walks CELL-BOUNDARY planes (the integer faces
`cellY+1.0`/`cellY+0.0`) and consults solidity at each ENTERED cell's
CENTER point (`solidAt(cellX+0.5, cellY+0.5, cellZ+0.5)`). The reference
(`World.rayTrace` lines 639-760) walks cells the same way, but at every
entered cell it calls **`block.rayTrace(world, pos, from, to)`**
(`Block.java` lines 546-590) — which FIRST `updateShape`s the block then
clips the segment against the block's **OWN SHAPE BOUNDS**
(`this.minX/maxX/minY/maxY/minZ/maxZ`, the six
`intermediateWithX/Y/Z` crossings each containment-checked, the nearest
kept). For a bottom slab (`SlabBlock.updateShape`:
`setShape(0,0,0,1,0.5,1)`; the hierarchy
`SingleStoneSlabBlock → StoneSlabBlock → SlabBlock → Block` overrides
`rayTrace` NOWHERE) the hit face is the slab's half-height TOP at
y=19.5 — NOT the cell boundary at y=20. The stick arm then composes the
rest at face − 0.05·û (ArrowEntity 296-302 + the 328-330 tail) =
**y=19.55** for a falling arrow. The engine's own `CollisionShapes`
carries the identical half-height box (`Box(0,0,0,1,0.5,1)`, the
exclusive-max `contains`), so the shape face is CONSULTABLE — the cell
walk just never asks. Two structural misses, both verified by reading
the #117 tree: (a) the walk consults only cells it ENTERS — a
fast-falling arrow whose pre-step position is already INSIDE the slab's
cell (the step started above the slab's box top but below the cell top)
never consults the slab's cell at all; (b) even when the walk enters
the slab's cell, the center-point consult at y=19.5 answers FALSE (the
exclusive-max `contains`: `0.5 < 0.5` is false) and the boundary plane
it would back off to is the CELL top at 20 — the walk is INCAPABLE of
answering a face inside the cell. The fence/stairs/anvil/snow-layer
class extends the gap beyond slabs: every non-full-cube collidable's
face sits inside its cell.

**VT-021's pass 2 — the behavioral seat, RED on the #117 tree.** The
b-lane's slab seat (`ArrowStickPlacementParityTest
.theStuckArrowRestsAtTheSlabShapeFaceNotTheCellBoundary`: a 5×5 bottom
slab platform at cell y=19 — shape top 19.5, cell boundary 20 — an
arrow launched down into it; the rest must sit within [19.5, 19.55])
ran against the #117 worktree: **RED, observed rest
`Position[x=42.45938855034985, y=18.826805946041848, z=42.51259591165122]`**
— the arrow tunneled THROUGH the slab's 0.5-thick box (the flight's
discrete midpoint/end-point sampling skipped past the shape face), the
cell walk answered null, and the rest fell back to the ENDPOINT: 0.72
BELOW the shape face, embedded through the slab's surface, in open air
under the platform. Vanilla's answer for the same step: the rayTrace
hits the slab's top at 19.5, the rest composes at 19.55. On the
b-lane's box-view construction (`237611d3`): the same seat **GREEN**
(rest within [19.5, 19.55]), all three seats 3/0 — the full-cube
platform and the thin-wall seats ride GREEN on BOTH constructions (the
law they share), the slab seat discriminates them.

**VT-021's pass 3 — the independent challenge.** (1) The slab hierarchy
walk: no `rayTrace` override anywhere below `Block` in the slab family —
the shape-bounds clipping is the slab's own law, not an inherited
accident. (2) The compensation hunt on the #117 tree: the observed
18.8268 IS the raw endpoint rest (the walk's null path) — nothing else
in the PR moves the rest; the wire's new teleport publish faithfully
publishes the WRONG value. (3) The alternative-hypothesis challenge: is
the tunneling itself the bug (the sampling, not the placement)? No —
the sampling granularity is the engine's own named limitation (the
midpoint/end-point discrete walk, the same limitation #117's own record
names for the same-cell residue), and the placement law stands
independently: WHENEVER the stick triggers, the rest must sit at the
collision SHAPE's face — 0.05 outside it along the approach. The
placement is the observable; the sampling depth is a separate, already-
named tail. (4) The intentional-design challenge: the PR's own doc
names the same-cell residue as the accepted cell-granularity
limitation — but the slab case is NOT the same-cell residue: the arrow
flew a full cell-plus through open air above the shape; the shape face
was CROSSED and missed. The gap is a genuine divergence from the
demonstrated reference law, not a documented trade-off.

**The verdict: VT-021 CONFIRMED — three passes** (source walk,
behavioral seat on the #117 tree, independent challenge), against
BOTH develop (the endpoint rest, observed 18.815 in the pass-33
record) AND PR #117's construction (observed 18.8268 fresh). The
correction: the box-view face computation — the b-lane's
`restAtTheHitFace` + `segmentEntry` construction (the entry face of
the from→sample segment against the hit cell's collision BOXES, the
slab method per axis with the behind-entry reject, `SolidQuery
.solidBoxesAt` answered by `ShapeGround` over `CollisionShapes
.cellBoxes` with the same neighbour probe the point test uses). The
b-branch carries it with all three seats green; the review note on
PR #117 documents the gap with the fresh observed values, and the
maintainer picks the construction — #117 amended with the box consult,
or the b-branch's whole alternative (both lift the same three
full-cube/thin-wall/slab seats green; the regression lanes ran fresh
on the b-branch this pass: the projectile package whole 48/0,
RangedCombat 10/0, TntSystem 2/0, AnvilImport 6/0, the parity seats
3/0 — 69 tests, no failures).


## VT-021 — the stick placement's PARTIAL-SHAPE face: the rest must sit at the collision shape's own face, never the cell boundary or the tunneled endpoint (CONFIRMED — three passes; RED on develop AND on PR #117's construction; the box-view alternative construction the correction; first numbered VT-020 — renumbered by the thirty-ninth pass, the spawn-floor lane's `hasSolidTop` claim took 20 publicly first)

- **Status:** CONFIRMED (three passes complete; the correction exists
  as the b-lane branch `fix/arrow-stick-placement-parity-audit-b`
  (`237611d3`), offered as the alternative to PR #117 through the
  review note — the maintainer picks the construction).
- **Subsystem:** projectile physics — the arrow family's block-hit
  resting geometry for NON-FULL-CUBE collidables (slabs, stairs,
  fences, anvils, snow layers, and every partial shape whose face
  sits inside its cell). Extends VT-014's placement law; distinct
  finding because the reference surface differs (the shape-bounds
  clip vs the cell walk) and PR #117's landed construction fixes
  VT-014's full-cube cases while leaving this one.
- **Affected engine files/methods:** `ProjectileManager
  .stickRestPosition` (PR #117's walk — the cell-boundary planes and
  the entered-cell center consults, both incapable of answering an
  intra-cell shape face); the pre-fix `resolveCollisions` solid arm
  (develop — the endpoint rest); the flight's discrete sampling (the
  midpoint/end-point walk whose tunneling through thin shapes the
  placement must absorb).
- **Vanilla reference:** `World.rayTrace` (lines 639-760) → at every
  entered cell `block.rayTrace(this, blockpos, from, to)` (`Block`
  lines 546-590: `updateShape` then the six-plane clip against the
  block's OWN `minX/maxX/minY/maxY/minZ/maxZ`, the nearest contained
  crossing kept) → the stick arm's composition (ArrowEntity 296-302 +
  328-330: rest = face − 0.05·û). The slab family overrides nothing:
  `SlabBlock.updateShape` sets the half-height bounds
  (`setShape(0,0,0,1,0.5,1)` bottom, `(0,0.5,0,1,1,1)` top).
- **Expected behavior:** an arrow sticking into a bottom slab rests
  0.05 above the slab's half-height surface (y=19.55 for a slab at
  cell 19) — the shaft visibly standing on the slab's surface, in the
  cell the arrow flew through, collectable from there.
- **Observed behavior:** develop: the endpoint rest, observed 18.815
  (the pass-33 stash-discrimination record). PR #117's tree (fresh,
  this pass): **18.826805946041848** — the arrow through the slab's
  surface, hanging in the air below the platform. Both ~0.72 below
  the reference's 19.55.
- **Exact technical explanation:** the #117 walk's faces are the
  integer cell boundaries; a partial shape's face (the slab's 19.5)
  is interior to the cell and unreachable by the walk. The walk
  consults only ENTERED cells at their CENTER points: (a) a
  fast-falling arrow whose step STARTS inside the slab's cell (above
  the box top 19.5, below the cell top 20) never consults the slab's
  cell; (b) the exclusive-max `contains` (`0.5 < 0.5` false) makes
  the center consult false anyway; (c) the walk returns null and the
  rest falls back to the post-step endpoint — which the discrete
  sampling can have carried a full block past the shape face (the
  tunneling). The reference's ray consult is continuous over the
  step and clips the SHAPE's planes, so vanilla never tunnels and
  always answers the shape face.
- **Reproduction:** `ArrowStickPlacementParityTest
  .theStuckArrowRestsAtTheSlabShapeFaceNotTheCellBoundary`
  (the b-branch `237611d3`): RED on develop and on the #117 tree
  (observed values above), GREEN on the b-branch (rest within
  [19.5, 19.55]).
- **Verification pass history:** pass 1 (the thirty-eighth): the
  source walk (the reference's block-level shape clip vs the walk's
  cell granularity; the slab hierarchy's non-override; the engine's
  own CollisionShapes box availability). Pass 2 (the
  thirty-seventh): the behavioral seat run fresh on the #117
  worktree — RED at 18.8268; GREEN 3/3 on the b-branch. Pass 3 (the
  thirty-seventh): the independent challenge (the compensation hunt
  negative, the tunneling-vs-placement separation, the
  intentional-design challenge negative — the PR's named
  same-cell-residue limitation does not cover this case).
- **Confidence:** high — deterministic construction, exact reference
  arithmetic, fresh observed values on both trees.
- **Proposed minimal correction:** the box-view face computation:
  `restAtTheHitFace` computes the from→sample segment's entry face
  against the HIT CELL's collision boxes (`segmentEntry`, the slab
  method per axis with the behind-entry reject), rests the arrow at
  face − 0.05·û; `SolidQuery.solidBoxesAt` (a default-null view)
  answered by `ShapeGround` over `CollisionShapes.cellBoxes` with
  the same neighbour probe the point test uses — partial shapes
  answer their OWN faces. Carried by the b-branch, offered as the
  alternative construction in the PR #117 review note (or #117's
  walk gains the same box consult at its entered cells).
- **Regression risks:** the same family as VT-014's (the wire's
  stuck-position consumers, the pickup band) — no new surface beyond
  the shape-aware consult; the stub `SolidQuery` implementations keep
  their point-test semantics (the default null, the caller's unit-
  cube fallback); the fence/stairs neighbour-probe shapes must
  answer their own composed boxes (the probe the point test already
  uses).
- **Associated branch:** `fix/arrow-stick-placement-parity-audit-b`
  (`237611d3`), pushed this pass; the review note on PR #117 the
  channel; no competing PR opened pending the maintainer's pick
  between the constructions (the note offers the branch).


## The thirty-ninth pass — the VT-020 numbering collision resolved (the spawn-floor lane's public claim first), the lineage's stale rows brought current, VT-019's seat the head

**The lane's provenance.** This lane composed the five-lane union
independently in the same window (the thirty-sixth close-out's named
first act) but the remote push lost to a sibling session's identical
union by minutes (`13575c2e` owns the thirty-seventh name; this
lane's union commit superseded, its corrections carried here whole).
The lane re-based on the canonical lineage — the thirty-seventh
union -> the thirty-eighth's recovery reconciliation + the
partial-shape stick face — as `audit/verifier-pass-39-discovery`.

**THE VT-020 COLLISION, RESOLVED BY THE PUBLIC-FIRST PROTOCOL.** Two
lanes claimed VT-020 within one minute of each other this window:
the spawn-floor lane's `hasSolidTop` finding
(`fix/spawn-floor-solid-top-parity-audit` `0f4bbfb4`, PUSHED
06:04:09 — the natural spawn floor's structured law: the ten
double-slab floors refused a spawn vanilla hosts, the stored
bottom-stair and stone_slab2 floors hosting 1966 night spawns
vanilla refuses; three passes complete in the lane's record) and the
thirty-eighth pass's partial-shape stick face (authored 06:03:08,
PUSHED 06:05:09 — the slab seat RED on PR #117's tree at 18.8268).
The protocol's operative word is PUSHED (the pass-35 lane's
"pushed bowhit-sound claim" precedent, the pass-32 lane's
"piston/grass claims took the ids first"): the spawn-floor lane
took VT-020 publicly first; the partial-shape stick face RENUMBERS
to **VT-021** — the renumber applied above in the thirty-eighth
pass's own records with the timestamps recorded (the honest note:
the thirty-eighth's record was authored first, pushed second; the
public-first rule governs, the author-date fact preserved here).

**THE LEDGER brought current** (the corrections this lane's
superseded union had composed, plus the lineage's own staleness):
the VT-009 pass-28 row to **PR #106 MERGED** (`becf84b0`); the
VT-012 row from NOT INSPECTED to **CONFIRMED — three passes
complete, fix-ready** (the vt-012 lane's own branch record,
`audit/vt-012-spawn-dimension-stamp` `ca05ad22`: fourteen unstamped
spawn sites, the invisible nether body, the permanent ghost); the
spawn-floor lane's VT-020 record added to the ledger
provenance-marked (its report record owed by its own lane); the
literal `±` escape artifact fixed in the VT-014 row (the
thirty-seventh's fix commit missed one instance); the queue's item
7 refreshed to the fetch's truth (#106/#115 MERGED, the ten-PR open
set, the three fix-ready branches owed PRs).

**THIS PASS'S HEAD — VT-019's seat** (the queue's item 2a, twice
named the head and twice deferred): the stuck burning arrow beside
TNT, the behavioral seat (pass 2) and the independent challenge
(pass 3) — **VT-019 CONFIRMED whole, three passes, Phase E
eligible**. The record:

**VT-019's pass 2 — the behavioral seat, RED on develop `777b7c86`
at exactly the pre-computed geometry**
(`ArrowStuckBurningTntDiscoveryTest`, this lane's construction). A
stone at `(60, 41, 51)` with a TNT directly beneath it at
`(60, 40, 51)`; a burning arrow (`setOnFireFor(2000)`, the Flame
release's own clock) spawned at `(59.85, 41.15, 51.5)` with
velocity `(0.3, 0, 0)` — the deterministic one-tick shot: the
midpoint sample `(59.9985, 41.125)` reads the AIR cell before the
stone, the endpoint sample `(60.147, 41.10)` reads the STONE —
`landInGround()` fires, the arrow rests at the endpoint
**`Position[x=60.147, y=41.1, z=51.5]`** (observed, matching the
pre-computed value to the digit). The TNT — never touched by the
flight, merely adjacent — sits at the bottom edge of the engine's
un-gated ±0.25 band (the band's Y cells {40, 41}), and
`burningTntPass` (ProjectileManager lines 1004-1037, the tick's
tail, no `inGround` gate) primes it ON THE STICK TICK ITSELF:
**the observed block is AIR, the asserted `minecraft:tnt` — RED at
exactly the finding's shape**. The reference's answer: the stick
arm's hit-block consult (ArrowEntity 311-313) read the STONE, and
the `inGround` branch (172-186) walks NOTHING — the TNT stands for
the whole 1200-tick stick clock.

- **The placement-independence** (the VT-015 seat precedent): the
  TNT's cell is in the band under BOTH rest geometries — the
  develop endpoint `(60.147, 41.10)` bands Y to {40, 41}; the
  VT-014-fixed rest (the reference's face−0.05·û at `(59.951,
  41.133)`) bands Y to {40, 41} too — **the seat stays RED on PR
  #117's tree**; the finding is the un-gated pass alone. And the
  reference's own consult window never reaches the cell: the rest
  box (the feet law, `[y, y+0.5]` = `[41.133, 41.633]`) covers
  cell 41 only — and it is walked on the STICK TICK alone (the
  flight branch's tail), never after.
- **The control** (`theControlBurningArrowLandingOnTntIgnitesIt`,
  GREEN on develop): a burning arrow dropped onto a TNT block at
  `(62, 41, 51)` ignites it — the engine's `tntIgniteSink` consult
  at the flight sample (the reference's stick-arm hit-block
  consult, the SAME law), the block leaves the world. A dead arm
  cannot hide behind the seat: the ignition path is live in the
  same boot, and the main seat's RED is the un-gated pass's own
  work.

**VT-019's pass 3 — the independent challenge (this lane).**

1. **THE EXECUTION-PATH CENSUS — NEGATIVE for compensation.** The
   reference's `checkBlockCollisions` (Entity lines 667-689) has
   exactly TWO call sites in the arrow's world: `Entity.move()`
   line 627 — **which arrows NEVER call** (the arrow family
   integrates x/y/z directly and calls `setPosition`, grep-verified
   across ArrowEntity/ProjectileEntity/ThrownEntity: zero `move()`
   calls) — and `ArrowEntity.tick` line 382, the FLIGHT branch's
   tail. `Entity.tick()` is `baseTick()` alone (line 264-265, no
   scan). The `inGround` branch consults the tracked cell and the
   stick clock ALONE. Nothing else can prime a stuck-arrow-adjacent
   TNT: the compensation hunt is NEGATIVE.
2. **THE FIRE-CLOCK AMPLIFICATION.** The engine's `tickFire()` runs
   in the flight branch ALONE (the `inGround` arm `continue`s
   before it) — a stuck arrow's fire clock FREEZES, so the un-gated
   pass detonates FOREVER. The reference's `baseTick` (the
   `super.tick()` at ArrowEntity line 150) decays `onFireTimer`
   every tick, stuck or not — vanilla's stuck burning arrow goes
   cold and even a hypothetical scan would find nothing. The
   engine's stuck arrow burns eternally: the divergence is worse
   than the seat's window shows.
3. **THE DESIGN-CHOICE CHALLENGE — the javadoc's own citation
   disproves its extension.** `burningTntPass`'s javadoc claims
   "every burning arrow — **in flight or stuck in the ground** —
   walks the cells its 0.5 box overlaps", citing
   `checkBlockCollisions` (Entity 665-689). But the citation's own
   call sites (item 1) prove the stuck extension is a MISREADING:
   the scan the javadoc cites runs from the flight branch's tail
   alone. The intent documented is "match the reference" — the
   implementation extends past the reference's own structure. A
   ledgered intent to match vanilla, misimplemented, is a genuine
   mismatch (the VT-006 precedent), not an intentional divergence.
4. **THE GRAZING EDGES QUANTIFIED** (the record's named tail, the
   second member of the same pass's law): the engine's band is the
   symmetric `[y−0.25, y+0.25]` (the point model's
   "box-center envelope" — the javadoc's own phrase, wrong in the
   vertical); the reference's box is feet-anchored `[y, y+0.5]`
   (`setPosition` line 246: `Box(x−f, y, z−f, x+f, y+f1, z+f)`).
   In FLIGHT the windows diverge at both edges — FALSE PRIMES on
   cells overlapping `[y−0.25, y)` only (an arrow at y=41.1 primes
   a TNT at cell 40 in the engine; the reference's box [41.1,
   41.6] never touches it) and MISSED PRIMES on cells overlapping
   `(y+0.25, y+0.5]` only (an arrow at y=41.6 with a TNT at cell
   42: the reference's box [41.6, 42.1] touches the cell, the
   engine's band [41.351, 41.849] does not). The fix's scope is
   ONE law: the reference's box walk — the gate AND the feet-law
   window together.
5. **THE REGRESSION SURFACE MAPPED.** The unit seat
   `ProjectileManagerTest.aBurningArrowPrimesTheTntItOverlaps`
   pins the stick-INTO-TNT observable (a burning arrow landing ON
   a TNT block primes it — vanilla-correct through the reference's
   HIT-block consult) but drives it through the UN-GATED band pass
   with a STUBBED `tntIgniteSink` (the default `(pos,dim) -> false`
   at line 376 — its arrow sticks inside the TNT cell because the
   sample consult is unwired; the band pass then primes). A blanket
   post-hoc `!inGround` gate would break this seat wrongly; the
   correct fix shape gates on **"flew this tick"** — the reference
   runs the tail scan ON the stick tick too (the stick tick is a
   flight-branch tick; the scan covers the REST box) — so the
   consult belongs in the per-projectile flight walk's tail (the
   reference's own structure), never in a post-loop pass that has
   lost the flew-this-tick information. The fix carries the unit
   seat's re-pin if its shape changes (the VT-009 review-correction
   precedent).
6. **THE ADJACENT TAIL NAMED, NOT FOLDED**: the stick-time consult
   itself is sampled at the midpoint/endpoint alone — a fast arrow
   crossing a TNT cell BETWEEN samples misses where the reference's
   continuous `rayTrace` never does (the VT-014 sampling class,
   its own root).

**The verdict: VT-019 CONFIRMED — three passes** (the
thirty-sixth's source walk; this lane's seat, RED at the
pre-computed geometry with the control green; this lane's
independent challenge — the execution-path census, the fire-clock
amplification, the javadoc's disproven extension, the quantified
grazing edges, the regression surface). The proposed minimal
correction: move the TNT consult into the per-projectile flight
walk's tail (the reference's own shape — runs on every flight tick
INCLUDING the stick tick, over the post-step position), the window
the reference's feet-anchored box `[y, y+0.5]` (x/z ±0.25
unchanged), and delete the post-loop `burningTntPass` (or gate it
on the flew-this-tick record). The regression set: the projectile
package whole (48/0 this run), `TntSystemTest` (2/0), the unit
seat's re-pin beside.


## The thirty-ninth pass, second act — VT-018 confirmed whole (the stick's bowhit sound): the seat, and the pass-3 walk's SECOND-SITE discovery

**VT-018's pass 2 — the FX-assert seat, RED on develop `777b7c86`**
(`ArrowStickBowhitSoundDiscoveryTest`, this lane's construction). A
recorder on the `FxManager` bus captures every `Sound` event; a
control pulse (`random.bow` injected at the platform) proves the
recorder hears the boot; the VT-014 seat's own vertical drop (spawn
`(50.5, 44.5, 50.5)`, velocity `(0, -0.8, 0)`) sticks the arrow in
the platform's center block at the known endpoint
**`Position[x=50.5, y=20.214295347071392, z=50.5]`** — and the
recorder holds exactly ONE event through the stick (the control
pulse): **the block stick lands in COMPLETE SILENCE — RED at exactly
the finding's shape.** The reference plays
`random.bowhit` at the rest position on that very tick (ArrowEntity
line 303, `1.0F, 1.2F / (rand·0.2 + 0.9)` — the pitch band
[1.0909, 1.3333], one fresh draw per stick, BEFORE
`inGround = true`). The seat's second assert pins the pitch band for
the fix's parity pin.

**VT-018's pass 3 — the independent challenge, and the SECOND SITE.**

1. **THE SECOND SITE DISCOVERED** (this walk's contribution — the
   finding's scope widens): the reference plays `bowhit` at TWO
   sites, not one. Line 303 is the BLOCK stick; **line 276 is the
   ENTITY hit** — every mob/player the arrow's scan lands on plays
   the same sound (the same formula, the same draw) BEFORE the
   `remove()` (the arrow dies on entity hits in 1.8.8, Endermen
   excepted by the reference's own gate). The engine plays NEITHER:
   `hitMob`/`hitPlayer`/`hitVehicle` are soundless (the only
   `fx.sound` in ProjectileManager is the shard break at line 1449,
   the thrown bottle family's own feedback). The bowhit is the
   arrow-family's impact signature at BOTH arm types — the fix must
   cover both sites.
2. **THE CONSUMERS WALK**: `onProjectileLanded`'s only registered
   implementation is the protocol adapter's explicit no-op
   (V18ProtocolServer line 1759, the position-sync comment); the
   stick arm fires that fan alone (ProjectileManager line ~1197).
   `random.bowhit` appears in the engine at the tripwire alone
   (TripwireSystem line 313 — a different mechanic's sound).
3. **THE POP CONTRAST** (the design-choice challenge, decisive): the
   reference's THIRD arrow sound — the pickup pop (line 436,
   `random.pop`, `((rand−rand)·0.7+1.0)·2.0`) — IS wired in the
   engine, with the reference's exact pitch formula (the
   `onProjectileCollected` dispatch, EngineServer line ~18487). The
   arrow family's sound policy is not "silent" — the pop proves the
   family's sounds were implemented one by one, and the bowhit pair
   alone is missing. An omission, not a choice (the VT-006
   precedent shape).
4. **THE RNG ROW**: each bowhit play consumes ONE `nextFloat` (the
   pitch draw) — two consumption sites in the reference, zero in the
   engine. The fix's draws come from the manager's own `random` (the
   same source the shard break's pitch uses — the closest mapping of
   the reference's per-entity `this.random`).
5. **THE EDGE WALK**: the entity-hit sound plays BEFORE the remove
   (line 276 precedes line 279's `remove()`) — the fix plays it at
   the hit verdict, never at the removal; the block-stick sound
   plays at the BACKED-OFF rest position (line 303 follows the 0.05
   backoff at lines 300-302) — the fix plays it at the rest, which
   on develop's geometry is the endpoint (the fix composes with
   VT-014's placement repair: on #117's tree the rest moves to the
   face, the sound moves with it — the fix's parity pin asserts the
   position beside the pitch band).

**The verdict: VT-018 CONFIRMED — three passes** (the pass-35 lane's
source walk; this lane's seat, RED in complete silence with the
control heard; this lane's independent challenge — the second site,
the consumers walk, the pop contrast, the RNG row, the edge walk).
The proposed minimal correction: the stick arm and the three
entity-hit verdicts each play
`fx.sound(at, "random.bowhit", 1.0f, 1.2f / (random.nextFloat() * 0.2f + 0.9f), dimension)`
(the arrow kind alone — the other kinds' impact sounds are their own
families' laws); the seat's pitch-band assert the parity pin.


## The thirty-ninth pass, third act — the cross-tree verifications (the honest-convergence protocol): VT-019's and VT-018's fix PRs independently verified GREEN through this lane's constructions

The fix lanes moved on this lane's confirmations within minutes — the
reviewer's duty followed (the pass-38 precedent: "independently
verify the landed construction — the verifier's duty to an open PR,
not just to develop"):

- **PR #123 (VT-019's gate, `fix/stuck-burning-arrow-tnt-parity-audit`
  `1a5e2059`)**: this lane's full-boot seat
  (`ArrowStuckBurningTntDiscoveryTest`) run on the fix tree —
  **2/0 GREEN** (the main seat's TNT stands, the control's
  flight-path ignition survives), against its RED on develop at the
  pre-computed geometry. Two independent constructions, one law. The
  review note posted with the two residues named: the flight band's
  feet law carried by **PR #128** (VT-023), the **stick-tick scan
  residue uncarried** (the reference's stick tick IS a flight tick —
  its line-382 scan covers the REST box; the post-hoc gate skips the
  stick tick entirely — the window narrow, the seat's TNT sitting in
  the band under both windows, a follow-up slice owed). Two
  convergent carrier branches exist beside the PR
  (`fix/stuck-burning-arrow-tnt-parity-audit-b2` `026e9135`,
  `fix/burning-arrow-tnt-gate-parity-audit` `1bc62cb7`) — the
  maintainer's pick, the #119/#120 protocol.
- **PR #124 (VT-018's bowhit, `fix/arrow-stick-bowhit-sound-parity-audit`
  `6a4811b4`)**: this lane's full-boot seat (renamed
  `ArrowStickBowhitSoundFullBootDiscoveryTest` — the fix lane's PR
  owns the canonical name, the file-resolution protocol) run on the
  fix tree — **1/0 GREEN** (the bowhit present, the pitch inside the
  reference's band, the seat's second assert the pin). The review
  note posted with the entity-hit site's requirements (the arrow
  kind alone, the sound at the hit verdict before the removal, its
  own seat) — the PR's named follow-up, now doubly confirmed.
- **The window's PR landscape** (this pass's fetch, 06:41): nineteen
  PRs open — the new since the thirty-ninth's first act: **#121**
  (VT-020, the spawn floor — the lane's own PR, the number this
  pass's resolution awarded), **#122** (the b-lane's VT-021+VT-014
  carrier, the renumber adopted), **#123** (VT-019), **#124**
  (VT-018), **#125** (VT-012, the dimension stamp), **#126** (VT-016,
  the piston pushability), **#128** (VT-023, the flight band's feet
  law — built on this lane's pass-3 grazing-edge quantification),
  **#129** (VT-022, the piston entity gate), **#130** (VT-013's
  post-fan stamp ordering). #106/#115 merged; #108-#111/#113/#114/
  #116-#120 unchanged. The VT-022/VT-023 numbers belong to the fix
  lanes' own discoveries this window (the piston entity gate, the
  flight band) — their records ride their branches; the next union
  folds them in.


## The thirty-ninth pass, fourth act — VT-023's cross-tree verification (the flight band's two grazing edges, seated and run on the fix tree)

The flight-band fix (**PR #128**, `fix/burning-arrow-flight-band-parity-audit`
`60bf1662` — the lane that elevated VT-019's named tail (b) to its
own finding) was verified through this lane's own quantified
constructions (`BurningArrowFlightBandDiscoveryTest`, the full-boot
deterministic one-tick flights isolating the band window alone — the
arrow clears the TNT's column before gravity can carry it into the
cell, the sample consult never firing):

- **Seat 1 — the FALSE PRIME** (the flight at post-step `y=41.15`
  over a TNT at cell 40): **RED on develop** (the TNT GONE — the
  symmetric band's lower edge 40.901 reaching a half-block below the
  reference's box bottom), **GREEN on the fix tree** (the feet-law
  band [41.151, 41.649] covers cell 41 alone — the TNT stands).
- **Seat 2 — the MISSED PRIME** (the flight at post-step `y=41.55`
  under a TNT at cell 42): **RED on develop** (the TNT still STANDS —
  the symmetric band's top 41.799 covering cell 41 alone), **GREEN on
  the fix tree** (the feet-law band [41.551, 42.049] touches cell 42 —
  the prime fires, the block leaves).

Two independent constructions, both edges, one law — the review note
posted on the PR with the construction details. The PR's own record
(the column-crossing helper, the #123-union verification, the
regression lanes) agrees with this lane's reading on every point of
the law.


## The thirty-ninth pass, fifth act — the stick tick's resting-box scan SEATED (VT-019's named tail (a), the uncarried slice): RED on develop AND on the #123+#128 union

The #123 review note named the stick-tick scan as the uncarried
residue; this act seats it (`ArrowStickTickTntScanDiscoveryTest`, the
placement-independent full-boot construction): a burning arrow's
one-tick flight (velocity `(0.3, 0, 0)`, the spawn at `(59.85, 41.7,
51.5)`) sticks in the stone at `(60, 41, 51)` with the TNT at `(60,
42, 51)` atop it — the rest box's upper quarter holds the TNT's cell
under BOTH rest geometries (the develop endpoint `41.65` and the
VT-014-fixed `41.683` both box to cells {41, 42}), outside the band
under both, and never touched by the flight samples (cell 41 alone).

- **RED on develop** (the symmetric band's top 41.899 a half-block
  short of the box's top).
- **RED on the #123+#128 union** (the scratch union merged and
  resolved this act — the javadoc conflict both carriers' edits
  create): the gate skips the stuck body, stick tick included, and
  the feet-law band runs for flying bodies alone. The union is the
  truthful composite of the two open carriers — the residue lives
  there.
- **#128 alone would answer GREEN for this construction — by
  ACCIDENT**: its un-gated feet-law band at the ENDPOINT rest covers
  cell 42 here, but the endpoint is not the reference's rest (the
  face−0.05 composition is), so the coverage is construction-specific,
  not the law. The gate (#123) removes even the accidental coverage.

**The reference's law, the seat's record**: the stick tick IS a
flight tick — the stick arm sets `inGround` (ArrowEntity 288-309),
the tail integration lands the rest (328-330), `setPosition` (381),
then `checkBlockCollisions()` (382) scans the REST BOX `[y, y+0.5]`
on the very stick tick — the LAST consult the reference ever makes
for that arrow. The slice's fix shape: the flight walk's landing arm
(the stick tick) consults the band at the rest position once, with
the feet-law window, before the stuck body exits the flight walk —
the reference's own structure. Its own VT number is owed at the fix
(the VT-023 precedent: a named tail elevated when its carrier opens).

**THE #128 UNION-CLAIM CORRECTION** (the review's honest note): the
#128 commit message claims "UNION with #123: merge verified clean" —
against the CURRENT #123 head (`1a5e2059`) the merge CONFLICTS (both
carriers rewrote `burningTntPass`'s javadoc: the gate's flying-only
law and the feet-law note). The resolution is trivial (this act's
scratch union carries it: the two notes compose in the one javadoc),
but the claim is stale — the review notes on both PRs should carry
the correction so the maintainer expects the one-hunk conflict.


## The thirty-ninth pass, sixth act — PR #122 verified through the four independent placement seats (the maintainer's #117-vs-#122 construction pick, both carriers now equivalently seated)

The b-lane's box-view construction (`fix/arrow-stick-placement-parity-audit-b`
`237611d3`) was run through the four independent placement seats (the
pass-33 lane's launch-based pair + the pass-34 lane's
spawnRestored-deterministic pair, both RED on develop at their
predicted geometries): **4/0 GREEN** — the identical set PR #117
passed in the pass-38 lane's verification. The two VT-014 carriers
are now equivalently seated; the pick between the rayTrace cell walk
(#117) and the box-view construction (#122, additionally carrying
VT-021's slab face) is a style/maintenance choice, not a coverage
one. The three un-stick seats run RED on #122's tree — its documented
scope boundary (the placement alone; VT-015's carriers #119/#120).
The review note posted.

**The pass's tally**: the VT-020 collision resolved (the public-first
protocol, the renumber to VT-021 adopted by the b-lane's PR #122);
VT-019 and VT-018 CONFIRMED whole (three passes each, the seats and
the independent walks); VT-023 cross-verified (both grazing edges);
the stick-tick residue seated (RED on develop and the #123+#128
union, the uncarried slice); five PRs cross-tree verified with the
review notes posted (#122, #123, #124, #128, and the union-conflict
correction on #123/#128); seven new seats on this lane's branch (the
VT-019 pair, the VT-018 full-boot pair — renamed per the
file-resolution protocol, the VT-023 pair, the stick-tick seat).
