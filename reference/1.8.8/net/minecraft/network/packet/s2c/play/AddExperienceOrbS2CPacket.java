package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;

public class AddExperienceOrbS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int x;
    private int y;
    private int z;
    private int xp;

    public AddExperienceOrbS2CPacket() {
    }

    public AddExperienceOrbS2CPacket(ExperienceOrbEntity entity) {
        this.id = entity.getNetworkId();
        this.x = MathHelper.floor(entity.x * 32.0);
        this.y = MathHelper.floor(entity.y * 32.0);
        this.z = MathHelper.floor(entity.z * 32.0);
        this.xp = entity.getXp();
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.xp = buffer.readShort();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeShort(this.xp);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleAddExperienceOrb(this);
    }

    public int getId() {
        return this.id;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getXp() {
        return this.xp;
    }
}
