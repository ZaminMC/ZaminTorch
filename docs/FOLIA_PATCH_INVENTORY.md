# Folia 26.2.x Patch Inventory — all modified Java source paths

Generated mechanically from `Folia-ver-26.2.x.zip` (blob `fb817f8be9fea6532fff9fe901a526c2ab40dbd5`) by `scripts/folia_inventory.py` + `scripts/folia_categorize.py`. 460 Java path entries; one row per modified path per patch (hunk counts and +/- churn line counts are mechanical counts from the patch text; category is keyword-classified — the per-path analysis for concurrency-critical buckets lives in [FOLIA_FORENSIC_AUDIT.md](FOLIA_FORENSIC_AUDIT.md)).

### minecraft

| Patch | Java source path | Hunks | +churn | -churn | Category |
|---|---|---|---|---|---|
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/common/misc/NearbyPlayers.java` | 2 | 2 | 5 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/paper/PaperHooks.java` | 2 | 2 | 2 | Other / misc |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/paper/util/BaseChunkSystemHooks.java` | 4 | 12 | 8 | Chunk system / world state |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/level/chunk/ChunkData.java` | 1 | 1 | 1 | Chunk system / world state |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/level/entity/EntityLookup.java` | 2 | 16 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/level/entity/server/ServerEntityLookup.java` | 5 | 13 | 19 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/player/RegionizedPlayerChunkLoader.java` | 2 | 2 | 2 | Regionizer / region structure |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/queue/ChunkUnloadQueue.java` | 1 | 33 | 0 | Other / misc |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkHolderManager.java` | 14 | 196 | 56 | Chunk system / world state |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkTaskScheduler.java` | 6 | 39 | 17 | Chunk system / world state |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/chunk_system/scheduling/NewChunkHolder.java` | 2 | 2 | 2 | Chunk system / world state |
| 0001-Region-Threading-Base | `ca/spottedleaf/moonrise/patches/collisions/CollisionUtil.java` | 1 | 11 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `io/papermc/paper/entity/activation/ActivationRange.java` | 7 | 46 | 36 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `io/papermc/paper/redstone/RedstoneWireTurbo.java` | 2 | 7 | 2 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/RegionShutdownThread.java` | 1 | 229 | 0 | Other / misc |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/RegionizedData.java` | 1 | 235 | 0 | Regionizer / region structure |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/RegionizedServer.java` | 1 | 427 | 0 | Regionizer / region structure |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/RegionizedTaskQueue.java` | 1 | 863 | 0 | Regionizer / region structure |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/RegionizedWorldData.java` | 1 | 787 | 0 | Regionizer / region structure |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/TeleportUtils.java` | 1 | 82 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/ThreadedRegionizer.java` | 1 | 1405 | 0 | Regionizer / region structure |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/TickRegionScheduler.java` | 1 | 562 | 0 | Tick scheduling |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/TickRegions.java` | 1 | 479 | 3 | Regionizer / region structure |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/commands/CommandServerHealth.java` | 1 | 355 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/commands/CommandUtil.java` | 1 | 120 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/scheduler/FoliaRegionScheduler.java` | 1 | 427 | 0 | Tick scheduling |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/util/SimpleThreadLocalRandomSource.java` | 1 | 79 | 0 | Other / misc |
| 0001-Region-Threading-Base | `io/papermc/paper/threadedregions/util/ThreadLocalRandomSource.java` | 1 | 73 | 0 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/commands/CommandSourceStack.java` | 1 | 1 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/commands/Commands.java` | 4 | 29 | 27 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/core/dispenser/DispenseItemBehavior.java` | 2 | 7 | 6 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/gametest/framework/GameTestHelper.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/gametest/framework/GameTestServer.java` | 1 | 6 | 2 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/network/Connection.java` | 15 | 155 | 57 | Packet / network / protocol |
| 0001-Region-Threading-Base | `net/minecraft/network/PacketProcessor.java` | 3 | 10 | 3 | Packet / network / protocol |
| 0001-Region-Threading-Base | `net/minecraft/network/protocol/PacketUtils.java` | 1 | 10 | 2 | Packet / network / protocol |
| 0001-Region-Threading-Base | `net/minecraft/server/MinecraftServer.java` | 40 | 276 | 170 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/AdvancementCommands.java` | 2 | 14 | 9 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/AttributeCommand.java` | 3 | 93 | 21 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/ClearInventoryCommands.java` | 1 | 8 | 3 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/DamageCommand.java` | 1 | 20 | 3 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/EffectCommands.java` | 3 | 18 | 3 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/EnchantCommand.java` | 1 | 67 | 34 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/ExperienceCommand.java` | 2 | 18 | 6 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/FetchProfileCommand.java` | 2 | 2 | 2 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/FillBiomeCommand.java` | 3 | 27 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/FillCommand.java` | 3 | 24 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/ForceLoadCommand.java` | 4 | 35 | 3 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/GameModeCommand.java` | 1 | 4 | 2 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/GiveCommand.java` | 1 | 12 | 10 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/KillCommand.java` | 1 | 3 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/PlaceCommand.java` | 5 | 66 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/RecipeCommand.java` | 2 | 12 | 2 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/RideCommand.java` | 3 | 37 | 4 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/RotateCommand.java` | 1 | 6 | 2 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/SetBlockCommand.java` | 3 | 19 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/SetSpawnCommand.java` | 1 | 5 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/SummonCommand.java` | 1 | 7 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/TeleportCommand.java` | 2 | 18 | 12 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/TimeCommand.java` | 6 | 37 | 6 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/WeatherCommand.java` | 1 | 6 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/commands/WorldBorderCommand.java` | 7 | 86 | 7 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/dedicated/DedicatedServer.java` | 4 | 18 | 6 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/server/level/ChunkMap.java` | 14 | 65 | 86 | Chunk system / world state |
| 0001-Region-Threading-Base | `net/minecraft/server/level/DistanceManager.java` | 4 | 8 | 8 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/level/PlayerSpawnFinder.java` | 4 | 13 | 9 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/level/ServerChunkCache.java` | 11 | 94 | 26 | Chunk system / world state |
| 0001-Region-Threading-Base | `net/minecraft/server/level/ServerEntityGetter.java` | 2 | 4 | 4 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/level/ServerLevel.java` | 62 | 325 | 206 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/level/ServerPlayer.java` | 11 | 518 | 19 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/level/ServerPlayerGameMode.java` | 4 | 5 | 5 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/level/TicketType.java` | 1 | 7 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/level/WorldGenRegion.java` | 2 | 8 | 0 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/server/network/ServerCommonPacketListenerImpl.java` | 1 | 13 | 18 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/network/ServerConfigurationPacketListenerImpl.java` | 2 | 30 | 2 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/network/ServerConnectionListener.java` | 2 | 5 | 2 | Packet / network / protocol |
| 0001-Region-Threading-Base | `net/minecraft/server/network/ServerGamePacketListenerImpl.java` | 31 | 117 | 43 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/network/ServerLoginPacketListenerImpl.java` | 4 | 9 | 3 | Packet / network / protocol |
| 0001-Region-Threading-Base | `net/minecraft/server/network/config/PrepareSpawnTask.java` | 5 | 68 | 10 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/server/players/BanListEntry.java` | 5 | 6 | 6 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/server/players/OldUsersConverter.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/server/players/PlayerList.java` | 27 | 124 | 39 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/server/players/StoredUserList.java` | 3 | 4 | 0 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/server/waypoints/ServerWaypointManager.java` | 2 | 4 | 34 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/util/SpawnUtil.java` | 1 | 1 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/world/RandomSequences.java` | 2 | 11 | 11 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/attribute/EnvironmentAttributeLayer.java` | 1 | 7 | 0 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/attribute/EnvironmentAttributeSystem.java` | 3 | 49 | 3 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/clock/ServerClockManager.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/damagesource/CombatTracker.java` | 2 | 2 | 2 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/damagesource/DamageSource.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/damagesource/FallLocation.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/Entity.java` | 27 | 868 | 21 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/EntityReference.java` | 6 | 15 | 15 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/LivingEntity.java` | 13 | 40 | 12 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/Mob.java` | 4 | 32 | 6 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/PortalProcessor.java` | 1 | 6 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/TamableAnimal.java` | 2 | 21 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/Brain.java` | 1 | 9 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/behavior/GoToPotentialJobSite.java` | 1 | 2 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/behavior/PoiCompetitorScan.java` | 1 | 5 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/behavior/YieldJobSite.java` | 1 | 7 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/navigation/GroundPathNavigation.java` | 1 | 5 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/navigation/PathNavigation.java` | 3 | 4 | 4 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/sensing/PlayerSensor.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/sensing/TemptingSensor.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/village/VillageSiege.java` | 4 | 35 | 29 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/ai/village/poi/PoiManager.java` | 3 | 6 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/animal/bee/Bee.java` | 2 | 10 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/animal/happyghast/HappyGhast.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/boss/enderdragon/EndCrystal.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/decoration/ItemFrame.java` | 1 | 2 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/item/FallingBlockEntity.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/item/ItemEntity.java` | 1 | 11 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/item/PrimedTnt.java` | 2 | 13 | 5 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/monster/Vex.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/npc/CatSpawner.java` | 1 | 6 | 5 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/npc/villager/AbstractVillager.java` | 1 | 9 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/npc/villager/Villager.java` | 3 | 3 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/npc/wanderingtrader/WanderingTraderSpawner.java` | 2 | 15 | 15 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/player/Player.java` | 1 | 8 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/FireworkRocketEntity.java` | 1 | 5 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/FishingHook.java` | 2 | 14 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/LlamaSpit.java` | 1 | 5 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/Projectile.java` | 2 | 13 | 2 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/ThrowableProjectile.java` | 1 | 5 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/arrow/AbstractArrow.java` | 1 | 5 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/hurtingprojectile/AbstractHurtingProjectile.java` | 1 | 5 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/hurtingprojectile/SmallFireball.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/throwableitemprojectile/ThrownEgg.java` | 1 | 2 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/projectile/throwableitemprojectile/ThrownEnderpearl.java` | 4 | 100 | 7 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/raid/Raid.java` | 5 | 26 | 5 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/raid/Raider.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/raid/Raids.java` | 8 | 32 | 17 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/variant/StructureCheck.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/vehicle/minecart/MinecartCommandBlock.java` | 1 | 6 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/entity/vehicle/minecart/MinecartHopper.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/inventory/AbstractContainerMenu.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/item/ItemStack.java` | 2 | 11 | 10 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/item/MapItem.java` | 7 | 8 | 2 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/item/component/LodestoneTracker.java` | 1 | 4 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/BaseCommandBlock.java` | 3 | 6 | 2 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/EntityGetter.java` | 6 | 11 | 6 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/Level.java` | 25 | 116 | 55 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/LevelAccessor.java` | 1 | 9 | 3 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/LevelReader.java` | 1 | 19 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/NaturalSpawner.java` | 1 | 1 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/world/level/ServerExplosion.java` | 1 | 4 | 3 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/ServerLevelAccessor.java` | 1 | 6 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/StructureManager.java` | 3 | 5 | 25 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/Block.java` | 1 | 2 | 2 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/DaylightDetectorBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/DoublePlantBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/EndGatewayBlock.java` | 1 | 42 | 15 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/EndPortalBlock.java` | 2 | 15 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/FarmlandBlock.java` | 1 | 2 | 2 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/HoneyBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/LightningRodBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/MushroomBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/NetherFungusBlock.java` | 1 | 2 | 2 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/NetherPortalBlock.java` | 3 | 30 | 3 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/Portal.java` | 1 | 4 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/RedStoneWireBlock.java` | 7 | 16 | 8 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/RedstoneTorchBlock.java` | 3 | 13 | 7 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/SaplingBlock.java` | 2 | 9 | 8 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/SpreadingSnowyBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/VegetationBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/WitherSkullBlock.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/BeaconBlockEntity.java` | 2 | 2 | 2 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/BlockEntity.java` | 3 | 8 | 2 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/CommandBlockEntity.java` | 1 | 7 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/ConduitBlockEntity.java` | 2 | 2 | 2 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/HopperBlockEntity.java` | 13 | 28 | 15 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/SculkCatalystBlockEntity.java` | 1 | 2 | 2 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java` | 3 | 220 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/entity/TickingBlockEntity.java` | 1 | 2 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/grower/TreeGrower.java` | 1 | 25 | 23 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/piston/PistonBaseBlock.java` | 1 | 1 | 1 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java` | 3 | 14 | 4 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/level/border/WorldBorder.java` | 4 | 7 | 12 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/chunk/ChunkGenerator.java` | 1 | 1 | 1 | Chunk system / world state |
| 0001-Region-Threading-Base | `net/minecraft/world/level/chunk/LevelChunk.java` | 9 | 28 | 10 | Chunk system / world state |
| 0001-Region-Threading-Base | `net/minecraft/world/level/chunk/storage/SerializableChunkData.java` | 1 | 1 | 1 | Chunk system / world state |
| 0001-Region-Threading-Base | `net/minecraft/world/level/dimension/end/EnderDragonFight.java` | 5 | 22 | 4 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/gameevent/GameEventDispatcher.java` | 1 | 5 | 0 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/gamerules/GameRuleMap.java` | 5 | 7 | 5 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/levelgen/PatrolSpawner.java` | 4 | 7 | 6 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/world/level/levelgen/PhantomSpawner.java` | 2 | 6 | 5 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/world/level/levelgen/feature/EndPlatformFeature.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/levelgen/structure/StructureStart.java` | 4 | 20 | 6 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/redstone/CollectingNeighborUpdater.java` | 1 | 1 | 0 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `net/minecraft/world/level/saveddata/SavedData.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/saveddata/WanderingTraderData.java` | 1 | 1 | 0 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/saveddata/maps/MapIndex.java` | 1 | 4 | 4 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/level/saveddata/maps/MapItemSavedData.java` | 13 | 21 | 17 | Global state / misc server |
| 0001-Region-Threading-Base | `net/minecraft/world/level/storage/SavedDataStorage.java` | 6 | 93 | 16 | Other / misc |
| 0001-Region-Threading-Base | `net/minecraft/world/ticks/LevelChunkTicks.java` | 1 | 15 | 0 | Chunk system / world state |
| 0001-Region-Threading-Base | `net/minecraft/world/ticks/LevelTicks.java` | 3 | 72 | 4 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `net/minecraft/world/timeline/AttributeTrackSampler.java` | 1 | 19 | 0 | Other / misc |
| 0002-Max-pending-logins | `net/minecraft/server/network/ServerLoginPacketListenerImpl.java` | 1 | 1 | 1 | Packet / network / protocol |
| 0002-Max-pending-logins | `net/minecraft/server/players/PlayerList.java` | 1 | 11 | 0 | Entity lifecycle / physics / movement |
| 0003-Add-chunk-system-throughput-counters-to-tps | `ca/spottedleaf/moonrise/patches/chunk_system/scheduling/task/ChunkFullTask.java` | 3 | 28 | 0 | Tick scheduling |
| 0003-Add-chunk-system-throughput-counters-to-tps | `io/papermc/paper/threadedregions/commands/CommandServerHealth.java` | 2 | 9 | 0 | Global state / misc server |
| 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu | `net/minecraft/world/level/Level.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu | `net/minecraft/world/level/block/DetectorRailBlock.java` | 1 | 2 | 2 | Block updates / redstone / fluids |
| 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu | `net/minecraft/world/level/block/PoweredRailBlock.java` | 1 | 2 | 2 | Block updates / redstone / fluids |
| 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu | `net/minecraft/world/level/block/RedStoneWireBlock.java` | 1 | 2 | 1 | Block updates / redstone / fluids |
| 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu | `net/minecraft/world/level/block/TripWireBlock.java` | 1 | 1 | 0 | Block updates / redstone / fluids |
| 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu | `net/minecraft/world/level/block/TripWireHookBlock.java` | 1 | 1 | 0 | Block updates / redstone / fluids |
| 0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu | `net/minecraft/world/level/redstone/CollectingNeighborUpdater.java` | 4 | 7 | 3 | Block updates / redstone / fluids |
| 0005-Block-reading-in-world-tile-entities-on-worldgen-thr | `net/minecraft/world/level/chunk/ImposterProtoChunk.java` | 1 | 5 | 0 | Chunk system / world state |
| 0006-Sync-vehicle-position-to-player-position-on-player-d | `net/minecraft/server/level/ServerPlayer.java` | 1 | 11 | 1 | Entity lifecycle / physics / movement |
| 0007-Region-profiler | `ca/spottedleaf/leafprofiler/LProfilerRegistry.java` | 1 | 48 | 0 | Observability / profiler / watchdog |
| 0007-Region-profiler | `ca/spottedleaf/leafprofiler/RegionizedProfiler.java` | 1 | 280 | 0 | Regionizer / region structure |
| 0007-Region-profiler | `ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkHolderManager.java` | 2 | 2 | 0 | Chunk system / world state |
| 0007-Region-profiler | `ca/spottedleaf/moonrise/patches/chunk_system/scheduling/NewChunkHolder.java` | 2 | 3 | 0 | Chunk system / world state |
| 0007-Region-profiler | `io/papermc/paper/threadedregions/TickRegionScheduler.java` | 3 | 19 | 0 | Tick scheduling |
| 0007-Region-profiler | `io/papermc/paper/threadedregions/TickRegions.java` | 5 | 36 | 2 | Regionizer / region structure |
| 0007-Region-profiler | `io/papermc/paper/threadedregions/commands/CommandProfiler.java` | 1 | 246 | 0 | Global state / misc server |
| 0007-Region-profiler | `net/minecraft/network/PacketProcessor.java` | 1 | 3 | 0 | Packet / network / protocol |
| 0007-Region-profiler | `net/minecraft/server/MinecraftServer.java` | 8 | 20 | 0 | Global state / misc server |
| 0007-Region-profiler | `net/minecraft/server/level/ChunkMap.java` | 4 | 13 | 1 | Chunk system / world state |
| 0007-Region-profiler | `net/minecraft/server/level/ServerChunkCache.java` | 11 | 24 | 1 | Chunk system / world state |
| 0007-Region-profiler | `net/minecraft/server/level/ServerLevel.java` | 10 | 36 | 0 | Entity lifecycle / physics / movement |
| 0007-Region-profiler | `net/minecraft/server/players/PlayerList.java` | 2 | 3 | 0 | Entity lifecycle / physics / movement |
| 0007-Region-profiler | `net/minecraft/world/entity/EntityType.java` | 3 | 15 | 2 | Entity lifecycle / physics / movement |
| 0007-Region-profiler | `net/minecraft/world/level/Level.java` | 4 | 11 | 0 | Entity lifecycle / physics / movement |
| 0007-Region-profiler | `net/minecraft/world/level/block/entity/BlockEntityType.java` | 1 | 5 | 1 | Entity lifecycle / physics / movement |
| 0007-Region-profiler | `net/minecraft/world/level/block/entity/BlockEntityTypes.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0007-Region-profiler | `net/minecraft/world/level/chunk/LevelChunk.java` | 2 | 4 | 0 | Chunk system / world state |
| 0007-Region-profiler | `net/minecraft/world/ticks/LevelTicks.java` | 1 | 6 | 0 | Entity lifecycle / physics / movement |
| 0008-Add-watchdog-thread | `io/papermc/paper/threadedregions/FoliaWatchdogThread.java` | 1 | 104 | 0 | Observability / profiler / watchdog |
| 0008-Add-watchdog-thread | `io/papermc/paper/threadedregions/TickRegionScheduler.java` | 5 | 12 | 0 | Tick scheduling |
| 0008-Add-watchdog-thread | `io/papermc/paper/threadedregions/TickRegions.java` | 1 | 2 | 2 | Regionizer / region structure |
| 0009-Teleport-desynced-passengers-to-root-vehicle | `ca/spottedleaf/leafprofiler/LProfilerRegistry.java` | 1 | 1 | 0 | Observability / profiler / watchdog |
| 0009-Teleport-desynced-passengers-to-root-vehicle | `net/minecraft/server/MinecraftServer.java` | 1 | 10 | 0 | Global state / misc server |
| 0009-Teleport-desynced-passengers-to-root-vehicle | `net/minecraft/world/entity/Entity.java` | 1 | 74 | 0 | Entity lifecycle / physics / movement |
| 0010-Do-not-allow-out-of-region-teleport-accept | `net/minecraft/server/network/ServerGamePacketListenerImpl.java` | 1 | 13 | 0 | Entity lifecycle / physics / movement |
| 0011-Use-Folia-logo | `net/minecraft/server/gui/MinecraftServerGui.java` | 1 | 1 | 1 | Global state / misc server |

