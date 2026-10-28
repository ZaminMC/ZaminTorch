# The rail family parity audit — findings (the verifier's thirteenth pass)

Discovery record, written BEFORE any code changed (§A-§F). Every law
carries its reference anchor (`reference/1.8.8`, Mojang mappings). The
implementation has landed: the three-verification record (§E) is CLOSED
and §G holds the implementation's own two finds (the own-commit
exemption + the stale derived-table pin).

**The family:** the rails — the plain rail's ten-state vocabulary, the
placement walk (the look law the reference does NOT carry), the RailNode
connection walk (onAdded's updateShape + neighborChanged's updateState),
the floor consult's own family (hasSolidTop), and the support-pop arms.

---

## A. The reference's law (every anchor re-derived from the tree)

**A1. The shape vocabulary.** `AbstractRailBlock.Shape` (lines 478-489):
TEN shapes — the six straight (`NORTH_SOUTH` 0, `EAST_WEST` 1,
`ASCENDING_EAST` 2, `ASCENDING_WEST` 3, `ASCENDING_NORTH` 4,
`ASCENDING_SOUTH` 5) and the four curves (`SOUTH_EAST` 6, `SOUTH_WEST` 7,
`NORTH_WEST` 8, `NORTH_EAST` 9). `getMetadataFromState`: shape | the
POWERED bit 8 — the plain rail's ten states over wire id 66, the golden
(27) / detector (28) / activator (157) families' twelve each (their
POWERED halves). **The curves are the plain rail's own family alone** —
the `alwaysStraight` flag (the AbstractRailBlock ctor's parameter; false
for the plain rail, true for the other three) gates every curve branch.

**A2. The placement (the default + the walk, NEVER the look).** RailBlock
line 15: the default state is `SHAPE = NORTH_SOUTH`. There is NO
`getPlacementState`/`onPlaced` in the rail family — the item places the
DEFAULT north-south state, and `onAdded` (AbstractRailBlock lines 76-83)
runs the connection walk immediately:
`updateShape(world, pos, state, force=true)` → the RailNode walk (§A3)
re-orients the rail to its neighbors; then, for the `alwaysStraight`
families alone (the powered rails), the explicit `neighborChanged` call.
**The placer's look is never consulted anywhere in the family.**

**A3. The RailNode connection walk (AbstractRailBlock lines 143-470).**
- `updateConnections` (lines 163-212): the per-shape connection lists —
  the two neighbour cells each shape reads (the ascending shapes read one
  same-level cell + one UP cell; the curves their two orthogonal cells).
- `getNeighborRail` (lines 233-246): the neighbour search — same level,
  then UP, then DOWN (rails connect across one level of slope).
- `removeSoftConnections` (lines 214-227): prune connections whose
  neighbour does not reciprocate (`connectsTo` — the neighbour's own list
  must contain our cell).
- `updateState(powered, force)` (lines 346-470): the shape selection —
  the exclusive bands (`(N||S) && !E && !W` → NORTH_SOUTH; `(E||W) && !N
  && !S` → EAST_WEST), the four curve branches (the `!alwaysStraight`
  family alone: S+E → SOUTH_EAST, S+W → SOUTH_WEST, N+W → NORTH_WEST,
  N+E → NORTH_EAST), the fallback bands, and the POWERED curve preference
  (lines 401+ — a powered straight rail with two orthogonal neighbours
  picks the curve the signal favours); `addConnection` (lines 277-336)
  carries the same table for the force arm plus the ASCENDING upgrades (a
  rail at the neighbour's UP cell lifts NORTH_SOUTH/EAST_WEST into the
  ASCENDING_* shapes, lines 316-335).
- `countConnections` (lines 259-268): the four-horizontal could-connect
  count — RailBlock.updateState (lines 19-23) re-walks when a signal
  source arrives and the count is exactly 3.

**A4. The gates.** `canBePlaced` (lines 71-73):
`World.hasSolidTop(world, pos.down())` — the HAS-SOLID-TOP family, not
isSolid (a bottom slab, a fence, a wall, the thin snow are NOT a rail
bed — the same family the pigman spawn and the fire walk already port).

