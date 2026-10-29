# The fall-path family's remainder — findings (the verifier's fifteenth pass, discovery record)

Scope: the demand-named remainder of the fall-path family the Wave TR
ledger row named — the slime pad's trio (`onFallenOn` / `beforeCollision`
/ `onSteppedOn`) and the redstone ore's glow family — read against the
engine's move paths (`MobEntity`'s solver + legacy walks, the player's
`tickLanding`, `ItemEntity.tick`, `ExperienceOrbEntity.tick`,
`VehicleEntity.solverMove`), the step path (`MobEntity.stepSoundWalk`,
its STALE no-op claim), the ore's landed arms (`useBlockArmOnTick`, the
dig-start listener, `RandomTickSystem`'s reversion), the drop walks
(`DropService`, `BlockBehaviorTable`'s silk set), and the FX bus
(`FxManager` over the 0x2A wire seam).

Reference anchors: `reference/1.8.8/net/minecraft/block/SlimeBlock.java`
(the trio + the 0.8 slipperiness), `RedstoneOreBlock.java` (the glow
family + the silk-drop override), `Block.java` (the default seams:
`onSteppedOn` 679, `onFallenOn` 878, `beforeCollision` 882,
`getSilkTouchDrop` 791, `afterMinedByPlayer` 773), `Entity.java`
(`move`'s dispatch order 561-655 — the -0.2 feet consult 568-579, the
fall consult 581, the vertical-clip consult 590-592, the steps band
594-624 with the `onSteppedOn` call at 602-604, the block-collision
walk 667-689, `checkFallDamage` 719-733, `takeFallDamage` 749-753,
`makesSteps` 715-717), `LivingEntity.java` (the landing-dust consult
161-182, `takeFallDamage` 823), `ItemStack.java` (the `ItemStack(Block)`
constructor 48-57), `Block.java`'s registration 1353 (the slime's
SLIME_SOUNDS family), `ArrowEntity.java` (the stick path — no slime
special-case).

The audit follows the standing verifier protocol: discovery first,
every finding verified against the reference line-by-line (V1) and the
engine's site read whole (V2) before any fix; the third verification is
the seat suite on the pre-fix tree (V3, §G's closure per finding). This
document is the discovery record; the fix commits close each finding's
§G row.

---

## A. The slime pad's trio (every arm absent — the demand-named slice)

The reference's dispatch order (Entity.move lines 568-604): after the
move's clip resolution the SAME feet cell feeds every consult —
`floor(y - 0.2)` with the air-cell fence/wall/gate fallback (the cell
BELOW the body, never the body's own). `checkFallDamage` reads it
(581), `beforeCollision` reads it (590-592, on the vertical clip
`d4 != dy`), and the steps band reads it (594-604) — the band is
`makesSteps() && !flag && vehicle == null` where `flag = onGround &&
isSneaking() && instanceof PlayerEntity` (line 412): a ground-sneaking
PLAYER alone skips the band; the consult itself is
`if (block1 != null && onGround) block1.onSteppedOn(world, blockpos,
this)` at 602-604 — per move call, every tick, BEFORE the
distance-walked band that gates only the step SOUND.

### A1. The soft landing (SlimeBlock.onFallenOn lines 24-30)
- Reference: landing with fallDistance > 0 on the slime cell →
  `entity.takeFallDamage(fallDistance, 0.0F)` — the damage multiplier
  ZERO — unless the body isSneaking() → super (`takeFallDamage(
  fallDistance, 1.0F)`, the Block default line 878-880: normal
  damage). A mob never sneaks; a player holding shift takes the full
  landing.
- Engine: every fall consult writes the multiplier 1.0 — the mob
  solver path (MobEntity lines 11289-11290: `takeFallDamage(
  fallDistance, 1.0f)`), the player's `tickLanding` (EngineServer
  6223-6239: `ceil(distance - 3)` with no block consult beyond the
  farmland trample). Landing on slime = full fall damage.
- Divergence: a mob or player falling onto a slime block takes the
  full wound. Vanilla: the slime pad spares every non-sneaking body
  (the famous fall-damage cushion).
- Fix seats: the mob solver path's landing consult (extend the landed
  block read — the trample consult's own landed cell is already
  computed there) and the player's tickLanding (the -0.2 cell with the
  fence fallback, `session.sneaking()` for the gate).

