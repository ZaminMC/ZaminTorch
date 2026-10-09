package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SignUpdateS2CPacket implements Packet<ClientPlayPacketHandler> {
    private World world;
    private BlockPos pos;
    private Text[] lines;

    public SignUpdateS2CPacket() {
    }

    public SignUpdateS2CPacket(World world, BlockPos pos, Text[] lines) {
        this.world = world;
        this.pos = pos;
        this.lines = new Text[]{lines[0], lines[1], lines[2], lines[3]};
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
        this.lines = new Text[4];

        for (int i = 0; i < 4; i++) {
            this.lines[i] = buffer.readText();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);

        for (int i = 0; i < 4; i++) {
            buffer.writeText(this.lines[i]);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleSignBlockEntityUpdate(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Text[] getLines() {
        return this.lines;
    }
}
