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
