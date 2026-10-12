package net.minecraft.network.packet.s2c.login;

import java.io.IOException;
import java.security.PublicKey;
import net.minecraft.client.network.handler.ClientLoginPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.encryption.EncryptionUtils;
import net.minecraft.network.packet.Packet;

public class HelloS2CPacket implements Packet<ClientLoginPacketHandler> {
    private String key;
    private PublicKey publicKey;
    private byte[] nonce;

    public HelloS2CPacket() {
    }

    public HelloS2CPacket(String key, PublicKey publicKey, byte[] nonce) {
        this.key = key;
        this.publicKey = publicKey;
        this.nonce = nonce;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.key = buffer.readString(20);
        this.publicKey = EncryptionUtils.generatePublicKey(buffer.readByteArray());
        this.nonce = buffer.readByteArray();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.key);
        buffer.writeByteArray(this.publicKey.getEncoded());
        buffer.writeByteArray(this.nonce);
    }

    public void handle(ClientLoginPacketHandler clientLoginPacketHandler) {
        clientLoginPacketHandler.handleHello(this);
    }

    public String getServerKey() {
        return this.key;
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    public byte[] getNonce() {
        return this.nonce;
    }
}
