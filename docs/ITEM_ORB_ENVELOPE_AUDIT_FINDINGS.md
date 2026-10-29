# The item/orb envelope family audit — the sixteenth pass's discovery record

The §22 queue's eldest named remainder (Wave IO-F's own closing row): "the
item/orb envelope tails — the item's lava fizz (the flag/25-tick gate + the
pop + the fizz sound); the orb's own lava fizz (unconditional) + the igniter
attraction (the 8-block nearest-player pull)". This doc records the whole
discovery BEFORE any code changed, per the house protocol: three independent
verifications per finding (the reference line-by-line, the engine site
whole-read, the stash probe failing on the pre-fix tree) before the fix.

---

## §A The reference law (verification 1 — every anchor re-derived from
`reference/1.8.8`, the house rule: the tree overrules memory)

### A1 The item's lava fizz — `ItemEntity.tick` lines 75-87

The gate, verbatim:

    boolean flag = (int)this.lastX != (int)this.x
                || (int)this.lastY != (int)this.y
                || (int)this.lastZ != (int)this.z;
    if (flag || this.ticks % 25 == 0) {
        if (this.world.getBlockState(new BlockPos(this)).getBlock()
                .getMaterial() == Material.LAVA) {
            this.velocityY = 0.2F;
            this.velocityX = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
            this.velocityZ = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
            this.playSound("random.fizz", 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
        }
        if (!this.world.isClient) { this.mergeWithNearbyItems(); }
    }

- The **crossing flag**: the Java `(int)` truncation of the pre-move
  position (`lastX/Y/Z`, set at `ItemEntity.tick` lines 69-71 — the same
  values `baseTick` already stored) against the post-move position — the
  body crossed a BLOCK-CELL boundary this tick. Verbatim law: the cast, not
  the floor.
- The **poll**: `this.ticks % 25 == 0`. `ticks` is the base entity's own
  lifetime counter, incremented by `World.updateEntity` (World.java line
  1355: `entity.ticks++` BEFORE `entity.tick()`) — an INDEPENDENT clock
  from the item's despawn `age` (which `setNeverDespawn` seeds to -6000,
  `resetAge` to 4800, `makeFakeItem` to 5999, and which pauses at -32768).
  A fused `age % 25` poll would drift wherever an age seed lands — the
  poll needs its own counter.
- The **consult cell**: `new BlockPos(this)` = `floor(x), floor(y), floor(z)`
  with `y` the box BOTTOM (the 1.8.8 position IS the feet) — the body's
  OWN cell, NOT the -0.2 feet cell the `beforeCollision` consult rides.
- The **material test**: `getMaterial() == Material.LAVA` — the MATERIAL
  consult, not the block id: both `Blocks.LAVA` and `Blocks.FLOWING_LAVA`
  answer `Material.LAVA` (the engine's `FluidBlocks.Kind.LAVA` covers the
  same set: `lava`, `flowing_lava`, `falling_lava`, `still_lava`, every
  level form).
- The **response**: the 0.2 vertical pop, the ±0.2 horizontal scatter
  (two `nextFloat()` draws per axis), and `random.fizz` at volume 0.4F,
  pitch `2.0F + nextFloat() * 0.4F` — an entity `playSound` with NO
  except-player: the broadcast every player in earshot hears.
- The **placement**: between the move (line 74) and the drag (lines
  89-103) — the fizz's written velocities ride the SAME tick's drag
  (`* f` horizontal, `* 0.98` vertical, the grounded `-0.5` flip). A
  popping item inside lava is airborne (the lava cell is not a support),
  so the pop survives at 0.196 after the vertical drag.
- The item does NOT burn away through this arm — the fizz only re-pops
  it (the burn clock is a different family; see §D's T1).

### A2 The orb's lava fizz — `ExperienceOrbEntity.tick` lines 71-76

Verbatim: the SAME consult at `new BlockPos(this)`, the SAME response —
but **UNCONDITIONAL**: no crossing flag, no 25-tick poll, every tick:

    this.velocityY -= 0.03F;                                    // line 70
    if (this.world.getBlockState(new BlockPos(this)).getBlock()
            .getMaterial() == Material.LAVA) {
        this.velocityY = 0.2F;
        this.velocityX = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
        this.velocityZ = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
        this.playSound("random.fizz", 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
    }

Placement: AFTER the pre-move gravity, BEFORE the move — the popped
velocity feeds the same tick's move (then the drag at lines 107-121
damps it: `* 0.98` all axes, the grounded `-0.9` flip).

### A3 The igniter attraction — `ExperienceOrbEntity.tick` lines 79-104

    double d0 = 8.0;
    if (this.lastTargetUpdateTick < this.renderTicks - 20 + this.getNetworkId() % 100) {
        if (this.igniter == null || this.igniter.squaredDistanceTo(this) > d0 * d0) {
            this.igniter = this.world.getNearestPlayer(this, d0);
        }
        this.lastTargetUpdateTick = this.renderTicks;
    }
    if (this.igniter != null && this.igniter.isSpectator()) { this.igniter = null; }
    if (this.igniter != null) {
        double d1 = (this.igniter.x - this.x) / d0;
        double d2 = (this.igniter.y + this.igniter.getEyeHeight() - this.y) / d0;
        double d3 = (this.igniter.z - this.z) / d0;
        double d4 = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);
        double d5 = 1.0 - d4;
        if (d5 > 0.0) {
            d5 *= d5;
            this.velocityX += d1 / d4 * d5 * 0.1;
            this.velocityY += d2 / d4 * d5 * 0.1;
            this.velocityZ += d3 / d4 * d5 * 0.1;
        }
    }

