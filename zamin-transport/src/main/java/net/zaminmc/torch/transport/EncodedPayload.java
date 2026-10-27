package net.zaminmc.torch.transport;

import io.netty.buffer.ByteBuf;

/**
 * One encoded packet shared across recipients (§35.3): the immutable
 * encoded-payload primitive the encode-once broadcast rides.
 *
 * <p><b>The shape</b>: a semantic update encodes ONCE into a ByteBuf; every
 * eligible recipient's queue takes a {@link #share()} of the same bytes; the
 * creator's own reference drops through {@link #release()}. The optimization
 * is the elimination of repeated serialization and allocation — each
 * recipient's channel still performs its own transmission, compression, and
 * encryption (§35.3's own law: the shared plaintext rides each pipeline
 * independently).</p>
 *
 * <p><b>The ownership law</b> (§35.4's rows, explicit):
 * <ul>
 *   <li>creator/owner — the caller that built the encoded bytes; {@code of}
 *       takes ownership of the buffer's single reference;</li>
 *   <li>retained references — every {@code share()} retains one ticket into
 *       the shared refcount; the ticket is an index-independent view, so a
 *       consuming encoder's read (the per-channel read-consume walk) cannot
 *       move another recipient's read position;</li>
 *   <li>queue ownership — the enqueued ticket belongs to the queue until the
 *       channel's pipeline consumes it (the frame encoder's post-encode
 *       release) or the queue drops it (close/cancel);</li>
 *   <li>release responsibility — the creator calls {@link #release()} once
 *       the distribution loop ends; Netty's write-failure path releases the
 *       rest;</li>
 *   <li>failure-path release — a share enqueued to a dead or closing channel
 *       is released by the queue's admission/drop paths, never silently
 *       leaked;</li>
 *   <li>shutdown behavior — a closing queue releases every queued ticket
 *       (the {@code packetsDropped} counter is the leak's tripwire).</li>
 * </ul></p>
 *
 * <p><b>The shareability preconditions</b> (§35.3's own list) are the
 * CALLER's law, not this class's: the caller must already have established
 * that the encoded bytes are identical for every recipient, that
 * recipient-specific fields are excluded, and that no recipient can mutate
 * the representation. This class only makes the lifetime correct.</p>
 */
public final class EncodedPayload {

    private final ByteBuf bytes;

    private EncodedPayload(ByteBuf bytes) {
        this.bytes = bytes;
    }

    /**
     * Takes ownership of the encoded packet. The buffer must carry exactly
     * the packet's bytes, fully written (its read indices are irrelevant —
     * every share starts from the same view).
     */
    public static EncodedPayload of(ByteBuf encoded) {
        if (encoded.refCnt() != 1) {
            encoded.release();
            throw new IllegalArgumentException(
                    "EncodedPayload.of takes exactly one reference, got refCnt=" + encoded.refCnt());
        }
        return new EncodedPayload(encoded);
    }

    /**
     * One recipient's ticket: an index-independent retained view of the
     * encoded bytes. The ticket joins one queue and is released by the
     * channel's consumption or the queue's drop path.
     */
    public ByteBuf share() {
        return this.bytes.retainedDuplicate();
    }

    /** The creator's base reference — call exactly once, after the distribution loop. */
    public void release() {
        this.bytes.release();
    }

    /** @return the encoded packet's byte size (the same for every share). */
    public int size() {
        return this.bytes.readableBytes();
    }

    /** @return the current shared refcount (the base + the live shares) — diagnostics only. */
    public int liveReferences() {
        return this.bytes.refCnt();
    }
}
