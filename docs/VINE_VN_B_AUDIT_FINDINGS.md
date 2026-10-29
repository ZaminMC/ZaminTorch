# The VN-B discovery record — the vine family's placement, decay, and growth
(the seventeenth pass; the §22 queue's sole remaining row, discovery-stage —
each finding read against `reference/1.8.8` line by line, NO speculative fixes)

The F-VINE queue's first slice (Wave VN-A) landed the sixteen-state vocabulary,
the climbable pair, and the collision repair. This pass pins the remaining
three mechanics against the reference and against the engine's actual sites.
All three are **missing whole** — none exists in any partial shape. Every
finding below is at the source-verification stage (pass 1 of 3); the fix wave
must complete the behavioral and independent passes before any PR.

---

## VN-B1 — the placement gate + the opposite-face state (MISSING WHOLE)

**The reference law** (`VineBlock.canBePlaced` lines 132-148 +
`getPlacementState` lines 296-300, over `BlockItem.useOn` lines 26-45 +
`World.canPlace` lines 2274-2282):

- The item-use walk: the clicked block's position `pos`, advanced
  `pos.offset(face)` when the clicked block is not replaceable; then
  `world.canPlace(block, pos, false, face, ...)` — which for the vine
  reduces to `block.canBePlaced(world, pos, face)` (the vine's own
  material is `REPLACEABLE_PLANT`, so the replaceable half holds).
- **The gate** (`canBePlaced` lines 132-144):
  - `face == UP` → `canGrowOn(pos.up())` — the block ABOVE the
    placement cell must be a solid cube that blocks movement (the vine
    hangs down from it);
  - `face` horizontal (NORTH/SOUTH/EAST/WEST) →
    `canGrowOn(pos.offset(face.getOpposite()))` — the CLICKED wall
    itself must be the growable solid;
  - `face == DOWN` → **false** (a vine never places against a ceiling).
- **`canGrowOn`** (lines 146-148): `block.isCube() &&
  block.material.blocksMovement()` — already landed on the engine as
  `VineBlocks.isGrowableSupport` (VN-A, riding
  `CollisionShapes.isFullCube`).
- **The placement state** (`getPlacementState` lines 296-300): the
  all-clear state, then `dir.getAxis().isHorizontal() ?
  state.set(getPropertyForDirection(dir.getOpposite()), true) : state`
  — a horizontal placement carries exactly ONE face bit, the face
  TOWARD the clicked wall (the opposite of the clicked face). A
  vertical placement keeps the all-clear form (metadata 0 — which
  `hasConnections` will pop at the first neighbor update unless a
  neighbor changed nothing: the dormant `vine_none` form VN-A named).

**The engine today**: the vine item exists (`BuiltinItems` line 1008,
"Vines", stack 64) but the placement dispatch (`EngineServer`'s
per-family arms, the ~11830-11870 region — the hopper's
"clicked-face-opposite rule" the exact pattern) has **NO vine arm**
(EngineServer's sole vine mention is the climbing consult at line
15349). The generic placement path places the vine with no gate and no
placement state — the VN-A default, the BARE ALL-FACES form
(metadata 15).

**The observable divergences**:
1. A player placing a vine against a wall gets the **all-faces form
   (metadata 15) on the wire** where vanilla carries the single
   opposite-face bit (1/2/4/8) — the client renders the vine
   connected on all four sides instead of one.
2. A vine places **mid-air** (no growable neighbor in any direction),
   against a **ceiling** (face DOWN), and against **non-cube walls**
   (torches, fences, glass panes) — vanilla refuses all three.
3. A vine places with `face == UP` regardless of the block above —
   vanilla requires the solid cube above (the hanging form).

