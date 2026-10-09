package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.packet.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class PacketDecoder extends ByteToMessageDecoder {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker MARKER = MarkerManager.getMarker("PACKET_RECEIVED", Connection.MARKER_NETWORK_PACKETS);
    private final PacketFlow flow;

    public PacketDecoder(PacketFlow flow) {
        this.flow = flow;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() != 0) {
            PacketByteBuf packetbytebuf = new PacketByteBuf(in);
            int i = packetbytebuf.readVarInt();
            Packet packet = ctx.channel().attr(Connection.PROTOCOL).get().createPacket(this.flow, i);
            if (packet == null) {
                throw new IOException("Bad packet id " + i);
            }

            packet.read(packetbytebuf);
            if (packetbytebuf.readableBytes() > 0) {
                throw new IOException(
                    "Packet "
                        + ctx.channel().attr(Connection.PROTOCOL).get().getId()
                        + "/"
                        + i
                        + " ("
                        + packet.getClass().getSimpleName()
                        + ") was larger than I expected, found "
                        + packetbytebuf.readableBytes()
                        + " bytes extra whilst reading packet "
                        + i
                );
            }

            out.add(packet);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(MARKER, " IN: [{}:{}] {}", ctx.channel().attr(Connection.PROTOCOL).get(), i, packet.getClass().getName());
            }
        }
    }
}
