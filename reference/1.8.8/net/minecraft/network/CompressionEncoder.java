package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

public class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    private final byte[] buffer = new byte[8192];
    private final Deflater deflator;
    private int threshold;

    public CompressionEncoder(int threshold) {
        this.threshold = threshold;
        this.deflator = new Deflater();
    }

    protected void encode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, ByteBuf byteBuf2) throws Exception {
        int i = byteBuf.readableBytes();
        PacketByteBuf packetbytebuf = new PacketByteBuf(byteBuf2);
        if (i < this.threshold) {
            packetbytebuf.writeVarInt(0);
            packetbytebuf.writeBytes(byteBuf);
        } else {
            byte[] abyte = new byte[i];
            byteBuf.readBytes(abyte);
            packetbytebuf.writeVarInt(abyte.length);
            this.deflator.setInput(abyte, 0, i);
            this.deflator.finish();

            while (!this.deflator.finished()) {
                int j = this.deflator.deflate(this.buffer);
                packetbytebuf.writeBytes(this.buffer, 0, j);
            }

            this.deflator.reset();
        }
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
