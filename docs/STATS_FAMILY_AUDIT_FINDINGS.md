# The stats family parity audit — findings (the verifier's eleventh pass, discovery record)

Scope: every statistic increment the 1.8.8 reference pays, read against the
engine's `stat` package and every increment site. The counter model
(`PlayerStats` — increment/set/dirty/progress, the achievement gate) was
surveyed first and is 1:1; the audit's territory is the INCREMENT SITES: which
stats fire, where, under which gates.

Reference anchors: `net/minecraft/stat/Stats.java` (the registry),
`PlayerEntity` (the clocks, kills, drops, damage, movement walk, jump),
`ServerPlayerEntity.die` (the victim walk), `LivingEntity.die` (the killer
walk), `PlayerInventory`/`ItemStack` (the item-stat family),
`FishingBobberEntity.getResult`, `VillagerEntity.interactMob`,
`TradeResultSlot`, the per-block interaction stats, `ServerPlayerStats`.

---

## A. The never-incremented stats (verified against the whole tree)

### A1. PLAYERS_KILLED never lands
- Reference: `PlayerEntity.takeKillScore` lines 555-570 — `victim instanceof
  PlayerEntity` → `incrementStat(PLAYERS_KILLED)` beside the scoreboard ride.
- Engine: `takeKillScore` (EngineServer ~6738) pays the scoreboard objectives
  alone (TOTAL_KILL_COUNT, PLAYER_KILL_COUNT, the team family) — the stat is
  missing. All four call sites (melee 6719, thorns 6704 + 19648, projectile
  19722) route through it, so ONE site restores every path.
- Note: the reference's takeKillScore pays MOBS_KILLED in the else-arm; the
  engine pays that row in `onMobDied` (19802) — equivalent shape, no fix
  needed.

### A2. entityKilledBy.<X> never lands (the victim's row)
- Reference: `ServerPlayerEntity.die` lines 391-399 — the last attacker walk:
  `getLastAttacker()` → when it is a registered spawn-egg type →
  `incrementStat(entityKilledByStat)` on the VICTIM.
- Engine: `killedByEntityOf` exists in the vocabulary (StatCriteria registers
  the criterion) but NO increment site exists. The engine has no
  last-attacker model on players; the mob→player damage sites
  (`onMobAttackedPlayer` 19946, the guardian laser 20915/20921, the mob-shot
  projectile walks) have the mob in hand at damage time.
- Shape decision to verify 3x: a light `lastMobAttacker` record on the
  session, set at every mob-damages-player site, consulted (and cleared) at
  `dieOnTick`. The reference's own model is the damage tracker's
  initial-attacker; the engine's equivalent behavior for the stat's purpose
  (which mob killed the player) is the last mob attacker. PvP deaths pay
  nothing (players are not in the spawn-egg band — Entities.java 226-287).

