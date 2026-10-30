# The fluid family audit findings — the liquid-blocking consult's material law

The seventeenth verifier pass's fluid-family audit (the compatibility
matrix's own standing note: "spread order audited loosely"). Anchored on
`reference/1.8.8 net/minecraft/block/LiquidBlock.java` (341 lines) +
`FlowingLiquidBlock.java` (223) + `LiquidSourceBlock.java` (77) +
`block/material/Material.java` + the four subclasses + `Blocks.java` +
the per-block constructors, against the engine's `FluidSystem.java` (837)
+ `FluidBlocks.java` (353) + `WorldSolidity.java` + `MaterialSolidity.java`
+ the `EngineServer` wiring (lines 2009-2016, 23313-23367).

## Verified 1:1 (no action)

The Wave VI migration's core is faithful — re-verified line by line this
pass: the tick cadences (water 5; lava 30/10 over the no-sky consult —
`FluidBlocks.cadenceOf`), the decay band (`j`: water 1, lava 2 in a
water-keeping dimension, 1 where water vaporizes), the LEVEL 0-15 nibble
with the falling 8-15 above-depth, the whole recomputation walk (the
horizontal depth walk with `getLowestDepth`'s source count, the death at
`l + j >= 8`, the falling inheritance from above with its `+ 8`
conversion, the two-source infinite-water birth over solid-or-source
ground), the lava slow-creep roll (`i1 > i` and `nextInt(4) != 0` →
`k *= 4`), the `i1 == i` equilibrium's still settle and the level-0
source settle, the down-spread's `i | 8` law and the lava-into-water
STONE arm, `getSpread`/`getDistanceToGap`'s four-deep gap-seeking walk
with the reference's own down-cell argument quirk, `spreadTo`'s wash-out
drops (fortune 0 through the `DropService`) and the lava fizz burn, the
contact bands (obsidian at LEVEL 0, cobblestone at LEVEL 1-4, the down
neighbor never converting), the wake path's flag semantics (the flags-2
quiet writes, the explicit `updateNeighbors` after a level change), the
source form's `convertToFlowing` on neighborChanged, and the liquid
random-tick wiring (the reference's `Block.randomTick` → `tick`
delegation, Block.java lines 434-436; the engine's gate — flowing forms
both kinds, the lava source/still forms, the water source never —
matches `setTicksRandomly`'s own band exactly, RandomTickSystem lines
279-296).

## F-FL1 — the liquid-blocking consult reads the collision model (CONFIRMED, fix-ready)

**Reference** (`FlowingLiquidBlock.isLiquidBlocking` lines 185-193):

```java
return block instanceof DoorBlock
    || block == Blocks.STANDING_SIGN
    || block == Blocks.LADDER
    || block == Blocks.REEDS
    || block.material == Material.PORTAL
    || block.material.blocksMovement();
```

**Engine** (`FluidSystem.isLiquidBlocking` lines 595-605): the explicit
rows are right (door, standing sign, ladder, cane, both portals), but the
final arm reads `WorldSolidity.isSolid(type)` — the ENTITY-COLLISION
model — where the reference reads `material.blocksMovement()` — the
MATERIAL model. The engine's own correct consult
(`MaterialSolidity.blocksMovement`, built by the twelfth pass for the
sign family and extended by Wave VN-B for the vines) is never consulted
here. `MaterialSolidity`'s own javadoc names the distinction: "the
MATERIAL model, not the collision model (`WorldSolidity` answers a
different question: what blocks a body)."

The material law (`Material.java` + subclasses): the base blocks
movement; `AirMaterial` (AIR, FIRE), `LiquidMaterial` (WATER, LAVA),
`PlantMaterial` (PLANT, REPLACEABLE_PLANT, DECORATION, CARPET,
SNOW_LAYER) and `PortalMaterial` (PORTAL) override to false; the
COBWEB's anonymous subclass overrides `blocksMovement()` alone.

**The concrete divergence cells** (each verified against the reference's
constructor registrations):

