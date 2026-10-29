# The sign family parity audit — findings (the verifier's twelfth pass)

Discovery record, written BEFORE any code changed. Every law carries its
reference anchor (`reference/1.8.8`, Mojang mappings). The three-verification
record closes the pass in §E.

**The family:** the sign — the standing sign's rotation model, the placement
walk (SignItem.useOn lines 19-54), the support-break laws (StandingSignBlock /
WallSignBlock neighborChanged), and the import/wire surface of the states.

---

## A. The reference's law (every anchor re-derived from the tree)

**A1. The 16-step rotation (THE FLAGSHIP).** `SignItem.useOn` line 44:

```java
int i = MathHelper.floor((player.yaw + 180.0F) * 16.0F / 360.0F + 0.5) & 15;
world.setBlockState(pos, Blocks.STANDING_SIGN.defaultState().set(StandingSignBlock.ROTATION, i), 3);
```

`StandingSignBlock.ROTATION = IntegerProperty.of("rotation", 0, 15)` (line 13)
— SIXTEEN rotations, 22.5 degrees apart; `getStateFromMetadata(metadata) =
ROTATION = metadata` (lines 26-29) — the wire metadata nibble IS the rotation.
The sign faces its placer (the +180 shift); at yaw 0 (a south look) the
rotation is 8 (the north-facing state). The in-house 16-step precedent: the
skull's floor mount (`floor(yaw * 16 / 360 + 0.5) & 15`, SkullItem lines
57-59, EngineServer's placeSkullOnTick) — the sign's band is the same walk
with the 180 shift folded in.

**A2. The placement walk's gates (SignItem.useOn, in order).**
- Lines 21-23: `face == Direction.DOWN` → refuse (a bottom-face click places
  NOTHING).
- Lines 25-27: the CLICKED block's material must be SOLID
  (`world.getBlockState(pos).getBlock().getMaterial().isSolid()`) — pos is the
  clicked cell BEFORE the offset. The sign's own material is WOOD
  (SignBlock line 25) — a sign may stack on a sign; a torch / tall grass /
  lily pad (DECORATION / PLANT / PlantMaterial) refuses.
- Line 29: `pos = pos.offset(face)` — the target cell.
- Line 31: `canUseItemOn` — the use gates.
- Lines 35-37: `Blocks.STANDING_SIGN.canBePlaced(world, pos)` — consults the
  STANDING sign's gate for BOTH arms (the wall placement too — the reference's
  own shape):
  `SignBlock.canBePlaced` (lines 82-84) = `!isNeighboringCactus && super`,
  where `isNeighboringCactus` (BlockWithBlockEntity lines 22-31) reads the
  FOUR horizontal neighbors for `Material.CACTUS`, and `Block.canBePlaced`
  (Block lines 664-666) = the target cell's material `isReplaceable`.
- Lines 43-47: the arm split — `face == UP` → the standing sign at the
  16-step rotation; a side face → the wall sign with `FACING = face`
  (WallSignBlock.getStateFromMetadata lines 54-60: metadata 2-5 = N/S/W/E,
  the Y-axis fallback NORTH unreachable from the item).
- Line 49: `stack.size--` — the survival decrement, after the block lands.
- Lines 50-53: the tile + `BlockItem.setBlockNbt` — when no NBT applied,
  `player.openSignEditor` (ServerPlayerEntity lines 547-550).
