package net.zaminmc.torch.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The encode-once primitive's lifetime pins (§35.3 + §35.4): the share is
 * an index-independent view (the per-channel read-consume hazard — two
 * consumers each read the FULL bytes), the refcount law is exact
 * (base + N shares), and a mis-handover is refused, not leaked.
 */
class EncodedPayloadTest {

    private static ByteBuf encoded(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }

    private static byte[] readAll(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    @Test
    void shareIsIndexIndependentEachConsumerReadsTheWholeBytes() {
        // The hazard this pins: a consuming encoder (each channel's own
        // compression/frame walk) READS-CONSUMES the buffer it encodes. A
        // shared plain buffer would hand the second channel an empty read.
        // The share must be an independent view: every consumer reads the
        // identical bytes, whatever the order.
        EncodedPayload payload = EncodedPayload.of(encoded("payload-bytes"));
        ByteBuf first = payload.share();
        ByteBuf second = payload.share();

        assertArrayEquals("payload-bytes".getBytes(StandardCharsets.UTF_8), readAll(first));
        assertArrayEquals("payload-bytes".getBytes(StandardCharsets.UTF_8), readAll(second));

        first.release();
        second.release();
        payload.release();
        assertEquals(0, payload.liveReferences());
    }

    @Test
    void refcountLawBasePlusSharesExactly() {
        EncodedPayload payload = EncodedPayload.of(encoded("abc"));
        assertEquals(1, payload.liveReferences());

        ByteBuf a = payload.share();
        ByteBuf b = payload.share();
        ByteBuf c = payload.share();
        assertEquals(4, payload.liveReferences());

        a.release();
        b.release();
        assertEquals(2, payload.liveReferences());

        c.release();
        payload.release();
        assertEquals(0, payload.liveReferences());
    }

    @Test
    void sizeMatchesEveryShareAndRefusesBadHandover() {
        EncodedPayload payload = EncodedPayload.of(encoded("12345"));
        assertEquals(5, payload.size());
        ByteBuf share = payload.share();
        assertEquals(5, share.readableBytes());
        share.release();

        // A buffer handed over twice (refCnt != 1) is refused — the wrong
        // ownership assumption must surface here, not as a use-after-free.
        // The refusal releases the handed-over reference once (refCnt 2 -> 1).
        ByteBuf twice = Unpooled.buffer(4);
        twice.retain();
        assertThrows(IllegalArgumentException.class, () -> EncodedPayload.of(twice));
        assertEquals(1, twice.refCnt());
        twice.release();
        assertEquals(0, twice.refCnt());
        payload.release();
        assertTrue(payload.liveReferences() == 0);
    }
}
