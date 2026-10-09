package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.Validate;

public class SoundEventS2CPacket implements Packet<ClientPlayPacketHandler> {
    private String name;
    private int x;
    private int y = Integer.MAX_VALUE;
    private int z;
    private float volume;
    private int pitch;

    public SoundEventS2CPacket() {
    }

    public SoundEventS2CPacket(String name, double x, double y, double z, float volume, float pitch) {
        Validate.notNull(name, "name");
        this.name = name;
        this.x = (int)(x * 8.0);
        this.y = (int)(y * 8.0);
        this.z = (int)(z * 8.0);
        this.volume = volume;
        this.pitch = (int)(pitch * 63.0F);
        pitch = MathHelper.clamp(pitch, 0.0F, 255.0F);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.name = buffer.readString(256);
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.volume = buffer.readFloat();
        this.pitch = buffer.readUnsignedByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.name);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeFloat(this.volume);
        buffer.writeByte(this.pitch);
    }

    public String getSound() {
        return this.name;
    }

    public double getX() {
        return this.x / 8.0F;
    }

    public double getY() {
        return this.y / 8.0F;
    }

    public double getZ() {
        return this.z / 8.0F;
    }

    public float getVolume() {
        return this.volume;
    }

    public float getPitch() {
        return this.pitch / 63.0F;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleSoundEvent(this);
    }
}
