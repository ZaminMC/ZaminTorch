package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;

/**
 * VarInt length-prefix framing. One instance pair per pipeline; both halves are
 * stateless so a shared instance is safe.
 */
final class FrameCodec {

    static final String DECODER_NAME = "frame-decoder";
    static final String ENCODER_NAME = "frame-encoder";

    private FrameCodec() {
    }

    static final class Decoder extends ByteToMessageDecoder {

        private static final int MAX_FRAME = 2_097_151; // 3-byte varint maximum, generous for slice 1

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 1) {
                return;
            }
            in.markReaderIndex();
            int length;
            try {
                length = ByteBufOps.readVarInt(in);
            } catch (IllegalArgumentException e) {
                ctx.close(); // malformed framing: fail the connection predictably (§126)
                return;
            }
            if (length == Integer.MIN_VALUE) {
                in.resetReaderIndex();
                return;
            }
            if (length > MAX_FRAME) {
                ctx.close(); // absurd frame size: reject instead of allocating (§120 backpressure)
                return;
            }
            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }
            out.add(in.readRetainedSlice(length));
        }
    }

    static final class Encoder extends MessageToByteEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
            ByteBufOps.writeVarInt(out, msg.readableBytes());
            out.writeBytes(msg);
        }
    }
}
