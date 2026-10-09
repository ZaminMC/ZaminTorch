package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.StringUtils;

public class CommandSuggestionsC2SPacket implements Packet<ServerPlayPacketHandler> {
    private String command;
    private BlockPos pos;

    public CommandSuggestionsC2SPacket() {
    }

    public CommandSuggestionsC2SPacket(String command) {
        this(command, null);
    }

    public CommandSuggestionsC2SPacket(String command, BlockPos pos) {
        this.command = command;
        this.pos = pos;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.command = buffer.readString(32767);
        boolean flag = buffer.readBoolean();
        if (flag) {
            this.pos = buffer.readBlockPos();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(StringUtils.substring(this.command, 0, 32767));
        boolean flag = this.pos != null;
        buffer.writeBoolean(flag);
        if (flag) {
            buffer.writeBlockPos(this.pos);
        }
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleCommandSuggestions(this);
    }

    public String getCommand() {
        return this.command;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
