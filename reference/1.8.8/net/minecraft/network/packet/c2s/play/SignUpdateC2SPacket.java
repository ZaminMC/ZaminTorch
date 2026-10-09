package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class SignUpdateC2SPacket implements Packet<ServerPlayPacketHandler> {
    private BlockPos pos;
    private Text[] lines;

    public SignUpdateC2SPacket() {
    }

    public SignUpdateC2SPacket(BlockPos pos, Text[] lines) {
        this.pos = pos;
        this.lines = new Text[]{lines[0], lines[1], lines[2], lines[3]};
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
        this.lines = new Text[4];

        for (int i = 0; i < 4; i++) {
            String s = buffer.readString(384);
            Text text = Text.Serializer.fromJson(s);
            this.lines[i] = text;
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);

        for (int i = 0; i < 4; i++) {
            Text text = this.lines[i];
            String s = Text.Serializer.toJson(text);
            buffer.writeString(s);
        }
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleSignUpdate(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Text[] getLines() {
        return this.lines;
    }
}
