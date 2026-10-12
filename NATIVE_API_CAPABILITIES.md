# Torch Native API Capability Ledger

The companion of `VANILLA_1_8_8_COMPATIBILITY.md`. That file tracks how
faithfully a mechanic matches 1.8.8; this file tracks whether the engine's
implemented capabilities are **usable through the public native API**
(`zamin-api`), per `Torch-Arcetecture.txt` §27.

A capability is not "exposed" merely because a public method exists. It must
be usable from a plugin-facing module (`zamin-api` or a documented engine
facade), correctly documented for threading, and covered by tests
(§27.10). Engine-internal listener interfaces count as **internal**, not
exposed.

Statuses: `NOT AUDITED → AUDITED → IN PROGRESS → IMPLEMENTED → VERIFIED / BLOCKED`.

Stability classes (§27.4): `Stable`, `Experimental`, `Internal`.

## 1. Server lifecycle and core

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Server state/lifecycle | `EngineServer` | `Server` (state, worlds, players, awaitShutdown) | any thread for reads | — | Stable | api smoke | VERIFIED | — |
| Dimension collection | `EngineServer.worlds()` | `Server.worlds()` (both booted dimensions) | any thread | — | Stable | `EventApiAcceptanceTest` | VERIFIED | — |
| Difficulty | `EngineServer.difficulty()` | `Server.difficulty()` (the id read; the set arm rides the difficulty slice) | any thread | — | Stable | `WorldApiAcceptanceTest` (the band) | IMPLEMENTED | the setter path (the engine's own `/difficulty` command) rides the difficulty slice |
| TPS observation | `EngineTicker.averageTps` (the 1m/5m/15m Paper windows) | **`Server.tps(int windowSeconds)`** — any thread, best-effort snapshot | any thread | — | Experimental | `SchedulerApiAcceptanceTest` (the sane band) | IMPLEMENTED | 20.0 before enough data accumulates |
| Configuration | `EngineConfig` | none (launcher-side file) | boot | — | — | — | NOT AUDITED | — |

## 2. World, chunk, block

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Block read | `EngineWorld.getBlock` | `World.getBlock` | any thread (epoch read) | — | Stable | api | VERIFIED | — |
| Block write | `EngineWorld.setBlock` | `World.setBlock` | simulation context (asserted) | — | Stable | api | VERIFIED | — |
| Time read | `EngineWorld.timeOfDay/totalTicks` | `World.timeOfDay/totalTicks` | any thread | — | Stable | api | VERIFIED | — |
| Time set | `EngineWorld.setTimeOfDay` (the floorMod wrap) | `World.setTimeOfDay` | owner context (asserted; call through `World.execute` off-lane) | — | Stable | `WorldApiAcceptanceTest` (the noon landing, the 25000 wrap, the per-world independence) | IMPLEMENTED | the clock keeps ticking forward after the set (the reference's own behavior); the set walks ONE world (the engine's `/time` walks every dimension — a plugin loops `worlds()`) |
| Chunk handles | `EngineChunk`, `getOrGenerate`, `peek`, `loadedChunks` | `isChunkLoaded` + `World.biomeAt` (the column's stable name) | any thread | — | Stable | `WorldApiAcceptanceTest` (the family names, the unloaded default) | IMPLEMENTED | the raw chunk content reads stay internal (the block read rides `World.getBlock`); the biome set is generation-only |
| Light levels | `EngineWorld.blockLightAt/skyLightAt` | `World.blockLightAt/skyLightAt` | any thread (best-effort) | — | Stable | `WorldApiAcceptanceTest` (the glowstone source cell at 15, the band) | IMPLEMENTED | the unloaded-chunk answers (0 block / 15 sky) are the engine's own documented totals |
| Block change observation | `WorldChangeListener.onBlockReplaced` (the combined pair, Slice 14a) | the `BlockChangedEvent` public event (§27.7 post-commit: every committed change — player, explosion, piston, fluid, growth) | handlers on the committing owner context | the event bus (any-thread registration) | Stable | `WorldApiAcceptanceTest` (the pair both ways, the world name, the unsubscribe) | IMPLEMENTED | not cancellable by design (the commit already happened — observe-and-react, §27.7); handler exceptions isolate in the bus |
| Scheduled ticks | `BlockUpdateSystem` | none | owner | — | — | — | NOT AUDITED | query/cancel surface |
| World deltas | `WorldDeltaSnapshot` | none | owner | — | — | — | NOT AUDITED | persistence-facing today |
| Block registry | `FrozenBlockRegistry`, `EngineBlockType` | `BlockRegistry` (limited) | any thread | — | Stable | api | AUDITED | lookup by id, the behavior tables |
| Block behavior/metadata tables | `BlockBehaviorTable`, `BlockLightTable` | none | any thread | — | — | — | NOT AUDITED | hardness/diggability reads |

## 3. Entities

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Player identity/state | `PlayerSession` | `Player` (uuid, name, state, world, position, rotation, onGround) | any thread | — | Stable | api | VERIFIED | — |
| Player body | `PlayerSession` health/food/saturation/dead/hurt/heal/addExhaustion | **`Player`** — the body family | reads consistent on owner; mutations owner-context | — | Experimental | `PlayerApiAcceptanceTest` | IMPLEMENTED | the armor-points walk; the breath window |
| Player XP | `totalXp`, `experienceLevel`, enchanting seed | **`Player`** — totalExperience/addExperience/experienceLevel | owner context for mutations | — | Experimental | `PlayerApiAcceptanceTest` | IMPLEMENTED | the enchanting-cost arm |
| Player inventory | `PlayerInventory` (36 slots + held) | **`Player`** — heldItem/heldSlot/countOf/give (the vanilla pickup rules; the give wire-syncs through the engine's own inventory fan-out) | owner context | — | Experimental | `PlayerApiAcceptanceTest` (2: the give/overflow ladder, the sync-hook fire) | IMPLEMENTED | slot-level reads/writes + armor (the container/inventory wave) |
| Player gamemode | `PlayerSession.gamemode/setGamemode` | **`Player.gamemode/setGamemode`** | owner context | — | Experimental | `PlayerApiAcceptanceTest` | IMPLEMENTED | — |
| Player fire | `ignite/extinguish/tickFire` | **`Player`** — burning/ignite/extinguish | owner context | — | Experimental | `PlayerApiAcceptanceTest` | IMPLEMENTED | — |
| Mob registry/spawn | `MobManager.spawnAt/spawnGroup` | `World.entities()` live view; `LivingEntity` handles (spawn still engine-side; the API spawn gate is the event) | owner context (enumerate inside `World.execute`) | `MobSpawnEvent` cancel | Experimental | `EntityApiAcceptanceTest` (4) | IMPLEMENTED | public spawn-with-parameters call (spawn eggs etc.); item-entity/projectile/vehicle handles |
| Mob lifecycle | `MobManager.Listener` (internal) + `LivingEntity.despawn()` | the handle's despawn (routes the manager's removal, reason `"api"`); `MobSpawnEvent` observes arrivals | owner context | spawn observe/cancel; despawn is a handle mutation | Experimental | `EntityApiAcceptanceTest` | IMPLEMENTED | death events |
| Mob state | `MobEntity` (health, velocity, AI, breeding, leash…) | **`LivingEntity`** — the engine object IS the handle (§27.5): position, rotation, velocity, health, persistent, heal/hurt/addVelocity/navigateToward/despawn | simulation context (the documented owner path is `World.execute`) | — | Experimental | `EntityApiAcceptanceTest` (4: live view, owner-context mutation ladder, snapshots, despawn) | IMPLEMENTED | breeding/leash/mount arms (their own waves) |
| Entity snapshots | `MobEntity.snapshot()` | **`EntitySnapshot`** — the immutable any-thread view | capture on owner, consume anywhere | — | Experimental | `EntityApiAcceptanceTest` | IMPLEMENTED | — |
| Mob AI goals | `GoalSelector`, `MobGoals` | none | owner | — | — | — | NOT AUDITED | register/replace (§27.3) |
| Item entities | `ItemEntityManager.spawnDropAtBlock` | none | owner | — | — | — | NOT AUDITED | — |
| Projectiles | `ProjectileManager` | none | owner | — | — | — | NOT AUDITED | — |
| Falling blocks | `FallingBlockEntityManager` | none | owner | — | — | — | NOT AUDITED | — |
| Vehicles/mounts | `MobManager.mountMob/dismountMob/steerRidden` | none | owner | — | — | — | NOT AUDITED | — |
| Leashes | `LeadSystem` | none | owner | — | — | — | NOT AUDITED | — |
| Experience orbs | `ExperienceOrbManager` | none | owner | — | — | — | NOT AUDITED | — |

## 4. Mechanics subsystems

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Block interaction (mine/place) | `BlockInteractionService` | none | submit-any/read-owner | internal listeners | — | — | NOT AUDITED | cancellable hooks |
| Explosion service | `ExplosionService` | none | owner | — | — | — | NOT AUDITED | — |
| Redstone | `RedstoneSystem` | none | owner | — | — | — | NOT AUDITED | — |
| Pistons | `PistonSystem` | none | owner | — | — | — | NOT AUDITED | — |
| Hoppers | `HopperSystem` | none | owner | — | — | — | NOT AUDITED | transfer query/cancel |
| Chests | `ChestManager` | none | owner | — | — | — | NOT AUDITED | — |
| Furnaces | `FurnaceManager` | none | owner | — | — | — | NOT AUDITED | — |
| Crafting | `CraftingService` | none | any thread (pure) | — | — | — | NOT AUDITED | recipe registry read |
| Brewing | `PotionSystem` | none | owner | — | — | — | NOT AUDITED | — |
| Enchanting | `EnchantmentHelper`, enchanting menu | none | owner | — | — | — | NOT AUDITED | — |
| Dispensers | `DispenserSystem` | none | owner | behavior registry | — | — | NOT AUDITED | behavior registration (§27.3) |
| TNT | `TntSystem` | none | owner | internal listeners | — | — | NOT AUDITED | — |
| Spawners | `SpawnerManager` | none | owner | — | — | — | NOT AUDITED | — |
| Fluids | `FluidSystem` | none | owner | — | — | — | NOT AUDITED | — |
| Signs | `SignManager` | none | owner | internal listeners | — | — | NOT AUDITED | — |

## 5. Concurrency and scheduling

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Tick submission | `EngineTicker.submit` | **`World.execute(Runnable)`** — the §27.6 owner path for plugins | submit any thread; run on the owner lane | — | Experimental | `EntityApiAcceptanceTest` (the foreign-thread arms) | IMPLEMENTED | — |
| Scheduler/task controls (delay + repeat + cancel) | `EngineTicker`'s due-time queue (`scheduleTask` + the head-of-tick drain; `EngineScheduler` view) | **`Server.scheduler()`** — `runNextTick` / `runLater(delayTicks)` / `runRepeating(delay, interval)` returning the cancellable `Task` | submit any thread; bodies on the owner context at the head of the due tick, due-then-submission order; a throwing body is logged and skipped, the loop never dies | — | Experimental | `SchedulerApiAcceptanceTest` (5) | IMPLEMENTED | the immediate path keeps its own admission lane (`World.execute`); the cron-calendar (wall-clock schedules) is not part of §27.3's tick-domain family |
| Owner-context execution | `OwnershipDomain`, `SimulationScheduler` | none | owner | — | — | — | NOT AUDITED | §27.6 owner-context API |
| Async compute | `ComputeSubsystem`, `ComputeTicket` | none | any thread → owner | — | — | — | NOT AUDITED | §27.3 safe async computation |
| Cross-owner requests | `CrossOwnerRouter` | none | owner → owner | — | — | — | NOT AUDITED | §27.3 |
| Snapshots | `PlayerSnapshot`, `MobDataStore` | none | owner → any thread | — | — | — | NOT AUDITED | §27.3 snapshot semantics |

## 6. Events (§27.7 observe/cancel)

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Event bus (register/subscribe/fire) | `server/event/EngineEventBus` | `Server.events()` → `net.zaminmc.torch.event.EventBus` | registration any thread; dispatch on the firing owner | priority + registration order, reentrant, exception-isolated | Experimental | `EventBusUnitTest` (9) | IMPLEMENTED | the class-hierarchy dispatch covers `extends`; interface-markers registered directly on the interface type are not discovered |
| Block break observe/cancel | `BlockInteractionService` pre-commit gates (creative + survival) | `event.block.BlockBreakEvent` | handlers on the tick thread | cancel prevents the mutation + drops + wear + XP; engine re-syncs the cell | Experimental | `EventApiAcceptanceTest` (2 arms) | IMPLEMENTED | — |
| Block place observe/cancel | `BlockInteractionService` place paths (creative + survival) | `event.block.BlockPlaceEvent` | same | cancel prevents the mutation and the survival item consumption | Experimental | `EventApiAcceptanceTest` | IMPLEMENTED | — |
| Mob spawn observe/cancel | `MobManager.spawnAt` (the single chokepoint: natural cycles, spawners, structure populators, commands) | `event.entity.MobSpawnEvent` | same | cancel returns null, no id consumed, no listeners notified | Experimental | `EventApiAcceptanceTest` | IMPLEMENTED | projectile/item-entity/vehicle spawns carry no events yet (their handles land with their waves) |
| Player chat observe/cancel | `ChatService.handleOnTick` pre-broadcast | `event.player.PlayerChatEvent` | same | cancel drops the message before any delivery | Experimental | `EventApiAcceptanceTest` (2) | IMPLEMENTED | — |
| Player damage observe/cancel/modify | `EngineServer.damageOnTick` (every environment/starve/thorns/melee/magic family) + `projectileHitPlayerOnTick` + the explosion player arm | `event.player.PlayerDamageEvent` (cancellable, mutable amount, `event.DamageCause`) | handlers on the firing owner | cancel lands nothing (no health loss, no exhaustion, no death); the modified amount drives the walk | Experimental | `LifecycleEventAcceptanceTest` (2) | IMPLEMENTED | the amount is the post-armor, post-enchantment value (the armor math runs at the call sites before the entry) |
| Mob damage observe/cancel/modify | `MobManager.hurt` (melee/projectile/explosion causes) + `hurtWithoutKnockback` (magic) | `event.entity.MobDamageEvent` (cancellable, mutable amount, `event.DamageCause`) | same | cancel answers the refused-takeDamage verdict; the modified amount drives hurt + knockback + death | Experimental | `LifecycleEventAcceptanceTest` (2) | IMPLEMENTED | — |
| Player join/quit observation | the login walk (`joinRequest`) + the disconnect walk (`clientDisconnected`); fires with the vanilla yellow broadcast (1.8.8: the joiner never sees their own line) | `event.player.PlayerJoinEvent` / `PlayerQuitEvent` (mutable broadcast line, null suppresses) | handlers on the connection's own context | not cancellable (§27.7 post-commit) | Experimental | `LifecycleEventAcceptanceTest` (2) | IMPLEMENTED | — |
| Player death observation / keep-inventory | `EngineServer.dieOnTick` — after the body dies, before the scatter | `event.player.PlayerDeathEvent` (cancellable = keep inventory + XP, mutable death line) | tick thread | cancel keeps items/XP on the body; the death still resolves | Experimental | `LifecycleEventAcceptanceTest` (2) | IMPLEMENTED | — |
| Mob death observation | `MobEventDispatch.onMobDied` — the death walk before the XP release | `event.entity.MobDeathEvent` (the live `LivingEntity` handle) | tick thread | not cancellable; the kill-counter observation point | Experimental | `LifecycleEventAcceptanceTest` (1) | IMPLEMENTED | — |
| Explosion events | `ExplosionService` | planned | — | — | — | — | NOT AUDITED | with the TNT/combat wave |

## 7. Registry family

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Block types | `FrozenBlockRegistry` | `BlockRegistry` (partial) | any thread | — | Stable | api | AUDITED | by-id lookup |
| Item types | `BuiltinItems` | `ItemType`/`ItemStack` value types | any thread | — | Stable | api | VERIFIED | registry enumeration |
| Enchantments | `EnchantmentRegistry` | none | any thread | — | — | — | NOT AUDITED | — |
| Recipes | `CraftingService` | none | any thread | — | — | — | NOT AUDITED | enumeration/registration |

## 8. Protocol (§27.3 packet construction/inspection/interception)

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Packet send/inspect | `zamin-protocol-v1_8_8` | none | wire thread | — | — | — | NOT AUDITED | interception hooks |

---

## Ledger policy

- One row per capability; same-commit updates with the code, like the vanilla
  ledger.
- A row graduates to `IMPLEMENTED` only when a plugin-facing test exercises
  it through `zamin-api` (or a documented engine facade) without casting to
  engine internals.
- Every `NOT AUDITED` row above has an engine implementation behind it; the
  omission column names what stands between the engine and the public
  surface.