- The **retarget cadence**: `lastTargetUpdateTick < renderTicks - 20 +
  networkId % 100` — roughly every 20 ticks, STAGGERED per orb by
  `networkId % 100` (the id space the engine's `nextEntityId` already
  mirrors). `renderTicks` is the orb's own tick counter (incremented at
  the tick's END, line 123 — independent of the despawn `age`, which the
  Anvil restore seeds; a fused counter would drift on every restored
  orb).
- The **sticky igniter**: on a retarget tick the CURRENT igniter is kept
  unless null or beyond `d0 * d0` (64 squared distance — 8 blocks) — a
  closer player does NOT steal the target while the current one is in
  range. `squaredDistanceTo(Entity)` reads the OTHER's position against
  the orb's own (the orb's `y` = the box bottom; the player's `y` = feet).
- The **acquire**: `World.getNearestPlayer(x, y, z, range)` (World.java
  lines 2399-2413) — the MINIMUM `squaredDistanceTo < range * range` over
  the world's players, filtered by `EntityFilter.NOT_SPECTATOR` (lines
  27-31 — the ONLY filter: a dead player still attracts; the spectator
  never does). The engine's same-dimension consult (the TF-10 pattern:
  `player.dimension() == orb.dimension()`) carries the reference's own
  world-scoping.
- The **spectator clear**: EVERY tick (not only retarget ticks) a
  spectator igniter is dropped — the player who turns spectator stops
  pulling the orb immediately.
- The **pull**: `d1..d3` the eye-aimed offset over 8 (`igniter.y +
  getEyeHeight()` — `PlayerEntity.getEyeHeight` line 1648: 1.62F
  standing, `-0.08F` sneaking, `0.2F` sleeping), `d4` the normalized
  distance, `d5 = 1 - d4` (positive inside the 8-block sphere), `d5 *=
  d5` — the pull grows QUADRATICALLY as the orb nears the player — and
  the velocity add `d_i / d4 * d5 * 0.1` per axis: the unit direction
  times `(1 - D/8)^2 * 0.1`. Placement: AFTER the fizz consult, BEFORE
  the move — a lava-popped orb's scatter is overwritten by no pull; the
  pull always feeds the same tick's move.

---

## §B The engine's state (verification 2 — the sites read whole)

- `ItemEntity.tick(Ground)` (the Wave IO-F shape): the pre-move gravity,
  the swept move with the vertical-clip consult, the post-move drag —
  and NO fizz arm. No lifetime `ticks` counter (the `age` field fuses
  the despawn clock with the tick count — see A1's seed drift), no
  pre-move position capture. The `Ground.blockIdAt` seam consults the
  **-0.2 feet cell** (the IO-F contract) — the WRONG cell for the fizz;
  the body's own cell needs its own seam.
- `ExperienceOrbEntity.tick(Ground)`: no fizz arm, no attraction — the
  entity carries NO `renderTicks`, `igniter`, or `lastTargetUpdateTick`
  fields at all.
- The managers already own every consult the law needs:
  `ItemEntityManager(ground, Random, firstEntityId)` and
  `ExperienceOrbManager(ground, Random, firstEntityId)` both hold the
  seeded `Random`; both `tick(Iterable<PlayerSession>)` walks already
  iterate the players (the pickup arms). The Listener pattern (the
  `random.pop` pickup sound rides `onItemPickupEffects` → the server's
  dispatch → `fxManager.sound(Position, name, volume, pitch)`) is the
  fizz sound's own shape.
- The lava model: `FluidBlocks.kindOf(Identifier) == Kind.LAVA` — the
  exact `Material.LAVA` set (source/flowing/falling/still, every level).
- `PlayerSession` carries every attraction consult: `gamemode()`
  (`GameMode.SPECTATOR`), `sneaking()`, `sleeping()`, `position()`,
  `dimension()`, `dead()`, `state()`.

## §C The findings

- **B1 — the item's fizz is ABSENT**: an engine item in lava rides
  inert — vanilla pops it (the crossing gate or the 25-tick poll), the
  scatter walks it along the surface, the fizz sound plays at every
  pop. The engine item never pops, never scatters, never sounds.
- **B2 — the orb's fizz is ABSENT**: the same, worse — vanilla consults
  EVERY tick for the orb (unconditional), the engine never does.
- **B3 — the igniter attraction is ABSENT**: the engine orb never pulls
  toward a player — vanilla's 8-block quadratic pull (the staggered
  retarget, the sticky target, the spectator clear, the eye-height aim)
  is the XP pickup's own feel ("the orbs swim to you"), gone entirely.

