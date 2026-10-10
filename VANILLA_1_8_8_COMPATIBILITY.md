# Vanilla 1.8.8 Compatibility Ledger

Tracks the behavior-level port of Minecraft Java Edition 1.8.8 vanilla
mechanics into Torch. The reference tree is `reference/1.8.8/net/minecraft/`
(1634 decompiled classes, in-repo). Every mechanic records its reference
sources, target locations, tests, and status.

Statuses: `NOT INVESTIGATED` / `INVESTIGATED` / `IN PROGRESS` /
`IMPLEMENTED` / `VERIFIED` / `BLOCKED`.

`IMPLEMENTED` = code exists. `VERIFIED` = the behavior passed meaningful
compatibility tests. Subsystem rows are never marked `VERIFIED` wholesale;
coverage is tracked per mechanic.

---

## 1. Crafting and containers

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| Shaped matching (offset scan, mirror-first, virtual 3x3) | `crafting/ShapedRecipes.java`, `crafting/CraftingManager.java` | `zamin-core .../server/crafting/CraftingRecipe.java` | crafting suite + real client | VERIFIED | — |
| Shapeless matching (multiset) | `crafting/ShapelessRecipes.java` | same | crafting suite | VERIFIED | — |
| Recipe order (shaped first, larger footprint first) | `CraftingManager.java` | same | crafting suite | VERIFIED | — |
| Workbench Open Window size byte 0 | `ContainerWorkbench` slot layout | protocol layer `sendCraftingWindow` | real client (9-row bug fixed) | VERIFIED | — |
| Right-click half-take, craft-all materials limit, close-drop | `ContainerWorkbench.onTake/click/close` | `EngineServer` crafting window | crafting integration | VERIFIED | — |
| Chest container (27 slots, click semantics) | `ContainerChest`, `InventoryLargeChest` | `EngineServer` chest window, `ChestBlockEntity` | chest tests | IMPLEMENTED | click paths predate the vanilla-port policy (hand-written, covered by acceptance tests) |
| Furnace (smelt economy, XP payout) | `TileEntityFurnace.java` | `zamin-core .../server/furnace/` | furnace acceptance + restart + xp | IMPLEMENTED | burn/XP math hand-ported earlier; re-check against reference pending |
| Horse inventory window (saddle + armor slots) | `inventory/menu/HorseMenu.java` | `EngineServer` horse window, `PlayerSession.openHorseWindow` | `HorseIntegrationTest` (real wire) | VERIFIED | Open Window now carries the vanilla full-menu count (38) — the bare window changed from the tolerant 2 |
| Donkey/mule chest (15-slot 3x5 grid, equip, spill) | `HorseBaseEntity` (chest arms), `HorseMenu`, `AnimalInventory` | `MobEntity` chest state, `EngineServer` chest clicks (shared `WindowClicks`), protocol 53-slot window | `DonkeyChestIntegrationTest` (real wire: 53-slot open + grid store) + `BreedingAndDonkeyChestTest` (spill order) | VERIFIED | shift-click into/out of the grid refused (resync restores) — the vanilla quickMove arm not yet ported |
| Villager trading (7-use stock, MC|TrList) | `EntityVillager.java`, `MerchantRecipeList.java` | `VillagerTrades.java`, `EngineServer` trade windows | `VillagerTradeIntegrationTest` | VERIFIED | offer table is the fixed career set (no unlock progression) |

## 2. Mining and block interaction

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| Break progress math (tier, efficiency, /5 water, /5 air, /30 vs /100) | `ServerPlayerInteractionManager.java`, `PlayerEntity.getMiningSpeed`, `Block.getMiningSpeed` | `BlockInteractionService` | `VanillaMiningTimingTest` + `EnchantmentEffectAcceptanceTest.efficiencyVDigReadsTheHeldStack` | VERIFIED | the Efficiency level and the helmet's Aqua Affinity now read the real held/armor stacks (`EnchantmentHelper.efficiencyLevel` / `highestLevel`); the timing tests keep pinning the level-0 math |
| Tick-clock accumulation, finish at f >= 0.7, wasMining self-complete | `ServerPlayerInteractionManager.updateBlockRemoving` | same | same | VERIFIED | — |
| Crack-stage broadcasts (0x28, stage (int)(f*10)) | same + `World.setBlockState` dust | protocol layer | integration | VERIFIED | — |
| Block drops, tool effectiveness, harvest gates | `Block.getDrops`, tool classes | `BlockBehaviorTable` | block behavior tests | IMPLEMENTED | pre-ledger implementation; spot-audited against reference |
| Scheduled ticks, neighbor updates (2-tick falling fuse) | `Block.update`, `World.scheduleUpdate` | `BlockUpdateSystem` | `BlockUpdateSystemTest` | IMPLEMENTED | fuse ported with falling blocks (VERIFIED there); general neighbor ordering not exhaustively audited |
| Fluids (water/lava spread) | `BlockLiquid.java`, `BlockDynamicLiquid.java` | `FluidSystem`, `FluidBlocks` | cane/cactus acceptance | IMPLEMENTED | spread order audited loosely; full tick-by-tick comparison pending |
| Fire spread and burnout | `BlockFire.java` | fire paths in `BlockUpdateSystem` | `FireAcceptanceTest` | IMPLEMENTED | — |

