package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class ParticleS2CPacket implements Packet<ClientPlayPacketHandler> {
    private ParticleType type;
    private float x;
    private float y;
    private float z;
    private float velocityX;
    private float velocityY;
    private float velocityZ;
    private float velocityScale;
    private int count;
    private boolean ignoreDistance;
    private int[] parameters;

    public ParticleS2CPacket() {
    }

    public ParticleS2CPacket(
        ParticleType type,
        boolean ignoreDistance,
        float x,
        float y,
        float z,
        float velocityX,
        float velocityY,
        float velocityZ,
        float velocityScale,
        int count,
        int... parameters
    ) {
        this.type = type;
        this.ignoreDistance = ignoreDistance;
        this.x = x;
        this.y = y;
        this.z = z;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.velocityScale = velocityScale;
        this.count = count;
        this.parameters = parameters;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.type = ParticleType.byId(buffer.readInt());
        if (this.type == null) {
            this.type = ParticleType.BARRIER;
        }

        this.ignoreDistance = buffer.readBoolean();
        this.x = buffer.readFloat();
        this.y = buffer.readFloat();
        this.z = buffer.readFloat();
        this.velocityX = buffer.readFloat();
        this.velocityY = buffer.readFloat();
        this.velocityZ = buffer.readFloat();
        this.velocityScale = buffer.readFloat();
        this.count = buffer.readInt();
        int i = this.type.getParameterCount();
        this.parameters = new int[i];

        for (int j = 0; j < i; j++) {
            this.parameters[j] = buffer.readVarInt();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.type.getId());
        buffer.writeBoolean(this.ignoreDistance);
        buffer.writeFloat(this.x);
        buffer.writeFloat(this.y);
        buffer.writeFloat(this.z);
        buffer.writeFloat(this.velocityX);
        buffer.writeFloat(this.velocityY);
        buffer.writeFloat(this.velocityZ);
        buffer.writeFloat(this.velocityScale);
        buffer.writeInt(this.count);
        int i = this.type.getParameterCount();

        for (int j = 0; j < i; j++) {
            buffer.writeVarInt(this.parameters[j]);
        }
    }

    public ParticleType getType() {
        return this.type;
    }

    public boolean getIgnoreDistance() {
        return this.ignoreDistance;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public float getVelocityX() {
        return this.velocityX;
    }

    public float getVelocityY() {
        return this.velocityY;
    }

    public float getVelocityZ() {
        return this.velocityZ;
    }

    public float getVelocityScale() {
        return this.velocityScale;
    }

    public int getCount() {
        return this.count;
    }

    public int[] getParameters() {
        return this.parameters;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleParticle(this);
    }
}
