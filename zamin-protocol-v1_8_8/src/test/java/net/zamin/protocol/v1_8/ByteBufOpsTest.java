package net.zamin.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteBufOpsTest {

    @Test
    void varIntRoundTripsAcrossBoundaries() {
        int[] values = {0, 1, 127, 128, 255, 16383, 16384, Integer.MAX_VALUE, -1};
        for (int value : values) {
            ByteBuf buffer = Unpooled.buffer(8);
            ByteBufOps.writeVarInt(buffer, value);
            assertEquals(value, ByteBufOps.readVarInt(buffer), "roundtrip of " + value);
            buffer.release();
        }
    }

    @Test
    void varIntSizeMatchesWrittenBytes() {
        for (int value : new int[]{0, 127, 128, 16383, 16384, Integer.MAX_VALUE}) {
            ByteBuf buffer = Unpooled.buffer(8);
            ByteBufOps.writeVarInt(buffer, value);
            assertEquals(buffer.readableBytes(), ByteBufOps.varIntSize(value));
            buffer.release();
        }
    }

    @Test
    void stringRoundTrips() {
        ByteBuf buffer = Unpooled.buffer(64);
        ByteBufOps.writeString(buffer, "ZaminTorch");
        ByteBufOps.writeString(buffer, "héllo ünïcode ✦");
        assertEquals("ZaminTorch", ByteBufOps.readString(buffer, 64));
        assertEquals("héllo ünïcode ✦", ByteBufOps.readString(buffer, 64));
        buffer.release();
    }

    @Test
    void truncatedStringIsRejected() {
        ByteBuf buffer = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(buffer, 100); // declares 100 bytes
        buffer.writeBytes(new byte[10]);     // but only 10 arrive
        assertThrows(IllegalArgumentException.class, () -> ByteBufOps.readString(buffer, 64));
        buffer.release();
    }

    @Test
    void oversizedVarIntIsRejected() {
        ByteBuf buffer = Unpooled.buffer(8);
        for (int i = 0; i < 6; i++) {
            buffer.writeByte(0x80); // five+ continuation bits, never terminates under 28 shifts
        }
        buffer.writeByte(0x7F);
        assertThrows(IllegalArgumentException.class, () -> ByteBufOps.readVarInt(buffer));
        buffer.release();
    }

    @Test
    void packedBlockPositionRoundTrips() {
        ByteBuf buffer = Unpooled.buffer(16);
        ByteBufOps.writePackedBlockPosition(buffer, 123456, 200, -98765);
        long packed = buffer.readLong();
        int x = (int) (packed >> 38);
        int y = (int) (packed << 52 >> 52);
        int z = (int) (packed << 26 >> 38);
        assertEquals(123456, x);
        assertEquals(200, y);
        assertEquals(-98765, z);
        buffer.release();
    }
}
