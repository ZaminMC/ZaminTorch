package net.minecraft.world;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowLayerBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ScheduledTick;
import net.minecraft.util.Int2ObjectHashMap;
import net.minecraft.util.Tickable;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.gen.structure.StructureBox;
import net.minecraft.world.saveddata.SavedData;
import net.minecraft.world.storage.SavedDataStorage;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.exception.SessionLockException;
import net.minecraft.world.village.SavedVillageData;

public abstract class World implements WorldView {
    private int seaLevel = 63;
    /**
     * If true, block ticks will be executed immediately instead of being scheduled.
     */
    protected boolean doTicksImmediately;
    public final List<Entity> entities = Lists.newArrayList();
    protected final List<Entity> entitiesToRemove = Lists.newArrayList();
    public final List<BlockEntity> blockEntities = Lists.newArrayList();
    public final List<BlockEntity> tickingBlockEntities = Lists.newArrayList();
    private final List<BlockEntity> pendingBlockEntities = Lists.newArrayList();
    private final List<BlockEntity> removedBlockEntities = Lists.newArrayList();
    public final List<PlayerEntity> players = Lists.newArrayList();
    public final List<Entity> globalEntities = Lists.newArrayList();
    protected final Int2ObjectHashMap<Entity> entitiesById = new Int2ObjectHashMap<>();
    private long cloudColor = 16777215L;
    private int ambientDarkness;
    protected int randomTickLCG = new Random().nextInt();
    protected final int randomTickLCGIncrement = 1013904223;
    protected float prevRain;
    protected float rain;
    protected float prevThunder;
    protected float thunder;
    private int lightningCooldown;
    public final Random random = new Random();
    public final Dimension dimension;
    protected List<WorldEventListener> eventListeners = Lists.newArrayList();
    protected ChunkSource chunkSource;
    protected final WorldStorage storage;
    protected WorldData data;
    protected boolean searchingSpawnPoint;
    protected SavedDataStorage savedDataStorage;
    protected SavedVillageData villages;
    public final Profiler profiler;
    private final Calendar calendar = Calendar.getInstance();
    protected Scoreboard scoreboard = new Scoreboard();
    public final boolean isClient;
    protected Set<ChunkPos> tickingChunks = Sets.newHashSet();
    private int ambientSoundCooldown = this.random.nextInt(12000);
    protected boolean spawnAnimals = true;
    protected boolean spawnMonsters = true;
    private boolean isTickingBlockEntities;
    private final WorldBorder worldBorder;
    int[] lightPropagation = new int[32768];

    protected World(WorldStorage storage, WorldData data, Dimension dimension, Profiler profiler, boolean isClient) {
        this.storage = storage;
        this.profiler = profiler;
        this.data = data;
        this.dimension = dimension;
        this.isClient = isClient;
        this.worldBorder = dimension.createWorldBorder();
    }

