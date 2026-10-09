package net.minecraft.client.network.handler;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.s2c.login.CompressionThresholdS2CPacket;
import net.minecraft.network.packet.s2c.login.HelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginFailS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;

public interface ClientLoginPacketHandler extends PacketHandler {
    void handleHello(HelloS2CPacket packet);

    void handleLoginSuccess(LoginSuccessS2CPacket packet);

    void handleLoginFail(LoginFailS2CPacket packet);

    void handleCompressionThreshold(CompressionThresholdS2CPacket packet);
}
