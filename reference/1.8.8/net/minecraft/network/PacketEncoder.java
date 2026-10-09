package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.IOException;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.AddPlayerS2CPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class PacketEncoder extends MessageToByteEncoder<Packet> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker MARKER = MarkerManager.getMarker("PACKET_SENT", Connection.MARKER_NETWORK_PACKETS);
    private final PacketFlow flow;

    public PacketEncoder(PacketFlow flow) {
        this.flow = flow;
    }

    protected void encode(ChannelHandlerContext channelHandlerContext, Packet packet, ByteBuf byteBuf) throws Exception {
        Integer integer = channelHandlerContext.channel().attr(Connection.PROTOCOL).get().getPacketId(this.flow, packet);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(MARKER, "OUT: [{}:{}] {}", channelHandlerContext.channel().attr(Connection.PROTOCOL).get(), integer, packet.getClass().getName());
        }

        if (integer == null) {
            throw new IOException("Can't serialize unregistered packet");
        }

        PacketByteBuf packetbytebuf = new PacketByteBuf(byteBuf);
        packetbytebuf.writeVarInt(integer);

        try {
            if (packet instanceof AddPlayerS2CPacket) {
                packet = packet;
            }

            packet.write(packetbytebuf);
        } catch (Throwable throwable) {
            LOGGER.error(throwable);
        }
    }
}
