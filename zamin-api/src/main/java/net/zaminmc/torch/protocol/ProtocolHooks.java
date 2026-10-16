package net.zaminmc.torch.protocol;

import java.util.List;

/**
 * The protocol hooks (§27.3's packet interception surface): observe,
 * cancel, and re-payload the outbound frames of the active protocol
 * version.
 *
 * <p><b>Scope:</b> the hooks see a player's PLAY-state outbound frames
 * (the gameplay traffic — entity tracking, chat, windows, world sync).
 * Login- and status-phase frames ride the wire untouched; the
 * connection-lifecycle family (§27.3) observes joins through
 * {@code PlayerJoinEvent}/{@code PlayerQuitEvent} instead.</p>
 *
 * <p><b>Threading:</b> {@link #addListener} and {@link #removeListener}
 * are safe from any thread; the listeners themselves run on each
 * recipient's wire thread (see {@link PacketListener}).</p>
 */
public interface ProtocolHooks {

    /**
     * Registers a listener for outbound packets. Safe from any thread.
     *
     * @return true when the listener was not already registered
     */
    boolean addListener(PacketListener listener);

    /**
     * Removes a listener. Safe from any thread.
     *
     * @return true when the listener stood registered
     */
    boolean removeListener(PacketListener listener);

    /**
     * The live listener set, in registration order — a point-in-time
     * snapshot; safe to consume anywhere.
     */
    List<PacketListener> listeners();

    /**
     * Registers a listener for inbound packets (the C2S mirror — every
     * PLAY-state frame a session sends, before the engine decodes it).
     * Safe from any thread.
     *
     * @return true when the listener was not already registered
     */
    boolean addInboundListener(InboundPacketListener listener);

    /**
     * Removes an inbound listener. Safe from any thread.
     *
     * @return true when the listener stood registered
     */
    boolean removeInboundListener(InboundPacketListener listener);

    /**
     * The live inbound listener set, in registration order — a
     * point-in-time snapshot; safe to consume anywhere.
     */
    List<InboundPacketListener> inboundListeners();

    /**
     * Sends one composed PLAY-state frame to a player (§27.3's packet
     * construction): the active protocol version's varint packet id plus
     * the payload bytes, framed and flushed through the same pipeline as
     * the engine's own traffic.
     *
     * <p><b>Interception:</b> composed frames walk the same
     * {@link PacketListener} walk as engine frames — a listener may
     * observe, cancel, or re-payload them like any other outbound frame.
     * Inbound listeners never see them (nothing arrives from the
     * client).</p>
     *
     * <p><b>Correctness:</b> the sender owns the frame's validity — the
     * id must exist in the active version's PLAY table and the payload
     * must match the client's expectation for it (a malformed frame risks
     * the client's own disconnect — the raw surface's law; the engine
     * does not validate plugin-composed frames).</p>
     *
     * <p><b>Threading:</b> safe from any thread (the wire write itself is
     * non-blocking); never blocks. A recipient whose connection is not
     * in the PLAY state (or has no wire — the headless engine) drops the
     * frame silently: the operation's documented result, not an error.</p>
     *
     * @param recipient the player to send to
     * @param packetId the id in the ACTIVE version's PLAY-state table
     * @param payload the frame's bytes after the id (copied; never null)
     */
    void send(net.zaminmc.torch.entity.Player recipient, int packetId, byte[] payload);
}
