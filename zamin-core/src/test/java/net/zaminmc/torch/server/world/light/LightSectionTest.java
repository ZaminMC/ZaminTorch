package net.zaminmc.torch.server.world.light;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The light nibble storage (§475): two nibble channels per section, the
 * historical packing (low nibble = even index, {@code (y << 8) | (z << 4) | x})
 * that the 1.8 wire copies verbatim, and the divergence check that decides
 * whether a section must ride the chunk packet at all.
 */
class LightSectionTest {

    @Test
    void nibblesStoreIndependentChannelsForBothParities() {
        LightSection section = new LightSection();
        // Even index (low nibble) and odd index (high nibble) per channel.
        section.setBlockLight(0, 0, 0, 7);  // even index -> low nibble
        section.setBlockLight(1, 0, 0, 14); // odd index -> high nibble
        section.setSkyLight(0, 0, 0, 3);
        section.setSkyLight(1, 0, 0, 9);
        assertEquals(7, section.blockLight(0, 0, 0));
        assertEquals(14, section.blockLight(1, 0, 0));
        assertEquals(3, section.skyLight(0, 0, 0));
        assertEquals(9, section.skyLight(1, 0, 0));
        // Unrelated cells keep the implicit defaults.
        assertEquals(LightSection.DEFAULT_BLOCK, section.blockLight(2, 0, 0));
        assertEquals(LightSection.DEFAULT_SKY, section.skyLight(2, 0, 0));
    }

    @Test
    void indexPackingIsYThenZThenX() {
        LightSection section = new LightSection();
        section.setBlockLight(5, 6, 7, 12);
        assertEquals(12, section.blockLight(5, 6, 7));
        assertEquals(LightSection.DEFAULT_BLOCK, section.blockLight(6, 6, 7));
        assertEquals(LightSection.DEFAULT_BLOCK, section.blockLight(5, 7, 7));
        assertEquals(LightSection.DEFAULT_BLOCK, section.blockLight(5, 6, 8));
    }

    @Test
    void divergenceCheckDrivesTheWireMask() {
        LightSection section = new LightSection();
        assertFalse(section.hasNonDefaultLight(), "a fresh section matches the implicit defaults");
        section.setBlockLight(0, 0, 0, 14);
        assertTrue(section.hasNonDefaultLight(), "any emission diverges");
        section.setBlockLight(0, 0, 0, LightSection.DEFAULT_BLOCK);
        assertFalse(section.hasNonDefaultLight(), "resetting to the default converges");
        section.setSkyLight(3, 3, 3, 2);
        assertTrue(section.hasNonDefaultLight(), "a sky shadow diverges");
    }

    @Test
    void rawArraysCopyToTheWireVerbatim() {
        LightSection section = new LightSection();
        section.setBlockLight(0, 0, 0, 14); // low nibble of byte 0
        // Sky is born pre-filled with the default 15 (a materialized section
        // starts at the implicit defaults), so byte 0's low nibble holds 15
        // and the write only replaces the high nibble.
        section.setSkyLight(1, 0, 0, 9);    // high nibble of byte 0
        assertArrayEquals(new byte[] {0x0E, 0x00}, new byte[] {
                section.blockLightArray()[0], section.blockLightArray()[1]});
        assertArrayEquals(new byte[] {(byte) 0x9F}, new byte[] {section.skyLightArray()[0]});
    }

    @Test
    void boundsAndLevelsFailLoudly() {
        LightSection section = new LightSection();
        assertThrows(IllegalArgumentException.class,
                () -> section.setBlockLight(16, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> section.setBlockLight(0, 16, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> section.setBlockLight(0, 0, 16, 1));
        assertThrows(IllegalArgumentException.class,
                () -> section.setBlockLight(0, 0, 0, 16));
        assertThrows(IllegalArgumentException.class,
                () -> section.setBlockLight(0, 0, 0, -1));
        assertEquals(15, section.skyLight(15, 15, 15), "the far corner reads the default");
    }
}
