package net.minecraft.network.packet.c2s.login;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.SecretKey;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.encryption.EncryptionUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerLoginPacketHandler;

public class KeyC2SPacket implements Packet<ServerLoginPacketHandler> {
    private byte[] reply = new byte[0];
    private byte[] nonce = new byte[0];

    public KeyC2SPacket() {
    }

    public KeyC2SPacket(SecretKey secretKey, PublicKey publicKey, byte[] nonce) {
        this.reply = EncryptionUtils.encrypt(publicKey, secretKey.getEncoded());
        this.nonce = EncryptionUtils.encrypt(publicKey, nonce);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.reply = buffer.readByteArray();
        this.nonce = buffer.readByteArray();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByteArray(this.reply);
        buffer.writeByteArray(this.nonce);
    }

    public void handle(ServerLoginPacketHandler serverLoginPacketHandler) {
        serverLoginPacketHandler.handleKey(this);
    }

    public SecretKey getSecretKey(PrivateKey privateKey) {
        return EncryptionUtils.decryptSecretKey(privateKey, this.reply);
    }

    public byte[] getNonce(PrivateKey privateKey) {
        return privateKey == null ? this.nonce : EncryptionUtils.decrypt(privateKey, this.nonce);
    }
}
