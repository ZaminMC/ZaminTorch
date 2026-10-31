# The explosion family audit findings (the nineteenth verifier pass — DISCOVERY)

Base: `origin/develop` at `0a89a0fe` (2026-10-29). Branch:
`fix/hopper-suck-band-parity-audit` (the eighteenth pass's F-HP2/F-HP3
records ride the same branch; this doc is the nineteenth pass's
discovery record — no code changes, findings only).

The reference: `reference/1.8.8/net/minecraft/world/explosion/
Explosion.java` (223 lines) read WHOLE, plus the consult sites it
rides. The engine: `server/block/ExplosionService.java` (239) +
`EngineServer.detonateAtIn` (23493+, the wound arms at 23559-23562)
read whole.

## The verified-1:1 core (no finding — the record for re-verification)

- **The ray fan** (`Explosion.damageEntities` lines 62-105 vs
  `ExplosionService.collectDamaged`): the shell-only 16³ fan (1352
  rays, `j==0||j==15||k==0||k==15||l==0||l==15`), the float arithmetic
  kept bit-for-bit (`ix / 15.0f * 2.0f - 1.0f`), the per-ray strength
  draw `power * (0.7f + rand * 0.6f)` (line 77), the per-cell tax
  `(resistance + 0.3f) * 0.3f` over the BlastSource consult (lines
  86-91), the destroy gate `f > 0 && canExplodeBlock` riding OUTSIDE
  any material check (lines 93-95 — the AIR cells ARE collected, the
  engine matches and the packet's offsets carry them), the per-step
  `f -= 0.22500001f`, the 0.3 step. The engine's reordered tail
  (gate → step-cost → advance vs the reference's gate → advance →
  step-cost) is semantically equivalent (independent variables).
- **The vertical bounds break** (engine lines 131-133): the reference
  has no bounds check; the ray walks off-world AIR paying only the
  0.225 step decay, and the out-of-world AIR cells it collects are
  INERT in damageBlocks (no write succeeds, the fire roll's
  setBlockState above the world no-ops). The engine's early break
  skips only those inert cells — a faithful optimization, not a
  divergence. (Verified against `new BlockPos(d4, d6, d8)` + the
  unloaded-chunk air read.)
- **The phase separation**: the whole fan reads the pre-blast world
  (the reference's damageEntities collects, damageBlocks writes) —
  the engine's collect/commit split is the same law (the javadoc's
  own rationale verified against the reference's call order).
- **The sound** (line 149): `random.explode` at 4.0F with the
  `(1 + (rand - rand) * 0.2) * 0.7` pitch band, played BEFORE the
  destroy loop, destructive or not — the engine's site (23600-ish)
  matches.
- **The EXPLOSION_HUGE/LARGE particles**: the B2-class non-row (the
  dedicated server's addParticle is the empty stub; the client renders
  its own burst from the 0x27 packet) — the F-DUST adjudication's
  precedent, no code owed.
- **The wound arms' formulas** (players/mobs/vehicles):
  `d12 = feet-position distance / f3` (Entity.distanceTo reads the
  body's position — the engine's feet frame ✓), the eye-height
  direction vector (1.62 stand / 1.54 sneak), the degenerate-guard,
  `blockDensity` 1:1 (World.getBlockDensity lines 1608-1637 — the
  sample grid, the lateral carries, the ray-cast fraction, the float
  accumulation), `damage = (int)((d10² + d10)/2 * 8 * f3 + 1.0)`,
  the Blast Protection shave on the KNOCKBACK SCALE never the
  damage, the packet's motion carry with the UNPROTECTED d10
  (lines 143-145), the difficulty ladder before the armor math.
- **The drop rolls + the TNT chain** (detonateAtIn's tail): one
  `nextFloat() <= 1/power` roll per destroyed cell over the collected
  type (`dropItems(..., 1.0F/power, 0)`, line 182); a consumed TNT
  cell never drops — it primes the short-fuse body
  (`TntBlock.shouldDropItemsOnExplosion` false + `onExploded` lines
  58-65, 115-117), the block-side chain verified in TntSystem
  ("the exploded-by-blast arm").
- **The §27.7 gates** (the event bus before any destruction, the
  player-damage gate after the protection math): engine-native, the
  reference carries no such gates — the house's own event surface,
  out of parity scope.

## The candidates (hypotheses — NOT confirmed, awaiting the three passes)

### F-EX1 — the blast's entity gather covers four families; vanilla's covers EVERY entity class

The reference's gather (lines 115-144): `world.getEntities(source,
box)` — EVERY entity class in the `[floor(c-f3-1), floor(c+f3+1)]`
box — filtered only by `!isImmuneToExplosions()`, and the census
(`grep isImmuneToExplosions` over the reference's entity package)
finds exactly ONE override: **ArmorStandEntity line 593 = true**.
PrimedTnt is NOT immune. Items, XP orbs, arrows, snowballs, ender
pearls, fireballs, fishing bobbers, ender crystals, fireworks — all
take the loop's unconditional `velocityX/Y/Z += dir * d11` (lines
135-137), the takeDamage result notwithstanding (Entity.damage's
default false — the WOUND no-ops, the SCATTER still lands).

The engine: exactly FOUR wound arms (23559-23562) — players, mobs,
vehicles, decorations (the decorations arm = item frames + paintings
only). **No item-entity arm, no orb arm, no projectile arm, no
primed-TNT arm, no crystal arm.** The observable: a blast next to a
dropped stack leaves it sitting still (vanilla scatters it — the
loud half); a blast on an end crystal never chain-detonates it (the
reference: the crystal's takeDamage → its own explosion — the
famous End chain); a blast never knocks a primed TNT (vanilla does —
PrimedTnt has no immunity override in 1.8.8).

The armor-stand direction is NOT a finding: the engine's blast never
touches armor stands (no arm covers them) and the reference EXCLUDES
them (immune) — the same outcome by different roads; only the OTHER
classes diverge.

**Verification plan**: source (this chain, re-walked per family);
behavioral (a creeper/TNT blast beside a restored item stack — the
pre-fix engine leaves the stack's position untouched, vanilla's
velocity add moves it; the crystal chain seat); independent (the
isImmuneToExplosions census re-run; the `getEntities` box-gather
shape vs the arms' distance gates — the reference's gate is the BOX
gather + d12 <= 1, the arms' is the squared distance, equivalent in
outcome).

### F-EX2 — the sleeping player's eye-height frame in the blast's knockback vector

`woundPlayersFromBlast` (23672-23676): `eye = sneaking ? 1.54 :
1.62` — the reference's `PlayerEntity.getEyeHeight` carries THREE
states: 1.62 stand, 1.54 sneak, **0.2 lying** (the sleeping frame).
A sleeping player inside the blast's reach takes the knockback
vector aimed 1.62 high instead of 0.2 — the direction (and through
it the motion-carry packet's record) diverges. Thin, constructible
(a bed + a blast), low impact.

### F-EX3 — the BlastResistanceTable's coverage vs the fallback

`ExplosionService.blastResistance(type)`: the table first (the
landed 1:1 values), then **`Float.MAX_VALUE` for unregistered or
unbreakable or negative-hardness**, then `hardness * 5.0` — "the
retired approximation's last resort". The reference's every block
carries its OWN declared resistance (`blockResistance / 5.0F`). The
table's census was re-pinned at 957 in the vine wave — the
verification item: every REGISTERED block must resolve through the
table (or a verified-equal value), never the fallback; any block
that falls through resists wrongly in BOTH directions (MAX_VALUE =
an unbreakable wall the reference carves; hardness*5 = the wrong
number either way).

### F-EX4 — the createFire arm: the bed and the ghast fireball place fire vanilla-wide; the engine has no fire arm at all

The reference's `damageBlocks(boolean createFire)` carries a QUIRK:
the per-cell EXPLOSION/SMOKE particle arm (line 159) reads the
PARAMETER; the fire-placement arm (line 191) reads the FIELD
(`this.createFire`); `World.explode` (lines 1597-1606) ALWAYS calls
`damageBlocks(true)` — the particles ride the param; the fire rides
the field. The complete call-site census (verified by grep over the
reference, 2026-10-30):

| Site | Call | createFire (the field) | destructive |
|---|---|---|---|
| `BedBlock.java:78` (the wrong-world bed) | `explode(null, x, y, z, 5.0F, true, true)` | **true** | true |
| `FireballEntity.java:33` (the ghast fireball's onHit) | `explode(null, x, y, z, power, flag, flag)` over `flag = mobGriefing` | **mobGriefing** | mobGriefing |
| `PrimedTntEntity.java:75` | six-arg (`..., true`) | false | true |
| `TntMinecartEntity.java:92` | six-arg (power `4.0 + rand*1.5*d0`) | false | true |
| `EnderCrystalEntity.java:77` | six-arg (`6.0F, true`) | false | true |
| `CreeperEntity.java:210` | six-arg (`explosionRadius * f`) | false | mobGriefing |
| `WitherEntity.java:217` | seven-arg (`7.0F, false, mobGriefing`) | false | mobGriefing |
| `WitherSkullEntity.java:83` | seven-arg (`1.0F, false, mobGriefing`) | false | mobGriefing |

The six-arg form (World line 1597) delegates with createFire=false —
**the ENGINE'S OWN CRYSTAL COMMENT ("createFire false, destructive
true", EngineServer 19568-19570) IS CORRECT**; this session's first
draft misread the decompile's parameter name and nearly recorded the
crystal as a fire caller — the discipline caught it, recorded here
as the pass's own correction. The crystal blast places NO fire in
vanilla, and the engine's crystal call matches (destructive true,
no fire).

The REAL fire sites the engine misses: **the wrong-world bed
(power 5, fire always)** and **the ghast fireball with mobGriefing
on (fire rides the flag)**. `detonateAtIn` has NO fire arm AT ALL
(no createFire parameter, no blast-fire site anywhere) — a ghast
fireball on a plain with mobGriefing on wounds, knocks and breaks
but never leaves the scorch fire vanilla rolls 1-in-3 on the
damaged AIR cells over an opaque floor (Explosion lines 191-199).
The verification: the bed's engine path (does the engine's bed even
blast? the bed family's own row), then the fireball's hit path,
then the fire arm's bounded port (the damaged-cells walk + the
1-in-3 roll + the opaque-below consult).

## The pass's standing note

No code changes this pass — discovery only, per the workflow's first
run rule. F-EX1 is the highest-value candidate (the item scatter +
the crystal chain); F-EX3 is the census chore; F-EX2/F-EX4 are
thin. The next session: the F-EX1 three-pass verification first,
then the remainder in value order.

## F-EX1 — VERIFIED and FIXED (the twentieth pass, 2026-10-30)

**Status: resolved** (the three passes complete, the fix landed on
`audit/verifier-pass-20-explosion`, the PR opened against `develop`).

### The twentieth pass's correction to the nineteenth's record

The nineteenth pass adjudicated the armor stand "NOT a finding" on the
claim that "the reference EXCLUDES them (immune)". The immunity consult
re-read: `ArmorStandEntity.isImmuneToExplosions` (line 593) answers
**`isInvisible()`** — only an INVISIBLE stand is immune; a VISIBLE stand
takes the loop, and `takeDamage`'s explosive arm (lines 382-386) breaks
it: `onBroken()` drops the EQUIPMENT alone (lines 480-490) — the stand
item does NOT drop (`onBrokenByPlayer` lines 475-478 is the arm that
drops it) — then `remove()`. The engine's stands carry no invisible flag,
so every engine stand maps to the reference's visible body: the blast
must break it. The armor-stand family IS part of F-EX1.

### The three verification passes

1. **Source (2026-10-30, run 1).** The reference chain re-walked per
   family: the gather (`Explosion.damageEntities` lines 115-144 —
   `world.getEntities(source, box)`, the `EntityFilter.NOT_SPECTATOR`
   walk, every class in the `[floor(c-f3-1), floor(c+f3+1)]` box); the
   census re-run (`grep isImmuneToExplosions` over the reference's entity
   tree: exactly ONE override — the armor stand's conditional; PrimedTnt
   carries none); the per-family wound laws (ItemEntity.takeDamage lines
   202-218 — health 5, the nether star's `source.isExplosive()` refusal
   at 207-209, the death-at-zero remove; ExperienceOrbEntity lines
   141-151 — the same health shape; EnderCrystalEntity lines 67-84 — any
   damage removes + detonates 6.0F; ProjectileEntity.takeDamage lines
   248-273 — the LIVING-attacker re-aim over the FireballEntity/
   SmallFireballEntity/WitherSkullEntity family, the arrows and thrown
   kinds extending Entity/ThrownEntity carrying the default no-op; the
   PrimedTnt and Fireworks the same default); the scatter (lines 133-137
   — `velocityX/Y/Z += dir * d11` riding OUTSIDE the takeDamage verdict,
   `d11 = d10` for every non-living body — `Entity.getEquipment()`'s null
   default zeroes the Blast Protection consult). The engine side: exactly
   four wound arms (the detonateAtIn walk, players/mobs/vehicles/
   decorations), no compensating consult in any family manager
   (`breakByExplosion` and the crystal `damage()` exist but nothing calls
   them from the walk — the armor-stand arm tested only in its own unit
   seat, the crystal death wired only for the arrow's pop).
2. **Behavioral (2026-10-30, run 1).** `ExplosionEntityGatherParityTest`
   — seven seats (the item destruction/scatter/star-refusal pair, the
   orb pair, the twin-arrow divergence, the primed-TNT impulse, the
   crystal chain, the armor-stand break with the no-stand-item quirk,
   the firework impulse). The stash verification: failing UNFIXED on
   EXACTLY all seven (the near stack surviving its wound, the twins' vx
   identical at 0.0, the chain timing out at one blast, the stand sweep
   timing out, the primed hop's 1.8E-4 residue, the rocket 0.0/0.0) and
   green whole with the fix (7/0).
3. **Independent (2026-10-30, run 1).** The gather box vs the per-arm
   distance gates (the box `[floor(c-f3-1), floor(c+f3+1)]` ⊇ every
   body with `d12 <= 1`); the reentrancy of the chain (the crystal's
   nested `detonateAtIn` re-enters the same region domains —
   `OwnershipDomain.enter` counts nested enters); the concurrent-remove
   safety (`all()` returns copies in every family); the §27.7 gate
   event's fire position (the blast's HEAD — the seats therefore await
   observable outcomes through tick-thread polls, never same-tick
   snapshots); the intentional-design check (no ledger or compat note
   documents the exclusion — unlike the tnt-in-End gap which IS
   ledgered; the engine's own `woundDecorationsFromBlast` javadoc states
   the reference's every-entity law).

### The fix (the walk's seven new arms + the family verbs)

- **The gather's SNAPSHOT law**: the seven family lists gathered at the
  wound phase's HEAD (before any arm runs) and passed into the arms — a
  body spawned during the walk (the decorations' drops) never takes the
  SAME blast's wound; a nested detonation (the crystal chain, the TNT
  cart's death) gathers its own FRESH lists, exactly the reference's
  nested explode inside the entity loop. The first cut iterated live
  lists and destroyed DecorationBlastTest's frame drops — the regression
  the seat caught, the law that repaired it.
- **The item arm**: the feet frame (the engine's center-stored body,
  `position.y - HALF_HEIGHT`), the eye height (Entity.getEyeHeight's
  `height * 0.85` = 0.2125), the 0.25-box density, the wound through the
  manager's new `hurtFromExplosion` (the nether star refusal + the new
  health field, default 5, the death-at-zero removal), the unconditional
  scatter.
- **The orb arm**: the same shape over the engine's own 0.25 orb body.
- **The projectile arm**: the per-kind half (the push table), the
  mob-sourced re-aim through the manager's `woundFromExplosion` (the
  `deflectBy` twin over the fireball pair + the wither skull — the
  reference's ProjectileEntity family; the engine's `deflect` punch
  surface itself still excludes the skull, a separate landed-scope
  note), the scatter through `addVelocity`.
- **The primed-TNT arm**: the reference's own bottom frame, the family's
  0.0 eye-height override (PrimedTntEntity line 93), the 0.98-hull
  density, the impulse through the system's `addVelocity` — the
  TNT-cannon's own law.
- **The crystal arm**: the d12 gate alone routes into `damage()` (the
  removal + the 6.0F blast) — THE END CHAIN, recursively.
- **The armor-stand arm**: `breakByExplosion` (the equipment drops, the
  stand item does not — the reference's own quirk).
- **The firework arm**: the impulse through the system's `addVelocity`.

### The re-pins the corrected fix forced (each with its own evidence)

`VehicleDamageSourceTest` and `VehicleWorldDamageTest` carried stale
pins from the pre-gather engine: the TNT cart's death detonation eats
its own just-dropped items (TntMinecartEntity.dropItems lines 73-81:
the drop FIRST, then `explode()` line 92 INSIDE dropItems — the nested
blast's FRESH gather wounds the fresh items to death, ItemEntity.takeDamage
lines 202-218). Vanilla's census after a blast-killed TNT cart pair
counts ZERO cart items; the lava seat's lone survivor is the dry
control's own AFTER-the-blast drop (the death blast reaches it at 3.85
blocks, d12 0.48, damage 26 over the cart's break threshold). Both
censuses re-read on the TICK THREAD (the direct cross-thread reads race
and serve stale views that resurrect the eaten drops — the probe pair
`all=0` on the tick thread vs `2` on the census thread). The
furnace-block drop gate's positive side stays covered by
`theMeleeDeathDropsTheFurnaceBlock` (a melee death — no blast follows
the drop).

### The tails this fix ledgers (not owed in this PR)

- **The falling-block family**: the engine's body carries vertical
  velocity alone — the blast's horizontal impulse needs the family's own
  model pass (a horizontal-velocity model change, out of a focused PR's
  bounds).
- **The item/orb Health persistence**: vanilla round-trips the `Health`
  tag (ItemEntity line 222, ExperienceOrbEntity line 157); the engine's
  stores keep their own documented excepted rows — restored bodies
  restart at 5.
- **The grounded-arrow scatter's wire publish**: vanilla's
  velocityChanged flag sends an immediate Entity Velocity packet for a
  blasted stuck arrow; the engine's flight publication covers the
  observable motion, the stored velocity rides the family's own wire
  surface.
- **The `deflect` punch surface's skull exclusion**: the engine's
  player-punch deflection refuses WITHER_SKULL where the reference's
  ProjectileEntity.takeDamage includes it — a separate landed-scope
  candidate (F-EX5), NOT this fix's arm.

### The evidence record

The stash verification (7/7 failing unfixed, 7/0 with the fix); the
family lanes (tnt + experience + endcrystal + fireworks + decoration +
the gather seats) 56/0; the vehicle lane whole 67/0 after the re-pins;
the entity-package lane 595/0 beside the two re-pinned vehicle seats
(the 597-run's only failures); the blast-adjacent acceptance lanes
green. The full suite rides the CI.

## F-EX5 — the punch deflection's skull exclusion (NEW candidate, the twentieth pass's discovery)

**Status: REJECTED** (the 2026-10-31 session's three passes — the
resolution section at the bottom of this doc). The twentieth pass's
hierarchy read stopped at `extends ProjectileEntity` (line 16) and
never opened the skull's OWN override: `WitherSkullEntity.takeDamage`
lines 94-96 answer **`return false`** — the skull refuses the punch
whole, exactly as the engine already behaves. The candidate was
wrong, and chasing it exposed the REAL divergence beside it (F-EX10,
the same session): the SMALL fireball's identical refusal, which the
engine violates on BOTH the punch and the blast-wound surfaces.

## The queue after this pass

F-EX2 (the sleeping eye-height frame — 0.2 vs 1.62 in the knockback
vector), F-EX3 (the BlastResistanceTable coverage census), F-EX4 (the
createFire arm — the wrong-world bed + the mobGriefing ghast fireball),
F-EX5 (the punch deflection's skull exclusion), and the standing
architecture tail F-FL4 (the nether/End fluid simulation — the §22
queue head, the per-dimension scheduled/random-tick infrastructure it
needs first). The F-EX1 family is retired end to end.

[The 2026-10-31 session's resolutions: F-EX2 retired (below), F-EX4
retired (PR #92), F-EX5 REJECTED with F-EX10 confirmed beside it
(below), F-EX7 retired (PR #93). The queue's live heads: F-EX3 (the
BlastResistanceTable coverage census), F-EX6 (the bed's wrong-world
arm), F-EX8 (the wither birth's flag), F-EX9 (the ghast fireball's
throwable gravity), and F-FL4.]

---

## F-EX2 — VERIFIED and FIXED (the 2026-10-31 session, PR pending this branch)

**Status: resolved** (the three passes complete — V1 by the nineteenth
pass, V2 + V3 this session; the fix on
`fix/explosion-sleeping-eye-frame-parity-audit`).

### The three-pass record

- **V1 (source, the nineteenth pass):** `woundPlayersFromBlast` reads
  `eye = sneaking ? 1.54 : 1.62` where the reference's
  `PlayerEntity.getEyeHeight` (lines 1648-1659) carries THREE states —
  1.62 standing, **0.2 lying** (the sleeping frame), the sneak's -0.08
  composed on either (a sneaking sleeper reads 0.12). The blast's
  knockback vector (Explosion line 123: `entity.y +
  entity.getEyeHeight() - this.y`) aims at the wrong height for a
  sleeping victim.
- **V2 (behavioral, 2026-10-31):** the discriminating probe — a sleeper
  in a bed (the Waker keeping the sleep pending), a TNT charge whose
  center lands BETWEEN the two eye heights above the sleeper's feet.
  The captured wire motion (`ExplosionEvent.playerMotion()`): **motion
  y = +0.0249** (aimed at the 1.62 standing eye) where the vanilla
  frame computes dy = **-1.2125** (the lying 0.2 eye) — the vertical
  SIGN flips: vanilla knocks the lying body DOWN toward the bed, the
  engine aimed it UP. The seat asserts the geometry discriminates
  (center y=6.4125, feet y=5.0 — inside the 0.2..1.62 band) before the
  frame assertion.
- **V3 (independent, 2026-10-31):** (a) the reference's line 123
  confirmed to read `getEyeHeight()` — the shared three-state law, not
  a blast-local constant; (b) the compensating-path search: the motion
  map is computed ONCE in `woundPlayersFromBlast` (the only
  `motion.put`) and rides the Explosion packet unchanged — no other
  site corrects it; (c) the intentionality check: the engine's
  `sleeping()` state exists (the Use Bed pose renders lying), and the
  site ALREADY carries the sneak refinement (1.54) — the standing-eye
  consult was a partial port of the same law, the lying frame an
  omission, not a design choice; (d) the blast path reaches sleeping
  players (the d12 gate reads `players.all()` with no sleep filter —
  the probe's non-null motion proves it); (e) the edge: the fix must
  implement the COMPOSED law (sleeping 0.2, then -0.08 on sneak), not a
  ternary.

### The fix

`woundPlayersFromBlast`'s eye consult (one site):

```java
double eye = player.sleeping() ? 0.2 : 1.62;
if (player.sneaking()) {
    eye -= 0.08;
}
```

### Evidence

`ExplosionSleepingEyeFrameParityTest` 2/0 NEW — the stash verification
failing UNFIXED on EXACTLY the sleeping seat (the motion aimed at the
standing eye, dy sign flipped) and the STANDING control green on BOTH
trees (the same geometry, the awake player, the 1.62 frame — the
guard).

### Regression record

The explosion + sleep + body-survival lanes 23/0 (ExplosionTntRedstone
Api 2, the new parity 2, MultiplayerSleep 3, Sleep 1, CreeperRows 3,
BodySurvival 10).

### The tail this pass recorded (NOT repaired)

The engine carries other hardcoded 1.62 consults (the eat-sound /
throw-gesture origin at line ~6167, the random-tick cell walk at line
~7842, the ray consults at lines ~12678/~12982) — each needs its own
reference comparison (the reference itself hardcodes 1.62 at SOME of
these sites and reads getEyeHeight at others; the census is its own
verification item, not this fix's scope).

---

## F-EX4 — VERIFIED and FIXED (the 2026-10-31 session, the twenty-first verifier pass)

**Status: resolved** (the three passes complete — V1 by the nineteenth
pass, V2 + V3 this session; the fix on
`audit/verifier-pass-21-explosion-fire`, **PR #92**).

### The three-pass record

- **V1 (source, the nineteenth pass + this session's completion):** the
  reference's `Explosion.damageBlocks` (lines 191-199) carries the fire
  arm as the FIELD `this.createFire`'s own walk AFTER the destructive
  clear: every `damagedBlocks` cell that holds AIR NOW, sits on an
  OPAQUE cube (`blockpos1.down().getBlock().isOpaque()`), and wins
  `this.random.nextInt(3) == 0` becomes `Blocks.FIRE` — the roll
  consuming the Explosion's OWN private `new Random()` (line 30, NEVER
  the world's stream; the particle arm at line 159 alone reads the
  world random through the PARAMETER). `World.explode` (lines
  1597-1606): the six-arg form delegates `createFire=false` and BOTH
  forms call `damageBlocks(true)` — the param feeds the particles, the
  field feeds the fire. The complete call-site census: the wrong-world
  bed (BedBlock.java:78 — `5.0F, true, true`, fire ALWAYS) and the
  ghast fireball (FireballEntity.java:33 — `flag, flag` over
  mobGriefing) are the ONLY fire sites; PrimedTnt, the TNT cart, the
  ender crystal, the creeper, the wither and the skull all pass false.
  The engine's `detonateAtIn` had NO createFire parameter and NO fire
  arm anywhere; its stand-in was the LARGE_FIREBALL hit paths' single
  `fireSink.tryPlaceFire` at the impact cell — a structural NO-OP on
  the block-hit path against un-cleared solid ground (the cell the
  blast just cleared is the only one its air gate ever accepts),
  UN-gated by mobGriefing, UN-floored, and absent on the player-hit
  path whole.
- **V2 (behavioral, 2026-10-31):** `ExplosionFireArmParityTest`'s four
  seats — the stash verification failing UNFIXED on EXACTLY the three
  predicted seats: (1) three ghast fireballs into a grass plain answer
  **3 fires** ("the stand-in law caps at three" — one per blast at the
  blast's own cleared center cell, the only cell the air-gated sink can
  accept on the ground path) where the 1-in-3 roll over the craters'
  air cells answers several; (2) the struck-body blast answers **1
  fire** ("the stand-in law answers at most one"); (3) mobGriefing off
  still answers **1 fire** at (10,5,12) — the ungated mob-hit stand-in,
  vanilla refuses the fire whole with the flag; (4) the glass floor-law
  guard green both trees (the post-fix pin). GREEN WHOLE with the fix.
- **V3 (independent, 2026-10-31):** (a) the floor-gate equivalence
  proof: `opaqueCube = isSolidRender()` at construction (Block.java
  line 215) and the light table's default `isSolidRender() ? 255 : 0`
  (line 216, folded to 15) — so filter 15 IS the opaque-cube answer
  for every block WITHOUT a setOpacity override, and the COMPLETE
  setOpacity census (eight non-zero sites) leaves exactly THREE
  divergence families (stairs line 65, farmland line 35, the SINGLE
  slabs — SlabBlock line 70's `isDouble()`, the constructor's own line
  25 setting the doubles true) which the gate excludes by name;
  (b) the placed flame SURVIVES its own onAdded (FireBlock lines
  322-324 remove a flame with neither solid top nor fuel — an isOpaque
  floor implies `World.hasSolidTop`, and the engine's
  `FireBlocks.hasSolidTop` is the same law 1:1 — the probe's mid-air
  control flame vanishing WAS vanilla, not an interference);
  (c) `doFireTick` is NOT a gate on the reference's fire arm (no
  gamerule consult in the walk — the fix must not invent one);
  (d) the compensating-site census clean: no other engine site places
  fire for a ghast blast (the lightning bolt, the small fireball's own
  landing arm, the End portal fire — none serve the blast path);
  (e) the edge set: y==0 cells skip the below consult (the engine's
  BlockPosition ctor throws where the reference reads the void as
  non-opaque); the AIR cells the rays collected ARE candidates (the
  engine's `collectDamaged` set-add rides outside the material check —
  the reference's own shape); the nested detonations (the crystal
  chain, the TNT cart's death) roll their own private streams exactly
  as the reference's nested `new Explosion` does; (f) the
  intentionality check: the `FireBlocks` family is ported whole (age
  progression, spread, the no-fuel death — fire is in scope by design),
  the missing arm was an omission, not a choice.

### The fix

`detonateAtIn` gains the `createFire` parameter (false at every caller
except the ghast fireball's sink, which passes the SAME mobGriefing
consult as its destructive flag — FireballEntity.onHit's `flag, flag`)
and the fire arm after `commitDamaged` inside the region scope: the
post-clear AIR gate, `isOpaqueCubeFloor` (the filter-15 floor with the
stairs/single-slab/farmland exclusions), the per-detonation private
`new Random()`'s `nextInt(3) == 0`, the FIRE write riding the scope's
ownership. The two LARGE_FIREBALL `tryPlaceFire` stand-ins (block-hit
+ mob-hit) are REMOVED — the blast's own arm replaces them.

### Evidence

`ExplosionFireArmParityTest` 4/0 NEW (the stash verification failing
UNFIXED on exactly the three seats above, green whole with the fix);
the stale stand-in pin in
`ProjectileDimensionReadsTest.theFireAndExplosionSinksCarryTheDimension`
re-pinned (the fire sink answers the SMALL fireball alone — the ghast
shot's fire rides its blast's own arm now).

### Regression record

The explosion family lane 7/0 (ExplosionEntityGatherParityTest); the
projectile lanes green whole (FireballDeflection, WitherSkull,
ProjectileDimensionReads re-pinned, FireworksAndBottle); the fire
family + combat lanes green (FireBlocks 8, Blaze, NetherMonster,
ThornsMobVictim, CombatTargetTails); the vehicle blast lanes 15/0
(VehicleDamageSource 4, VehicleWorldDamage 3, VehicleFire 8); the
TNT/dispenser/creeper/crystal lanes 46/0; IceMelt 11/0; Lightning 4/0
(the bolt's own fire untouched); DimensionWalk green in isolation (the
documented load-flake signature, unrelated). The full suite rides the
CI run itself.

### The side-discoveries this pass recorded (candidates, NOT repaired)

- **F-EX6 — the wrong-world bed never blasts at all.** The engine's
  bed use routes straight to `sleepOnTick` (EngineServer ~11867):
  NO dimension gate, NO removal of the halves, NO power-5
  createFire-true blast (BedBlock.use lines 68-79 — the
  `!dimension.hasSpawnPoint() || biome == HELL` arm; both the Nether
  and the End answer `hasSpawnPoint()` false, verified in the
  reference's dimension classes). A bed clicked in the engine's nether
  reads the OVERWORLD's clock and tries the ordinary sleep walk (it
  can even SET A BED SPAWN there). The fire arm this pass landed is
  the bed blast's own fire substrate — the bed family's arm is its own
  finding awaiting its three passes.
- **F-EX7 — the TNT minecart's destructive flag rides mobGriefing; the
  reference hardcodes true.** TntMinecartEntity.java:92 calls the
  six-arg `explode(this, ..., power, true)` — the cart's blast breaks
  blocks with mobGriefing OFF. The engine's blast sink factory
  (EngineServer ~2846) passes `gameRules.getBoolean("mobGriefing")`.
  A no-grief world: vanilla's cart still craters, the engine's does
  not. Thin, one flag, awaiting its own passes.
- **F-EX8 — the wither birth blast's destructive flag is hardcoded
  false; the reference rides mobGriefing.** WitherEntity.java:217
  calls the seven-arg `(7.0F, false, mobGriefing)` — createFire false,
  DESTRUCTIVE mobGriefing. The engine's birth site
  (EngineServer ~21885) passes `false` whole. A griefing world:
  vanilla's birth blast breaks blocks, the engine's never does.
  Awaiting its own passes.
- **F-EX9 — the ghast fireball flies with throwable gravity; the
  reference's fireball family is gravity-free.** The reference's
  `ProjectileEntity.tick` (the fireball family's base — FireballEntity
  extends it directly, NOT ThrownEntity) applies
  `velocity += acceleration; velocity *= drag` with NO gravity term;
  the gravity walk lives in `ThrownEntity.tick` (line ~218) alone. The
  engine's `gravityOf` (ProjectileManager) answers
  `THROWABLE_GRAVITY` (0.03) for LARGE_FIREBALL — it excludes
  SMALL_FIREBALL and WITHER_SKULL but not the ghast shot. A long ghast
  shot droops in the engine, flies straight in vanilla. Awaiting its
  own passes.
- **F-EX3 data point — the stairs family's blast resistance rows read
  0f in the engine's BlastResistanceTable; the reference delegates to
  the base block** (StairsBlock.getBlastResistance lines 417-418 →
  the base block's own; oak stairs over planks' 2.0, stone-stair
  families over their stone bases). The 0f rows make the engine's
  stairs ~free to blast through. Feeds F-EX3's census.

## The queue after this pass

F-EX6 (the bed's wrong-world blast arm — the bed family's own row,
the fire substrate now landed), F-EX3 (the BlastResistanceTable
coverage census — now carrying the stairs-resistance data point),
F-EX7/F-EX8 (the two thin destructive-flag rows), F-EX5 (the punch
deflection's skull exclusion), F-EX9 (the ghast fireball's gravity),
and the standing architecture tail F-FL4 (the nether/End fluid
simulation — the §22 queue head).

## F-EX7 — VERIFIED and FIXED (the twenty-first verifier pass, 2026-10-31)

**Status: resolved** (the three passes complete in one session; the fix
on `fix/tnt-cart-destructive-flag-parity-audit`). Discovered by the
twenty-first pass's explosion call-site census (the fire-arm
investigation's side-find, PR #92's record).

### The finding

The reference's `TntMinecartEntity.explode` (lines 85-92) calls the
SIX-ARG `world.explode(this, x, y, z, power, TRUE)` — the cart's blast
HARDCODES the destructive flag, and mobGriefing never gates it (the
famous exception: a TNT cart craters even in a no-grief world; only
the fireball and the creeper family ride the rule). The engine's blast
sink factory passed `gameRules.getBoolean("mobGriefing")` — a no-grief
world's cart blast wounded and knocked but never broke a block.

### The three-pass record

- **V1 (source):** the reference's SINGLE `explode(distance)` method
  serves every cart detonation (the burning arrow's annihilation arm
  `takeDamage` lines 63-73, the fall-death line 44, the fire/explosive
  death line 81) — and the six-arg call hardcodes TRUE. The engine's
  factory sink serves the same three detonations through
  `explodeWith` — one flag, one divergence.
- **V2 (behavioral):** `TntCartDestructiveFlagParityTest` (NEW):
  mobGriefing off, a TNT cart on the plain, a burning arrow into the
  cart. The stash verification failing UNFIXED on exactly the crater
  await — the probe reading `flag=false crater=` EMPTY (the engine's
  flag consult left every grass cell standing) — and green with the
  fix.
- **V3 (independent):** the flag's only consult was the factory lambda
  (no compensating site); the wound arms stay unconditional on both
  sides (the flag gates the BLOCK walk alone — the seat's cart still
  died to the wound with the flag off); the factory comment's
  "mobGriefing's destructive gate" prose described the engine's
  general detonation walk, not a cart choice (the pattern copy from
  the fireball sink — an omission); the first cut's empty crater was
  the UNGENERATED-void read outside the 25 spawn chunks (the probe
  caught it, the seat moved inside the pregenerated band).

### The fix

The factory's flag → `true` (one line + the reference cite).

### Evidence

The stash-verified discrimination (the empty crater unfixed → green
fixed); the whole vehicle package 82/0 (every cart family:
chest/hopper/spawner/command-block minecarts, the fire lanes, the
persistence, the riding lanes) + the explosion family lanes (the
gather 8, the fire arm 4) + the TNT lanes green.

## F-EX5 REJECTED + F-EX10 — the re-aim census: the large fireball ALONE (the 2026-10-31 session, F-EX5's resolution)

**F-EX5: rejected.** The twentieth pass's candidate claimed the skull
CARRIES the re-aim arm because `WitherSkullEntity extends
ProjectileEntity` (line 16) — a hierarchy read that stopped one level
short. The skull's OWN `takeDamage` (lines 94-96) answers
**`return false`**: the punch never lands, no redirect, no new
shooter. The engine's exclusion was already correct
(`WitherSkullTest` line 171's seat pins it with the right cite).

**F-EX10: confirmed and fixed (the same investigation's three
passes).** The subclass-counting census that misled F-EX5 misled TWO
engine arms in the OPPOSITE direction — the SMALL fireball's identical
refusal was violated on both surfaces:

- `ProjectileManager.deflect` (the punch) covered
  `LARGE_FIREBALL + SMALL_FIREBALL`: a player's punch on a blaze's
  charge (or a dispensed fire charge) redirected it — vanilla's
  `SmallFireballEntity.takeDamage` (lines 62-64) answers `false`, the
  punch never lands, the charge keeps its course and its shooter.
- `ProjectileManager.woundFromExplosion` (the mob-sourced blast's
  wound) covered `LARGE + SMALL + WITHER_SKULL`: a creeper blast
  beside a charge or a skull re-aimed it (the blast's source becoming
  the new shooter) — vanilla's overrides refuse the wound whole; only
  the SCATTER (Explosion lines 135-137, outside the takeDamage
  verdict) ever moves them.

### The reference census (the complete table)

Of `ProjectileEntity`'s THREE direct subclasses exactly ONE inherits
the re-aim (ProjectileEntity.takeDamage lines 248-273: the velocity
BECOMES the attacker's look, the acceleration look*0.1, the attacker
the new shooter):

| Kind | Reference class | takeDamage | Punch deflect | Blast wound re-aim |
|---|---|---|---|---|
| LARGE_FIREBALL | FireballEntity | inherited re-aim | YES | YES |
| SMALL_FIREBALL | SmallFireballEntity | `return false` (62-64) | NO | NO |
| WITHER_SKULL | WitherSkullEntity | `return false` (94-96) | NO | NO |
| arrows / thrown | Entity / ThrownEntity | default false | NO | NO |

The wound's caller shape (Explosion.java lines 115-144): the gather
calls `entity.takeDamage(DamageSource.explosion(this), ...)` — the
small fireball and the skull answer false, no re-aim — then the
unconditional scatter `velocityX/Y/Z += dir * d11` lands for every
gathered body whatever the verdict. The punch's caller
(PlayerEntity.attack line 981): `flag2 = target.takeDamage(...)` gates
the knockback push AND the sprint damping (lines 983-991) AND the 0.3
exhaustion (line 1043) — a refused punch charges nothing.

### The three-pass record

- **V1 (source)**: the hierarchy grep (THREE direct subclasses), the
  override bodies read in full (SmallFireball 62-64, WitherSkull
  94-96, FireballEntity's single takeDamage mention is a CALL at line
  28 — no override), the blast loop's unconditional-call +
  scatter-outside shape, the flag2-gated punch arms, and
  DamageSource.explosion's getSource() attacker gate (the engine's
  mob-sourced-alone gate already matches).
- **V2 (behavioral — the stash discrimination)**: THREE seats failing
  UNFIXED on exactly the predicted assertions and green FIXED —
  (1) `FireballDeflectionTest.theThrownFamilyAndArrowsTakeNoDeflection`
  re-pinned (the fire charge's `assertTrue` → `assertFalse`: "the fire
  charge answers false (the override's own verdict)" — the wrongly
  pinned seat's own comment was the census error's record: "the fire
  charge is family (Bootstrap's dispensed arm)");
  (2) `FireballDeflectionTest.theBlastWoundReAimsTheGhastFireballAlone`
  NEW (the unit census: the LARGE re-aimed with the exact look vector
  and the new shooter; the SMALL's thrower and velocity untouched; the
  SKULL's thrower and velocity untouched);
  (3) `ExplosionEntityGatherParityTest.theBlastReAimsTheGhastFireball
  ButNeverTheBlazeCharge` NEW over the REAL blast wire (the creeper's
  own detonation: the ghast shot's thrower BECOMES the creeper's id —
  the re-aim lands; the near charge diverges from its un-blasted twin
  — the scatter lands — while its thrower stays -1; the unfixed tree
  timed out on exactly that last await).
- **V3 (independent)**: (a) the compensating-site census — exactly TWO
  `deflectBy` writers exist (deflect + woundFromExplosion), both now
  gated; the third `onProjectileDeflected` mention is the engine's
  listener BRIDGE, not a writer; (b) the flag2-gated arms verified
  (push, damping, exhaustion all inside the verdict — the refused
  punch charges nothing, matching the engine's deflect-verdict shape);
  (c) the intentionality check — the two javadocs' own prose records
  the census error ("the ballistic kinds answer false exactly the
  reference's takeDamage (arrows and shards...)" — the law applied to
  two kinds and skipped the other two; "the fireball pair + the wither
  skull" — subclass-counting, never override-checking): an honest
  omission, no deviation decision anywhere; (d) the edges — the
  dispensed charge rides the same SMALL kind (the re-pinned seat's
  launcher IS the dispenser's), the LARGE's shooter-immunity window
  composes (the re-aim flips the shooter — the existing
  strike-its-own-shooter seat stays green), and the refused wound
  fires no wire deflection (no velocity re-publish, matching the
  reference's velocityChanged never set).

### The fix

Both gates read `LARGE_FIREBALL` alone; both javadocs carry the
corrected override census; the wrongly-pinned seat re-pinned; the two
caller comments refined. The scatter (the caller's addVelocity arm) is
untouched — every gathered body still takes it.

### Evidence

The stash-verified discrimination (3 failing unfixed on exactly the
predicted assertions, the LARGE controls green on BOTH trees); the
whole projectile package + the explosion family + the vehicle blast
lanes + the TNT/dispenser/creeper/crystal/fire lanes green post-fix
(187/0 over 33 classes).

## F-EX3 — VERIFIED and FIXED (the 2026-10-31 session, the boot-registry census)

**Status: resolved** (the three passes complete in one session; the fix on
`fix/blast-resistance-boot-census`).

The nineteenth pass's suspicion was RIGHT but measured the wrong registry:
the standing census (`BlastResistanceTableTest`) enumerated **BuiltinBlocks
alone (957 rows)** — the engine's BOOT registry wraps ELEVEN registerAll
families on top (cauldron, ender chest, beacon, brewing, dispenser,
hopper, piston, tripwire, redstone, daylight detector, fluids — **1302
identifiers whole**), and **107 of the families' flattened STATE ids fell
through `BlastResistanceTable.read` to `blastResistance`'s fallback**.

### The divergence table (the probe's computed answers vs the reference)

| Family (count) | Engine fallback | Vanilla READ | The reference's own row |
|---|---|---|---|
| repeater_east_2 … (16) | **MAX_VALUE** | 0 | setStrength(0.0F) — Block.java 1195-1196 |
| comparator_*_compare/subtract (8) | **MAX_VALUE** | 0 | setStrength(0.0F) — 1319-1321 |
| lever_down/up_x(_powered) (4) | **MAX_VALUE** | 0.5 | setStrength(0.5F) — 1135 |
| tripwire_*suspended* (8) | **MAX_VALUE** | 0 | no setStrength — 1285 |
| moving_piston_*(_sticky) (12) | **MAX_VALUE** | **0** | MovingBlock setStrength(-1.0F) → the field FLOORS at 0 — NOT blast-immune |
| daylight_detector(_inverted)_p0..15 (32) | 1.0 | 0.2 | DaylightDetectorBlock ctor setStrength(0.2F) |
| hopper_*_disabled (5) | 15.0 | 4.8 | setStrength(3.0F).setBlastResistance(8.0F) — 1335 |
| piston_head_*_sticky (6) | 2.5 | 0.5 | PistonHeadBlock ctor setStrength(0.5F) |
| falling_water/lava(_1..7) (16) | MAX_VALUE | 100 | the fluid family's setStrength(100.0F) — 1017-1020 |

The loudest rows: **a redstone clock SURVIVED a point-blank creeper** in
the engine (the repeater, the comparator and the lever answered
MAX_VALUE — the rays died at the first cell) where vanilla shreds them
(READ 0/0.5 pays ~0.09-0.24 per crossing). The moving piston's row is
the model's own lesson: `setStrength(-1.0F)` (the unbreakable helper)
FLOORS the resistance field at `max(0, -5) = 0` — the moving carrier is
unbreakable BY MINING yet pays **READ 0** to a blast (the reference's
field/READ model: setBlastResistance ×3, setStrength floors at s×5, the
read ÷5 — Block.java lines 266, 312-315, 542-543).

### The three-pass record

- **V1 (source)**: every family's registration read directly (the
  Block.java lines above + the ctors + MovingBlock); the field/READ model
  verified at the setter bodies; the fallback's own divergence analysis
  (its `hardness * 5.0` answers the FIELD form — vanilla's read for a
  setStrength-only block is `hardness` itself; its `!diggable → MAX` has
  no vanilla basis — the lever/comparator/repeater/tripwire rows are
  `diggable=false` in the behavior table yet pay their declared READs).
- **V2 (behavioral — the stash discrimination)**: the probe census
  (full=1302, tableMissing=107, familyAdded=345, the fallback answers
  computed per identifier); the re-pinned census test + the value pins +
  the NEW wire seat failing UNFIXED on exactly the predicted points —
  the census's missing-set assertion, the value pins' null reads, and
  `BlastResistanceFallbackParityTest.theBlastShredsTheRedstoneClock`'s
  "Timed out waiting for the repeater shredded" (the MAX_VALUE fallback
  left the clock standing on its shelf) — and green FIXED (3/3). The
  wire seat's own engineering lessons: the parts sit on an OBSIDIAN
  shelf (READ 1200 — the crater cannot pop them by support loss, the
  only road by which the bare plain false-passes), and NO stone control
  between the blast and the comparator (a READ-6 stone SHIELDS the
  comparator behind it — vanilla's own shielding law, respected in the
  seat's geometry). The blast-fired proof: the creeper's registry
  removal (the blast replaces the body — no death flag).
- **V3 (independent)**: (a) the setter bodies re-read (the FLOOR
  semantics at lines 312-315 — the moving piston's READ 0 proven from
  the model, not inferred); (b) the fold-collision analysis — the new
  suffixes (`p<num>`, `inverted`, `disabled`, `suspended`, `sticky`,
  `x`) only ADD fold paths AFTER the direct/renames/identity lookups
  miss, so no previously-resolving identifier can change its answer
  (confirmed by the 523/0 regression); (c) the intentionality check —
  the census's own pin (957, "the registry's own row pin") believed
  itself complete: an enumeration omission, not a per-block decision;
  (d) the edges — the suspended tripwire CANNOT stand in a seat (the
  update system normalizes an unhooked suspended wire to its resting
  form — its fold is pinned by the census and the value seats instead);
  the moving piston's `diggable=false` behavior row STAYS (the dig
  family's correct unbreakable-by-mining law — the blast consult now
  answers 0 beside it); the fallback convention itself is now
  UNREACHABLE for registered blocks (the census asserts zero-missing)
  — its standing wrongness (the FIELD-vs-READ form and the
  diggable-gate) is this ledger's caveat, not a live defect.

### The fix

Seven renames (comparator/powered_comparator/repeater/powered_repeater →
the reference's unpowered/powered forms; moving_piston → piston_extension;
falling_water/falling_lava → water/lava) + six fold-suffix additions
(`p<num>` in the numbered pattern; `inverted`, `disabled`, `suspended`,
`sticky`, `x` in the word pattern). The census re-pinned to the FULL BOOT
REGISTRY (1302) with the registry() helper enumerating the engine's own
eleven-family boot chain; the value pins extended to all nine families;
the wire seat NEW.

### Evidence

The stash-verified discrimination (3 failing unfixed on exactly the
predicted points, 3/3 green fixed); the regression lanes green whole
(523/0 over 94 classes: the explosion family, TNT, vehicles, redstone,
the block package, hoppers, pistons, daylight, crystals, creepers,
projectiles).

## F-EX8 — VERIFIED and FIXED (the 2026-10-31 session, this branch): the wither birth blast's destructive flag

**Status: fix-ready → PR opened (this branch).** The twenty-first pass's
candidate, three-pass verified this session.

- **Subsystem:** the wither's birth explosion (Slice 59's event family)
  over the gamerule family's destructive gate (Slice 24a).
- **Affected engine files:** `EngineServer.onWitherBirth` (the listener
  dispatch's `detonateAtFromMob` call — the hardcoded `false`).
- **Reference:** `WitherEntity.mobAiTick` line 217 —
  `this.world.explode(this, this.x, this.y + this.getEyeHeight(),
  this.z, 7.0F, false, this.world.getGameRules().getBoolean("mobGriefing"))`.
- **Expected behavior:** the birth blast's SEVENTH argument (the
  block-breaking flag) reads the mobGriefing game rule. The 6th
  (createFire) stays `false`. The five-arg overload the creeper rides
  (`World.explode` lines 1597-1598) confirms the semantics: it delegates
  to `(source, x, y, z, power, false, destructive)` — the LAST argument
  is the destructive flag, and `CreeperEntity.explode` line 210 passes
  mobGriefing there.
- **Observed behavior (pre-fix):** the engine hardcoded `false` whole —
  a griefing world's wither birth wounded and knocked but never tore a
  single block, whatever the rule said.
- **The exact divergence:** one boolean at one call site — the rule
  consult never reached the blast walk.
- **Reproduction:** `WitherBirthGriefingParityAcceptanceTest` (NEW, 2
  seats) drives the REAL summon wire — a wither spawned onto a stone
  platform, `witherSummoned()`'s 220-tick blue window on the engine's own
  clock, the birth consumed by the manager's tick and dispatched through
  `onWitherBirth` into the detonation walk.
- **The three-pass record:**
  1. **Source:** the reference's line 217 read directly; the
     `World.explode` overloads' parameter semantics verified (the 6th
     createFire, the 7th destructive; the five-arg form's flag is
     destructive); the engine's `detonateAtFromMob(..., float power,
     boolean destructive)` forwards the flag into `detonateAtIn`'s
     `destructive` row — the fix's parameter target verified.
  2. **Behavioral:** the stash discrimination — the core seat (default
     rules, mobGriefing true) FAILED UNFIXED at exactly the predicted
     await ("the birth blast tore the platform under the wither" — the
     hardcoded false left the platform standing) while the GUARD seat
     (mobGriefing=false) passed on BOTH trees, proving the birth-event
     wire itself fires identically; FIXED the pair runs 2/0 green with
     the power pin (7.0F).
  3. **Independent:** the parameter-order challenge (the engine's flag
     rides the same position the reference's does — verified at the
     forwarding site); the compensating-behavior census (ONE
     `onWitherBirth` implementation engine-wide, no other gate between
     the event and the walk); the creeper's parallel form (the
     established `gameRules.getBoolean("mobGriefing")` consult my fix
     mirrors); the default rule value ("true", GameRules line 87 — the
     fixed engine now tears on default servers exactly like vanilla).
- **The fix:** `false` → `gameRules.getBoolean("mobGriefing")` at the
  `onWitherBirth` detonation call, with the reference's line citation in
  the comment. Nothing else changes: the wound walks, the packet, the
  power, the source-mob carry are untouched; the block-tearing arm and
  its drop rolls/TNT chain reaction run under the same walk the
  creeper's blast has always used.
- **Regression risk:** low — one boolean at one call site; the
  neighboring lanes verified green whole this session: WitherBossTest
  8/0, WitherExemptionArmsAcceptanceTest 5/0, CreeperRowsAcceptanceTest
  3/0, GameRuleAcceptanceTest 11/0, ExplosionEntityGatherParityTest 8/0,
  the new pair 2/0.
- **The tails this fix does NOT touch (recorded, their own lanes):** the
  birth blast's WORLD binding (the mob-sourced detonation family's
  overworld-bound route — F-EX11 below) and the createFire arm (F-EX4,
  PR #92).

## F-EX11 + F-EX12 — VERIFIED and FIXED (the 2026-10-31 session, this branch): the blast walk's remaining dimension routing

**Status: fix-ready → PR opened (this branch).** Discovered this session
while tracing F-EX8's call site; three-pass verified whole.

- **Subsystem:** the generalized detonation walk's dimension binding over
  the mob-sourced and projectile-sourced call sites (Wave FL4-F7's form
  completed).
- **Affected engine files:** `EngineServer.detonateAtFromMob` (the
  creeper's and the wither birth's shared helper — the hardcoded
  `(world, explosionService, 0)` triple), the `setExplosionSink` /
  `setSkullExplosionSink` wiring (the overworld `explosionService`
  passed beside the correctly-routed `worldForSafe(dimension)`), and the
  walk's own drop roll (the three-arg `spawnDropAtBlock` defaulting the
  dimension to 0).
- **Reference:** every blast call site receives the ACTING world —
  `CreeperEntity.explode` line 210, `WitherEntity.mobAiTick` line 217,
  `FireballEntity.onHit` line 33, `WitherSkullEntity` line 83, all
  `this.world.explode(...)`; the body is constructed IN its world, so
  the rays, the destruction commit, the wounds, the drops and the packet
  range the dimension the source lives in.
- **Expected behavior:** a nether creeper's blast, a nether ghast
  fireball's blast and a nether wither skull's blast tear the NETHER's
  cells and drop their items there; the overworld's same-coordinate
  cells are never consulted.
- **Observed behavior (pre-fix):** BOTH seams flew the ray fan through
  the OVERWORLD's same-coordinate cells — the creeper's and the wither
  birth's helper hardcoding the overworld triple whole, and the
  projectile sinks routing the blast WORLD per dimension while passing
  the OVERWORLD's `ExplosionService` (the service whose ray fan reads
  and commits its OWN bound world — `BlastWorld(world)` wraps a fixed
  target). A nether ghast fireball wounded nether bodies yet never tore
  the nether — exactly FL4-F7's pre-fix TNT signature ("the blast
  wounded but never tore") — while a coincident overworld block (a
  glass marker) TORE instead. The walk's drop roll compounded it: the
  three-arg spawn stamped the drops into the overworld.
- **The exact divergence:** `explosionServiceFor(dimension)` — the
  helper FL4-F7 built for exactly this routing — was consulted at ONE
  site engine-wide (the TNT seam). The remaining call sites kept the
  overworld binding: an enumeration omission, the F-EX3 census's own
  defect class. TF-9's own commit message claimed "the fireball's fire
  and detonation land in their own world through worldForSafe +
  detonateAtIn" — the claim was PARTIAL: the blastWorld yes, the service
  no.
- **Reproduction:** `NetherBlastRoutingAcceptanceTest` (NEW, 2 seats)
  with the FL4-F7 discriminator — a nether dirt platform, an OVERWORLD
  glass marker at the SAME coordinates, and the blast: the platform
  tears, the marker survives.
- **The three-pass record:**
  1. **Source:** all four reference call sites read directly (the
     `this.world` receiver at every blast); the engine's seams traced —
     `detonateAtFromMob`'s hardcoded triple, the two sinks' mismatched
     pairing, `BlastWorld`'s fixed-target binding inside
     `ExplosionService`, and the drop roll's three-arg default.
  2. **Behavioral:** the stash discrimination — BOTH seats FAILED
     UNFIXED at exactly the predicted awaits (the nether platform never
     tore: the overworld service's world never writes a nether cell),
     and both run 2/0 green FIXED with the event-dimension pins (the
     wire event's own row -1, the pre-fix 0) and the marker-survival
     guards.
  3. **Independent:** the intentionality challenge (the engine's own
     mobs and projectiles carry dimension stamps — Slice 25d / TF-9 —
     the data was present, the consult wasn't; FL4-F7's own record
     names this binding a defect for the TNT; TF-9's partial claim
     re-read); the compensating-behavior census (no second blast path
     exists; the routing helper was built and consulted once); the
     overworld byte-identity (`worldForSafe(0) == world` and
     `explosionServiceFor(0) == explosionService`, verified at both
     bodies — every existing overworld blast keeps the identical path);
     the unbooted-dimension fallback (the worldForSafe law); and the
     family's own completion (the walk's drop roll stamped with the
     walk's dimension, the TF-9 four-arg form).
- **The fix:** `detonateAtFromMob` routes by `source.dimension()` (the
  FL4-F7 form); the two projectile sinks consult
  `explosionServiceFor(dimension)`; the drop roll passes the walk's
  `dimension`. Nothing else changes — the flags, the powers, the wound
  walks, the packet shape are untouched.
- **Regression risk:** low — the overworld paths are byte-identical by
  construction; the lanes verified green this session: the new pair 2/0,
  CreeperRows 3/0, NetherTntRouting 1/0, ExplosionEntityGather 8/0,
  ProjectileDimensionReads 5/0, WitherBoss 8/0, ArmorStand 13/0,
  NetherDimension 5/0, the vehicle family whole (RidingFollow 6,
  SpawnerMinecart 8, Persistence 2, Fire 8, HopperMinecart 7,
  DimensionReads 3, WorldDamage 3), EnderCrystal 10/0, WitherSkull 4/0,
  FireballDeflection 5/0.
- **The tails this fix does NOT touch (recorded, their own lanes):** the
  TNT cart's blast sink (the vehicle family's dimension-blind
  `BlastSink` interface — the ledged tail until the family carries a
  dimension field) and the skull family's summon path (the overworld
  `SkullManager.Host` consults — a wither BUILT in the nether needs the
  family's own routing pass first).
