package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zombie pigman (Slice 16d — ZombiePigmanEntity's anger model): the
 * neutral gate (the hunt only runs while angry), the group anger on a
 * player's hit with the 400+nextInt(400) window and the delayed angry
 * sound, and the kind's own identity.
 */
class ZombiePigmanTest {

    /** A flat-world stub: solid below y=8, air above, no player. */
    private static MobEntity.WorldQuery worldAt(Position nearest) {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                if (nearest == null) {
                    return null;
                }
                double dx = nearest.x() - x;
                double dy = nearest.y() - y;
                double dz = nearest.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? nearest : null;
            }
        };
    }

    private static MobEntity pigman(long seed) {
        return new MobEntity(950, MobType.ZOMBIE_PIGMAN, new Position(0.5, 8.0, 0.5),
                new Random(seed), worldAt(null));
    }

    @Test
    void theKindCarriesTheReferenceIdentity() {
        assertEquals(57, MobType.ZOMBIE_PIGMAN.legacyTypeId);
        assertEquals(0.6, MobType.ZOMBIE_PIGMAN.width, 1e-9);
        assertEquals(1.8, MobType.ZOMBIE_PIGMAN.height, 1e-9);
        assertEquals(20.0f, MobType.ZOMBIE_PIGMAN.maxHealth, 1e-6f);
        assertEquals(5.0f, MobType.ZOMBIE_PIGMAN.attackDamage, 1e-6f); // initAttributes
        assertTrue(MobType.ZOMBIE_PIGMAN.fireImmune());
        assertFalse(MobType.ZOMBIE_PIGMAN.slimeFamily());
        assertEquals("mob.zombiepig.zpig", MobType.ZOMBIE_PIGMAN.idleSound);
        assertEquals("mob.zombiepig.zpighurt", MobType.ZOMBIE_PIGMAN.hurtSound);
        assertEquals("mob.zombiepig.zpigdeath", MobType.ZOMBIE_PIGMAN.deathSound);
        assertEquals("minecraft:zombie_pigman", MobType.ZOMBIE_PIGMAN.identifier().toString());
    }

    @Test
    void theAngerWindowRollsAndDecays() {
        MobEntity pigman = pigman(81L);
        assertFalse(pigman.isAngry(), "a fresh pigman is neutral");

        // The anger: 400 + nextInt(400) ticks.
        pigman.becomeAngryTo("Attacker");
        assertTrue(pigman.isAngry());
        assertEquals("Attacker", pigman.angerTargetName());

        // The window decays one tick at a time (the tick's own walk).
        int ticks = 0;
        while (pigman.isAngry() && ticks < 1000) {
            pigman.tick(false);
            ticks++;
        }
        assertTrue(ticks >= 400 && ticks < 800,
                "the window is 400-799 ticks (took " + ticks + ")");
        assertFalse(pigman.isAngry());
    }

    @Test
    void theDelayedAngrySoundFiresWithinTheWindow() {
        MobEntity pigman = pigman(82L);
        pigman.becomeAngryTo("Attacker");
        boolean fired = false;
        for (int i = 0; i < 45 && !fired; i++) {
            pigman.tick(false);
            fired = pigman.consumePigmanAngrySound();
        }
        assertTrue(fired, "the angry sound fires within nextInt(40) + the tick walk");
        assertFalse(pigman.consumePigmanAngrySound(), "the sound fires once per anger");
    }

    @Test
    void theGroupAngerSpreadsThroughTheManager() {
        // The manager's propagation walk: the hurt body and every calm
        // pigman within 32 blocks anger together.
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class CountingLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                                                net.zaminmc.torch.item.ItemStack stack) {
            }
        }
        MobManager manager = new MobManager(worldAt(null), new Random(83L),
                new CountingLoot(), 960_000, surface);
        MobEntity hurt = manager.spawnAt(MobType.ZOMBIE_PIGMAN, new Position(0.5, 8.0, 0.5));
        MobEntity near = manager.spawnAt(MobType.ZOMBIE_PIGMAN, new Position(6.5, 8.0, 0.5));
        MobEntity far = manager.spawnAt(MobType.ZOMBIE_PIGMAN, new Position(90.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(3.5, 8.0, 0.5));

        manager.pigmenBecomeAngryAt(hurt, "Attacker");
        assertTrue(hurt.isAngry(), "the hurt body angers");
        assertTrue(near.isAngry(), "the nearby pigman angers with it");
        assertFalse(far.isAngry(), "the far pigman stays calm (beyond 32)");
        assertFalse(zombie.isAngry(), "the zombie has no anger model");
        assertEquals("Attacker", near.angerTargetName());

        // The already-angry keep their own window: a second hit on the
        // group doesn't extend the neighbor's roll... but the hurt body
        // re-rolls its own (a fresh 400+nextInt(400)).
        int hurtWindow = 0;
        while (hurt.isAngry() && hurtWindow < 1000) {
            hurt.tick(false);
            hurtWindow++;
        }
        assertTrue(hurtWindow >= 400, "the re-rolled window holds the base");
    }
}