## §D The honest tails this discovery names (NOT this pass's scope —
one family per pass; each named for its own audit)

- **T1 — the burn clock (a NEW unledgered row)**: the reference item and
  orb both carry `health = 5` and the base-tick lava arm
  (`Entity.baseTick`: `isInLava()` → `takeLavaDamage()` →
  `takeDamage(DamageSource.LAVA, 4.0F)` + `setOnFireFor(15)`; health
  `<= 0` → `remove()`). The engine's item/orb carry NO health field and
  NO fire damage — a stationary body in lava would survive forever
  (the fizz pops it OUT; the burn clock is what kills it). The
  fire-damage family's own pass.
- **T2 — the merge gate**: the reference's `mergeWithNearbyItems` rides
  the SAME `flag || ticks % 25 == 0` gate (lines 84-86) — the engine's
  `mergeNearbyItems` runs EVERY tick. The merge LAWS (the 32767 delay
  gate, the NBT/metadata rules, the size ordering) and this gate both
  belong to the item-merge family's own audit.
- **T3 — the water arms**: `checkWaterCollisions` (the splash effect +
  `applyLiquidDrag`'s 0.8 sinking band) — the engine item/orb has no
  water-drag arm. The buoyancy family's own pass.

## §E The verified-1:1 list (already landed by Wave IO-F — NOT to
re-implement; the re-adjudication's own law)

The pre-move gravity (item 0.04 / orb 0.03), the vertical-clip
`beforeCollision` consult (-0.2 cell, the slime flip, the default zero),
the post-move drag bands (the slipperiness consult at the support cell),
the grounded flips (-0.5 / -0.9), `makesSteps` FALSE for both bodies (the
adjudicated law — no step sounds, no step damping, no ore lighting ever
fires for either body), the pickup delays, the age 6000 despawn.

## §F The fix plan (each row implemented only after its own stash probe)

- **F1** — the own-cell seam: `blockIdAtCell(centerX, bottomY,
  centerZ)` on both `Ground` interfaces (default null — stub grounds
  never fizz), the production `ShapeGround` adapter reading the ACTIVE
  dimension's plain `floor(x), floor(bottom), floor(z)` cell.
- **F2** — the item's fizz: the lifetime `ticks` counter (the
  `World.updateEntity` increment mirrored on the entity), the pre-move
  position capture, the crossing flag (the `(int)` cast verbatim), the
  gate, the `FluidBlocks.Kind.LAVA` consult at the own cell, the
  pop/scatter/pitch — placed between the move and the drag; the
  `tick(Ground, Random)` overload (the old signature delegates — the
  fizz's random draws happen only when the fizz fires); the
  take-once fizz-pitch read the manager publishes through a new
  DEFAULT `onItemFizzed(entity, pitch)` Listener arm; the server's
  adapter plays `random.fizz` 0.4F at the body's position.
- **F3** — the orb's fizz: the same consult UNCONDITIONAL, placed after
  the pre-move gravity and before the move; the same overload/pitch/
  Listener shape (`onOrbFizzed`).
- **F4** — the attraction: the orb's `renderTicks`/`igniter`/
  `lastTargetUpdateTick` fields; the `Attraction` consult (nearest
  non-spectator same-dimension playing player within 8 by squared
  distance + the by-identity lookup the stickiness check reads)
  supplied by the manager over its own players walk; the retarget law,
  the spectator clear, the eye-height aim (1.62 − 0.08 sneaking, 0.2
  sleeping), the quadratic pull — all inside the tick at the
  reference's own placement.
- **F5** — the seats: `ItemOrbEnvelopeParityTest` — the item's
  crossing-gate pop, the 25-tick poll pop (a stationary body), the
  gate's stone-cell negative, the orb's unconditional first-tick pop,
  the pull's closed-form velocity delta, the retarget cadence (the
  stagger's exact first-acquire tick), the spectator clear, the
  sticky-target law.

## §G The three-verification record

- **V1** — §A above: every anchor read from `reference/1.8.8`
  (`ItemEntity` lines 60-114; `ExperienceOrbEntity` lines 61-128;
  `World.updateEntity` 1355; `World.getNearestPlayer` 2399-2413;
  `EntityFilter.NOT_SPECTATOR` 27-31; `PlayerEntity.getEyeHeight`
  1648; `Entity.baseTick`/`takeLavaDamage` 338/358).
- **V2** — §B: the engine sites read whole (both tick walks, both
  Ground seams, both managers + Listeners, the production ShapeGround
  adapter, FluidBlocks' kind table, PlayerSession's consults,
  FxManager's sound bus).
- **V3** — the stash probe: the seats written FIRST, run on the
  pristine tree, each documented FAILING (the divergence it pins);
  then the fix; then green. Recorded with the fix commit.