### A3. JUMPS never lands
- Reference: `PlayerEntity.jump` line 1278 — every jump() call pays
  `incrementStat(JUMPS)` BEFORE the fatigue; NO gamemode gate (creative
  players' jump counts jump on the vanilla statistics screen).
- Engine: the pass-10 exhaustion walk detects the jump (the body-delta
  ground→rising arm, EngineServer ~5502) but pays only the 0.2/0.8 fatigue;
  the walk's creative/spectator early return would swallow the stat for
  creative players.

### A4. The unmounted movement stats never land (8 rows)
- Reference: `PlayerEntity.tickNonRidingMovementRelatedStats` lines 1310-1350
  — the STAT walk is the same arms as the exhaustion walk but pays rows:
  - submerged: CM_DIVEN += round(sqrt(x²+y²+z²)·100)  [3-D norm]
  - in water: CM_SWUM += round(sqrt(x²+z²)·100)
  - climbing: CM_CLIMB += round(y·100) when y > 0
  - on ground: CM_WALKED += round(sqrt(x²+z²)·100); sprinting adds
    SPRINT_ONE_CM (same k); else sneaking adds CROUCH_ONE_CM (same k)
  - airborne: CM_FLOWN += round(sqrt(x²+z²)·100) when l > 25 (STRICT)
  - NO gamemode gate — the walk runs for creative players too (it is the
    stats half; the fatigue half is what creative refuses).
- Engine: `tickMovementExhaustion` (5447) implements the fatigue half alone;
  the rows are absent. Also CM_FALLEN (takeFallDamage lines 1376-1384:
  distance >= 2.0 && !abilities.canFly → round(distance·100)) has no site —
  the engine's fall-damage walk never pays it.

### A5. The riding stats: only CM_MINECART exists
- Reference: `PlayerEntity.rideTick` → `tickRidingRelatedStats` lines
  1352-1373 — the rider's own position delta across the vehicle's move, the
  **3-D norm** `sqrt(x²+y²+z²)·100`, i > 0, over FOUR vehicle families:
  MinecartEntity → CM_MINECART (+ the kilometer achievement),
  BoatEntity → CM_SAILED, PigEntity → CM_PIG, HorseBaseEntity → CM_HORSE.
  No gamemode gate.
- Engine: the vehicle walk (17130-17173) captures `beforeSeat` and pays
  CM_MINECART for kind != BOAT — but with a HORIZONTAL-ONLY norm (rideDx,
  rideDz; rideDy ignored) and the boat arm pays NOTHING. The mob-rider walk
  (`tickMobRiders` 17231) moves the pig/horse rider with no stats at all.
- Verified-1:1 already: the minecartTravelStart clear-on-dismount (reference
  PlayerEntity.tick 279-281; engine 17004), the kilometer squared-1,000,000
  law, the minecart-family-whatever-cargo law.

### A6. The fishing stats never land
- Reference: `FishingBobberEntity.getResult` lines 491-513 — the ROLL pays:
  junk band → JUNK_FISHED, treasure band → TREASURE_FISHED, else
  FISH_CAUGHT. The stat fires at the roll itself.
- Engine: `FishingSystem.retrieve` (502-556) classifies
  (LootRoll.category — JUNK/TREASURE/FISH all modeled) and drops through
  `CatchSink.dropCatch`, which carries no category and no stat. The sink seam
  plus `sessionByEngineId` can pay the row at the roll.

### A7. The villager pair never lands
- Reference: `VillagerEntity.interactMob` line 405 — TALKED_TO_VILLAGER on
  the trade-window interact arm (alive, no customer, not baby, not
  spawn-egg); `TradeResultSlot.onItemRemoved` line 58 — TRADED_WITH_VILLAGER
  when a taken result carries a valid offer + payment.
- Engine: `interactVillagerOnTick` (15440) opens the window, pays no stat;
  `clickVillagerWindowOnTick` (15652) executes the trade, pays no stat.

### A8. BANNER_CLEANED — honest tail (no banner item exists in the registry;
the reference's banner wash arm needs the banner family first — same tail as
pass 9).

## B. The wrong-gate / wrong-shape increments

### B1. The mined-block stat + the 0.025 fatigue lack the harvest gate
- Reference: `ServerPlayerInteractionManager.tryMineBlock` lines 185-229 —
  the creative arm (line 211) NEVER calls afterMinedByPlayer (no stat, no
  fatigue, no drops — creative drops nothing); the survival arm calls it at
  line 223-225 only when `flag1 && flag` — flag1 = the block actually
  removed, flag = `player.canMineBlock(block)` = the TOOL HARVEST gate
  (PlayerInventory 458-465: material.isToolNotRequired() OR the held tool's
  own gate — bare-hand stone pays NOTHING).
- Engine: `BlockInteractionService.tryMineBlock` (787-821) gates the stat +
  fatigue on survival/adventure alone — the harvest gate is missing. A
  bare-handed stone break pays the stat and the 0.025 where the reference
  pays neither. The engine's `BlockBehaviorTable.canHarvest` (consulted by
  the drop walk and the mining-speed walk) is the right gate.
