package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slot NBT payload (the item-NBT slice's wire): a named stack encodes
 * {@code {display:{Name:"…"}}} as a vanilla NBT compound with short-length
 * modified-UTF-8 strings; the structural skip and the display-name reader
 * round-trip it byte-exactly, and an unnamed stack keeps the single TAG_End
 * marker the client's parser expects.
 */
class SlotNbtTest {

    @Test
    void unnamedStacksKeepTheSingleTagEndMarker() {
        ByteBuf out = Unpooled.buffer();
        assertFalse(SlotNbt.hasPayload((String) null));
        assertFalse(SlotNbt.hasPayload(""));
        // The caller (writeSlot) writes the plain 0 marker for these; SlotNbt
        // itself only owns the named path.
    }

    @Test
    void namedPayloadMatchesTheVanillaByteLayout() {
        ByteBuf out = Unpooled.buffer();
        SlotNbt.writeFor(out, namedStack("Hi"));
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        // 10 (root compound) | len 0 (root name) | 10 (display compound)
        // | len 7 "display" | 8 (Name string) | len 4 "Name"
        // | len 2 "Hi" | 0 (end display) | 0 (end root)
        assertArrayEquals(new byte[] {
                10, 0, 0,
                10, 0, 7, 'd', 'i', 's', 'p', 'l', 'a', 'y',
                8, 0, 4, 'N', 'a', 'm', 'e',
                0, 2, 'H', 'i',
                0, 0
        }, bytes);
    }

    @Test
    void displayNameReaderRoundTrips() {
        ByteBuf out = Unpooled.buffer();
        SlotNbt.writeFor(out, namedStack("Excalibur"));
        assertEquals("Excalibur", SlotNbt.readDisplayName(out));

        out.clear();
        out.writeByte(0); // the unnamed marker
        assertNull(SlotNbt.readDisplayName(out));
    }

    @Test
    void skipLandsExactlyAfterThePayload() {
        ByteBuf out = Unpooled.buffer();
        SlotNbt.writeFor(out, namedStack("Excalibur"));
        SlotNbt.skip(out);
        assertEquals(0, out.readableBytes(), "the skip consumed the whole payload");
    }

    @Test
    void modifiedUtf8RoundTripsAcrossRanges() {
        // ASCII, Latin-1 (2-byte), BMP (3-byte), the § formatting code, spaces.
        String[] samples = {
                "Excalibur", "§aGreen Name", "Schwert ö", "剑·圣者之刃", "café ☕ edge"};
        for (String sample : samples) {
            byte[] encoded = SlotNbt.ModifiedUtf8.encode(sample);
            assertEquals(sample, SlotNbt.ModifiedUtf8.decode(encoded),
                    "round trip: " + sample);
        }
        // U+0000 encodes as C0 80 (modified UTF-8's null spelling).
        byte[] nullChar = SlotNbt.ModifiedUtf8.encode("a\u0000b");
        assertArrayEquals(new byte[] {'a', (byte) 0xC0, (byte) 0x80, 'b'}, nullChar);
    }

    @Test
    void supplementaryCharactersEncodeAsCesuSurrogatePairs() {
        // Java's writeUTF encodes surrogate PAIRS as two 3-byte sequences
        // (CESU-8), not one 4-byte UTF-8 sequence: a char-level walk of the
        // string is exactly the historical behavior.
        String emoji = "sword \uD83D\uDE0E"; // U+1F60E as a surrogate pair
        byte[] encoded = SlotNbt.ModifiedUtf8.encode(emoji);
        assertEquals(emoji, SlotNbt.ModifiedUtf8.decode(encoded));
        // "sword " is six chars, the two surrogates take 3 bytes each (CESU-8).
        assertEquals(6 + 3 + 3, encoded.length);
    }

    @Test
    void readerFindsNestedDisplayNameAmongOtherTags() {
        // A compound shaped like a real anvil result: display plus other data.
        ByteBuf out = Unpooled.buffer();
        out.writeByte(10);          // root compound
        out.writeShort(0);          // root name
        out.writeByte(8);           // a plain string entry first
        writeShortString(out, "other");
        writeShortString(out, "ignored");
        out.writeByte(10);          // tag.display
        writeShortString(out, "display");
        {
            out.writeByte(8);
            writeShortString(out, "Name");
            writeShortString(out, "Found It");
        }
        out.writeByte(0);           // end display
        out.writeByte(0);           // end root
        assertEquals("Found It", SlotNbt.readDisplayName(out));
    }

    /** A named, unenchanted stack — the shape the display-only payload encodes. */
    private static net.zaminmc.torch.item.ItemStack namedStack(String name) {
        net.zaminmc.torch.item.ItemType stick = new net.zaminmc.torch.item.ItemType() {
            @Override
            public net.zaminmc.torch.util.Identifier identifier() {
                return net.zaminmc.torch.util.Identifier.parse("minecraft:stick");
            }
        };
        return new net.zaminmc.torch.item.ItemStack(stick, 1, 0, name);
    }

    private static void writeShortString(ByteBuf out, String value) {
        byte[] encoded = SlotNbt.ModifiedUtf8.encode(value);
        out.writeShort(encoded.length);
        out.writeBytes(encoded);
    }
}