## 3. Entity movement and physics

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| Falling blocks (gravity -0.04, move-then-drag 0.98, two-tick fuse, landing gate, slab quirk, 600-tick lifetime) | `entity/FallingBlockEntity.java`, `block/FallingBlock.java` | `FallingBlockEntity(Manager)`, `BlockUpdateSystem` fall queue | `FallingBlockEntityTest`, `BlockUpdateSystemTest`, `CollisionShapeTest`, `FallingBlockIntegrationTest` | VERIFIED | no splash particles entering water (cosmetic) |
| Knockback (half-then-impulse, 1e-4 jitter, 0.4 rise cap, server-motion residual) | `LivingEntity.applyKnockback` + takeDamage callers | `MobEntity.knockbackFrom`, `PlayerSession` motion residual | `KnockbackAndExhaustionTest` | VERIFIED | no knockback-resistance attribute (rolls at 0) |
| Player movement friction/acceleration | `LivingEntity.travel`, `EntityPlayerSP`-server gates | player session body | body survival acceptance | IMPLEMENTED | pre-ledger approximation, close to vanilla constants; full tick-by-tick diff pending |
| Swimming/fluid drag for players | `LivingEntity.travel` fluid arms | player body fluid path | body survival acceptance | IMPLEMENTED | single fluid model (no flowing/still distinction for drag) |
| Fall damage | `LivingEntity.fall` | player + mount fall paths | body survival acceptance | IMPLEMENTED | — |
| Collision (shapes, step-up, slab/stairs) | `Block.getCollisionShape` + movement solver | `WorldSolidity`, collision shapes | `CollisionShapeTest`, `SlabStairsAcceptanceTest` | IMPLEMENTED | — |

## 4. Food, exhaustion, damage

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| FoodStats (strict > 4.0, 40.0 cap, peaceful arm, difficulty-exact starvation) | `FoodStats.java` | player session food economy | `KnockbackAndExhaustionTest` | VERIFIED | — |
| Exhaustion ledger (sprint 0.099999994/m, walk 0.01/m, swim 0.015/m, jump 0.2/0.8, damage 0.3/bypass 0.0, break 0.025) | `PlayerEntity.addFatigue` sites | per-meter displacement ledger | same | VERIFIED | naturalRegeneration gamerule not yet a config surface (default matches) |
| Damage pipeline (armor, resistance, i-frames) | `LivingEntity.takeDamage` family | player/mob damage paths | combat acceptance | IMPLEMENTED | enchantment modifiers (Protection etc.) land with enchanting |
| Eat path (nutrition + pre-doubled saturation) | `ItemFood.onItemRightClick`, `FoodStats.add` | `Foods`, eat path | player tests | VERIFIED | golden apple's Regeneration II + Absorption effects need a potion system (registered as plain food) |