    public World load() {
        return this;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        if (this.isChunkLoaded(pos)) {
            WorldChunk worldchunk = this.getChunk(pos);

            try {
                return worldchunk.getBiome(pos, this.dimension.getBiomeSource());
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Getting biome");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Coordinates of biome request");
                crashreportcategory.add("Location", new Callable<String>() {
                    public String call() throws Exception {
                        return CrashReportCategory.formatPosition(pos);
                    }
                });
                throw new CrashException(crashreport);
            }
        } else {
            return this.dimension.getBiomeSource().getBiome(pos, Biome.PLAINS);
        }
    }

    public BiomeSource getBiomeSource() {
        return this.dimension.getBiomeSource();
    }

    protected abstract ChunkSource createChunkCache();

    public void init(WorldSettings settings) {
        this.data.setInitialized(true);
    }

    public void resetSpawnPoint() {
        this.setSpawnPoint(new BlockPos(8, 64, 8));
    }

    /**
     * Returns the top-most block of the ground above sea-level.
     */
    public Block getSurfaceBlock(BlockPos pos) {
        BlockPos blockpos = new BlockPos(pos.getX(), this.getSeaLevel(), pos.getZ());

        while (!this.isAir(blockpos.up())) {
            blockpos = blockpos.up();
        }

        return this.getBlockState(blockpos).getBlock();
    }

    private boolean contains(BlockPos pos) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000 && pos.getY() >= 0 && pos.getY() < 256;
    }

    @Override
    public boolean isAir(BlockPos pos) {
        return this.getBlockState(pos).getBlock().getMaterial() == Material.AIR;
    }

    public boolean isChunkLoaded(BlockPos pos) {
        return this.isChunkLoaded(pos, true);
    }

    public boolean isChunkLoaded(BlockPos pos, boolean allowEmpty) {
        return this.contains(pos) && this.isChunkLoadedAt(pos.getX() >> 4, pos.getZ() >> 4, allowEmpty);
    }

    public boolean isAreaLoaded(BlockPos pos, int range) {
        return this.isAreaLoaded(pos, range, true);
    }

    public boolean isAreaLoaded(BlockPos pos, int range, boolean allowEmpty) {
        return this.isAreaLoaded(
            pos.getX() - range, pos.getY() - range, pos.getZ() - range, pos.getX() + range, pos.getY() + range, pos.getZ() + range, allowEmpty
        );
    }

    public boolean isAreaLoaded(BlockPos min, BlockPos max) {
        return this.isAreaLoaded(min, max, true);
    }

    public boolean isAreaLoaded(BlockPos min, BlockPos max, boolean allowEmpty) {
        return this.isAreaLoaded(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), allowEmpty);
    }

    public boolean isAreaLoaded(StructureBox bounds) {
        return this.isAreaLoaded(bounds, true);
    }

    public boolean isAreaLoaded(StructureBox bounds, boolean allowEmpty) {
        return this.isAreaLoaded(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ, allowEmpty);
    }

    private boolean isAreaLoaded(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean allowEmpty) {
        if (maxY >= 0 && minY < 256) {
            minX >>= 4;
            minZ >>= 4;
            maxX >>= 4;
            maxZ >>= 4;

            for (int i = minX; i <= maxX; i++) {
                for (int j = minZ; j <= maxZ; j++) {
                    if (!this.isChunkLoadedAt(i, j, allowEmpty)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    protected boolean isChunkLoadedAt(int chunkX, int chunkZ, boolean allowEmpty) {
        return this.chunkSource.hasChunk(chunkX, chunkZ) && (allowEmpty || !this.chunkSource.getChunk(chunkX, chunkZ).isEmpty());
    }

    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunkAt(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public WorldChunk getChunkAt(int chunkX, int chunkZ) {
        return this.chunkSource.getChunk(chunkX, chunkZ);
    }

    public boolean setBlockState(BlockPos pos, BlockState state, int flags) {
        if (!this.contains(pos)) {
            return false;
        }

        if (!this.isClient && this.data.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            return false;
        }

        WorldChunk worldchunk = this.getChunk(pos);
        Block block = state.getBlock();
        BlockState blockstate = worldchunk.setBlockState(pos, state);
        if (blockstate == null) {
            return false;
        }

        Block block1 = blockstate.getBlock();
        if (block.getOpacity() != block1.getOpacity() || block.getLight() != block1.getLight()) {
            this.profiler.push("checkLight");
            this.checkLight(pos);
            this.profiler.pop();
        }

        if ((flags & 2) != 0 && (!this.isClient || (flags & 4) == 0) && worldchunk.isPopulated()) {
            this.notifyBlockChanged(pos);
        }

        if (!this.isClient && (flags & 1) != 0) {
            this.onBlockChanged(pos, blockstate.getBlock());
            if (block.isAnalogSignalSource()) {
                this.updateNeighborComparators(pos, block);
            }
        }

        return true;
    }

    public boolean removeBlock(BlockPos pos) {
        return this.setBlockState(pos, Blocks.AIR.defaultState(), 3);
    }

    public boolean breakBlock(BlockPos pos, boolean dropItems) {
        BlockState blockstate = this.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (block.getMaterial() == Material.AIR) {
            return false;
        }

        this.doEvent(2001, pos, Block.serialize(blockstate));
        if (dropItems) {
            block.dropItems(this, pos, blockstate, 0);
        }

        return this.setBlockState(pos, Blocks.AIR.defaultState(), 3);
    }

    public boolean setBlockState(BlockPos pos, BlockState state) {
        return this.setBlockState(pos, state, 3);
    }

    public void notifyBlockChanged(BlockPos pos) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).notifyBlockChanged(pos);
        }
    }

    public void onBlockChanged(BlockPos pos, Block block) {
        if (this.data.getGeneratorType() != WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            this.updateNeighbors(pos, block);
        }
    }

    public void onHeightMapChanged(int x, int z, int newHeight, int oldHeight) {
        if (newHeight > oldHeight) {
            int i = oldHeight;
            oldHeight = newHeight;
            newHeight = i;
        }

        if (!this.dimension.hasNoSky()) {
            for (int j = newHeight; j <= oldHeight; j++) {
                this.updateLight(LightType.SKY, new BlockPos(x, j, z));
            }
        }

        this.notifyRegionChanged(x, newHeight, z, x, oldHeight, z);
    }

    public void notifyRegionChanged(BlockPos minPos, BlockPos maxPos) {
        this.notifyRegionChanged(minPos.getX(), minPos.getY(), minPos.getZ(), maxPos.getX(), maxPos.getY(), maxPos.getZ());
    }

    public void notifyRegionChanged(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).notifyRegionChanged(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public void updateNeighbors(BlockPos pos, Block block) {
        this.neighborChanged(pos.west(), block);
        this.neighborChanged(pos.east(), block);
        this.neighborChanged(pos.down(), block);
        this.neighborChanged(pos.up(), block);
        this.neighborChanged(pos.north(), block);
        this.neighborChanged(pos.south(), block);
    }

    public void updateNeighborsExcept(BlockPos pos, Block block, Direction dir) {
        if (dir != Direction.WEST) {
            this.neighborChanged(pos.west(), block);
        }

        if (dir != Direction.EAST) {
            this.neighborChanged(pos.east(), block);
        }

        if (dir != Direction.DOWN) {
            this.neighborChanged(pos.down(), block);
        }

        if (dir != Direction.UP) {
            this.neighborChanged(pos.up(), block);
        }

        if (dir != Direction.NORTH) {
            this.neighborChanged(pos.north(), block);
        }

        if (dir != Direction.SOUTH) {
            this.neighborChanged(pos.south(), block);
        }
    }

    public void neighborChanged(BlockPos pos, Block neighborBlock) {
        if (!this.isClient) {
            BlockState blockstate = this.getBlockState(pos);

            try {
                blockstate.getBlock().neighborChanged(this, pos, blockstate, neighborBlock);
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Exception while updating neighbours");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Block being updated");
                crashreportcategory.add(
                    "Source block type",
                    new Callable<String>() {
                        public String call() throws Exception {
                            try {
                                return String.format(
                                    "ID #%d (%s // %s)",
                                    Block.getId(neighborBlock),
                                    neighborBlock.getTranslationKey(),
                                    neighborBlock.getClass().getCanonicalName()
                                );
                            } catch (Throwable throwable1) {
                                return "ID #" + Block.getId(neighborBlock);
                            }
                        }
                    }
                );
                CrashReportCategory.addBlockDetails(crashreportcategory, pos, blockstate);
                throw new CrashException(crashreport);
            }
        }
    }

    public boolean willTickThisTick(BlockPos pos, Block block) {
        return false;
    }

    public boolean hasSkyAccess(BlockPos pos) {
        return this.getChunk(pos).hasSkyAccess(pos);
    }

    public boolean hasSkyAccessIgnoreLiquids(BlockPos pos) {
        if (pos.getY() >= this.getSeaLevel()) {
            return this.hasSkyAccess(pos);
        }

        BlockPos blockpos = new BlockPos(pos.getX(), this.getSeaLevel(), pos.getZ());
        if (!this.hasSkyAccess(blockpos)) {
            return false;
        }

        for (BlockPos blockpos1 = blockpos.down(); blockpos1.getY() > pos.getY(); blockpos1 = blockpos1.down()) {
            Block block = this.getBlockState(blockpos1).getBlock();
            if (block.getOpacity() > 0 && !block.getMaterial().isLiquid()) {
                return false;
            }
        }

        return true;
    }

    public int getActualLight(BlockPos pos) {
        if (pos.getY() < 0) {
            return 0;
        }

        if (pos.getY() >= 256) {
            pos = new BlockPos(pos.getX(), 255, pos.getZ());
        }

        return this.getChunk(pos).getLight(pos, 0);
    }

    public int getRawBrightness(BlockPos pos) {
        return this.getRawBrightness(pos, true);
    }

    public int getRawBrightness(BlockPos pos, boolean useNeighborLight) {
        if (pos.getX() < -30000000 || pos.getZ() < -30000000 || pos.getX() >= 30000000 || pos.getZ() >= 30000000) {
            return 15;
        }

        if (useNeighborLight && this.getBlockState(pos).getBlock().usesNeighborLight()) {
            int i1 = this.getRawBrightness(pos.up(), false);
            int i = this.getRawBrightness(pos.east(), false);
            int j = this.getRawBrightness(pos.west(), false);
            int k = this.getRawBrightness(pos.south(), false);
            int l = this.getRawBrightness(pos.north(), false);
            if (i > i1) {
                i1 = i;
            }

            if (j > i1) {
                i1 = j;
            }

            if (k > i1) {
                i1 = k;
            }

            if (l > i1) {
                i1 = l;
            }

            return i1;
        } else {
            if (pos.getY() < 0) {
                return 0;
            }

            if (pos.getY() >= 256) {
                pos = new BlockPos(pos.getX(), 255, pos.getZ());
            }

            WorldChunk worldchunk = this.getChunk(pos);
            return worldchunk.getLight(pos, this.ambientDarkness);
        }
    }

    public BlockPos getHeight(BlockPos pos) {
        int i;
        if (pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000) {
            if (this.isChunkLoadedAt(pos.getX() >> 4, pos.getZ() >> 4, true)) {
                i = this.getChunkAt(pos.getX() >> 4, pos.getZ() >> 4).getHeight(pos.getX() & 15, pos.getZ() & 15);
            } else {
                i = 0;
            }
        } else {
            i = this.getSeaLevel() + 1;
        }

        return new BlockPos(pos.getX(), i, pos.getZ());
    }

    public int getLowestHeight(int x, int z) {
        if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
            if (!this.isChunkLoadedAt(x >> 4, z >> 4, true)) {
                return 0;
            }

            WorldChunk worldchunk = this.getChunkAt(x >> 4, z >> 4);
            return worldchunk.getLowestHeight();
        } else {
            return this.getSeaLevel() + 1;
        }
    }

    public int getBrightness(LightType type, BlockPos pos) {
        if (this.dimension.hasNoSky() && type == LightType.SKY) {
            return 0;
        }

        if (pos.getY() < 0) {
            pos = new BlockPos(pos.getX(), 0, pos.getZ());
        }

        if (!this.contains(pos)) {
            return type.defaultValue;
        }

        if (!this.isChunkLoaded(pos)) {
            return type.defaultValue;
        }

        if (this.getBlockState(pos).getBlock().usesNeighborLight()) {
            int i1 = this.getLight(type, pos.up());
            int i = this.getLight(type, pos.east());
            int j = this.getLight(type, pos.west());
            int k = this.getLight(type, pos.south());
            int l = this.getLight(type, pos.north());
            if (i > i1) {
                i1 = i;
            }

            if (j > i1) {
                i1 = j;
            }

            if (k > i1) {
                i1 = k;
            }

            if (l > i1) {
                i1 = l;
            }

            return i1;
        } else {
            WorldChunk worldchunk = this.getChunk(pos);
            return worldchunk.getLight(type, pos);
        }
    }

    public int getLight(LightType type, BlockPos pos) {
        if (pos.getY() < 0) {
            pos = new BlockPos(pos.getX(), 0, pos.getZ());
        }

        if (!this.contains(pos)) {
            return type.defaultValue;
        }

        if (!this.isChunkLoaded(pos)) {
            return type.defaultValue;
        }

        WorldChunk worldchunk = this.getChunk(pos);
        return worldchunk.getLight(type, pos);
    }

    public void setLight(LightType type, BlockPos pos, int light) {
        if (this.contains(pos)) {
            if (this.isChunkLoaded(pos)) {
                WorldChunk worldchunk = this.getChunk(pos);
                worldchunk.setLight(type, pos, light);
                this.notifyLightChanged(pos);
            }
        }
    }

    public void notifyLightChanged(BlockPos pos) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).notifyLightChanged(pos);
        }
    }

    @Override
    public int getLightColor(BlockPos pos, int blockLight) {
        int i = this.getBrightness(LightType.SKY, pos);
        int j = this.getBrightness(LightType.BLOCK, pos);
        if (j < blockLight) {
            j = blockLight;
        }

        return i << 20 | j << 4;
    }

    public float getBrightness(BlockPos pos) {
        return this.dimension.getBrightnessTable()[this.getRawBrightness(pos)];
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (!this.contains(pos)) {
            return Blocks.AIR.defaultState();
        }

        WorldChunk worldchunk = this.getChunk(pos);
        return worldchunk.getBlockState(pos);
    }

    public boolean isSunny() {
        return this.ambientDarkness < 4;
    }

    public HitResult rayTrace(Vec3d from, Vec3d to) {
        return this.rayTrace(from, to, false, false, false);
    }

    public HitResult rayTrace(Vec3d from, Vec3d to, boolean allowLiquids) {
        return this.rayTrace(from, to, allowLiquids, false, false);
    }

    public HitResult rayTrace(Vec3d from, Vec3d to, boolean allowLiquids, boolean ignoreBlocksWithoutCollision, boolean allowHitInStartPosition) {
        if (Double.isNaN(from.x) || Double.isNaN(from.y) || Double.isNaN(from.z)) {
            return null;
        }

        if (!Double.isNaN(to.x) && !Double.isNaN(to.y) && !Double.isNaN(to.z)) {
            int i = MathHelper.floor(to.x);
            int j = MathHelper.floor(to.y);
            int k = MathHelper.floor(to.z);
            int l = MathHelper.floor(from.x);
            int i1 = MathHelper.floor(from.y);
            int j1 = MathHelper.floor(from.z);
            BlockPos blockpos = new BlockPos(l, i1, j1);
            BlockState blockstate = this.getBlockState(blockpos);
            Block block = blockstate.getBlock();
            if ((!ignoreBlocksWithoutCollision || block.getCollisionShape(this, blockpos, blockstate) != null) && block.canRayTrace(blockstate, allowLiquids)) {
                HitResult hitresult = block.rayTrace(this, blockpos, from, to);
                if (hitresult != null) {
                    return hitresult;
                }
            }

            HitResult hitresult2 = null;
            int k1 = 200;

            while (k1-- >= 0) {
                if (Double.isNaN(from.x) || Double.isNaN(from.y) || Double.isNaN(from.z)) {
                    return null;
                }

                if (l == i && i1 == j && j1 == k) {
                    return allowHitInStartPosition ? hitresult2 : null;
                }

                boolean flag2 = true;
                boolean flag = true;
                boolean flag1 = true;
                double d0 = 999.0;
                double d1 = 999.0;
                double d2 = 999.0;
                if (i > l) {
                    d0 = l + 1.0;
                } else if (i < l) {
                    d0 = l + 0.0;
                } else {
                    flag2 = false;
                }

                if (j > i1) {
                    d1 = i1 + 1.0;
                } else if (j < i1) {
                    d1 = i1 + 0.0;
                } else {
                    flag = false;
                }

                if (k > j1) {
                    d2 = j1 + 1.0;
                } else if (k < j1) {
                    d2 = j1 + 0.0;
                } else {
                    flag1 = false;
                }

                double d3 = 999.0;
                double d4 = 999.0;
                double d5 = 999.0;
                double d6 = to.x - from.x;
                double d7 = to.y - from.y;
                double d8 = to.z - from.z;
                if (flag2) {
                    d3 = (d0 - from.x) / d6;
                }

                if (flag) {
                    d4 = (d1 - from.y) / d7;
                }

                if (flag1) {
                    d5 = (d2 - from.z) / d8;
                }

                if (d3 == -0.0) {
                    d3 = -1.0E-4;
                }

                if (d4 == -0.0) {
                    d4 = -1.0E-4;
                }

                if (d5 == -0.0) {
                    d5 = -1.0E-4;
                }

                Direction direction;
                if (d3 < d4 && d3 < d5) {
                    direction = i > l ? Direction.WEST : Direction.EAST;
                    from = new Vec3d(d0, from.y + d7 * d3, from.z + d8 * d3);
                } else if (d4 < d5) {
                    direction = j > i1 ? Direction.DOWN : Direction.UP;
                    from = new Vec3d(from.x + d6 * d4, d1, from.z + d8 * d4);
                } else {
                    direction = k > j1 ? Direction.NORTH : Direction.SOUTH;
                    from = new Vec3d(from.x + d6 * d5, from.y + d7 * d5, d2);
                }

                l = MathHelper.floor(from.x) - (direction == Direction.EAST ? 1 : 0);
                i1 = MathHelper.floor(from.y) - (direction == Direction.UP ? 1 : 0);
                j1 = MathHelper.floor(from.z) - (direction == Direction.SOUTH ? 1 : 0);
                blockpos = new BlockPos(l, i1, j1);
                BlockState blockstate1 = this.getBlockState(blockpos);
                Block block1 = blockstate1.getBlock();
                if (!ignoreBlocksWithoutCollision || block1.getCollisionShape(this, blockpos, blockstate1) != null) {
                    if (block1.canRayTrace(blockstate1, allowLiquids)) {
                        HitResult hitresult1 = block1.rayTrace(this, blockpos, from, to);
                        if (hitresult1 != null) {
                            return hitresult1;
                        }
                    } else {
                        hitresult2 = new HitResult(HitResult.Type.MISS, from, direction, blockpos);
                    }
                }
            }

            return allowHitInStartPosition ? hitresult2 : null;
        } else {
            return null;
        }
    }

    public void playSound(Entity source, String sound, float volume, float pitch) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).playSound(sound, source.x, source.y, source.z, volume, pitch);
        }
    }

    public void playSound(PlayerEntity source, String sound, float volume, float pitch) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).playSound(source, sound, source.x, source.y, source.z, volume, pitch);
        }
    }

    public void playSound(double x, double y, double z, String sound, float volume, float pitch) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).playSound(sound, x, y, z, volume, pitch);
        }
    }

    public void playSound(double x, double y, double z, String sound, float volume, float pitch, boolean ignoreDistance) {
    }

    public void playRecordMusic(BlockPos pos, String record) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).playRecordMusic(record, pos);
        }
    }

    public void addParticle(ParticleType type, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
        this.addParticle(type.getId(), type.ignoreDistance(), x, y, z, velocityX, velocityY, velocityZ, parameters);
    }

    public void addParticle(
        ParticleType type, boolean ignoreDistance, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters
    ) {
        this.addParticle(type.getId(), type.ignoreDistance() | ignoreDistance, x, y, z, velocityX, velocityY, velocityZ, parameters);
    }

    private void addParticle(
        int type, boolean ignoreDistance, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters
    ) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).addParticle(type, ignoreDistance, x, y, z, velocityX, velocityY, velocityZ, parameters);
        }
    }

    public boolean addGlobalEntity(Entity entity) {
        this.globalEntities.add(entity);
        return true;
    }

    public boolean addEntity(Entity entity) {
        int i = MathHelper.floor(entity.x / 16.0);
        int j = MathHelper.floor(entity.z / 16.0);
        boolean flag = entity.teleporting;
        if (entity instanceof PlayerEntity) {
            flag = true;
        }

        if (!flag && !this.isChunkLoadedAt(i, j, true)) {
            return false;
        }

        if (entity instanceof PlayerEntity) {
            PlayerEntity playerentity = (PlayerEntity)entity;
            this.players.add(playerentity);
            this.updatePlayersSleepingStatus();
        }

        this.getChunkAt(i, j).addEntity(entity);
        this.entities.add(entity);
        this.notifyEntityAdded(entity);
        return true;
    }

    protected void notifyEntityAdded(Entity entity) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).notifyEntityAdded(entity);
        }
    }

    protected void notifyEntityRemoved(Entity entity) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).notifyEntityRemoved(entity);
        }
    }

    public void removeEntity(Entity entity) {
        if (entity.rider != null) {
            entity.rider.startRiding(null);
        }

        if (entity.vehicle != null) {
            entity.startRiding(null);
        }

        entity.remove();
        if (entity instanceof PlayerEntity) {
            this.players.remove(entity);
            this.updatePlayersSleepingStatus();
            this.notifyEntityRemoved(entity);
        }
    }

    public void removeEntityNow(Entity entity) {
        entity.remove();
        if (entity instanceof PlayerEntity) {
            this.players.remove(entity);
            this.updatePlayersSleepingStatus();
        }

        int i = entity.chunkX;
        int j = entity.chunkZ;
        if (entity.inChunk && this.isChunkLoadedAt(i, j, true)) {
            this.getChunkAt(i, j).removeEntity(entity);
        }

        this.entities.remove(entity);
        this.notifyEntityRemoved(entity);
    }

    public void addEventListener(WorldEventListener listener) {
        this.eventListeners.add(listener);
    }

    public void removeEventListener(WorldEventListener listener) {
        this.eventListeners.remove(listener);
    }

    public List<Box> getCollisions(Entity entity, Box shape) {
        List<Box> list = Lists.newArrayList();
        int i = MathHelper.floor(shape.minX);
        int j = MathHelper.floor(shape.maxX + 1.0);
        int k = MathHelper.floor(shape.minY);
        int l = MathHelper.floor(shape.maxY + 1.0);
        int i1 = MathHelper.floor(shape.minZ);
        int j1 = MathHelper.floor(shape.maxZ + 1.0);
        WorldBorder worldborder = this.getWorldBorder();
        boolean flag = entity.isOutsideWorldBorder();
        boolean flag1 = this.isWithinBorder(worldborder, entity);
        BlockState blockstate = Blocks.STONE.defaultState();
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 < j; k1++) {
            for (int l1 = i1; l1 < j1; l1++) {
                if (this.isChunkLoaded(blockpos$mutable.set(k1, 64, l1))) {
                    for (int i2 = k - 1; i2 < l; i2++) {
                        blockpos$mutable.set(k1, i2, l1);
                        if (flag && flag1) {
                            entity.setOutsideWorldBorder(false);
                        } else if (!flag && !flag1) {
                            entity.setOutsideWorldBorder(true);
                        }

                        BlockState blockstate1 = blockstate;
                        if (worldborder.contains(blockpos$mutable) || !flag1) {
                            blockstate1 = this.getBlockState(blockpos$mutable);
                        }

                        blockstate1.getBlock().addCollisions(this, blockpos$mutable, blockstate1, shape, list, entity);
                    }
                }
            }
        }

        double d0 = 0.25;
        List<Entity> list1 = this.getEntities(entity, shape.grown(d0, d0, d0));

        for (int j2 = 0; j2 < list1.size(); j2++) {
            if (entity.rider != list1 && entity.vehicle != list1) {
                Box box = list1.get(j2).getCollisionShape();
                if (box != null && box.intersects(shape)) {
                    list.add(box);
                }

                box = entity.getCollisionAgainstShape(list1.get(j2));
                if (box != null && box.intersects(shape)) {
                    list.add(box);
                }
            }
        }

        return list;
    }

    public boolean isWithinBorder(WorldBorder border, Entity entity) {
        double d0 = border.getMinX();
        double d1 = border.getMinZ();
        double d2 = border.getMaxX();
        double d3 = border.getMaxZ();
        if (entity.isOutsideWorldBorder()) {
            d0++;
            d1++;
            d2--;
            d3--;
        } else {
            d0--;
            d1--;
            d2++;
            d3++;
        }

        return entity.x > d0 && entity.x < d2 && entity.z > d1 && entity.z < d3;
    }

    public List<Box> getBlockCollisions(Box shape) {
        List<Box> list = Lists.newArrayList();
        int i = MathHelper.floor(shape.minX);
        int j = MathHelper.floor(shape.maxX + 1.0);
        int k = MathHelper.floor(shape.minY);
        int l = MathHelper.floor(shape.maxY + 1.0);
        int i1 = MathHelper.floor(shape.minZ);
        int j1 = MathHelper.floor(shape.maxZ + 1.0);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 < j; k1++) {
            for (int l1 = i1; l1 < j1; l1++) {
                if (this.isChunkLoaded(blockpos$mutable.set(k1, 64, l1))) {
                    for (int i2 = k - 1; i2 < l; i2++) {
                        blockpos$mutable.set(k1, i2, l1);
                        BlockState blockstate;
                        if (k1 >= -30000000 && k1 < 30000000 && l1 >= -30000000 && l1 < 30000000) {
                            blockstate = this.getBlockState(blockpos$mutable);
                        } else {
                            blockstate = Blocks.BEDROCK.defaultState();
                        }

                        blockstate.getBlock().addCollisions(this, blockpos$mutable, blockstate, shape, list, null);
                    }
                }
            }
        }

        return list;
    }

    public int calculateAmbientDarkness(float tickDelta) {
        float f = this.getTimeOfDay(tickDelta);
        float f1 = 1.0F - (MathHelper.cos(f * (float) Math.PI * 2.0F) * 2.0F + 0.5F);
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 = (float)(f1 * (1.0 - this.getRain(tickDelta) * 5.0F / 16.0));
        f1 = (float)(f1 * (1.0 - this.getThunder(tickDelta) * 5.0F / 16.0));
        f1 = 1.0F - f1;
        return (int)(f1 * 11.0F);
    }

    public float calculateAmbientLight(float tickDelta) {
        float f = this.getTimeOfDay(tickDelta);
        float f1 = 1.0F - (MathHelper.cos(f * (float) Math.PI * 2.0F) * 2.0F + 0.2F);
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 = (float)(f1 * (1.0 - this.getRain(tickDelta) * 5.0F / 16.0));
        f1 = (float)(f1 * (1.0 - this.getThunder(tickDelta) * 5.0F / 16.0));
        return f1 * 0.8F + 0.2F;
    }

    public Vec3d getSkyColor(Entity entity, float tickDelta) {
        float f = this.getTimeOfDay(tickDelta);
        float f1 = MathHelper.cos(f * (float) Math.PI * 2.0F) * 2.0F + 0.5F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        int i = MathHelper.floor(entity.x);
        int j = MathHelper.floor(entity.y);
        int k = MathHelper.floor(entity.z);
        BlockPos blockpos = new BlockPos(i, j, k);
        Biome biome = this.getBiome(blockpos);
        float f2 = biome.getTemperature(blockpos);
        int l = biome.getSkyColor(f2);
        float f3 = (l >> 16 & 0xFF) / 255.0F;
        float f4 = (l >> 8 & 0xFF) / 255.0F;
        float f5 = (l & 0xFF) / 255.0F;
        f3 *= f1;
        f4 *= f1;
        f5 *= f1;
        float f6 = this.getRain(tickDelta);
        if (f6 > 0.0F) {
            float f7 = (f3 * 0.3F + f4 * 0.59F + f5 * 0.11F) * 0.6F;
            float f8 = 1.0F - f6 * 0.75F;
            f3 = f3 * f8 + f7 * (1.0F - f8);
            f4 = f4 * f8 + f7 * (1.0F - f8);
            f5 = f5 * f8 + f7 * (1.0F - f8);
        }

        float f10 = this.getThunder(tickDelta);
        if (f10 > 0.0F) {
            float f11 = (f3 * 0.3F + f4 * 0.59F + f5 * 0.11F) * 0.2F;
            float f9 = 1.0F - f10 * 0.75F;
            f3 = f3 * f9 + f11 * (1.0F - f9);
            f4 = f4 * f9 + f11 * (1.0F - f9);
            f5 = f5 * f9 + f11 * (1.0F - f9);
        }

        if (this.lightningCooldown > 0) {
            float f12 = this.lightningCooldown - tickDelta;
            if (f12 > 1.0F) {
                f12 = 1.0F;
            }

            f12 *= 0.45F;
            f3 = f3 * (1.0F - f12) + 0.8F * f12;
            f4 = f4 * (1.0F - f12) + 0.8F * f12;
            f5 = f5 * (1.0F - f12) + 1.0F * f12;
        }

        return new Vec3d(f3, f4, f5);
    }

    public float getTimeOfDay(float tickDelta) {
        return this.dimension.getTimeOfDay(this.data.getTimeOfDay(), tickDelta);
    }

    public int getMoonPhase() {
        return this.dimension.getMoonPhase(this.data.getTimeOfDay());
    }

    public float getMoonSize() {
        return Dimension.MOON_PHASE_TO_SIZE[this.dimension.getMoonPhase(this.data.getTimeOfDay())];
    }

    public float getSunAngle(float tickDelta) {
        float f = this.getTimeOfDay(tickDelta);
        return f * (float) Math.PI * 2.0F;
    }

    public Vec3d getCloudColor(float tickDelta) {
        float f = this.getTimeOfDay(tickDelta);
        float f1 = MathHelper.cos(f * (float) Math.PI * 2.0F) * 2.0F + 0.5F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        float f2 = (float)(this.cloudColor >> 16 & 255L) / 255.0F;
        float f3 = (float)(this.cloudColor >> 8 & 255L) / 255.0F;
        float f4 = (float)(this.cloudColor & 255L) / 255.0F;
        float f5 = this.getRain(tickDelta);
        if (f5 > 0.0F) {
            float f6 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.6F;
            float f7 = 1.0F - f5 * 0.95F;
            f2 = f2 * f7 + f6 * (1.0F - f7);
            f3 = f3 * f7 + f6 * (1.0F - f7);
            f4 = f4 * f7 + f6 * (1.0F - f7);
        }

        f2 *= f1 * 0.9F + 0.1F;
        f3 *= f1 * 0.9F + 0.1F;
        f4 *= f1 * 0.85F + 0.15F;
        float f9 = this.getThunder(tickDelta);
        if (f9 > 0.0F) {
            float f10 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.2F;
            float f8 = 1.0F - f9 * 0.95F;
            f2 = f2 * f8 + f10 * (1.0F - f8);
            f3 = f3 * f8 + f10 * (1.0F - f8);
            f4 = f4 * f8 + f10 * (1.0F - f8);
        }

        return new Vec3d(f2, f3, f4);
    }

    public Vec3d getFogColor(float tickDelta) {
        float f = this.getTimeOfDay(tickDelta);
        return this.dimension.getFogColor(f, tickDelta);
    }

    public BlockPos getPrecipitationHeight(BlockPos pos) {
        return this.getChunk(pos).getPrecipitationHeight(pos);
    }

    public BlockPos getSurfaceHeight(BlockPos pos) {
        WorldChunk worldchunk = this.getChunk(pos);
        BlockPos blockpos = new BlockPos(pos.getX(), worldchunk.getHighestSectionOffset() + 16, pos.getZ());

        while (blockpos.getY() >= 0) {
            BlockPos blockpos1 = blockpos.down();
            Material material = worldchunk.getBlock(blockpos1).getMaterial();
            if (material.blocksMovement() && material != Material.LEAVES) {
                break;
            }

            blockpos = blockpos1;
        }

        return blockpos;
    }

    public float getStarBrightness(float tickDelta) {
        float f = this.getTimeOfDay(tickDelta);
        float f1 = 1.0F - (MathHelper.cos(f * (float) Math.PI * 2.0F) * 2.0F + 0.25F);
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        return f1 * f1 * 0.5F;
    }

    public void scheduleTick(BlockPos pos, Block block, int delay) {
    }

    public void scheduleTick(BlockPos pos, Block block, int delay, int priority) {
    }

    public void loadScheduledTick(BlockPos pos, Block block, int delay, int priority) {
    }

    public void tickEntities() {
        this.profiler.push("entities");
        this.profiler.push("global");

        for (int i = 0; i < this.globalEntities.size(); i++) {
            Entity entity = this.globalEntities.get(i);

            try {
                entity.ticks++;
                entity.tick();
            } catch (Throwable throwable2) {
                CrashReport crashreport = CrashReport.of(throwable2, "Ticking entity");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being ticked");
                if (entity == null) {
                    crashreportcategory.add("Entity", "~~NULL~~");
                } else {
                    entity.populateCrashReport(crashreportcategory);
                }

                throw new CrashException(crashreport);
            }

            if (entity.removed) {
                this.globalEntities.remove(i--);
            }
        }

        this.profiler.swap("remove");
        this.entities.removeAll(this.entitiesToRemove);

        for (int k = 0; k < this.entitiesToRemove.size(); k++) {
            Entity entity1 = this.entitiesToRemove.get(k);
            int j = entity1.chunkX;
            int l1 = entity1.chunkZ;
            if (entity1.inChunk && this.isChunkLoadedAt(j, l1, true)) {
                this.getChunkAt(j, l1).removeEntity(entity1);
            }
        }

        for (int l = 0; l < this.entitiesToRemove.size(); l++) {
            this.notifyEntityRemoved(this.entitiesToRemove.get(l));
        }

        this.entitiesToRemove.clear();
        this.profiler.swap("regular");

        for (int i1 = 0; i1 < this.entities.size(); i1++) {
            Entity entity2 = this.entities.get(i1);
            if (entity2.vehicle != null) {
                if (!entity2.vehicle.removed && entity2.vehicle.rider == entity2) {
                    continue;
                }

                entity2.vehicle.rider = null;
                entity2.vehicle = null;
            }

            this.profiler.push("tick");
            if (!entity2.removed) {
                try {
                    this.tickEntity(entity2);
                } catch (Throwable throwable1) {
                    CrashReport crashreport1 = CrashReport.of(throwable1, "Ticking entity");
                    CrashReportCategory crashreportcategory2 = crashreport1.addCategory("Entity being ticked");
                    entity2.populateCrashReport(crashreportcategory2);
                    throw new CrashException(crashreport1);
                }
            }

            this.profiler.pop();
            this.profiler.push("remove");
            if (entity2.removed) {
                int k1 = entity2.chunkX;
                int i2 = entity2.chunkZ;
                if (entity2.inChunk && this.isChunkLoadedAt(k1, i2, true)) {
                    this.getChunkAt(k1, i2).removeEntity(entity2);
                }

                this.entities.remove(i1--);
                this.notifyEntityRemoved(entity2);
            }

            this.profiler.pop();
        }

        this.profiler.swap("blockEntities");
        this.isTickingBlockEntities = true;
        Iterator<BlockEntity> iterator = this.tickingBlockEntities.iterator();

        while (iterator.hasNext()) {
            BlockEntity blockentity = iterator.next();
            if (!blockentity.isRemoved() && blockentity.hasWorld()) {
                BlockPos blockpos = blockentity.getPos();
                if (this.isChunkLoaded(blockpos) && this.worldBorder.contains(blockpos)) {
                    try {
                        ((Tickable)blockentity).tick();
                    } catch (Throwable throwable) {
                        CrashReport crashreport2 = CrashReport.of(throwable, "Ticking block entity");
                        CrashReportCategory crashreportcategory1 = crashreport2.addCategory("Block entity being ticked");
                        blockentity.populateCrashReport(crashreportcategory1);
                        throw new CrashException(crashreport2);
                    }
                }
            }

            if (blockentity.isRemoved()) {
                iterator.remove();
                this.blockEntities.remove(blockentity);
                if (this.isChunkLoaded(blockentity.getPos())) {
                    this.getChunk(blockentity.getPos()).removeBlockEntity(blockentity.getPos());
                }
            }
        }

        this.isTickingBlockEntities = false;
        if (!this.removedBlockEntities.isEmpty()) {
            this.tickingBlockEntities.removeAll(this.removedBlockEntities);
            this.blockEntities.removeAll(this.removedBlockEntities);
            this.removedBlockEntities.clear();
        }

        this.profiler.swap("pendingBlockEntities");
        if (!this.pendingBlockEntities.isEmpty()) {
            for (int j1 = 0; j1 < this.pendingBlockEntities.size(); j1++) {
                BlockEntity blockentity1 = this.pendingBlockEntities.get(j1);
                if (!blockentity1.isRemoved()) {
                    if (!this.blockEntities.contains(blockentity1)) {
                        this.addBlockEntity(blockentity1);
                    }

                    if (this.isChunkLoaded(blockentity1.getPos())) {
                        this.getChunk(blockentity1.getPos()).setBlockEntity(blockentity1.getPos(), blockentity1);
                    }

                    this.notifyBlockChanged(blockentity1.getPos());
                }
            }

            this.pendingBlockEntities.clear();
        }

        this.profiler.pop();
        this.profiler.pop();
    }

    public boolean addBlockEntity(BlockEntity blockEntity) {
        boolean flag = this.blockEntities.add(blockEntity);
        if (flag && blockEntity instanceof Tickable) {
            this.tickingBlockEntities.add(blockEntity);
        }

        return flag;
    }

    public void loadBlockEntities(Collection<BlockEntity> blockEntities) {
        if (this.isTickingBlockEntities) {
            this.pendingBlockEntities.addAll(blockEntities);
        } else {
            for (BlockEntity blockentity : blockEntities) {
                this.blockEntities.add(blockentity);
                if (blockentity instanceof Tickable) {
                    this.tickingBlockEntities.add(blockentity);
                }
            }
        }
    }

    public void tickEntity(Entity entity) {
        this.tickEntity(entity, true);
    }

    /**
     * Attempts to tick the given entity and updates chunk management for this entity.
     * 
     * <br>
     * If {@code requireLoaded} is {@code true}, then the entire area within a range of 32 blocks
     * of the entity must be loaded, or the method will exit early. This effectively means an area
     * of 5x5 chunks around the entity must be loaded.
     */
    public void tickEntity(Entity entity, boolean requireLoaded) {
        int i = MathHelper.floor(entity.x);
        int j = MathHelper.floor(entity.z);
        int k = 32;
        if (!requireLoaded || this.isAreaLoaded(i - k, 0, j - k, i + k, 0, j + k, true)) {
            entity.prevX = entity.x;
            entity.prevY = entity.y;
            entity.prevZ = entity.z;
            entity.lastYaw = entity.yaw;
            entity.lastPitch = entity.pitch;
            if (requireLoaded && entity.inChunk) {
                entity.ticks++;
                if (entity.vehicle != null) {
                    entity.rideTick();
                } else {
                    entity.tick();
                }
            }

            this.profiler.push("chunkCheck");
            if (Double.isNaN(entity.x) || Double.isInfinite(entity.x)) {
                entity.x = entity.prevX;
            }

            if (Double.isNaN(entity.y) || Double.isInfinite(entity.y)) {
                entity.y = entity.prevY;
            }

            if (Double.isNaN(entity.z) || Double.isInfinite(entity.z)) {
                entity.z = entity.prevZ;
            }

            if (Double.isNaN(entity.pitch) || Double.isInfinite(entity.pitch)) {
                entity.pitch = entity.lastPitch;
            }

            if (Double.isNaN(entity.yaw) || Double.isInfinite(entity.yaw)) {
                entity.yaw = entity.lastYaw;
            }

            int l = MathHelper.floor(entity.x / 16.0);
            int i1 = MathHelper.floor(entity.y / 16.0);
            int j1 = MathHelper.floor(entity.z / 16.0);
            if (!entity.inChunk || entity.chunkX != l || entity.chunkY != i1 || entity.chunkZ != j1) {
                if (entity.inChunk && this.isChunkLoadedAt(entity.chunkX, entity.chunkZ, true)) {
                    this.getChunkAt(entity.chunkX, entity.chunkZ).removeEntity(entity, entity.chunkY);
                }

                if (this.isChunkLoadedAt(l, j1, true)) {
                    entity.inChunk = true;
                    this.getChunkAt(l, j1).addEntity(entity);
                } else {
                    entity.inChunk = false;
                }
            }

            this.profiler.pop();
            if (requireLoaded && entity.inChunk && entity.rider != null) {
                if (!entity.rider.removed && entity.rider.vehicle == entity) {
                    this.tickEntity(entity.rider);
                } else {
                    entity.rider.vehicle = null;
                    entity.rider = null;
                }
            }
        }
    }

    public boolean isUnobstructed(Box bounds) {
        return this.isUnobstructed(bounds, null);
    }

    public boolean isUnobstructed(Box bounds, Entity ignore) {
        List<Entity> list = this.getEntities(null, bounds);

        for (int i = 0; i < list.size(); i++) {
            Entity entity = list.get(i);
            if (!entity.removed && entity.blocksBuilding && entity != ignore && (ignore == null || ignore.vehicle != entity && ignore.rider != entity)) {
                return false;
            }
        }

        return true;
    }

    public boolean containsNonAir(Box bounds) {
        int i = MathHelper.floor(bounds.minX);
        int j = MathHelper.floor(bounds.maxX);
        int k = MathHelper.floor(bounds.minY);
        int l = MathHelper.floor(bounds.maxY);
        int i1 = MathHelper.floor(bounds.minZ);
        int j1 = MathHelper.floor(bounds.maxZ);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 <= j; k1++) {
            for (int l1 = k; l1 <= l; l1++) {
                for (int i2 = i1; i2 <= j1; i2++) {
                    Block block = this.getBlockState(blockpos$mutable.set(k1, l1, i2)).getBlock();
                    if (block.getMaterial() != Material.AIR) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean containsLiquid(Box bounds) {
        int i = MathHelper.floor(bounds.minX);
        int j = MathHelper.floor(bounds.maxX);
        int k = MathHelper.floor(bounds.minY);
        int l = MathHelper.floor(bounds.maxY);
        int i1 = MathHelper.floor(bounds.minZ);
        int j1 = MathHelper.floor(bounds.maxZ);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 <= j; k1++) {
            for (int l1 = k; l1 <= l; l1++) {
                for (int i2 = i1; i2 <= j1; i2++) {
                    Block block = this.getBlockState(blockpos$mutable.set(k1, l1, i2)).getBlock();
                    if (block.getMaterial().isLiquid()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean containsFireSource(Box bounds) {
        int i = MathHelper.floor(bounds.minX);
        int j = MathHelper.floor(bounds.maxX + 1.0);
        int k = MathHelper.floor(bounds.minY);
        int l = MathHelper.floor(bounds.maxY + 1.0);
        int i1 = MathHelper.floor(bounds.minZ);
        int j1 = MathHelper.floor(bounds.maxZ + 1.0);
        if (this.isAreaLoaded(i, k, i1, j, l, j1, true)) {
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

            for (int k1 = i; k1 < j; k1++) {
                for (int l1 = k; l1 < l; l1++) {
                    for (int i2 = i1; i2 < j1; i2++) {
                        Block block = this.getBlockState(blockpos$mutable.set(k1, l1, i2)).getBlock();
                        if (block == Blocks.FIRE || block == Blocks.FLOWING_LAVA || block == Blocks.LAVA) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean applyLiquidDrag(Box bounds, Material material, Entity entity) {
        int i = MathHelper.floor(bounds.minX);
        int j = MathHelper.floor(bounds.maxX + 1.0);
        int k = MathHelper.floor(bounds.minY);
        int l = MathHelper.floor(bounds.maxY + 1.0);
        int i1 = MathHelper.floor(bounds.minZ);
        int j1 = MathHelper.floor(bounds.maxZ + 1.0);
        if (!this.isAreaLoaded(i, k, i1, j, l, j1, true)) {
            return false;
        }

        boolean flag = false;
        Vec3d vec3d = new Vec3d(0.0, 0.0, 0.0);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 < j; k1++) {
            for (int l1 = k; l1 < l; l1++) {
                for (int i2 = i1; i2 < j1; i2++) {
                    blockpos$mutable.set(k1, l1, i2);
                    BlockState blockstate = this.getBlockState(blockpos$mutable);
                    Block block = blockstate.getBlock();
                    if (block.getMaterial() == material) {
                        double d0 = l1 + 1 - LiquidBlock.getHeightLoss(blockstate.get(LiquidBlock.LEVEL));
                        if (l >= d0) {
                            flag = true;
                            vec3d = block.applyMaterialDrag(this, blockpos$mutable, entity, vec3d);
                        }
                    }
                }
            }
        }

        if (vec3d.length() > 0.0 && entity.hasLiquidCollision()) {
            vec3d = vec3d.normalize();
            double d1 = 0.014;
            entity.velocityX = entity.velocityX + vec3d.x * d1;
            entity.velocityY = entity.velocityY + vec3d.y * d1;
            entity.velocityZ = entity.velocityZ + vec3d.z * d1;
        }

        return flag;
    }

    public boolean containsMaterial(Box bounds, Material material) {
        int i = MathHelper.floor(bounds.minX);
        int j = MathHelper.floor(bounds.maxX + 1.0);
        int k = MathHelper.floor(bounds.minY);
        int l = MathHelper.floor(bounds.maxY + 1.0);
        int i1 = MathHelper.floor(bounds.minZ);
        int j1 = MathHelper.floor(bounds.maxZ + 1.0);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 < j; k1++) {
            for (int l1 = k; l1 < l; l1++) {
                for (int i2 = i1; i2 < j1; i2++) {
                    if (this.getBlockState(blockpos$mutable.set(k1, l1, i2)).getBlock().getMaterial() == material) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean containsLiquid(Box bounds, Material material) {
        int i = MathHelper.floor(bounds.minX);
        int j = MathHelper.floor(bounds.maxX + 1.0);
        int k = MathHelper.floor(bounds.minY);
        int l = MathHelper.floor(bounds.maxY + 1.0);
        int i1 = MathHelper.floor(bounds.minZ);
        int j1 = MathHelper.floor(bounds.maxZ + 1.0);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 < j; k1++) {
            for (int l1 = k; l1 < l; l1++) {
                for (int i2 = i1; i2 < j1; i2++) {
                    BlockState blockstate = this.getBlockState(blockpos$mutable.set(k1, l1, i2));
                    Block block = blockstate.getBlock();
                    if (block.getMaterial() == material) {
                        int j2 = blockstate.get(LiquidBlock.LEVEL);
                        double d0 = l1 + 1;
                        if (j2 < 8) {
                            d0 = l1 + 1 - j2 / 8.0;
                        }

                        if (d0 >= bounds.minY) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public Explosion explode(Entity source, double x, double y, double z, float power, boolean destructive) {
        return this.explode(source, x, y, z, power, false, destructive);
    }

    public Explosion explode(Entity source, double x, double y, double z, float power, boolean createFire, boolean destructive) {
        Explosion explosion = new Explosion(this, source, x, y, z, power, createFire, destructive);
        explosion.damageEntities();
        explosion.damageBlocks(true);
        return explosion;
    }

    public float getBlockDensity(Vec3d pos, Box bounds) {
        double d0 = 1.0 / ((bounds.maxX - bounds.minX) * 2.0 + 1.0);
        double d1 = 1.0 / ((bounds.maxY - bounds.minY) * 2.0 + 1.0);
        double d2 = 1.0 / ((bounds.maxZ - bounds.minZ) * 2.0 + 1.0);
        double d3 = (1.0 - Math.floor(1.0 / d0) * d0) / 2.0;
        double d4 = (1.0 - Math.floor(1.0 / d2) * d2) / 2.0;
        if (!(d0 < 0.0) && !(d1 < 0.0) && !(d2 < 0.0)) {
            int i = 0;
            int j = 0;

            for (float f = 0.0F; f <= 1.0F; f = (float)(f + d0)) {
                for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float)(f1 + d1)) {
                    for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float)(f2 + d2)) {
                        double d5 = bounds.minX + (bounds.maxX - bounds.minX) * f;
                        double d6 = bounds.minY + (bounds.maxY - bounds.minY) * f1;
                        double d7 = bounds.minZ + (bounds.maxZ - bounds.minZ) * f2;
                        if (this.rayTrace(new Vec3d(d5 + d3, d6, d7 + d4), pos) == null) {
                            i++;
                        }

                        j++;
                    }
                }
            }

            return (float)i / j;
        } else {
            return 0.0F;
        }
    }

    public boolean extinguishFire(PlayerEntity player, BlockPos pos, Direction face) {
        pos = pos.offset(face);
        if (this.getBlockState(pos).getBlock() == Blocks.FIRE) {
            this.doEvent(player, 1004, pos, 0);
            this.removeBlock(pos);
            return true;
        } else {
            return false;
        }
    }

    public String getDebugInfo() {
        return "All: " + this.entities.size();
    }

    public String getChunkSourceDebugInfo() {
        return this.chunkSource.getDebugInfo();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        if (!this.contains(pos)) {
            return null;
        }

        BlockEntity blockentity = null;
        if (this.isTickingBlockEntities) {
            for (int i = 0; i < this.pendingBlockEntities.size(); i++) {
                BlockEntity blockentity1 = this.pendingBlockEntities.get(i);
                if (!blockentity1.isRemoved() && blockentity1.getPos().equals(pos)) {
                    blockentity = blockentity1;
                    break;
                }
            }
        }

        if (blockentity == null) {
            blockentity = this.getChunk(pos).getBlockEntity(pos, WorldChunk.BlockEntityCreationType.IMMEDIATE);
        }

        if (blockentity == null) {
            for (int j = 0; j < this.pendingBlockEntities.size(); j++) {
                BlockEntity blockentity2 = this.pendingBlockEntities.get(j);
                if (!blockentity2.isRemoved() && blockentity2.getPos().equals(pos)) {
                    blockentity = blockentity2;
                    break;
                }
            }
        }

        return blockentity;
    }

    public void setBlockEntity(BlockPos pos, BlockEntity blockEntity) {
        if (blockEntity != null && !blockEntity.isRemoved()) {
            if (this.isTickingBlockEntities) {
                blockEntity.setPos(pos);
                Iterator<BlockEntity> iterator = this.pendingBlockEntities.iterator();

                while (iterator.hasNext()) {
                    BlockEntity blockentity = iterator.next();
                    if (blockentity.getPos().equals(pos)) {
                        blockentity.markRemoved();
                        iterator.remove();
                    }
                }

                this.pendingBlockEntities.add(blockEntity);
            } else {
                this.addBlockEntity(blockEntity);
                this.getChunk(pos).setBlockEntity(pos, blockEntity);
            }
        }
    }

    public void removeBlockEntity(BlockPos pos) {
        BlockEntity blockentity = this.getBlockEntity(pos);
        if (blockentity != null && this.isTickingBlockEntities) {
            blockentity.markRemoved();
            this.pendingBlockEntities.remove(blockentity);
        } else {
            if (blockentity != null) {
                this.pendingBlockEntities.remove(blockentity);
                this.blockEntities.remove(blockentity);
                this.tickingBlockEntities.remove(blockentity);
            }

            this.getChunk(pos).removeBlockEntity(pos);
        }
    }

    public void unloadBlockEntity(BlockEntity blockEntity) {
        this.removedBlockEntities.add(blockEntity);
    }

    public boolean isFullCube(BlockPos pos) {
        BlockState blockstate = this.getBlockState(pos);
        Box box = blockstate.getBlock().getCollisionShape(this, pos, blockstate);
        return box != null && box.getAverageSideLength() >= 1.0;
    }

    public static boolean hasSolidTop(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (block.getMaterial().isSolidBlocking() && block.isCube()) {
            return true;
        } else if (block instanceof StairsBlock) {
            return blockstate.get(StairsBlock.HALF) == StairsBlock.Half.TOP;
        } else {
            return block instanceof SlabBlock
                ? blockstate.get(SlabBlock.HALF) == SlabBlock.Half.TOP
                : block instanceof HopperBlock || block instanceof SnowLayerBlock && blockstate.get(SnowLayerBlock.LAYERS) == 7;
        }
    }

    public boolean isSolidBlockingCube(BlockPos pos, boolean defaultValue) {
        if (!this.contains(pos)) {
            return defaultValue;
        }

        WorldChunk worldchunk = this.chunkSource.getChunk(pos);
        if (worldchunk.isEmpty()) {
            return defaultValue;
        }

        Block block = this.getBlockState(pos).getBlock();
        return block.getMaterial().isSolidBlocking() && block.isCube();
    }

    public void initAmbientDarkness() {
        int i = this.calculateAmbientDarkness(1.0F);
        if (i != this.ambientDarkness) {
            this.ambientDarkness = i;
        }
    }

    public void setAllowedMobSpawns(boolean spawnAnimals, boolean spawnMonsters) {
        this.spawnAnimals = spawnAnimals;
        this.spawnMonsters = spawnMonsters;
    }

    public void tick() {
        this.tickWeather();
    }

    protected void initWeather() {
        if (this.data.isRaining()) {
            this.rain = 1.0F;
            if (this.data.isThundering()) {
                this.thunder = 1.0F;
            }
        }
    }

    protected void tickWeather() {
        if (!this.dimension.hasNoSky()) {
            if (!this.isClient) {
                int i = this.data.getClearWeatherTime();
                if (i > 0) {
                    this.data.setClearWeatherTime(--i);
                    this.data.setThunderTime(this.data.isThundering() ? 1 : 2);
                    this.data.setRainTime(this.data.isRaining() ? 1 : 2);
                }

                int j = this.data.getThunderTime();
                if (j <= 0) {
                    if (this.data.isThundering()) {
                        this.data.setThunderTime(this.random.nextInt(12000) + 3600);
                    } else {
                        this.data.setThunderTime(this.random.nextInt(168000) + 12000);
                    }
                } else {
                    this.data.setThunderTime(--j);
                    if (j <= 0) {
                        this.data.setThundering(!this.data.isThundering());
                    }
                }

                this.prevThunder = this.thunder;
                if (this.data.isThundering()) {
                    this.thunder = (float)(this.thunder + 0.01);
                } else {
                    this.thunder = (float)(this.thunder - 0.01);
                }

                this.thunder = MathHelper.clamp(this.thunder, 0.0F, 1.0F);
                int k = this.data.getRainTime();
                if (k <= 0) {
                    if (this.data.isRaining()) {
                        this.data.setRainTime(this.random.nextInt(12000) + 12000);
                    } else {
                        this.data.setRainTime(this.random.nextInt(168000) + 12000);
                    }
                } else {
                    this.data.setRainTime(--k);
                    if (k <= 0) {
                        this.data.setRaining(!this.data.isRaining());
                    }
                }

                this.prevRain = this.rain;
                if (this.data.isRaining()) {
                    this.rain = (float)(this.rain + 0.01);
                } else {
                    this.rain = (float)(this.rain - 0.01);
                }

                this.rain = MathHelper.clamp(this.rain, 0.0F, 1.0F);
            }
        }
    }

    protected void purgeTickingChunks() {
        this.tickingChunks.clear();
        this.profiler.push("buildList");

        for (int i = 0; i < this.players.size(); i++) {
            PlayerEntity playerentity = this.players.get(i);
            int j = MathHelper.floor(playerentity.x / 16.0);
            int k = MathHelper.floor(playerentity.z / 16.0);
            int l = this.getChunkViewDistance();

            for (int i1 = -l; i1 <= l; i1++) {
                for (int j1 = -l; j1 <= l; j1++) {
                    this.tickingChunks.add(new ChunkPos(i1 + j, j1 + k));
                }
            }
        }

        this.profiler.pop();
        if (this.ambientSoundCooldown > 0) {
            this.ambientSoundCooldown--;
        }

        this.profiler.push("playerCheckLight");
        if (!this.players.isEmpty()) {
            int k1 = this.random.nextInt(this.players.size());
            PlayerEntity playerentity1 = this.players.get(k1);
            int l1 = MathHelper.floor(playerentity1.x) + this.random.nextInt(11) - 5;
            int i2 = MathHelper.floor(playerentity1.y) + this.random.nextInt(11) - 5;
            int j2 = MathHelper.floor(playerentity1.z) + this.random.nextInt(11) - 5;
            this.checkLight(new BlockPos(l1, i2, j2));
        }

        this.profiler.pop();
    }

    protected abstract int getChunkViewDistance();

    protected void tickAmbienceAndLight(int x, int z, WorldChunk chunk) {
        this.profiler.swap("moodSound");
        if (this.ambientSoundCooldown == 0 && !this.isClient) {
            this.randomTickLCG = this.randomTickLCG * 3 + 1013904223;
            int i = this.randomTickLCG >> 2;
            int j = i & 15;
            int k = i >> 8 & 15;
            int l = i >> 16 & 0xFF;
            BlockPos blockpos = new BlockPos(j, l, k);
            Block block = chunk.getBlock(blockpos);
            j += x;
            k += z;
            if (block.getMaterial() == Material.AIR && this.getActualLight(blockpos) <= this.random.nextInt(8) && this.getLight(LightType.SKY, blockpos) <= 0) {
                PlayerEntity playerentity = this.getNearestPlayer(j + 0.5, l + 0.5, k + 0.5, 8.0);
                if (playerentity != null && playerentity.squaredDistanceTo(j + 0.5, l + 0.5, k + 0.5) > 4.0) {
                    this.playSound(j + 0.5, l + 0.5, k + 0.5, "ambient.cave.cave", 0.7F, 0.8F + this.random.nextFloat() * 0.2F);
                    this.ambientSoundCooldown = this.random.nextInt(12000) + 6000;
                }
            }
        }

        this.profiler.swap("checkLight");
        chunk.checkBorderLight();
    }

    protected void tickChunks() {
        this.purgeTickingChunks();
    }

    public void tickBlockNow(Block block, BlockPos pos, Random random) {
        this.doTicksImmediately = true;
        block.tick(this, pos, this.getBlockState(pos), random);
        this.doTicksImmediately = false;
    }

    public boolean canFreeze(BlockPos pos) {
        return this.canFreeze(pos, false);
    }

    public boolean canFreezeNaturally(BlockPos pos) {
        return this.canFreeze(pos, true);
    }

    public boolean canFreeze(BlockPos pos, boolean needsAdjacentNonWaterBlock) {
        Biome biome = this.getBiome(pos);
        float f = biome.getTemperature(pos);
        if (f > 0.15F) {
            return false;
        }

        if (pos.getY() >= 0 && pos.getY() < 256 && this.getLight(LightType.BLOCK, pos) < 10) {
            BlockState blockstate = this.getBlockState(pos);
            Block block = blockstate.getBlock();
            if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && blockstate.get(LiquidBlock.LEVEL) == 0) {
                if (!needsAdjacentNonWaterBlock) {
                    return true;
                }

                boolean flag = this.isWater(pos.west()) && this.isWater(pos.east()) && this.isWater(pos.north()) && this.isWater(pos.south());
                if (!flag) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isWater(BlockPos pos) {
        return this.getBlockState(pos).getBlock().getMaterial() == Material.WATER;
    }

    public boolean canSnowFall(BlockPos pos, boolean checkLight) {
        Biome biome = this.getBiome(pos);
        float f = biome.getTemperature(pos);
        if (f > 0.15F) {
            return false;
        }

        if (!checkLight) {
            return true;
        }

        if (pos.getY() >= 0 && pos.getY() < 256 && this.getLight(LightType.BLOCK, pos) < 10) {
            Block block = this.getBlockState(pos).getBlock();
            if (block.getMaterial() == Material.AIR && Blocks.SNOW_LAYER.canBePlaced(this, pos)) {
                return true;
            }
        }

        return false;
    }

    public boolean checkLight(BlockPos pos) {
        boolean flag = false;
        if (!this.dimension.hasNoSky()) {
            flag |= this.updateLight(LightType.SKY, pos);
        }

        return flag | this.updateLight(LightType.BLOCK, pos);
    }

    private int findLight(BlockPos pos, LightType type) {
        if (type == LightType.SKY && this.hasSkyAccess(pos)) {
            return 15;
        }

        Block block = this.getBlockState(pos).getBlock();
        int i = type == LightType.SKY ? 0 : block.getLight();
        int j = block.getOpacity();
        if (j >= 15 && block.getLight() > 0) {
            j = 1;
        }

        if (j < 1) {
            j = 1;
        }

        if (j >= 15) {
            return 0;
        }

        if (i >= 14) {
            return i;
        }

        for (Direction direction : Direction.values()) {
            BlockPos blockpos = pos.offset(direction);
            int k = this.getLight(type, blockpos) - j;
            if (k > i) {
                i = k;
            }

            if (i >= 14) {
                return i;
            }
        }

        return i;
    }

    public boolean updateLight(LightType type, BlockPos pos) {
        if (!this.isAreaLoaded(pos, 17, false)) {
            return false;
        }

        int i = 0;
        int j = 0;
        this.profiler.push("getBrightness");
        int k = this.getLight(type, pos);
        int l = this.findLight(pos, type);
        int i1 = pos.getX();
        int j1 = pos.getY();
        int k1 = pos.getZ();
        if (l > k) {
            this.lightPropagation[j++] = 133152;
        } else if (l < k) {
            this.lightPropagation[j++] = 133152 | k << 18;

            while (i < j) {
                int l1 = this.lightPropagation[i++];
                int i2 = (l1 & 63) - 32 + i1;
                int j2 = (l1 >> 6 & 63) - 32 + j1;
                int k2 = (l1 >> 12 & 63) - 32 + k1;
                int l2 = l1 >> 18 & 15;
                BlockPos blockpos = new BlockPos(i2, j2, k2);
                int i3 = this.getLight(type, blockpos);
                if (i3 == l2) {
                    this.setLight(type, blockpos, 0);
                    if (l2 > 0) {
                        int j3 = MathHelper.abs(i2 - i1);
                        int k3 = MathHelper.abs(j2 - j1);
                        int l3 = MathHelper.abs(k2 - k1);
                        if (j3 + k3 + l3 < 17) {
                            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                            for (Direction direction : Direction.values()) {
                                int i4 = i2 + direction.getOffsetX();
                                int j4 = j2 + direction.getOffsetY();
                                int k4 = k2 + direction.getOffsetZ();
                                blockpos$mutable.set(i4, j4, k4);
                                int l4 = Math.max(1, this.getBlockState(blockpos$mutable).getBlock().getOpacity());
                                i3 = this.getLight(type, blockpos$mutable);
                                if (i3 == l2 - l4 && j < this.lightPropagation.length) {
                                    this.lightPropagation[j++] = i4 - i1 + 32 | j4 - j1 + 32 << 6 | k4 - k1 + 32 << 12 | l2 - l4 << 18;
                                }
                            }
                        }
                    }
                }
            }

            i = 0;
        }

        this.profiler.pop();
        this.profiler.push("checkedPosition < toCheckCount");

        while (i < j) {
            int i5 = this.lightPropagation[i++];
            int j5 = (i5 & 63) - 32 + i1;
            int k5 = (i5 >> 6 & 63) - 32 + j1;
            int l5 = (i5 >> 12 & 63) - 32 + k1;
            BlockPos blockpos1 = new BlockPos(j5, k5, l5);
            int i6 = this.getLight(type, blockpos1);
            int j6 = this.findLight(blockpos1, type);
            if (j6 != i6) {
                this.setLight(type, blockpos1, j6);
                if (j6 > i6) {
                    int k6 = Math.abs(j5 - i1);
                    int l6 = Math.abs(k5 - j1);
                    int i7 = Math.abs(l5 - k1);
                    boolean flag = j < this.lightPropagation.length - 6;
                    if (k6 + l6 + i7 < 17 && flag) {
                        if (this.getLight(type, blockpos1.west()) < j6) {
                            this.lightPropagation[j++] = j5 - 1 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLight(type, blockpos1.east()) < j6) {
                            this.lightPropagation[j++] = j5 + 1 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLight(type, blockpos1.down()) < j6) {
                            this.lightPropagation[j++] = j5 - i1 + 32 + (k5 - 1 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLight(type, blockpos1.up()) < j6) {
                            this.lightPropagation[j++] = j5 - i1 + 32 + (k5 + 1 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLight(type, blockpos1.north()) < j6) {
                            this.lightPropagation[j++] = j5 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - 1 - k1 + 32 << 12);
                        }

                        if (this.getLight(type, blockpos1.south()) < j6) {
                            this.lightPropagation[j++] = j5 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 + 1 - k1 + 32 << 12);
                        }
                    }
                }
            }
        }

        this.profiler.pop();
        return true;
    }

    /**
     * @param flush Flush all scheduled ticks, rather than just the ones scheduled for this tick.
     */
    public boolean doScheduledTicks(boolean flush) {
        return false;
    }

    public List<ScheduledTick> getScheduledTicks(WorldChunk chunk, boolean remove) {
        return null;
    }

    public List<ScheduledTick> getScheduledTicks(StructureBox bounds, boolean remove) {
        return null;
    }

    public List<Entity> getEntities(Entity exclude, Box bounds) {
        return this.getEntities(exclude, bounds, EntityFilter.NOT_SPECTATOR);
    }

    public List<Entity> getEntities(Entity exclude, Box bounds, Predicate<? super Entity> filter) {
        List<Entity> list = Lists.newArrayList();
        int i = MathHelper.floor((bounds.minX - 2.0) / 16.0);
        int j = MathHelper.floor((bounds.maxX + 2.0) / 16.0);
        int k = MathHelper.floor((bounds.minZ - 2.0) / 16.0);
        int l = MathHelper.floor((bounds.maxZ + 2.0) / 16.0);

        for (int i1 = i; i1 <= j; i1++) {
            for (int j1 = k; j1 <= l; j1++) {
                if (this.isChunkLoadedAt(i1, j1, true)) {
                    this.getChunkAt(i1, j1).getEntities(exclude, bounds, list, filter);
                }
            }
        }

        return list;
    }

    public <T extends Entity> List<T> getEntities(Class<? extends T> type, Predicate<? super T> filter) {
        List<T> list = Lists.newArrayList();

        for (Entity entity : this.entities) {
            if (type.isAssignableFrom(entity.getClass()) && filter.apply((T)entity)) {
                list.add((T)entity);
            }
        }

        return list;
    }

    public <T extends Entity> List<T> getPlayers(Class<? extends T> type, Predicate<? super T> filter) {
        List<T> list = Lists.newArrayList();

        for (Entity entity : this.players) {
            if (type.isAssignableFrom(entity.getClass()) && filter.apply((T)entity)) {
                list.add((T)entity);
            }
        }

        return list;
    }

    public <T extends Entity> List<T> getEntitiesOfType(Class<? extends T> type, Box bounds) {
        return this.getEntitiesOfType(type, bounds, EntityFilter.NOT_SPECTATOR);
    }

    public <T extends Entity> List<T> getEntitiesOfType(Class<? extends T> type, Box box, Predicate<? super T> filter) {
        int i = MathHelper.floor((box.minX - 2.0) / 16.0);
        int j = MathHelper.floor((box.maxX + 2.0) / 16.0);
        int k = MathHelper.floor((box.minZ - 2.0) / 16.0);
        int l = MathHelper.floor((box.maxZ + 2.0) / 16.0);
        List<T> list = Lists.newArrayList();

        for (int i1 = i; i1 <= j; i1++) {
            for (int j1 = k; j1 <= l; j1++) {
                if (this.isChunkLoadedAt(i1, j1, true)) {
                    this.getChunkAt(i1, j1).getEntitiesOfType(type, box, list, filter);
                }
            }
        }

        return list;
    }

    public <T extends Entity> T getNearestEntity(Class<? extends T> type, Box bounds, T entity) {
        List<T> list = this.getEntitiesOfType(type, bounds);
        T t = null;
        double d0 = Double.MAX_VALUE;

        for (int i = 0; i < list.size(); i++) {
            T t1 = (T)list.get(i);
            if (t1 != entity && EntityFilter.NOT_SPECTATOR.apply(t1)) {
                double d1 = entity.squaredDistanceTo(t1);
                if (!(d1 > d0)) {
                    t = t1;
                    d0 = d1;
                }
            }
        }

        return t;
    }

    public Entity getEntity(int networkId) {
        return this.entitiesById.get(networkId);
    }

    public List<Entity> getEntities() {
        return this.entities;
    }

    public void notifyBlockEntityChanged(BlockPos pos, BlockEntity blockEntity) {
        if (this.isChunkLoaded(pos)) {
            this.getChunk(pos).markDirty();
        }
    }

    public int getEntityCount(Class<?> type) {
        int i = 0;

        for (Entity entity : this.entities) {
            if ((!(entity instanceof MobEntity) || !((MobEntity)entity).isPersistent()) && type.isAssignableFrom(entity.getClass())) {
                i++;
            }
        }

        return i;
    }

    public void loadEntities(Collection<Entity> entities) {
        this.entities.addAll(entities);

        for (Entity entity : entities) {
            this.notifyEntityAdded(entity);
        }
    }

    public void unloadEntities(Collection<Entity> entities) {
        this.entitiesToRemove.addAll(entities);
    }

    public boolean canPlace(Block block, BlockPos pos, boolean skipCollisionChecks, Direction face, Entity entity, ItemStack item) {
        Block blockx = this.getBlockState(pos).getBlock();
        Box box = skipCollisionChecks ? null : block.getCollisionShape(this, pos, block.defaultState());
        return (box == null || this.isUnobstructed(box, entity))
            && (
                blockx.getMaterial() == Material.DECORATION && block == Blocks.ANVIL
                    || blockx.getMaterial().isReplaceable() && block.canBePlaced(this, pos, face, item)
            );
    }

    public int getSeaLevel() {
        return this.seaLevel;
    }

    public void setSeaLevel(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    @Override
    public int getDirectSignal(BlockPos pos, Direction dir) {
        BlockState blockstate = this.getBlockState(pos);
        return blockstate.getBlock().getDirectSignal(this, pos, blockstate, dir);
    }

    @Override
    public WorldGeneratorType getGeneratorType() {
        return this.data.getGeneratorType();
    }

    /**
     * Returns the direct redstone signal the given position is receiving from neighboring blocks.
     */
    public int getDirectNeighborSignal(BlockPos pos) {
        int i = 0;
        i = Math.max(i, this.getDirectSignal(pos.down(), Direction.DOWN));
        if (i >= 15) {
            return i;
        }

        i = Math.max(i, this.getDirectSignal(pos.up(), Direction.UP));
        if (i >= 15) {
            return i;
        }

        i = Math.max(i, this.getDirectSignal(pos.north(), Direction.NORTH));
        if (i >= 15) {
            return i;
        }

        i = Math.max(i, this.getDirectSignal(pos.south(), Direction.SOUTH));
        if (i >= 15) {
            return i;
        }

        i = Math.max(i, this.getDirectSignal(pos.west(), Direction.WEST));
        if (i >= 15) {
            return i;
        }

        i = Math.max(i, this.getDirectSignal(pos.east(), Direction.EAST));
        return i >= 15 ? i : i;
    }

    /**
     * Returns whether the block at the given position is emitting a redstone signal in the given direction.
     * This roughly equates to what is colloquially known as 'soft' or 'weak' power.
     * 
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the direction <i>opposite</i> of the one given.
     */
    public boolean hasSignal(BlockPos pos, Direction dir) {
        return this.getSignal(pos, dir) > 0;
    }

    /**
     * Returns the redstone signal the block at the given position is emitting in the given direction.
     * This roughly equates to what is colloquially known as 'soft' or 'weak' power.
     * 
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * returns the signal emitted in the direction <i>opposite</i> of the one given.
     */
    public int getSignal(BlockPos pos, Direction dir) {
        BlockState blockstate = this.getBlockState(pos);
        Block block = blockstate.getBlock();
        return block.isSolid() ? this.getDirectNeighborSignal(pos) : block.getSignal(this, pos, blockstate, dir);
    }

    /**
     * Returns whether the given position is receiving a redstone signal from neighboring blocks.
     */
    public boolean hasNeighborSignal(BlockPos pos) {
        return this.getSignal(pos.down(), Direction.DOWN) > 0
            || this.getSignal(pos.up(), Direction.UP) > 0
            || this.getSignal(pos.north(), Direction.NORTH) > 0
            || this.getSignal(pos.south(), Direction.SOUTH) > 0
            || this.getSignal(pos.west(), Direction.WEST) > 0
            || this.getSignal(pos.east(), Direction.EAST) > 0;
    }

    /**
     * Returns the redstone signal the given position is receiving from neighboring blocks.
     */
    public int getNeighborSignal(BlockPos pos) {
        int i = 0;

        for (Direction direction : Direction.values()) {
            int j = this.getSignal(pos.offset(direction), direction);
            if (j >= 15) {
                return 15;
            }

            if (j > i) {
                i = j;
            }
        }

        return i;
    }

    public PlayerEntity getNearestPlayer(Entity entity, double range) {
        return this.getNearestPlayer(entity.x, entity.y, entity.z, range);
    }

    public PlayerEntity getNearestPlayer(double x, double y, double z, double range) {
        double d0 = -1.0;
        PlayerEntity playerentity = null;

        for (int i = 0; i < this.players.size(); i++) {
            PlayerEntity playerentity1 = this.players.get(i);
            if (EntityFilter.NOT_SPECTATOR.apply(playerentity1)) {
                double d1 = playerentity1.squaredDistanceTo(x, y, z);
                if ((range < 0.0 || d1 < range * range) && (d0 == -1.0 || d1 < d0)) {
                    d0 = d1;
                    playerentity = playerentity1;
                }
            }
        }

        return playerentity;
    }

    public boolean isPlayerWithinRange(double x, double y, double z, double range) {
        for (int i = 0; i < this.players.size(); i++) {
            PlayerEntity playerentity = this.players.get(i);
            if (EntityFilter.NOT_SPECTATOR.apply(playerentity)) {
                double d0 = playerentity.squaredDistanceTo(x, y, z);
                if (range < 0.0 || d0 < range * range) {
                    return true;
                }
            }
        }

        return false;
    }

    public PlayerEntity getPlayer(String name) {
        for (int i = 0; i < this.players.size(); i++) {
            PlayerEntity playerentity = this.players.get(i);
            if (name.equals(playerentity.getName())) {
                return playerentity;
            }
        }

        return null;
    }

    public PlayerEntity getPlayer(UUID uuid) {
        for (int i = 0; i < this.players.size(); i++) {
            PlayerEntity playerentity = this.players.get(i);
            if (uuid.equals(playerentity.getUuid())) {
                return playerentity;
            }
        }

        return null;
    }

    public void disconnect() {
    }

    public void checkSessionLock() throws SessionLockException {
        this.storage.checkSessionLock();
    }

    public void setTime(long time) {
        this.data.setTime(time);
    }

    public long getSeed() {
        return this.data.getSeed();
    }

    public long getTime() {
        return this.data.getTime();
    }

    public long getTimeOfDay() {
        return this.data.getTimeOfDay();
    }

    public void setTimeOfDay(long time) {
        this.data.setTimeOfDay(time);
    }

    public BlockPos getSpawnPoint() {
        BlockPos blockpos = new BlockPos(this.data.getSpawnX(), this.data.getSpawnY(), this.data.getSpawnZ());
        if (!this.getWorldBorder().contains(blockpos)) {
            blockpos = this.getHeight(new BlockPos(this.getWorldBorder().getCenterX(), 0.0, this.getWorldBorder().getCenterZ()));
        }

        return blockpos;
    }

    public void setSpawnPoint(BlockPos pos) {
        this.data.setSpawnPoint(pos);
    }

    public void addEntityAlways(Entity entity) {
        int i = MathHelper.floor(entity.x / 16.0);
        int j = MathHelper.floor(entity.z / 16.0);
        int k = 2;

        for (int l = i - k; l <= i + k; l++) {
            for (int i1 = j - k; i1 <= j + k; i1++) {
                this.getChunkAt(l, i1);
            }
        }

        if (!this.entities.contains(entity)) {
            this.entities.add(entity);
        }
    }

    public boolean canModify(PlayerEntity player, BlockPos pos) {
        return true;
    }

    public void doEntityEvent(Entity entity, byte event) {
    }

    public ChunkSource getChunkSource() {
        return this.chunkSource;
    }

    public void addBlockEvent(BlockPos pos, Block block, int type, int data) {
        block.doEvent(this, pos, this.getBlockState(pos), type, data);
    }

    public WorldStorage getStorage() {
        return this.storage;
    }

    public WorldData getData() {
        return this.data;
    }

    public GameRules getGameRules() {
        return this.data.getGameRules();
    }

    public void updatePlayersSleepingStatus() {
    }

    public float getThunder(float tickDelta) {
        return (this.prevThunder + (this.thunder - this.prevThunder) * tickDelta) * this.getRain(tickDelta);
    }

    public void setThunder(float thunder) {
        this.prevThunder = thunder;
        this.thunder = thunder;
    }

    public float getRain(float tickDelta) {
        return this.prevRain + (this.rain - this.prevRain) * tickDelta;
    }

    public void setRain(float rain) {
        this.prevRain = rain;
        this.rain = rain;
    }

    public boolean isThundering() {
        return this.getThunder(1.0F) > 0.9;
    }

    public boolean isRaining() {
        return this.getRain(1.0F) > 0.2;
    }

    public boolean isRaining(BlockPos pos) {
        if (!this.isRaining()) {
            return false;
        }

        if (!this.hasSkyAccess(pos)) {
            return false;
        }

        if (this.getPrecipitationHeight(pos).getY() > pos.getY()) {
            return false;
        }

        Biome biome = this.getBiome(pos);
        return !biome.isSnowy() && !this.canSnowFall(pos, false) && biome.isRainy();
    }

    public boolean isHumid(BlockPos pos) {
        Biome biome = this.getBiome(pos);
        return biome.isHumid();
    }

    public SavedDataStorage getSavedDataStorage() {
        return this.savedDataStorage;
    }

    public void setSavedData(String id, SavedData data) {
        this.savedDataStorage.set(id, data);
    }

    public SavedData loadSavedData(Class<? extends SavedData> type, String id) {
        return this.savedDataStorage.load(type, id);
    }

    public int getSavedDataCount(String id) {
        return this.savedDataStorage.getNextCount(id);
    }

    public void doGlobalEvent(int type, BlockPos pos, int data) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            this.eventListeners.get(i).doGlobalEvent(type, pos, data);
        }
    }

    public void doEvent(int type, BlockPos pos, int data) {
        this.doEvent(null, type, pos, data);
    }

    public void doEvent(PlayerEntity source, int type, BlockPos pos, int data) {
        try {
            for (int i = 0; i < this.eventListeners.size(); i++) {
                this.eventListeners.get(i).doEvent(source, type, pos, data);
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Playing level event");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Level event being played");
            crashreportcategory.add("Block coordinates", CrashReportCategory.formatPosition(pos));
            crashreportcategory.add("Event source", source);
            crashreportcategory.add("Event type", type);
            crashreportcategory.add("Event data", data);
            throw new CrashException(crashreport);
        }
    }

    public int getHeight() {
        return 256;
    }

    public int getDimensionHeight() {
        return this.dimension.hasNoSky() ? 128 : 256;
    }

    public Random setRandomSeed(int x, int z, int seed) {
        long i = x * 341873128712L + z * 132897987541L + this.getData().getSeed() + seed;
        this.random.setSeed(i);
        return this.random;
    }

    public BlockPos findNearestStructure(String type, BlockPos pos) {
        return this.getChunkSource().findNearestStructure(this, type, pos);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    public double getHorizonHeight() {
        return this.data.getGeneratorType() == WorldGeneratorType.FLAT ? 0.0 : 63.0;
    }

    public CrashReportCategory populateCrashReport(CrashReport report) {
        CrashReportCategory crashreportcategory = report.addCategory("Affected level", 1);
        crashreportcategory.add("Level name", this.data == null ? "????" : this.data.getName());
        crashreportcategory.add("All players", new Callable<String>() {
            public String call() {
                return World.this.players.size() + " total; " + World.this.players.toString();
            }
        });
        crashreportcategory.add("Chunk stats", new Callable<String>() {
            public String call() {
                return World.this.chunkSource.getDebugInfo();
            }
        });

        try {
            this.data.populateCrashReport(crashreportcategory);
        } catch (Throwable throwable) {
            crashreportcategory.add("Level Data Unobtainable", throwable);
        }

        return crashreportcategory;
    }

    public void updateBlockMiningProgress(int id, BlockPos pos, int progress) {
        for (int i = 0; i < this.eventListeners.size(); i++) {
            WorldEventListener worldeventlistener = this.eventListeners.get(i);
            worldeventlistener.updateBlockMiningProgress(id, pos, progress);
        }
    }

    public Calendar getCalendar() {
        if (this.getTime() % 600L == 0L) {
            this.calendar.setTimeInMillis(MinecraftServer.getTimeMillis());
        }

        return this.calendar;
    }

    public void addFireworksParticle(double x, double y, double z, double velocityX, double velocityY, double velocityZ, NbtCompound nbt) {
    }

    public Scoreboard getScoreboard() {
        return this.scoreboard;
    }

    public void updateNeighborComparators(BlockPos pos, Block block) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.offset(direction);
            if (this.isChunkLoaded(blockpos)) {
                BlockState blockstate = this.getBlockState(blockpos);
                if (Blocks.COMPARATOR.isSameDiode(blockstate.getBlock())) {
                    blockstate.getBlock().neighborChanged(this, blockpos, blockstate, block);
                } else if (blockstate.getBlock().isSolid()) {
                    blockpos = blockpos.offset(direction);
                    blockstate = this.getBlockState(blockpos);
                    if (Blocks.COMPARATOR.isSameDiode(blockstate.getBlock())) {
                        blockstate.getBlock().neighborChanged(this, blockpos, blockstate, block);
                    }
                }
            }
        }
    }

    public LocalDifficulty getLocalDifficulty(BlockPos pos) {
        long i = 0L;
        float f = 0.0F;
        if (this.isChunkLoaded(pos)) {
            f = this.getMoonSize();
            i = this.getChunk(pos).getInhabitedTime();
        }

        return new LocalDifficulty(this.getDifficulty(), this.getTimeOfDay(), i, f);
    }

    public Difficulty getDifficulty() {
        return this.getData().getDifficulty();
    }

    public int getAmbientDarkness() {
        return this.ambientDarkness;
    }

    public void setAmbientDarkness(int ambientDarkness) {
        this.ambientDarkness = ambientDarkness;
    }

    public int getLightningCooldown() {
        return this.lightningCooldown;
    }

    public void setLightningCooldown(int ticks) {
        this.lightningCooldown = ticks;
    }

    public boolean isSearchingSpawnPoint() {
        return this.searchingSpawnPoint;
    }

    public SavedVillageData getVillages() {
        return this.villages;
    }

    public WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    public boolean isSpawnChunk(int chunkX, int chunkZ) {
        BlockPos blockpos = this.getSpawnPoint();
        int i = chunkX * 16 + 8 - blockpos.getX();
        int j = chunkZ * 16 + 8 - blockpos.getZ();
        int k = 128;
        return i >= -k && i <= k && j >= -k && j <= k;
    }
}
