package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockUpdateS2CPacket implements Packet<ClientPlayPacketHandler> {
    private BlockPos pos;
    private BlockState state;

    public BlockUpdateS2CPacket() {
    }

    public BlockUpdateS2CPacket(World world, BlockPos pos) {
        this.pos = pos;
        this.state = world.getBlockState(pos);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
        this.state = Block.STATE_REGISTRY.get(buffer.readVarInt());
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);
        buffer.writeVarInt(Block.STATE_REGISTRY.getId(this.state));
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleBlockUpdate(this);
    }

    public BlockState getBlockState() {
        return this.state;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
