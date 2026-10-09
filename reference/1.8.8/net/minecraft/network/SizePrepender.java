package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class SizePrepender extends MessageToByteEncoder<ByteBuf> {
    protected void encode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, ByteBuf byteBuf2) throws Exception {
        int i = byteBuf.readableBytes();
        int j = PacketByteBuf.getVarIntSizeBytes(i);
        if (j > 3) {
            throw new IllegalArgumentException("unable to fit " + i + " into " + 3);
        }

        PacketByteBuf packetbytebuf = new PacketByteBuf(byteBuf2);
        packetbytebuf.ensureWritable(j + i);
        packetbytebuf.writeVarInt(i);
        packetbytebuf.writeBytes(byteBuf, byteBuf.readerIndex(), i);
    }
}
