package net.minecraft.world.chunk;

import java.util.List;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public interface ChunkSource {
    /**
     * @return whether a world chunk exists at the given chunk coordinates
     */
    boolean hasChunk(int chunkX, int chunkZ);

    /**
     * @return the world chunk at the given chunk coordinates.
     */
    WorldChunk getChunk(int chunkX, int chunkZ);

    /**
     * @return the world chunk at the given position.
     */
    WorldChunk getChunk(BlockPos pos);

    /**
     * Populates the world chunk at the given chunk coordinates, placing features and spawning mobs.
     */
    void populateChunk(ChunkSource source, int chunkX, int chunkZ);

    /**
     * Performs a second pass of population on the chunk at the given chunk coordinates.
     */
    boolean populateChunkAfterTerrain(ChunkSource source, WorldChunk chunk, int chunkX, int chunkZ);

    /**
     * Saves all loaded world chunks to storage.
     */
    boolean save(boolean saveEntities, ProgressListener listener);

    boolean tick();

    boolean shouldSave();

    String getDebugInfo();

    List<Biome.SpawnEntry> getSpawnEntries(MobCategory category, BlockPos pos);

    BlockPos findNearestStructure(World world, String type, BlockPos pos);

    int size();

    /**
     * Place structure features in the chunk at the given chunk coordinates.
     */
    void placeStructures(WorldChunk chunk, int chunkX, int chunkZ);

    void flush();
}
