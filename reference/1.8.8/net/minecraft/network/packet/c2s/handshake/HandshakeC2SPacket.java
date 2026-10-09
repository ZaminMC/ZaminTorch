package net.minecraft.network.packet.c2s.handshake;

import java.io.IOException;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerHandshakePacketHandler;

public class HandshakeC2SPacket implements Packet<ServerHandshakePacketHandler> {
    private int version;
    private String address;
    private int port;
    private NetworkProtocol username;

    public HandshakeC2SPacket() {
    }

    public HandshakeC2SPacket(int version, String address, int port, NetworkProtocol protocol) {
        this.version = version;
        this.address = address;
        this.port = port;
        this.username = protocol;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.version = buffer.readVarInt();
        this.address = buffer.readString(255);
        this.port = buffer.readUnsignedShort();
        this.username = NetworkProtocol.byId(buffer.readVarInt());
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.version);
        buffer.writeString(this.address);
        buffer.writeShort(this.port);
        buffer.writeVarInt(this.username.getId());
    }

    public void handle(ServerHandshakePacketHandler serverHandshakePacketHandler) {
        serverHandshakePacketHandler.handleHandshake(this);
    }

    public NetworkProtocol getUsername() {
        return this.username;
    }

    public int getVersion() {
        return this.version;
    }
}
