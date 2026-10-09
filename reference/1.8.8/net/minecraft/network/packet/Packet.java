package net.minecraft.network.packet;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.handler.PacketHandler;

public interface Packet<T extends PacketHandler> {
    void read(PacketByteBuf buffer) throws IOException;

    void write(PacketByteBuf buffer) throws IOException;

    void handle(T handler);
}
