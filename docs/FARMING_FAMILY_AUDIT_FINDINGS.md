# The farming family parity audit — findings (the verifier's fourteenth pass, discovery record)

Scope: every growth-cadence, farmland-state, planting, and import law the
1.8.8 reference carries for the farming family, read against the engine's
`RandomTickSystem` (the growth arms), `EngineServer.useFarmingOnTick` (the
hoe/seed/bone-meal arms), `BlockUpdateSystem` (the support arms),
`BlockBehaviorTable` (the drop rows), the protocol module's
`LegacyBlockIds` (the wire nibbles), and `AnvilBlockIds` (the import rows).
The discovery sweep also recorded the adjacent VINE family (§F) and the
future-pass candidates (§H).

Reference anchors: `reference/1.8.8/net/minecraft/block/WheatBlock.java`
(the crop growth formula + `getGrowthSpeed`), `StemBlock.java` (the fruit
arm), `SugarCaneBlock.java`, `CactusBlock.java` (the AGE-15 accumulators),
`FarmlandBlock.java` (the moisture ladder, rain, trample, the solid-above
law), `NetherWartBlock.java` (the 1-in-10 no-light growth),
`CarrotsBlock.java`/`PotatoesBlock.java` (the drop tails),
`VineBlock.java` (the spread walk), `HoeItem.java` (the coarse-dirt till),
`WheatSeedsItem.java` + `CropItem.java` (the planting laws),
`Item.java` (the registrations 295/361/362/372/391/392).

