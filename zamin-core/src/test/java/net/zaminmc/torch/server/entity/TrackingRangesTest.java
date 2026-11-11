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

    // ------------------------------------------------------------------
    // WAVE TR-2 — the family roll-out's own table seats
    // ------------------------------------------------------------------

    /**
     * WAVE TR-2 — THE OTHER FAMILIES' TABLE (EntityMap.onEntityAdded's
     * non-mob rows, unit-pinned 1:1): the item 64, the projectile family
     * 64 (one band — the arrow's 64/20 and the shards' 64/10 share the
     * range), the bobber 64, the ender eye 64, the fireworks 64, the
     * minecart/boat 80, the primed TNT 160, the falling block 160, the
     * decoration family 160 (the frame, the painting, the leash knot),
     * the armor stand 160, the experience orb 160, the ender crystal 256
     * (the table's lone 256 row).
     */
    @Test
    void theFamilyTableMatchesTheReference() {
        assertEquals(64, TrackingRanges.TrackedKind.ITEM.trackingRange(),
                "ItemEntity's row (lines 92-93)");
        assertEquals(64, TrackingRanges.TrackedKind.PROJECTILE.trackingRange(),
                "the projectile family's one band (lines 72-89)");
        assertEquals(64, TrackingRanges.TrackedKind.BOBBER.trackingRange(),
                "the fishing bobber's row (lines 70-71)");
        assertEquals(64, TrackingRanges.TrackedKind.ENDER_EYE.trackingRange(),
                "the eye of ender's row (lines 82-83)");
        assertEquals(64, TrackingRanges.TrackedKind.FIREWORKS.trackingRange(),
                "the firework rocket's row (lines 90-91)");
        assertEquals(80, TrackingRanges.TrackedKind.VEHICLE.trackingRange(),
                "the minecart/boat row (lines 94-97)");
        assertEquals(160, TrackingRanges.TrackedKind.TNT.trackingRange(),
                "the primed TNT's row (lines 108-109)");
        assertEquals(160, TrackingRanges.TrackedKind.FALLING.trackingRange(),
                "the falling block's row (lines 110-111)");
        assertEquals(160, TrackingRanges.TrackedKind.DECORATION.trackingRange(),
                "the decoration family's row (lines 112-113)");
        assertEquals(160, TrackingRanges.TrackedKind.ARMOR_STAND.trackingRange(),
                "the armor stand's row (lines 114-115)");
        assertEquals(160, TrackingRanges.TrackedKind.XP_ORB.trackingRange(),
                "the experience orb's row (lines 116-117)");
        assertEquals(256, TrackingRanges.TrackedKind.ENDER_CRYSTAL.trackingRange(),
                "the ender crystal's lone 256 row (lines 118-119)");
    }

    /**
     * WAVE TR-2 — THE FAMILIES' REGISTRATION CLAMP (the same
     * EntityMap.startTracking law the mob band rides, lines 128-130):
     * every family's row clamps to the view distance in BLOCKS — the
     * item's 64, the vehicle's 80, the TNT's 160 and the crystal's 256
     * ALL answer 48 on the view-4 rig; a wide view answers the rows
     * whole (view 10 = 144: the crystal's 256 clamps, the vehicle's 80
     * answers 80; view 17 = 256: the crystal answers whole).
     */
    @Test
    void everyFamilyRidesTheSameRegistrationClamp() {
        assertEquals(48, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.ITEM, 4), "the item's 64 -> 48");
        assertEquals(48, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.VEHICLE, 4), "the vehicle's 80 -> 48");
        assertEquals(48, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.TNT, 4), "the TNT's 160 -> 48");
        assertEquals(48, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.ENDER_CRYSTAL, 4), "the crystal's 256 -> 48");
        assertEquals(64, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.ITEM, 10), "view 10 = 144: the item's 64 answers whole");
        assertEquals(80, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.VEHICLE, 10), "the vehicle's 80 answers whole under 144");
        assertEquals(144, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.TNT, 10), "the TNT's 160 clamps to 144");
        assertEquals(256, TrackingRanges.effectiveRange(
                TrackingRanges.TrackedKind.ENDER_CRYSTAL, 17), "view 17 = 256: the crystal answers whole");
    }

    /**
     * WAVE TR-3 — THE PLAYER BAND'S OWN ROW (EntityMap lines 61-62: the
     * table's widest, {@code startTracking(entity, 512, 2)}): the 512
     * rides the same registration clamp — 48 on the view-4 rig, 144 on
     * view 10, and whole only at view 33+ (512 + 16 = the span).
     */
    @Test
    void thePlayerBandRidesTheSameClamp() {
        assertEquals(512, TrackingRanges.PLAYER_RANGE,
                "the player's own row (EntityMap lines 61-62)");
        assertEquals(48, TrackingRanges.effectivePlayerRange(4),
                "the 512 clamps to the view-4 span");
        assertEquals(144, TrackingRanges.effectivePlayerRange(10),
                "the 512 clamps to the view-10 span");
        assertEquals(512, TrackingRanges.effectivePlayerRange(33),
                "the 512 answers whole on a wide-enough view (33 * 16 - 16 = 512)");
    }
}