## 5. Mob AI and pathfinding

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| A* search (BinaryHeap, g-cost cap range*2, closest-node fallback) | `entity/ai/pathing/PathFinder.java`, `BinaryHeap.java`, `PathNode.java` | `.../entity/ai/pathing/PathFinder` + heap + node | `PathfindingTest`, `BinaryHeapTest` | VERIFIED | — |
| Walk node evaluation (cardinal successors, blocking types, climb, safe-fall-3) | `WalkNodeEvaluator.java` | `WalkNodeEvaluator` port | same | VERIFIED | trapdoor arm dormant (no trapdoor blocks yet); door arms unreachable |
| Navigation (waypoint band, DDA shortcut, 100-tick stuck check) | `PathNavigate.java`, `GroundPathNavigation.java` | `MobNavigation` | same | VERIFIED | — |
| Chase/panic/stroll goal set (engine baseline) | community architecture + vanilla shape | `MobGoals`, `GoalSelector` | mob tests | IMPLEMENTED | vanilla's full 52-goal set grows per mechanic (breed lands in Slice 6) |
| Animal breeding (love 600, proximity 60, cooldown 6000, child -24000, XP 1-7) | `AnimalEntity.java`, `AnimalBreedGoal.java`, `PassiveEntity.java` | `MobEntity` breeding state, `MobManager.tickBreedingFor` + deferred breed, breed goal (priority 2) | `BreedingAndDonkeyChestTest` (13: love window, hit-clears-love, off-age clear, baby-feed growth, age walk, breed landing, mule sterility, chest spill) + `DonkeyChestIntegrationTest.wheatOnACowBurstsHeartsOverTheWire` (event 18 on the real wire) | VERIFIED | ambient single-heart particles every 10 ticks await a verified heart id in the particle data (the clock runs, the emission is dropped); the breed celebration uses the same event-18 channel |
| Horse breeding rules (tamed, full health, no rider, type combos, mule ban) | `HorseBaseEntity.canBreed/canBreedWith/makeChild` | `MobEntity.canBreedWith`, `MobManager.breed` + `childSubtypeOf` | `BreedingAndDonkeyChestTest` (tame gate, full-health gate with hit-clears-love, 0x1 mule cross, donkey pass-through) | VERIFIED | variant dice + child stat averaging not yet ported (baby stats default to the kind's baseline) |
| Animal feeding tables (breeding items per kind; horse heal/grow/temper/love) | `AnimalEntity.isBreedingItem/interactMob`, `EntityPig/Chicken overrides`, `HorseBaseEntity.interactMob` food table | `EngineServer.feedAnimal/breedingItemOf/horseFood*`, pig carrot arm, chest equip arm | `BreedingAndDonkeyChestTest` (baby-feed tenth), `HorseIntegrationTest` (the wheat temper ladder still drives taming), food tables pinned in switch tables | IMPLEMENTED | the golden pair's full heal/grow/love arms implemented; the untamed bare-hand arm (vanilla angry sound instead of a mount attempt) intentionally kept on the engine's verified taming flow — divergence logged below |
| Baby growth on the wire (index-12 age byte, grew-up delta) | `PassiveEntity.registerSyncedData/setBreedingAge` | protocol `sendMobSpawn` age byte + `sendMobAgeMetadata`, listener `onMobGrewUp` | `DataWatcherParityIntegrationTest` (index 12 present, byte type) | VERIFIED | the server-side hitbox stays full-size (the vanilla 0.5 scale halves it) — the client renders the child scale from the byte |
| Lead (leash knot, break distance, fence anchor) | `EntityLead.java`, `BlockLeash.java` | — | — | NOT INVESTIGATED | — |
| Despawn rules | `EntityLiving.canDespawn` + hostile checks | `MobManager` range despawn | mob tests | IMPLEMENTED | simplified to range-only (vanilla adds 30s no-player timer for some kinds) |

## 6. Combat and projectiles

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| Melee damage, weapon tiers, crits, sprint knockback | `PlayerEntity.attack`, `ItemStack.attack` | `EngineServer` attack paths | `MobCombatAcceptanceTest` + `EnchantmentEffectAcceptanceTest` (sharpness/smite one-shot numbers, the fire-aspect re-arm + burn kill, the protection band) + `CritParityAcceptanceTest` (the falling 1.5x one-shot, the exact PvP 1.5 fist band, the Animation 4/5 bursts) | IMPLEMENTED | the enchantment arms of the reference attack walk landed (modifyDamage by mob category, the Knockback extra on the attacker's look yaw with the 60% decay + sprint wipe, the Fire Aspect pre-set/post-set/extinguish-on-refusal); the vanilla crit landed (the fall 1.5x multiply rides the BASE damage before the enchantment family joins, the reference's flag gate: fallDistance>0, airborne, not climbing, not in water, not blind, unmounted, living target — the blindness arm reads false until the potion slice exists) with the landed-hit bursts broadcast as Animation 0x0B codes 4/5 over the EntityAnimationObserver (the reference's addCritParticles/addEnchantedCritParticles audience); the overkill stat (f >= 18) has no stat surface yet |
| Bow (charge, damage curve, spread) | `ItemBow.java`, `EntityArrow.java` | projectile package | `RangedCombatAcceptanceTest` | IMPLEMENTED | — |
| Projectiles (gravity 0.05, drag 0.99, hit rules) | `EntityArrow.java`, `EntityThrowable.java` | projectile package | ranged tests + `RangedCombatAcceptanceTest.theThrownShardSpawnsBehindTheEyeLikeTheReference` (the exact 0.16 lateral read) | IMPLEMENTED | the living-thrower spawn pull-back landed (ThrownEntity lines 58-62 / ArrowEntity lines 89-93: x -= cos(yaw)*0.16, z -= sin(yaw)*0.16, y -= 0.1 — the swapped-trig legacy, exactly perpendicular to the throw direction), so thrown bodies no longer lean on the thrower-immunity tick window; the skeleton's target-aimed constructor (shooter, target, speed) spawns 1 unit toward the target without the pull-back — ours still spawns at the eye aimed at the torso, the skeleton divergence/damage math lands with the bow family |
| Armor mitigation (points, toughness-era absent in 1.8) | `LivingEntity.applyArmor` | armor paths | armor tests | IMPLEMENTED | — |
| Enchantments (registry, offer math, item storage) | `enchantment/Enchantment.java` (25 ids, weights, curves), `EnchantmentHelper.java` (buildEnchantmentList, getRequiredXpLevel, WeightedPicker), per-family subclasses | `server/enchantment/Enchantments` (registry + categories + enchantability), `server/enchantment/EnchantmentHelper` (ladders, offers, addRandomEnchantment, damage math), `ItemStack.enchantments` (the tag.ench storage) + `SlotNbt` ench wire encoding | `EnchantmentTest` (11) + `EnchantingMenuTest` (12 menu-state pins) + `EnchantingTableIntegrationTest` (the real-wire walk) — details: registry count/weights/caps, exact XP curves, compatibility conflicts, enchantability tiers incl. the shears' 0, seeded ladder rolls, window pools, book category, seeded offer determinism + pairwise compatibility, stack landing + merge identity, damage tables) + `EnchantingMenuTest` (menu state: cost ladder + clue picks, lapis gate, button gates incl. creative + the null-offer still-pays quirk, book conversion, recompute ordering, close-drop) | IMPLEMENTED | the menu state AND the live window are landed (`server/enchantment/EnchantingMenu` + the EngineServer open/click/enchant routing + the adapter's Open Window/property/view syncs); VERIFIED on the real wire (EnchantingTableIntegrationTest: open with the vanilla size byte + 38 slots + 7 properties, the gated clicks, the cost diffs at power 6, the enchant paying slot+1 levels and lapis with the XP resync, the close dropping both slots); the effect hooks landed (`EnchantmentEffectMathTest` pins the exact reference tables; `EnchantmentEffectAcceptanceTest` wires them over the live engine): protection (the per-piece (6+lvl^2)/3 curve scaled per kind, the 0..25 clamp + the half-to-full roll, the 20-cap and the /25 envelope) on every player damage entry with the reference DamageKind flags (melee/projectile/explosion/in-fire/on-fire/fall/drown/cactus; starve skips as the unblockable, void contributes 0), the blast shave (floor(dmg*lvl*0.15)) before the explosion pipeline, the fire-timer shortening on the ignite sites, efficiency + aqua affinity in the dig, the damage family by mob category (zombie/skeleton undead, spider arthropods), the Knockback extra + the 60% attacker decay + the sprint wipe, the Fire Aspect pre-set/level*4 re-arm/extinguish-on-refusal; the thorns wildcard landed (the 15%-per-level independent per-piece roll, the 1-4/level-10 damage table through the retaliated body's own armor+protection walk, the damage.thorns 0.5F/1.0F sound, the wear 3/1 riding the FIRST thorns stack via getEquipmentWithEnchantment, all three reference sites: the PvP landing, the mob-melee landing with the row captured before the damage walk so a killing blow still retaliates, and the arrow hit with the shooter resolved through the player then mob band; `ThornsAcceptanceTest` — the PvP bite with the helmet-vs-chest wear split, the absorbed swing wearing nothing, the zombie arm, the arrow arm with the re-draw loop, the math pins); the bow family landed (the BowItem (f*f + f*2)/3 charge curve with the 0.1 flick gate and the 1.0 full-draw clamp setting the crit flag, Infinity, Power level*0.5+0.5, Punch, Flame's whole-flight burn + the index-0 flags byte at spawn, the caller-recipe launch sounds; `BowFamilyTest` + the ranged walks; the pickup=2 retrieval surface ledgered with its slice); the loot family landed (Slice 7f: silk touch, fortune, looting); **Unbreaking landed (Slice 7g: the takeDamage per-unit reduction walk with the reference's own branch order — the armor's 60% early-false gate eats the roll without consuming the int, the level reads once per walk, every wear surface rides it (the six damageHeld sites, the wearArmor walk now carrying the reference damageArmor /4 min-1 (int) scaling — replacing the flat 1-unit wear, so heavy hits wear proportionally like the reference — and the thorns targeted visit; a fully-rolled-away visit touches nothing); `UnbreakingAcceptanceTest` with ForcedRandom gate pins + the 20k-roll bands; remaining hook gaps: respiration/depth strider; mobs carry no equipment so mob-side reads are structurally zero (the bare-vanilla-mob read); the second-pick roll mapping uses id order where the reference walks Java-8 HashMap buckets (pool membership exact, seed-exact pick order not a compatibility target)** |

## 7. Player systems

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| Inventory + container clicks | `Container.java`, `PlayerInventory.java` | `PlayerSession` inventory, window click routers | inventory tests | IMPLEMENTED | click matrix hand-written pre-ledger; audited against real clients |
| XP orbs (pickup box, burst splits) | `ExperienceOrbEntity.java` | `ExperienceOrbManager` | xp tests | IMPLEMENTED | — |
| Sleep/beds, respawn | `PlayerEntity` sleep family | sleep paths | `SleepAcceptanceTest` | IMPLEMENTED | — |
| Hunger visual (food ticks to client) | `FoodStats` sync | protocol metadata | player tests | IMPLEMENTED | — |

## 8. World and generation

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| Terrain (seeded noise, strata, ores, trees, cane/cactus) | `world/gen/*` reference tree | `.../server/world/` generator | `WorldModelTest` | IMPLEMENTED | seeded and stable, but not yet diffed tick-by-tick against reference output for identical seeds |
| Chunk lifecycle (load, border, respawn re-anchor) | `Chunk.java`, `PlayerChunkMap.java` | chunk services | persistence tests | IMPLEMENTED | — |
| Block placement rules (collision-less inside entities) | `ItemBlock.canPlace` body checks | placement gates | protocol batch two | VERIFIED | — |
| Weather, day cycle | `WorldServer` weather/tick paths | time ticker | world tests | IMPLEMENTED | weather cycle simplified (rain commands only) |

## 9. Protocol and sync (1.8.8 / protocol 47)

| Mechanic | Reference | Target | Tests | Status | Known differences |
|---|---|---|---|---|---|
| Teleport tails (relative-flag byte, absolute anchors, tp-x2) | `ServerPlayNetHandler` movement | protocol layer | real-client batch two | VERIFIED | — |
| Spawn metadata (living flags, villager profession, horse subtype/flags) | `Entity` DataWatcher tables | `V18Connection` metadata builders | real client | VERIFIED | — |
| Chunk delivery, respawn re-anchor | `PlayerChunkMap` | chunk streaming | real-client batches | VERIFIED | — |
| Window system (crafting size byte 0, EntityHorse mount id, villager plugin message) | container open paths | protocol windows | real client + integration | VERIFIED | — |
| Entity event 18 (love hearts burst) | `AnimalEntity.lovePlayer` | `MobEntity.enterLove` pending burst → `onMobLoveBurst` → `sendMobStatus(mob, 18)` | `DonkeyChestIntegrationTest.wheatOnACowBurstsHeartsOverTheWire` (real wire) | VERIFIED | ambient heart particles every 10 ticks pending the heart id in community particle data; `ENTITY_STATUS_HURT/DEAD` constants were found missing from `Protocol18` (stale build masked it) and are restored |

## 10. Not yet investigated

- Redstone (wire, repeaters, comparators, pistons, observers-era absent) — the whole subsystem is untouched (`NOT INVESTIGATED`).
- Potions, brewing, splash effects (`NOT INVESTIGATED`).
- Enchanting: the registry + offer math + item NBT storage + the table's live window landed (`IMPLEMENTED`, the window walk `VERIFIED` on the wire) and the effect hooks are wired (protection/efficiency/knockback/fire aspect/damage family + the fire-timer and blast shaves, `IMPLEMENTED` with exact-table unit pins and live-engine acceptance); the remaining hooks: respiration/depth strider land with their gameplay slices; thorns, the bow family, the loot family and unbreaking landed (Slices 7d-7g).
- Nether portals (Slice 8 target, `NOT INVESTIGATED`).
- Leads (deferred from Slice 6, `NOT INVESTIGATED`).
- Structures (villages, mineshafts, strongholds) beyond terrain decoration (`NOT INVESTIGATED`).
- Spawning (natural mob cycles with pack rules) — spawn commands exist, natural cycles simplified (`IMPLEMENTED` partial, simplified).

---

## Ledger policy

- One row per mechanic; a subsystem is `VERIFIED` only when its rows are.
- Every slice must update this file in the same commit as the code.
- `IMPLEMENTED` rows that predate the ledger (hand-written implementations
  with acceptance coverage) carry an explicit re-audit note; they graduate to
  `VERIFIED` only after a reference-driven audit.
