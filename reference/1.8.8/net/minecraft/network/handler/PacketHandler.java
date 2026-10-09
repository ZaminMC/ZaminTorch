package net.minecraft.network.handler;

import net.minecraft.text.Text;

public interface PacketHandler {
    void onDisconnect(Text reason);
}