### paper-server

| Patch | Java source path | Hunks | +churn | -churn | Category |
|---|---|---|---|---|---|
| 0001-Region-Threading-Base | `src/main/java/ca/spottedleaf/moonrise/common/util/TickThread.java` | 3 | 144 | 13 | Tick scheduling |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/SparksFly.java` | 5 | 6 | 9 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/adventure/ChatProcessor.java` | 2 | 2 | 2 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/adventure/providers/ClickCallbackProviderImpl.java` | 2 | 18 | 12 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/command/PaperCommands.java` | 2 | 2 | 1 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/command/subcommands/EntityCommand.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/command/subcommands/HeapDumpCommand.java` | 1 | 2 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/command/subcommands/ReloadCommand.java` | 1 | 2 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/configuration/GlobalConfiguration.java` | 1 | 15 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/configuration/WorldConfiguration.java` | 2 | 2 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/console/BrigadierCommandCompleter.java` | 1 | 1 | 1 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/entity/PaperSchoolableFish.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/entity/activation/ActivationType.java` | 1 | 1 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/plugin/manager/PaperPermissionManager.java` | 7 | 48 | 5 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/plugin/manager/PaperPluginInstanceManager.java` | 1 | 1 | 6 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/plugin/provider/configuration/PaperPluginMeta.java` | 2 | 8 | 0 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/plugin/provider/type/paper/PaperPluginProviderFactory.java` | 1 | 5 | 0 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/plugin/provider/type/spigot/SpigotPluginProviderFactory.java` | 2 | 5 | 1 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/threadedregions/EntityScheduler.java` | 1 | 8 | 0 | Tick scheduling |
| 0001-Region-Threading-Base | `src/main/java/io/papermc/paper/util/MCUtil.java` | 1 | 1 | 0 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/CraftServer.java` | 8 | 76 | 6 | Bukkit/Paper API compatibility |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/CraftWorld.java` | 31 | 50 | 17 | Bukkit/Paper API compatibility |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java` | 12 | 82 | 6 | Bukkit/Paper API compatibility |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/block/CraftBlockEntityState.java` | 2 | 3 | 3 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/block/CraftBlockState.java` | 2 | 3 | 0 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/block/CraftBlockStates.java` | 1 | 3 | 3 | Block updates / redstone / fluids |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/command/ConsoleCommandCompleter.java` | 2 | 2 | 2 | Bukkit/Paper API compatibility |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/AbstractProjectile.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractArrow.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractCow.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractCubeMob.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractHorse.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractNautilus.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractSkeleton.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractVillager.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAbstractWindCharge.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAgeable.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAllay.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAmbient.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAnimals.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAreaEffectCloud.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftArmadillo.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftArmorStand.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftArrow.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftAxolotl.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBat.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBee.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBlaze.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBlockAttachedEntity.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBlockDisplay.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBoat.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBogged.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBreeze.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftBreezeWindCharge.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCamel.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCamelHusk.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCat.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCaveSpider.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftChestBoat.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftChestedHorse.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftChicken.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCod.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftComplexPart.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCopperGolem.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCow.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCreaking.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCreature.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftCreeper.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftDisplay.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftDolphin.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftDrowned.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEgg.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEnderCrystal.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEnderDragon.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEnderDragonPart.java` | 1 | 8 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEnderPearl.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEnderSignal.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEnderman.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEndermite.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java` | 9 | 51 | 24 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEvoker.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftEvokerFangs.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftExperienceOrb.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftFallingBlock.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftFireball.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftFirework.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftFish.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftFishHook.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftFox.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftFrog.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftGhast.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftGiant.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftGlowItemFrame.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftGlowSquid.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftGoat.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftGolem.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftGuardian.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftHanging.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftHoglin.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftHorse.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftHumanEntity.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftIllager.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftIllusioner.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftInteraction.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftIronGolem.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftItem.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftItemDisplay.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftItemFrame.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftLargeFireball.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftLeash.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftLightningStrike.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftLivingEntity.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftLlama.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftLlamaSpit.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMagmaCube.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMannequin.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMarker.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMinecart.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMinecartCommand.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMinecartContainer.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMinecartFurnace.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMinecartHopper.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMinecartMobSpawner.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMinecartTNT.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMob.java` | 1 | 2 | 1 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMonster.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftMushroomCow.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftNautilus.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftOcelot.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftOminousItemSpawner.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPainting.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPanda.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftParched.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftParrot.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPhantom.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPig.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPigZombie.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPiglin.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPiglinAbstract.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPiglinBrute.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPillager.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java` | 7 | 39 | 7 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPolarBear.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftProjectile.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftPufferFish.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftRabbit.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftRaider.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftRavager.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSalmon.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSheep.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftShulker.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftShulkerBullet.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSilverfish.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSizedFireball.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSkeleton.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSkeletonHorse.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSlime.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSmallFireball.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSniffer.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSnowball.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSnowman.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSpectralArrow.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSpellcaster.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSpider.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSquid.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftStrider.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftSulfurCube.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTNTPrimed.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTadpole.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTameableAnimal.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTextDisplay.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftThrowableProjectile.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftThrownExpBottle.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftThrownLingeringPotion.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftThrownPotion.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftThrownSplashPotion.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTraderLlama.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTrident.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTropicalFish.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftTurtle.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftVex.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftVillager.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftVillagerZombie.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftVindicator.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWanderingTrader.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWarden.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWaterMob.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWindCharge.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWitch.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWither.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWitherSkull.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftWolf.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftZoglin.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftZombie.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/entity/CraftZombieNautilus.java` | 1 | 1 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java` | 2 | 2 | 2 | Bukkit/Paper API compatibility |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/map/CraftMapView.java` | 5 | 38 | 0 | Bukkit/Paper API compatibility |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/scheduler/CraftScheduler.java` | 1 | 1 | 0 | Tick scheduling |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/scoreboard/CraftScoreboard.java` | 3 | 3 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/scoreboard/CraftScoreboardManager.java` | 2 | 2 | 0 | Global state / misc server |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/util/CraftMagicNumbers.java` | 1 | 6 | 0 | Bukkit/Paper API compatibility |
| 0001-Region-Threading-Base | `src/main/java/org/bukkit/craftbukkit/util/DelegatedLevelAccessor.java` | 1 | 7 | 0 | Entity lifecycle / physics / movement |
| 0001-Region-Threading-Base | `src/main/java/org/spigotmc/SpigotCommand.java` | 2 | 2 | 0 | Other / misc |
| 0001-Region-Threading-Base | `src/main/java/org/spigotmc/SpigotConfig.java` | 2 | 2 | 2 | Global state / misc server |
| 0001-Region-Threading-Base | `src/main/java/org/spigotmc/SpigotWorldConfig.java` | 1 | 1 | 1 | Global state / misc server |
| 0001-Region-Threading-Base | `src/test/java/io/papermc/paper/plugin/TestPluginMeta.java` | 1 | 7 | 0 | Other / misc |
| 0002-Build-changes | `src/main/java/com/destroystokyo/paper/Metrics.java` | 2 | 3 | 3 | Other / misc |
| 0002-Build-changes | `src/main/java/com/destroystokyo/paper/PaperVersionFetcher.java` | 4 | 5 | 5 | Other / misc |
| 0002-Build-changes | `src/main/java/io/papermc/paper/ServerBuildInfoImpl.java` | 1 | 2 | 2 | Bukkit/Paper API compatibility |
| 0003-Fix-tests-by-removing-them | `src/test/java/io/papermc/paper/permissions/MinecraftCommandPermissionsTest.java` | 1 | 1 | 0 | Other / misc |
| 0004-Region-profiler | `src/main/java/io/papermc/paper/command/PaperCommands.java` | 1 | 1 | 0 | Other / misc |
| 0005-Add-watchdog-thread | `src/main/java/org/spigotmc/WatchdogThread.java` | 1 | 1 | 1 | Observability / profiler / watchdog |
| 0006-Add-TPS-From-Region | `src/main/java/org/bukkit/craftbukkit/CraftServer.java` | 1 | 65 | 0 | Bukkit/Paper API compatibility |

