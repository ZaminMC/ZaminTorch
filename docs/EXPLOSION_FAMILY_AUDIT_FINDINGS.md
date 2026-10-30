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

The engine's player-punch deflection (`ProjectileManager.deflect`)
applies the reference's `ProjectileEntity.takeDamage` re-aim to the
LARGE_FIREBALL and SMALL_FIREBALL kinds alone; the javadoc claims "the
ballistic kinds answer false exactly the reference's takeDamage". The
reference's hierarchy: **WitherSkullEntity extends ProjectileEntity**
(reference/1.8.8 entity/projectile/WitherSkullEntity.java line 16) — the
skull CARRIES the re-aim arm (a player's punch on a wither skull
redirects it along the puncher's look, the puncher becoming the new
shooter). The blast arm this pass landed includes the skull (the
reference's family whole); the PUNCH surface alone excludes it. Status:
suspected — awaiting its own three passes (a thin seat: a punch on a
wither skull mid-flight).

## The queue after this pass

F-EX2 (the sleeping eye-height frame — 0.2 vs 1.62 in the knockback
vector), F-EX3 (the BlastResistanceTable coverage census), F-EX4 (the
createFire arm — the wrong-world bed + the mobGriefing ghast fireball),
F-EX5 (the punch deflection's skull exclusion), and the standing
architecture tail F-FL4 (the nether/End fluid simulation — the §22
queue head, the per-dimension scheduled/random-tick infrastructure it
needs first). The F-EX1 family is retired end to end.

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
