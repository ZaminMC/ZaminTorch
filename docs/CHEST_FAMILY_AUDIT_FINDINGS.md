# The chest family's F-DC1 correction + the F-TC1 discovery — the twentieth verifier pass

Date: 2026-10-30 (the twentieth continuous verifier pass). Branch:
`audit/verifier-pass-20-discovery` (off `origin/develop` @ `be3f76b6`).
The pass's vehicle: **PR #86** (`fix/double-chest-merged-inventory-parity-audit`)
— the F-DC1 correction, the eighteenth pass's architecture-scale root,
carried whole after this pass completed its independent re-verification.

The F-DC1 discovery record lives on the eighteenth pass's branch
(`audit/verifier-pass-18-discovery`, `docs/HOPPER_CONTAINER_FAMILY_AUDIT_FINDINGS.md`)
— the twin scan, the four surfaces, the three verification passes. This
record covers the CORRECTION (what landed, the seats, the stash evidence,
the two re-pins the seats forced) and the NEW findings the seats exposed.

---

## F-DC1 — the double chest as an inventory: RESOLVED via PR #86

**Subsystem:** containers / the chest family. Status: **fix-ready → PR #86
opened** (the three verification passes: V1+V2+V3 on the eighteenth pass's
record; this pass's independent re-verification below).

### What landed (one consult, four surfaces)

`ChestPairs` (new, `server/chest/`) — the reference's
`ChestBlock.getInventory` (lines 339-370) whole:

- **The twin scan** over the four horizontal neighbors in
  `Direction.Plane.HORIZONTAL`'s own order (NORTH, EAST, SOUTH, WEST —
  the last match wins, the reference's own no-break loop), the
  `block == this` identity law (plain joins plain, trapped joins trapped,
  every facing state AND the base form — the engine's `isPlainChest`).
- **The `isLocked` gate on both halves** (lines 402-421): a solid block
  or a sitting ocelot above either one refuses the whole view. The solid
  consult is the reference's OWN `Block.isSolid()` law
  (`material.isSolidBlocking() && isCube() && !isSignalSource()`,
  Block.java lines 274-276): the glass family, the chests, the slabs, the
  stairs, the fences, the leaves (Material.LEAVES is translucent), the
  ice, the TNT, the glowstone/sea lantern and the redstone block above
  NEVER lock; stone, packed ice, a double slab, a bookshelf or a furnace
  DOES.
- **The join order** (lines 360-364): a WEST/NORTH twin comes FIRST —
  the GUI's left half is the western one.

The four consumers:
1. **The GUI** (`openChestOnTick`): Open Window count 54, the
   `DoubleInventory` title law (the first half's custom name, then the
   second's, then `container.chestDouble` = "Large Chest"), Window Items
   90 slots (54 pair + 27 main + 9 hotbar), the halves **PINNED** on the
   session at the open (the reference's one-time DoubleInventory
   construction — clicks/view/close read the pin, never a fresh world
   consult; a half dying mid-window rejects the window like the single
   stale shape), the click router's merged bands (modes 0/1/2/4 + the
   quick-craft paint + the refusal classifier), the pair-aware
   quick-move, both lids swinging (`DoubleInventory.onOpen` lines
   110-113), both trapped halves' neighbor updates, the close closing
   both.
2. **The transfers** (`HopperSystem.containerSlotsAt`'s chest arm over
   `getInventoryAt` lines 460-463): the merged 54 slots in join order;
   the locked view falls to the ENTITY fallback exactly like the
   reference's null; the pair's slot writes mark BOTH halves dirty
   (`DoubleInventory.markDirty` lines 99-102 — a transfer into either
   half re-arms the comparators reading both).
3. **The comparator**: the MERGED fullness over 54 slots
   (`InventoryMenu.getAnalogSignal` over the DoubleInventory); the locked
   view answers 0.
4. **The placement** (`canBePlaced` lines 244-284 through
   `BlockItem.useOn` line 43's `World.canPlace`): the no-triple gate —
   never beside TWO same-type chests, never beside an already-doubled
   chest.

Plus the viewer family: the merged window counts on BOTH halves (the
trapped-chest probe, the ocelot perch's `containerViewersAt`).

### The independent re-verification (this pass)

The seats were written from the source walk BEFORE running, then
executed against both trees. **8 of 9 seats failed on the pre-fix tree
with exactly the predicted failure modes** (the west-first join order
never receiving; the full clicked half blocking the push; the merged
fullness never read; the west comparator never waking on east writes;
the third chest PLACING beside the pair; the cursor picking up the
clicked half's stack through the single window; the locked chest
OPENING; the locked chest RECEIVING) — and 9/9 with the fix (the ninth
seat compile-gated by the pin seam, the documented "seams absent"
shape). The execution-path challenge: no compensating merged view
exists anywhere in the engine (the ChestManager map is the sole chest
state); the persistence stays per-half (vanilla's own shape); the
per-half spill on break confirmed not-a-divergence (the eighteenth
pass's own verification).

### The two re-pins the seats forced

1. **ChestFacingTest's third placement** — the pre-fix seat placed a
   chest beside an ALREADY-DOUBLED pair, a placement vanilla's
   `canBePlaced` never allows (`BlockItem.useOn` line 43 consults
   `World.canPlace`; the isDoubleChest arm refuses). The seat had pinned
   the ENGINE's own quirk against the reference's gate. Re-pinned onto a
   lone neighbour two cells clear of the pair — the same re-facing arm,
   the legal geometry.
2. **The re-facing walk's family consult** (`sameChestKindAt`) — it
   matched only the facing states, not the base form, while the no-triple
   gate (and vanilla's `block == this`) sees every form. Widened — the
   walk and the gate now agree on what "a chest" is.

### The regression record

The chest + hopper + container lanes 54/0; the redstone + placement +
persistence + decoration lanes green; the block package 309/0; the
entity + dispenser + furnace + brewing sweep 650/0; bukkit + launcher
green. The protocol module's chest wire suites green;
`QuickCraftDragWireTest`'s packet-48 timeout fails IDENTICALLY on the
pre-fix tree (stash-verified) — the documented pre-existing flake
(Wave IO-F's record), not wave-caused.

---

## F-TC1 — the trapped chest's viewer signal never reaches a wire (CONFIRMED — the pass's own find, awaiting its lane)

**Subsystem:** redstone / the trapped chest. Status: **confirmed** (the
three passes below). Severity: M (a whole signal family dead: every
trapped-chest viewer circuit — the classic trapped-chest alarm — reads
0). Discovered by F-DC1's viewer-signal seat failing on BOTH trees.

**Engine:** `RedstoneSystem.signal` (the World.getSignal port) routes
through `isSignalSolid(type)` → `directNeighborSignal(pos)` (the
re-radiation arm) when the type is "signal-solid". `isSignalSolid`'s
first conjunct is **`WorldSolidity.isSolid(type)` — the MOVEMENT
consult** — which classifies the CHEST family as solid (a chest blocks
bodies). So `signal(trapped chest)` answers the re-radiation of strong
power INTO the chest — 0 — and never reaches `ownSignal`'s trapped-chest
arm (the `setTrappedChestViewers` probe, lines 711-721), which is where
the viewer count lives.

**Reference:** `World.getSignal` (lines 2357-2361):

```java
return block.isSolid() ? this.getDirectNeighborSignal(pos)
                       : block.getSignal(this, pos, blockstate, dir);
```

— where `Block.isSolid()` = `material.isSolidBlocking() && isCube() &&
!isSignalSource()` (Block.java lines 274-276). The trapped chest:
`isCube()` FALSE (ChestBlock lines 46-48) → NOT solid → the chest's OWN
`getSignal` — `clamp(viewerCount, 0, 15)` (ChestBlock lines 383-395).

**Verification passes:**
- **V1 (source, 2026-10-30):** the walk above — both sides read
  line-by-line; the engine's consult is the movement model, the
  reference's is the render-cube + material + signal-source law; the
  chest family is the observable casualty (the one non-cube family with
  a non-zero ownSignal: glass/slabs/stairs/fences emit nothing either
  way; the trapped chest emits the viewer count).
- **V2 (behavioral, 2026-10-30):** the scratch probe (not committed):
  a single trapped chest, a wire beside it, the window opened through
  the real use path — **the wire reads 0** where vanilla reads 1. Run
  on the develop tree WITHOUT this pass's changes (stash-verified) —
  pre-existing, not F-DC1-caused.
- **V3 (independent, 2026-10-30):** (a) Intentional? The WORKLOG claims
  Slice 49b landed the viewer signal; the probe proves the wire read
  dead — a translation defect, not a design choice. (b) Compensating
  behavior? The comparator's read of a trapped chest is the ANALOG arm
  (the container fullness — a different value, vanilla's own split);
  nothing else consumes `ownSignal`'s trapped arm. (c) The routing law:
  the reference's ternary is `Block.isSolid()` — the same consult
  F-DC1's lock gate already ported (`ChestPairs.isLockedByBlock`); the
  fix is the consult swap in `isSignalSolid`, with the re-radiation set
  narrowing for the partial-shape and translucent families alike
  (glass, slabs, stairs, fences, chests, leaves, TNT, ice — each needs
  its own seat: the stone re-radiation positive stays, the glass
  re-radiation negative arrives).

**Proposed minimal correction:** `isSignalSolid` reads the reference's
`Block.isSolid()` law (the `ChestPairs.isLockedByBlock` consult shape,
hoisted to a shared helper) — one consult swap, the exclusion list's
hand-rolled signal-source arms subsumed by the law's own
`!isSignalSource()` conjunct. Own lane: the swap touches the whole
re-radiation set; it needs its own seats (the trapped viewer positive,
the glass/leaf re-radiation negatives, the stone positive).

**Regression risks:** the re-radiation narrowing — any engine circuit
relying on glass/slabs/stairs/fences/leaves re-radiating strong power
would change (none should exist: vanilla never re-radiates through
them).

---

## F-EC1 — the ender chest's solid-above gate reads the movement consult (SUSPECTED — minor)

**Subsystem:** the ender chest family. Status: **suspected** (V1
source-only). `openEnderChestOnTick` gates the open on
`WorldSolidity.isSolid(block above)` — the MOVEMENT consult — where the
reference (`EnderChestBlock.use` line 80) reads
`world.getBlockState(pos.up()).getBlock().isSolid()` — the same
`Block.isSolid()` law F-DC1's lock gate ported. Divergences: GLASS
above (movement-solid, isCube-false) — the engine refuses the open,
vanilla allows; LEAVES/TNT/ICE above (movement-solid, translucent
material) — same; a CHEST above (movement-solid, isCube-false) — same.
The fix is one consult swap onto the shared helper (F-TC1's own lane
carries the helper hoist). Needs the seats before its PR.

## F-DC1-tail — the single quick-move's silent partial merge (SUSPECTED — minor)

`ChestBlockEntity.quickMoveIn` (the SINGLE walk, pre-existing): when
pass 1 merges into partials but pass 2 finds no empty slot, the merged
writes landed WITHOUT the bump (no comparator wake, no serial move for
the other viewers). The PAIR walk (`quickMoveInPair`, this pass) tracks
`changed` and marks both halves — the single walk should adopt the same
tail. V1 source-only; the observable is narrow (a full chest with
partial room: the other viewer's window and the comparator miss the
merged part).

---

## The coverage ledger row

The chest family: **F-DC1 RESOLVED via PR #86** (the merged view whole,
all four surfaces + the viewer family); the single-chest surfaces, the
spill, the type split, the facing states, the persistence re-verified
this pass (all green). The redstone family: F-TC1 CONFIRMED (the
trapped viewer signal dead on wires — its own lane queued). The ender
chest: F-EC1 suspected (the solid-above consult). The hopper family's
standing tails unchanged (F-HP2 under verification behind F-HP3,
F-HP3/F-HP4 suspected).

## The queue after this pass

1. **F-TC1** (this pass's confirmed find) — the consult swap + its
   seats; the highest-confidence next correction.
2. **F-EX1..F-EX4** (the nineteenth pass's explosion family) — the
   entity-gather coverage first.
3. **F-FL4** (the nether/End fluid simulation) — the architecture tail.
4. **F-HP3** (the cart position convention) — unblocks F-HP2.
5. **F-EC1 / F-DC1-tail** (the suspected minors).

---

## The twenty-first verifier pass's session record (2026-10-31)

**F-TC1 RESOLVED via PR #87** (`fix/trapped-chest-viewer-signal-parity-audit`):
the consult swap whole — the new shared `BlockSolidity.isSolid`
(material.isSolidBlocking && isCube && !isSignalSource over the engine's
own tables) at all six signal-routing sites (signal, the comparator's
through-solid arm, the wire's climb/descent bands, the wire's
onAdded/onRemoved wakes, wakeComparators) + the same root cause's four
further casualties: F-TC1-a the redstone block's own 15 (the new
ownSignal arm — the block was signal-solid and answered the
re-radiation arm's 0), F-TC1-b the trapped chest's UP-only strong arm
(the new directSignal arm), F-TC1-c the viewer-count wakes at both ends
(the onOpen/onClose pos + pos.down() pair — the engine's close woke
NOTHING: a wire that had read the count never released it), F-TC1-d the
re-radiation set narrowed whole (glass/leaves/slabs/stairs/chests/...
never conduct). TrappedChestViewerSignalParityTest 6/0 — the stash
verification 6/6 red unfixed. The findings doc:
`docs/SIGNAL_CONSULT_PARITY_FINDINGS.md` (rides the PR).

**F-EX2 RESOLVED via PR #88** (`fix/explosion-sleeping-eye-frame-parity-audit`):
the sleeping player's blast knockback aims at the LYING eye
(PlayerEntity.getEyeHeight's 0.2 frame; the sneak's -0.08 composed).
The three passes complete; ExplosionSleepingEyeFrameParityTest 2/0 —
the stash verification failing unfixed on exactly the sleeping seat,
the standing control green both ways. The record rides the PR's
EXPLOSION_FAMILY_AUDIT_FINDINGS.md section.

**The new suspected finds (V1 source-only, their own lanes queued):**
- **F-SP1 — the SUPPORT consult family**: `World.isSolidBlockingCube` /
  `hasSolidTop` = `material.isSolidBlocking() && isCube()` (NO
  signal-source conjunct) — the torch/door/rail/lever/button/wire-floor/
  wall-mount sites all read the movement consult (a torch hangs on glass
  in the engine where vanilla fells it; a wire sits on glass). The
  correction is mechanical through BlockSolidity's isCube + the material
  consult once verified.
- **F-DS1 — the double slab's HALF-height collision row**:
  `CollisionShapes.boxesOf`'s `endsWith("_slab")` catches the double
  forms — bodies sink to the mid-line; the reference's double slab is a
  full cube.
- **The eye-height consult census**: the engine's other hardcoded 1.62
  sites (the eat-sound origin, the random-tick cell walk, the ray
  consults) each need their own reference comparison.

**The queue after this pass:** F-EX3 (the BlastResistanceTable census)
and F-EX4 (the createFire arm — the wrong-world bed + the ghast
fireball) from the nineteenth pass's explosion family; F-EX5 (the punch
deflection's skull exclusion, the twentieth explosion pass's find);
F-HP3 (the cart position convention, unblocking F-HP2); F-SP1/F-DS1
(the new suspects); F-EC1 (the ender chest's solid-above gate — rides
F-SP1's helper); F-DC1-tail (the single quick-move's silent partial
merge). PRs #85/#86/#87/#88 all open against develop awaiting review.