| Cell | Engine (collision model) | Reference (material) | Observable |
|---|---|---|---|
| Open wood doors — all six families (`oak/spruce/birch/jungle/acacia/dark_oak_door_*`, every open state incl. uppers) | washable (`isOpenDoorHalf` carves them out of `isSolid`; `isDoor` does not match them) | `DoorBlock instanceof` — always blocking | flowing water DESTROYS an open oak/spruce/birch/jungle/acacia/dark_oak door (the item pops); vanilla flows around every door state |
| Wall signs (`wall_sign*`) | washable (`isSign` carve) | `SignBlock` material WOOD | water destroys wall signs; vanilla flows around them |
| Banners (`standing_banner*`, `wall_banner*`) | washable (`isBanner` carve) | `BannerBlock` material WOOD | water destroys banners |
| Open fence gates | flow-through (`isOpenFenceGate` carve) | `FenceGateBlock` material WOOD — state-independent | water flows through an open gate and washes it |
| Stone + wooden pressure plates | washable (`isRedstoneComponent` carve) | `PressurePlateBlock(Material.STONE/WOOD)` — blocking (the weighted pair's IRON too) | water destroys stone/wooden plates (the weighted pair happens to match) |
| Flower pots (`flower_pot`, `potted_*`) | blocking (no carve) | `FlowerPotBlock` material DECORATION | water never enters a pot cell; vanilla washes pots |
| Skulls (`skull`, `skull_down`, `skull_*`) | blocking (no carve) | `SkullBlock` material DECORATION | water never enters a skull cell; vanilla washes heads |
| Moving piston carriers (`moving_piston_*`) | flow-through (`isMovingPiston` carve) | `MovingBlock` material PISTON | water enters the two-tick carrier cell |

**The door consult's second defect — the stale names**
(`FluidSystem.isDoor` lines 608-615): the consult matches
`wooden_door`/`wooden_door_*`/`iron_door`/`iron_door_*`. The engine's
real door ids are the seven `WoodDoorFamily` families
(`oak_door_open_north`, `spruce_door_upper_open`, ... — WoodDoorFamily
lines 71-95); `wooden_door*` matches NO registered id. Only the iron
family (`iron_door_*`) is covered. The closed wood states stay blocking
by accident (the collision arm answers solid for closed halves); every
OPEN wood state is lost.

**The existing seat's blind spot**: `FluidFlowTest`
.`theOpenDoorBlocksTheFlowDespiteBeingWalkThrough` (lines 373-384) pins
the consult against a SYNTHETIC unregistered type —
`Identifier.parse("minecraft:wooden_door_open") == null ? STONE :
lookupDoorOpen()` constructs a fake `wooden_door_open` block type that
satisfies the stale prefix but never touches the real registry. The
seat passes while every real open wood door washes away. Re-pinned to
the real ids by this pass's fix.

## F-FL2 — MaterialSolidity's DECORATION holes: the skull + the flower pot (CONFIRMED, fix-ready)

`MaterialSolidity.isSolid`'s DECORATION set covers the ladder, the
rails, the tripwire family, the torch, and `isDecorationRedstone` (the
wire, the redstone torches, the repeaters, the comparators, the levers,
the buttons) — but NOT the skull or the flower pot, both
`super(Material.DECORATION)` in the reference (SkullBlock /
FlowerPotBlock constructors). They read material-solid in the engine.
This is the same root cause's second face: swapping F-FL1's consult
alone would leave pots and skulls blocking (the inverse error), so the
fix extends the DECORATION set with the two rows. The extension also
corrects the consult's other consumers (the sign family's placement
gates read `MaterialSolidity.isSolid`; the vine family's `canGrowOn`
reads `blocksMovement`) — a vine never grows on a skull or a pot, and a
standing sign never stands on one, exactly like the reference.

## F-FL3 — the fizz emission's pitch + position (CONFIRMED, separate PR)

**Reference** (`LiquidBlock.fizz` lines 296-305):
`playSound(x + 0.5, y + 0.5, z + 0.5, "random.fizz", 0.5F, 2.6F +
(world.random.nextFloat() - world.random.nextFloat()) * 0.8F)` — the
block CENTER, the 2.2-3.0 pitch band.

**Engine** (`FluidSystem.fizz` lines 801-803):
`sink.sound(new Position(at.x(), at.y(), at.z()), "random.fizz", 0.5f,
1.0f)` — the raw corner, a fixed 1.0 pitch. The house convention for
block sounds is the `+0.5` center (EngineServer lines 10116, 11425+).
The 8 smoke particles are a NON-ROW per the F-DUST adjudication (the
dedicated server's `addParticle` is the empty stub — client-side
prediction). Fix: the pitch roll over the system's own `Random` + the
center position. Different root cause (the emission, not the gate) —
its own PR.

## F-FL4 — the nether and the End carry no fluid simulation (CONFIRMED, architecture-scoped — documented, not fixed this pass)

The engine wires ONE `FluidSystem` to the overworld: the listener on
`world` (EngineServer line 2015), the seam registration on the
overworld's `ScheduledTickSystem` (lines 2012-2014), and the
random-tick liquid arm through the overworld's `RandomTickSystem`
(line 2079). The `netherWorld` and `endWorld` receive only
`PublicBlockChangeBridge` + `TilePlacementListener` (lines 762-763,
788-789) — no scheduled-tick system, no random-tick system, no fluid
listener. The `FluidWorld` adapter's `yeetsWater()`/`hasNoSky()` default
false (lines 23313-23335, no overrides).