**Proposed minimal correction** (the fix wave's scope): a placement
arm at the dispatch — the gate over `VineBlocks.isGrowableSupport`
(the UP-above / wall-opposite / DOWN-refuse law) and the
opposite-face state (the single-bit placement state from the clicked
face). Risks: none named yet — the generic path is untouched for every
other block; the wire's metadata band already carries the sixteen
states (VN-A).

---

## VN-B2 — the neighborChanged decay through hasConnections (MISSING WHOLE)

**The reference law** (`VineBlock.neighborChanged` lines 190-195 over
`hasConnections` lines 150-172 + the drop law lines 302-320):

- Every neighbor update (server-side) runs `hasConnections`:
  - For each horizontal direction: if the state carries that face bit
    AND the neighbor there is not `canGrowOn`, the bit **clears** —
    UNLESS the vine directly above carries the same bit (the chain
    rule, lines 156-159: `blockstate1 = world.getBlockState(pos.up());
    if (blockstate1.getBlock() != this || !blockstate1.get(bit))` —
    a bit survives when the vine above keeps it, whatever the neighbor
    beside is; the hanging chain's own transitivity).
  - `getConnectionCount(state) == 0` → returns false → the caller
    pops: `dropItems(world, pos, state, 0)` (which drops NOTHING —
    `getDropItem` line 303-305 returns null, `getBaseDropCount` 0) and
    `world.removeBlock(pos)`.
  - A partially-cleared state writes back (`setBlockState(pos, state,
    2)` — the no-neighbor-update flag, no cascade).
- The connection count includes the virtual UP property
  (`PROPERTIES` = {UP, NORTH, SOUTH, WEST, EAST}, line 30 +
  `getConnectionCount` lines 376-386) — but the STORED state's UP is
  always false (never persisted, never placed), so the stored-state
  count is the four horizontal bits alone; the all-clear `vine_none`
  form pops at the first neighbor update.

**The engine today**: `BlockUpdateSystem` has **no vine arm** — a
vine whose wall is broken floats forever; a bit whose neighbor
vanished never clears.

**The observable divergences**: break the wall a vine hangs on →
vanilla pops the vine (no drop); the engine leaves it floating. A vine
placed on a wall that later becomes non-supporting (e.g. a piston
pulls it) → same pop, never in the engine.

**Proposed minimal correction**: the decay arm in
`BlockUpdateSystem`'s dispatch — the per-face clear with the
chain-rule exception over `isGrowableSupport`, the write-back at flag
2, the connectionless removal (no drops). Risks: the chain rule reads
the vine ABOVE (a second world read per update — bounded, the update
lane is already per-block); the removal must not cascade neighbor
updates from the pop itself (flag discipline — the reference's
`removeBlock` DOES notify, the pop's own neighbors get the update:
verify the engine's remove-and-notify shape at fix time).

---

## VN-B3 — the random-tick growth whole (MISSING WHOLE)

**The reference law** (`VineBlock.tick` lines 198-294 — the
`setTicksRandomly(true)` registration at line 35):

- **The roll**: `world.random.nextInt(4) == 0` — 25% per random tick.
- **The density census** (lines 200-217): scan the box
  `pos.add(k, i1, l)` for `k, l in [-4, 4]`, `i1 in [-1, 1]` (a
  9×3×9 box, 243 cells); count neighboring vine blocks, decrementing
  `j` from 5; `flag = true` when the count hits 5 (the density
  cap). The census counts EVERY vine cell in the box (including the
  trunk above/below).
- **The direction pick** (line 219): `Direction.pick(random)` —
  uniform over all SIX directions (`values()[random.nextInt(6)]`,
  Direction.java line 180-182).
- **The arms**:
  1. `direction == UP` and `y < 255` and `pos.up()` is air
     (lines 221-234): if under the density cap, the spread state =
     the CURRENT state with each horizontal bit thinned by
     `random.nextBoolean() || !canGrowOn(the cell beside the target
     in that direction)` (a bit survives only when the coin passes
     AND the wall beside the new vine can host it); place at
     `pos.up()` only if at least one horizontal bit survives. (The
     spread carries the parent's faces minus the thinning — a
     vertical growth spurt.)
  2. Else if the picked direction is horizontal AND the state carries
     that face bit (line 235): the DOWNWARD continuation (lines
     236-266) — at `y > 1`:
     - the cell below is AIR → the child state = the parent's with
       each horizontal bit thinned by `random.nextBoolean()` alone
       (no canGrowOn consult here — the hanging copy); place only if
       a bit survives;
     - the cell below is a VINE → the MERGE arm: the child's bits =
       its own OR'ed with the parent's bits where
       `random.nextBoolean() && state.get(bit)` — the random
       per-bit inheritance; write only if a bit survives.
  3. Else if the picked direction is horizontal and the face bit is
     NOT set (or the pick was DOWN — the `!flag` guard, line 267):
     the CORNER SPREAD at `pos.offset(direction)` (lines 268-290):
     - the target is AIR: the four corner cases over
       `clockwiseY`/`counterClockwiseY` — (a) the parent carries the
       clockwise bit and `canGrowOn` that corner cell → place the
       single-bit clockwise vine there; (b) the counter-clockwise
       twin; (c) the clockwise bit + the corner cell air +
       `canGrowOn(pos.offset(clockwise))` → place the
       OPPOSITE-of-picked vine at the clockwise cell; (d) the
       counter-clockwise twin; (e) the fallback —
       `canGrowOn(target.up())` → place the all-clear default at the
       target.
     - the target is a solid cube blocking movement (line 288): the
       wall-attach arm — write the CURRENT state with the picked
       direction's bit SET (the vine grows sideways onto the wall).
- Every placement rides `setBlockState(..., 2)` (no neighbor cascade).

**The engine today**: `RandomTickSystem` has **no vine row** — vines
never spread, never attach sideways, never extend downward.

**The observable divergences**: a lone wall vine in vanilla gradually
carpets the wall and the ground below (the corner spreads + the
downward continuation); in the engine it is a frozen single cell
forever. The density cap keeps vanilla carpets at ≤5 vines per 9×3×9
box — the engine would have no cap at all (once growth lands without
the census, a runaway).

**Proposed minimal correction**: the growth arm in
`RandomTickSystem`'s dispatch — the roll, the census (bounded: the
243-cell scan only on a passed roll), the six-way pick, the three
arms with their exact thinning coins. Risks: the RANDOM CONSUMPTION
ORDER must match the reference exactly (the roll, the pick, then the
per-arm coins — the census consumes NO random); the census is the
cost center (243 reads per passing roll — the same order the
reference itself pays).

---

## The verified-1:1 list (no code owed — checked this pass)

- **The collision** (`getCollisionShape` line 127-129 returns null) —
  landed VN-A (the walk-through).
- **The state vocabulary** (`getStateFromMetadata` lines 328-330 /
  `getMetadataFromState` lines 333-352) — landed VN-A (the sixteen
  states, the anvil rows).
- **The climbable pair** (`LivingEntity.isClimbing`: LADDER || VINE)
  — landed VN-A.
- **The shape** (`updateShape` lines 65-124) — RENDER-ONLY (the
  visual band per face bit); a client concern, no server code owed
  (the same adjudication as the other render-only families).
- **The virtual UP property** (`resolveVirtualProperties` line 41 —
  `blocksAmbientLight` of the block above, resolved at query time,
  never persisted) — render/culling-side; the wire never carries it
  (the metadata nibble holds the four horizontal bits alone — VN-A's
  own note).
- **The flammability** (FireBlocks' vine rows, 15/100) — landed with
  the fire family.
- **The shears drop** (`afterMinedByPlayer` lines 313-320: the shears
  arm drops one vine + the stat; otherwise the generic harvest over
  `getDropItem` null drops nothing) — the engine's BuiltinItems
  comment (lines 1003-1008) names the shears arm as landed
  (the cobweb-family shape); verify at fix time, no finding opened.
- **The material** (`REPLACEABLE_PLANT` — `canBeReplaced` line 60-62
  returns true: any placement can overwrite a vine cell) — verify the
  engine's material row at fix time (the generic placement's
  replaceable consult), no finding opened yet.

---

## The fix plan (one VN-B wave, three arms, one PR family)

1. The placement arm (VN-B1): the dispatch gate + the opposite-face
   state — the wire's metadata band already carries the sixteen
   states.
2. The decay arm (VN-B2): `BlockUpdateSystem`'s per-face clear with
   the chain exception + the connectionless pop.
3. The growth arm (VN-B3): `RandomTickSystem`'s roll + census + the
   three spread arms.

Seats per arm: a wall-side placement (the single-bit metadata on the
wire + the gate's three refusals + the UP-above hanging form), the
broken-wall decay (the pop, the no-drop law, the chain-rule
exception, the partial clear's write-back), and the growth arms over
a scripted random (the census flag suppressing the up-spread, the
thinning coins' exact survival, the corner-spread quartet, the
wall-attach bit set). The stash probe must fail pre-fix on every arm
(all three are missing whole — the seats will not compile against a
missing seam, the strongest failing form, or must be written against
the seams the fix introduces).

**Verification state**: pass 1 (source) complete for VN-B1/B2/B3 —
every line citation above was read this pass from
`reference/1.8.8/net/minecraft/block/VineBlock.java` (387 lines),
`item/BlockItem.java`, `world/World.java` (canPlace), and
`util/math/Direction.java`. Passes 2 (behavioral) and 3 (independent
re-verification) belong to the fix wave.
