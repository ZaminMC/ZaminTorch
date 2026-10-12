package net.minecraft.client.world.chunk;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.Long2ObjectHashMap;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientChunkCache implements ChunkSource {
    private static final Logger LOGGER = LogManager.getLogger();
    private WorldChunk empty;
    private Long2ObjectHashMap<WorldChunk> chunksByPos = new Long2ObjectHashMap<>();
    private List<WorldChunk> chunks = Lists.newArrayList();
    private World world;

    public ClientChunkCache(World world) {
        this.empty = new EmptyChunk(world, 0, 0);
        this.world = world;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    /**
     * Unloads the world chunk at the given chunk coordinates from memory.
     */
    public void unloadChunk(int chunkX, int chunkZ) {
        WorldChunk worldchunk = this.getChunk(chunkX, chunkZ);
        if (!worldchunk.isEmpty()) {
            worldchunk.unload();
        }

        this.chunksByPos.remove(ChunkPos.toLong(chunkX, chunkZ));
        this.chunks.remove(worldchunk);
    }

    /**
     * Loads a new world chunk at the given chunk coordinates into memory.
     */
    public WorldChunk loadChunk(int chunkX, int chunkZ) {
        WorldChunk worldchunk = new WorldChunk(this.world, chunkX, chunkZ);
        this.chunksByPos.put(ChunkPos.toLong(chunkX, chunkZ), worldchunk);
        this.chunks.add(worldchunk);
        worldchunk.setLoaded(true);
        return worldchunk;
    }

    @Override
    public WorldChunk getChunk(int chunkX, int chunkZ) {
        WorldChunk worldchunk = this.chunksByPos.get(ChunkPos.toLong(chunkX, chunkZ));
        return worldchunk == null ? this.empty : worldchunk;
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
        long i = System.currentTimeMillis();

        for (WorldChunk worldchunk : this.chunks) {
            worldchunk.tick(System.currentTimeMillis() - i > 5L);
        }

        if (System.currentTimeMillis() - i > 100L) {
            LOGGER.info("Warning: Clientside chunk ticking took {} ms", new Object[]{System.currentTimeMillis() - i});
        }

        return false;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public void populateChunk(ChunkSource source, int chunkX, int chunkZ) {
    }

    @Override
    public boolean populateChunkAfterTerrain(ChunkSource source, WorldChunk chunk, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public String getDebugInfo() {
        return "MultiplayerChunkCache: " + this.chunksByPos.size() + ", " + this.chunks.size();
    }

    @Override
    public List<Biome.SpawnEntry> getSpawnEntries(MobCategory category, BlockPos pos) {
        return null;
    }

    @Override
    public BlockPos findNearestStructure(World world, String type, BlockPos pos) {
        return null;
    }

    @Override
    public int size() {
        return this.chunks.size();
    }

    @Override
    public void placeStructures(WorldChunk chunk, int chunkX, int chunkZ) {
    }

    @Override
    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
