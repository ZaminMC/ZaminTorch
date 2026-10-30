# The signal-consult parity findings (F-TC1's lane)

The reference's `Block.isSolid()` (Block.java lines 274-276) — the SIGNAL
re-radiation consult — versus the engine's movement consult. This document
records F-TC1's correction whole: the finding, the three verification
passes (recorded by the twentieth verifier pass), the fix's own extension
(the same root cause's full casualty census, discovered while preparing
the correction), the stash-discriminated seats, and the regression
record.

---

## F-TC1 — the signal consult reads the movement model (RESOLVED via this PR)

**Subsystem:** redstone / the signal routing. Status: **resolved** (the
three passes below, the fix on this branch). Severity: M-to-H (a whole
signal family dead plus a second dead source family — see the census).

**The root cause.** The engine had ONE solidity consult —
`WorldSolidity.isSolid`, the MOVEMENT model (what blocks a body) —
standing in for the reference's `Block.isSolid()` at every signal-routing
site. The reference's law:

```java
// Block.java lines 274-276
public boolean isSolid() {
    return this.material.isSolidBlocking() && this.isCube() && !this.isSignalSource();
}
```

```java
// World.java lines 2358-2362 — the routing ternary
return block.isSolid() ? this.getDirectNeighborSignal(pos)
                       : block.getSignal(this, pos, blockstate, dir);
```

**The consult sites swapped** (six, all the same root cause):

1. `RedstoneSystem.signal` — the `World.getSignal` port (the re-radiation
   ternary itself).
2. `RedstoneSystem.comparatorInput` — `ComparatorBlock.getInputSignal`
   line 114's through-solid arm (a comparator never reads a container
   through the glass, a chest or a slab).
3. `RedstoneSystem.computeWirePower` — `RedstoneWireBlock.doUpdatePower`
   lines 129 + 133 (the climb and descent bands: the glass, the chests
   and the translucent families are TRANSPARENT to the wire's climb —
   the dust reads the wire DOWN through them, never UP over them).
4. `RedstoneSystem.onBlockAdded`'s wire arm — `onAdded` lines 191-194
   (the arrival wake: the wire above a solid cube, the wire below
   everything else).
5. `RedstoneSystem.onBlockRemoved`'s wire arm — `onRemoved` lines 216-219
   (the same pair on departure).
6. `RedstoneSystem.wakeComparators` — `World.updateNeighborComparators`
   line 2708 (the comparator wake's through-solid walk).

**The correction.** A new shared consult —
`net.zaminmc.torch.server.block.BlockSolidity.isSolid` — porting the
reference's three conjuncts over the engine's own tables:

- `material.isSolidBlocking()` (Material.java lines 100-102 = `!translucent
  && blocksMovement()`) over `MaterialSolidity` (the twelfth pass's
  material model);
- `isCube()` — the full-box collision shape (`CollisionShapes.isFullCube`)
  MINUS the reference's own full-box isCube-false families (the glass
  pair — GlassBlock lines 25-27 + StainedGlassBlock lines 60-62; the
  beacon — BeaconBlock lines 54-56; the piston bases — PistonBaseBlock
  lines 223-225) PLUS the double slab (SlabBlock's own `isDouble()` — the
  engine's flat `double_*_slab` ids keep the joined pair's full cube);
- `!isSignalSource()` — the true-override census (the reference grep
  whole): the redstone block (RedstoneBlock lines 16-18) is the ONLY
  member that survives the first two conjuncts; the trapped chest
  (ChestBlock lines 377-380), the daylight detector, the buttons, the
  plates, the levers, the wire, the diodes, the redstone torches, the
  tripwire hook and the detector rail each die at isCube or the material
  conjunct first.

The old `isSignalSolid`'s hand-rolled exclusion list (the wire, the
torch, the repeater, the comparator, the piston family, the daylight
detector, the detector rail) is subsumed by the law's own conjuncts.

**The same root cause's further casualties — the fix's extension.**
Preparing the correction exposed four more divergences riding the same
defect, each repaired in this PR as inseparable parts of the one root
cause:

- **F-TC1-a — the redstone block never powered a wire.** The reference's
  `RedstoneBlock.getSignal` (lines 21-23) answers 15 weakly in EVERY
  direction, and the block's `isSignalSource()` (lines 16-18) keeps it
  out of the re-radiation set. The engine's consult classified it
  signal-solid: `signal(redstone block)` answered the re-radiation arm
  (`directNeighborSignal` — the strong power entering it, usually 0) and
  never reached an own-emission arm (none existed). THE most basic
  redstone circuit — a wire beside a block of redstone — read 0.
  The fix: the consult swap (the block is a signal source, not solid)
  PLUS the new `ownSignal` arm answering 15.
- **F-TC1-b — the trapped chest's strong arm was missing.** ChestBlock
  lines 397-400: `getDirectSignal` is UP-ONLY — the block UNDER the
  trapped chest receives the viewer count (the consumer below asks with
  dir=UP), which it then re-radiates to its own adjacent wires. The
  engine's `directSignal` had no chest arm. The fix: the new arm
  (`dir == UP ? the viewer count : 0`) over the same
  `trappedChestViewers` probe the weak arm reads.
- **F-TC1-c — the viewer-count wakes were wrong at both ends.** The
  reference's `ChestBlockEntity.onOpen` (lines 312-314) and `onClose`
  (lines 322-324) each wake the chest's own cell AND the cell below it
  (`updateNeighbors(pos)` + `updateNeighbors(pos.down())` — the strong
  arm's consumer sits one further out). The engine's open path woke the
  chest's cell alone (twice — the use arm carried a redundant copy), and
  the close path woke NOTHING: a wire that had read the count NEVER
  released it. The fix: `trappedChestViewersChangedOnTick` (the pair),
  called from the open (after the window assignment), the client close
  and the swap-close — the close wakes run AFTER the window state clears
  so the probe reads the departed count; the stale-window gate (the
  block must still be the trapped family, the reference's own
  `this.getBlock() instanceof ChestBlock`) refuses a broken chest's
  notification.
- **F-TC1-d — the re-radiation set was too wide everywhere.** The glass,
  the leaves, the single slabs, the stairs, the fences, the walls, the
  panes, the farmland, the hoppers, the chests, the ice, the TNT, the
  glowstone, the sea lantern, the closed doors, the trapdoors, the
  beacon and the rest of the isCube-false / translucent census all
  re-radiated strong power in the engine where vanilla answers their own
  (zero) emission — the classic "power through glass" family. The fix:
  the consult swap whole (the seats below pin the glass, the leaves and
  the stone control).

**Verification passes** (the three-pass threshold; the first three
recorded by the twentieth verifier pass on the audit branch, the fourth
added by this correction):

- **V1 (source, 2026-10-30):** both sides read line-by-line — the
  engine's consult is the movement model; the reference's is the
  render-cube + material + signal-source law; the chest family is the
  observable casualty (the one non-cube family with a non-zero
  ownSignal).
- **V2 (behavioral, 2026-10-30):** the scratch probe — a single trapped
  chest, a wire beside it, the window opened through the real use path —
  read 0 where vanilla reads 1. Run on the develop tree (stash-verified
  pre-existing).
- **V3 (independent, 2026-10-30):** the intentional-design check (the
  WORKLOG claims Slice 49b landed the viewer signal — the probe proves
  the wire read dead, a translation defect), the compensating-site
  search (the comparator's analog arm reads a different value by
  vanilla's own split; nothing else consumes the trapped arm), and the
  routing law (the same `Block.isSolid()` consult F-DC1's lock gate
  already ported).
- **V4 (the correction's own discrimination, 2026-10-31):** the
  stash-verified parity suite below — 6/6 red on the develop tree, 6/6
  green with the fix.

**The parity seats** (`TrappedChestViewerSignalParityTest`, 6 tests —
the stash verification failing UNFIXED on exactly the six and green
whole with the fix):

1. **The weak arm** — the wire beside an OPEN trapped chest reads 1 (the
   viewer count), 0 after the close (the close wake carrying the pair).
2. **The strong arm** — the wire beside the stone UNDER an open trapped
   chest reads 1 (the UP-only `getDirectSignal` re-radiated by the
   carrier), 0 after the close.
3. **The redstone block** — the wire beside a block of redstone reads
   the full 15.
4. **The re-radiation negatives** — the lever-fed repeater into GLASS
   and into LEAVES: the wires beside them stay 0 over 3-second
   never-windows; the STONE control re-radiates the full 15 (the lever
   input isolates the consult — a redstone-block input would fail the
   control unfixed through F-TC1-a's own deadness).
5. **The comparator's through-solid arm** — the chest behind GLASS: the
   comparator never powers and the wire stays 0 over a 3-second
   never-window; the STONE control reads the fullness 1.
6. **The wire's descent band** — the powered wire UNDER a glass cell
   feeds the wire at the glass's own level (15 - 1 = 14 through the
   descent band); the STONE control blocks the band (0).

**Regression record:** the redstone package 30/0; the chest + hopper +
piston packages 51/0; the block package 309/0; the container +
interaction lanes 14/0 (ContainerApi, ContainerLock, ContainerOpenEvent,
BlockInteraction, InteractionEvent); the furnace + brewing +
BlockEntityApi lanes 21/0; the protocol module's suite (the
QuickCraftDragWireTest pre-existing flake documented by PR #86's record
— same disposition).

**Remaining risks:** the re-radiation narrowing — any engine circuit
relying on glass/slabs/stairs/fences/leaves conducting strong power
changes behavior (none should exist: vanilla never conducts through
them; the full lanes above found none).

---

## The suspected findings discovered during the fix (NOT repaired here)

### F-SP1 — the SUPPORT consult family (SUSPECTED — V1 source-only)

The reference carries a THIRD solidity law: `World.isSolidBlockingCube`
(lines 1754-1766) / `World.hasSolidTop` (lines 1740-1752) =
`material.isSolidBlocking() && isCube()` — the SUPPORT consult, WITHOUT
the signal-source conjunct (a torch hangs on a redstone block; a wire
sits on one). The engine reads the movement consult at every support
site (the RedstoneSystem sites alone): the torch's attachment
(`torchSupported`), the door's floor (`World.hasSolidTop`, DoorBlock),
the rail's bed and ascending headwall (AbstractRailBlock lines 88-102),
the lever's and button's mounts, the wire's floor
(`RedstoneWireBlock.canBePlaced` lines 92-94), and the wall-mount
placement consult (`canAttach`'s `isSolidBlockingCube`). The observable
divergences: a torch hanging on GLASS (vanilla: it falls — glass is not
a blocking cube), a rail on glass, a wire on glass or on a single slab
(the engine keeps them all), and the inverse — the stairs/slab-half
refinements `hasSolidTop` carries (lines 1745-1748: a TOP stair or a
top slab has a solid top, a bottom one does not). Needs its own lane
with its own seats; the consult helper (`BlockSolidity.isCube` +
`MaterialSolidity`) makes the correction mechanical once verified.

### F-DS1 — the double slab's collision row (SUSPECTED — V1 source-only)

`CollisionShapes.boxesOf`'s slab rows match `value.endsWith("_slab")` —
the DOUBLE forms (`double_stone_slab`, `double_wooden_slab`, the twelve
others) carry the HALF-height box (0, 0, 0, 1, 0.5, 1). The reference's
double slab is a full cube (SlabBlock's `isDouble()` — the shape, the
collision and the render all whole). The observable: a body standing on
a double slab sinks to its mid-line; `supportY` answers 0.5. Discovered
by F-TC1's isCube consult (the double slab must be carved INTO the cube
set — the consult does this itself; the collision row is the separate,
unrepaired casualty). Needs the slab family's own lane.

---

## The coverage ledger row

The redstone signal routing: **F-TC1 RESOLVED via this PR** (the consult
swap whole + the redstone block's emission + the trapped chest's two
arms + the viewer-count wakes at both ends). The re-radiation set
narrowed to the reference's own law. The support-consult family
(F-SP1) and the double-slab collision row (F-DS1) recorded as suspected,
their own lanes queued.
