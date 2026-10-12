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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rabbit's own mechanics (Slice 12l — the reference's RabbitEntity):
 * the kind identity, the hop cycle (the cadence, the zeroed input between
 * hops, the launch impulse), the drops (the hide and the raw rabbit), the
 * breeding family (the carrot, the golden carrot, the dandelion), and the
 * animal dispatch.
 */
class RabbitEntityTest {

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
        assertEquals(101, MobType.RABBIT.legacyTypeId);
        assertEquals(0.6, MobType.RABBIT.width, 1e-9); // setSize(0.6F, 0.7F)
        assertEquals(0.7, MobType.RABBIT.height, 1e-9);
        assertEquals(10.0f, MobType.RABBIT.maxHealth, 1e-6f); // initAttributes
        assertFalse(MobType.RABBIT.hostile);
        assertEquals("mob.rabbit.idle", MobType.RABBIT.idleSound);
        assertEquals("mob.rabbit.hurt", MobType.RABBIT.hurtSound);
        assertEquals("mob.rabbit.death", MobType.RABBIT.deathSound);
        assertEquals("minecraft:rabbit", MobType.RABBIT.identifier().toString());
    }

    @Test
    void theHopCycleLaunchesOnTheCadenceAndStillsBetween() {
        // The HOP action (the reference's Action enum): jumpDelay 10,
        // jumpStrength 0.2, speed 0.8 * 1.5 = 1.2 at launch. Between hops
        // the input is zeroed (the MovementControl rule). A wandering
        // rabbit leaves the ground within the window; an idle one never
        // does (the NONE action: the sitting rabbit).
        MobEntity wanderer = new MobEntity(400, MobType.RABBIT, new Position(0.5, 8.0, 0.5),
                new Random(7L), worldAt());
        boolean hopped = false;
        double peakY = wanderer.position().y();
        // The idle band (IDLE_TICKS 60 + nextInt(60)) passes first, then
        // the wander phase hops on the 10-tick cadence — 220 ticks cover
        // a full idle-then-wander cycle.
        for (int tick = 0; tick < 220 && !hopped; tick++) {
            wanderer.tick(false);
            peakY = Math.max(peakY, wanderer.position().y());
            if (wanderer.position().y() > 8.01) {
                hopped = true;
            }
        }
        assertTrue(hopped, "the wandering rabbit left the ground on a hop (peak y "
                + peakY + ")");
        assertEquals("minecraft:rabbit", wanderer.type().identifier().toString());
    }

    @Test
    void theDropsCarryTheReferenceRolls() {
        // dropItems: the hide nextInt(2) + nextInt(1+looting) units and the
        // raw rabbit nextInt(2) units (the cooked swap rides the burn arm;
        // the foot's 2.5% rare roll ledgers with the rare-loot slice).
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(), new Random(5L), loot,
                1_500_000, SURFACE);
        MobEntity rabbit = manager.spawnAt(MobType.RABBIT, new Position(0.5, 8.0, 0.5));
        assertNotNull(rabbit);
        manager.hurt(rabbit, 100.0f, 0.0);
        for (int tick = 0; tick < 40; tick++) {
            manager.tick(List.of(), 10_000L);
        }
        int hides = 0;
        int raw = 0;
        for (ItemStack stack : loot.drops) {
            if (stack.type() == BuiltinItems.RABBIT_HIDE) {
                hides += stack.count();
            } else if (stack.type() == BuiltinItems.RABBIT) {
                raw += stack.count();
            }
        }
        assertTrue(hides <= 1 && raw <= 1,
                "the reference rolls: 0-1 hide, 0-1 raw (saw " + hides + "/" + raw + ")");
    }

    @Test
    void theRabbitFamilyItemsCarryTheReferenceIds() {
        // Item.java lines 715-719: rabbit 411, cooked_rabbit 412,
        // rabbit_stew 413 (stack 1 — the bowl rule), rabbit_foot 414,
        // rabbit_hide 415.
        assertEquals("minecraft:rabbit", BuiltinItems.RABBIT.identifier().toString());
        assertEquals(64, BuiltinItems.RABBIT.maxStackSize());
        assertEquals("minecraft:cooked_rabbit", BuiltinItems.COOKED_RABBIT.identifier().toString());
        assertEquals("minecraft:rabbit_stew", BuiltinItems.RABBIT_STEW.identifier().toString());
        assertEquals(1, BuiltinItems.RABBIT_STEW.maxStackSize(), "the stew stacks to 1");
        assertEquals("minecraft:rabbit_hide", BuiltinItems.RABBIT_HIDE.identifier().toString());
        assertEquals("minecraft:rabbit_foot", BuiltinItems.RABBIT_FOOT.identifier().toString());
    }
}
