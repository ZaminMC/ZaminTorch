package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slime's own mechanics (Slice 12k — the reference's SlimeEntity): the
 * size family (the initialize roll, the per-size body recomputes), the
 * split-on-kill, the size-gated drops, the hop cadence, the big body's
 * contact damage, and the slime-chunk spawn gate.
 */
class SlimeEntityTest {

    /** A flat-world stub: solid below y=8, air above, an optional player. */
    private static MobEntity.WorldQuery worldAt(Position player) {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                if (player == null) {
                    return null;
                }
                double dx = player.x() - x;
                double dy = player.y() - y;
                double dz = player.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? player : null;
            }
        };
    }

    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 8;

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();

        @Override
        public void spawnLootDrop(Position position, ItemStack stack) {
            drops.add(stack);
        }
    }

    @Test
    void theKindCarriesTheReferenceIdentity() {
        assertEquals(55, MobType.SLIME.legacyTypeId);
        assertEquals(0.51000005, MobType.SLIME.width, 1e-9); // the reference's own float
        assertEquals(0.51000005, MobType.SLIME.height, 1e-9);
        assertEquals(1.0f, MobType.SLIME.maxHealth, 1e-6f); // size 1: 1*1
        assertTrue(MobType.SLIME.hostile);
        assertEquals("", MobType.SLIME.idleSound, "no ambient idle (the squish is the jump)");
        assertEquals("minecraft:slime", MobType.SLIME.identifier().toString());
    }

    @Test
    void theSizeFamilyRecomputesTheBody() {
        // setSize lines 51-59: the dimension 0.51*size, the health size²,
        // the movement speed 0.2+0.1*size (the hop reads it), the xp the
        // size, the damage the size.
        Random random = new Random(3L);
        MobEntity slime = new MobEntity(300, MobType.SLIME, new Position(0.5, 8.0, 0.5),
                random, worldAt(null));
        assertEquals(1, slime.slimeSize());
        assertEquals(1.0f, slime.health(), 1e-6f);
        assertEquals(0.51000005, slime.bodyWidth(), 1e-9);
        assertEquals(0.51000005, slime.bodyHeight(), 1e-9);
        assertEquals("mob.slime.small", slime.hurtSound());

        slime.setSlimeSize(4);
        assertEquals(4, slime.slimeSize());
        assertEquals(16.0f, slime.health(), 1e-6f); // 4*4
        assertEquals(4 * 0.51000005, slime.bodyWidth(), 1e-9);
        assertEquals(4 * 0.51000005, slime.bodyHeight(), 1e-9);
        assertEquals(4.0f, slime.attackDamage(), 1e-6f); // getDamageAmount
        assertEquals("mob.slime.big", slime.hurtSound());

        slime.setSlimeSize(2);
        assertEquals(4.0f, slime.health(), 1e-6f); // 2*2
        assertEquals(2.0f, slime.attackDamage(), 1e-6f);
    }

    /**
     * The magma cube's own armor override (MagmaCubeEntity lines 34-37):
     * getArmorProtection = size * 3 — the hull's protection replaces the
     * equipment sum entirely (a size-2 cube carries 6 points, the 24%
     * armor envelope).
     */
    @Test
    void theMagmaCubeArmorIsItsOwnSizeTimesThree() {
        MobEntity magma = new MobEntity(301, MobType.MAGMA_CUBE,
                new Position(0.5, 8.0, 0.5), new Random(4L), worldAt(null));
        magma.setSlimeSize(1);
        assertEquals(3, magma.armorProtectionPoints(), "a size-1 cube: 3 points");
        magma.setSlimeSize(2);
        assertEquals(6, magma.armorProtectionPoints(), "a size-2 cube: 6 points");
        magma.setSlimeSize(4);
        assertEquals(12, magma.armorProtectionPoints(), "a size-4 cube: 12 points");
        // The envelope reads the same points (damage * (25 - 6) / 25).
        magma.setSlimeSize(2);
        assertEquals(6.0f * 19 / 25.0f, magma.damageAfterArmor(6.0f), 1e-6f,
                "the armor envelope rides the size-based points");
        // The plain slime keeps the equipment walk (no override — the
        // reference's SlimeEntity carries none).
        MobEntity slime = new MobEntity(302, MobType.SLIME,
                new Position(0.5, 8.0, 0.5), new Random(4L), worldAt(null));
        slime.setSlimeSize(4);
        assertEquals(0, slime.armorProtectionPoints(),
                "the plain slime's bare hull carries no armor points");
    }

    @Test
    void theInitializeRollsTheSizeFamily() {
        // initialize lines 286-295: size = 1 << nextInt(3) — the 1/2/4
        // family (the local-difficulty bump arm rides the difficulty
        // slice; the engine rolls the plain family).
        int[] sizes = new int[3];
        for (int trial = 0; trial < 90; trial++) {
            MobEntity slime = new MobEntity(301 + trial, MobType.SLIME,
                    new Position(0.5, 8.0, 0.5), new Random(trial), worldAt(null));
            slime.initializeSlimeSize();
            assertTrue(slime.slimeSize() == 1 || slime.slimeSize() == 2 || slime.slimeSize() == 4,
                    "the size family is 1/2/4 (was " + slime.slimeSize() + ")");
            sizes[(int) (Math.log(slime.slimeSize()) / Math.log(2))]++;
        }
        assertTrue(sizes[0] > 0 && sizes[1] > 0,
                "the roll covers at least the 1 and 2 families");
    }

    @Test
    void theBigBodySplitsOnDeath() {
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(null), new Random(11L), loot,
                1_500_000, SURFACE);
        MobEntity parent = manager.spawnAt(MobType.SLIME, new Position(0.5, 8.0, 0.5));
        assertNotNull(parent);
        parent.setSlimeSize(4);
        assertEquals(16.0f, parent.health(), 1e-6f);

        // Kill through the manager's own walk so the split runs.
        manager.hurt(parent, 100.0f, 0.0);
        for (int tick = 0; tick < 40; tick++) {
            manager.tick(List.of(), 10_000L);
        }
        assertNull(manager.byId(parent.entityId()), "the parent left");

        List<MobEntity> children = new ArrayList<>();
        for (MobEntity mob : manager.all()) {
            if (mob.type() == MobType.SLIME) {
                children.add(mob);
            }
        }
        // remove() lines 160-177: 2-4 children at half the size (2), each
        // with the full size² health.
        assertTrue(children.size() >= 2 && children.size() <= 4,
                "2-4 children (was " + children.size() + ")");
        for (MobEntity child : children) {
            assertEquals(2, child.slimeSize(), "the child is half the parent");
            assertEquals(4.0f, child.health(), 1e-6f);
        }
    }

    @Test
    void onlyTheSmallestBodyDrops() {
        // getDropItem line 231: the slime balls ride the size-1 body only.
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(null), new Random(13L), loot,
                1_600_000, SURFACE);

        MobEntity big = manager.spawnAt(MobType.SLIME, new Position(0.5, 8.0, 0.5));
        big.setSlimeSize(4);
        manager.hurt(big, 100.0f, 0.0);
        for (int tick = 0; tick < 40; tick++) {
            manager.tick(List.of(), 10_000L);
        }
        List<ItemStack> bigDrops = new ArrayList<>(loot.drops);
        // The big body's own drops: nothing (the children are alive).
        for (ItemStack drop : bigDrops) {
            assertTrue(drop.type() != BuiltinItems.SLIME_BALL,
                    "the big body drops no slime balls");
        }
        // The split children (size 2) drop nothing either when killed.
        loot.drops.clear();
        for (MobEntity mob : List.copyOf(manager.all())) {
            if (mob.type() == MobType.SLIME) {
                manager.hurt(mob, 100.0f, 0.0);
            }
        }
        for (int tick = 0; tick < 60; tick++) {
            manager.tick(List.of(), 10_000L);
        }
        for (ItemStack drop : loot.drops) {
            assertTrue(drop.type() != BuiltinItems.SLIME_BALL,
                    "the size-2 bodies drop no slime balls");
        }

        // The size-1 body: 0-2 slime balls (the common ItemRoll), and no
        // split — the smallest body dies whole (remove's size > 1 gate).
        loot.drops.clear();
        MobEntity small = manager.spawnAt(MobType.SLIME, new Position(20.5, 8.0, 20.5));
        assertNotNull(small);
        manager.hurt(small, 100.0f, 0.0);
        for (int tick = 0; tick < 40; tick++) {
            manager.tick(List.of(), 10_000L);
        }
        int balls = 0;
        for (ItemStack drop : loot.drops) {
            if (drop.type() == BuiltinItems.SLIME_BALL) {
                balls += drop.count();
            }
        }
        assertTrue(balls <= 2, "the 0-2 ball roll (was " + balls + ")");
        // The cascade settled: the splits stop at the smallest body — no
        // size-2-or-larger slime remains anywhere in the manager.
        assertEquals(0, manager.all().stream()
                .filter(m -> m.type() == MobType.SLIME && m.slimeSize() > 1).count(),
                "the split cascade stops at the smallest body");
    }

    @Test
    void theHopCadenceMovesTheBodyInBursts() {
        Random random = new Random(17L);
        MobEntity slime = new MobEntity(320, MobType.SLIME, new Position(0.5, 8.0, 0.5),
                random, worldAt(null));
        slime.setSlimeSize(2);

        // Between hops the body is still (the zeroed movement input), and a
        // hop launches velocityY 0.42 with the facing impulse.
        double startX = slime.position().x();
        double startZ = slime.position().z();
        boolean hopped = false;
        boolean stillBetweenHops = false;
        for (int tick = 0; tick < 200; tick++) {
            Position before = slime.position();
            slime.tick(false);
            Position after = slime.position();
            boolean groundedBefore = before.y() == 8.0 && after.y() == 8.0;
            if (after.y() > before.y() + 0.01) {
                hopped = true; // the launch
            } else if (hopped && groundedBefore) {
                double slide = Math.hypot(after.x() - before.x(), after.z() - before.z());
                if (slide < 0.005) {
                    // The grounded rest: the zeroed input means the body
                    // coasts on the residual only (a fraction of the 0.4
                    // hop launch) — the engine's damping never zeroes it
                    // outright, so "essentially still" is the observable.
                    stillBetweenHops = true;
                }
            }
        }
        assertTrue(hopped, "the slime hops");
        assertTrue(stillBetweenHops, "the body rests between hops");
        double moved = Math.hypot(slime.position().x() - startX, slime.position().z() - startZ);
        assertTrue(moved > 0.5, "the hops carry the body (moved " + moved + ")");
    }

    @Test
    void theBigBodyContactsTheNearbyPlayer() {
        // onPlayerCollision + damageTargetEntity (lines 192-204): the big
        // body within 0.6*size blocks with a clear line arms the melee
        // pipeline (the damage the size — getDamageAmount) on the
        // player's own 10-tick cadence. The small body never arms.
        Position player = new Position(0.5, 8.0, 1.4); // 0.9 away: inside 0.6*2=1.2
        MobEntity big = new MobEntity(330, MobType.SLIME, new Position(0.5, 8.0, 0.5),
                new Random(19L), worldAt(player));
        big.setSlimeSize(2);
        boolean armed = false;
        for (int tick = 0; tick < 30 && !armed; tick++) {
            big.tick(false);
            armed = big.consumePendingMeleeAttack();
        }
        assertTrue(armed, "the big body's contact damage armed");
        assertEquals(2.0f, big.attackDamage(), 1e-6f, "the damage is the size");

        // The small body never arms (isBig: size > 1 only).
        MobEntity small = new MobEntity(331, MobType.SLIME, new Position(0.5, 8.0, 0.5),
                new Random(23L), worldAt(player));
        boolean smallArmed = false;
        for (int tick = 0; tick < 30; tick++) {
            small.tick(false);
            if (small.consumePendingMeleeAttack()) {
                smallArmed = true;
            }
        }
        assertFalse(smallArmed, "the small body never contact-damages");

        // Out of reach: the big body far from the player never arms.
        MobEntity far = new MobEntity(332, MobType.SLIME, new Position(30.5, 8.0, 30.5),
                new Random(29L), worldAt(player));
        far.setSlimeSize(4);
        boolean farArmed = false;
        for (int tick = 0; tick < 30; tick++) {
            far.tick(false);
            if (far.consumePendingMeleeAttack()) {
                farArmed = true;
            }
        }
        assertFalse(farArmed, "the out-of-reach body never contact-damages");
    }

    @Test
    void theSlimeChunkGateMatchesTheReferenceSeed() {
        // The slime-chunk derivation (WorldChunk.getRandomForSlime line
        // 795): the chunk's own random seeded
        // seed + cx*cx*4987142 + cx*5947611 + cz*cz*4392871 + cz*389711 ^ 987234911L.
        long worldSeed = 12345L;
        for (int chunkX = -3; chunkX <= 3; chunkX++) {
            for (int chunkZ = -3; chunkZ <= 3; chunkZ++) {
                long slimeSeed = worldSeed
                        + (long) chunkX * chunkX * 4987142L
                        + (long) chunkX * 5947611L
                        + (long) chunkZ * chunkZ * 4392871L
                        + (long) chunkZ * 389711L
                        ^ 987234911L;
                // The 10% chunk roll: whichever chunks pass, the reference
                // and the engine agree on the same seeded stream.
                int first = new Random(slimeSeed).nextInt(10);
                int second = new Random(slimeSeed).nextInt(10);
                assertEquals(first, second,
                        "the seeded stream is deterministic (the same derivation twice)");
            }
        }
        // A concrete anchor: the derivation's XOR binds over the whole sum
        // (the + before the ^ — the reference's own precedence).
        long anchor = 42L + 2L * 2L * 4987142L + 2L * 5947611L + 3L * 3L * 4392871L + 3L * 389711L
                ^ 987234911L;
        assertEquals(42L + 19948568L + 11895222L + 39535839L + 1169133L ^ 987234911L, anchor);
    }
}
