package net.minecraft.world.chunk.storage;

import java.io.IOException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.storage.exception.SessionLockException;

public interface ChunkStorage {
    WorldChunk loadChunk(World world, int chunkX, int chunkZ) throws IOException;

    void saveChunk(World world, WorldChunk chunk) throws IOException, SessionLockException;

    void saveEntities(World world, WorldChunk chunk) throws IOException;

    void tick();

    void flush();
}
