package net.minecraft.world.chunk.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.resource.Identifier;
import net.minecraft.server.world.ScheduledTick;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleStorage;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import net.minecraft.world.chunk.storage.io.ChunkIo;
import net.minecraft.world.chunk.storage.io.ChunkIoTask;
import net.minecraft.world.storage.exception.SessionLockException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AnvilChunkStorage implements ChunkStorage, ChunkIoTask {
    private static final Logger LOGGER = LogManager.getLogger();
    private Map<ChunkPos, NbtCompound> chunkSaveQueue = new ConcurrentHashMap<>();
    private Set<ChunkPos> queuedChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final File dir;
    private boolean saving = false;

    public AnvilChunkStorage(File dir) {
        this.dir = dir;
    }

    @Override
    public WorldChunk loadChunk(World world, int chunkX, int chunkZ) throws IOException {
        ChunkPos chunkpos = new ChunkPos(chunkX, chunkZ);
        NbtCompound nbtcompound = this.chunkSaveQueue.get(chunkpos);
        if (nbtcompound == null) {
            DataInputStream datainputstream = RegionIo.getChunkInputStream(this.dir, chunkX, chunkZ);
            if (datainputstream == null) {
                return null;
            }

            nbtcompound = NbtIo.read(datainputstream);
        }

        return this.loadChunk(world, chunkX, chunkZ, nbtcompound);
    }

    protected WorldChunk loadChunk(World world, int chunkX, int chunkZ, NbtCompound nbt) {
        if (!nbt.contains("Level", 10)) {
            LOGGER.error("Chunk file at " + chunkX + "," + chunkZ + " is missing level data, skipping");
            return null;
        }

        NbtCompound nbtcompound = nbt.getCompound("Level");
        if (!nbtcompound.contains("Sections", 9)) {
            LOGGER.error("Chunk file at " + chunkX + "," + chunkZ + " is missing block data, skipping");
            return null;
        }

        WorldChunk worldchunk = this.createChunkFromNbt(world, nbtcompound);
        if (!worldchunk.isAt(chunkX, chunkZ)) {
            LOGGER.error(
                "Chunk file at "
                    + chunkX
                    + ","
                    + chunkZ
                    + " is in the wrong location; relocating. (Expected "
                    + chunkX
                    + ", "
                    + chunkZ
                    + ", got "
                    + worldchunk.chunkX
                    + ", "
                    + worldchunk.chunkZ
                    + ")"
            );
            nbtcompound.putInt("xPos", chunkX);
            nbtcompound.putInt("zPos", chunkZ);
            worldchunk = this.createChunkFromNbt(world, nbtcompound);
        }

        return worldchunk;
    }

    @Override
    public void saveChunk(World world, WorldChunk chunk) throws IOException, SessionLockException {
        world.checkSessionLock();

        try {
            NbtCompound nbtcompound = new NbtCompound();
            NbtCompound nbtcompound1 = new NbtCompound();
            nbtcompound.put("Level", nbtcompound1);
            this.writeChunkToNbt(chunk, world, nbtcompound1);
            this.queueChunkSave(chunk.getPos(), nbtcompound);
        } catch (Exception exception) {
            LOGGER.error("Failed to save chunk", exception);
        }
    }

    protected void queueChunkSave(ChunkPos pos, NbtCompound nbt) {
        if (!this.queuedChunks.contains(pos)) {
            this.chunkSaveQueue.put(pos, nbt);
        }

        ChunkIo.getInstance().registerTask(this);
    }

    @Override
    public boolean run() {
        if (this.chunkSaveQueue.isEmpty()) {
            if (this.saving) {
                LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", new Object[]{this.dir.getName()});
            }

            return false;
        } else {
            ChunkPos chunkpos = this.chunkSaveQueue.keySet().iterator().next();

            try {
                this.queuedChunks.add(chunkpos);
                NbtCompound nbtcompound = this.chunkSaveQueue.remove(chunkpos);
                if (nbtcompound != null) {
                    try {
                        this.saveChunk(chunkpos, nbtcompound);
                    } catch (Exception exception) {
                        LOGGER.error("Failed to save chunk", exception);
                    }
                }

                return true;
            } finally {
                this.queuedChunks.remove(chunkpos);
            }
        }
    }

    private void saveChunk(ChunkPos pos, NbtCompound nbt) throws IOException {
        DataOutputStream dataoutputstream = RegionIo.getChunkOutputStream(this.dir, pos.x, pos.z);
        NbtIo.write(nbt, dataoutputstream);
        dataoutputstream.close();
    }

    @Override
    public void saveEntities(World world, WorldChunk chunk) throws IOException {
    }

    @Override
    public void tick() {
    }

    @Override
    public void flush() {
        try {
            this.saving = true;

            while (this.run()) {
            }
        } finally {
            this.saving = false;
        }
    }

    private void writeChunkToNbt(WorldChunk chunk, World world, NbtCompound nbt) {
        nbt.putByte("V", (byte)1);
        nbt.putInt("xPos", chunk.chunkX);
        nbt.putInt("zPos", chunk.chunkZ);
        nbt.putLong("LastUpdate", world.getTime());
        nbt.putIntArray("HeightMap", chunk.getHeightMap());
        nbt.putBoolean("TerrainPopulated", chunk.isTerrainPopulated());
        nbt.putBoolean("LightPopulated", chunk.isLightPopulated());
        nbt.putLong("InhabitedTime", chunk.getInhabitedTime());
        WorldChunkSection[] aworldchunksection = chunk.getSections();
        NbtList nbtlist = new NbtList();
        boolean flag = !world.dimension.hasNoSky();

        for (WorldChunkSection worldchunksection : aworldchunksection) {
            if (worldchunksection != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putByte("Y", (byte)(worldchunksection.getOffsetY() >> 4 & 0xFF));
                byte[] abyte = new byte[worldchunksection.getBlockStates().length];
                ChunkNibbleStorage chunknibblestorage = new ChunkNibbleStorage();
                ChunkNibbleStorage chunknibblestorage1 = null;

                for (int i = 0; i < worldchunksection.getBlockStates().length; i++) {
                    char c0 = worldchunksection.getBlockStates()[i];
                    int j = i & 15;
                    int k = i >> 8 & 15;
                    int l = i >> 4 & 15;
                    if (c0 >> '\f' != 0) {
                        if (chunknibblestorage1 == null) {
                            chunknibblestorage1 = new ChunkNibbleStorage();
                        }

                        chunknibblestorage1.set(j, k, l, c0 >> '\f');
                    }

                    abyte[i] = (byte)(c0 >> 4 & 0xFF);
                    chunknibblestorage.set(j, k, l, c0 & 15);
                }

                nbtcompound.putByteArray("Blocks", abyte);
                nbtcompound.putByteArray("Data", chunknibblestorage.getData());
                if (chunknibblestorage1 != null) {
                    nbtcompound.putByteArray("Add", chunknibblestorage1.getData());
                }

                nbtcompound.putByteArray("BlockLight", worldchunksection.getBlockLightStorage().getData());
                if (flag) {
                    nbtcompound.putByteArray("SkyLight", worldchunksection.getSkyLightStorage().getData());
                } else {
                    nbtcompound.putByteArray("SkyLight", new byte[worldchunksection.getBlockLightStorage().getData().length]);
                }

                nbtlist.addElement(nbtcompound);
            }
        }

        nbt.put("Sections", nbtlist);
        nbt.putByteArray("Biomes", chunk.getBiomes());
        chunk.setHasEntities(false);
        NbtList nbtlist1 = new NbtList();

        for (int i1 = 0; i1 < chunk.getEntities().length; i1++) {
            for (Entity entity : chunk.getEntities()[i1]) {
                NbtCompound nbtcompound1 = new NbtCompound();
                if (entity.writeNbtIfNotMount(nbtcompound1)) {
                    chunk.setHasEntities(true);
                    nbtlist1.addElement(nbtcompound1);
                }
            }
        }

        nbt.put("Entities", nbtlist1);
        NbtList nbtlist2 = new NbtList();

        for (BlockEntity blockentity : chunk.getBlockEntities().values()) {
            NbtCompound nbtcompound2 = new NbtCompound();
            blockentity.writeNbt(nbtcompound2);
            nbtlist2.addElement(nbtcompound2);
        }

        nbt.put("TileEntities", nbtlist2);
        List<ScheduledTick> list = world.getScheduledTicks(chunk, false);
        if (list != null) {
            long j1 = world.getTime();
            NbtList nbtlist3 = new NbtList();

            for (ScheduledTick scheduledtick : list) {
                NbtCompound nbtcompound3 = new NbtCompound();
                Identifier identifier = Block.REGISTRY.getKey(scheduledtick.getBlock());
                nbtcompound3.putString("i", identifier == null ? "" : identifier.toString());
                nbtcompound3.putInt("x", scheduledtick.pos.getX());
                nbtcompound3.putInt("y", scheduledtick.pos.getY());
                nbtcompound3.putInt("z", scheduledtick.pos.getZ());
                nbtcompound3.putInt("t", (int)(scheduledtick.time - j1));
                nbtcompound3.putInt("p", scheduledtick.priority);
                nbtlist3.addElement(nbtcompound3);
            }

            nbt.put("TileTicks", nbtlist3);
        }
    }

    private WorldChunk createChunkFromNbt(World world, NbtCompound nbt) {
        int i = nbt.getInt("xPos");
        int j = nbt.getInt("zPos");
        WorldChunk worldchunk = new WorldChunk(world, i, j);
        worldchunk.setHeightMap(nbt.getIntArray("HeightMap"));
        worldchunk.setTerrainPopulated(nbt.getBoolean("TerrainPopulated"));
        worldchunk.setLightPopulated(nbt.getBoolean("LightPopulated"));
        worldchunk.setInhabitedTime(nbt.getLong("InhabitedTime"));
        NbtList nbtlist = nbt.getList("Sections", 10);
        int k = 16;
        WorldChunkSection[] aworldchunksection = new WorldChunkSection[k];
        boolean flag = !world.dimension.hasNoSky();

        for (int l = 0; l < nbtlist.size(); l++) {
            NbtCompound nbtcompound = nbtlist.getCompound(l);
            int i1 = nbtcompound.getByte("Y");
            WorldChunkSection worldchunksection = new WorldChunkSection(i1 << 4, flag);
            byte[] abyte = nbtcompound.getByteArray("Blocks");
            ChunkNibbleStorage chunknibblestorage = new ChunkNibbleStorage(nbtcompound.getByteArray("Data"));
            ChunkNibbleStorage chunknibblestorage1 = nbtcompound.contains("Add", 7) ? new ChunkNibbleStorage(nbtcompound.getByteArray("Add")) : null;
            char[] achar = new char[abyte.length];

            for (int j1 = 0; j1 < achar.length; j1++) {
                int k1 = j1 & 15;
                int l1 = j1 >> 8 & 15;
                int i2 = j1 >> 4 & 15;
                int j2 = chunknibblestorage1 != null ? chunknibblestorage1.get(k1, l1, i2) : 0;
                achar[j1] = (char)(j2 << 12 | (abyte[j1] & 255) << 4 | chunknibblestorage.get(k1, l1, i2));
            }

            worldchunksection.setBlockStates(achar);
            worldchunksection.setBlockLightStorage(new ChunkNibbleStorage(nbtcompound.getByteArray("BlockLight")));
            if (flag) {
                worldchunksection.setSkyLightStorage(new ChunkNibbleStorage(nbtcompound.getByteArray("SkyLight")));
            }

            worldchunksection.validateBlockCounters();
            aworldchunksection[i1] = worldchunksection;
        }

        worldchunk.setSections(aworldchunksection);
        if (nbt.contains("Biomes", 7)) {
            worldchunk.setBiomes(nbt.getByteArray("Biomes"));
        }

        NbtList nbtlist1 = nbt.getList("Entities", 10);
        if (nbtlist1 != null) {
            for (int k2 = 0; k2 < nbtlist1.size(); k2++) {
                NbtCompound nbtcompound1 = nbtlist1.getCompound(k2);
                Entity entity = Entities.create(nbtcompound1, world);
                worldchunk.setHasEntities(true);
                if (entity != null) {
                    worldchunk.addEntity(entity);
                    Entity entity1 = entity;

                    for (NbtCompound nbtcompound4 = nbtcompound1; nbtcompound4.contains("Riding", 10); nbtcompound4 = nbtcompound4.getCompound("Riding")) {
                        Entity entity2 = Entities.create(nbtcompound4.getCompound("Riding"), world);
                        if (entity2 != null) {
                            worldchunk.addEntity(entity2);
                            entity1.startRiding(entity2);
                        }

                        entity1 = entity2;
                    }
                }
            }
        }

        NbtList nbtlist2 = nbt.getList("TileEntities", 10);
        if (nbtlist2 != null) {
            for (int l2 = 0; l2 < nbtlist2.size(); l2++) {
                NbtCompound nbtcompound2 = nbtlist2.getCompound(l2);
                BlockEntity blockentity = BlockEntity.fromNbt(nbtcompound2);
                if (blockentity != null) {
                    worldchunk.addBlockEntity(blockentity);
                }
            }
        }

        if (nbt.contains("TileTicks", 9)) {
            NbtList nbtlist3 = nbt.getList("TileTicks", 10);
            if (nbtlist3 != null) {
                for (int i3 = 0; i3 < nbtlist3.size(); i3++) {
                    NbtCompound nbtcompound3 = nbtlist3.getCompound(i3);
                    Block block;
                    if (nbtcompound3.contains("i", 8)) {
                        block = Block.byKey(nbtcompound3.getString("i"));
                    } else {
                        block = Block.byId(nbtcompound3.getInt("i"));
                    }

                    world.loadScheduledTick(
                        new BlockPos(nbtcompound3.getInt("x"), nbtcompound3.getInt("y"), nbtcompound3.getInt("z")),
                        block,
                        nbtcompound3.getInt("t"),
                        nbtcompound3.getInt("p")
                    );
                }
            }
        }

        return worldchunk;
    }
}
