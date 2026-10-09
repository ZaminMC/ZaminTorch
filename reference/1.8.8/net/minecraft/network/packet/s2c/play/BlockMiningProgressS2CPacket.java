package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;

public class BlockMiningProgressS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private BlockPos pos;
    private int progress;

    public BlockMiningProgressS2CPacket() {
    }

    public BlockMiningProgressS2CPacket(int id, BlockPos pos, int progress) {
        this.id = id;
        this.pos = pos;
        this.progress = progress;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.pos = buffer.readBlockPos();
        this.progress = buffer.readUnsignedByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeBlockPos(this.pos);
        buffer.writeByte(this.progress);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleBlockMiningProgress(this);
    }

    public int getPlayerId() {
        return this.id;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getProgress() {
        return this.progress;
    }
}
