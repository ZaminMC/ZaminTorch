package net.minecraft.world.chunk;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeInstanceMultiMap;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.gen.chunk.DebugChunkGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorldChunk {
    private static final Logger LOGGER = LogManager.getLogger();
    private final WorldChunkSection[] sections = new WorldChunkSection[16];
    /**
     * The biome id for each column in this chunk.
     */
    private final byte[] biomes = new byte[256];
    /**
     * The minimum y-coordinate at which snow or rain can fall for each column in this chunk.
     */
    private final int[] precipitationHeight = new int[256];
    /**
     * Stores whether each column in this chunk needs a light update.
     */
    private final boolean[] recheckGap = new boolean[256];
    /**
     * True if  this chunk is currently loaded into the world.
     */
    private boolean loaded;
    private final World world;
    /**
     * The y-coordinate of the highest non-air block for each column in this chunk.
     */
    private final int[] heightMap;
    public final int chunkX;
    public final int chunkZ;
    private boolean recheckGaps;
    private final Map<BlockPos, BlockEntity> blockEntities = Maps.newHashMap();
    private final TypeInstanceMultiMap<Entity>[] entities;
    private boolean terrainPopulated;
    private boolean lightPopulated;
    /**
     * True if this chunk has ticked at least once.
     */
    private boolean hasTicked;
    /**
     * Used to check if this chunk has changes that need to be saved to disk.
     */
    private boolean dirty;
    /**
     * True if this chunk contains entities - used to decide how often to save this chunk to disk.
     */
    private boolean lastSaveHadEntities;
    private long lastSaveTime;
    /**
     * The lowest height among all columns of this chunk.
     */
    private int lowestHeight;
    /**
     * The number of ticks for which players have been present in this chunk - used for local difficulty.
     */
    private long inhabitedTime;
    private int nextBorderLightCheck = 4096;
    private ConcurrentLinkedQueue<BlockPos> blockEntitiesToCreate = Queues.newConcurrentLinkedQueue();

    public WorldChunk(World world, int chunkX, int chunkZ) {
        this.entities = new TypeInstanceMultiMap[16];
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.heightMap = new int[256];

        for (int i = 0; i < this.entities.length; i++) {
            this.entities[i] = new TypeInstanceMultiMap<>(Entity.class);
        }

        Arrays.fill(this.precipitationHeight, -999);
        Arrays.fill(this.biomes, (byte)-1);
    }

    public WorldChunk(World world, BlockStateStorage blocks, int chunkX, int chunkZ) {
        this(world, chunkX, chunkZ);
        int i = 256;
        boolean flag = !world.dimension.hasNoSky();

        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < i; l++) {
                    int i1 = j * i * 16 | k * i | l;
                    BlockState blockstate = blocks.get(i1);
                    if (blockstate.getBlock().getMaterial() != Material.AIR) {
                        int j1 = l >> 4;
                        if (this.sections[j1] == null) {
                            this.sections[j1] = new WorldChunkSection(j1 << 4, flag);
                        }

                        this.sections[j1].setBlockState(j, l & 15, k, blockstate);
                    }
                }
            }
        }
    }

    public boolean isAt(int chunkX, int chunkZ) {
        return chunkX == this.chunkX && chunkZ == this.chunkZ;
    }

    public int getHeight(BlockPos pos) {
        return this.getHeight(pos.getX() & 15, pos.getZ() & 15);
    }

    public int getHeight(int localX, int localZ) {
        return this.heightMap[localZ << 4 | localX];
    }

    public int getHighestSectionOffset() {
        for (int i = this.sections.length - 1; i >= 0; i--) {
            if (this.sections[i] != null) {
                return this.sections[i].getOffsetY();
            }
        }

        return 0;
    }

    public WorldChunkSection[] getSections() {
        return this.sections;
    }

    protected void populateHeightMapOnly() {
        int i = this.getHighestSectionOffset();
        this.lowestHeight = Integer.MAX_VALUE;

        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                this.precipitationHeight[j + (k << 4)] = -999;

                for (int l = i + 16; l > 0; l--) {
                    Block block = this.getBlockAt(j, l - 1, k);
                    if (block.getOpacity() != 0) {
                        this.heightMap[k << 4 | j] = l;
                        if (l < this.lowestHeight) {
                            this.lowestHeight = l;
                        }
                        break;
                    }
                }
            }
        }

        this.dirty = true;
    }

    public void populateHeightMap() {
        int i = this.getHighestSectionOffset();
        this.lowestHeight = Integer.MAX_VALUE;

        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                this.precipitationHeight[j + (k << 4)] = -999;

                for (int l = i + 16; l > 0; l--) {
                    if (this.getOpacityAt(j, l - 1, k) != 0) {
                        this.heightMap[k << 4 | j] = l;
                        if (l < this.lowestHeight) {
                            this.lowestHeight = l;
                        }
                        break;
                    }
                }

                if (!this.world.dimension.hasNoSky()) {
                    int k1 = 15;
                    int i1 = i + 16 - 1;

                    while (true) {
                        int j1 = this.getOpacityAt(j, i1, k);
                        if (j1 == 0 && k1 != 15) {
                            j1 = 1;
                        }

                        k1 -= j1;
                        if (k1 > 0) {
                            WorldChunkSection worldchunksection = this.sections[i1 >> 4];
                            if (worldchunksection != null) {
                                worldchunksection.setSkyLight(j, i1 & 15, k, k1);
                                this.world.notifyLightChanged(new BlockPos((this.chunkX << 4) + j, i1, (this.chunkZ << 4) + k));
                            }
                        }

                        if (--i1 <= 0 || k1 <= 0) {
                            break;
                        }
                    }
                }
            }
        }

        this.dirty = true;
    }

    private void recheckGap(int localX, int localZ) {
        this.recheckGap[localX + localZ * 16] = true;
        this.recheckGaps = true;
    }

    private void lightGaps(boolean checkOne) {
        this.world.profiler.push("recheckGaps");
        if (this.world.isAreaLoaded(new BlockPos(this.chunkX * 16 + 8, 0, this.chunkZ * 16 + 8), 16)) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    if (this.recheckGap[i + j * 16]) {
                        this.recheckGap[i + j * 16] = false;
                        int k = this.getHeight(i, j);
                        int l = this.chunkX * 16 + i;
                        int i1 = this.chunkZ * 16 + j;
                        int j1 = Integer.MAX_VALUE;

                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            j1 = Math.min(j1, this.world.getLowestHeight(l + direction.getOffsetX(), i1 + direction.getOffsetZ()));
                        }

                        this.lightGap(l, i1, j1);

                        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                            this.lightGap(l + direction1.getOffsetX(), i1 + direction1.getOffsetZ(), k);
                        }

                        if (checkOne) {
                            this.world.profiler.pop();
                            return;
                        }
                    }
                }
            }

            this.recheckGaps = false;
        }

        this.world.profiler.pop();
    }

    private void lightGap(int x, int z, int y) {
        int i = this.world.getHeight(new BlockPos(x, 0, z)).getY();
        if (i > y) {
            this.updateSkyLight(x, z, y, i + 1);
        } else if (i < y) {
            this.updateSkyLight(x, z, i, y + 1);
        }
    }

    private void updateSkyLight(int x, int z, int minY, int maxY) {
        if (maxY > minY && this.world.isAreaLoaded(new BlockPos(x, 0, z), 16)) {
            for (int i = minY; i < maxY; i++) {
                this.world.updateLight(LightType.SKY, new BlockPos(x, i, z));
            }

            this.dirty = true;
        }
    }

    private void updateHeightMap(int localX, int y, int localZ) {
        int i = this.heightMap[localZ << 4 | localX] & 0xFF;
        int j = i;
        if (y > i) {
            j = y;
        }

        while (j > 0 && this.getOpacityAt(localX, j - 1, localZ) == 0) {
            j--;
        }

        if (j != i) {
            this.world.onHeightMapChanged(localX + this.chunkX * 16, localZ + this.chunkZ * 16, j, i);
            this.heightMap[localZ << 4 | localX] = j;
            int k = this.chunkX * 16 + localX;
            int l = this.chunkZ * 16 + localZ;
            if (!this.world.dimension.hasNoSky()) {
                if (j < i) {
                    for (int k1 = j; k1 < i; k1++) {
                        WorldChunkSection worldchunksection2 = this.sections[k1 >> 4];
                        if (worldchunksection2 != null) {
                            worldchunksection2.setSkyLight(localX, k1 & 15, localZ, 15);
                            this.world.notifyLightChanged(new BlockPos((this.chunkX << 4) + localX, k1, (this.chunkZ << 4) + localZ));
                        }
                    }
                } else {
                    for (int i1 = i; i1 < j; i1++) {
                        WorldChunkSection worldchunksection = this.sections[i1 >> 4];
                        if (worldchunksection != null) {
                            worldchunksection.setSkyLight(localX, i1 & 15, localZ, 0);
                            this.world.notifyLightChanged(new BlockPos((this.chunkX << 4) + localX, i1, (this.chunkZ << 4) + localZ));
                        }
                    }
                }

                int l1 = 15;

                while (j > 0 && l1 > 0) {
                    int j2 = this.getOpacityAt(localX, --j, localZ);
                    if (j2 == 0) {
                        j2 = 1;
                    }

                    l1 -= j2;
                    if (l1 < 0) {
                        l1 = 0;
                    }

                    WorldChunkSection worldchunksection1 = this.sections[j >> 4];
                    if (worldchunksection1 != null) {
                        worldchunksection1.setSkyLight(localX, j & 15, localZ, l1);
                    }
                }
            }

            int i2 = this.heightMap[localZ << 4 | localX];
            int k2 = i;
            int l2 = i2;
            if (l2 < k2) {
                int j1 = k2;
                k2 = l2;
                l2 = j1;
            }

            if (i2 < this.lowestHeight) {
                this.lowestHeight = i2;
            }

            if (!this.world.dimension.hasNoSky()) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    this.updateSkyLight(k + direction.getOffsetX(), l + direction.getOffsetZ(), k2, l2);
                }

                this.updateSkyLight(k, l, k2, l2);
            }

            this.dirty = true;
        }
    }

    public int getOpacity(BlockPos pos) {
        return this.getBlock(pos).getOpacity();
    }

    private int getOpacityAt(int localX, int y, int localZ) {
        return this.getBlockAt(localX, y, localZ).getOpacity();
    }

    private Block getBlockAt(int localX, int y, int localZ) {
        Block block = Blocks.AIR;
        if (y >= 0 && y >> 4 < this.sections.length) {
            WorldChunkSection worldchunksection = this.sections[y >> 4];
            if (worldchunksection != null) {
                try {
                    block = worldchunksection.getBlock(localX, y & 15, localZ);
                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.of(throwable, "Getting block");
                    throw new CrashException(crashreport);
                }
            }
        }

        return block;
    }

    public Block getBlock(int x, int y, int z) {
        try {
            return this.getBlockAt(x & 15, y, z & 15);
        } catch (CrashException crashexception) {
            CrashReportCategory crashreportcategory = crashexception.getReport().addCategory("Block being got");
            crashreportcategory.add("Location", new Callable<String>() {
                public String call() throws Exception {
                    return CrashReportCategory.formatPosition(new BlockPos(WorldChunk.this.chunkX * 16 + x, y, WorldChunk.this.chunkZ * 16 + z));
                }
            });
            throw crashexception;
        }
    }

    public Block getBlock(BlockPos pos) {
        try {
            return this.getBlockAt(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        } catch (CrashException crashexception) {
            CrashReportCategory crashreportcategory = crashexception.getReport().addCategory("Block being got");
            crashreportcategory.add("Location", new Callable<String>() {
                public String call() throws Exception {
                    return CrashReportCategory.formatPosition(pos);
                }
            });
            throw crashexception;
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        if (this.world.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            BlockState blockstate = null;
            if (pos.getY() == 60) {
                blockstate = Blocks.BARRIER.defaultState();
            }

            if (pos.getY() == 70) {
                blockstate = DebugChunkGenerator.getBlockState(pos.getX(), pos.getZ());
            }

            return blockstate == null ? Blocks.AIR.defaultState() : blockstate;
        } else {
            try {
                if (pos.getY() >= 0 && pos.getY() >> 4 < this.sections.length) {
                    WorldChunkSection worldchunksection = this.sections[pos.getY() >> 4];
                    if (worldchunksection != null) {
                        int j = pos.getX() & 15;
                        int k = pos.getY() & 15;
                        int i = pos.getZ() & 15;
                        return worldchunksection.getBlockState(j, k, i);
                    }
                }

                return Blocks.AIR.defaultState();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Getting block state");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Block being got");
                crashreportcategory.add("Location", new Callable<String>() {
                    public String call() throws Exception {
                        return CrashReportCategory.formatPosition(pos);
                    }
                });
                throw new CrashException(crashreport);
            }
        }
    }

    private int getBlockMetadataAt(int localX, int y, int localZ) {
        if (y >> 4 >= this.sections.length) {
            return 0;
        }

        WorldChunkSection worldchunksection = this.sections[y >> 4];
        return worldchunksection != null ? worldchunksection.getBlockMetadata(localX, y & 15, localZ) : 0;
    }

    public int getBlockMetadata(BlockPos pos) {
        return this.getBlockMetadataAt(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    public BlockState setBlockState(BlockPos pos, BlockState state) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;
        int l = k << 4 | i;
        if (j >= this.precipitationHeight[l] - 1) {
            this.precipitationHeight[l] = -999;
        }

        int i1 = this.heightMap[l];
        BlockState blockstate = this.getBlockState(pos);
        if (blockstate == state) {
            return null;
        }

        Block block = state.getBlock();
        Block block1 = blockstate.getBlock();
        WorldChunkSection worldchunksection = this.sections[j >> 4];
        boolean flag = false;
        if (worldchunksection == null) {
            if (block == Blocks.AIR) {
                return null;
            }

            worldchunksection = this.sections[j >> 4] = new WorldChunkSection(j >> 4 << 4, !this.world.dimension.hasNoSky());
            flag = j >= i1;
        }

        worldchunksection.setBlockState(i, j & 15, k, state);
        if (block1 != block) {
            if (!this.world.isClient) {
                block1.onRemoved(this.world, pos, blockstate);
            } else if (block1 instanceof BlockEntityProvider) {
                this.world.removeBlockEntity(pos);
            }
        }

        if (worldchunksection.getBlock(i, j & 15, k) != block) {
            return null;
        }

        if (flag) {
            this.populateHeightMap();
        } else {
            int j1 = block.getOpacity();
            int k1 = block1.getOpacity();
            if (j1 > 0) {
                if (j >= i1) {
                    this.updateHeightMap(i, j + 1, k);
                }
            } else if (j == i1 - 1) {
                this.updateHeightMap(i, j, k);
            }

            if (j1 != k1 && (j1 < k1 || this.getLight(LightType.SKY, pos) > 0 || this.getLight(LightType.BLOCK, pos) > 0)) {
                this.recheckGap(i, k);
            }
        }

        if (block1 instanceof BlockEntityProvider) {
            BlockEntity blockentity = this.getBlockEntity(pos, WorldChunk.BlockEntityCreationType.CHECK);
            if (blockentity != null) {
                blockentity.clearBlockCache();
            }
        }

        if (!this.world.isClient && block1 != block) {
            block.onAdded(this.world, pos, state);
        }

        if (block instanceof BlockEntityProvider) {
            BlockEntity blockentity1 = this.getBlockEntity(pos, WorldChunk.BlockEntityCreationType.CHECK);
            if (blockentity1 == null) {
                blockentity1 = ((BlockEntityProvider)block).createBlockEntity(this.world, block.getMetadataFromState(state));
                this.world.setBlockEntity(pos, blockentity1);
            }

            if (blockentity1 != null) {
                blockentity1.clearBlockCache();
            }
        }

        this.dirty = true;
        return blockstate;
    }

    public int getLight(LightType type, BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;
        WorldChunkSection worldchunksection = this.sections[j >> 4];
        if (worldchunksection == null) {
            return this.hasSkyAccess(pos) ? type.defaultValue : 0;
        } else if (type == LightType.SKY) {
            return this.world.dimension.hasNoSky() ? 0 : worldchunksection.getSkyLight(i, j & 15, k);
        } else {
            return type == LightType.BLOCK ? worldchunksection.getBlockLight(i, j & 15, k) : type.defaultValue;
        }
    }

    public void setLight(LightType type, BlockPos pos, int light) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;
        WorldChunkSection worldchunksection = this.sections[j >> 4];
        if (worldchunksection == null) {
            worldchunksection = this.sections[j >> 4] = new WorldChunkSection(j >> 4 << 4, !this.world.dimension.hasNoSky());
            this.populateHeightMap();
        }

        this.dirty = true;
        if (type == LightType.SKY) {
            if (!this.world.dimension.hasNoSky()) {
                worldchunksection.setSkyLight(i, j & 15, k, light);
            }
        } else if (type == LightType.BLOCK) {
            worldchunksection.setBlockLight(i, j & 15, k, light);
        }
    }

    public int getLight(BlockPos pos, int ambientDarkness) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;
        WorldChunkSection worldchunksection = this.sections[j >> 4];
        if (worldchunksection == null) {
            return !this.world.dimension.hasNoSky() && ambientDarkness < LightType.SKY.defaultValue ? LightType.SKY.defaultValue - ambientDarkness : 0;
        }

        int l = this.world.dimension.hasNoSky() ? 0 : worldchunksection.getSkyLight(i, j & 15, k);
        l -= ambientDarkness;
        int i1 = worldchunksection.getBlockLight(i, j & 15, k);
        if (i1 > l) {
            l = i1;
        }

        return l;
    }

    public void addEntity(Entity entity) {
        this.lastSaveHadEntities = true;
        int i = MathHelper.floor(entity.x / 16.0);
        int j = MathHelper.floor(entity.z / 16.0);
        if (i != this.chunkX || j != this.chunkZ) {
            LOGGER.warn("Wrong location! (" + i + ", " + j + ") should be (" + this.chunkX + ", " + this.chunkZ + "), " + entity, new Object[]{entity});
            entity.remove();
        }

        int k = MathHelper.floor(entity.y / 16.0);
        if (k < 0) {
            k = 0;
        }

        if (k >= this.entities.length) {
            k = this.entities.length - 1;
        }

        entity.inChunk = true;
        entity.chunkX = this.chunkX;
        entity.chunkY = k;
        entity.chunkZ = this.chunkZ;
        this.entities[k].add(entity);
    }

    public void removeEntity(Entity entity) {
        this.removeEntity(entity, entity.chunkY);
    }

    public void removeEntity(Entity entity, int chunkY) {
        if (chunkY < 0) {
            chunkY = 0;
        }

        if (chunkY >= this.entities.length) {
            chunkY = this.entities.length - 1;
        }

        this.entities[chunkY].remove(entity);
    }

    public boolean hasSkyAccess(BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;
        return j >= this.heightMap[k << 4 | i];
    }

    private BlockEntity createBlockEntity(BlockPos pos) {
        Block block = this.getBlock(pos);
        return !block.hasBlockEntity() ? null : ((BlockEntityProvider)block).createBlockEntity(this.world, this.getBlockMetadata(pos));
    }

    public BlockEntity getBlockEntity(BlockPos pos, WorldChunk.BlockEntityCreationType creationType) {
        BlockEntity blockentity = this.blockEntities.get(pos);
        if (blockentity == null) {
            if (creationType == WorldChunk.BlockEntityCreationType.IMMEDIATE) {
                blockentity = this.createBlockEntity(pos);
                this.world.setBlockEntity(pos, blockentity);
            } else if (creationType == WorldChunk.BlockEntityCreationType.QUEUED) {
                this.blockEntitiesToCreate.add(pos);
            }
        } else if (blockentity.isRemoved()) {
            this.blockEntities.remove(pos);
            return null;
        }

        return blockentity;
    }

    public void addBlockEntity(BlockEntity blockEntity) {
        this.setBlockEntity(blockEntity.getPos(), blockEntity);
        if (this.loaded) {
            this.world.addBlockEntity(blockEntity);
        }
    }

    public void setBlockEntity(BlockPos pos, BlockEntity blockEntity) {
        blockEntity.setWorld(this.world);
        blockEntity.setPos(pos);
        if (this.getBlock(pos) instanceof BlockEntityProvider) {
            if (this.blockEntities.containsKey(pos)) {
                this.blockEntities.get(pos).markRemoved();
            }

            blockEntity.cancelRemoval();
            this.blockEntities.put(pos, blockEntity);
        }
    }

    public void removeBlockEntity(BlockPos pos) {
        if (this.loaded) {
            BlockEntity blockentity = this.blockEntities.remove(pos);
            if (blockentity != null) {
                blockentity.markRemoved();
            }
        }
    }

    public void load() {
        this.loaded = true;
        this.world.loadBlockEntities(this.blockEntities.values());

        for (int i = 0; i < this.entities.length; i++) {
            for (Entity entity : this.entities[i]) {
                entity.load();
            }

            this.world.loadEntities(this.entities[i]);
        }
    }

    public void unload() {
        this.loaded = false;

        for (BlockEntity blockentity : this.blockEntities.values()) {
            this.world.unloadBlockEntity(blockentity);
        }

        for (int i = 0; i < this.entities.length; i++) {
            this.world.unloadEntities(this.entities[i]);
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void getEntities(Entity exclude, Box bounds, List<Entity> entities, Predicate<? super Entity> filter) {
        int i = MathHelper.floor((bounds.minY - 2.0) / 16.0);
        int j = MathHelper.floor((bounds.maxY + 2.0) / 16.0);
        i = MathHelper.clamp(i, 0, this.entities.length - 1);
        j = MathHelper.clamp(j, 0, this.entities.length - 1);

        for (int k = i; k <= j; k++) {
            if (!this.entities[k].isEmpty()) {
                for (Entity entity : this.entities[k]) {
                    if (entity.getShape().intersects(bounds) && entity != exclude) {
                        if (filter == null || filter.apply(entity)) {
                            entities.add(entity);
                        }

                        Entity[] aentity = entity.getParts();
                        if (aentity != null) {
                            for (int l = 0; l < aentity.length; l++) {
                                entity = aentity[l];
                                if (entity != exclude && entity.getShape().intersects(bounds) && (filter == null || filter.apply(entity))) {
                                    entities.add(entity);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public <T extends Entity> void getEntitiesOfType(Class<? extends T> type, Box bounds, List<T> entities, Predicate<? super T> filter) {
        int i = MathHelper.floor((bounds.minY - 2.0) / 16.0);
        int j = MathHelper.floor((bounds.maxY + 2.0) / 16.0);
        i = MathHelper.clamp(i, 0, this.entities.length - 1);
        j = MathHelper.clamp(j, 0, this.entities.length - 1);

        for (int k = i; k <= j; k++) {
            for (T t : this.entities[k].find(type)) {
                if (t.getShape().intersects(bounds) && (filter == null || filter.apply(t))) {
                    entities.add(t);
                }
            }
        }
    }

    public boolean shouldSave(boolean saveEntities) {
        if (saveEntities) {
            if (this.lastSaveHadEntities && this.world.getTime() != this.lastSaveTime || this.dirty) {
                return true;
            }
        } else if (this.lastSaveHadEntities && this.world.getTime() >= this.lastSaveTime + 600L) {
            return true;
        }

        return this.dirty;
    }

    public Random getRandomForSlime(long seed) {
        return new Random(
            this.world.getSeed() + this.chunkX * this.chunkX * 4987142 + this.chunkX * 5947611 + this.chunkZ * this.chunkZ * 4392871L + this.chunkZ * 389711
                ^ seed
        );
    }

    public boolean isEmpty() {
        return false;
    }

    public void populate(ChunkSource source, ChunkSource generator, int chunkX, int chunkZ) {
        boolean flag = source.hasChunk(chunkX, chunkZ - 1);
        boolean flag1 = source.hasChunk(chunkX + 1, chunkZ);
        boolean flag2 = source.hasChunk(chunkX, chunkZ + 1);
        boolean flag3 = source.hasChunk(chunkX - 1, chunkZ);
        boolean flag4 = source.hasChunk(chunkX - 1, chunkZ - 1);
        boolean flag5 = source.hasChunk(chunkX + 1, chunkZ + 1);
        boolean flag6 = source.hasChunk(chunkX - 1, chunkZ + 1);
        boolean flag7 = source.hasChunk(chunkX + 1, chunkZ - 1);
        if (flag1 && flag2 && flag5) {
            if (!this.terrainPopulated) {
                source.populateChunk(generator, chunkX, chunkZ);
            } else {
                source.populateChunkAfterTerrain(generator, this, chunkX, chunkZ);
            }
        }

        if (flag3 && flag2 && flag6) {
            WorldChunk worldchunk = source.getChunk(chunkX - 1, chunkZ);
            if (!worldchunk.terrainPopulated) {
                source.populateChunk(generator, chunkX - 1, chunkZ);
            } else {
                source.populateChunkAfterTerrain(generator, worldchunk, chunkX - 1, chunkZ);
            }
        }

        if (flag && flag1 && flag7) {
            WorldChunk worldchunk1 = source.getChunk(chunkX, chunkZ - 1);
            if (!worldchunk1.terrainPopulated) {
                source.populateChunk(generator, chunkX, chunkZ - 1);
            } else {
                source.populateChunkAfterTerrain(generator, worldchunk1, chunkX, chunkZ - 1);
            }
        }

        if (flag4 && flag && flag3) {
            WorldChunk worldchunk2 = source.getChunk(chunkX - 1, chunkZ - 1);
            if (!worldchunk2.terrainPopulated) {
                source.populateChunk(generator, chunkX - 1, chunkZ - 1);
            } else {
                source.populateChunkAfterTerrain(generator, worldchunk2, chunkX - 1, chunkZ - 1);
            }
        }
    }

    public BlockPos getPrecipitationHeight(BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getZ() & 15;
        int k = i | j << 4;
        BlockPos blockpos = new BlockPos(pos.getX(), this.precipitationHeight[k], pos.getZ());
        if (blockpos.getY() == -999) {
            int l = this.getHighestSectionOffset() + 15;
            blockpos = new BlockPos(pos.getX(), l, pos.getZ());
            int i1 = -1;

            while (blockpos.getY() > 0 && i1 == -1) {
                Block block = this.getBlock(blockpos);
                Material material = block.getMaterial();
                if (!material.blocksMovement() && !material.isLiquid()) {
                    blockpos = blockpos.down();
                } else {
                    i1 = blockpos.getY() + 1;
                }
            }

            this.precipitationHeight[k] = i1;
        }

        return new BlockPos(pos.getX(), this.precipitationHeight[k], pos.getZ());
    }

    public void tick(boolean skipCheckGaps) {
        if (this.recheckGaps && !this.world.dimension.hasNoSky() && !skipCheckGaps) {
            this.lightGaps(this.world.isClient);
        }

        this.hasTicked = true;
        if (!this.lightPopulated && this.terrainPopulated) {
            this.populateLight();
        }

        while (!this.blockEntitiesToCreate.isEmpty()) {
            BlockPos blockpos = this.blockEntitiesToCreate.poll();
            if (this.getBlockEntity(blockpos, WorldChunk.BlockEntityCreationType.CHECK) == null && this.getBlock(blockpos).hasBlockEntity()) {
                BlockEntity blockentity = this.createBlockEntity(blockpos);
                this.world.setBlockEntity(blockpos, blockentity);
                this.world.notifyRegionChanged(blockpos, blockpos);
            }
        }
    }

    public boolean isPopulated() {
        return this.hasTicked && this.terrainPopulated && this.lightPopulated;
    }

    public ChunkPos getPos() {
        return new ChunkPos(this.chunkX, this.chunkZ);
    }

    public boolean isEmpty(int minY, int maxY) {
        if (minY < 0) {
            minY = 0;
        }

        if (maxY >= 256) {
            maxY = 255;
        }

        for (int i = minY; i <= maxY; i += 16) {
            WorldChunkSection worldchunksection = this.sections[i >> 4];
            if (worldchunksection != null && !worldchunksection.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public void setSections(WorldChunkSection[] sections) {
        if (this.sections.length != sections.length) {
            LOGGER.warn("Could not set level chunk sections, array length is " + sections.length + " instead of " + this.sections.length);
        } else {
            for (int i = 0; i < this.sections.length; i++) {
                this.sections[i] = sections[i];
            }
        }
    }

    public void update(byte[] data, int sections, boolean full) {
        int i = 0;
        boolean flag = !this.world.dimension.hasNoSky();

        for (int j = 0; j < this.sections.length; j++) {
            if ((sections & 1 << j) != 0) {
                if (this.sections[j] == null) {
                    this.sections[j] = new WorldChunkSection(j << 4, flag);
                }

                char[] achar = this.sections[j].getBlockStates();

                for (int k = 0; k < achar.length; k++) {
                    achar[k] = (char)((data[i + 1] & 255) << 8 | data[i] & 0xFF);
                    i += 2;
                }
            } else if (full && this.sections[j] != null) {
                this.sections[j] = null;
            }
        }

        for (int l = 0; l < this.sections.length; l++) {
            if ((sections & 1 << l) != 0 && this.sections[l] != null) {
                ChunkNibbleStorage chunknibblestorage = this.sections[l].getBlockLightStorage();
                System.arraycopy(data, i, chunknibblestorage.getData(), 0, chunknibblestorage.getData().length);
                i += chunknibblestorage.getData().length;
            }
        }

        if (flag) {
            for (int i1 = 0; i1 < this.sections.length; i1++) {
                if ((sections & 1 << i1) != 0 && this.sections[i1] != null) {
                    ChunkNibbleStorage chunknibblestorage1 = this.sections[i1].getSkyLightStorage();
                    System.arraycopy(data, i, chunknibblestorage1.getData(), 0, chunknibblestorage1.getData().length);
                    i += chunknibblestorage1.getData().length;
                }
            }
        }

        if (full) {
            System.arraycopy(data, i, this.biomes, 0, this.biomes.length);
            i += this.biomes.length;
        }

        for (int j1 = 0; j1 < this.sections.length; j1++) {
            if (this.sections[j1] != null && (sections & 1 << j1) != 0) {
                this.sections[j1].validateBlockCounters();
            }
        }

        this.lightPopulated = true;
        this.terrainPopulated = true;
        this.populateHeightMapOnly();

        for (BlockEntity blockentity : this.blockEntities.values()) {
            blockentity.clearBlockCache();
        }
    }

    public Biome getBiome(BlockPos pos, BiomeSource source) {
        int i = pos.getX() & 15;
        int j = pos.getZ() & 15;
        int k = this.biomes[j << 4 | i] & 255;
        if (k == 255) {
            Biome biome = source.getBiome(pos, Biome.PLAINS);
            k = biome.id;
            this.biomes[j << 4 | i] = (byte)(k & 0xFF);
        }

        Biome biome1 = Biome.byId(k);
        return biome1 == null ? Biome.PLAINS : biome1;
    }

    public byte[] getBiomes() {
        return this.biomes;
    }

    public void setBiomes(byte[] biomes) {
        if (this.biomes.length != biomes.length) {
            LOGGER.warn("Could not set level chunk biomes, array length is " + biomes.length + " instead of " + this.biomes.length);
        } else {
            for (int i = 0; i < this.biomes.length; i++) {
                this.biomes[i] = biomes[i];
            }
        }
    }

    public void resetBorderLightChecks() {
        this.nextBorderLightCheck = 0;
    }

    public void checkBorderLight() {
        BlockPos blockpos = new BlockPos(this.chunkX << 4, 0, this.chunkZ << 4);

        for (int i = 0; i < 8; i++) {
            if (this.nextBorderLightCheck >= 4096) {
                return;
            }

            int j = this.nextBorderLightCheck % 16;
            int k = this.nextBorderLightCheck / 16 % 16;
            int l = this.nextBorderLightCheck / 256;
            this.nextBorderLightCheck++;

            for (int i1 = 0; i1 < 16; i1++) {
                BlockPos blockpos1 = blockpos.add(k, (j << 4) + i1, l);
                boolean flag = i1 == 0 || i1 == 15 || k == 0 || k == 15 || l == 0 || l == 15;
                if (this.sections[j] == null && flag || this.sections[j] != null && this.sections[j].getBlock(k, i1, l).getMaterial() == Material.AIR) {
                    for (Direction direction : Direction.values()) {
                        BlockPos blockpos2 = blockpos1.offset(direction);
                        if (this.world.getBlockState(blockpos2).getBlock().getLight() > 0) {
                            this.world.checkLight(blockpos2);
                        }
                    }

                    this.world.checkLight(blockpos1);
                }
            }
        }
    }

    public void populateLight() {
        this.terrainPopulated = true;
        this.lightPopulated = true;
        BlockPos blockpos = new BlockPos(this.chunkX << 4, 0, this.chunkZ << 4);
        if (!this.world.dimension.hasNoSky()) {
            if (this.world.isAreaLoaded(blockpos.add(-1, 0, -1), blockpos.add(16, this.world.getSeaLevel(), 16))) {
                label44:
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        if (!this.checkLightAt(i, j)) {
                            this.lightPopulated = false;
                            break label44;
                        }
                    }
                }

                if (this.lightPopulated) {
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        int k = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 : 1;
                        this.world.getChunk(blockpos.offset(direction, k)).checkBorderLight(direction.getOpposite());
                    }

                    this.queueLightUpdates();
                }
            } else {
                this.lightPopulated = false;
            }
        }
    }

    private void queueLightUpdates() {
        for (int i = 0; i < this.recheckGap.length; i++) {
            this.recheckGap[i] = true;
        }

        this.lightGaps(false);
    }

    private void checkBorderLight(Direction side) {
        if (this.terrainPopulated) {
            if (side == Direction.EAST) {
                for (int i = 0; i < 16; i++) {
                    this.checkLightAt(15, i);
                }
            } else if (side == Direction.WEST) {
                for (int j = 0; j < 16; j++) {
                    this.checkLightAt(0, j);
                }
            } else if (side == Direction.SOUTH) {
                for (int k = 0; k < 16; k++) {
                    this.checkLightAt(k, 15);
                }
            } else if (side == Direction.NORTH) {
                for (int l = 0; l < 16; l++) {
                    this.checkLightAt(l, 0);
                }
            }
        }
    }

    private boolean checkLightAt(int localX, int localZ) {
        int i = this.getHighestSectionOffset();
        boolean flag = false;
        boolean flag1 = false;
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable((this.chunkX << 4) + localX, 0, (this.chunkZ << 4) + localZ);

        for (int j = i + 16 - 1; j > this.world.getSeaLevel() || j > 0 && !flag1; j--) {
            blockpos$mutable.set(blockpos$mutable.getX(), j, blockpos$mutable.getZ());
            int k = this.getOpacity(blockpos$mutable);
            if (k == 255 && blockpos$mutable.getY() < this.world.getSeaLevel()) {
                flag1 = true;
            }

            if (!flag && k > 0) {
                flag = true;
            } else if (flag && k == 0 && !this.world.checkLight(blockpos$mutable)) {
                return false;
            }
        }

        for (int l = blockpos$mutable.getY(); l > 0; l--) {
            blockpos$mutable.set(blockpos$mutable.getX(), l, blockpos$mutable.getZ());
            if (this.getBlock(blockpos$mutable).getLight() > 0) {
                this.world.checkLight(blockpos$mutable);
            }
        }

        return true;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public World getWorld() {
        return this.world;
    }

    public int[] getHeightMap() {
        return this.heightMap;
    }

    public void setHeightMap(int[] heightMap) {
        if (this.heightMap.length != heightMap.length) {
            LOGGER.warn("Could not set level chunk heightmap, array length is " + heightMap.length + " instead of " + this.heightMap.length);
        } else {
            for (int i = 0; i < this.heightMap.length; i++) {
                this.heightMap[i] = heightMap[i];
            }
        }
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public TypeInstanceMultiMap<Entity>[] getEntities() {
        return this.entities;
    }

    public boolean isTerrainPopulated() {
        return this.terrainPopulated;
    }

    public void setTerrainPopulated(boolean populated) {
        this.terrainPopulated = populated;
    }

    public boolean isLightPopulated() {
        return this.lightPopulated;
    }

    public void setLightPopulated(boolean populated) {
        this.lightPopulated = populated;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void setHasEntities(boolean hasEntities) {
        this.lastSaveHadEntities = hasEntities;
    }

    public void setLastSaveTime(long lastSaveTime) {
        this.lastSaveTime = lastSaveTime;
    }

    public int getLowestHeight() {
        return this.lowestHeight;
    }

    public long getInhabitedTime() {
        return this.inhabitedTime;
    }

    public void setInhabitedTime(long time) {
        this.inhabitedTime = time;
    }

    public enum BlockEntityCreationType {
        IMMEDIATE,
        QUEUED,
        CHECK;
    }
}
