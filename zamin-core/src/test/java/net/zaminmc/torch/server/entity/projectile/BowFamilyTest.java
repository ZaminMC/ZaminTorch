package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bow enchantment family at the manager level (reference/1.8.8
 * ArrowEntity lines 229-279 + BowItem lines 21-52): the Power bonus rides
 * the damage multiplier, the full-draw crit roll widens the band, the Punch
 * impulse rides the landed verdict along the arrow's travel, the burning
 * arrow ignites before the damage, and the launch spread exists.
 */
class BowFamilyTest {

    private static final FxManager FX = new FxManager();

    /** The minimal world query MobEntity demands (solid ground + no players). */
    private static MobEntity.WorldQuery solidWorld() {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return y <= 0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    /** A recording sink whose mob hits always land. */
    private static final class LandedCombat extends ProjectileManagerTest.RecordingCombat {
        final List<String> order = new ArrayList<>();

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            order.add("hit");
            return super.mobHit(mob, damage, kbYaw, shooterId);
        }

        @Override
        public void mobIgniteFromProjectile(MobEntity mob) {
            order.add("ignite");
            mob.ignite(100);
        }
    }

    @Test
    void powerBonusRidesTheDamageMultiplier() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new ProjectileManagerTest.RiggedHits(),
                new ProjectileManagerTest.RecordingCombat(), FX, new Random(11), 1_000);
        // The Power V arm (BowItem line 42): damage += 5 * 0.5 + 0.5 = 3.0,
        // so the multiplier is 5.0 — the full-draw band ceil(3 * 5) = 15
        // (the launch noise swings the speed, the band stays 14..16).
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 12, 0), 0, 0, 3.0);
        arrow.addBonusDamage(5 * 0.5 + 0.5);
        ProjectileEntity plain = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 12, 0), 0, 0, 3.0);

        double powerBand = Math.ceil(arrow.launchSpeed() * (2.0 + arrow.bonusDamage()));
        double plainBand = Math.ceil(plain.launchSpeed() * 2.0);
        assertTrue(powerBand >= 14.0 && powerBand <= 16.0,
                "Power V full draw lands 14..16, saw " + powerBand);
        assertTrue(plainBand >= 5.0 && plainBand <= 7.0,
                "the plain full draw lands 5..7, saw " + plainBand);
        assertTrue(powerBand > plainBand * 2.0,
                "the Power V hit more than doubles the plain draw");
    }

    @Test
    void fullDrawCritRollWidensTheDamageBand() {
        ProjectileManagerTest.RiggedHits hits = new ProjectileManagerTest.RiggedHits();
        LandedCombat combat = new LandedCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, FX, new Random(12), 2_000);
        // The crit roll (ArrowEntity lines 233-235): l += nextInt(l / 2 + 2)
        // — the base 6..7 widens to 6..11. Twenty rolls must reach past the
        // plain cap at least once (the all-zero miss odds are ~4e-15).
        int max = 0;
        for (int i = 0; i < 20; i++) {
            MobEntity zombie = new MobEntity(9_100 + i, MobType.ZOMBIE,
                    new Position(0, 10, 0), new Random(100 + i), solidWorld());
            hits.mob = zombie;
            ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                    new Position(0, 12, 0), 0, 0, 3.0);
            arrow.setCritical(true);
            manager.tick();
        }
        int maxDamage = combat.damages.stream().mapToInt(Float::intValue).max().orElse(0);
        assertTrue(maxDamage > 7, "the crit roll pushed past the plain cap, saw " + maxDamage);
        assertTrue(maxDamage <= 11, "the crit roll stays inside the vanilla band, saw " + maxDamage);
    }

    @Test
    void punchRidesTheLandedVerdictAlongTheTravel() {
        ProjectileManagerTest.RiggedHits hits = new ProjectileManagerTest.RiggedHits();
        LandedCombat combat = new LandedCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, FX, new Random(13), 3_000);
        MobEntity zombie = new MobEntity(9_101, MobType.ZOMBIE,
                new Position(0, 10, 0), new Random(14), solidWorld());
        hits.mob = zombie;
        // Straight -x travel: the Punch II impulse (ArrowEntity lines 255-260)
        // rides vx * 2 * 0.6 / |vxz| plus the 0.1 rise.
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 12, 0), 0, 0, 3.0);
        arrow.setPunchLevel(2);
        manager.tick();
        assertEquals(List.of("hit"), combat.order, "the hit landed");
        double horizontal = Math.sqrt(arrow.velocityX() * arrow.velocityX()
                + arrow.velocityZ() * arrow.velocityZ());
        assertEquals(arrow.velocityX() * 2 * 0.6 / horizontal, zombie.velocityX(), 1.0E-9,
                "the punch impulse rides the arrow's horizontal travel");
        assertEquals(0.1, zombie.velocityY(), 1.0E-9, "the punch rise");
        assertEquals(arrow.velocityZ() * 2 * 0.6 / horizontal, zombie.velocityZ(), 1.0E-9,
                "the punch impulse carries no sideways component");
    }

    @Test
    void punchWithoutALandedVerdictStaysQuiet() {
        ProjectileManagerTest.RiggedHits hits = new ProjectileManagerTest.RiggedHits();
        ProjectileManager.CombatSink refused = new ProjectileManager.CombatSink() {
            @Override
            public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
                return false; // the i-frame absorb
            }

            @Override
            public void playerHit(PlayerSession player, float damage, double kbYaw) {
            }

            @Override
            public void chickenHatch(Position position) {
            }
        };
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, refused, FX, new Random(15), 4_000);
        MobEntity zombie = new MobEntity(9_102, MobType.ZOMBIE,
                new Position(0, 10, 0), new Random(16), solidWorld());
        hits.mob = zombie;
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 12, 0), 0, 0, 3.0);
        arrow.setPunchLevel(2);
        manager.tick();
        assertEquals(0.0, zombie.velocityX(), 1.0E-9,
                "the refused hit carries no punch impulse");
        assertEquals(0.0, zombie.velocityY(), 1.0E-9, "the refused hit carries no rise");
    }

    @Test
    void burningArrowIgnitesBeforeTheDamage() {
        ProjectileManagerTest.RiggedHits hits = new ProjectileManagerTest.RiggedHits();
        LandedCombat combat = new LandedCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, FX, new Random(17), 5_000);
        MobEntity zombie = new MobEntity(9_103, MobType.ZOMBIE,
                new Position(0, 10, 0), new Random(18), solidWorld());
        hits.mob = zombie;
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 12, 0), 0, 0, 3.0);
        arrow.setOnFireFor(2_000);
        manager.tick();
        assertEquals(List.of("ignite", "hit"), combat.order,
                "the ignite precedes the damage (the reference's setOnFireFor(5) arm)");
        assertTrue(zombie.burning(), "the target caught fire");
    }

    @Test
    void launchSpreadExists() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new ProjectileManagerTest.RiggedHits(),
                new ProjectileManagerTest.RecordingCombat(), FX, new Random(19), 6_000);
        // The reference dispense (ThrownEntity.dispense lines 91-93): the
        // unit look vector picks up per-axis gaussian noise — two identical
        // launches never fly identically.
        ProjectileEntity a = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 12, 0), 0, 0, 3.0);
        ProjectileEntity b = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 12, 0), 0, 0, 3.0);
        assertNotEquals(a.velocityX(), b.velocityX(),
                "the launch spread separates identical shots");
    }
}
