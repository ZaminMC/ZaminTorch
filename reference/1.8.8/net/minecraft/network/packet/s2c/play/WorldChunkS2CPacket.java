package net.minecraft.network.packet.s2c.play;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;

public class WorldChunkS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int chunkX;
    private int chunkZ;
    private WorldChunkS2CPacket.ChunkData data;
    private boolean full;

    public WorldChunkS2CPacket() {
    }

    public WorldChunkS2CPacket(WorldChunk chunk, boolean full, int sections) {
        this.chunkX = chunk.chunkX;
        this.chunkZ = chunk.chunkZ;
        this.full = full;
        this.data = writeChunkData(chunk, full, !chunk.getWorld().dimension.hasNoSky(), sections);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.chunkX = buffer.readInt();
        this.chunkZ = buffer.readInt();
        this.full = buffer.readBoolean();
        this.data = new WorldChunkS2CPacket.ChunkData();
        this.data.sections = buffer.readShort();
        this.data.bytes = buffer.readByteArray();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.chunkX);
        buffer.writeInt(this.chunkZ);
        buffer.writeBoolean(this.full);
        buffer.writeShort((short)(this.data.sections & 65535));
        buffer.writeByteArray(this.data.bytes);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleWorldChunk(this);
    }

    public byte[] getChunkData() {
        return this.data.bytes;
    }

    protected static int findBufferSize(int sections, boolean light, boolean full) {
        int i = sections * 2 * 16 * 16 * 16;
        int j = sections * 16 * 16 * 16 / 2;
        int k = light ? sections * 16 * 16 * 16 / 2 : 0;
        int l = full ? 256 : 0;
        return i + j + k + l;
    }

    public static WorldChunkS2CPacket.ChunkData writeChunkData(WorldChunk chunk, boolean full, boolean light, int sections) {
        WorldChunkSection[] aworldchunksection = chunk.getSections();
        WorldChunkS2CPacket.ChunkData worldchunks2cpacket$chunkdata = new WorldChunkS2CPacket.ChunkData();
        List<WorldChunkSection> list = Lists.newArrayList();

        for (int i = 0; i < aworldchunksection.length; i++) {
            WorldChunkSection worldchunksection = aworldchunksection[i];
            if (worldchunksection != null && (!full || !worldchunksection.isEmpty()) && (sections & 1 << i) != 0) {
                worldchunks2cpacket$chunkdata.sections |= 1 << i;
                list.add(worldchunksection);
            }
        }

        worldchunks2cpacket$chunkdata.bytes = new byte[findBufferSize(Integer.bitCount(worldchunks2cpacket$chunkdata.sections), light, full)];
        int j = 0;

        for (WorldChunkSection worldchunksection1 : list) {
            char[] achar = worldchunksection1.getBlockStates();

            for (char c0 : achar) {
                worldchunks2cpacket$chunkdata.bytes[j++] = (byte)(c0 & 0xFF);
                worldchunks2cpacket$chunkdata.bytes[j++] = (byte)(c0 >> '\b' & 0xFF);
            }
        }

        for (WorldChunkSection worldchunksection2 : list) {
            j = arrayCopy(worldchunksection2.getBlockLightStorage().getData(), worldchunks2cpacket$chunkdata.bytes, j);
        }

        if (light) {
            for (WorldChunkSection worldchunksection3 : list) {
                j = arrayCopy(worldchunksection3.getSkyLightStorage().getData(), worldchunks2cpacket$chunkdata.bytes, j);
            }
        }

        if (full) {
            arrayCopy(chunk.getBiomes(), worldchunks2cpacket$chunkdata.bytes, j);
        }

        return worldchunks2cpacket$chunkdata;
    }

    private static int arrayCopy(byte[] src, byte[] dst, int dstPos) {
        System.arraycopy(src, 0, dst, dstPos, src.length);
        return dstPos + src.length;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public int getSections() {
        return this.data.sections;
    }

    public boolean isFull() {
        return this.full;
    }

    public static class ChunkData {
        public byte[] bytes;
        public int sections;
    }
}
