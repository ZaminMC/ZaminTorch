package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.chunk.WorldChunk;

public class WorldChunksS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int[] chunkX;
    private int[] chunkZ;
    private WorldChunkS2CPacket.ChunkData[] data;
    private boolean light;

    public WorldChunksS2CPacket() {
    }

    public WorldChunksS2CPacket(List<WorldChunk> chunks) {
        int i = chunks.size();
        this.chunkX = new int[i];
        this.chunkZ = new int[i];
        this.data = new WorldChunkS2CPacket.ChunkData[i];
        this.light = !chunks.get(0).getWorld().dimension.hasNoSky();

        for (int j = 0; j < i; j++) {
            WorldChunk worldchunk = chunks.get(j);
            WorldChunkS2CPacket.ChunkData worldchunks2cpacket$chunkdata = WorldChunkS2CPacket.writeChunkData(worldchunk, true, this.light, 65535);
            this.chunkX[j] = worldchunk.chunkX;
            this.chunkZ[j] = worldchunk.chunkZ;
            this.data[j] = worldchunks2cpacket$chunkdata;
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.light = buffer.readBoolean();
        int i = buffer.readVarInt();
        this.chunkX = new int[i];
        this.chunkZ = new int[i];
        this.data = new WorldChunkS2CPacket.ChunkData[i];

        for (int j = 0; j < i; j++) {
            this.chunkX[j] = buffer.readInt();
            this.chunkZ[j] = buffer.readInt();
            this.data[j] = new WorldChunkS2CPacket.ChunkData();
            this.data[j].sections = buffer.readShort() & '\uffff';
            this.data[j].bytes = new byte[WorldChunkS2CPacket.findBufferSize(Integer.bitCount(this.data[j].sections), this.light, true)];
        }

        for (int k = 0; k < i; k++) {
            buffer.readBytes(this.data[k].bytes);
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBoolean(this.light);
        buffer.writeVarInt(this.data.length);

        for (int i = 0; i < this.chunkX.length; i++) {
            buffer.writeInt(this.chunkX[i]);
            buffer.writeInt(this.chunkZ[i]);
            buffer.writeShort((short)(this.data[i].sections & 65535));
        }

        for (int j = 0; j < this.chunkX.length; j++) {
            buffer.writeBytes(this.data[j].bytes);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleWorldChunks(this);
    }

    public int getChunkX(int index) {
        return this.chunkX[index];
    }

    public int getChunkZ(int index) {
        return this.chunkZ[index];
    }

    public int getChunkCount() {
        return this.chunkX.length;
    }

    public byte[] getData(int index) {
        return this.data[index].bytes;
    }

    public int getSections(int index) {
        return this.data[index].sections;
    }
}
