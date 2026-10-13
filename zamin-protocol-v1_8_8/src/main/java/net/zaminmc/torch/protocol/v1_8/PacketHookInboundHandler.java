package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.protocol.InboundPacket;
import net.zaminmc.torch.server.net.PacketHookRegistry;
import net.zaminmc.torch.server.player.PlayerSession;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * The inbound interception point (§27.3's packet hooks, 1.8.8 side — the
 * C2S mirror of {@link PacketHookOutboundHandler}): one Netty handler
 * per connection, sitting between the frame decoder and the connection's
 * own dispatch — every PLAY-state frame a session sends passes here as
 * the raw wire form (varint id + payload) before the engine decodes it.
 *
 * <p>The walk: parse the id without consuming the buffer, hand the
 * registry each inbound listener (registration order, exception-isolated
 * — the registry's own law), then forward. A cancelled frame is released
 * and never reaches the engine: no decode, no authoritative mutation, no
 * semantic event. Pre-login frames (status, login phase) ride untouched
 * — the public surface scopes itself to gameplay traffic. There is no
 * payload replacement here by design (see {@link InboundPacket}): the
 * engine's parsers own the decode; the interception boundary is
 * observe-or-drop.</p>
 */
final class PacketHookInboundHandler extends ChannelInboundHandlerAdapter {

    static final String NAME = "torch-packet-hook-in";

    private final PacketHookRegistry hooks;
    private final V18Connection connection;

    PacketHookInboundHandler(PacketHookRegistry hooks, V18Connection connection) {
        this.hooks = hooks;
        this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg); // not a packet buffer: nothing to inspect
            return;
        }
        PlayerSession sender = connection.currentSession();
        if (sender == null) {
            ctx.fireChannelRead(msg); // pre-login traffic: out of the public scope
            return;
        }
        // The id varint, parsed without consuming the frame.
        int idVarIntLength = peekVarIntLength(frame);
        if (idVarIntLength < 0) {
            ctx.fireChannelRead(msg); // an unparseable edge: honest passthrough
            return;
        }
        int packetId = readVarIntAt(frame, idVarIntLength);
        HookInboundPacket packet = new HookInboundPacket(sender, packetId, frame, idVarIntLength);
        hooks.fireInbound(packet);
        if (packet.cancelled) {
            frame.release();
            return; // the engine never sees it
        }
        ctx.fireChannelRead(msg);
    }

    /** The id varint's byte length at the frame's reader index; -1 on truncation. */
    private static int peekVarIntLength(ByteBuf frame) {
        int at = frame.readerIndex();
        for (int i = 0; i < 5; i++) {
            if (at + i >= frame.writerIndex()) {
                return -1;
            }
            if ((frame.getUnsignedByte(at + i) & 0x80) == 0) {
                return i + 1;
            }
        }
        return -1; // a varint longer than five bytes is not a packet id
    }

    /** The varint value at the frame's reader index (the length is known). */
    private static int readVarIntAt(ByteBuf frame, int length) {
        int value = 0;
        int shift = 0;
        for (int i = 0; i < length; i++) {
            value |= (frame.getUnsignedByte(frame.readerIndex() + i) & 0x7F) << shift;
            shift += 7;
        }
        return value;
    }

    /** The callback-scoped handle the inbound listeners see. */
    private static final class HookInboundPacket implements InboundPacket {

        private final PlayerSession sender;
        private final int packetId;
        private final ByteBuf frame;
        private final int idVarIntLength;

        private boolean cancelled;

        HookInboundPacket(PlayerSession sender, int packetId, ByteBuf frame, int idVarIntLength) {
            this.sender = sender;
            this.packetId = packetId;
            this.frame = frame;
            this.idVarIntLength = idVarIntLength;
        }

        @Override public net.zaminmc.torch.entity.Player sender() {
            return sender;
        }

        @Override public int packetId() {
            return packetId;
        }

        @Override public byte[] payload() {
            // The wire's own bytes are never handed out — a fresh copy of
            // the payload AFTER the id varint (the frame keeps its reader
            // index for the forward).
            int payloadLength = frame.readableBytes() - idVarIntLength;
            byte[] copy = new byte[Math.max(payloadLength, 0)];
            frame.getBytes(frame.readerIndex() + idVarIntLength, copy);
            return copy;
        }

        @Override public void cancel() {
            cancelled = true;
        }

        @Override public boolean cancelled() {
            return cancelled;
        }
    }
}
