package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;

public class SplitterHandler extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();
        byte[] abyte = new byte[3];

        for (int i = 0; i < abyte.length; i++) {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }

            abyte[i] = in.readByte();
            if (abyte[i] >= 0) {
                PacketByteBuf packetbytebuf = new PacketByteBuf(Unpooled.wrappedBuffer(abyte));

                try {
                    int j = packetbytebuf.readVarInt();
                    if (in.readableBytes() >= j) {
                        out.add(in.readBytes(j));
                        return;
                    }

                    in.resetReaderIndex();
                } finally {
                    packetbytebuf.release();
                }

                return;
            }
        }

        throw new CorruptedFrameException("length wider than 21-bit");
    }
}
