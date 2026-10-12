package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;

public class BlockEntityUpdateS2CPacket implements Packet<ClientPlayPacketHandler> {
    private BlockPos pos;
    private int type;
    private NbtCompound nbt;

    public BlockEntityUpdateS2CPacket() {
    }

    public BlockEntityUpdateS2CPacket(BlockPos pos, int type, NbtCompound nbt) {
        this.pos = pos;
        this.type = type;
        this.nbt = nbt;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
        this.type = buffer.readUnsignedByte();
        this.nbt = buffer.readNbtCompound();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);
        buffer.writeByte((byte)this.type);
        buffer.writeNbtCompound(this.nbt);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleBlockEntityUpdate(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getType() {
        return this.type;
    }

    public NbtCompound getNbt() {
        return this.nbt;
    }
}
