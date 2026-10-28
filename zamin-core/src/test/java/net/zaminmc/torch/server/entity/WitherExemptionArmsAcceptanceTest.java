package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wither's conditional exemption arms (the README's pending row —
 * WitherEntity's own overrides, each read from the reference before the
 * fix): the armor-protection hull and the never-despawn law are the row's
 * two REAL gaps, fixed here; the row's other arms retire with evidence —
 * the fall exemption and the summon shape were already landed, the
 * light-level override has no server surface (the reference's callers are
 * client particles and renderers alone), and the cobweb and no-riding
 * exemptions are satisfied by construction (the engine's mob model has no
 * cobweb slow and its mobs never ride — the jockey's mount carries the
 * steps). The potion immunity (WitherEntity.addStatusEffect's empty body)
 * landed with Slice 59's refusesEffects gate.
 *
 * <p>The two fixes, by their reference rows: the hull
 * (WitherEntity.getArmorProtection lines 338-340 — a FLAT FOUR, the
 * equipment walk never runs) and the never-despawn law
 * (WitherEntity.checkDespawn lines 462-464 + EnderDragonEntity
 * .checkDespawn lines 587-589 — the override REPLACES the whole walk, so
 * neither the 128-block instant arm nor the 600-tick idle window ever
 * fires for a boss, whatever the distance).</p>
 */
class WitherExemptionArmsAcceptanceTest {

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
                return dx * dx + dy * dy + dz * dz <= range * range ? nearest : nearest;
            }
        };
    }

    private static MobEntity wither(long seed) {
        return new MobEntity(900, MobType.WITHER, new Position(0.5, 8.0, 0.5),
                new Random(seed), worldAt(null));
    }

    @Test
    void theArmorHullIsTheFlatFour() {
        // WitherEntity.getArmorProtection lines 338-340: the bare boss
        // already carries FOUR points — the pre-fix walk answered the
        // equipment sum (zero for a bare wither), so every wound the
        // engine ever landed overshot the reference by the hull's four.
        MobEntity boss = wither(1L);
        assertEquals(4, boss.armorProtectionPoints(), "the flat hull");
        assertEquals(300.0f, boss.type().maxHealth, 1e-6f);
        // The envelope the hull feeds (LivingEntity.getDamageAfterArmor):
        // 10 * (25 - 4) / 25 = 8.4 — the reference's exact float shape.
        assertEquals(8.4f, boss.damageAfterArmor(10.0f), 1e-6f);
    }

    @Test
    void theEquipmentNeverOverridesTheHull() {
        // The override REPLACES the walk whole: a diamond chestplate
        // (eight points on any other body) never raises the boss's four,
        // and a helmet held in the hand (the reference's own held-helmet
        // quirk) contributes nothing either.
        MobEntity armored = wither(2L);
        armored.setEquipment(4, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1));
        armored.setEquipment(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        assertEquals(4, armored.armorProtectionPoints(),
                "the hull stays the flat four whatever the equipment");
        // The contrast pair: an ordinary body answers the equipment walk.
        MobEntity zombie = new MobEntity(901, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), new Random(2L), worldAt(null));
        assertEquals(0, zombie.armorProtectionPoints(), "the bare zombie carries nothing");
        zombie.setEquipment(4, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1));
        assertEquals(8, zombie.armorProtectionPoints(), "the chestplate's eight");
    }

    @Test
    void theBossFamilyNeverDespawns() {
        // WitherEntity.checkDespawn lines 462-464 + EnderDragonEntity
        // lines 587-589: the override replaces the WHOLE walk — no arm
        // fires at any distance, for any window.
        assertFalse(wither(3L).canDespawn(), "the wither never despawns");
        MobEntity dragon = new MobEntity(902, MobType.ENDER_DRAGON,
                new Position(0.5, 8.0, 0.5), new Random(3L), worldAt(null));
        assertFalse(dragon.canDespawn(), "the dragon never despawns");
        // The walk's own bands never fire for a boss: the 128-block
        // instant arm (squared 16384+) and the 600-tick idle roll alike.
        Random dice = new Random(4L);
        for (int i = 0; i < 64; i++) {
            assertFalse(net.zaminmc.torch.server.spawn.NaturalSpawnSystem.shouldDespawn(
                    false, wither(5L).canDespawn(), 20000.0, 1000, dice),
                    "the 128-block instant arm never fires");
            assertFalse(net.zaminmc.torch.server.spawn.NaturalSpawnSystem.shouldDespawn(
                    false, wither(6L).canDespawn(), 2000.0, 1000, dice),
                    "the idle window never fires");
        }
        // The contrast: an ordinary monster still leaves (the walk's own
        // law intact — the fix rides the family gate alone).
        MobEntity zombie = new MobEntity(903, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), new Random(7L), worldAt(null));
        assertTrue(zombie.canDespawn(), "the ordinary monster still despawns");
        assertTrue(net.zaminmc.torch.server.spawn.NaturalSpawnSystem.shouldDespawn(
                false, zombie.canDespawn(), 20000.0, 1000, dice),
                "the 128-block instant arm still fires for a monster");
    }

    @Test
    void theFallExemptionStands() {
        // WitherEntity.takeFallDamage lines 471-473: the empty override —
        // a twenty-block fall costs the boss nothing (already landed; the
        // row's retirement pin).
        MobEntity boss = wither(8L);
        boss.takeFallDamage(20.0f, 1.0f);
        assertEquals(300.0f, boss.health(), 1e-6f, "the fall never wounds the boss");
        // The contrast: the same fall wounds an ordinary body
        // (ceil(20 - 3) = 17).
        MobEntity zombie = new MobEntity(904, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), new Random(8L), worldAt(null));
        zombie.takeFallDamage(20.0f, 1.0f);
        assertEquals(3.0f, zombie.health(), 1e-6f, "the ordinary fall wound");
    }

    @Test
    void theSummonShapeStands() {
        // onSummoned lines 328-331: the 220-tick window and the
        // third-health start (already landed with the skull build's
        // summon — the row's spawner-arm retirement pin).
        MobEntity boss = wither(9L);
        assertEquals(300.0f, boss.health(), 1e-6f, "the body spawns full");
        boss.witherSummoned();
        assertEquals(100.0f, boss.health(), 1e-6f, "the third-health start");
        assertEquals(220, boss.witherInvulnTicks(), "the 220-tick window");
        assertTrue(boss.witherInvulnerable(), "the blue window is armed");
    }
}
