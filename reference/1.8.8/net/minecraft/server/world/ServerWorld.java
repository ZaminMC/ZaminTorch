package net.minecraft.server.world;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.entity.living.mob.passive.Npc;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.living.mob.water.WaterMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.AddGlobalEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.scoreboard.SavedScoreboardData;
import net.minecraft.server.ChunkMap;
import net.minecraft.server.EntityMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.scoreboard.ServerScoreboard;
import net.minecraft.server.world.chunk.ServerChunkCache;
import net.minecraft.util.BlockableEventLoop;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.NaturalSpawner;
import net.minecraft.world.World;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.gen.feature.BonusChestFeature;
import net.minecraft.world.gen.structure.LootEntry;
import net.minecraft.world.gen.structure.StructureBox;
import net.minecraft.world.storage.SavedDataStorage;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.exception.SessionLockException;
import net.minecraft.world.village.SavedVillageData;
import net.minecraft.world.village.VillageSiege;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerWorld extends World implements BlockableEventLoop {
    private static final Logger LOGGER = LogManager.getLogger();
    private final MinecraftServer server;
    private final EntityMap entityMap;
    private final ChunkMap chunkMap;
    private final Set<ScheduledTick> scheduledTicks = Sets.newHashSet();
    private final TreeSet<ScheduledTick> scheduledTicksInOrder = new TreeSet<>();
    private final Map<UUID, Entity> entitiesByUuid = Maps.newHashMap();
    public ServerChunkCache chunkCache;
    public boolean savingDisabled;
    private boolean allPlayersSleeping;
    private int idleTimeout;
    private final PortalForcer portalForcer;
    private final NaturalSpawner naturalSpawner = new NaturalSpawner();
    protected final VillageSiege villageSiege = new VillageSiege(this);
    private ServerWorld.BlockEventQueue[] blockEvents = new ServerWorld.BlockEventQueue[]{new ServerWorld.BlockEventQueue(), new ServerWorld.BlockEventQueue()};
    private int nextBlockEventQueueIndex;
    private static final List<LootEntry> BONUS_CHEST_LOOT_ENTRIES = Lists.newArrayList(
        new LootEntry(Items.STICK, 0, 1, 3, 10),
        new LootEntry(Item.byBlock(Blocks.PLANKS), 0, 1, 3, 10),
        new LootEntry(Item.byBlock(Blocks.LOG), 0, 1, 3, 10),
        new LootEntry(Items.STONE_AXE, 0, 1, 1, 3),
        new LootEntry(Items.WOODEN_AXE, 0, 1, 1, 5),
        new LootEntry(Items.STONE_PICKAXE, 0, 1, 1, 3),
        new LootEntry(Items.WOODEN_PICKAXE, 0, 1, 1, 5),
        new LootEntry(Items.APPLE, 0, 2, 3, 5),
        new LootEntry(Items.BREAD, 0, 2, 3, 3),
        new LootEntry(Item.byBlock(Blocks.LOG2), 0, 1, 3, 10)
    );
    private List<ScheduledTick> scheduledTicksThisTick = Lists.newArrayList();

    public ServerWorld(MinecraftServer server, WorldStorage storage, WorldData data, int dimension, Profiler profiler) {
        super(storage, data, Dimension.fromId(dimension), profiler, false);
        this.server = server;
        this.entityMap = new EntityMap(this);
        this.chunkMap = new ChunkMap(this);
        this.dimension.init(this);
        this.chunkSource = this.createChunkCache();
        this.portalForcer = new PortalForcer(this);
        this.initAmbientDarkness();
        this.initWeather();
        this.getWorldBorder().setMaxSize(server.getMaxWorldSize());
    }

    @Override
    public World load() {
        this.savedDataStorage = new SavedDataStorage(this.storage);
        String s = SavedVillageData.getId(this.dimension);
        SavedVillageData savedvillagedata = (SavedVillageData)this.savedDataStorage.load(SavedVillageData.class, s);
        if (savedvillagedata == null) {
            this.villages = new SavedVillageData(this);
            this.savedDataStorage.set(s, this.villages);
        } else {
            this.villages = savedvillagedata;
            this.villages.setWorld(this);
        }

        this.scoreboard = new ServerScoreboard(this.server);
        SavedScoreboardData savedscoreboarddata = (SavedScoreboardData)this.savedDataStorage.load(SavedScoreboardData.class, "scoreboard");
        if (savedscoreboarddata == null) {
            savedscoreboarddata = new SavedScoreboardData();
            this.savedDataStorage.set("scoreboard", savedscoreboarddata);
        }

        savedscoreboarddata.setScoreboard(this.scoreboard);
        ((ServerScoreboard)this.scoreboard).setSavedData(savedscoreboarddata);
        this.getWorldBorder().setCenter(this.data.getBorderCenterX(), this.data.getBorderCenterZ());
        this.getWorldBorder().setDamagePerBlock(this.data.getBorderDamagePerBlock());
        this.getWorldBorder().setSafeZone(this.data.getBorderSafeZone());
        this.getWorldBorder().setWarningDistance(this.data.getBorderWarningBlocks());
        this.getWorldBorder().setWarningTime(this.data.getBorderWarningTime());
        if (this.data.getBorderSizeLerpTime() > 0L) {
            this.getWorldBorder().setSize(this.data.getBorderSize(), this.data.getBorderSizeLerpTarget(), this.data.getBorderSizeLerpTime());
        } else {
            this.getWorldBorder().setSize(this.data.getBorderSize());
        }

        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getData().isHardcore() && this.getDifficulty() != Difficulty.HARD) {
            this.getData().setDifficulty(Difficulty.HARD);
        }

        this.dimension.getBiomeSource().tick();
        if (this.canSkipNight()) {
            if (this.getGameRules().getBoolean("doDaylightCycle")) {
                long i = this.data.getTimeOfDay() + 24000L;
                this.data.setTimeOfDay(i - i % 24000L);
            }

            this.wakeSleepingPlayers();
        }

        this.profiler.push("mobSpawner");
        if (this.getGameRules().getBoolean("doMobSpawning") && this.data.getGeneratorType() != WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            this.naturalSpawner.tick(this, this.spawnAnimals, this.spawnMonsters, this.data.getTime() % 400L == 0L);
        }

        this.profiler.swap("chunkSource");
        this.chunkSource.tick();
        int j = this.calculateAmbientDarkness(1.0F);
        if (j != this.getAmbientDarkness()) {
            this.setAmbientDarkness(j);
        }

        this.data.setTime(this.data.getTime() + 1L);
        if (this.getGameRules().getBoolean("doDaylightCycle")) {
            this.data.setTimeOfDay(this.data.getTimeOfDay() + 1L);
        }

        this.profiler.swap("tickPending");
        this.doScheduledTicks(false);
        this.profiler.swap("tickBlocks");
        this.tickChunks();
        this.profiler.swap("chunkMap");
        this.chunkMap.tick();
        this.profiler.swap("village");
        this.villages.tick();
        this.villageSiege.tick();
        this.profiler.swap("portalForcer");
        this.portalForcer.tick(this.getTime());
        this.profiler.pop();
        this.doBlockEvents();
    }

    public Biome.SpawnEntry pickSpawnEntry(MobCategory category, BlockPos pos) {
        List<Biome.SpawnEntry> list = this.getChunkSource().getSpawnEntries(category, pos);
        return list != null && !list.isEmpty() ? WeightedPicker.pick(this.random, list) : null;
    }

    public boolean isValidSpawnEntry(MobCategory category, Biome.SpawnEntry entry, BlockPos pos) {
        List<Biome.SpawnEntry> list = this.getChunkSource().getSpawnEntries(category, pos);
        return list != null && !list.isEmpty() && list.contains(entry);
    }

    @Override
    public void updatePlayersSleepingStatus() {
        this.allPlayersSleeping = false;
        if (!this.players.isEmpty()) {
            int i = 0;
            int j = 0;

            for (PlayerEntity playerentity : this.players) {
                if (playerentity.isSpectator()) {
                    i++;
                } else if (playerentity.isSleeping()) {
                    j++;
                }
            }

            this.allPlayersSleeping = j > 0 && j >= this.players.size() - i;
        }
    }

    protected void wakeSleepingPlayers() {
        this.allPlayersSleeping = false;

        for (PlayerEntity playerentity : this.players) {
            if (playerentity.isSleeping()) {
                playerentity.wakeUp(false, false, true);
            }
        }

        this.clearWeather();
    }

    private void clearWeather() {
        this.data.setRainTime(0);
        this.data.setRaining(false);
        this.data.setThunderTime(0);
        this.data.setThundering(false);
    }

    public boolean canSkipNight() {
        if (this.allPlayersSleeping && !this.isClient) {
            for (PlayerEntity playerentity : this.players) {
                if (playerentity.isSpectator() || !playerentity.isSleepingLongEnough()) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void resetSpawnPoint() {
        if (this.data.getSpawnY() <= 0) {
            this.data.setSpawnY(this.getSeaLevel() + 1);
        }

        int i = this.data.getSpawnX();
        int j = this.data.getSpawnZ();
        int k = 0;

        while (this.getSurfaceBlock(new BlockPos(i, 0, j)).getMaterial() == Material.AIR) {
            i += this.random.nextInt(8) - this.random.nextInt(8);
            j += this.random.nextInt(8) - this.random.nextInt(8);
            if (++k == 10000) {
                break;
            }
        }

        this.data.setSpawnX(i);
        this.data.setSpawnZ(j);
    }

    @Override
    protected void tickChunks() {
        super.tickChunks();
        if (this.data.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            for (ChunkPos chunkpos1 : this.tickingChunks) {
                this.getChunkAt(chunkpos1.x, chunkpos1.z).tick(false);
            }
        } else {
            int i = 0;
            int j = 0;

            for (ChunkPos chunkpos : this.tickingChunks) {
                int k = chunkpos.x * 16;
                int l = chunkpos.z * 16;
                this.profiler.push("getChunk");
                WorldChunk worldchunk = this.getChunkAt(chunkpos.x, chunkpos.z);
                this.tickAmbienceAndLight(k, l, worldchunk);
                this.profiler.swap("tickChunk");
                worldchunk.tick(false);
                this.profiler.swap("thunder");
                if (this.random.nextInt(100000) == 0 && this.isRaining() && this.isThundering()) {
                    this.randomTickLCG = this.randomTickLCG * 3 + 1013904223;
                    int i1 = this.randomTickLCG >> 2;
                    BlockPos blockpos = this.findLightningTarget(new BlockPos(k + (i1 & 15), 0, l + (i1 >> 8 & 15)));
                    if (this.isRaining(blockpos)) {
                        this.addGlobalEntity(new LightningBoltEntity(this, blockpos.getX(), blockpos.getY(), blockpos.getZ()));
                    }
                }

                this.profiler.swap("iceandsnow");
                if (this.random.nextInt(16) == 0) {
                    this.randomTickLCG = this.randomTickLCG * 3 + 1013904223;
                    int k2 = this.randomTickLCG >> 2;
                    BlockPos blockpos2 = this.getPrecipitationHeight(new BlockPos(k + (k2 & 15), 0, l + (k2 >> 8 & 15)));
                    BlockPos blockpos1 = blockpos2.down();
                    if (this.canFreezeNaturally(blockpos1)) {
                        this.setBlockState(blockpos1, Blocks.ICE.defaultState());
                    }

                    if (this.isRaining() && this.canSnowFall(blockpos2, true)) {
                        this.setBlockState(blockpos2, Blocks.SNOW_LAYER.defaultState());
                    }

                    if (this.isRaining() && this.getBiome(blockpos1).isRainy()) {
                        this.getBlockState(blockpos1).getBlock().randomPrecipitationTick(this, blockpos1);
                    }
                }

                this.profiler.swap("tickBlocks");
                int l2 = this.getGameRules().getInt("randomTickSpeed");
                if (l2 > 0) {
                    for (WorldChunkSection worldchunksection : worldchunk.getSections()) {
                        if (worldchunksection != null && worldchunksection.hasRandomTickingBlocks()) {
                            for (int j1 = 0; j1 < l2; j1++) {
                                this.randomTickLCG = this.randomTickLCG * 3 + 1013904223;
                                int k1 = this.randomTickLCG >> 2;
                                int l1 = k1 & 15;
                                int i2 = k1 >> 8 & 15;
                                int j2 = k1 >> 16 & 15;
                                j++;
                                BlockState blockstate = worldchunksection.getBlockState(l1, j2, i2);
                                Block block = blockstate.getBlock();
                                if (block.ticksRandomly()) {
                                    i++;
                                    block.randomTick(this, new BlockPos(l1 + k, j2 + worldchunksection.getOffsetY(), i2 + l), blockstate, this.random);
                                }
                            }
                        }
                    }
                }

                this.profiler.pop();
            }
        }
    }

    protected BlockPos findLightningTarget(BlockPos pos) {
        BlockPos blockpos = this.getPrecipitationHeight(pos);
        Box box = new Box(blockpos, new BlockPos(blockpos.getX(), this.getHeight(), blockpos.getZ())).grown(3.0, 3.0, 3.0);
        List<LivingEntity> list = this.getEntitiesOfType(LivingEntity.class, box, new Predicate<LivingEntity>() {
            public boolean apply(LivingEntity livingEntity) {
                return livingEntity != null && livingEntity.isAlive() && ServerWorld.this.hasSkyAccess(livingEntity.getCommandSourceBlockPos());
            }
        });
        return !list.isEmpty() ? list.get(this.random.nextInt(list.size())).getCommandSourceBlockPos() : blockpos;
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, Block block) {
        ScheduledTick scheduledtick = new ScheduledTick(pos, block);
        return this.scheduledTicksThisTick.contains(scheduledtick);
    }

    @Override
    public void scheduleTick(BlockPos pos, Block block, int delay) {
        this.scheduleTick(pos, block, delay, 0);
    }

    @Override
    public void scheduleTick(BlockPos pos, Block block, int delay, int priority) {
        ScheduledTick scheduledtick = new ScheduledTick(pos, block);
        int i = 0;
        if (this.doTicksImmediately && block.getMaterial() != Material.AIR) {
            if (block.acceptsImmediateTicks()) {
                int b0 = 8;
                if (this.isAreaLoaded(scheduledtick.pos.add(-b0, -b0, -b0), scheduledtick.pos.add(b0, b0, b0))) {
                    BlockState blockstate = this.getBlockState(scheduledtick.pos);
                    if (blockstate.getBlock().getMaterial() != Material.AIR && blockstate.getBlock() == scheduledtick.getBlock()) {
                        blockstate.getBlock().tick(this, scheduledtick.pos, blockstate, this.random);
                    }
                }

                return;
            }

            delay = 1;
        }

        if (this.isAreaLoaded(pos.add(-i, -i, -i), pos.add(i, i, i))) {
            if (block.getMaterial() != Material.AIR) {
                scheduledtick.setTime(delay + this.data.getTime());
                scheduledtick.setPriority(priority);
            }

            if (!this.scheduledTicks.contains(scheduledtick)) {
                this.scheduledTicks.add(scheduledtick);
                this.scheduledTicksInOrder.add(scheduledtick);
            }
        }
    }

    @Override
    public void loadScheduledTick(BlockPos pos, Block block, int delay, int priority) {
        ScheduledTick scheduledtick = new ScheduledTick(pos, block);
        scheduledtick.setPriority(priority);
        if (block.getMaterial() != Material.AIR) {
            scheduledtick.setTime(delay + this.data.getTime());
        }

        if (!this.scheduledTicks.contains(scheduledtick)) {
            this.scheduledTicks.add(scheduledtick);
            this.scheduledTicksInOrder.add(scheduledtick);
        }
    }

    @Override
    public void tickEntities() {
        if (this.players.isEmpty()) {
            if (this.idleTimeout++ >= 1200) {
                return;
            }
        } else {
            this.resetIdleTimeout();
        }

        super.tickEntities();
    }

    public void resetIdleTimeout() {
        this.idleTimeout = 0;
    }

    @Override
    public boolean doScheduledTicks(boolean flush) {
        if (this.data.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            return false;
        }

        int i = this.scheduledTicksInOrder.size();
        if (i != this.scheduledTicks.size()) {
            throw new IllegalStateException("TickNextTick list out of synch");
        }

        if (i > 1000) {
            i = 1000;
        }

        this.profiler.push("cleaning");

        for (int j = 0; j < i; j++) {
            ScheduledTick scheduledtick = this.scheduledTicksInOrder.first();
            if (!flush && scheduledtick.time > this.data.getTime()) {
                break;
            }

            this.scheduledTicksInOrder.remove(scheduledtick);
            this.scheduledTicks.remove(scheduledtick);
            this.scheduledTicksThisTick.add(scheduledtick);
        }

        this.profiler.pop();
        this.profiler.push("ticking");
        Iterator<ScheduledTick> iterator = this.scheduledTicksThisTick.iterator();

        while (iterator.hasNext()) {
            ScheduledTick scheduledtick1 = iterator.next();
            iterator.remove();
            int k = 0;
            if (this.isAreaLoaded(scheduledtick1.pos.add(-k, -k, -k), scheduledtick1.pos.add(k, k, k))) {
                BlockState blockstate = this.getBlockState(scheduledtick1.pos);
                if (blockstate.getBlock().getMaterial() != Material.AIR && Block.areEqual(blockstate.getBlock(), scheduledtick1.getBlock())) {
                    try {
                        blockstate.getBlock().tick(this, scheduledtick1.pos, blockstate, this.random);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.of(throwable, "Exception while ticking a block");
                        CrashReportCategory crashreportcategory = crashreport.addCategory("Block being ticked");
                        CrashReportCategory.addBlockDetails(crashreportcategory, scheduledtick1.pos, blockstate);
                        throw new CrashException(crashreport);
                    }
                }
            } else {
                this.scheduleTick(scheduledtick1.pos, scheduledtick1.getBlock(), 0);
            }
        }

        this.profiler.pop();
        this.scheduledTicksThisTick.clear();
        return !this.scheduledTicksInOrder.isEmpty();
    }

    @Override
    public List<ScheduledTick> getScheduledTicks(WorldChunk chunk, boolean remove) {
        ChunkPos chunkpos = chunk.getPos();
        int i = (chunkpos.x << 4) - 2;
        int j = i + 16 + 2;
        int k = (chunkpos.z << 4) - 2;
        int l = k + 16 + 2;
        return this.getScheduledTicks(new StructureBox(i, 0, k, j, 256, l), remove);
    }

    @Override
    public List<ScheduledTick> getScheduledTicks(StructureBox bounds, boolean remove) {
        List<ScheduledTick> list = null;

        for (int i = 0; i < 2; i++) {
            Iterator<ScheduledTick> iterator;
            if (i == 0) {
                iterator = this.scheduledTicksInOrder.iterator();
            } else {
                iterator = this.scheduledTicksThisTick.iterator();
            }

            while (iterator.hasNext()) {
                ScheduledTick scheduledtick = iterator.next();
                BlockPos blockpos = scheduledtick.pos;
                if (blockpos.getX() >= bounds.minX && blockpos.getX() < bounds.maxX && blockpos.getZ() >= bounds.minZ && blockpos.getZ() < bounds.maxZ) {
                    if (remove) {
                        this.scheduledTicks.remove(scheduledtick);
                        iterator.remove();
                    }

                    if (list == null) {
                        list = Lists.newArrayList();
                    }

                    list.add(scheduledtick);
                }
            }
        }

        return list;
    }

    @Override
    public void tickEntity(Entity entity, boolean requireLoaded) {
        if (!this.shouldSpawnAnimals() && (entity instanceof AnimalEntity || entity instanceof WaterMobEntity)) {
            entity.remove();
        }

        if (!this.shouldSpawnNpcs() && entity instanceof Npc) {
            entity.remove();
        }

        super.tickEntity(entity, requireLoaded);
    }

    private boolean shouldSpawnNpcs() {
        return this.server.shouldSpawnNpcs();
    }

    private boolean shouldSpawnAnimals() {
        return this.server.shouldSpawnAnimals();
    }

    @Override
    protected ChunkSource createChunkCache() {
        ChunkStorage chunkstorage = this.storage.getChunkStorage(this.dimension);
        this.chunkCache = new ServerChunkCache(this, chunkstorage, this.dimension.createChunkGenerator());
        return this.chunkCache;
    }

    public List<BlockEntity> getBlockEntities(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<BlockEntity> list = Lists.newArrayList();

        for (int i = 0; i < this.blockEntities.size(); i++) {
            BlockEntity blockentity = this.blockEntities.get(i);
            BlockPos blockpos = blockentity.getPos();
            if (blockpos.getX() >= minX
                && blockpos.getY() >= minY
                && blockpos.getZ() >= minZ
                && blockpos.getX() < maxX
                && blockpos.getY() < maxY
                && blockpos.getZ() < maxZ) {
                list.add(blockentity);
            }
        }

        return list;
    }

    @Override
    public boolean canModify(PlayerEntity player, BlockPos pos) {
        return !this.server.isSpawnProtected(this, pos, player) && this.getWorldBorder().contains(pos);
    }

    @Override
    public void init(WorldSettings settings) {
        if (!this.data.isInitialized()) {
            try {
                this.initSpawnPoint(settings);
                if (this.data.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
                    this.initDebugWorld();
                }

                super.init(settings);
            } catch (Throwable throwable1) {
                CrashReport crashreport = CrashReport.of(throwable1, "Exception initializing level");

                try {
                    this.populateCrashReport(crashreport);
                } catch (Throwable throwable) {
                }

                throw new CrashException(crashreport);
            }

            this.data.setInitialized(true);
        }
    }

    private void initDebugWorld() {
        this.data.setAllowStructures(false);
        this.data.setAllowCommands(true);
        this.data.setRaining(false);
        this.data.setThundering(false);
        this.data.setClearWeatherTime(1000000000);
        this.data.setTimeOfDay(6000L);
        this.data.setDefaultGameMode(WorldSettings.GameMode.SPECTATOR);
        this.data.setHardcore(false);
        this.data.setDifficulty(Difficulty.PEACEFUL);
        this.data.setDifficultyLocked(true);
        this.getGameRules().set("doDaylightCycle", "false");
    }

    private void initSpawnPoint(WorldSettings settings) {
        if (!this.dimension.hasSpawnPoint()) {
            this.data.setSpawnPoint(BlockPos.ORIGIN.up(this.dimension.getMinSpawnY()));
        } else if (this.data.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            this.data.setSpawnPoint(BlockPos.ORIGIN.up());
        } else {
            this.searchingSpawnPoint = true;
            BiomeSource biomesource = this.dimension.getBiomeSource();
            List<Biome> list = biomesource.getBiomesForSpawnPoint();
            Random random = new Random(this.getSeed());
            BlockPos blockpos = biomesource.findBiome(0, 0, 256, list, random);
            int i = 0;
            int j = this.dimension.getMinSpawnY();
            int k = 0;
            if (blockpos != null) {
                i = blockpos.getX();
                k = blockpos.getZ();
            } else {
                LOGGER.warn("Unable to find spawn biome");
            }

            int l = 0;

            while (!this.dimension.isValidSpawnPoint(i, k)) {
                i += random.nextInt(64) - random.nextInt(64);
                k += random.nextInt(64) - random.nextInt(64);
                if (++l == 1000) {
                    break;
                }
            }

            this.data.setSpawnPoint(new BlockPos(i, j, k));
            this.searchingSpawnPoint = false;
            if (settings.generateBonusChest()) {
                this.placeBonusChest();
            }
        }
    }

    protected void placeBonusChest() {
        BonusChestFeature bonuschestfeature = new BonusChestFeature(BONUS_CHEST_LOOT_ENTRIES, 10);

        for (int i = 0; i < 10; i++) {
            int j = this.data.getSpawnX() + this.random.nextInt(6) - this.random.nextInt(6);
            int k = this.data.getSpawnZ() + this.random.nextInt(6) - this.random.nextInt(6);
            BlockPos blockpos = this.getSurfaceHeight(new BlockPos(j, 0, k)).up();
            if (bonuschestfeature.place(this, this.random, blockpos)) {
                break;
            }
        }
    }

    public BlockPos getForcedSpawnPoint() {
        return this.dimension.getForcedSpawnPoint();
    }

    public void save(boolean saveEntities, ProgressListener listener) throws SessionLockException {
        if (this.chunkSource.shouldSave()) {
            if (listener != null) {
                listener.progressStartNoAbort("Saving level");
            }

            this.saveData();
            if (listener != null) {
                listener.progressStage("Saving chunks");
            }

            this.chunkSource.save(saveEntities, listener);

            for (WorldChunk worldchunk : Lists.newArrayList(this.chunkCache.getChunks())) {
                if (worldchunk != null && !this.chunkMap.hasChunk(worldchunk.chunkX, worldchunk.chunkZ)) {
                    this.chunkCache.unloadChunk(worldchunk.chunkX, worldchunk.chunkZ);
                }
            }
        }
    }

    public void flushChunks() {
        if (this.chunkSource.shouldSave()) {
            this.chunkSource.flush();
        }
    }

    protected void saveData() throws SessionLockException {
        this.checkSessionLock();
        this.data.setBorderSize(this.getWorldBorder().getLerpSize());
        this.data.setBorderCenterX(this.getWorldBorder().getCenterX());
        this.data.setBorderCenterZ(this.getWorldBorder().getCenterZ());
        this.data.setBorderSafeZone(this.getWorldBorder().getSafeZone());
        this.data.setBorderDamagePerBlock(this.getWorldBorder().getDamagePerBlock());
        this.data.setBorderWarningBlocks(this.getWorldBorder().getWarningDistance());
        this.data.setBorderWarningTime(this.getWorldBorder().getWarningTime());
        this.data.setBorderSizeLerpTarget(this.getWorldBorder().getSizeLerpTarget());
        this.data.setBorderSizeLerpTime(this.getWorldBorder().getLerpTime());
        this.storage.saveData(this.data, this.server.getPlayerManager().getSingleplayerData());
        this.savedDataStorage.save();
    }

    @Override
    protected void notifyEntityAdded(Entity entity) {
        super.notifyEntityAdded(entity);
        this.entitiesById.put(entity.getNetworkId(), entity);
        this.entitiesByUuid.put(entity.getUuid(), entity);
        Entity[] aentity = entity.getParts();
        if (aentity != null) {
            for (int i = 0; i < aentity.length; i++) {
                this.entitiesById.put(aentity[i].getNetworkId(), aentity[i]);
            }
        }
    }

    @Override
    protected void notifyEntityRemoved(Entity entity) {
        super.notifyEntityRemoved(entity);
        this.entitiesById.remove(entity.getNetworkId());
        this.entitiesByUuid.remove(entity.getUuid());
        Entity[] aentity = entity.getParts();
        if (aentity != null) {
            for (int i = 0; i < aentity.length; i++) {
                this.entitiesById.remove(aentity[i].getNetworkId());
            }
        }
    }

    @Override
    public boolean addGlobalEntity(Entity entity) {
        if (super.addGlobalEntity(entity)) {
            this.server.getPlayerManager().sendPacket(entity.x, entity.y, entity.z, 512.0, this.dimension.getId(), new AddGlobalEntityS2CPacket(entity));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void doEntityEvent(Entity entity, byte event) {
        this.getEntityMap().sendPacketToAll(entity, new EntityEventS2CPacket(entity, event));
    }

    @Override
    public Explosion explode(Entity source, double x, double y, double z, float power, boolean createFire, boolean destructive) {
        Explosion explosion = new Explosion(this, source, x, y, z, power, createFire, destructive);
        explosion.damageEntities();
        explosion.damageBlocks(false);
        if (!destructive) {
            explosion.clearDamagedBlocks();
        }

        for (PlayerEntity playerentity : this.players) {
            if (playerentity.squaredDistanceTo(x, y, z) < 4096.0) {
                ((ServerPlayerEntity)playerentity)
                    .networkHandler
                    .sendPacket(new ExplosionS2CPacket(x, y, z, power, explosion.getDamagedBlocks(), explosion.getDamagedPlayers().get(playerentity)));
            }
        }

        return explosion;
    }

    @Override
    public void addBlockEvent(BlockPos pos, Block block, int type, int data) {
        BlockEvent blockevent = new BlockEvent(pos, block, type, data);

        for (BlockEvent blockevent1 : this.blockEvents[this.nextBlockEventQueueIndex]) {
            if (blockevent1.equals(blockevent)) {
                return;
            }
        }

        this.blockEvents[this.nextBlockEventQueueIndex].add(blockevent);
    }

    private void doBlockEvents() {
        while (!this.blockEvents[this.nextBlockEventQueueIndex].isEmpty()) {
            int i = this.nextBlockEventQueueIndex;
            this.nextBlockEventQueueIndex ^= 1;

            for (BlockEvent blockevent : this.blockEvents[i]) {
                if (this.doBlockEvent(blockevent)) {
                    this.server
                        .getPlayerManager()
                        .sendPacket(
                            blockevent.getPos().getX(),
                            blockevent.getPos().getY(),
                            blockevent.getPos().getZ(),
                            64.0,
                            this.dimension.getId(),
                            new BlockEventS2CPacket(blockevent.getPos(), blockevent.getBlock(), blockevent.getType(), blockevent.getData())
                        );
                }
            }

            this.blockEvents[i].clear();
        }
    }

    private boolean doBlockEvent(BlockEvent blockEvent) {
        BlockState blockstate = this.getBlockState(blockEvent.getPos());
        return blockstate.getBlock() == blockEvent.getBlock()
            && blockstate.getBlock().doEvent(this, blockEvent.getPos(), blockstate, blockEvent.getType(), blockEvent.getData());
    }

    public void forceSave() {
        this.storage.forceSave();
    }

    @Override
    protected void tickWeather() {
        boolean flag = this.isRaining();
        super.tickWeather();
        if (this.prevRain != this.rain) {
            this.server.getPlayerManager().sendPacket(new GameEventS2CPacket(7, this.rain), this.dimension.getId());
        }

        if (this.prevThunder != this.thunder) {
            this.server.getPlayerManager().sendPacket(new GameEventS2CPacket(8, this.thunder), this.dimension.getId());
        }

        if (flag != this.isRaining()) {
            if (flag) {
                this.server.getPlayerManager().sendPacket(new GameEventS2CPacket(2, 0.0F));
            } else {
                this.server.getPlayerManager().sendPacket(new GameEventS2CPacket(1, 0.0F));
            }

            this.server.getPlayerManager().sendPacket(new GameEventS2CPacket(7, this.rain));
            this.server.getPlayerManager().sendPacket(new GameEventS2CPacket(8, this.thunder));
        }
    }

    @Override
    protected int getChunkViewDistance() {
        return this.server.getPlayerManager().getChunkViewDistance();
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public EntityMap getEntityMap() {
        return this.entityMap;
    }

    public ChunkMap getChunkMap() {
        return this.chunkMap;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public void addParticle(
        ParticleType type,
        double x,
        double y,
        double z,
        int count,
        double velocityX,
        double velocityY,
        double velocityZ,
        double velocityScale,
        int... parameters
    ) {
        this.addParticle(type, false, x, y, z, count, velocityX, velocityY, velocityZ, velocityScale, parameters);
    }

    public void addParticle(
        ParticleType type,
        boolean ignoreDistance,
        double x,
        double y,
        double z,
        int count,
        double velocityX,
        double velocityY,
        double velocityZ,
        double velocityScale,
        int... parameters
    ) {
        Packet packet = new ParticleS2CPacket(
            type, ignoreDistance, (float)x, (float)y, (float)z, (float)velocityX, (float)velocityY, (float)velocityZ, (float)velocityScale, count, parameters
        );

        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)this.players.get(i);
            BlockPos blockpos = serverplayerentity.getCommandSourceBlockPos();
            double d0 = blockpos.squaredDistanceTo(x, y, z);
            if (d0 <= 256.0 || ignoreDistance && d0 <= 65536.0) {
                serverplayerentity.networkHandler.sendPacket(packet);
            }
        }
    }

    public Entity getEntity(UUID uuid) {
        return this.entitiesByUuid.get(uuid);
    }

    @Override
    public ListenableFuture<Object> execute(Runnable task) {
        return this.server.execute(task);
    }

    @Override
    public boolean isOnSameThread() {
        return this.server.isOnSameThread();
    }

    static class BlockEventQueue extends ArrayList<BlockEvent> {
        private BlockEventQueue() {
        }
    }
}
