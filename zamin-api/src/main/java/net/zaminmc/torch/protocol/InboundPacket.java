package net.zaminmc.torch.protocol;

import net.zaminmc.torch.entity.Player;

/**
 * One inbound packet on its way from a player's connection into the
 * engine — the inspection and interception handle of the inbound
 * protocol hooks (§27.3's "packet construction/inspection/
 * interception", the C2S mirror of {@link OutboundPacket}).
 *
 * <p>The packet is presented in the ACTIVE protocol version's raw wire
 * form: the packet id (the version's own table — protocol 47, Minecraft
 * 1.8.8, in this build) followed by the payload bytes. The id and the
 * id-to-name mapping belong to the versioned protocol surface; this
 * handle is deliberately version-shaped (§27.8: version-specific
 * behavior rides deliberate versioned surfaces, never a silently
 * reinterpreted common name).</p>
 *
 * <p><b>Scope:</b> inbound hooks see a session's PLAY-state frames
 * (the gameplay traffic — movement, chat, clicks, held-slot switches).
 * Login- and status-phase frames ride untouched. There is no
 * payload-replacement verb on the inbound side by design: the engine's
 * own parsers own the decode (a hand-replaced payload could corrupt
 * authoritative state); the interception boundary is observe-or-drop,
 * and a plugin that wants to change a player's intent implements it at
 * the semantic layer (the command/event surfaces), not by rewriting
 * bytes mid-decode.</p>
 *
 * <p><b>Lifecycle:</b> the handle is valid only inside the
 * {@link InboundPacketListener#onReceive} callback that received it —
 * the backing frame is dropped or forwarded when the walk ends. Copy
 * anything that must outlive the callback.</p>
 */
public interface InboundPacket {

    /** The player whose connection sent the frame. Constant for the callback's life. */
    Player sender();

    /** The packet id in the active protocol version's table. */
    int packetId();

    /**
     * The payload after the packet id — a fresh copy, safe to keep and
     * mutate (the wire's own bytes are never handed out).
     */
    byte[] payload();

    /**
     * Drops the frame: the engine never sees it (no decode, no
     * authoritative mutation, no semantic event — the sender's client
     * believes it sent, the server simply did not listen).
     */
    void cancel();

    /** Whether this frame was dropped (a later listener may observe it). */
    boolean cancelled();
}
