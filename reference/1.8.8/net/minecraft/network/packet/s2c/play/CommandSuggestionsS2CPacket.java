package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class CommandSuggestionsS2CPacket implements Packet<ClientPlayPacketHandler> {
    private String[] suggestions;

    public CommandSuggestionsS2CPacket() {
    }

    public CommandSuggestionsS2CPacket(String[] suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.suggestions = new String[buffer.readVarInt()];

        for (int i = 0; i < this.suggestions.length; i++) {
            this.suggestions[i] = buffer.readString(32767);
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.suggestions.length);

        for (String s : this.suggestions) {
            buffer.writeString(s);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleCommandSuggestions(this);
    }

    public String[] getSuggestions() {
        return this.suggestions;
    }
}
