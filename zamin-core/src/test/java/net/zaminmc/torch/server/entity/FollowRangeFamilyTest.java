package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-kind hunt band (the reference's FOLLOW_RANGE attribute —
 * MobEntity line 90's 16.0 default with the per-kind overrides): the
 * engine's player-hunt scan reads the kind's own band instead of the
 * flat 16 — BlazeEntity line 44's 48, EndermanEntity line 74's 64,
 * WitherEntity line 484's 40, ZombieEntity line 84's 35 (the pigman's
 * super.initAttributes inheritance included), GhastEntity line 81's
 * 100, the shared default 16 for the rest.
 */
class FollowRangeFamilyTest {

    /** Records every range the player scan consults. */
    private static final class RangeWorld implements MobEntity.WorldQuery {
        final Set<Double> playerScanRanges = new HashSet<>();
        Position player;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            playerScanRanges.add(range);
            if (player == null) {
                return null;
            }
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            return dx * dx + dy * dy + dz * dz <= range * range ? player : null;
        }

        @Override
        public MobEntity nearestMobOfKind(double x, double y, double z, double range, MobType kind) {
            return null;
        }
    }

    private static Set<Double> scanRangesOf(MobType type) {
        RangeWorld world = new RangeWorld();
        MobEntity body = new MobEntity(880, type, new Position(0.5, 8.0, 0.5),
                new Random(7), world);
        body.tick(false);
        return world.playerScanRanges;
    }

    @Test
    void theHuntScansReadTheReferenceFollowRanges() {
        // BlazeEntity line 44: 48.
        assertTrue(scanRangesOf(MobType.BLAZE).contains(48.0),
                "the blaze's own 48-block band");
        // EndermanEntity line 74: 64 (the stare's own read beside it).
        assertTrue(scanRangesOf(MobType.ENDERMAN).contains(64.0),
                "the enderman's own 64-block band");
        // WitherEntity line 484: 40.
        assertTrue(scanRangesOf(MobType.WITHER).contains(40.0),
                "the wither's own 40-block band");
        // ZombieEntity line 84: 35 — the zombie family (the pigman's
        // super.initAttributes inheritance, ZombiePigmanEntity line 50).
        assertTrue(scanRangesOf(MobType.ZOMBIE).contains(35.0),
                "the zombie's own 35-block band");
        assertTrue(scanRangesOf(MobType.ZOMBIE_PIGMAN).contains(35.0),
                "the pigman inherits the zombie's 35");
        // GhastEntity line 81: 100.
        assertTrue(scanRangesOf(MobType.GHAST).contains(100.0),
                "the ghast's own 100-block band");
        // The shared default (MobEntity line 90): 16 — the skeleton
        // carries no override.
        assertTrue(scanRangesOf(MobType.SKELETON).contains(16.0),
                "the skeleton keeps the shared 16");
    }
}
