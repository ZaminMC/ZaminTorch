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
}