- NO entity-collision consult anywhere in the walk — the sign's collision
  shape is null (SignBlock lines 31-34) and the item never asks
  canBlockBePlaced-with-entities (the ItemBlock family's own consult): a sign
  plants into the placer's own cell in vanilla.

**A3. The support-break laws.**
- StandingSignBlock.neighborChanged (lines 22-29): the floor below not
  Material-solid → `dropItems(world, pos, state, 0)` + `removeBlock`.
- WallSignBlock.neighborChanged (lines 43-51): the block BEHIND the sign
  (`pos.offset(FACING.getOpposite())`) not solid → drop + remove.

**A4. The import/wire surface.** The vanilla chunk format stores the standing
sign as block id 63 + the rotation nibble 0-15; the wall sign 68 + 2-5. The
client renders all sixteen orientations from the nibble alone.

**A5. The Material model behind the gates** (Material.java): the base class
is SOLID (lines 59-62); AirMaterial (AIR, FIRE), LiquidMaterial (WATER,
LAVA), PlantMaterial (PLANT, REPLACEABLE_PLANT, DECORATION, CARPET,
SNOW_LAYER) and PortalMaterial (PORTAL) override false. Consequences the
collision model gets wrong for these consults: the cobweb is material-SOLID
(lines 36-41, only blocksMovement overridden), the signs are WOOD (solid — a
sign stacks on a sign), the pressure plates are WOOD/ROCK/GOLD/IRON (the
ctor's own parameter — solid), the doors and gates WOOD/IRON, the moving
piston PISTON. The DECORATION family (the torch — TorchBlock's own line, the
rails, the wire, the levers, the buttons, the tripwire, the ladders, the
diodes) is non-solid; the vines are REPLACEABLE_PLANT; the stems PLANT.

---

## B. The engine's state (the divergences)

**B1. Only 4 of the 16 standing-sign states exist.** BuiltinBlocks: `sign`
(meta 0), `sign_west` (4), `sign_north` (8), `sign_east` (12) — the comment
itself names the fold ("the metadata nibble's 45-degree band 0/4/8/12 =
S/W/N/E"). The placement (placeSignOnTick's `facings[]` pick) selects one of
the four by the 4-band look. **At every non-cardinal yaw the placed rotation
is wrong by up to 67.5 degrees**: yaw 22.5 → vanilla 9 (NNE), engine 8 (N);
yaw 45 → vanilla 10 (NE), engine 12 (E); yaw 67.5 → vanilla 11 (ENE),
engine 12. The client renders the sign's text at the wrong angle for every
diagonal look.

**B2. The anvil import loses vanilla signs.** AnvilBlockIds carries only
`state(63, 0/4/8/12)`; `resolve(63, r)` for the twelve non-cardinal
rotations returns empty and the caller isolates the cell as air ("a counted
reason, §31.6: never a silent substitute") — a vanilla world's angled sign
imports as AIR, losing the block AND the sign text.

**B3. The support-break laws are absent.** BlockUpdateSystem's family census
(the class javadoc + the support arms): gravity blocks, the floor torch, the
carpet, the cocoa, the lily pad, the flora — NO sign arm. A standing sign's
floor removed, a wall sign's wall removed: the engine leaves both floating;
vanilla pops both as the sign item and clears the cell.

**B4. The face-DOWN refusal is absent.** placeSignOnTick's wall arm is
`face >= 2 && face <= 5`; face 0 (a bottom-face click) falls into the STANDING
arm and plants a floating sign at the cell BELOW the clicked block, where
the reference refuses (SignItem lines 21-23).

**B5. The clicked-solid gate is absent.** The engine's gates reject clicked
AIR alone; the reference requires the clicked block's MATERIAL solid —
vanilla refuses a sign placed by clicking a torch / tall grass / a lily pad;
the engine places it.

**B6. The cactus-neighbor gate is absent.** canOccupyForPlacement covers the
target-replaceable half of `canBePlaced` but no consult reads the
four-horizontal `Material.CACTUS` neighbors (BlockWithBlockEntity lines
22-31). Vanilla refuses the sign beside a cactus (the placement would break
the cactus's own law); the engine places and the cactus stays caged.

**B7. The body-collision consult is an EXTRA gate.** placeSignOnTick carried
`intersectsPlayerBox` (a full-cell AABB overlap); the reference's SignItem
walk has NO entity gate — the sign's collision is null and the placement
into the placer's own cell is vanilla behavior (a player standing on a
block, placing a sign at their feet). The gate refused exactly that
placement.

---

## C. Verified 1:1 already (no change needed)

- The wall sign's face mapping: face 2/3/4/5 → NORTH/SOUTH/WEST/EAST
  (WallSignBlock's FACING = the clicked face; the engine's WALL_SIGN_* states
  carry metadata 2-5).
- The editor open on placement, both arms, the placing player alone
  (openSignEditorOnPlacement; SignEditorOpenAcceptanceTest owns the pin).
- The survival decrement after the block lands (the creative hand skips it).
- The sign text store keyed by position, block-agnostic across both forms,
  and its per-tick discard when the block is gone (SignDataStoreSplitTest).
- The skull's floor-mount 16-step band — the in-house precedent the sign's
  law shares (the +180 shift aside).
- The band formula family-wide: every lookBand site in EngineServer carries
  the +45-shift form `((yaw % 360) + 360 + 45) % 360` — mathematically
  `byIdHorizontal(floor(yaw*4/360 + 0.5) & 3)` (Entity.getHorizontalFacing
  lines 1837-1839; Direction.byRotation lines 176-178). THE STALE LEDGER NOTE
  CORRECTED: the Wave PF commit's claim "the chest/furnace placement sites'
  older truncation band" does not describe the current code — all fifteen
  sites were re-read line by line in this discovery and every one carries
  the centered form. A Ledgered note is a claim about the code at its
  writing time (the Wave FC lesson) — this one is re-pinned here.
- The prefix-based sign consults (WorldSolidity.isSign, SignManager.isSignType)
  auto-cover the twelve new states — no membership edits owed.

## D. The honest tails (recorded, out of scope)

- **BlockItem.setBlockNbt** (SignItem lines 50-53): a sign stack carrying a
  BlockEntityTag applies its NBT to the tile and SKIPS the editor open. The
  engine's sign text lives in the position-keyed store and the sign item
  carries no BlockEntityTag consult — the editor always opens. The
  item-NBT family's future row.
- **The banner family** shares the canBePlaced + support shapes but no
  banner item exists in the registry (pass 9's standing tail, unchanged).
- **The engine's cactus arm** substitutes the collision model
  (WorldSolidity.isSolid) where the reference reads Material.isSolid — a
  cobweb beside a cactus (material-SOLID, breaking the cactus in vanilla)
  is spared by the collision model's walk-through cobweb. The cactus
  family's future row; MaterialSolidity now exists for that re-audit.
- **The floor torch's support arm** consults AIR-ONLY where the reference
  reads Material.isSolid (a torch on a leaf floor survives vanilla, pops in
  the engine; a torch on a cobweb floor survives vanilla, pops in the
  engine). The same class; recorded beside it.

---

## E. The three-verification record

1. **Discovery** (verification 1): every finding above read from the
   reference with file + line anchors, matched against the engine's site,
   and recorded here BEFORE any code changed. The Wave PF stale note was
   run down and re-pinned (all fifteen lookBand sites re-read).
2. **Anchor re-derivation** (verification 2): the critical anchors re-read
   directly at implementation time — SignItem line 44's 16-step band, the
   gate order (DOWN → clicked-solid → offset → use-gates → canBePlaced →
   the arm split → decrement → the editor, and the NO-entity-gate law),
   StandingSignBlock's ROTATION property bounds and getStateFromMetadata,
   both neighborChanged arms, the isNeighboringCactus four-neighbor consult,
   Material.WOOD on the sign, the material model (base solid; the four
   non-solid subclasses) with the cobweb/plates/signs consequences.
3. **The seat suite** (verification 3): SignRotationParityTest (NEW) pins
   every restoration with its anchor cited; the regression batches in the
   PR body (the sign package, the block package incl. the registry count
   re-pin 902 + 12 = 914, the interaction + world/import batches, the
   protocol wire batch, the update-system family, the skull batch).

## F. Fix plan (one family pass, every law separately seated)

1. **The twelve states** (B1): BuiltinBlocks — sign_ssw(1), sign_sw(2),
   sign_wsw(3), sign_wnw(5), sign_nw(6), sign_nnw(7), sign_nne(9),
   sign_ne(10), sign_ene(11), sign_ese(13), sign_se(14), sign_sse(15) —
   the compass-16 names, the metadata nibble each (the base `sign` keeps
   metadata 0, existing worlds valid).
2. **The placement law** (B1): the 16-step band
   `floor((yaw + 180) * 16 / 360 + 0.5) & 15` selects the state; the 4-band
   `facings[]` pick retired.
3. **The gates** (B4, B5, B6, B7): the face-0 refusal, the clicked-solid
   consult (the new MaterialSolidity — the reference's material model), the
   cactus-neighbor consult, and the body-collision consult REMOVED — in the
   reference's own order.
4. **The support arms** (B3): BlockUpdateSystem — the standing sign's floor
   consult and the wall sign's behind-consult (the per-state facing's
   opposite offset), both popping the sign item through the standard
   block-drop delay and clearing the cell (the floor-torch arm's own
   pattern; the store's per-tick discard owns the text).
5. **The surface rows** (B1, B2): the behavior/light/blast/note rows for
   the twelve (no collision rows — the sign's collision is null);
   AnvilBlockIds' twelve import rows (no sign state isolates as air
   anymore); LegacyBlockIds' twelve rows (id 63, the metadata nibble each).
6. **The registry re-pin**: BlockRegistryTest 902 → 914.
