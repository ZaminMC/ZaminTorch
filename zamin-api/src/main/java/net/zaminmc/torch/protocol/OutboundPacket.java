package net.zaminmc.torch.protocol;

import net.zaminmc.torch.entity.Player;

/**
 * One outbound packet on its way to a player's connection — the
 * inspection and interception handle of the protocol hooks
 * (§27.3's "packet construction/inspection/interception").
 *
 * <p>The packet is presented in the ACTIVE protocol version's raw wire
 * form: the packet id (the version's own table — protocol 47, Minecraft
 * 1.8.8, in this build) followed by the payload bytes. The id and the
 * id-to-name mapping belong to the versioned protocol surface; this
 * handle is deliberately version-shaped (§27.8: version-specific
 * behavior rides deliberate versioned surfaces, never a silently
 * reinterpreted common name).</p>
 *
 * <p><b>Lifecycle:</b> the handle is valid only inside the
 * {@link PacketListener#onSend} callback that received it — the backing
 * frame is released or forwarded when the walk ends. Copy anything that
 * must outlive the callback.</p>
 */
public interface OutboundPacket {

    /** The player the frame rides to. Constant for the callback's life. */
    Player recipient();

    /** The packet id in the active protocol version's table. */
    int packetId();

    /**
     * The payload after the packet id — a fresh copy, safe to keep and
     * mutate (the wire's own bytes are never handed out).
     */
    byte[] payload();

    /** Drops the frame: nothing reaches the recipient's connection. */
    void cancel();

    /** Whether this frame was dropped (a later listener may observe it). */
    boolean cancelled();

    /**
     * Replaces the payload after the packet id — the id stays. The
     * replacement is used verbatim on the wire; a malformed replacement
     * is the replacer's own protocol error to own. The whole-payload
     * replacement (the id rides along unchanged) is the deliberate
     * boundary: a listener never rewrites the packet's identity.
     */
    void payload(byte[] replacement);
}
