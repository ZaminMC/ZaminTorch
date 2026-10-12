package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import java.util.Collection;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.map.MapDecoration;
import net.minecraft.world.map.SavedMapData;

public class MapDataS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private byte scale;
    private MapDecoration[] decorations;
    private int dirtyMinX;
    private int dirtyMinY;
    private int dirtyWidth;
    private int dirtyHeight;
    private byte[] colors;

    public MapDataS2CPacket() {
    }

    public MapDataS2CPacket(
        int id, byte scale, Collection<MapDecoration> decorations, byte[] colors, int dirtyMinX, int dirtyMinY, int dirtyWidth, int dirtyHeight
    ) {
        this.id = id;
        this.scale = scale;
        this.decorations = decorations.toArray(new MapDecoration[decorations.size()]);
        this.dirtyMinX = dirtyMinX;
        this.dirtyMinY = dirtyMinY;
        this.dirtyWidth = dirtyWidth;
        this.dirtyHeight = dirtyHeight;
        this.colors = new byte[dirtyWidth * dirtyHeight];

        for (int i = 0; i < dirtyWidth; i++) {
            for (int j = 0; j < dirtyHeight; j++) {
                this.colors[i + j * dirtyWidth] = colors[dirtyMinX + i + (dirtyMinY + j) * 128];
            }
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.scale = buffer.readByte();
        this.decorations = new MapDecoration[buffer.readVarInt()];

        for (int i = 0; i < this.decorations.length; i++) {
            short short1 = buffer.readByte();
            this.decorations[i] = new MapDecoration((byte)(short1 >> 4 & 15), buffer.readByte(), buffer.readByte(), (byte)(short1 & 15));
        }

        this.dirtyWidth = buffer.readUnsignedByte();
        if (this.dirtyWidth > 0) {
            this.dirtyHeight = buffer.readUnsignedByte();
            this.dirtyMinX = buffer.readUnsignedByte();
            this.dirtyMinY = buffer.readUnsignedByte();
            this.colors = buffer.readByteArray();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.scale);
        buffer.writeVarInt(this.decorations.length);

        for (MapDecoration mapdecoration : this.decorations) {
            buffer.writeByte((mapdecoration.getType() & 15) << 4 | mapdecoration.getRotation() & 15);
            buffer.writeByte(mapdecoration.getX());
            buffer.writeByte(mapdecoration.getY());
        }

        buffer.writeByte(this.dirtyWidth);
        if (this.dirtyWidth > 0) {
            buffer.writeByte(this.dirtyHeight);
            buffer.writeByte(this.dirtyMinX);
            buffer.writeByte(this.dirtyMinY);
            buffer.writeByteArray(this.colors);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleMapData(this);
    }

    public int getId() {
        return this.id;
    }

    public void apply(SavedMapData mapData) {
        mapData.scale = this.scale;
        mapData.decorations.clear();

        for (int i = 0; i < this.decorations.length; i++) {
            MapDecoration mapdecoration = this.decorations[i];
            mapData.decorations.put("icon-" + i, mapdecoration);
        }

        for (int j = 0; j < this.dirtyWidth; j++) {
            for (int k = 0; k < this.dirtyHeight; k++) {
                mapData.colors[this.dirtyMinX + j + (this.dirtyMinY + k) * 128] = this.colors[j + k * this.dirtyWidth];
            }
        }
    }
}
