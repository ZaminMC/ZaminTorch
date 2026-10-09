package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.block.Block;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;

public class BlockEventS2CPacket implements Packet<ClientPlayPacketHandler> {
    private BlockPos pos;
    private int type;
    private int data;
    private Block block;

    public BlockEventS2CPacket() {
    }

    public BlockEventS2CPacket(BlockPos pos, Block block, int type, int data) {
        this.pos = pos;
        this.type = type;
        this.data = data;
        this.block = block;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
        this.type = buffer.readUnsignedByte();
        this.data = buffer.readUnsignedByte();
        this.block = Block.byId(buffer.readVarInt() & 4095);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);
        buffer.writeByte(this.type);
        buffer.writeByte(this.data);
        buffer.writeVarInt(Block.getId(this.block) & 4095);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleBlockEvent(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getType() {
        return this.type;
    }

    public int getData() {
        return this.data;
    }

    public Block getBlock() {
        return this.block;
    }
}