- **The pass-10 tail note is corrected by this finding**: the recorded claim
  ("the engine counts BLOCKS_MINED only for survival/adventure where the
  reference's increment carries no gamemode gate at the site") misread the
  caller — the site carries no gate but the CALLER carries both the creative
  gate (matching the engine) and the harvest gate (missing in the engine).

### B2. MINUTES_PLAYED stalls while dead
- Reference: `PlayerEntity.tick` lines 283-289 — `incrementStat
  (MINUTES_PLAYED)` is UNGATED (the dead body on the respawn screen still
  ticks the play clock); only TIME_SINCE_DEATH carries the `isAlive()` gate.
- Engine: the player loop (5245-5246) skips `session.dead()` before both
  clocks — a dead body's play clock stalls.
- The leavingTheEnd skip is correct (Slice 64 — the credits body is removed
  from the world before the tick).

### B3. ITEMS_BROKEN pays at exactly 2 of ~11 break paths
- Reference: `ItemStack.takeDamageAndBreak` lines 221-243 — ONE site pays
  `ITEMS_BROKEN[item]` when the stack passes max damage, and every wear
  caller routes through it: ToolItem.attack (2) + ToolItem.mineBlock (1),
  SwordItem.attack (1) + SwordItem.mineBlock (2), ShearsItem.mineBlock (1),
  BowItem (1), FlintAndSteelItem (1), HoeItem (1), FishingRodItem (the
  result), CarrotOnAStickItem (7), the armor rows (PlayerInventory 492,
  LivingEntity 650), ThornsEnchantment (3/1), TntBlock ignite (1),
  SheepEntity/MooshroomEntity shear (1), PlayerControlGoal (1),
  CreeperEntity shear (1).
- Engine: `PlayerInventory.damageHeld` breaks the stack silently; only the
  two MELEE sites pay breakItemOf by manual detection (6484, 6636 — the
  mob path at 6477 pays nothing, the PvP path at 6630 pays). The mining
  (974), shears sheep/mooshroom (15009, 15047), bow (7712), flint-on-TNT
  (12463), flint-on-block (12489), hoe (13000), fishing rod (17856) paths
  never pay. `wearArmor`/`wearArmorStack` (504/543) never pay either.
- Fix shape: the stats-carrying overload of damageHeld (one site, every
  caller — the reference's own shape), plus the armor-wear seam.

### B4. ITEMS_USED never pays for the mining arm
- Reference: `ItemStack.mineBlock` lines 252-257 — `flag =
  item.mineBlock(...)` → TRUE for every ToolItem/SwordItem break (the return
  sits OUTSIDE the hardness gate — even hardness-0 blocks pay the use stat;
  only the WEAR is hardness-gated), TRUE for the shears band
  (ShearsItem 18-29), FALSE for bare hands and non-tools (Item 145-147) →
  `incrementStat(ITEMS_USED[tool])` per mined block. The consult lives in
  the NON-creative arm of tryMineBlock (lines 214-221) — creative pays no
  use stat on mining.
- Engine: `wearHeldTool` (946-978) wears the tool but never pays the use
  stat.

### B5. ITEMS_USED never pays for the melee arm
- Reference: `ItemStack.attack` lines 245-250 — SwordItem.attack (47-50) and
  ToolItem.attack (34-37) return TRUE (SaddleItem too — engine carries no
  saddle-attack arm), else FALSE → a landed melee hit with sword/tool pays
  ITEMS_USED[sword/tool]; bare hands never. Consulted INSIDE the landed-hit
  verdict (PlayerEntity.attack lines 1029-1034), so it pays for mob and
  player targets alike, once per landed hit.
- Engine: both melee paths (mob 6340s, player 6504+) never pay it.

### B6. DROPS misses the outside-window click
- Reference: `InventoryMenu` lines 178-192 — the slot -999 click with
  (action 0||1) && (button 0||1) drops the cursor through
  `player.dropItem(stack, TRUE)` / `dropItem(stack.split(1), TRUE)` —
  thrownByPlayer=true pays DROPS (PlayerEntity.dropItem 643-645).
- Engine: the outside-click arm (8313-8338) throws via throwFromPlayer and
  pays nothing; only the Q-drop (dropHeld 8222) pays.
- Verified-1:1: the Q-arm's stat, the death drops / container spills /
  bottle returns riding the no-stat two-arg form (pass 9's own law).

## C. Verified 1:1 already (no fix — recorded so the next audit trusts them)

- The counter model (increment/set/takeDirty/markAllDirty/takeAchievements/
  progress), the achievement parent gate, the time-since-death pair
  (tick + the death clear), DEATHS, DAMAGE_TAKEN (round·10, the != 0 gate),
  DAMAGE_DEALT at both wound sites + the overkill f >= 18 pre-armor check,
  MOBS_KILLED + KILL_ENEMY (the hostile gate) + killEntity.<X> over the
  spawn-egg band (entityKeyOf's golem/dragon/wither exclusions),
  ANIMALS_BRED, GAMES_LEFT at disconnect, ITEMS_CRAFTED with the count at
  both craft sites + the craft achievement ladder, the per-item use stats
  that exist (milk, both books, empty map, ender eye, the potion family,
  the bucket family), the interaction family (chests + trapped + ender,
  crafting table, furnace, brewing, beacon, hopper/dispenser/dropper
  inspect, note blocks both, records, cake slices, flowers potted, items
  enchanted), the cauldron pair + ARMOR_CLEANED (passes 8-9), the cart
  kilometer law + the travel-start reset, and the creative never-mines gate
  itself (B1's finding narrows, not widens, the old tail).

## D. Fix plan (one family pass, every law separately seated)

1. B1: the harvest-gate consult on the mined stat + the 0.025.
2. B2: the dead body's play clock.
3. A1+A2: the kill-credit pair (PLAYERS_KILLED in takeKillScore; the
   victim's entityKilledBy via the light last-mob-attacker record).
4. A3+A4: the movement-stat walk (stats ungated, fatigue keeps its gates) —
   CM_WALKED/CROUCH/SPRINT/SWUM/DIVEN/CLIMB/FLOWN + JUMPS + CM_FALLEN.
5. A5: the riding walk — the 3-D norm, the boat arm, the pig/horse arms.
6. A6: the fishing trio at the roll.
7. A7: the villager pair.
8. B3: the break-stat at every wear path (the damageHeld stats overload +
   the armor seam; retire the two manual melee detections).
9. B4+B5: the use-stat for the mining arm and the melee arm.
10. B6: the outside-click DROPS.

Every restoration cites its anchor; StatsParityTest seats each law.

---

## E. The three verification record (the discipline the pass owes)

1. **Discovery** (verification 1): every finding above read from the
   reference with file + line anchors, matched against the engine's site,
   and recorded here BEFORE any code changed.
2. **Anchor re-derivation** (verification 2): the critical anchors re-read
   directly — the tryMineBlock flag1&&flag gate (B1), the takeKillScore
   instanceof arm (A1), ServerPlayerEntity.die's last-attacker walk (A2),
   the movement walk's branch order and sprint/crouch interplay (A4), the
   rideTick 3-D norm (A5), getResult's stat position (A6), the villager
   pair (A7), the takeDamageAndBreak caller census (B3), the
   attack/mineBlock flag semantics incl. B4's creative-only-else-arm
   subtlety (B4/B5), and InventoryMenu's dropItem(stack, TRUE) pair (B6).
3. **The seat suite** (verification 3): StatsParityTest 15/0 — every
   restored law pinned with its anchor cited — plus the regression
   batches: the stat package 32/0 (including the cobweb-row fold fixing
   the pre-existing StatsCompleteness failure), BodySurvival 10/0 +
   HungerExhaustion 15/0 + Food 6/0 + Knockback 7/0 + Crit 3/0 +
   SurvivalMining 10/0, EmptyShiftClick 8/0 + WindowClickRejection 4/0 +
   VillagerTrade 7/0 + FishingApi 7/0, the entity package whole 574/0,
   interaction+player+item 89/0, the protocol module's wire batch
   (MobIntegration's combined-run flake green in isolation — the
   documented load class), ToolWear 6/0 + MountSeats + the vehicle
   package + Milk 12/0 + Cauldron 7/0 + CauldronAcceptance 4/0 (88/0
   combined), and bukkit 52/0 + launcher 53/0 + api 19/0.

**The bonus in-family find** (B7, fixed beside the audit): the cobweb's
ITEM row — the 1.8.8 registry names item id 30 "web", and the use-row
vocabulary answered a `stat.useItem.minecraft.cobweb` row the 1.8.8
statistics screen never resolves (the VANILLA_NAMES fold lacked the
cobweb→web row the block map already carried). StatsCompletenessTest's
pre-existing failure (verified failing on the BASE commit too) surfaced
it; the fold restores the row the client reads.