**Vanilla**: the nether's lava flows — tick rate 10
(`getTickRate`'s no-sky band), full seven-block reach (`j = 1` where
water vaporizes — NetherDimension line 14's `yeetsWater = true`); the
End's water flows at cadence 5 (`yeetsWater` false) and the End's lava
at cadence 10 (`TheEndDimension` line 16's `noSky = true`). In the
engine, nether lava sits inert beside a broken block forever; End water
poured by bucket never spreads.

This is the engine's known single-mechanics-world shape (the house
precedent: Slice 62's "the End carries no fire-tick listener" and "the
tnt-in-End gap, ledgered" notes) — but the fluid family's dimension gap
was never ledgered (the compatibility matrix's fluids row carries no
dimension caveat). The bounded correction needs per-dimension
scheduled/random-tick infrastructure first (the nether/End have none of
it), then a per-dimension `FluidSystem` pair with the dimension profile
(`yeetsWater`/`hasNoSky`) wired from the world. Recorded here as the
family's standing tail; the correction belongs to a dedicated
architecture pass, not this focused PR.

## The fix plan (this pass's PR)

One root cause — the liquid-blocking gate's material law — fixed whole:

1. `FluidSystem.isLiquidBlocking`'s final arm: `WorldSolidity.isSolid`
   → `MaterialSolidity.blocksMovement` (the material model).
2. `FluidSystem.isDoor`: the seven engine door families
   (`WoodDoorFamily.FAMILIES`), every state — the reference's
   `DoorBlock instanceof` law.
3. `MaterialSolidity`: the skull + flower pot rows join the DECORATION
   set (F-FL2).
4. The existing door seat re-pinned to the REAL registry ids; new seats
   for every divergence cell (the wall sign, the banners, the open gate,
   the plates, the pot, the skull — both the blocking and the washable
   sides), each asserting the reference's law.

## Verification history

- **Pass 1 (source, 2026-10-30)**: the reference's liquid family whole
  (the three classes + the Material hierarchy + the per-block
  constructor registrations + the dimension flags); the engine's
  FluidSystem/FluidBlocks/WorldSolidity/MaterialSolidity whole + the
  door/sign/banner/pot/skull registrations + the EngineServer wiring.
  Every divergence cell double-checked against the reference's material
  row and the engine's consult set. The verified-1:1 list above
  re-confirmed.
- **Pass 2 (behavioral, 2026-10-30)**: FluidFlowTest over the
  direct-driven window — six new seats on the REAL registry ids (the
  open wood doors of all six families + the open upper, the wall sign +
  both banner forms, the open fence gate + the stone/wooden plates, the
  flower pot + skull wash-out, the moving piston carrier) and the fizz
  seat (the center + the volume + the 1.8-3.4 pitch band + the seeded
  determinism). THE STASH VERIFICATION: the material fix stashed —
  EXACTLY the five material seats fail (the water taking the open
  gate/door/wall-sign/banner/carrier cells, refusing the pot/skull
  cells); the fizz fix stashed — EXACTLY the fizz seat fails (the
  raw-corner position). Green whole with each fix (19/0, then 20/0).
- **Pass 3 (independent re-verification, 2026-10-30)**: the execution
  path re-walked from the wash arm (the `DropService.washedDrops`
  consult through the `FluidSink`), the compensating-behavior hunt (the
  §27.7 flow gate admits everything by default; no other consult
  protects the open doors/banners/pots), the
  intentional-vs-defect question (the engine's own comment claimed the
  solidity rule "carries the reference's material consult" — a false
  claim, no documented intentional divergence; the §27.7 gate is the
  engine's own documented addition and stays untouched), and the
  consumer-impact audit of the MaterialSolidity extension (the sign +
  banner support arms, the farmland solid-above arm, the sign/banner
  placement gates — every consumer shifts TOWARD the reference; the
  regression lanes confirm nothing pinned the old wrong answers).
  Regressions: the block package whole 308/0 (309/0 with the fizz
  seat); the family lanes 99/0 (banner, decoration, piston, sign,
  skull, redstone, FarmlandTrample, VinePlacementDispatch,
  Decorations, PistonPush); the consumer lanes 28/0 (BuildingBlocks,
  BlockInteraction, Bonemeal, CaneAndCactus, PistonFluid,
  AnvilImportApi); the anvil/tree/worldgen lanes 85/0; the
  ItemOrbLavaEnvelope lane 8/0 (the OTHER random.fizz site — the item's
  own, untouched). The full-suite single window exceeds the 10-minute
  tool limit (the house's own precedent); the targeted batches are the
  recorded evidence.

## Standing tails

- F-FL3 (the fizz emission) — its own PR this pass.
- F-FL4 (the nether/End fluid simulation) — the architecture pass
  (per-dimension scheduled/random-tick infrastructure first).
- The `getFlow` current consult for entity drag — the travel audits'
  territory (the compatibility matrix's swimming rows), not re-audited
  here.
