# The hopper & container-family audit — the eighteenth verifier pass's discovery

Date: 2026-10-30 (the eighteenth continuous verifier pass). Branch:
`audit/verifier-pass-18-discovery` (off `origin/develop` @ `0a89a0fe`).
Scope entered from the pass-17 handoff with the queue empty: the hopper
transfer family whole (`HopperSystem` + `HopperBlockEntity` +
`HopperBlocks` + `HopperMinecartEntity` + the container consults), plus
the container consults' chest side, which the hopper's `getInventoryAt`
port drags in. The reference: `reference/1.8.8/` —
`block/entity/HopperBlockEntity.java`, `block/HopperBlock.java`,
`entity/vehicle/HopperMinecartEntity.java`, `block/ChestBlock.java`,
`block/DropperBlock.java`, `entity/EntityFilter.java`,
`world/World.java` (`canPlace` lines 2274-2282). Cross-checked against
the real MCP 1.8.8 decompile (Build 918 = 1.8.8,
`TileEntityHopper.java`, `BlockHopper.java`, `EntityMinecartHopper.java`,
`EntityItem.java`) — fetched 2026-10-30 to settle the suck-band geometry
one-for-one; every cross-checked line agreed with the supplied
reference (the `getYPos() = pos.y + 0.5D` mouth, the `+ 1.0D` in the
capture call, `EntityItem`'s `motionY *= -0.5D` bounce, the never
reassigned `field_174900_c = BlockPos.ORIGIN`).

---

## F-HP1 — the block hopper's item-suck band sits half a block too low (CONFIRMED)

**Subsystem:** container transfer / item collection. Affected: every
survival item-collection hopper. Status: **confirmed** (three
verification passes below). Severity: M (major gameplay/simulation
mismatch — pickup timing + a false-positive suck vanilla never makes).

**Engine:** `HopperSystem.pullItem` — the item-entity arm, the band
`Math.abs(at.y() - (pos.y() + 1.0)) <= 0.5` (the y containment
`[by+0.5, by+1.5]`; x/z bands `[bx, bx+1]` correct).

**Reference:** `HopperBlockEntity.pullItem` line 323 →
`getItem(world, hopper.getX(), hopper.getY() + 1.0, hopper.getZ())`
(lines 445-447, the Box `x±0.5, y±0.5, z±0.5` around the passed point)
with the Hopper mouth accessors (lines 483-496):
`getX() = pos.x + 0.5`, `getY() = pos.y + 0.5`, `getZ() = pos.z + 0.5`.
The passed y is therefore `by + 0.5 + 1.0 = by + 1.5` and the Box spans
**`[by+1.0, by+2.0]` — the full cell ABOVE the hopper** — tested by
box-intersection with each item's own 0.25-tall box (the real
`World.getEntitiesWithinAABB` consults `entity.getBoundingBox()
.intersectsWith(aabb)`), so the effective item-CENTER band is
**`(by+0.875, by+2.125)`** (strict bounds — `Box.intersectsWith` uses
`<`/`>`).

**The mismatch:** the engine band is half a block low at both ends, and
it is a center-point containment, not a box intersection. Both shifts
move observable behavior:

- **The false-positive side (engine-only sucks):** an item resting in
  the hopper's own funnel mouth — collision slab top 0.625
  (`HopperBlock.addCollisions`: the full-footprint slab
  `(0,0,0)-(1,0.625,1)` + the four rim walls, both sides identical) —
  sits at box `[by+0.625, by+0.875]`, center `by+0.75`. The engine's
  `[by+0.5, by+1.5]` band CONTAINS it: the engine's hopper eats a
  settled mouth item on the very next transfer tick. Vanilla's box
  `[by+1, by+2]` never contains it: a settled mouth item is never taken
  (vanilla catches items only while their box still overlaps the cell
  above — during the descent and the `motionY *= -0.5D` bounce
  (`EntityItem.tick`), which is how a dropped item gets eaten mid-air
  before it ever settles).
