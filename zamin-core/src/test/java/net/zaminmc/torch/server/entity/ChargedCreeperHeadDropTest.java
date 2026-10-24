package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The charged-creeper head drop (the ML-100 family tail, 1:1 over the
 * reference): a victim killed by a CHARGED creeper's blast drops its own
 * head — the zombie's (SKULL damage 2, ZombieEntity.die lines 589-598),
 * the skeleton's (damage 1 wither variant / 0 plain, SkeletonEntity.die
 * lines 156-172), the creeper's own (damage 4 — killed by ANOTHER charged
 * creeper, CreeperEntity.die lines 139-152) — once per creeper
 * (shouldDropMobHead's counter, CreeperEntity lines 29 + 223-227). The
 * attribution rides the blast's source through the wound walk (the
 * reference's DamageSource.getAttacker read).
 */
class ChargedCreeperHeadDropTest {

    /** The flat stub world: solid below y=8, no players. */
    private static final class FlatWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 8;

    /** The recording loot sink: every drop lands in the list. */
    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();

        @Override
        public void spawnLootDrop(Position position, ItemStack stack) {
            drops.add(stack);
        }
    }

    private record ManagerWithLoot(MobManager manager, Loot loot) { }

    private static ManagerWithLoot manager(long seed) {
        Loot loot = new Loot();
        MobManager manager = new MobManager(new FlatWorld(), new Random(seed), loot,
                1_500_300, SURFACE);
        return new ManagerWithLoot(manager, loot);
    }

    /** Kills the victim through the blast's own wound, then ticks the death out. */
    private static void killByBlast(MobManager manager, MobEntity victim, MobEntity source) {
        assertTrue(manager.hurtFromExplosion(victim, 1000.0f, source),
                "the blast wound landed");
        assertTrue(victim.dead(), "the victim died");
        for (int i = 0; i < 30 && !victim.deathAnimationFinished(); i++) {
            manager.tick(List.of(), 6_000L);
        }
        assertTrue(victim.deathAnimationFinished(), "the death animation completed");
        manager.tick(List.of(), 6_000L); // the removal tick: the loot lands
    }

    private static long skullsOfDamage(List<ItemStack> drops, int damage) {
        return drops.stream()
                .filter(d -> d.type() == BuiltinItems.SKULL && d.damage() == damage)
                .count();
    }

    @Test
    void theZombieKilledByAChargedCreeperDropsItsHead() {
        // ZombieEntity.die lines 589-598: the charged gate + the once gate
        // -> SKULL damage 2 (the zombie head), beside the corpse's own
        // rotten flesh.
        ManagerWithLoot m = manager(31L);
        MobEntity creeper = m.manager().spawnAt(MobType.CREEPER,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(creeper);
        creeper.setCreeperCharged(true);
        MobEntity zombie = m.manager().spawnAt(MobType.ZOMBIE,
                new Position(2.5, 8.0, 0.5));
        assertNotNull(zombie);
        killByBlast(m.manager(), zombie, creeper);
        assertEquals(1, skullsOfDamage(m.loot().drops, 2),
                "the zombie's head dropped once");
        assertTrue(m.loot().drops.stream().anyMatch(
                        d -> d.type() == net.zaminmc.torch.server.item.BuiltinItems.ROTTEN_FLESH),
                "the corpse's own loot still landed");
    }

    @Test
    void theOnceGateOneHeadPerCreeper() {
        // CreeperEntity.shouldDropMobHead line 223: mobHeadDropCount < 1 —
        // the second victim of the same charged creeper carries no head.
        ManagerWithLoot m = manager(32L);
        MobEntity creeper = m.manager().spawnAt(MobType.CREEPER,
                new Position(0.5, 8.0, 0.5));
        creeper.setCreeperCharged(true);
        MobEntity first = m.manager().spawnAt(MobType.ZOMBIE,
                new Position(2.5, 8.0, 0.5));
        MobEntity second = m.manager().spawnAt(MobType.ZOMBIE,
                new Position(4.5, 8.0, 0.5));
        killByBlast(m.manager(), first, creeper);
        killByBlast(m.manager(), second, creeper);
        assertEquals(1, skullsOfDamage(m.loot().drops, 2),
                "one head per creeper, never two");
    }

    @Test
    void theUnchargedBlastNeverDropsAHead() {
        // The isCharged gate (line 593): a plain creeper's blast kills
        // without the head.
        ManagerWithLoot m = manager(33L);
        MobEntity creeper = m.manager().spawnAt(MobType.CREEPER,
                new Position(0.5, 8.0, 0.5));
        assertFalse(creeper.creeperCharged(), "the plain creeper");
        MobEntity zombie = m.manager().spawnAt(MobType.ZOMBIE,
                new Position(2.5, 8.0, 0.5));
        killByBlast(m.manager(), zombie, creeper);
        assertEquals(0, skullsOfDamage(m.loot().drops, 2),
                "no head from the uncharged blast");
    }

    @Test
    void theSkeletonVariantCarriesItsOwnSkullDamage() {
        // SkeletonEntity.die lines 165-172: {@code getType() == 1 ? 1 : 0}
        // — the wither skeleton's skull over the plain skeleton's.
        ManagerWithLoot m = manager(34L);
        MobEntity creeper = m.manager().spawnAt(MobType.CREEPER,
                new Position(0.5, 8.0, 0.5));
        creeper.setCreeperCharged(true);
        MobEntity wither = m.manager().spawnAt(MobType.SKELETON,
                new Position(2.5, 8.0, 0.5));
        wither.setSkeletonWither(true);
        killByBlast(m.manager(), wither, creeper);
        assertEquals(1, skullsOfDamage(m.loot().drops, 1),
                "the wither skeleton's skull (damage 1)");
    }

    @Test
    void theCreeperVictimCarriesItsOwnSkull() {
        // CreeperEntity.die lines 146-152: a creeper killed by ANOTHER
        // charged creeper drops the creeper head (damage 4). The blasting
        // body itself never wounds itself (the pre-blast removal).
        ManagerWithLoot m = manager(35L);
        MobEntity charged = m.manager().spawnAt(MobType.CREEPER,
                new Position(0.5, 8.0, 0.5));
        charged.setCreeperCharged(true);
        MobEntity victim = m.manager().spawnAt(MobType.CREEPER,
                new Position(2.5, 8.0, 0.5));
        killByBlast(m.manager(), victim, charged);
        assertEquals(1, skullsOfDamage(m.loot().drops, 4),
                "the victim creeper's own head (damage 4)");
    }

    @Test
    void theHeadlessFamilyNeverDropsASkull() {
        // The die arms exist on the zombie, the skeleton, and the creeper
        // alone — a charged blast killing a cow carries no head.
        ManagerWithLoot m = manager(36L);
        MobEntity creeper = m.manager().spawnAt(MobType.CREEPER,
                new Position(0.5, 8.0, 0.5));
        creeper.setCreeperCharged(true);
        MobEntity cow = m.manager().spawnAt(MobType.COW,
                new Position(2.5, 8.0, 0.5));
        killByBlast(m.manager(), cow, creeper);
        assertTrue(m.loot().drops.stream().noneMatch(
                        d -> d.type() == BuiltinItems.SKULL),
                "the headless family drops no skull");
    }
}
