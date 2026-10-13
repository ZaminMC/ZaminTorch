package net.zaminmc.torch.protocol;

/**
 * One observer of inbound packets (§27.3's packet interception hooks,
 * the C2S mirror of {@link PacketListener}).
 *
 * <p><b>Threading:</b> listeners run on the sender's wire thread (the
 * protocol adapter's I/O context) — never the simulation owner lane. A
 * listener must not block (no cross-owner waits, §27.6's own law) and
 * must not touch live engine state outside its ownership contract;
 * safe work from here is packet inspection, cancellation, and posting
 * a task through {@code World.execute}.</p>
 *
 * <p><b>Ordering and isolation:</b> listeners run in registration
 * order on each frame; each listener's exception is logged and skipped
 * (the walk continues with the frame's current state — a cancelled
 * frame stays cancelled).</p>
 */
@FunctionalInterface
public interface InboundPacketListener {

    /**
     * Observes one inbound frame. The handle is valid only inside this
     * callback.
     *
     * @param packet the frame on its way from
     *        {@link InboundPacket#sender()}'s connection into the engine
     */
    void onReceive(InboundPacket packet);
}
