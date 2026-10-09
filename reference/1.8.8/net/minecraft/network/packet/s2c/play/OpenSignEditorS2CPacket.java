package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;

public class OpenSignEditorS2CPacket implements Packet<ClientPlayPacketHandler> {
    private BlockPos pos;

    public OpenSignEditorS2CPacket() {
    }

    public OpenSignEditorS2CPacket(BlockPos x) {
        this.pos = x;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleOpenSignEditor(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
