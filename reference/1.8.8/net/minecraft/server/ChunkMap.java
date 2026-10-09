package net.minecraft.server;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunkS2CPacket;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Long2ObjectHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.Dimension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkMap {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ServerWorld world;
    /**
     * All players in this world.
     */
    private final List<ServerPlayerEntity> players = Lists.newArrayList();
    /**
     * A map of all loaded chunks by chunk position.
     */
    private final Long2ObjectHashMap<ChunkMap.ChunkHolder> chunksByPos = new Long2ObjectHashMap<>();
    /**
     * Chunks that need to sync block data with clients.
     */
    private final List<ChunkMap.ChunkHolder> dirty = Lists.newArrayList();
    /**
     * All loaded chunks.
     */
    private final List<ChunkMap.ChunkHolder> chunks = Lists.newArrayList();
    private int chunkViewDistance;
    /**
     * The last time chunks' inhabited time was updated.
     */
    private long lastUpdateTime;
    /**
     * Relative chunk coordinates for adjacent chunks for each of the cardinal directions.
     */
    private final int[][] adjacentChunkCoords = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public ChunkMap(ServerWorld world) {
        this.world = world;
        this.updateViewDistance(world.getServer().getPlayerManager().getChunkViewDistance());
    }

    public ServerWorld getWorld() {
        return this.world;
    }

    public void tick() {
        long i = this.world.getTime();
        if (i - this.lastUpdateTime > 8000L) {
            this.lastUpdateTime = i;

            for (int j = 0; j < this.chunks.size(); j++) {
                ChunkMap.ChunkHolder chunkmap$chunkholder = this.chunks.get(j);
                chunkmap$chunkholder.sendChanges();
                chunkmap$chunkholder.updateInhabitedTime();
            }
        } else {
            for (int k = 0; k < this.dirty.size(); k++) {
                ChunkMap.ChunkHolder chunkmap$chunkholder1 = this.dirty.get(k);
                chunkmap$chunkholder1.sendChanges();
            }
        }

        this.dirty.clear();
        if (this.players.isEmpty()) {
            Dimension dimension = this.world.dimension;
            if (!dimension.hasSpawnPoint()) {
                this.world.chunkCache.unloadAllChunks();
            }
        }
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        long i = chunkX + 2147483647L | chunkZ + 2147483647L << 32;
        return this.chunksByPos.get(i) != null;
    }

    private ChunkMap.ChunkHolder getChunk(int chunkX, int chunkZ, boolean add) {
        long i = chunkX + 2147483647L | chunkZ + 2147483647L << 32;
        ChunkMap.ChunkHolder chunkmap$chunkholder = this.chunksByPos.get(i);
        if (chunkmap$chunkholder == null && add) {
            chunkmap$chunkholder = new ChunkMap.ChunkHolder(chunkX, chunkZ);
            this.chunksByPos.put(i, chunkmap$chunkholder);
            this.chunks.add(chunkmap$chunkholder);
        }

        return chunkmap$chunkholder;
    }

    public void onBlockChanged(BlockPos pos) {
        int i = pos.getX() >> 4;
        int j = pos.getZ() >> 4;
        ChunkMap.ChunkHolder chunkmap$chunkholder = this.getChunk(i, j, false);
        if (chunkmap$chunkholder != null) {
            chunkmap$chunkholder.onBlockChanged(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        }
    }

    public void addPlayer(ServerPlayerEntity player) {
        int i = (int)player.x >> 4;
        int j = (int)player.z >> 4;
        player.trackedX = player.x;
        player.trackedZ = player.z;

        for (int k = i - this.chunkViewDistance; k <= i + this.chunkViewDistance; k++) {
            for (int l = j - this.chunkViewDistance; l <= j + this.chunkViewDistance; l++) {
                this.getChunk(k, l, true).addPlayer(player);
            }
        }

        this.players.add(player);
        this.trackNewLoadedChunks(player);
    }

    public void trackNewLoadedChunks(ServerPlayerEntity player) {
        List<ChunkPos> list = Lists.newArrayList(player.pendingChunks);
        int i = 0;
        int j = this.chunkViewDistance;
        int k = (int)player.x >> 4;
        int l = (int)player.z >> 4;
        int i1 = 0;
        int j1 = 0;
        ChunkPos chunkpos = this.getChunk(k, l, true).pos;
        player.pendingChunks.clear();
        if (list.contains(chunkpos)) {
            player.pendingChunks.add(chunkpos);
        }

        for (int k1 = 1; k1 <= j * 2; k1++) {
            for (int l1 = 0; l1 < 2; l1++) {
                int[] aint = this.adjacentChunkCoords[i++ % 4];

                for (int i2 = 0; i2 < k1; i2++) {
                    i1 += aint[0];
                    j1 += aint[1];
                    chunkpos = this.getChunk(k + i1, l + j1, true).pos;
                    if (list.contains(chunkpos)) {
                        player.pendingChunks.add(chunkpos);
                    }
                }
            }
        }

        i %= 4;

        for (int j2 = 0; j2 < j * 2; j2++) {
            i1 += this.adjacentChunkCoords[i][0];
            j1 += this.adjacentChunkCoords[i][1];
            chunkpos = this.getChunk(k + i1, l + j1, true).pos;
            if (list.contains(chunkpos)) {
                player.pendingChunks.add(chunkpos);
            }
        }
    }

    public void removePlayer(ServerPlayerEntity player) {
        int i = (int)player.trackedX >> 4;
        int j = (int)player.trackedZ >> 4;

        for (int k = i - this.chunkViewDistance; k <= i + this.chunkViewDistance; k++) {
            for (int l = j - this.chunkViewDistance; l <= j + this.chunkViewDistance; l++) {
                ChunkMap.ChunkHolder chunkmap$chunkholder = this.getChunk(k, l, false);
                if (chunkmap$chunkholder != null) {
                    chunkmap$chunkholder.removePlayer(player);
                }
            }
        }

        this.players.remove(player);
    }

    private boolean isChunkWithinView(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ, int chunkViewDistance) {
        int i = chunkX - playerChunkX;
        int j = chunkZ - playerChunkZ;
        return i >= -chunkViewDistance && i <= chunkViewDistance && j >= -chunkViewDistance && j <= chunkViewDistance;
    }

    public void movePlayer(ServerPlayerEntity player) {
        int i = (int)player.x >> 4;
        int j = (int)player.z >> 4;
        double d0 = player.trackedX - player.x;
        double d1 = player.trackedZ - player.z;
        double d2 = d0 * d0 + d1 * d1;
        if (!(d2 < 64.0)) {
            int k = (int)player.trackedX >> 4;
            int l = (int)player.trackedZ >> 4;
            int i1 = this.chunkViewDistance;
            int j1 = i - k;
            int k1 = j - l;
            if (j1 != 0 || k1 != 0) {
                for (int l1 = i - i1; l1 <= i + i1; l1++) {
                    for (int i2 = j - i1; i2 <= j + i1; i2++) {
                        if (!this.isChunkWithinView(l1, i2, k, l, i1)) {
                            this.getChunk(l1, i2, true).addPlayer(player);
                        }

                        if (!this.isChunkWithinView(l1 - j1, i2 - k1, i, j, i1)) {
                            ChunkMap.ChunkHolder chunkmap$chunkholder = this.getChunk(l1 - j1, i2 - k1, false);
                            if (chunkmap$chunkholder != null) {
                                chunkmap$chunkholder.removePlayer(player);
                            }
                        }
                    }
                }

                this.trackNewLoadedChunks(player);
                player.trackedX = player.x;
                player.trackedZ = player.z;
            }
        }
    }

    public boolean isChunkWithinView(ServerPlayerEntity player, int chunkX, int chunkZ) {
        ChunkMap.ChunkHolder chunkmap$chunkholder = this.getChunk(chunkX, chunkZ, false);
        return chunkmap$chunkholder != null && chunkmap$chunkholder.players.contains(player) && !player.pendingChunks.contains(chunkmap$chunkholder.pos);
    }

    public void updateViewDistance(int chunkViewDistance) {
        chunkViewDistance = MathHelper.clamp(chunkViewDistance, 3, 32);
        if (chunkViewDistance != this.chunkViewDistance) {
            int i = chunkViewDistance - this.chunkViewDistance;

            for (ServerPlayerEntity serverplayerentity : Lists.newArrayList(this.players)) {
                int j = (int)serverplayerentity.x >> 4;
                int k = (int)serverplayerentity.z >> 4;
                if (i > 0) {
                    for (int j1 = j - chunkViewDistance; j1 <= j + chunkViewDistance; j1++) {
                        for (int k1 = k - chunkViewDistance; k1 <= k + chunkViewDistance; k1++) {
                            ChunkMap.ChunkHolder chunkmap$chunkholder = this.getChunk(j1, k1, true);
                            if (!chunkmap$chunkholder.players.contains(serverplayerentity)) {
                                chunkmap$chunkholder.addPlayer(serverplayerentity);
                            }
                        }
                    }
                } else {
                    for (int l = j - this.chunkViewDistance; l <= j + this.chunkViewDistance; l++) {
                        for (int i1 = k - this.chunkViewDistance; i1 <= k + this.chunkViewDistance; i1++) {
                            if (!this.isChunkWithinView(l, i1, j, k, chunkViewDistance)) {
                                this.getChunk(l, i1, true).removePlayer(serverplayerentity);
                            }
                        }
                    }
                }
            }

            this.chunkViewDistance = chunkViewDistance;
        }
    }

    public static int getViewDistance(int chunkViewDistance) {
        return chunkViewDistance * 16 - 16;
    }

    class ChunkHolder {
        /**
         * Players within view distance of this chunk.
         */
        private final List<ServerPlayerEntity> players = Lists.newArrayList();
        private final ChunkPos pos;
        private short[] dirtyBlocks = new short[64];
        private int blocksChanged;
        private int dirtySections;
        /**
         * The last time this chunk's inhabited time was updated.
         */
        private long lastUpdateTime;

        public ChunkHolder(int chunkX, int chunkZ) {
            this.pos = new ChunkPos(chunkX, chunkZ);
            ChunkMap.this.getWorld().chunkCache.loadChunk(chunkX, chunkZ);
        }

        public void addPlayer(ServerPlayerEntity player) {
            if (this.players.contains(player)) {
                ChunkMap.LOGGER.debug("Failed to add player. {} already is in chunk {}, {}", player, this.pos.x, this.pos.z);
            } else {
                if (this.players.isEmpty()) {
                    this.lastUpdateTime = ChunkMap.this.world.getTime();
                }

                this.players.add(player);
                player.pendingChunks.add(this.pos);
            }
        }

        public void removePlayer(ServerPlayerEntity player) {
            if (this.players.contains(player)) {
                WorldChunk worldchunk = ChunkMap.this.world.getChunkAt(this.pos.x, this.pos.z);
                if (worldchunk.isPopulated()) {
                    player.networkHandler.sendPacket(new WorldChunkS2CPacket(worldchunk, true, 0));
                }

                this.players.remove(player);
                player.pendingChunks.remove(this.pos);
                if (this.players.isEmpty()) {
                    long i = this.pos.x + 2147483647L | this.pos.z + 2147483647L << 32;
                    this.updateInhabitedTime(worldchunk);
                    ChunkMap.this.chunksByPos.remove(i);
                    ChunkMap.this.chunks.remove(this);
                    if (this.blocksChanged > 0) {
                        ChunkMap.this.dirty.remove(this);
                    }

                    ChunkMap.this.getWorld().chunkCache.unloadChunk(this.pos.x, this.pos.z);
                }
            }
        }

        public void updateInhabitedTime() {
            this.updateInhabitedTime(ChunkMap.this.world.getChunkAt(this.pos.x, this.pos.z));
        }

        private void updateInhabitedTime(WorldChunk chunk) {
            chunk.setInhabitedTime(chunk.getInhabitedTime() + ChunkMap.this.world.getTime() - this.lastUpdateTime);
            this.lastUpdateTime = ChunkMap.this.world.getTime();
        }

        public void onBlockChanged(int localX, int y, int localZ) {
            if (this.blocksChanged == 0) {
                ChunkMap.this.dirty.add(this);
            }

            this.dirtySections |= 1 << (y >> 4);
            if (this.blocksChanged < 64) {
                short short1 = (short)(localX << 12 | localZ << 8 | y);

                for (int i = 0; i < this.blocksChanged; i++) {
                    if (this.dirtyBlocks[i] == short1) {
                        return;
                    }
                }

                this.dirtyBlocks[this.blocksChanged++] = short1;
            }
        }

        public void sendPacket(Packet packet) {
            for (int i = 0; i < this.players.size(); i++) {
                ServerPlayerEntity serverplayerentity = this.players.get(i);
                if (!serverplayerentity.pendingChunks.contains(this.pos)) {
                    serverplayerentity.networkHandler.sendPacket(packet);
                }
            }
        }

        public void sendChanges() {
            if (this.blocksChanged != 0) {
                if (this.blocksChanged == 1) {
                    int i = (this.dirtyBlocks[0] >> 12 & 15) + this.pos.x * 16;
                    int j = this.dirtyBlocks[0] & 255;
                    int k = (this.dirtyBlocks[0] >> 8 & 15) + this.pos.z * 16;
                    BlockPos blockpos = new BlockPos(i, j, k);
                    this.sendPacket(new BlockUpdateS2CPacket(ChunkMap.this.world, blockpos));
                    if (ChunkMap.this.world.getBlockState(blockpos).getBlock().hasBlockEntity()) {
                        this.sendBlockEntityUpdate(ChunkMap.this.world.getBlockEntity(blockpos));
                    }
                } else if (this.blocksChanged == 64) {
                    int i1 = this.pos.x * 16;
                    int k1 = this.pos.z * 16;
                    this.sendPacket(new WorldChunkS2CPacket(ChunkMap.this.world.getChunkAt(this.pos.x, this.pos.z), false, this.dirtySections));

                    for (int i2 = 0; i2 < 16; i2++) {
                        if ((this.dirtySections & 1 << i2) != 0) {
                            int k2 = i2 << 4;
                            List<BlockEntity> list = ChunkMap.this.world.getBlockEntities(i1, k2, k1, i1 + 16, k2 + 16, k1 + 16);

                            for (int l = 0; l < list.size(); l++) {
                                this.sendBlockEntityUpdate(list.get(l));
                            }
                        }
                    }
                } else {
                    this.sendPacket(new BlocksUpdateS2CPacket(this.blocksChanged, this.dirtyBlocks, ChunkMap.this.world.getChunkAt(this.pos.x, this.pos.z)));

                    for (int j1 = 0; j1 < this.blocksChanged; j1++) {
                        int l1 = (this.dirtyBlocks[j1] >> 12 & 15) + this.pos.x * 16;
                        int j2 = this.dirtyBlocks[j1] & 255;
                        int l2 = (this.dirtyBlocks[j1] >> 8 & 15) + this.pos.z * 16;
                        BlockPos blockpos1 = new BlockPos(l1, j2, l2);
                        if (ChunkMap.this.world.getBlockState(blockpos1).getBlock().hasBlockEntity()) {
                            this.sendBlockEntityUpdate(ChunkMap.this.world.getBlockEntity(blockpos1));
                        }
                    }
                }

                this.blocksChanged = 0;
                this.dirtySections = 0;
            }
        }

        private void sendBlockEntityUpdate(BlockEntity blockEntity) {
            if (blockEntity != null) {
                Packet packet = blockEntity.createUpdatePacket();
                if (packet != null) {
                    this.sendPacket(packet);
                }
            }
        }
    }
}
