package net.minecraft.world.gen.chunk;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;

public class DebugChunkGenerator implements ChunkSource {
    private static final List<BlockState> BLOCK_STATES = Lists.newArrayList();
    private static final int GRID_LENGTH;
    private static final int GRID_WIDTH;
    private final World world;

    public DebugChunkGenerator(World world) {
        this.world = world;
    }

    @Override
    public WorldChunk getChunk(int chunkX, int chunkZ) {
        BlockStateStorage blockstatestorage = new BlockStateStorage();

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = chunkX * 16 + i;
                int l = chunkZ * 16 + j;
                blockstatestorage.set(i, 60, j, Blocks.BARRIER.defaultState());
                BlockState blockstate = getBlockState(k, l);
                if (blockstate != null) {
                    blockstatestorage.set(i, 70, j, blockstate);
                }
            }
        }

        WorldChunk worldchunk = new WorldChunk(this.world, blockstatestorage, chunkX, chunkZ);
        worldchunk.populateHeightMap();
        Biome[] abiome = this.world.getBiomeSource().getBiomes(null, chunkX * 16, chunkZ * 16, 16, 16);
        byte[] abyte = worldchunk.getBiomes();

        for (int i1 = 0; i1 < abyte.length; i1++) {
            abyte[i1] = (byte)abiome[i1].id;
        }

        worldchunk.populateHeightMap();
        return worldchunk;
    }

    public static BlockState getBlockState(int x, int z) {
        BlockState blockstate = null;
        if (x > 0 && z > 0 && x % 2 != 0 && z % 2 != 0) {
            x /= 2;
            z /= 2;
            if (x <= GRID_LENGTH && z <= GRID_WIDTH) {
                int i = MathHelper.abs(x * GRID_LENGTH + z);
                if (i < BLOCK_STATES.size()) {
                    blockstate = BLOCK_STATES.get(i);
                }
            }
        }

        return blockstate;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public void populateChunk(ChunkSource source, int chunkX, int chunkZ) {
    }

    @Override
    public boolean populateChunkAfterTerrain(ChunkSource source, WorldChunk chunk, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean save(boolean saveEntities, ProgressListener listener) {
        return true;
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean tick() {
        return false;
    }

    @Override
    public boolean shouldSave() {
        return true;
    }

    @Override
    public String getDebugInfo() {
        return "DebugLevelSource";
    }

    @Override
    public List<Biome.SpawnEntry> getSpawnEntries(MobCategory category, BlockPos pos) {
        Biome biome = this.world.getBiome(pos);
        return biome.getSpawnEntries(category);
    }

    @Override
    public BlockPos findNearestStructure(World world, String type, BlockPos pos) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void placeStructures(WorldChunk chunk, int chunkX, int chunkZ) {
    }

    @Override
    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    static {
        for (Block block : Block.REGISTRY) {
            BLOCK_STATES.addAll(block.stateDefinition().all());
        }

        GRID_LENGTH = MathHelper.ceil(MathHelper.sqrt(BLOCK_STATES.size()));
        GRID_WIDTH = MathHelper.ceil((float)BLOCK_STATES.size() / GRID_LENGTH);
    }
}
