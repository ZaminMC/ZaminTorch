package net.minecraft.server.network.handler;

import net.minecraft.network.Connection;
import net.minecraft.network.packet.c2s.query.PingC2SPacket;
import net.minecraft.network.packet.c2s.query.ServerStatusC2SPacket;
import net.minecraft.network.packet.s2c.query.PingS2CPacket;
import net.minecraft.network.packet.s2c.query.ServerStatusS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class ServerQueryNetworkHandler implements ServerQueryPacketHandler {
    private static final Text DISCONNECT_REASON = new LiteralText("Status request has been handled.");
    private final MinecraftServer server;
    private final Connection connection;
    private boolean hasStatusRequest;

    public ServerQueryNetworkHandler(MinecraftServer server, Connection connection) {
        this.server = server;
        this.connection = connection;
    }

    @Override
    public void onDisconnect(Text reason) {
    }

    @Override
    public void handleServerStatus(ServerStatusC2SPacket packet) {
        if (this.hasStatusRequest) {
            this.connection.disconnect(DISCONNECT_REASON);
        } else {
            this.hasStatusRequest = true;
            this.connection.send(new ServerStatusS2CPacket(this.server.getStatus()));
        }
    }

    @Override
    public void handlePing(PingC2SPacket packet) {
        this.connection.send(new PingS2CPacket(packet.getTime()));
        this.connection.disconnect(DISCONNECT_REASON);
    }
}
