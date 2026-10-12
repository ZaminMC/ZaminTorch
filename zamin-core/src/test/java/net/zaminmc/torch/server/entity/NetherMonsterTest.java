package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nether's monster pair (Slice 16c — MagmaCubeEntity + GhastEntity):
 * the magma cube's slime-family shares (the size system, the split, the
 * contact damage) with its own overrides (the 4x cadence, the +2 damage,
 * the size-scaled jump, the fire immunity, the magma-cream drop), and the
 * ghast's flight + the charged fireball cycle.
 */
class NetherMonsterTest {

    /** A flat-world stub: solid below y=8, air above, no player. */
    private static MobEntity.WorldQuery worldAt() {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static MobEntity magma(long seed) {
        return new MobEntity(900, MobType.MAGMA_CUBE, new Position(0.5, 8.0, 0.5),
                new Random(seed), worldAt());
    }

    private static MobEntity ghast(long seed) {
        return new MobEntity(910, MobType.GHAST, new Position(0.5, 40.0, 0.5),
                new Random(seed), worldAt());
    }

    @Test
    void theMagmaCubeCarriesTheFamilyAndItsOwnOverrides() {
        assertEquals(62, MobType.MAGMA_CUBE.legacyTypeId);
        assertTrue(MobType.MAGMA_CUBE.slimeFamily());
        assertTrue(MobType.MAGMA_CUBE.fireImmune());
        assertTrue(MobType.SLIME.slimeFamily());
        assertFalse(MobType.ZOMBIE.slimeFamily());
        assertFalse(MobType.BLAZE.fireImmune() == false); // the blaze carries it too
        assertTrue(MobType.BLAZE.fireImmune());

        MobEntity cube = magma(71L);
        assertEquals(1, cube.slimeSize());
        cube.setSlimeSize(2);
        assertEquals(2, cube.slimeSize());
        assertEquals(4.0f, cube.health(), 1e-6f, "the size-squared health recompute");
        assertEquals(4.0f, cube.attackDamage(), 1e-6f, "the size + 2 damage (size 2)");
        cube.setSlimeSize(1);
        assertEquals(3.0f, cube.attackDamage(), 1e-6f, "the size + 2 damage (size 1)");
        cube.setSlimeSize(2);
        assertEquals("mob.magmacube.big", cube.hurtSound());
        cube.setSlimeSize(1);
        assertEquals("mob.magmacube.small", cube.hurtSound());
    }

    @Test
    void theGhastCarriesTheFlightIdentity() {
        assertEquals(56, MobType.GHAST.legacyTypeId);
        assertEquals(4.0, MobType.GHAST.width, 1e-9);
        assertEquals(4.0, MobType.GHAST.height, 1e-9);
        assertEquals(10.0f, MobType.GHAST.maxHealth, 1e-6f);
        assertTrue(MobType.GHAST.fireImmune());
        assertEquals(0.0f, MobType.GHAST.attackDamage, 1e-6f, "no melee — the fireball is the attack");
        assertEquals("mob.ghast.moan", MobType.GHAST.idleSound);
        assertEquals("mob.ghast.scream", MobType.GHAST.hurtSound);
        assertEquals("mob.ghast.death", MobType.GHAST.deathSound);

        MobEntity flyer = ghast(72L);
        // The flight model never accumulates gravity: the vertical speed
        // stays inside the burst scale's own steady band (0.1 per burst
        // against the 0.91 drag tops out near 1.1) — a gravity body under
        // the same drag settles toward 2.0.
        double maxAbsVy = 0;
        for (int i = 0; i < 300; i++) {
            flyer.tick(true);
            maxAbsVy = Math.max(maxAbsVy, Math.abs(flyer.velocityY()));
        }
        assertTrue(maxAbsVy < 1.5, "no gravity accumulation (peak |vy| " + maxAbsVy + ")");
        assertFalse(flyer.onGround());
    }

    @Test
    void theGhastChargesAndLooses() {
        // A scripted world with a target the ghast can see.
        class TargetedWorld implements MobEntity.WorldQuery {
            Position target = new Position(30.5, 40.0, 0.5);

            @Override public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return target;
            }

            @Override public boolean clearLine(Position from, Position to) {
                return true; // the open sky
            }
        }
        TargetedWorld world = new TargetedWorld();
        MobEntity flyer = new MobEntity(911, MobType.GHAST, new Position(0.5, 40.0, 0.5),
                new Random(73L), world);

        assertFalse(flyer.ghastCharging());
        Position fired = null;
        for (int i = 0; i < 30 && fired == null; i++) {
            flyer.tick(true);
            if (flyer.ghastCharging()) {
                assertTrue(i >= 10, "the flag rises only past charge 10");
            }
            fired = flyer.consumeGhastFireball();
        }
        assertNotNull(fired, "the 20-tick charge loosed the fireball");
        assertEquals(world.target, fired);
        assertFalse(flyer.ghastCharging(), "the -40 cooldown clears the flag");

        // The cooldown: no new launch until the cycle re-arms (60 ticks).
        for (int i = 0; i < 40; i++) {
            flyer.tick(true);
            assertNull(flyer.consumeGhastFireball(), "the cooldown holds");
        }
    }

    @Test
    void theFireballKindCarriesTheWireIdentity() {
        assertEquals(64, net.zaminmc.torch.server.entity.projectile.ProjectileEntity
                .Kind.LARGE_FIREBALL.objectType);
        assertEquals(63, net.zaminmc.torch.server.entity.projectile.ProjectileEntity
                .Kind.SMALL_FIREBALL.objectType);
    }
}
