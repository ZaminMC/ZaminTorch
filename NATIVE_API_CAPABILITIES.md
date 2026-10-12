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
| Difficulty | `EngineServer.difficulty()` | none | owner | — | — | — | NOT AUDITED | the engine setter path |
| TPS observation | `EngineTicker.averageTps` | none | any thread | — | — | — | NOT AUDITED | — |
| Configuration | `EngineConfig` | none (launcher-side file) | boot | — | — | — | NOT AUDITED | — |

## 2. World, chunk, block

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Block read | `EngineWorld.getBlock` | `World.getBlock` | any thread (epoch read) | — | Stable | api | VERIFIED | — |
| Block write | `EngineWorld.setBlock` | `World.setBlock` | simulation context (asserted) | — | Stable | api | VERIFIED | — |
| Time read | `EngineWorld.timeOfDay/totalTicks` | `World.timeOfDay/totalTicks` | any thread | — | Stable | api | VERIFIED | — |
| Time set | `EngineWorld.setTimeOfDay` | none | owner | — | — | — | NOT AUDITED | rides `/time` |
| Chunk handles | `EngineChunk`, `getOrGenerate`, `peek`, `loadedChunks` | `isChunkLoaded` only | owner | — | — | — | NOT AUDITED | chunk content reads, biome reads |
| Light levels | `EngineWorld.blockLightAt/skyLightAt` | none | any thread | — | — | — | NOT AUDITED | — |
| Block change observation | `WorldChangeListener` (internal) | none | owner callback | internal only | — | — | NOT AUDITED | the public event ride |
| Scheduled ticks | `BlockUpdateSystem` | none | owner | — | — | — | NOT AUDITED | query/cancel surface |
| World deltas | `WorldDeltaSnapshot` | none | owner | — | — | — | NOT AUDITED | persistence-facing today |
| Block registry | `FrozenBlockRegistry`, `EngineBlockType` | `BlockRegistry` (limited) | any thread | — | Stable | api | AUDITED | lookup by id, the behavior tables |
| Block behavior/metadata tables | `BlockBehaviorTable`, `BlockLightTable` | none | any thread | — | — | — | NOT AUDITED | hardness/diggability reads |

## 3. Entities

| Capability | Engine implementation | Public entry point | Threading | Hooks | Stability | Tests | Status | Omissions |
|---|---|---|---|---|---|---|---|---|
| Player identity/state | `PlayerSession` | `Player` (uuid, name, state, world, position, rotation, onGround) | any thread | — | Stable | api | VERIFIED | — |
| Player health/food | `PlayerSession.health/food/saturation` | none | owner | — | — | — | NOT AUDITED | — |
| Player XP | `totalXp`, `experienceLevel`, enchanting seed | none | owner | — | — | — | NOT AUDITED | — |
| Player inventory | `PlayerInventory` (36 slots + held) | none | owner | — | — | — | NOT AUDITED | — |
| Player gamemode | `PlayerSession.gamemode/setGamemode` | none | owner | — | — | — | NOT AUDITED | — |
| Player fire | `ignite/extinguish/tickFire` | none | owner | — | — | — | NOT AUDITED | — |
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
| Tick submission | `EngineTicker.submit` | **`World.execute(Runnable)`** — the §27.6 owner path for plugins | submit any thread; run on the owner lane | — | Experimental | `EntityApiAcceptanceTest` (the foreign-thread arms) | IMPLEMENTED | delayed/repeating task controls (the scheduler wave) |
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
| Damage/knockback events | the hurt paths | planned | — | — | — | — | NOT AUDITED | needs the damage-source model (the combat family) |
| Explosion events | `ExplosionService` | planned | — | — | — | — | NOT AUDITED | with the TNT/combat wave |
| Player join/quit events | the login walk | planned | — | — | — | — | NOT AUDITED | with the session wave |
| Entity death events | the death walks | planned | — | — | — | — | NOT AUDITED | with the death/loot wave |

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