**A5. The support arms (neighborChanged lines 86-113).** The floor below
not hasSolidTop → `dropItems` + `removeBlock` (the rail pops); each
ASCENDING shape's own run consult (`ASCENDING_EAST` needs hasSolidTop at
`pos.east()`, and so on around the compass); else `updateState` (the
signal-source arm above). THE DISPATCH LAW these arms ride
(World.updateNeighbors lines 342-349 over setBlockState's flag-1 walk):
a block's change notifies its SIX NEIGHBOURS alone — the changed cell
ITSELF never receives neighborChanged, so the support consults run on
NEIGHBOUR wakes alone (the own-commit exemption, §G1).

**A6. The wire/import surface.** The vanilla chunk format: the plain rail
id 66 with the shape nibble 0-9; the powered families 27/28/157 with the
shape | 8 band.

---

## B. The engine's state (the divergences)

**B1. The plain rail carries TWO of TEN states.** BuiltinBlocks: `rail`
(meta 0) + `rail_ew` (1) alone — the FOUR ASCENDING shapes and the FOUR
CURVES are unregistered (the RailBlocks family map holds the golden/
detector/activator families' twelve each; the plain rail's states never
rode it). A vanilla slope of plain rails cannot exist in the engine, and
no curve can ever be laid.

**B2. The anvil import loses slopes AND curves.** AnvilBlockIds maps only
`(66, 0)` and `(66, 1)` — `resolve(66, r)` returns empty for r 2-9 and
the importer isolates the cell as air: **a vanilla world's ascending
plain rails and every curve import as AIR.**

**B3. The RailNode walk is absent entirely.** No `onAdded` walk at the
placement, no neighbor re-orientation: the engine's rails NEVER connect
(neither the placed rail nor the existing neighbours re-shape), the
ascending upgrades never happen, the curves never form. The engine's rail
states are write-once at placement.

**B4. The placement carries a look law the reference does not.**
EngineServer's placeRailOnTick: the +45 4-band picks north-south vs
east-west from the LOOK — vanilla places the default north-south and the
connection walk decides. A lone rail placed with an east-west look:
vanilla north-south, engine east-west.

**B5. The floor consult is the wrong family.** The site consults
`WorldSolidity.isSolid(floor)` — a bottom slab, a fence, a wall or the
thin snow is accepted as a rail bed; the reference's `hasSolidTop`
refuses them (the engine's own 1:1 hasSolidTop port already exists — the
fire family's, EngineServer lines 2032-2039).

**B6. The support-pop arms are absent.** The BlockUpdateSystem carries no
rail arm: a rail's floor removed → the engine floats it; vanilla pops it
with its drop. The ASCENDING run's own support consults are absent with
it (moot until B1/B3 restore the ascending states).

**B7. The placement carries a body-collision gate the reference does not**
(found at the anchor re-derivation, verification 2): the site's
`intersectsPlayerBox` consult refuses a rail into the placer's own cell —
but the reference's item walk carries no entity gate (the same law the
sign pass's F7 removed) and the rail's collision shape is NULL
(AbstractRailBlock.getCollisionShape lines 38-41): a vanilla rail
plants under the player's feet, and the engine refuses the click.

---

## C. Verified 1:1 already (no change needed)

- The golden/detector/activator families' twelve-state bands each (the
  six straight shapes + the POWERED bit — RailBlocks' `state`/`metaOf`
  are the reference's `getMetadataFromState` 1:1).
- The powered families' `alwaysStraight` curve exclusion — moot in the
  engine (no curves anywhere) but the family map's shape lists already
  carry no curve rows.
- The vehicle reads (`shapeOf`/`axisIsX`/the ascending consult) over the
  six straight shapes.
- The rail families' walk-through solidity (WorldSolidity.isRail) and
  their blast/light/behavior surface rows.

## D. The honest tails (recorded, out of scope)

- The POWERED curve preference arm (A3's `powered` branches) — the plain
  rail is never powered; the golden rail's powered-curve walk is the
  redstone family's own future row.
- The signal-source re-walk (RailBlock.updateState's count==3 arm) rides
  the RedstoneSystem's own event surface — the integration seam is the
  redstone family's row.

---

## E. The three-verification record (to close at implementation)

1. **Discovery** (verification 1 — DONE): every finding above read from
   the reference with file + line anchors, matched against the engine's
   sites, and recorded here BEFORE any code changed.
2. **Anchor re-derivation** (verification 2 — DONE, this pass): the
   ten-shape enum (ids 0-9 re-read), the default state + the NO-look law
   (RailBlock line 15, no getPlacementState anywhere in the family), the
   onAdded walk order (updateShape force=true first, the alwaysStraight
   neighborChanged second), the updateState selection table (the
   exclusive bands, the four curve branches, the null fallback + the
   unpowered curve picks, the ascending upgrades over both straight
   shapes — south-after-north and west-after-east, two ifs not
   else-ifs), the reciprocation prune's X/Z-ONLY compare (hasConnection
   lines 247-256 — the up/down cells collapse), the hasSolidTop gate,
   both support arms, and ONE NEW FINDING: the collision-null body gate
   (B7, AbstractRailBlock lines 38-41).
3. **The seat suite** (verification 3 — DONE, at the implementation):
   the stash-verified probe — eight seats over the PRE-FIX tree, every
   one failing with its documented divergence (A the look law
   `rail_ew` vs `rail`; B the walk `rail` vs `rail_ew`; C the curve
   `rail` vs `rail_north_east`; D the floor consult `rail` lands on a
   bottom slab; E the support pop `rail` floats; H the body gate
   `air` vs `rail`; F/G the vocabulary + import `false`) — and the
   post-fix suites green: the shape vocabulary (registry + wire +
   import for all ten plain-rail states), the default placement (the
   look NEVER consulted), the connection walk (both rails re-orient;
   the curve forms; the ascending upgrade; the trio straight-only;
   no re-walk on removal), the floor consult (the bottom slab + fence
   refusals, the stone control), the body gate (the underfoot plant),
   and the support pops (the floor break, the ascending run's break,
   the family item drops, the imported-rail first wake, and the
   OWN-COMMIT EXEMPTION — see §G).

## F. Fix plan (one family pass)

1. **The plain rail's ten states** (B1, B2): the plain rail joins the
   RailBlocks family map (wire id 66, no POWERED half) — the four
   ascending + four curve states registered, the import rows (66, 0-9),
   the wire rows with the shape nibbles, the surface tables (behavior/
   light/blast/collision/note) — the registry count re-pin (+8).
2. **The placement** (B4, B5, B7): the default north-south state lands;
   the look law retired; the floor consult switches to the hasSolidTop
   port; the body-collision consult removed (the collision-null law,
   the sign pass's own F7 precedent).
3. **The connection walk** (B3): the RailNode port (the updateConnections
   lists, the neighbour search up/down, the reciprocation prune, the
   updateState selection table with the curve and ascending branches over
   the plain rail's ten states, the force arm at placement) — run at the
   placement (onAdded) and at the rail arms of the update system.
4. **The support arms** (B6): the BlockUpdateSystem's rail arm — the
   floor consult (hasSolidTop), the pop with the family's item drop,
   the ASCENDING run consults once the states exist — and the
   OWN-COMMIT EXEMPTION in the dispatch (§G1): the changed rail
   never wakes ITSELF, only its six neighbours do
   (World.updateNeighbors lines 342-349).

## G. The implementation record (the third verification's own finds)

The fix landed as §F planned, with TWO laws the discovery record
held only implicitly, both surfaced by the live test runs:

**G1. THE OWN-COMMIT EXEMPTION (the dispatch law the reference carries
and the engine's bus does not — World.updateNeighbors lines 342-349
over setBlockState's flag-1 walk):** a block's own change notifies
its SIX NEIGHBOURS alone; the changed cell ITSELF never receives
neighborChanged. The engine's BlockUpdateSystem dispatch carries a
`{0,0,0}` self-offset (every commit wakes the changed cell too) — the
established torch/carpet/sign convention, a latent divergence of its
own — and the rail arm riding it popped every DIRECTLY-WRITTEN rail
on its own commit: a plugin-path `World.setBlock` rail over air
floats in vanilla (onAdded lines 76-83 run the walk, NO support
consult — only the item path's canBePlaced consult guards the bed)
and pops only when a NEIGHBOUR changes. The regression surfaced
LIVE: HopperMinecartTest's cart-rail scene (a directly-setBlock'd
rail at height, no bed — green on develop because the pre-audit
engine carried no rail arm at all) failed the window-click seat when
the arm popped its rail and the cart fell. THE FIX: the rail
families' own-commit exemption in `onBlockChanged` — when the
changed cell's new type is a rail, the `{0,0,0}` self-schedule skips
(the six neighbour offsets schedule as always). LOAD-BEARING both
directions: without the exemption scene F fails (the floating rail
pops through its own commit) AND the hopper regression returns; with
it both green, and the command-block/spawner cart scenes' floating
rails persist exactly like vanilla's too. The other families'
self-wake divergence stays LEDGERED here as their own audits' rows
(the torch/carpet/sign arms' convention — out of this pass's scope).

**G2. The stale derived-table pin (the sign pass's tail):** the
protocol module's AnvilBlockIdsIntegrityTest carries a pinned
count of the wire/import derivation — the sign-rotation pass
(47ad888b) registered the standing sign's twelve intermediate states
WITHOUT the pin moving (1251 was already stale at 1263; the pass's
own evidence ran a nine-test wire batch that did not include the
integrity size pin — the same stale-pin class the test's own comment
chain documents). This pass re-pins it 1251 -> 1271 (the sign's
TWELVE + the rail's EIGHT rows), the missing-rows/wrong-rows/
collision checks green both before and after (only the count had
drifted).
