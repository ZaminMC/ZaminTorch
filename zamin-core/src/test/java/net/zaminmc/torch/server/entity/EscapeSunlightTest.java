package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skeleton's sunlight escape (Slice TF-7b — EscapeSunlightGoal 1:1
 * at band 3, the reference's own registration order before the wolf
 * flee): the on-fire body under the open sunny sky walks to the first
 * shaded cell the favor family rejects; the walk owns the body until
 * the path's own end, whatever the gates do meanwhile (shouldContinue
 * is the navigation's isDone alone).
 */
class EscapeSunlightTest {

    /**
     * The stub world: a solid floor below y=8, a sunny flag, and a
     * sky-access law by the x half — everything east of x=3 stands
     * under a roof (the shaded region the escape hunts), the west is
     * open sky. Full brightness everywhere (the monster family's favor
     * reads -0.5 at every cell, so only the sky-access arm picks).
     */
    private static final class SunWorld implements MobEntity.WorldQuery {
        boolean sunny = true;
        boolean allCovered;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean isSunny() {
            return sunny;
        }

        @Override
        public boolean hasSkyAccess(int x, int y, int z) {
            if (allCovered) {
                return false;
            }
            return x < 3; // the shaded half east of x=3
        }
    }

    private static MobEntity skeletonIn(SunWorld world, long seed) {
        return new MobEntity(500, MobType.SKELETON, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theBurningSkeletonUnderSunnySkyWalksToShade() {
        SunWorld world = new SunWorld();
        MobEntity skeleton = skeletonIn(world, 7L);
        // The burn (mobTick's daylight arm left the manager's own walk —
        // the bare body ignites here; the fire outlives the window).
        skeleton.ignite(2000);

        boolean escaped = false;
        for (int i = 0; i < 300 && !escaped; i++) {
            skeleton.tick(true);
            escaped = skeleton.escapeSunlightLive();
        }
        assertTrue(escaped, "the escape window opened (a shaded pick pathed)");

        // The walk owns the body toward the shaded half: the body's own x
        // grows past its spawn cell (the covered region sits at x>=3).
        double furthestEast = skeleton.position().x();
        for (int i = 0; i < 60; i++) {
            skeleton.tick(true);
            furthestEast = Math.max(furthestEast, skeleton.position().x());
        }
        assertTrue(furthestEast > 1.5,
                "the escape walked the body toward the shade (reached x "
                        + furthestEast + ")");
    }

    @Test
    void theNegativeGatesRefuseTheEscape() {
        // Not sunny: no escape (World.isSunny's own gate).
        SunWorld cloudy = new SunWorld();
        cloudy.sunny = false;
        MobEntity rained = skeletonIn(cloudy, 8L);
        rained.ignite(2000);
        for (int i = 0; i < 120; i++) {
            rained.tick(true);
            assertFalse(rained.escapeSunlightLive(),
                    "a cloudy sky never arms the escape");
        }

        // Not burning: no escape (the on-fire gate — a dry skeleton in
        // the noon sun keeps its own band). The dry fixture wears a
        // pumpkin: the burn lane's own helmet gate (getEquipment(4)'s
        // block) is what holds the body unlit now — a bare head would
        // catch the sun through the family's own walk and arm the
        // escape after it (the daylight-burn lane's law).
        SunWorld dry = new SunWorld();
        MobEntity comfortable = skeletonIn(dry, 9L);
        comfortable.setEquipmentAt(4, ItemStack.of(BuiltinItems.PUMPKIN, 1));
        for (int i = 0; i < 120; i++) {
            comfortable.tick(true);
            assertFalse(comfortable.burning(),
                    "the pumpkin holds the noon sun off (the helmet gate)");
            assertFalse(comfortable.escapeSunlightLive(),
                    "an unlit body never escapes the sun");
        }

        // The covered feet: no escape (hasSkyAccess at the body's own
        // cell — a skeleton already in the shade stands still).
        SunWorld roofed = new SunWorld();
        roofed.allCovered = true;
        MobEntity shaded = skeletonIn(roofed, 10L);
        shaded.ignite(2000);
        for (int i = 0; i < 120; i++) {
            shaded.tick(true);
            assertFalse(shaded.escapeSunlightLive(),
                    "a body already under cover never re-escapes");
        }
    }
}