The audit follows the standing verifier protocol: discovery first, every
finding verified against the reference line-by-line (V1) and the engine's
site read whole (V2) before any fix; the third verification is the seat
suite on the pre-fix tree (V3, §G's closure per finding). This document is
the discovery record; the fix commits close each finding's §G row.

---

## A. The growth cadences (every farming plant grows too fast)

### A1. The crop growth formula is not the reference's `25/f` roll
- Reference: `WheatBlock.tick` lines 34-45 — the light gate
  (`getRawBrightness(pos.up()) >= 9`), then
  `if (random.nextInt((int)(25.0F / f) + 1) == 0)` with
  `f = getGrowthSpeed(this, world, pos)` (lines 56-98).
- The formula's own law: `f` starts 1.0; the 3x3 soil ring under the crop
  contributes `+1.0` per farmland cell (`+3.0` when
  `blockstate.get(FarmlandBlock.MOISTURE) > 0` — ANY moisture, line 66),
  with the eight non-center cells divided by 4 (lines 71-73). The
  same-crop row pattern then halves `f` (lines 79-95: a crop with its own
  kind both N/S and E/W, or in the four corners).
- The numbers: wet center alone `f=4.0` → `(int)(25/4=6.25)=6` →
  **nextInt(7), a 1-in-7 chance**; dry center `f=2.0` → 12 → **1-in-13**;
  no farmland `f=1.0` → 26; a fully-wet-surrounded crop `f=10.0` →
  **1-in-3** (the famous row acceleration); the row-half can take it to
  1-in-2.5. The `f` band is a CONTINUUM the engine must compute, not two
  constants.
- Engine: `RandomTickSystem.tickCropGrowth` —
  `random.nextInt(hydrated ? 3 : 6) == 0` (1-in-3 wet / 1-in-6 dry).
- Divergences: the wet rate is 2.33x the reference's (1/3 vs 1/7), the dry
  rate 2.17x (1/6 vs 1/13), the neighbour-farmland row acceleration is
  absent entirely, the same-crop row halving is absent, and the
  `MOISTURE > 0` consult (the reference grows at the wet rate for the six
  drying-window levels too) has no engine shape (the engine's binary
  farmland flips instantly).
- Fix: port `getGrowthSpeed` whole (the 3x3 ring walk with the /4, the
  moisture consult over the engine's binary farmland_wet — the flattening
  documented at §D2, the row-half pattern) and roll
  `nextInt((int)(25.0F / f) + 1)`.

### A2. The stems grow on the crop band AND fruit on a roll the engine skipped
- Reference: `StemBlock.tick` lines 57-82 — the SAME `25/f` roll gates BOTH
  the age advance (lines 63-65) and the fruit spawn (lines 66-79): a
  mature stem (AGE 7) attempts its fruit ONLY on a passing growth roll
  (~1-in-7 hydrated), first refusing when any fruit already sits
  horizontally (lines 67-71), then picking ONE random horizontal
  (line 73), planting only into AIR over FARMLAND/DIRT/GRASS
  (lines 74-77).
- Engine: `RandomTickSystem.tickCropGrowth` calls
  `MelonBlocks.trySpawnFruit` on EVERY random tick of a mature stem (no
  roll), and the stem's own age advance rides the wrong A1 band.
  `MelonBlocks.trySpawnFruit` itself is 1:1 (the existing-fruit consult,
  one random direction, the air + floor gates — verified).
- Divergences: the fruit spawn fires ~7x too often (every tick vs
  1-in-7), and the age advance carries A1's error.
- Fix: the stem branch takes the SAME `25/f` roll; only a passing roll
  reaches either the age advance or the fruit walk.

### A3. Sugar cane: the AGE-15 accumulator vs the engine's 1-in-3
- Reference: `SugarCaneBlock.tick` lines 28-49 — only a block with air
  above accumulates; `i` counts the column below; `if (i < 3)` the AGE
  (0-15) increments on EVERY random tick, and at `j == 15` the new block
  lands above with AGE 0 while the spawner resets to AGE 0 (lines 40-45).
  The effective column cadence is **1-in-16 random ticks** (the AGE
  accumulator; the 15-tick prelude is the growth time vanilla players
  know). The support consult at line 30: a block sitting on another reed
  skips the check; otherwise `canSurviveOrBreak` DROPS AND REMOVES the
  block on the spot (lines 76-84 — the random tick is a support check
  too).
- Engine: `tickCaneGrowth` — `random.nextInt(3) == 0` (1-in-3) after its
  own support walk. 5.33x the reference's rate; no AGE accumulator model.
- Fix: the cadence becomes the AGE-15 law's 1-in-16
  (`nextInt(16) == 0` — statistically identical to the accumulator for a
  fresh column; the accumulator's own nibble is wire-invisible, see A5).

### A4. Cactus: the same AGE-15 accumulator vs the engine's 1-in-3
- Reference: `CactusBlock.tick` lines 28-49 — the same AGE-15 shape
  (lines 37-45): only an air-above block accumulates, `i < 3` gates, at
  `j == 15` the new block lands and the spawner resets — **1-in-16**.
  Line 43: the spawn arm then calls
  `this.neighborChanged(world, blockpos, blockstate, this)` on the NEW
  block — the new cell's own horizontal-solid consult runs AFTER the
  placement (a bad cell pops the new block immediately, WITH its drop).
- Engine: `tickCactusGrowth` — `random.nextInt(3) == 0` (1-in-3, 5.33x
  too fast), and the growth consult reads the CURRENT top's horizontal
  neighbours instead (the engine refuses the placement pre-hoc; the
  reference places then pops).
- Divergence detail: the engine's refusal arm also consults
  `WorldSolidity.isSolid` where the reference reads the MATERIAL model
  (`getMaterial().isSolid()` — the sign pass's own class,
  `CactusBlock.canSurvive` lines 85-94: a torch, a rail or tall grass
  beside a cactus is material-NOT-solid and does not break it; the
  engine's solidity model must match the material model at this site).
- Fix: 1-in-16 cadence; the growth consult consults the NEW cell's
  neighbours through the material-solidity model; a refused cell places
  then pops with the drop (the reference's own order) — the simpler
  honest form: consult the new cell's four horizontals through
  `MaterialSolidity` before placing, which is behavior-identical for the
  drop walk (the placed-then-popped block's drop is the same item), but
  NOT identical on the wire (the placed-then-popped flash); the seat
  pins the wire-visible law the reference carries (place + pop).

### A5. The AGE nibbles are a whole import band the table isolates as AIR
- Reference: `SugarCaneBlock.AGE` 0-15 (`getStateFromMetadata` lines
  126-127), `CactusBlock.AGE` 0-15 (lines 107-108). Any grown vanilla
  world carries canes and cacti at every nibble in the band (the AGE
  accumulator advances on every random tick a block sits loaded near a
  player — 15 of 16 loaded canes in a lived-in world are NOT at 0).
- Engine: the import table carries `(83, 0) -> minecraft:sugar_cane` and
  `(81, 0) -> minecraft:cactus` ALONE (`AnvilBlockIds` rows); every
  `(81/83, 1..15)` numeric state imports AS AIR (the counted isolation,
  §31.6) — a vanilla world's cane stands and cactus fields VANISH, the
  same data-loss class the sign (twelve states) and rail (eight states)
  passes fixed. The wire side writes nibble 0 for both (the single-type
  model) — client-invisible (the 1.8.8 client renders every AGE
  identically; no model, sound, or logic consumes the cane/cactus
  nibble) — but the IMPORT side loses blocks.
- Fix: map the whole band `(81, 0..15)` and `(83, 0..15)` to the single
  types (the flattening precedent: the farmland moisture band's own
  documented gap-fill at §D2, the banner's nearest-quarter fold), with
  the nibble law recorded in the rows' javadoc. The AGE accumulator's
  observable content (the growth cadence) is restored by A3/A4; the
  nibble itself is ledgered as a documented flattening, the wire table's
  sugar_cane/cactus rows unchanged.

## B. The farmland state laws (the moisture model and its five gates)

### B1. The moisture ladder flattened — and the wire never says 7
- Reference: `FarmlandBlock.MOISTURE` 0-7 (line 19). The tick (lines
  44-56) decays ONE level per random tick (line 48-49); water or rain
  hydrates straight to 7 (line 53-54); `getMetadataFromState` writes the
  level (lines 127-130) and the vanilla client renders the WET skin iff
  the received nibble is exactly 7 (the blockstate `moist` variant — the
  engine must send 7 for wet, any lower value renders dry).
- Engine: a binary `FARMLAND`/`FARMLAND_WET` pair; `FARMLAND_WET`'s
  `legacyMetadata` is 1 (BuiltinBlocks line 191) but the wire path
  (`ChunkSerializer18` lines 217/236 → `LegacyBlockIds.metadataOf`) has
  NO farmland_wet metadata row — the default answers **0**. The engine's
  wet farmland is written on the wire as nibble 0: a real 1.8.8 client
  renders EVERY engine farmland with the DRY skin.
- Fix: `metadataOf`'s table gains `farmland_wet -> 7` (the farmland dry
  row keeps 0). The 8-level ladder itself stays the documented flattening
  (the import table's own javadoc records the 0..7 → dry/wet decision);
  the six intermediate levels are ledgered at §G as the family's named
  tail (the ladder would need six registry states; no observable client
  difference below 7 — the wet skin is the only nibble consumer).

### B2. The rain hydration arm is absent
- Reference: `FarmlandBlock.tick` line 47 — `!world.isRaining(pos.up())`
  in the dry branch: rain alone hydrates farmland to 7, no water needed.
- Engine: `tickFarmlandHydration` consults water alone. The engine HAS
  the rain consult (`EngineWorld.isRainingAt`, the mob code's own site).
- Fix: the wet branch also fires when `world.isRainingAt` reads true at
  the cell above; the dry branch carries the negation.

### B3. The dry-farmland-to-dirt reversion is absent (with the wheat/stem quirk)
- Reference: `FarmlandBlock.tick` lines 50-51 — at MOISTURE 0, when the
  block above is NOT a `WheatBlock` or `StemBlock` (`isUnderCrop` lines
  73-76), the farmland turns to DIRT on the next random tick. THE QUIRK:
  carrots and potatoes are NOT in `isUnderCrop` (only wheat and the
  melon/pumpkin stems) — vanilla farmland dries to dirt UNDER a carrot
  or potato crop, popping the crop (the neighbour bus). Empty dry
  farmland reverts to dirt the same way.
- Engine: `tickFarmlandHydration`'s else-arm only flips wet→dry; a dry
  farmland stays farmland forever, whatever sits on it.
- Fix: the dry arm consults the cell above — a wheat or stem state
  protects; everything else (air, flowers, carrots, potatoes) lets the
  DIRT reversion fire through the standard block-change walk (which pops
  the unsupported crop above it).

### B4. The trample law is absent (both the mob and the player paths)
- Reference: `FarmlandBlock.onFallenOn` lines 59-71 — a LIVING entity
  landing on farmland rolls `world.random.nextFloat() <
  fallDistance - 0.5F` (a plain 1-block hop is fallDistance ≈ 1.1 →
  a 60% trample; a 2-block drop tramples with certainty), gated by
  mobGriefing for non-player entities, turning the farmland to DIRT.
  The roll runs inside the fall path itself — BEFORE the damage consult
  (`super.onFallenOn` carries the fall damage; farmland tramples even
  when the fall deals no damage).
- Engine: NO `onFallenOn` consult exists anywhere — `MobEntity`'s
  landing (`takeFallDamage` lines 11347+) and `PlayerSession`'s
  `consumeFallDistance` path never consult the landed block. Farmland
  on this engine is trample-proof; vanilla's farms require fencing
  exactly because of this law.
- Fix: an onFallenOn consult at both landing sites (mob + player), the
  fallDistance read BEFORE it is zeroed, the nextFloat roll, the
  mobGriefing gate for non-player bodies, the DIRT write. The reference's
  `instanceof LivingEntity` band is the engine's own mob/player pair
  (items and vehicles never trample — and never consult).

### B5. The solid-above→dirt neighbour arm is absent
- Reference: `FarmlandBlock.neighborChanged` lines 88-94 — when the cell
  above turns material-SOLID (a block placed on farmland), the farmland
  becomes DIRT immediately (popping any crop above it, which the crop's
  own support arm carries).
- Engine: `BlockUpdateSystem` has NO farmland arm (verified — no row
  mentions farmland). Placing a stone block on farmland leaves farmland
  under stone forever.
- Fix: the update bus gains the farmland arm in the flora family's own
  shape: the cell above's MATERIAL solidity (the sign pass's
  `MaterialSolidity`, not the collision model — a torch on farmland does
  not revert it) → the DIRT write.

### B6. The hydration consult admits LAVA (and scans below the field)
- Reference: `FarmlandBlock.isNearWater` lines 78-86 — the scan box is
  the farmland's own level and ONE ABOVE (`pos.add(-4, 0, -4)` to
  `pos.add(4, 1, 4)`), and the consult is `getMaterial() == Material.WATER`
  (line 80). LAVA never hydrates; water BELOW the farmland's level never
  hydrates.
- Engine: `tickFarmlandHydration` → `waterWithinFour` scans dy in
  [-1, +1] (the engine hydrates from water one level UNDER the field)
  and consults `FluidBlocks.isFluid` — which admits LAVA (verified at
  `FluidBlocks.buildKinds` lines 104-126: every LAVA identifier is a
  fluid kind). The engine's farmland hydrates beside a lava lake and
  from a water table underneath; the reference hydrates ONLY from water
  at its own level or one above.
- Fix: the scan box narrows to dy [0, +1]; the consult reads the WATER
  kind alone (`FluidBlocks.kindOf(...) == Kind.WATER`). The same
  WATER-only consult fixes A3's cane law (`SugarCaneBlock.canBePlaced`
  lines 62-68: `getMaterial() == Material.WATER` — the engine's
  `waterBeside` admits lava today).

## C. The planting arms (three families the player cannot plant, one till gap)

### C1. Carrots and potatoes cannot be planted
- Reference: `Item.java` line 391-392 —
  `new CropItem(3, 0.6F, Blocks.CARROTS, Blocks.FARMLAND)` (carrot),
  `new CropItem(1, 0.3F, Blocks.POTATOES, Blocks.FARMLAND)` (potato);
  `CropItem.useOn` — face==UP, the soil consult, air above, plant the
  default state, `stack.size--`. (The eat path stays live when the use
  does not plant — `CropItem extends FoodItem`.)
- Engine: NO dispatch plants `CARROTS_STAGE0` or `POTATOES_STAGE0`
  anywhere (verified — every write site of the stage-0 types is the
  registry, the growth switch, the import rows, the structure palette;
  `useFarmingOnTick`'s seed arm handles wheat/pumpkin/melon seeds only).
  The engine has the carrot/potato crop family whole (growth, drops,
  import) but the player cannot start one: a held carrot on farmland
  falls through to the eat path.
- Fix: the farming use arm grows the carrot/potato pair (the CropItem
  law: face==UP already the arm's own shape, the soil consult over both
  farmland forms, air above, stage-0 plant, consume one, the
  `dig.grass` 0.6/1.1 sound pair the seed arm already plays).

### C2. Nether wart cannot be planted — and never grows
- Reference: `Item.java` line 658 — `register(372, "nether_wart", new
  WheatSeedsItem(Blocks.NETHER_WART, Blocks.SOUL_SAND))`: the SAME seed
  law over SOUL_SAND. `NetherWartBlock.tick` lines 38-46 — `i < 3 &&
  random.nextInt(10) == 0` (1-in-10, AGE 0-3), NO LIGHT GATE (nether
  wart grows in the dark), then the PlantBlock support consult. The
  drops: age 3 → 2-4 (+fortune), else exactly 1 (`dropItems` lines
  49-63).
- Engine: the NETHER_WART item exists, the four stage blocks exist, the
  import rows exist, the fortress palette places stages — but NO
  planting dispatch (the farming arm has no nether_wart branch) and NO
  random-tick arm (`RandomTickSystem.randomTick` has no nether-wart
  branch: an imported stage-0 wart NEVER grows).
- Fix: the planting arm (the WheatSeedsItem law over soul_sand) + the
  growth arm in the random-tick dispatch (1-in-10, no light gate, the
  soul-sand support consult the flora family's own arm carries).

### C3. The hoe does not till coarse dirt
- Reference: `HoeItem.useOn` lines 30-46 — GRASS → FARMLAND;
  DIRT variant DIRT → FARMLAND; DIRT variant COARSE_DIRT →
  **regular DIRT** (not farmland — the second click tills it).
- Engine: `useFarmingOnTick`'s hoe arm tills `GRASS_BLOCK` and `DIRT`
  only; `coarse_dirt` is refused (the use falls through — nothing
  happens). Vanilla converts it to dirt on the first click.
- Fix: the tillable consult gains `coarse_dirt → dirt` (the tillBlock
  law's own target), the sound and the wear riding the same path.

## D. The crop drop bands (the harvest rolls)

### D1. RESOLVED-STALE — the drop bands were already whole (the V2 miss, recorded honestly)
- The finding as first recorded claimed the fixed-count drop rows as the
  engine's law. FALSE, and the miss was mine: `DropService.dropsFor`
  (the drop walk every break consults) already carries the full
  reference bands — the wheat/carrot/potato mature walk
  (`3 + fortune` rolls of `nextInt(15) <= 7`, the plant item first, the
  carrots'/potatoes' merged stack, the 1-in-50 POISONOUS_POTATO roll at
  age 7) and the nether wart's age-3 band (`2 + nextInt(3)` + the
  fortune gate) — the javadoc's own "pre-fix rows folded each band to a
  flat count" note records the earlier drop-bands pass that landed it.
  `BlockBehaviorTable`'s fixed rows are the FALLBACK surface
  `dropsFor` never reaches for these families, not the live law.
- The lesson (the taiga wolf row's own class): a V2 read that stops at
  the first table that spells the family has not found the law — the
  DROP WALK sites (`DropService.dropsFor`) own these bands. No fix owed;
  this row stays as the correction record.

## D2. The sub-finds the fix passes surfaced (both landed with them)

### D2a. The seeds' UP-face gate was missing (fixed in PR #70)
- Reference: `WheatSeedsItem.useOn`'s first gate — `face != UP → false`:
  a side click never plants, nothing is spent. The engine's seeds arm
  planted on ANY face. Fixed with the carrot/potato/wart arms carrying
  the same gate.

### D2b. The planting was not silent (fixed in PR #70)
- Reference: `WheatSeedsItem.useOn`/`CropItem.useOn` carry NO playSound —
  vanilla seed planting is silent. The engine's seeds arm played a
  `dig.grass` pair (an engine-side addition); the hoe's till sound also
  had its volume/pitch arguments swapped against the reference's own
  form (`tillBlock` lines 51-58: `(volume+1)/2`, `pitch*0.8`). Both
  aligned.

### D2c. The derived-table pin was stale on develop (repaired in PR #69)
- The banner's eight states (176/177) joined the import derivation at
  dc4cb71a (Wave BN-A) without `AnvilBlockIdsIntegrityTest`'s pinned
  count moving — develop's pin sat stale-red at 1251 against the 1259
  derivation, the same stale-pin class the sign pass caught. PR #69's
  re-pin (1251 → 1289) carries both increments and the attribution.

## E. The vine family (the adjacent discovery — its own pass)

### E1. The vine spread walk is absent
- Reference: `VineBlock.tick` lines 198-273 — the 1-in-4 roll
  (`nextInt(4) == 0`); the 9x9x3 density gate (counting the same-family
  cells at dy -1..+1 within ±4; a sixth neighbour sets the flag and
  blocks the grow-up arm); the random direction pick; the grow-up arm
  (only under the density gate, under 255, into air, keeping only the
  faces that roll `nextBoolean` AND can grow on a solid); the
  grow-down arm (into air below, the face mask halved by four
  `nextBoolean` rolls); the merge arm (into a vine below, the shared
  faces ANDed in).
- Engine: NO vine arm in `RandomTickSystem` (the dispatch was read
  whole — vines never spread, never grow down, never merge) and NO vine
  support arm in `BlockUpdateSystem` (`neighborChanged` lines 190-196:
  a vine with no connections DROPS — the engine's single-state vine
  (metadata 15) floats forever when its wall departs).
- Engine shape note: the engine's vine is ONE state, nibble 15 (all
  four faces) — the reference's vine is a 4-bit connection mask whose
  nibble the import table isolates: `(106, 15) -> vine` ALONE is
  mapped; a vanilla wall vine (nibbles 1-14, e.g. 4 = WEST-attached)
  imports AS AIR — the same isolation class as A5. The spread walk's
  face model and the import band both ride this pass's own registry
  decision (a 15-state vine family vs the flattened single state).
- Plan: recorded for the NEXT pass (the vine family is its own audit —
  the face-mask registry decision belongs with the spread/support/
  import laws, one coherent wave).

## F. Verified-1:1-already (no fix needed — recorded for the record)

- The stem fruit walk's own shape (the existing-fruit consult, one
  random horizontal, air + farmland/dirt/grass floor) —
  `MelonBlocks.trySpawnFruit` matches `StemBlock` lines 66-79 line for
  line; only its CALL SITE (A2) is wrong.
- The growth light gates: the crops' `>= 9` at the cell above matches
  `WheatBlock` line 36 (the stems' the same); the nether wart's
  no-light law is C2's to add.
- The bone-meal grower: `stage + 2 + nextInt(4)` matches
  `WheatBlock.growFully` lines 47-54 / `StemBlock.grow` lines 84-87.
- The farmland hydration scan box's horizontal extent (±4) matches;
  only the dy band and the lava consult are B6's.
- The seed planting arms that exist (wheat/pumpkin/melon seeds over
  farmland, air-above, consume one) match `WheatSeedsItem.useOn`.
- The cocoa growth arm (1-in-5, AGE 0-2) matches `CocoaBlock.tick`
  lines 44-50.
- The mushroom spread (1-in-25, the density gate, the four-step walk)
  — the VIII8 slice already carries it.
- The farmland import rows (60, 0-7 → the dry/wet pair) — the
  documented flattening, consistent with B1's wire fix.

## G. The three-verification record (to be closed per finding)

Per the standing protocol, every finding above needs three independent
verifications before its fix lands. V1 (the reference read, with line
anchors) and V2 (the engine site read, whole) are complete for every
finding — recorded in the sections above. V3 is the seat suite: each
fix lands with a parity test that pins the restored law, and the
pre-fix tree verified failing (the stash-verified pattern every prior
pass carried). The closure per finding:

- [x] A1+A2 (the growth formula + the stem roll): CLOSED — PR #69
      (`FarmingGrowthCadenceParityTest`: the f-speed pins per shape, the
      seeded statistical bands, the stem's exact first-pass index;
      stash-verified 7/7 failing).
- [x] A3+A4+A5 (the AGE-15 cadences + the import bands): CLOSED — PR #69
      (the 1-in-16 statistical bands, the cactus new-cell consult with the
      same-tick pop + item, the stacked-cane short-circuit, the
      water-loss pop, the (81/83, 0..15) import seat, the pin 1251 → 1289).
- [ ] B1 (the nibble-7 wire): OPEN — the serializer seat reads 7 for wet.
- [ ] B2+B3+B5 (rain, the dry→dirt reversion with the wheat/stem
      exception, the solid-above arm): OPEN — the scene seats.
- [ ] B4 (the trample): OPEN — the fall-path seat (the player hop, the
      mob drop, the mobGriefing gate, the items-never-trample band).
- [ ] B6 (the water-only consult): PART-CLOSED — the CANE's water-only
      consult landed with PR #69 (the lava-beside refusal rides the
      waterBeside consult); the FARMLAND's scan box (dy [0,+1] and the
      water-only consult) stays OPEN with the B pass.
- [x] C1+C2+C3 (the planting arms): CLOSED — PR #70
      (`FarmingAcceptanceTest`'s four live seats +
      `NetherWartGrowthParityTest`; stash-verified 6/6 failing), the
      sub-finds D2a/D2b (the face gate, the silent planting, the till
      sound form) landing with it.
- [x] D1 (the drop rolls): CLOSED-STALE — no fix owed (§D1: the bands
      were already whole; the V2 miss recorded).
- NOTE: the cane's neighbour-arm water consult (the bus-side
      `tickCaneSupport` reading soil alone where the reference's
      `neighborChanged → canSurviveOrBreak` consults the water too)
      was noted during PR #69's review and rides the B pass (the
      farmland/cane bus arms share the consult's shape).

## H. The future passes' candidate list (recorded, not owed)

The broad sweep's notes for the next discovery passes, each needing its
own audit before any claim: the vine family (§E), the snow-layer
placement/melt and ice laws, the door family's double-door propagation,
the noteblock/jukebox/daylight-sensor band laws, the fire family's
spread ladder depth, and the falling-block family's landing laws.
