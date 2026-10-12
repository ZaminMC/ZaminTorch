package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.protocol.OutboundPacket;
import net.zaminmc.torch.server.net.PacketHookRegistry;
import net.zaminmc.torch.server.player.PlayerSession;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * The outbound interception point (§27.3's packet hooks, 1.8.8 side): one
 * Netty handler per connection, sitting between the connection's writers
 * and the frame encoder — every PLAY-state frame passes here as the raw
 * wire form (varint id + payload) before the length prefix is applied.
 *
 * <p>The walk: parse the id without consuming the buffer, hand the
 * registry each listener (registration order, exception-isolated — the
 * registry's own law), then forward. A cancelled frame is released and
 * never reaches the wire; a replaced payload is rebuilt with the SAME
 * packet id (the listener never rewrites the frame's identity) and
 * forwarded. Pre-login frames (status, login phase) ride untouched — the
 * public surface scopes itself to gameplay traffic.</p>
 */
final class PacketHookOutboundHandler extends ChannelOutboundHandlerAdapter {

    static final String NAME = "torch-packet-hook";

    private final PacketHookRegistry hooks;
    private final V18Connection connection;

    PacketHookOutboundHandler(PacketHookRegistry hooks, V18Connection connection) {
        this.hooks = hooks;
        this.connection = connection;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.write(msg, promise); // not a packet buffer: nothing to inspect
            return;
        }
        PlayerSession recipient = connection.currentSession();
        if (recipient == null) {
            ctx.write(msg, promise); // pre-login traffic: out of the public scope
            return;
        }
        // The id varint, parsed without consuming the frame.
        int idVarIntLength = peekVarIntLength(frame);
        if (idVarIntLength < 0) {
            ctx.write(msg, promise); // an unparseable edge: honest passthrough
            return;
        }
        int packetId = readVarIntAt(frame, idVarIntLength);
        HookPacket packet = new HookPacket(recipient, packetId, frame, idVarIntLength);
        boolean any = hooks.fire(packet);
        if (packet.cancelled) {
            frame.release();
            promise.trySuccess(); // delivered nowhere, by the listener's own call
            return;
        }
        if (any && packet.replacement != null) {
            ByteBuf rebuilt = Unpooled.buffer(idVarIntLength + packet.replacement.length);
            rebuilt.writeBytes(frame, frame.readerIndex(), idVarIntLength); // the id varint, verbatim
            rebuilt.writeBytes(packet.replacement);
            frame.release();
            ctx.write(rebuilt, promise);
            return;
        }
        ctx.write(msg, promise);
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

    /** The callback-scoped handle the listeners see. */
    private static final class HookPacket implements OutboundPacket {

        private final Player recipient;
        private final int packetId;
        private final ByteBuf frame;
        private final int idVarIntLength;

        private boolean cancelled;
        private byte[] replacement;

        HookPacket(Player recipient, int packetId, ByteBuf frame, int idVarIntLength) {
            this.recipient = recipient;
            this.packetId = packetId;
            this.frame = frame;
            this.idVarIntLength = idVarIntLength;
        }

        @Override public Player recipient() {
            return recipient;
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

        @Override public void payload(byte[] replacement) {
            this.replacement = replacement == null ? new byte[0] : replacement.clone();
        }
    }
}