- **The false-negative side (engine-only misses):** an item bouncing or
  stacked high in the cell above with center in `(by+1.5, by+2.125]` —
  vanilla catches it, the engine misses it until it settles below
  `by+1.5`.

**The engine's own proof of the slip:** the hopper-CART arm
(`HopperSystem.hopperCartTransfer`) translates the SAME reference row
correctly — `mouthY = y + 0.5` (the reference's `getY()`), then the
band `|at.y - (mouthY + 1.0)| <= 0.5` = `[y+1.0, y+2.0]`. The block arm
wrote `pos.y() + 1.0` directly, dropping the mouth's `+0.5`.

**The shipped seat's blind spot:** `HopperSystemTest
.theHopperSucksTheItemEntityAboveIt` spawns its item via
`spawnDropAtBlock(x, y+1, z)` — the position `(x+0.5, by+1.5, z+0.5)`,
the exact boundary point BOTH bands share (`1.5` is the engine's
inclusive top and the reference box's center). The shipped pin does not
discriminate the two laws.

**Reproduction (the discriminating seats):**
1. *High-band seat:* an item whose center sits at `by+1.75` (in the cell
   above, above the engine's band, inside vanilla's `(by+0.875,
   by+2.125)`): the reference-law engine sucks it; the pre-fix engine
   never does.
2. *Mouth negative:* an item resting in the funnel mouth (center
   `by+0.75`, settled): vanilla never takes it (it is below the box);
   the pre-fix engine takes it.

**Verification passes:**
- **V1 (source, 2026-10-30):** the walk above — the reference chain read
  line-by-line (the mouth accessors, the `+1.0` call, the Box, the
  intersection test), then the real MCP 1.8.8 `TileEntityHopper`/
  `EntityItem` fetched and compared one-for-one: `getXPos/getYPos =
  pos + 0.5D`, `func_181556_a(world, getXPos(), getYPos() + 1.0D,
  getZPos())`, the AABB `±0.5D`, `onGround → motionY *= -0.5D`. Every
  line agreed with the supplied reference. Conclusion: the reference row
  is faithful real 1.8.8; the engine band diverges.
- **V2 (behavioral, 2026-10-30):** the two discriminating seats above,
  run through the stash protocol on the fix branch (see
  `HopperSuckBandParityTest`): the high-band seat fails unfixed (the
  engine never sucks) and passes fixed; the mouth negative inverts
  (sucked unfixed, refused fixed). The band geometry asserted against
  the reference's own bounds.
- **V3 (independent re-verification, 2026-10-30):** challenged the
  hypothesis from the execution path: (a) is the mouth-item suck
  intentional gameplay smoothing? The engine's own comment says "the box
  centered on the block above" — the code contradicts its own comment;
  the cart arm's correct translation of the same row shows the block
  arm is a slip, not a design choice. (b) Is there a compensating
  behavior (an item physics that lifts mouth items back into the
  engine's band)? None: the engine's `ItemEntity` carries no bounce
  lift into `[by+1.5, by+2.125]`, so the high-band miss is real, and
  nothing else consults the mouth cell. (c) The gameplay invariant
  (dropped items ARE collected) holds under the reference law through
  the descent/bounce window — verified by the physics walk, so the
  reference band does not break collection. Confirmed.

**Proposed minimal correction:** one band — the item-entity arm's y
containment moves from `|at.y - (pos.y + 1.0)| <= 0.5` to the box-law
form `at.y - ITEM_HALF > by + 1.0 - ITEM_HALF && at.y - ITEM_HALF <
by + 2.0 + ITEM_HALF` (i.e. the item's FEET box
`[at.y - 0.125, at.y + 0.125]` intersecting `[by+1, by+2]`, strict
bounds, `ITEM_HALF = 0.125`); the x/z containment gains the same
half-width margins (the reference's Box `[bx, bx+1]` intersected by the
item's ±0.125 box → center band `(bx-0.125, bx+1.125)`).

**Regression risks:** low — the band only widens upward (items above
the old band now collect, matching vanilla) and stops the
below-the-top-face suck (the mouth negative). The shipped seat's
boundary spawn (`by+1.5` center) stays inside the fixed band (the box
law's center band tops at `by+2.125`). The `spawnDropAtBlock` y
(`blockY + 0.5`) — a fresh dropped item's first-tick position — stays
inside.

---

## F-HP2 — the container-entity fallback misses the hopper minecart (CONFIRMED)

**Subsystem:** container consult / vehicle inventories. Status:
**confirmed** (three passes below). Severity: M (a whole inventory
entity class is invisible to the transfer family).

**Engine:** the cart fallback is wired chest-only — `EngineServer`
(`hopperSystem.setCartLookup`, the scan at the hopper wiring block)
filters `instanceof ChestMinecartEntity`; `HopperSystem.containerSlotsAt`
consults that lookup for every non-block-container cell (both the
push-target arm and the pull-above arm share it), and the dropper's
push (`pushOneIntoContainer`) shares `containerSlotsAt` too.

**Reference:** `HopperBlockEntity.getInventoryAt` lines 466-472: the
entity fallback is `world.getEntities(null, box, EntityFilter.INVENTORY)`
— `EntityFilter.INVENTORY` = `entity instanceof Inventory && isAlive`
(EntityFilter.java lines 22-26). In 1.8.8 the inventory ENTITIES are
the chest minecart AND the hopper minecart
(`HopperMinecartEntity implements Hopper`, `Hopper extends Inventory`) —
one picked at random (`world.random.nextInt(list.size())`). The real
1.8.8 confirms the filter (`EntitySelectors.selectInventories`,
`entity instanceof IInventory && entity.isAlive()` — the PaperSpigot
patch 0089's own comment pins the same law). `DropperBlock.dispense`
line 42 consults the same static.

**The mismatch:** a parked HOPPER minecart is a push target and a pull
source in vanilla; the engine's consults never see it. Concretely:
1. A block hopper facing into a parked hopper cart never fills it.
2. A block hopper under a hopper cart never pulls from it.
3. A dropper facing a parked hopper cart drops its item on the ground
   instead of inserting it.
4. A hopper cart's own container-above pull misses another hopper cart
   parked above it (the engine's cart arm consults the same lookup).

**Verification passes:**
- **V1 (source, 2026-10-30):** the walk above — both sides read
  line-by-line; the engine's chest-only filter vs the reference's
  `instanceof Inventory`; the engine's own `HopperMinecartEntity`
  exposes its 5-slot `HopperBlockEntity` through `hopper()`, so the
  adapter surface exists and is one lookup away.
- **V2 (behavioral, 2026-10-30):** the stash-discriminated seats on the
  fix branch (`HopperCartTargetParityTest`): a hopper facing a parked
  hopper cart transfers into it (fails unfixed — the item never moves;
  passes fixed); the dropper's insert into the cart (same shape); the
  random pick among TWO carts sharing the cell (one chest cart + one
  hopper cart) seeded and asserted.
- **V3 (independent, 2026-10-30):** the compensating-behavior sweep:
  does anything else already route hopper carts into the block hopper's
  consults? No — `containerSlotsAt` is the sole fallback and its lookup
  is chest-only. Is the hopper cart's own 5-slot surface compatible
  with the slot adapter (no filters, every face, the `markDirty` wake
  through the cart's own listener)? Yes — verified by reading
  `HopperMinecartEntity.hopper()` (a real `HopperBlockEntity`) and the
  container adapter's `hopperSlot` shape. Is the random pick's Random
  consumption order preserved (the reference draws once per consult on
  the world's random)? The engine's cart arm draws from its own
  `Random` field — the engine's established shape (Slice 25e), the fix
  only widens the candidate list, not the draw count. Confirmed.

**Proposed minimal correction:** the lookup in `EngineServer`'s wiring
widens to the inventory-vehicle family — the chest cart and the hopper
cart — each exposing its own slot adapter (the chest cart its 27-slot
`ChestBlockEntity`, the hopper cart its 5-slot `HopperBlockEntity`); the
random pick keeps the existing shape.

**Regression risks:** low — the widened lookup only adds candidates
where the pre-fix consult answered "no container". The chest-cart
lanes keep their exact seats (`HopperSystemTest`'s cart test,
`HopperMinecartTest`).

---

## F-DC1 — the double chest does not exist as an inventory (CONFIRMED — the pass's architecture-scale find)

**Subsystem:** containers / the chest family. Status: **confirmed**
(three passes below). Severity: **H** (a core survival-era mechanic
missing everywhere at once; the oldest merged-container mechanic in the
game).

**Reference (the law, four surfaces + the formation rule):**
`ChestBlock.getInventory` (lines 339-360): the twin scan — the four
horizontal neighbors for `block == this` (plain never joins trapped:
the `this` identity over the two registered `ChestBlock` instances),
`isLocked` on either half blocks the open AND the merge, and the join
order is the reference's own: a WEST/NORTH twin comes FIRST, an
EAST/SOUTH twin SECOND (`DoubleInventory(this, neighbor)` vs
`DoubleInventory(neighbor, this)`). The surfaces:
1. **The GUI** — `ChestBlock.use` line 326: `openChestMenu(getInventory
   (world, pos))`: the merged menu (54 container slots) whenever the
   twin exists.
2. **The hopper/dropper transfers** — `HopperBlockEntity.getInventoryAt`
   lines 460-463: `instanceof ChestBlockEntity && block instanceof
   ChestBlock` → `((ChestBlock)block).getInventory(world, blockpos)` —
   the merged 54-slot inventory is the transfer surface for BOTH the
   hopper's push target and its pull-above source, and for the dropper
   (which consults the same static).
3. **The comparator** — `ChestBlock.getAnalogSignal` line 430:
   `InventoryMenu.getAnalogSignal(this.getInventory(world, pos))` — the
   MERGED pair's fullness.
4. **The placement** — `ChestBlock.canBePlaced` lines 244-283: a chest
   refuses to place beside TWO same-type chests (`i <= 1`; each
   neighboring chest that is itself already doubled refuses outright) —
   no triples/quads; consulted through `World.canPlace` lines
   2274-2282 (`blockx.material.isReplaceable() && block.canBePlaced
   (world, pos, face, item)`).

**Engine (all four surfaces read; the gap is total):**
- The GUI: the open path serves the single 27-slot window — the wire
  layout pins `CHEST_WIRE_SLOT_LAST = 26` (3x9) and the click router
  reads `ChestManager.peek(position)` alone; two adjacent chests open
  two independent 27-slot windows.
- The transfers: `HopperSystem.containerSlotsAt` — the chest arm reads
  `chests.getOrCreate(at)` and enumerates `ChestBlockEntity
  .SLOT_COUNT` (27) of the ONE half; the dropper shares it.
- The comparator: the analog reader (the `setAnalogReader` wiring)
  reads `chestManager.peek(position)` — the ONE half's fullness; a
  comparator under a full-twin/empty-half pair reads the half it faces.
- The placement: `canOccupyForPlacement` (the generic gate every
  placement arm consults) is air-or-replaceable only; no chest
  consult anywhere in the placement path — triples place freely.

**Verified-not-owed / already-correct neighbors:** the per-half spill
on break (vanilla `onRemoved` drops the BROKEN half's own contents —
the engine matches), the trapped/plain type split (`block == this` in
the twin scan ↔ the engine's separate plain/trapped family predicates),
the facing states (Slice L2), the per-half persistence, the trapped
chest's viewer-count signal (per-half — matches vanilla's own per-BE
count), the chest collision shape (the double's joined edge already
1:1 in `CollisionShapes`).

**Verification passes:**
- **V1 (source, 2026-10-30):** the walk above — every surface read on
  both sides; the twin scan's direction order, the `isLocked` gate, the
  `block == this` type law, the `i <= 1` formation rule, and the engine's
  four sites each read whole. The merged inventory is the single root;
  the four surfaces are its consumers.
- **V2 (behavioral, 2026-10-30):** the discriminating seats
  (`DoubleChestParityTest`, the fix branch): (a) two adjacent chests —
  the hopper facing the FIRST half pushes through into the SECOND
  half's slots (fails unfixed — the item stops at slot 27; passes
  fixed); (b) the comparator under the second half reads the MERGED
  pair's fullness (fails unfixed — reads the empty half's 0; passes
  fixed); (c) the placement seat — a chest beside two chests refuses
  (places unfixed; refuses fixed); (d) the GUI seat — the merged
  54-slot window over the pair (the window-items count and the click
  routing into both halves).
- **V3 (independent, 2026-10-30):** the compensating sweep — no merged
  view exists anywhere in the engine (the ChestManager map is the sole
  chest state; the window machinery's chest layout is pinned to 27); the
  type-split and lock-key surfaces verified per-half correct (no new
  gap opened by the merge — the `isLocked` consult must gate it, the
  twin scan must keep the plain/trapped identity split); the spill
  per-half confirmed NOT a divergence to drag in. The GUI surface is
  the only wire-visible addition (the 54-slot window layout: 54
  container + 27 main + 9 hotbar = the 90-slot window, protocol 47's
  own double-chest shape). Confirmed.

**Proposed correction (bounded, one root):** the merged-view helper —
a chest-neighbor resolution (the four horizontal neighbors, the same
`ChestBlock` type, the lock-key gate on both halves, the WEST/NORTH-
first join order) — landed as one consult consumed by all four sites:
the GUI open (the 54-slot window layout + the click router over both
halves), `containerSlotsAt`'s chest arm (the merged 54 slots for the
hopper/dropper transfers), the analog reader (the merged fullness),
and the placement's no-triple gate (a chest-specific `canBePlaced` arm
in the placement path). The persistence stays per-half (vanilla's own
shape — each BE saves its own 27).

**Regression risks:** the wire window layout (a new 54-slot window
kind — the client's own double-chest GUI renders over the same
`minecraft:chest` Open Window; the slot indices past 26 are new wire
surface for the click router); the comparator's signal shift for
existing doubled pairs (toward vanilla); the placement gate refusing
patterns that previously placed (toward vanilla — triples were never
vanilla-legal). The single-chest paths keep every existing seat.

**Grouping note:** F-DC1's four surfaces are ONE root cause (the
missing merged view); the fix lands as one branch. The hopper-local
F-HP1/F-HP2 are separate roots and ride their own branches.

---

## F-HP3 — the cart-fallback box: centered on the cell, not the reference's corner-straddling box (SUSPECTED — minor)

The reference's `getInventoryAt` is called with INTEGER cell
coordinates on the push-target and dropper paths (`getTargetInventory`:
`pos.getX() + offset`; `DropperBlock.dispense`: `blockpos.getX()`) and
with the hopper's +0.5 mouth coordinates on the pull-above path
(`getInventoryAbove`: `hopper.getX()` = `pos.x + 0.5`). The Box is
always `passed ± 0.5`, so the reference's push/dropper band straddles
the target cell's min corner — `[bx-0.5, bx+0.5]` — while the pull-above
band is the centered `[bx, bx+1]`. The engine's cart lookup uses the
centered `[bx, bx+1]` for BOTH (correct for the pull-above, +0.5
east/south of the reference on the push-target and dropper paths), and
tests the vehicle's CENTER POINT, not its box intersection. Impact: a
cart parked in the cell adjacent to the push target's west/north edge is
a valid vanilla target that the engine misses; the margins are ~0.5
cells. Status: suspected (V1 source-only; the behavioral seat needs the
cart-parking precision that the vehicle lane's own shapes make fiddly);
queued behind F-HP1/F-HP2/F-DC1.

## F-HP4 — the hopper cart's own-shape fallback y-band (SUSPECTED — minor)

`HopperMinecartEntity.transferItems` (reference lines 124-128): the
fallback gathers items whose box intersects `getShape().grown(0.25,
0.0, 0.25)` — the cart's hull `[cartY, cartY+0.7]` — so the faithful
item-center band is `(cartY-0.125, cartY+0.825)`. The engine
(`HopperSystem.hopperCartTransfer`'s fallback) tests
`at.y + 0.25 >= cartY && at.y <= cartY + 0.7` — the band
`[cartY-0.25, cartY+0.7]`: a quarter too permissive below, a eighth too
narrow above (an item resting ON the cart's roof at center
`cartY+0.8` is vanilla-valid and engine-missed). Same y-frame family as
F-HP1. Status: suspected (V1 source-only); queued with F-HP3.

---

## Verified 1:1 this pass (the negatives)

- **The cooldown law:** `transferCooldown = -1` initial; `tick` lines
  166-174 (decrement, the off-cooldown zero, `transferItems`); the 8-tick
  set on success; `canTransferNextTick()` = `<= 1` gating the
  receiving-hopper reset (`noteReceived` ↔ the reference's slot-push
  arm lines 414-419). Engine `HopperBlockEntity.tickCooldown/
  noteReceived` match.
- **The transfer walk's shape:** push-if-holding first, pull-if-room
  second, the 8-tick arm on either success (reference
  `transferItems` lines 176-199 ↔ engine `transferItemsInScope`).
- **One item per transfer with the copy-restore:** the slot-level
  `removeItem(i, 1)` + the whole-stack copy held back + the restore on
  a refused push (lines 221-246, 333-347) — the engine matches on all
  three arms (push, pull, cart).
- **The furnace's sided slots:** top `{input}`, bottom
  `{output, fuel}`, sides `{fuel}` (the reference's own arrays) + the
  fuel-slot pull-from-below bucket-only gate (`canPullItem` lines
  340-348).
- **The enabled flip:** the flag-4 write through `neighborChanged` +
  `onAdded` (HopperBlock lines 115-120); the placement facing
  (opposite-clicked, UP→DOWN, lines 76-86); the break spill +
  the comparator wake (`onRemoved` lines 123-131); the collision shape
  (the 0.625 slab + the four rim walls — both sides identical).
- **The hopper cart's tick WHOLE:** the never-reassigned
  `field_174900_c = BlockPos.ORIGIN` quirk (the cooldown decrements
  only at the world-origin cell; everywhere else the 4-tick cooldown
  resets to 0 every tick — the every-tick transfer the cart is known
  for) — verified against the real MCP `EntityMinecartHopper` and read
  1:1 in the engine's own comment + code; the activator-rail sense
  (powered = disabled); the 4-tick set; the transfer arms' order
  (container-above → the box-above suck → the own-shape fallback that
  never arms the cooldown); the empty-container early return (the
  box-above suck is NOT consulted when a container sits above).
- **The suck's x/z band** `[bx, bx+1]` (the block arm) — correct on
  both sides (the y is F-HP1).
- **The filters:** `EntityFilter.ALIVE` (the suck), `INVENTORY`
  (`instanceof Inventory && isAlive`) — the supplied reference matches
  the real `EntitySelectors` law.

## The coverage ledger row

The hopper transfer family: **inspected whole; two confirmed divergences
(F-HP1, F-HP2) + the container-consult root find (F-DC1) + two minor
suspected tails (F-HP3, F-HP4)**. The cart's own tick verified 1:1
(including the ORIGIN quirk). The chest family: the merged-inventory
root (F-DC1) now owned; the single-chest surfaces, the spill, the
type split, the facing states, the persistence all previously landed
and re-verified this pass.
