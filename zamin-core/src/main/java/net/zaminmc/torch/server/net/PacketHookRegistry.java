package net.zaminmc.torch.server.net;

import net.zaminmc.torch.protocol.PacketListener;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The packet hooks (§27.3's interception surface), engine side: the
 * listener registries the protocol adapter's outbound and inbound walks
 * consult.
 *
 * <p><b>Threading:</b> registration is safe from any thread; the fires
 * happen on the wire threads (the protocol adapter's I/O context). The
 * listener sets ride {@linkplain CopyOnWriteArrayList copy-on-write}
 * registries — the honest carrier for tables registered rarely from any
 * thread and iterated on the wire threads; it is not an ownership
 * substitute (rule 11): the listeners' own engine work still obeys §27.6
 * (inspect-and-cancel here, simulation work through
 * {@code World.execute}).</p>
 */
public final class PacketHookRegistry implements net.zaminmc.torch.protocol.ProtocolHooks {

    private static final Logger LOGGER = Logger.getLogger(PacketHookRegistry.class.getName());

    private final CopyOnWriteArrayList<PacketListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<net.zaminmc.torch.protocol.InboundPacketListener>
            inboundListeners = new CopyOnWriteArrayList<>();

    @Override
    public boolean addListener(PacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        return listeners.addIfAbsent(listener);
    }

    @Override
    public boolean removeListener(PacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        return listeners.remove(listener);
    }

    @Override
    public List<PacketListener> listeners() {
        return List.copyOf(listeners);
    }

    @Override
    public boolean addInboundListener(
            net.zaminmc.torch.protocol.InboundPacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        return inboundListeners.addIfAbsent(listener);
    }

    @Override
    public boolean removeInboundListener(
            net.zaminmc.torch.protocol.InboundPacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        return inboundListeners.remove(listener);
    }

    @Override
    public List<net.zaminmc.torch.protocol.InboundPacketListener> inboundListeners() {
        return List.copyOf(inboundListeners);
    }

    /**
     * Fires the walk over every registered listener, in registration
     * order, each isolated — a throwing listener is logged and skipped,
     * the frame keeps whatever state earlier calls applied (the cancel
     * stays, the replacement stays). Runs on the recipient's wire thread.
     *
     * @return true when at least one listener stands registered
     */
    public boolean fire(net.zaminmc.torch.protocol.OutboundPacket packet) {
        if (listeners.isEmpty()) {
            return false;
        }
        for (PacketListener listener : listeners) {
            try {
                listener.onSend(packet);
            } catch (RuntimeException error) {
                LOGGER.warning("A packet listener threw on packet "
                        + packet.packetId() + " to " + packet.recipient().name()
                        + ": " + error);
            }
        }
        return true;
    }

    /**
     * Fires the inbound walk over every registered inbound listener, in
     * registration order, each isolated — a throwing listener is logged
     * and skipped, the frame keeps whatever state earlier calls applied
     * (the cancel stays). Runs on the sender's wire thread.
     *
     * @return true when at least one inbound listener stands registered
     */
    public boolean fireInbound(net.zaminmc.torch.protocol.InboundPacket packet) {
        if (inboundListeners.isEmpty()) {
            return false;
        }
        for (net.zaminmc.torch.protocol.InboundPacketListener listener : inboundListeners) {
            try {
                listener.onReceive(packet);
            } catch (RuntimeException error) {
                LOGGER.warning("An inbound packet listener threw on packet "
                        + packet.packetId() + " from " + packet.sender().name()
                        + ": " + error);
            }
        }
        return true;
    }
}