### paper-api

| Patch | Java source path | Hunks | +churn | -churn | Category |
|---|---|---|---|---|---|
| 0001-Force-disable-timings | `src/main/java/co/aikar/timings/Timings.java` | 1 | 1 | 0 | Other / misc |
| 0002-Region-scheduler-API | `src/main/java/org/bukkit/plugin/SimplePluginManager.java` | 1 | 2 | 2 | Bukkit/Paper API compatibility |
| 0002-Region-scheduler-API | `src/main/java/org/bukkit/scheduler/BukkitScheduler.java` | 1 | 9 | 0 | Tick scheduling |
| 0003-Require-plugins-to-be-explicitly-marked-as-Folia-sup | `src/main/java/io/papermc/paper/plugin/configuration/PluginMeta.java` | 1 | 8 | 0 | Other / misc |
| 0003-Require-plugins-to-be-explicitly-marked-as-Folia-sup | `src/main/java/org/bukkit/plugin/PluginDescriptionFile.java` | 3 | 23 | 0 | Bukkit/Paper API compatibility |
| 0004-Add-TPS-From-Region | `src/main/java/org/bukkit/Bukkit.java` | 1 | 36 | 0 | Bukkit/Paper API compatibility |
| 0004-Add-TPS-From-Region | `src/main/java/org/bukkit/Server.java` | 1 | 30 | 0 | Bukkit/Paper API compatibility |