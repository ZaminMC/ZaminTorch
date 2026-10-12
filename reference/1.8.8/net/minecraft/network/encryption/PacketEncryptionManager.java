package net.minecraft.network.encryption;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class PacketEncryptionManager {
    private final Cipher cipher;
    private byte[] conversionBuffer = new byte[0];
    private byte[] encryptionBuffer = new byte[0];

    protected PacketEncryptionManager(Cipher cipher) {
        this.cipher = cipher;
    }

    private byte[] toByteArray(ByteBuf buffer) {
        int i = buffer.readableBytes();
        if (this.conversionBuffer.length < i) {
            this.conversionBuffer = new byte[i];
        }

        buffer.readBytes(this.conversionBuffer, 0, i);
        return this.conversionBuffer;
    }

    protected ByteBuf decrypt(ChannelHandlerContext context, ByteBuf buffer) throws ShortBufferException {
        int i = buffer.readableBytes();
        byte[] abyte = this.toByteArray(buffer);
        ByteBuf bytebuf = context.alloc().heapBuffer(this.cipher.getOutputSize(i));
        bytebuf.writerIndex(this.cipher.update(abyte, 0, i, bytebuf.array(), bytebuf.arrayOffset()));
        return bytebuf;
    }

    protected void encrypt(ByteBuf bufferIn, ByteBuf bufferOut) throws ShortBufferException {
        int i = bufferIn.readableBytes();
        byte[] abyte = this.toByteArray(bufferIn);
        int j = this.cipher.getOutputSize(i);
        if (this.encryptionBuffer.length < j) {
            this.encryptionBuffer = new byte[j];
        }

        bufferOut.writeBytes(this.encryptionBuffer, 0, this.cipher.update(abyte, 0, i, this.encryptionBuffer));
    }
}
