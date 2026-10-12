package net.zaminmc.torch.server.net;

import net.zaminmc.torch.protocol.PacketListener;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The outbound packet hooks (§27.3's interception surface), engine side:
 * the listener registry the protocol adapter's outbound walk consults.
 *
 * <p><b>Threading:</b> registration is safe from any thread; the fire
 * happens on each recipient's WIRE thread (the protocol adapter's I/O
 * context). The listener set rides a {@linkplain CopyOnWriteArrayList
 * copy-on-write} registry — the honest carrier for a table registered
 * rarely from any thread and iterated on the wire threads; it is not an
 * ownership substitute (rule 11): the listeners' own engine work still
 * obeys §27.6 (inspect-and-cancel here, simulation work through
 * {@code World.execute}).</p>
 */
public final class PacketHookRegistry implements net.zaminmc.torch.protocol.ProtocolHooks {

    private static final Logger LOGGER = Logger.getLogger(PacketHookRegistry.class.getName());

    private final CopyOnWriteArrayList<PacketListener> listeners = new CopyOnWriteArrayList<>();

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
}
