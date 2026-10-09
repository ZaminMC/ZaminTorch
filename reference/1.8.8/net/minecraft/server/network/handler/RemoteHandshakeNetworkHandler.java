package net.minecraft.server.network.handler;

import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginFailS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class RemoteHandshakeNetworkHandler implements ServerHandshakePacketHandler {
    private final MinecraftServer server;
    private final Connection connection;

    public RemoteHandshakeNetworkHandler(MinecraftServer server, Connection connection) {
        this.server = server;
        this.connection = connection;
    }

    @Override
    public void handleHandshake(HandshakeC2SPacket packet) {
        switch (packet.getUsername()) {
            case LOGIN:
                this.connection.setProtocol(NetworkProtocol.LOGIN);
                if (packet.getVersion() > 47) {
                    LiteralText literaltext = new LiteralText("Outdated server! I'm still on 1.8.8");
                    this.connection.send(new LoginFailS2CPacket(literaltext));
                    this.connection.disconnect(literaltext);
                } else if (packet.getVersion() < 47) {
                    LiteralText literaltext1 = new LiteralText("Outdated client! Please use 1.8.8");
                    this.connection.send(new LoginFailS2CPacket(literaltext1));
                    this.connection.disconnect(literaltext1);
                } else {
                    this.connection.setListener(new ServerLoginNetworkHandler(this.server, this.connection));
                }
                break;
            case STATUS:
                this.connection.setProtocol(NetworkProtocol.STATUS);
                this.connection.setListener(new ServerQueryNetworkHandler(this.server, this.connection));
                break;
            default:
                throw new UnsupportedOperationException("Invalid intention " + packet.getUsername());
        }
    }

    @Override
    public void onDisconnect(Text reason) {
    }
}
