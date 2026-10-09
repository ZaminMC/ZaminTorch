package net.minecraft.network.packet.s2c.play;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ExplosionS2CPacket implements Packet<ClientPlayPacketHandler> {
    private double x;
    private double y;
    private double z;
    private float power;
    private List<BlockPos> damagedBlocks;
    private float playerVelocityX;
    private float playerVelocityY;
    private float playerVelocityZ;

    public ExplosionS2CPacket() {
    }

    public ExplosionS2CPacket(double x, double y, double z, float power, List<BlockPos> damagedBlocks, Vec3d playerVelocity) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
        this.damagedBlocks = Lists.newArrayList(damagedBlocks);
        if (playerVelocity != null) {
            this.playerVelocityX = (float)playerVelocity.x;
            this.playerVelocityY = (float)playerVelocity.y;
            this.playerVelocityZ = (float)playerVelocity.z;
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.x = buffer.readFloat();
        this.y = buffer.readFloat();
        this.z = buffer.readFloat();
        this.power = buffer.readFloat();
        int i = buffer.readInt();
        this.damagedBlocks = Lists.newArrayListWithCapacity(i);
        int j = (int)this.x;
        int k = (int)this.y;
        int l = (int)this.z;

        for (int i1 = 0; i1 < i; i1++) {
            int j1 = buffer.readByte() + j;
            int k1 = buffer.readByte() + k;
            int l1 = buffer.readByte() + l;
            this.damagedBlocks.add(new BlockPos(j1, k1, l1));
        }

        this.playerVelocityX = buffer.readFloat();
        this.playerVelocityY = buffer.readFloat();
        this.playerVelocityZ = buffer.readFloat();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeFloat((float)this.x);
        buffer.writeFloat((float)this.y);
        buffer.writeFloat((float)this.z);
        buffer.writeFloat(this.power);
        buffer.writeInt(this.damagedBlocks.size());
        int i = (int)this.x;
        int j = (int)this.y;
        int k = (int)this.z;

        for (BlockPos blockpos : this.damagedBlocks) {
            int l = blockpos.getX() - i;
            int i1 = blockpos.getY() - j;
            int j1 = blockpos.getZ() - k;
            buffer.writeByte(l);
            buffer.writeByte(i1);
            buffer.writeByte(j1);
        }

        buffer.writeFloat(this.playerVelocityX);
        buffer.writeFloat(this.playerVelocityY);
        buffer.writeFloat(this.playerVelocityZ);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleExplosion(this);
    }

    public float getPlayerVelocityX() {
        return this.playerVelocityX;
    }

    public float getPlayerVelocityY() {
        return this.playerVelocityY;
    }

    public float getPlayerVelocityZ() {
        return this.playerVelocityZ;
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

    public float getPower() {
        return this.power;
    }

    public List<BlockPos> getDamagedBlocks() {
        return this.damagedBlocks;
    }
}
