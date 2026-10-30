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

## VT-004 — the portal stand clock re-fires while the body stays pinned in the arrival frame (UNDER VERIFICATION)

**Subsystem:** portal mechanics (the stand clock / the crossing's re-fire
law). **Status:** under verification — two passes recorded (the source
and the behavioral, both this run); the independent re-verification owed.

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

**Pass 3 (independent re-verification) owed:** the
compensating-behavior hunt — whether any other gate holds the re-fire
(grace ticks, the wire's respawn sequence, the forcer's cache), whether
`DimensionWalkTest`'s round-trip expectations pin the divergence as
intentional, and the edge cases (the creative 0-tick stand; the
vehicle-riding gate; the End pair's instant arm, which is a different
law).

**Proposed minimal correction (once verified):** the reference's own gate
shape — the standing clock consults the cooldown (`inPortal` armed only
when the cooldown is expired; the cooldown re-arms on every collision
while cooling) — plus the stand clock's decay across the crossing. The
`DimensionWalkTest` round-trip seat would need re-pinning to the
reference's law (the body must walk out and back in for the return).

**Confidence:** medium-high on the source reading (the reference's gate is
unambiguous); the engine's intent claim ("the reference's own shape")
makes the independent pass especially important before any correction.

## Coverage ledger (updated by the twenty-second pass)

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
| Portal mechanics (flag1, forcer) | landed by FL4-F1; not re-audited — the stand clock's re-fire law is VT-004 (UNDER VERIFICATION, two passes; the seat PortalRefireDiscoveryTest is the record) |
| Drops — the TF-9 dimension stamp | LANDED by PR #90 (the dig/place walks stamp `world.dimension()`; the tile-family spill sites remain the per-dimension tile tail) |
| Tile families — per-dimension stores | NOT INSPECTED as its own pass (the PR #90 ledger: the furnace/chest/skull/banner spill + cleanup ride the single-instance managers — the FL4 fluids/fire precedent applies; the nether tile placement/cleanup is its own architectural pass) |
| Mob spawns — the dimension stamp | NOT INSPECTED (the PR #90 ledger names `MobManager.spawnAt`; the infested burst is the surfaced site) |
| FX broadcasts — the dimension awareness | NOT INSPECTED (the FxManager position-only API; the PR #90 ledger) |
| World border — per-dimension | NOT INSPECTED (the reference's per-World border vs the engine's shared one; surfaced by the dig-start gate review) |
| Zombie/mob arms — world binding | NOT INSPECTED (the zombie door-break consults the overworld field; surfaced by PR #90's cleanup threading) |
| Explosions — dimension routing | not inspected (secondary until the dimension surface lands; the crystal arm's dimension gate landed with PR #85's rider) |
| Item entities / pickup — dimension | the TF-9 stamped sites are the record; the pickup consult un-audited

## The queue for the next pass

1. ~~The VT-003 fix branch~~ — LANDED (PR #89, `IceFamilyParityTest` 11/0,
   the stash verification).
2. ~~The VT-001 fix branch~~ — LANDED (Wave FL4-F2 on develop, the fire
   family's per-dimension scheduling).
3. ~~The VT-002 bounded first slice~~ — LANDED (Wave FL4-F3 took the
   family arms; PR #90 took the remaining dig/place GENERIC surface,
   `DigPathDimensionParityTest` 4/0).
4. **VT-004's verification pass 3** (the portal stand clock's re-fire —
   passes 1 and 2 landed this run: the source reading and the
   deterministic behavioral seat
   `PortalRefireDiscoveryTest`, RED on develop by contract): the
   independent re-verification (the compensating-behavior hunt; the
   `DimensionWalkTest` round-trip seat's own expectations; the creative
   / vehicle / End-pair edges).
5. The explosion-path dimension audit (the ledger row above — now
   unblocked: nether placement exists post-PR #90).
6. The standing lanes from the earlier passes, untouched by this one:
   F-TC1 (the trapped chest's viewer signal — PR #87 open) and F-EC1
   (the ender chest's solid-above gate, suspected).
7. The newly-ledgered dimension tails (each its own discovery pass, the
   PR #90 ledger): the per-dimension tile families, the mob spawn's
   dimension stamp, the FX dimension awareness, the per-dimension world
   border, the zombie arm's world binding.