### A2. The bounce (SlimeBlock.beforeCollision lines 33-39)
- Reference: on the vertical clip (`d4 != dy`, Entity.move 590-592),
  consulting the -0.2 feet cell: sneaking → super (the Block default
  line 882-884: `velocityY = 0.0`); else `velocityY < 0.0` →
  `velocityY = -velocityY` (the negation — the bounce). The vy >= 0
  ceiling hit on a slime cell falls through BOTH branches: NO zeroing
  (the default's ceiling zeroing is lost on slime — the body keeps
  pushing up until gravity wins; an edge the fix must carry
  faithfully, pinned by a seat).
- Engine: every vertical-clip site zeroes — the mob's `solverMove`
  (10670-10672: `result.zeroVelocityY() → velocityY = 0.0`), the
  vehicle's `solverMove` (VehicleEntity 693-695), `ItemEntity.tick`
  (the solver arm 341-343 + the legacy arms 370-380),
  `ExperienceOrbEntity.tick` (245-263). No negation anywhere.
- Divergence: bodies landing on slime dead-stop. Vanilla: mobs,
  items, XP orbs and vehicles bounce (the item's resting drift cycle
  — vy = (0 - 0.04) * 0.98 each tick, negated each clip — sustains a
  micro-hop forever: items visibly jiggle on slime; the fix must
  reproduce the sustained oscillation, not a single flip).
- Player carve-out: the player's movement is client-authoritative —
  the real 1.8.8 client runs its own Entity.move and applies the
  bounce locally; the server owes NOTHING for the player's own
  bounce (and the server has no player velocity model to negate).
- Fix seats: the mob solver path (consult the -0.2 cell at the
  zeroVelocityY site — the same cell the fall consult computes; the
  solver path runs the fall consult right after, so the cell is
  computed once), `ItemEntity.tick`'s landing arms, the orb's tick,
  the vehicle's solverMove.

### A3. The step damping (SlimeBlock.onSteppedOn lines 42-50)
- Reference: every steps-band tick on the slime cell with
  `|velocityY| < 0.1 && !isSneaking()` → `d0 = 0.4 + |velocityY| *
  0.2`; `velocityX *= d0; velocityZ *= d0`. The horizontal velocity is
  multiplied DOWN (the pad drags walkers to a fraction of their
  speed); with the 0.8 slipperiness the crossing is visibly sluggish.
- Engine: no onSteppedOn dispatch exists (see §C). The mob's velocity
  model is a persistent per-tick damped vector (MobEntity 8243-8247:
  the walk input ADDS to the velocity, `velocityX *= 0.6` per tick),
  so a `×d0` consult at the step site is observable — the body's
  per-tick displacement compounds down.
- Player carve-out: the client owns the local damping (the server has
  no player velocity); nothing owed.
- Fix seats: the mob's stepSoundWalk (the ground/feetY cell is
  already computed there) and the item/orb ticks (their velocityX/Z
  are per-tick damped — the same compounding shape).

### A4. The friction consult (the adjacent row — NOT demand-named)
- Reference: the constructor sets `slipperiness = 0.8F` (line 15);
  the friction walk reads the block's own slipperiness (ice/packed ice
  0.98, the default 0.6).
- Engine: `blockSlipperinessAt` carries the whole table correctly
  (EngineServer 19371-19382) — but ONLY the armor-stand family
  consults it (ArmorStandSystem 596-635). The mob walk hardcodes
  `onGround ? 0.6 : 0.98` (MobEntity 8245), and the item/orb ticks
  hardcode the same 0.6 (ItemEntity 304, ExperienceOrbEntity 247). A
  mob or item crossing ICE never slides (0.6 vs 0.98), and the slime's
  0.8 never applies.
