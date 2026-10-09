package net.minecraft.server.world.chunk;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Long2ObjectHashMap;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.storage.exception.SessionLockException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerChunkCache implements ChunkSource {
    private static final Logger LOGGER = LogManager.getLogger();
    /**
     * Queue for chunks to unload.
     */
    private Set<Long> chunksToUnload = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private WorldChunk empty;
    /**
     * Generator for new chunks.
     */
    private ChunkSource generator;
    /**
     * Storage for all existing chunks.
     */
    private ChunkStorage storage;
    public boolean forceLoad = true;
    /**
     * All loaded chunks, by chunk position.
     */
    private Long2ObjectHashMap<WorldChunk> chunksByPos = new Long2ObjectHashMap<>();
    /**
     * All loaded chunks.
     */
    private List<WorldChunk> chunks = Lists.newArrayList();
    private ServerWorld world;

    public ServerChunkCache(ServerWorld world, ChunkStorage storage, ChunkSource generator) {
        this.empty = new EmptyChunk(world, 0, 0);
        this.world = world;
        this.storage = storage;
        this.generator = generator;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.chunksByPos.contains(ChunkPos.toLong(chunkX, chunkZ));
    }

    /**
     * @return a list of all loaded chunks.
     */
    public List<WorldChunk> getChunks() {
        return this.chunks;
    }

    /**
     * Queue the world chunk at the given chunk coordinates to be unloaded.
     * This will happen the next time this chunk cache is ticked.
     */
    public void unloadChunk(int chunkX, int chunkZ) {
        if (this.world.dimension.hasSpawnPoint()) {
            if (!this.world.isSpawnChunk(chunkX, chunkZ)) {
                this.chunksToUnload.add(ChunkPos.toLong(chunkX, chunkZ));
            }
        } else {
            this.chunksToUnload.add(ChunkPos.toLong(chunkX, chunkZ));
        }
    }

    /**
     * Queue all chunks to be unloaded.
     * This will happen the next time this chunk cache is ticked.
     */
    public void unloadAllChunks() {
        for (WorldChunk worldchunk : this.chunks) {
            this.unloadChunk(worldchunk.chunkX, worldchunk.chunkZ);
        }
    }

    /**
     * Loads the world chunk at the given chunk coordinates.
     */
    public WorldChunk loadChunk(int chunkX, int chunkZ) {
        long i = ChunkPos.toLong(chunkX, chunkZ);
        this.chunksToUnload.remove(i);
        WorldChunk worldchunk = this.chunksByPos.get(i);
        if (worldchunk == null) {
            worldchunk = this.loadChunkFromStorage(chunkX, chunkZ);
            if (worldchunk == null) {
                if (this.generator == null) {
                    worldchunk = this.empty;
                } else {
                    try {
                        worldchunk = this.generator.getChunk(chunkX, chunkZ);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.of(throwable, "Exception generating new chunk");
                        CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk to be generated");
                        crashreportcategory.add("Location", String.format("%d,%d", chunkX, chunkZ));
                        crashreportcategory.add("Position hash", i);
                        crashreportcategory.add("Generator", this.generator.getDebugInfo());
                        throw new CrashException(crashreport);
                    }
                }
            }

            this.chunksByPos.put(i, worldchunk);
            this.chunks.add(worldchunk);
            worldchunk.load();
            worldchunk.populate(this, this, chunkX, chunkZ);
        }

        return worldchunk;
    }

    @Override
    public WorldChunk getChunk(int chunkX, int chunkZ) {
        WorldChunk worldchunk = this.chunksByPos.get(ChunkPos.toLong(chunkX, chunkZ));
        if (worldchunk == null) {
            return !this.world.isSearchingSpawnPoint() && !this.forceLoad ? this.empty : this.loadChunk(chunkX, chunkZ);
        } else {
            return worldchunk;
        }
    }

    /**
     * Loads the world chunk at the given chunk coordinates from storage.
     */
    private WorldChunk loadChunkFromStorage(int chunkX, int chunkZ) {
        if (this.storage == null) {
            return null;
        }

        try {
            WorldChunk worldchunk = this.storage.loadChunk(this.world, chunkX, chunkZ);
            if (worldchunk != null) {
                worldchunk.setLastSaveTime(this.world.getTime());
                if (this.generator != null) {
                    this.generator.placeStructures(worldchunk, chunkX, chunkZ);
                }
            }

            return worldchunk;
        } catch (Exception exception) {
            LOGGER.error("Couldn't load chunk", exception);
            return null;
        }
    }

    private void saveEntities(WorldChunk chunk) {
        if (this.storage != null) {
            try {
                this.storage.saveEntities(this.world, chunk);
            } catch (Exception exception) {
                LOGGER.error("Couldn't save entities", exception);
            }
        }
    }

    private void saveChunk(WorldChunk chunk) {
        if (this.storage != null) {
            try {
                chunk.setLastSaveTime(this.world.getTime());
                this.storage.saveChunk(this.world, chunk);
            } catch (IOException ioexception) {
                LOGGER.error("Couldn't save chunk", ioexception);
            } catch (SessionLockException sessionlockexception) {
                LOGGER.error("Couldn't save chunk; already in use by another instance of Minecraft?", sessionlockexception);
            }
        }
    }

    @Override
    public void populateChunk(ChunkSource source, int chunkX, int chunkZ) {
        WorldChunk worldchunk = this.getChunk(chunkX, chunkZ);
        if (!worldchunk.isTerrainPopulated()) {
            worldchunk.populateLight();
            if (this.generator != null) {
                this.generator.populateChunk(source, chunkX, chunkZ);
                worldchunk.markDirty();
            }
        }
    }

    @Override
    public boolean populateChunkAfterTerrain(ChunkSource source, WorldChunk chunk, int chunkX, int chunkZ) {
        if (this.generator != null && this.generator.populateChunkAfterTerrain(source, chunk, chunkX, chunkZ)) {
            WorldChunk worldchunk = this.getChunk(chunkX, chunkZ);
            worldchunk.markDirty();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean save(boolean saveEntities, ProgressListener listener) {
        int i = 0;
        List<WorldChunk> list = Lists.newArrayList(this.chunks);

        for (int j = 0; j < list.size(); j++) {
            WorldChunk worldchunk = list.get(j);
            if (saveEntities) {
                this.saveEntities(worldchunk);
            }

            if (worldchunk.shouldSave(saveEntities)) {
                this.saveChunk(worldchunk);
                worldchunk.setDirty(false);
                if (++i == 24 && !saveEntities) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void flush() {
        if (this.storage != null) {
            this.storage.flush();
        }
    }

    @Override
    public boolean tick() {
        if (!this.world.savingDisabled) {
            for (int i = 0; i < 100; i++) {
                if (!this.chunksToUnload.isEmpty()) {
                    Long olong = this.chunksToUnload.iterator().next();
                    WorldChunk worldchunk = this.chunksByPos.get(olong);
                    if (worldchunk != null) {
                        worldchunk.unload();
                        this.saveChunk(worldchunk);
                        this.saveEntities(worldchunk);
                        this.chunksByPos.remove(olong);
                        this.chunks.remove(worldchunk);
                    }

                    this.chunksToUnload.remove(olong);
                }
            }

            if (this.storage != null) {
                this.storage.tick();
            }
        }

        return this.generator.tick();
    }

    @Override
    public boolean shouldSave() {
        return !this.world.savingDisabled;
    }

    @Override
    public String getDebugInfo() {
        return "ServerChunkCache: " + this.chunksByPos.size() + " Drop: " + this.chunksToUnload.size();
    }

    @Override
    public List<Biome.SpawnEntry> getSpawnEntries(MobCategory category, BlockPos pos) {
        return this.generator.getSpawnEntries(category, pos);
    }

    @Override
    public BlockPos findNearestStructure(World world, String type, BlockPos pos) {
        return this.generator.findNearestStructure(world, type, pos);
    }

    @Override
    public int size() {
        return this.chunksByPos.size();
    }

    @Override
    public void placeStructures(WorldChunk chunk, int chunkX, int chunkZ) {
    }

    @Override
    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
