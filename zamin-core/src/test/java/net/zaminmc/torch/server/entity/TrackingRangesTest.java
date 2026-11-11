package net.zaminmc.torch.server.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE TR — THE PER-ENTITY TRACKING-RANGE TABLE'S OWN LAWS (the
 * reference's EntityMap/TrackedEntity consult, unit-pinned 1:1): the
 * per-type table, the registration's view-distance clamp, Paper 0216's
 * per-entity override consulted first, and broadcastTo's ±range box.
 */
class TrackingRangesTest {

    /**
     * THE TABLE (EntityMap.onEntityAdded's rows over the engine's mob
     * kinds): the squid's own 64, the wither's and the bat's 80, the ender
     * dragon's 160, and every other living mob's 80 (SpawnableEntity's
     * row).
     */
    @Test
    void thePerTypeTableMatchesTheReference() {
        assertEquals(64, TrackingRanges.rangeOf(MobType.SQUID),
                "the squid's own row (EntityMap lines 98-99)");
        assertEquals(80, TrackingRanges.rangeOf(MobType.WITHER),
                "the wither's own row (lines 100-101)");
        assertEquals(80, TrackingRanges.rangeOf(MobType.BAT),
                "the bat's own row (lines 102-103)");
        assertEquals(160, TrackingRanges.rangeOf(MobType.ENDER_DRAGON),
                "the ender dragon's own row (lines 104-105)");
        assertEquals(80, TrackingRanges.rangeOf(MobType.ZOMBIE),
                "SpawnableEntity's row (lines 106-107) — every other living mob");
        assertEquals(80, TrackingRanges.rangeOf(MobType.GHAST));
        assertEquals(80, TrackingRanges.rangeOf(MobType.COW));
    }

    /**
     * THE VIEW-DISTANCE CONVERSION (ChunkMap.getViewDistance line 262):
     * the chunk radius's own block span — view 4 answers 48, view 10
     * answers 144, view 1 answers 0.
     */
    @Test
    void theViewDistanceConvertsToBlocks() {
        assertEquals(48, TrackingRanges.viewDistanceBlocks(4));
        assertEquals(144, TrackingRanges.viewDistanceBlocks(10));
        assertEquals(0, TrackingRanges.viewDistanceBlocks(1));
        assertEquals(304, TrackingRanges.viewDistanceBlocks(20));
    }

    /**
     * THE REGISTRATION CLAMP (EntityMap.startTracking lines 128-130): the
     * table's range never exceeds the view distance in blocks — 80 on a
     * view-4 server answers 48, the dragon's 160 answers 48 too, and on a
     * wide view the table's own row answers whole.
     */
    @Test
    void theRegistrationClampsTheTableToTheView() {
        assertEquals(48, TrackingRanges.effectiveRange(MobType.ZOMBIE, 0, 4),
                "80 clamps to the view-4 span");
        assertEquals(48, TrackingRanges.effectiveRange(MobType.ENDER_DRAGON, 0, 4),
                "160 clamps to the view-4 span too");
        assertEquals(64, TrackingRanges.effectiveRange(MobType.SQUID, 0, 10),
                "the squid's 64 answers whole on a view-10 server (144 span)");
        assertEquals(80, TrackingRanges.effectiveRange(MobType.ZOMBIE, 0, 20),
                "the mob row answers whole on a view-20 server");
    }

    /**
     * PAPER 0216'S OVERRIDE (the per-entity custom consulted FIRST): a
     * positive custom answers before the table's category — a SHRINK holds
     * whole (16 answers 16), an EXTENSION rides the view clamp (512 on a
     * view-4 server answers 48, the registration's own law), and 0/absent
     * falls to the table.
     */
    @Test
    void theCustomOverrideIsConsultedFirstThroughTheClamp() {
        assertEquals(16, TrackingRanges.effectiveRange(MobType.ZOMBIE, 16, 4),
                "a custom shrink answers whole (16 < the 48 span)");
        assertEquals(30, TrackingRanges.effectiveRange(MobType.ENDER_DRAGON, 30, 10),
                "a custom below the table's row answers over the row (0216's consult-first)");
        assertEquals(48, TrackingRanges.effectiveRange(MobType.ZOMBIE, 512, 4),
                "a custom extension rides the view clamp (the engine's stateless-consult note)");
        assertEquals(304, TrackingRanges.effectiveRange(MobType.ZOMBIE, 512, 20),
                "a custom above even a wide view clamps to the view's own span (view 20 = 304)");
        assertEquals(512, TrackingRanges.effectiveRange(MobType.ZOMBIE, 512, 33),
                "the same custom answers whole on a wide-enough view (view 33 = 512)");
        assertEquals(48, TrackingRanges.effectiveRange(MobType.ZOMBIE, 0, 4),
                "unset falls to the table's row through the clamp");
    }

    /**
     * THE BOX LAW (TrackedEntity.broadcastTo lines 379-387): the ±range
     * box on the horizontal plane alone — the boundary row ITSELF is
     * inside (inclusive), y is NEVER consulted, and the box is a SQUARE:
     * a diagonal (48, 48) sits inside while an axis (49, 0) does not.
     */
    @Test
    void theBoxIsInclusiveHorizontalAndSquare() {
        assertTrue(TrackingRanges.inRange(48, 0, 0, 48, 0),
                "the boundary row itself is inside (dx exactly the range)");
        assertTrue(TrackingRanges.inRange(48, 0, 0, -48, 0),
                "the negative boundary row too");
        assertFalse(TrackingRanges.inRange(48, 0, 0, 49, 0),
                "one block past the range is outside");
        assertFalse(TrackingRanges.inRange(48, 0, 0, 0, 49),
                "the z axis carries the same law");
        assertTrue(TrackingRanges.inRange(48, 0, 0, 34, 34),
                "the SQUARE: a diagonal pair within the box is inside (a circle would cut it)");
        // (y is never consulted by construction — broadcastTo reads x and z
        // alone, and the consult's own signature carries no y)
        assertFalse(TrackingRanges.inRange(16, 0, 0, 16.5, 0),
                "a half-block past a custom shrink is outside");
    }
}