- This is the friction family's own row (ice included), recorded here
  as its own finding (F-FRICTION in §F's candidate list — the fix
  shape touches the same walk sites as A3 but is a separate law with a
  broader family; it does NOT ride this pass's PR).

---

## B. The redstone ore's glow family (the remainder the wave's row named)

### B1. The walk-on lighting (RedstoneOreBlock.onSteppedOn 40-43)
- Reference: `onSteppedOn` → `interact` (51-56): the particle burst +
  the swap `if (this == REDSTONE_ORE) setBlockState(LIT)`. Standing on
  the ore lights it (the per-tick steps band); the LIT form's consult
  carries only the burst.
- Engine: the use arm (EngineServer 11157-11162) and the dig-start
  listener (3289-3296) swap unlit → LIT ✓; the walk-on arm does not
  exist — a mob or player standing on unlit redstone ore never lights
  it, and neither does a dropped item or an XP orb (all four body
  families pass the reference's band).
- Fix seats: the mob stepSoundWalk, a per-tick player arm (the same
  tick() family as tickLanding — `session.onGround()`, the !sneaking
  gate), the item/orb ticks' ground consults.

### B2. The interact particle burst (addParticles 96-132, the S2A broadcast)
- Reference: every `interact` (use/startMining/onSteppedOn) calls
  `addParticles`: SIX reddust particles, one per pass, each positioned
  `pos + nextFloat()` on its axis, the six passes pinning the six
  faces (up/down/south/north/east/west) — each face's particle
  relocates to the face band `±0.0625` ONLY when the neighbor that
  side is NOT solid-render (`d2 = y + 0.0625 + 1.0` for the open top,
  etc.), and spawns only when the computed point falls outside the
  block's own bounds (the 128-line clamp consult). Server-side:
  `World.addParticle` → the WorldEventListener broadcast → the 0x2A
  packet to every player in the 32-block radius (the /particle law's
  own band).
- Engine: no burst anywhere. The use arm's comment claims "the
  particles ride the client's randomDisplayTick band" — HALF right:
  the LIT form's AMBIENT display is client-owned (the real client
  renders it from its own blockstate), but the interact burst is a
  SERVER broadcast at the swap moment — the engine never emits it, so
  no real client sees the spark at the moment of lighting.
- The wire seam exists: `ClientLink.worldParticles` (the 0x2A record,
  the /particle command's own broadcast walk, EngineServer 24870-24889)
  and the FX bus carries particle precedents (blockShatter's
  BLOCK_DUST, the potion splash, the smoke). A `RedstoneOreSpark` FX
  event over the six-face law is the natural shape.
- Fix seat: the FxManager record + the three interact arms (use,
  dig-start, and B1's new step arm — the burst fires for BOTH forms:
  the lit form's use/dig-start/step spark without the swap).

### B3. The LIT form's silk drop is EMPTY (the silk-arm audit's miss — NEW)
- Reference: `RedstoneOreBlock.getSilkTouchDrop` (134-137) OVERRIDES
  the default walk — `return new ItemStack(Blocks.REDSTONE_ORE)` —
  and the `ItemStack(Block)` constructor resolves
  `Item.byBlock(REDSTONE_ORE)` (ItemStack.java 56-57: the registered
  ore ITEM, legacy 73) → a valid stack, non-null →
  `afterMinedByPlayer` (Block.java 776-780) drops it. The override
  rides the CLASS: it answers for the LIT form exactly as for the
  unlit one — silk-mining a LIT redstone ore drops THE PLAIN ORE ITEM.
  In practice the dig-start swap (startMining → interact) means the
  LIT form is the one every survival silk pick breaks.
- Engine: `DropService`'s ore arm (lines 565-579) — the unlit form is
  `SILK_TOUCHABLE` (BlockBehaviorTable 66) and the general silk arm
  (430-439) drops the ore item ✓; the LIT form is NOT in the silk set
  and falls to the family arm's `if (silkTouch) return List.of()` —
  NOTHING DROPS. The arm's own comment records the mistaken reasoning:
  it read the DEFAULT `getSilkTouchDrop` (`Item.byBlock(this)` → null
  for the unregistered lit form) and never saw the override. A silk
  pick on redstone ore in ZaminTorch LOSES THE BLOCK ENTIRELY.
- The XP rows are whole for both forms (ExperienceAwards 25/31:
  [1, 6] — `1 + nextInt(5)` ✓); the plain dust walk is whole (4 +
  nextInt(2) + nextInt(fortune + 1) ✓); the lit form's behavior row is
  whole (hardness 3.0, the rock family, tier 3 ✓); the anvil import
  rows are whole (73/0, 74/0 ✓).
- Fix seat: one line — the lit form's silk consult returns the ore
  item (`minecraft:redstone_ore`, count 1) instead of the empty list;
  the comment corrected to cite the override.

### B4. The landed rows (no action owed — the honest records)
- The random-tick reversion (LIT → ore): landed (RandomTickSystem
  340-345, Slice 33) ✓ — `setTicksRandomly(true)` rides the lit
  registration and the reversion consults the form, no scheduled
  delay, the reference's own `tick` seam.
- The use arm's fall-through (super.use → false → the placement
  proposal keeps flowing) ✓ (useBlockArmOnTick 11157-11166).
- The dig-start arm ✓ (the mining-start listener 3289-3296).
- The light rows: the LIT form 9/15, the plain 0 ✓ (BlockLightTable
  749, 727-739).
- The glow's blast/map-color/sound rows ✓ (the stone family).

---

## C. The step dispatch seam (the STALE claim, re-pinned)

- The engine's step path, `MobEntity.stepSoundWalk` (10725+), carries
  the javadoc claim (10721-10723): "The reference's onSteppedOn call
  is a 1.8.8 no-op (no block class overrides it — the farmland
  trample rides the fall path) and carries nothing here." The claim
  was recorded by the step-sound slice and was ALREADY re-pinned
  STALE by Wave TR's ledger row; this pass re-pins the CODE comment
  with the fix.
- The reference's three overrides: `SlimeBlock.onSteppedOn` (42-50,
  §A3), `RedstoneOreBlock.onSteppedOn` (40-43, §B1), and
  `StairsBlock.onSteppedOn` (468-469) — the stairs' DELEGATION to its
  base block, a 1.8.8 no-op in practice (no vanilla stair base is a
  slime or an ore: the delegation carries nothing observable; the
  engine's stairs owe no arm, recorded here so the delegation is not
  "found" again).
- The band the engine already models 1:1 in stepSoundWalk:
  `makesSteps()` (the seven structural overrides), the -0.2 feet cell
  with the fence fallback, the distance-walked band for the SOUND.
  The onSteppedOn consult must ride the same band but BEFORE the
  distance-walked gate (the reference's order: the consult at 602-604,
  the sound's gate at 608) — standing still on the ore lights it on
  the first tick.

## D. The body-family seats (who owns which arm)

| Body | bounce (beforeCollision) | soft landing (onFallenOn) | step consult (onSteppedOn) |
|------|--------------------------|---------------------------|---------------------------|
| Mob  | owed — solverMove's zeroVelocityY (10670) | owed — the landing consult (11289) | owed — stepSoundWalk |
| Player | client-owned (nothing owed — no server velocity model) | owed — tickLanding (6223) with the sneak gate | owed — a per-tick arm (the tickLanding family), the !sneaking band |
| ItemEntity | owed — tick's landing arms (341/370) | not owed (items take no fall damage) | owed — tick's ground consult |
| XP orb | owed — tick's landing arms (245-263) | not owed | owed — tick's ground consult |
| Vehicle (cart/boat) | owed — solverMove (693-695) | the rider delegate (Entity.takeFallDamage 749-753 — a cart landing spares its rider; the engine's vehicle tracks fallDistance but has no takeFallDamage consult — recorded, the rider-sparing is the §F candidate) | not owed (makesSteps false — the reference's own override) |
| TNT primed | the physics family's own row (the engine's TNT tick — §F candidate) | not owed | not owed (makesSteps false) |
| Falling block | places regardless (the reference's onGround consult places the block — no divergence) | n/a | not owed (makesSteps false) |
| Projectile/arrow | never consulted (the arrow integrates directly, never calls move — ArrowEntity 328-329; sticks on ray hit with no slime special-case; the engine's stick law matches) | n/a | n/a |

The player's sneak flags exist on the session (`sneaking()`,
PlayerSession 1693) for both the fall gate (A1) and the step band (B1).
Mobs never sneak — the reference's isSneaking() is false for every mob.

---

## E. The three-verification record

- **A1 (the soft landing):** V1 — SlimeBlock.java 24-30 read line-by-line
  against Block.java's default 878-880 and Entity.checkFallDamage 719-733
  (this pass, 2026-10-29): the multiplier is the BLOCK's to own, the
  slime answers 0.0, the sneak gate routes to the 1.0 default. V2 —
  both engine fall consults read whole (MobEntity 11245-11298 + the
  legacy 11392-11396; EngineServer tickLanding 6202-6260): every site
  writes 1.0/unconditional; the farmland consult proves the -0.2-cell
  shape the fix extends. V3 — the seat suite on the pre-fix tree (the
  fix branch's stash run).
- **A2 (the bounce):** V1 — SlimeBlock.java 33-39 over Block.java
  882-884 and Entity.move 590-592: the negation is the vertical-clip
  consult over the -0.2 cell, the vy>=0 edge carries NO zeroing. V2 —
  all five vertical-clip sites read whole (mob/vehicle/item/orb; the
  player is client-owned): every site zeroes; the item's resting-drift
  cycle (ItemEntity 327-328) proves the sustained micro-hop shape the
  fix must reproduce. V3 — the stash run.
- **A3 (the step damping):** V1 — SlimeBlock.java 42-50: the 0.4 +
  |vy|*0.2 multiply on both horizontal components, the |vy| < 0.1
  gate, the sneak gate. V2 — the mob's velocity model read whole
  (MobEntity 8243-8247: the persistent damped vector — the compounding
  that makes the multiply observable; the item/orb's 0.6 damping the
  same shape). V3 — the stash run.
- **B1 (the walk-on lighting):** V1 — RedstoneOreBlock 40-43 over
  interact 51-56 over Entity.move 594-604: the steps band fires the
  consult per move tick, the ground-sneaking player alone skips. V2 —
  the engine's three existing arms read whole (use 11157, dig-start
  3289, reversion RandomTickSystem 340) and the step site read whole
  (stepSoundWalk 10725+ — the band's gates all present, the consult
  absent); the item/orb ticks' ground sites read whole. V3 — the
  stash run.
- **B2 (the interact burst):** V1 — addParticles 96-132 read line-by-
  line (the six passes, the solid-render consults, the 0.0625 bands,
  the bounds clamp) + World.addParticle 796-810 over the listener
  broadcast + the S2A radius band. V2 — the engine's FX bus + the
  worldParticles wire seam read whole (the /particle broadcast walk
  24870-24889 — the 32-radius band's own precedent); the use arm's
  half-true comment identified. V3 — the stash run.
- **B3 (the LIT silk drop):** V1 — RedstoneOreBlock.getSilkTouchDrop
  134-137 read against Block.afterMinedByPlayer 773-785 and the
  ItemStack(Block) constructor 48-57: the override answers for the
  class, the plain ore's item resolves, the stack is dropped. V2 —
  DropService's walk order read whole (the general silk arm 430 before
  the ore arm 565; the silk set's membership BlockBehaviorTable 48-66;
  the lit form's fall-through to the empty list 578-579) with the
  dig-start swap (EngineServer 3289) proving the lit form is the
  broken one in practice. V3 — the stash run.

## F. The future-pass candidates (the honest remainder)

- **F-FRICTION: the ground-friction consult.** The mobs', items' and
  orbs' walks hardcode 0.6 (MobEntity 8245, ItemEntity 304, the orb
  247); the reference reads the block's slipperiness (ice/packed ice
  0.98, slime 0.8, the default 0.6). The table itself is already in
  the engine (`blockSlipperinessAt`) — the armor-stand family alone
  consults it. The ICE slide is the loudest half of this family.
- **F-VEHICLE-BOUNCE: the vehicle family's own bounce consult.** The
  reference's `beforeCollision` fires for every body — the cart and the
  boat included (they ride `Entity.move` whole; a dropped cart bounces
  on a slime pad). The engine's `VehicleEntity.solverMove` (693-695)
  zeroes `velocityY` on the vertical clip with no consult; the vehicle's
  `WorldQuery` surface needs its own block-id read to carry the -0.2
  consult — the family row rides the F-RIDER-SPARE pass (the vehicle
  landing family's own slice; the mobs/items/orbs carry this PR's trio).
- **F-RIDER-SPARE: the vehicle's landing rider-delegate.**
  Entity.takeFallDamage 749-753 delegates to the rider; a cart/boat
  landing on slime spares its rider the wound (the multiplier consult
  applies). The engine's VehicleEntity tracks fallDistance but has no
  takeFallDamage consult; the passenger's fall-damage path on
  dismount/landing is its own family row.
- **F-DUST: the landing-dust burst.** LivingEntity.checkFallDamage
  161-179: the > 3-block landing broadcasts the BLOCK_DUST count
  `150 * min(0.2 + (dist-3)/15, 10)` — a server-side particle family
  the engine has not modeled (the blockShatter seam exists for it).
- **F-ITEMSTEP: the non-mob bodies' step sounds — REJECTED WITH THE
  PASS (the re-adjudication below, §H/3):** `ItemEntity.makesSteps()`
  (lines 44-46) and `ExperienceOrbEntity.makesSteps()` (lines 32-34)
  are FALSE — items and orbs never enter the steps band at all, no
  step sounds, no ore lighting, no damping. The row's premise ('items
  ride Entity.move whole') missed the makesSteps overrides.
- **F-VINE (carried from the fourteenth pass §F): the vine family's
  own pass** — the 1-in-4 spread walk, the no-connections drop, the
  (106, 1..14) import isolation.
- The slime's ceiling edge (vy >= 0 → no zeroing, §A2's reference
  note) is pinned INSIDE this pass's fix as a faithful edge, not a
  candidate.

