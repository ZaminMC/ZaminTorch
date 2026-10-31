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

## VT-006 — the engine's non-player entity families carry no portal walk: mobs (and items, orbs, boats, carts, arrows) standing in portal cells never cross (CONFIRMED — three passes; the fix is a substantial architectural walk, the next run's head)

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

## VT-007 — the blast family's dimension routing is partial: the fireball, skull, creeper, wither-birth, and TNT-cart detonations still bind the overworld, and the blast drop stamp carries dimension 0 (RESOLVED-BY-PR — **PR #103 OPEN**)

**Subsystem:** explosions / dimension routing (the queue's named audit)
**Affected version:** the current develop baseline (deef2a17)
**Status:** RESOLVED-BY-PR (three passes — the source census, the
executed behavioral seats, the independent pass with the FL4-F7
precedent, the compensating hunt, and the wire-level consequence; the
correction landed the same run as **PR #103**)

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
The correction LANDED the same run — RESOLVED-BY-PR — PR #103 OPEN**
(`fix/explosion-dimension-routing-parity-audit` based on `deef2a17`:
the FL4-F7 idiom at each site — the two projectile sinks consult
`explosionServiceFor(dimension)`, `detonateAtFromMob` threads the
source mob's own dimension, the TNT cart's `BlastSink` carries the
dimension with the factory routing through the resolver, the drop
roll stamps the blast's dimension through the TF-9 four-arg form;
the two dead overworld-hardcoded wrappers retire in the same sweep;
the stale javadoc corrected. The seats 2/0 GREEN on the fix branch —
seven consecutive runs — with the construction hardened on the fix
branch: the nether HEADROOM carve keeps the creeper's spawn band
clear of the nether's seed-drifting natural terrain, one observed
precondition flake root-caused to that terrain roll. The regression
lanes: the explosion family, CreeperRows, NetherTntRouting,
BlastResistanceTable, the vehicle family whole (the signature's
three test implementations updated), the tnt + endcrystal packages,
the nether trio + DimensionWalk + DimensionTimeSync +
PerDimensionFire + PerDimensionFluid 24/0, the block + world
packages 555/0. One pre-existing load flake retired from
consideration: VehicleWorldDamageTest's lava seat, once under the
parallel lane load, passing alone on BOTH trees, the diff touching
no fire path.)

## VT-008 — the bed's out-of-dimension blast arm is missing: the sleep walk carries no dimension gate (UNDER VERIFICATION — pass 1 recorded)

**Subsystem:** block use / the bed family / dimension gating
**Affected version:** the current develop baseline (deef2a17)
**Status:** under verification (pass 1, the source pass, recorded below)

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

- **Pass 1 (source, this run, 2026-10-31):** BedBlock.use lines 45-84
  read in full — the gate's conjuncts, the else arm's
  remove-both-halves order, the explode call's power/fire/smoke
  flags; the engine's `sleepOnTick` + the use dispatch read — no
  dimension consult anywhere on the path. CONCLUSION: the arm is
  absent at the source level.
- Pass 2 (behavioral): owed — the seat needs a nether-anchored
  session (the portal walk construction); deferred to the next run
  exactly as VT-006's arrow seat was (the source evidence stands).
- Pass 3 (independent): owed.

## Coverage ledger (updated by the twenty-sixth pass)

| Area | Status |
|---|---|
| Fire family — overworld tick/spread/burn (FireBlocks vs FireBlock) | inspected, verified 1:1 on the overworld path (the fourteenth-pass-era migration); the per-dimension arm LANDED by Wave FL4-F2 (VT-001 resolved) |
| Fire family — nether/End scheduling | LANDED by Wave FL4-F2 (the VT-001 correction; `PerDimensionFireAcceptanceTest` is the record) |
| Fluid family — per-dimension simulation | landed by FL4; not re-audited (the FL4 seats are the record) |
| Dig path — dimension routing | LANDED by PR #90 (the VT-002 remaining slice; `DigPathDimensionParityTest` 4/0) |
| Use/place families — dimension routing | the family arms LANDED by Wave FL4-F3; the generic surface LANDED by PR #90 |
| Generic placement (placeOnTick/survivalPlaceOnTick) — dimension routing | LANDED by PR #90 |
| Ice family — break-to-water, melt | PR #89 open (VT-003's correction) |
| Snow layer — placement support/melt/drops | drops landed (the mining-speed audit); melt + the 7-layer state surface recorded as VT-003's tail |
| Packed ice — melt/break laws | inspected: the reference's PackedIceBlock carries no melt arm (the `setTicksRandomly` false, the silk-drop row landed); nothing owed |
| Portal mechanics (flag1, forcer) | landed by FL4-F1; not re-audited — the stand clock's re-fire law is VT-004 (CONFIRMED, three passes; the correction is **PR #91 OPEN**; the seat PortalRefireDiscoveryTest rides green on the fix branch) |
| Portal mechanics — the non-player crossing (the entity-level arm) | the MOBS family LANDED by **PR #98** (VT-006's correction: the entity-level crossing service + the mobs family — the gated collision, the threshold-0 clock, the 300 cooldown with the re-arm pin, the 8:1 walk, the forcer's placement, the stamp swap, the per-dimension wire; `PortalMobCrossingDiscoveryTest` 3/0 with the cooldown and riding-gate pins). The REMAINING families (items, orbs, boats, carts, arrows) ride the `DimensionCrossing` seam in their own passes — the behavioral seats authored (RED on develop BY CONTRACT, the twenty-fifth pass); the TNT and falling families excluded by the census; the End-origin arm is the named tail |
| Bed use — the out-of-dimension blast arm | AUDITED this pass (the twenty-sixth): the sleep walk carries no dimension gate — a nether/End bed sleeps where vanilla blasts (**VT-008, UNDER VERIFICATION** — pass 1 recorded; the seat deferred) |
| Portal mechanics — the riding gate (vehicle == null && rider == null) | VT-005 CONFIRMED (three passes — source, the pig-in-portal behavioral seat, the dismount-control independent pass; the correction rides `fix/portal-riding-gate-parity-audit`, PR #94) |
| Drops — the TF-9 dimension stamp | LANDED by PR #90 (the dig/place walks stamp `world.dimension()`; the tile-family spill sites remain the per-dimension tile tail) |
| Tile families — per-dimension stores | NOT INSPECTED as its own pass (the PR #90 ledger: the furnace/chest/skull/banner spill + cleanup ride the single-instance managers — the FL4 fluids/fire precedent applies; the nether tile placement/cleanup is its own architectural pass) |
| Mob spawns — the dimension stamp | NOT INSPECTED (the PR #90 ledger names `MobManager.spawnAt`; the infested burst is the surfaced site) |
| FX broadcasts — the dimension awareness | NOT INSPECTED (the FxManager position-only API; the PR #90 ledger) |
| World border — per-dimension | NOT INSPECTED (the reference's per-World border vs the engine's shared one; surfaced by the dig-start gate review) |
| Zombie/mob arms — world binding | NOT INSPECTED (the zombie door-break consults the overworld field; surfaced by PR #90's cleanup threading) |
| Explosions — dimension routing | AUDITED this pass (the twenty-sixth): the call-site census whole — the primed TNT (FL4-F7) and the End crystal (Slice 62) correct; the ghast fireball and wither skull sinks pass the routed world with the OVERWORLD service, the creeper/wither-birth/TNT-cart wrappers bind the overworld outright, and the shared drop roll stamps dimension 0 (**VT-007 RESOLVED-BY-PR #103** — three passes: the census, the executed behavioral seats RED stable across three runs, the independent pass with the FL4-F7 precedent + the wire-level desync; the correction landed the same run: the five sites routed + the drop stamp + the dead wrappers retired, the seats 2/0 green seven consecutive runs, the regression lanes green whole). The blast WOUND walks are routed (the dimension gate + the blastWorld consult); the fire placement is routed (F-EX4/PR #92); the named tail: the End/nether services' shared `gameplayRandom` vs the reference's per-world `world.random` |
| Item entities / pickup — dimension | the TF-9 stamped sites are the record; the pickup consult un-audited

## The queue for the next pass

1. ~~The VT-003 fix branch~~ — LANDED (PR #89, `IceFamilyParityTest` 11/0,
   the stash verification).
2. ~~The VT-001 fix branch~~ — LANDED (Wave FL4-F2 on develop, the fire
   family's per-dimension scheduling).
3. ~~The VT-002 bounded first slice~~ — LANDED (Wave FL4-F3 took the
   family arms; PR #90 took the remaining dig/place GENERIC surface,
   `DigPathDimensionParityTest` 4/0).
4. ~~VT-004's verification pass 3~~ — DONE (the twenty-third pass: the
   compensating-behavior hunt found nothing — the dispatch cadence is a
   per-tick bounding-box scan, the reference's placement lands INSIDE the
   cells, `DimensionWalkTest`'s round trip is the misread's symptom;
   VT-004 CONFIRMED). ~~The VT-004 fix branch~~ — **PR #91 OPEN** (the
   reference's gate shape in `advancePortalClock`; the DimensionWalk
   round-trip re-pin; the discovery seat rides green; the world package
   248/0 + the block lane 309/0).
5. ~~VT-005's behavioral + independent passes~~ — DONE (the
   twenty-fourth pass: the pig-in-portal seat's timeline — the clock
   accumulated to 80 while mounted and the crossing fired at t80 with
   `riding` held through the window; the dismount control — the carried
   clock 44 into the fresh window; the compensating hunt — nothing;
   VT-005 CONFIRMED). ~~The VT-005 fix branch~~ — **PR #94 OPEN** (the
   riding consult on the nether arm's `inPortal` computation; both
   discovery seats ride green on the fix branch).
6. ~~VT-006's independent pass~~ — DONE (the twenty-fourth pass's
   second act: the family census — the crossing families are the mobs,
   the items, the orbs, the boats, the carts, the arrows; the TNT and
   falling families carry NO portal arm in the reference, their omission
   an accidental match the fix must not over-correct; the nether-origin
   trace — no setDimension site ever moves a live body; the engine's own
   javadoc ledgers the gap). VT-006 CONFIRMED. ~~The fix — the mobs
   family first~~ — **PR #98 OPEN** (the twenty-fifth pass: the
   entity-level crossing service + the mobs family on
   `fix/portal-entity-crossing-parity-audit` based on `deef2a17`; the
   seats 3/0 with the cooldown and riding-gate pins; the portal+mob+world
   lanes 271/0 + the block+interaction lanes 313/0; the DimensionWalk
   seed-flake root-caused and recorded — same-seed control proves the
   placement identical). **The remaining crossing families behind the
   seam — the items, the orbs, the boats, the carts, the arrows (each
   confirmed by the census; the behavioral seats authored, the
   twenty-fifth pass) — remain BLOCKED on PR #98's merge (the
   `DimensionCrossing` seam's dependency).**
7. ~~The explosion-path dimension audit~~ — DONE (the twenty-sixth pass:
   **VT-007 CONFIRMED** over three passes — the census (five divergent
   sites + the drop stamp), the executed behavioral seats (RED on
   develop stable: the nether 0 holes / the overworld 4-24, both
   families), and the independent pass (the FL4-F7 precedent naming
   the shape as the engine's own recognized defect, the empty
   compensating hunt, the wire-level desync of BOTH dimension views).
   ~~The fix branch~~ — **PR #103 OPEN** (the same run: the FL4-F7
   idiom at each site, the BlastSink dimension carry, the drop stamp,
   the dead wrappers retired; the seats 2/0 green seven consecutive
   runs; the regression lanes green whole). **The next run's head:
   VT-008's passes** (the bed's blast arm — its pass 2 seat with the
   nether-anchored session, its pass 3; the fix composes with PR
   #103's routed service), then the queue below.)
8. ~~The standing lanes~~ — F-EC1 RESOLVED-BY-PR (the twenty-fourth
   pass: confirmed over three passes and corrected — **PR #95 OPEN**, the one-consult
   swap to `BlockSolidity.isSolid`; F-TC1 was already resolved by PR
   #87's merge). The F-SP1 support-consult family and F-DS1 (the double
   slab's collision row) remain suspected from the twentieth pass's
   record — their passes owed.
9. The newly-ledgered dimension tails (each its own discovery pass, the
   PR #90 ledger): the per-dimension tile families, the mob spawn's
   dimension stamp, the FX dimension awareness, the per-dimension world
   border, the zombie arm's world binding.
10. The watch items: the open PRs #91/#94/#95/#98/#103 awaiting the
   maintainer's review; the FL4-F10 redstone world binding — the house
   stream's own claimed next act (its trace and six-point plan recorded
   at `99f1718b` on develop) — NOT to be duplicated by this stream.