## G. The closure ledger (updated by the fix commits, RE-ADJUDICATED in §H)

| ID | Status | Closed by |
|----|--------|-----------|
| A1 | CLOSED by Wave SG 5429b60b (the soft landing, the sneak gate) | develop |
| A2 | CLOSED for mobs/vehicles by Wave SG 5429b60b (the bounce; the cart twin); items/orbs REJECTED in §H/4 (the -0.5/-0.9 ground negations shadow the flip) | develop |
| A3 | CLOSED for mobs by Wave SG 5429b60b (the damping inside stepSoundWalk); items/orbs REJECTED in §H/4 (makesSteps false) | develop |
| B1 | CLOSED-REDUCED: the mob/player arms landed by Wave SG 5429b60b; the item/orb extension REJECTED in §H/3 (makesSteps false for both); the gate's own gaps CLOSED by PR #74 (the squid colliding move + the mob gate) and PR #75 (the flying player) | develop + PR #74/#75 |
| B2 | REJECTED — see §H/2 (the empty-stub chain); PR #73 CLOSED unmerged with the evidence | — |
| B3 | CLOSED by PR #72 (stash-verified failing on the pre-fix tree) | fix/lit-ore-silk-drop-parity-audit |
| A4/F-FRICTION | suspected (V1 evidence; the family broader than this pass) — the next pass's loudest candidate | a future pass |
| F-RIDER-SPARE | suspected | a future pass |
| F-DUST | suspected | a future pass |
| F-ITEMSTEP | REJECTED in §H/3 (makesSteps false for items/orbs) | — |
| F-VINE | suspected (the fourteenth pass's own record) | a future pass |
| F-SQUID-WALK | CLOSED by PR #74 (the colliding move restored; the phasing repaired) | fix/step-band-gate-parity-audit |
| F-FLY-GATE | CLOSED by PR #75 (the flying player never enters the band) | fix/flying-step-gate-parity-audit |
| F-ORB-GLUE | suspected (§H/4 — the narrow surviving observable) | a future pass |
| F-BAT-WALK | suspected (§H/5 — the bat's flight still integrates raw) | a future pass |
| F-GUARDIAN-WALK | suspected (§H/5 — the same moveRelative family) | a future pass |
| F-SQUID-LANDING | suspected (§H/6 — the landing consult arms absent) | a future pass |
| F-STEPHEIGHT | suspected (§H/7 — LivingEntity 0.6 vs the engine's 0.5) | a future pass |

## H. The re-adjudication (the pass's own claims against the reference — the
## session after the PR #73 opening)

The fifteenth pass's §B1/§B2 claims, the §A item/orb arms, and the
§22 queue's item/orb slice were re-verified against the reference
from cold, three independent reads each. Four claims of this pass
were DISPROVEN by its own reference; the corrections follow.

### H1. B2 REJECTED — the interact spark burst is NOT a server broadcast

The reference chain, end to end: `RedstoneOreBlock.addParticles`
line 129 calls the 7-double-arg `World.addParticle(ParticleType,
double x6, int...)` (World.java:796) → the private dispatch
(World.java:806-812) over `eventListeners` ALONE — and the dedicated
server's listener is `ServerWorldEventListener`, whose `addParticle`
override (lines 23-26) is an EMPTY STUB, while its `playSound`/
`doEvent`/`notifyBlockChanged` DO send packets. The broadcasting form
`ServerWorld.addParticle` (ServerWorld.java:899-932, the
`ParticleS2CPacket` + the 256-radius walk) has a DIFFERENT SIGNATURE
(the `count` + `velocityScale` parameters); blocks hold a `World`
reference and the 7-double form can never resolve to it.
Singleplayer's visible burst rides the integrated server's
client-bridge listener — a dedicated server lacks it. NOTHING is
owed 1:1. The pass's '0x2A wire over the six-pass band' ports a packet
vanilla's dedicated server never sends. PR #73 closed unmerged with
this evidence (the comment of record).

### H2. The makeSteps law re-read — five mob kinds, not three; items
### and orbs NEVER ride the band

`Entity.makesSteps()` defaults TRUE (line 715). The overrides to
FALSE: the guardian (229), the endermite (51), the silverfish (57),
the BAT (BatEntity 144-146), and the SQUID (SquidEntity 73-75) — the
engine's gate carried three. The bodies this pass claimed as band
riders are NOT: `ItemEntity.makesSteps()` (44-46) and
`ExperienceOrbEntity.makesSteps()` (32-34) are FALSE, as are the
arrow (444), the falling block (53), the ender crystal (29), the TNT
(37), the boat (41), and the minecart (82).

### H3. B1's item/orb extension REJECTED — and the seats that pinned it

The claim 'items and XP orbs light the ore by standing on it' is
contradicted by the makesSteps law above: items and orbs never enter
the steps band, never trigger `onSteppedOn`, never light redstone
ore, never take the slime damping, never make step sounds. PR #73's
item/orb arms and its 'the item's rest' / 'the orb's rest' seats
encoded a law vanilla contradicts — the stash probe passed 4/5 over
the pre-fix tree only because the seats asserted the WRONG law. The
lesson, recorded: a probe verifies only the law it is told to assert;
the reference read is the authority, not the seat's green.

### H4. §A's item/orb bounce arms RE-ADJUDICATED — the flip is shadowed

The reference's own friction chains negate the slime flip every
tick: the item's `if (onGround) velocityY *= -0.5` (ItemEntity
96-104, after the x0.98) and the orb's `*= -0.9` (ExperienceOrbEntity
116-130). The post-tick velocityY stays NEGATIVE on slime (the
item's steady state ~-0.0384, the orb's ~-0.224) and the POSITION
NEVER RISES — the §A 'resting micro-hop cycle' was a mis-prediction;
no visible hop exists for items in 1.8.8, and the engine's flat rest
is CORRECT. The sole surviving observable, narrow: the ORB's glue
delay — the negative internal state swallows the +0.1 attraction for
~3-5 ticks before the orb lifts off slime toward a collecting player
(the engine lifts instantly). Recorded as F-ORB-GLUE (narrow; needs
its own three-pass verification before any fix).

### H5. The NEW find the re-adjudication uncovered — the squid phasing (CLOSED by PR #74)

The seat that disproved H3 timed out: the squid spawned over a
redstone-ore floor never grounded. The reference's
`SquidEntity.moveRelative` override (lines 154-157) is the FULL
COLLIDING `this.move(vX, vY, vZ)` — the reference's own Entity.move
with the clip and the steps band — but the engine's Slice 16e read
it as RAW POSITION INTEGRATION (`position += velocity; onGround =
false`): every squid in ZaminTorch PHASED THROUGH BLOCKS (a stranded
squid sank out of the world; a swimmer crossed aquarium glass). The
squid now rides the swept solver with the steps band over the
applied move, the makesSteps gate completed (the squid AND the bat —
the bat currently unreachable, see F-BAT-WALK), the stub worlds keep
the legacy raw fallback (the walker's own shape). Stash-verified:
the grounding await fails over the pre-fix tree. The GUARDIAN carries
the same engine pattern ('owns the whole integration, no walk') where
the reference's moveRelative rides the colliding move in water and
the super's walk on land (GuardianEntity 441-458) — recorded as
F-GUARDIAN-WALK for its own pass. The BAT likewise
('no gravity, no walk' where the reference bat rides the standard
chain) — F-BAT-WALK.

### H6. The flying-player gate (CLOSED by PR #75)

The landed tickStepOn review: the reference's player overrides
makesSteps with `!this.abilities.flying` (PlayerEntity 1568-1570) — a
creative body flying at ground contact never enters the band, never
lights the ore. The engine's consult carried only the
onGround/sneak/vehicle gates; the client's reported abilities bit
(PlayerSession.flying, the 0x13 packet's own flag) joins them.
Stash-verified: 'expected redstone_ore but was lit_redstone_ore'
over the pre-fix tree.

### H7. Follow-ups recorded

- **F-SQUID-LANDING:** the walk adoption covers the clip + the steps
  band; the reference's move also carries checkFallDamage (the
  squid's landing consult — the trample, the slime soft-landing, the
  fall damage) — needs its own pass.
- **F-STEPHEIGHT:** LivingEntity sets stepHeight 0.6 (line 143); the
  engine's walkers carry 0.5 (WALK_STEP_HEIGHT) — a family-wide
  candidate.
- **THE PRE-EXISTING RED ON DEVELOP** (flagged for the next wave's
  review, nothing to do with the PR #74/#75 diffs): MobSolverWalkTest
  7/7, SpiderWallClimbTest 3/4, VillageReturnWalkTest 4/5 fail
  IDENTICALLY on the pristine develop tree (verified by stash before
  either branch's first line) — a landing between 5429b60b and now
  broke the walker lanes.
- **F-FRICTION remains the loudest queued candidate** (the
  slipperiness consult: the mobs/items/orbs hardcode 0.6 where the
  reference reads the block's own slipperiness — the item's ground
  band `slipperiness * 0.98` (ItemEntity 96-101), the orb's (116-124),
  the mob travel's `slipperiness * 0.91` family) — the next pass's
  own demand-named slice; the §22 item/orb 'bounce/damping/lighting'
  arms are NOT owed (see H3/H4) and MUST NOT be implemented.
