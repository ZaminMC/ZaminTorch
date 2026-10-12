package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
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
 * The witch's own mechanics (Slice 12j — the reference's WitchEntity): the
 * defensive drink ladder with its hasStatusEffect gates, the 33-tick
 * drinking window (the reference's own post-decrement), the stand-still
 * while drinking (the -0.25 speed penalty on the 0.25 base), the heal
 * landing through the potion system, the gated re-drink, and the custom
 * single-item loot walk.
 */
class WitchEntityTest {

    private static MobEntity.WorldQuery worldAt(Position nearestPlayer, boolean submerged) {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 4.0;
            }

            @Override
            public boolean inFluid(double x, double y, double z) {
                return submerged;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                if (nearestPlayer == null) {
                    return null;
                }
                double dx = nearestPlayer.x() - x;
                double dy = nearestPlayer.y() - y;
                double dz = nearestPlayer.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? nearestPlayer : null;
            }
        };
    }

    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 4;

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();

        @Override
        public void spawnLootDrop(Position position, ItemStack stack) {
            drops.add(stack);
        }
    }

    /**
     * The potion surface for the manager's mobs: the effect stores keyed by
     * the engine ids, the heal and the health read riding the mob itself.
     */
    private static final class MobBodies implements PotionSystem.Bodies {
        final List<MobEntity> mobs = new ArrayList<>();
        float hurtAmount;

        @Override
        public List<Object> bodiesIn(double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
            List<Object> found = new ArrayList<>();
            for (MobEntity mob : mobs) {
                Position at = mob.position();
                if (at.x() >= minX && at.x() <= maxX && at.z() >= minZ && at.z() <= maxZ
                        && at.y() >= minY && at.y() <= maxY + 1.95) {
                    found.add(mob);
                }
            }
            return found;
        }

        @Override
        public double squaredDistanceTo(Object body, double x, double y, double z) {
            Position at = ((MobEntity) body).position();
            double dx = at.x() - x;
            double dy = at.y() - y;
            double dz = at.z() - z;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public void hurt(Object body, float amount, String cause, Object attacker, boolean magic) {
            ((MobEntity) body).hurt(amount);
            hurtAmount = amount;
        }

        @Override
        public void heal(Object body, float amount) {
            ((MobEntity) body).heal(amount);
        }

        @Override
        public void exhaust(Object body, float amount) {
        }

        @Override
        public void feed(Object body, int food, float saturation) {
        }

        @Override
        public float healthOf(Object body) {
            return ((MobEntity) body).health();
        }

        @Override
        public boolean fireResistant(Object body) {
            return false;
        }

        @Override
        public boolean undead(Object body) {
            return false; // the witch is living
        }

        @Override
        public boolean isPlayer(Object body) {
            return false;
        }

        @Override
        public int keyOf(Object body) {
            return ((MobEntity) body).entityId();
        }
    }

    /**
     * A scripted float source (the reference's own roll order): the queued
     * values answer first, then the safe 0.5 default fails every gate.
     */
    private static final class ScriptedRandom extends Random {
        private final float[] scripted;
        private int index;

        ScriptedRandom(float... scripted) {
            this.scripted = scripted;
        }

        @Override
        public float nextFloat() {
            if (index < scripted.length) {
                return scripted[index++];
            }
            return 0.5f;
        }
    }

    @Test
    void theKindCarriesTheReferenceIdentity() {
        assertEquals(66, MobType.WITCH.legacyTypeId);
        assertEquals(1.95, MobType.WITCH.height, 1e-9);
        assertEquals(26.0f, MobType.WITCH.maxHealth, 1e-6f);
        assertTrue(MobType.WITCH.hostile);
        assertTrue(MobType.WITCH.traits.ranged());
        assertEquals("", MobType.WITCH.idleSound, "the reference's null ambient hook");
        assertEquals("minecraft:witch", MobType.WITCH.identifier().toString());
    }

    @Test
    void aHurtWitchDrinksItsHealingPotion() {
        Random random = new Random(21L);
        MobBodies bodies = new MobBodies();
        PotionSystem potions = new PotionSystem(bodies);
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(null, false), random, loot,
                1_200_000, SURFACE);
        manager.setPotionSystem(potions);
        MobEntity witch = manager.spawnAt(MobType.WITCH, new Position(0.5, 4.0, 0.5));
        assertNotNull(witch);
        bodies.mobs.add(witch);
        manager.hurt(witch, 20.0f, 0.0);
        assertTrue(witch.health() < MobType.WITCH.maxHealth);

        // The 5%-per-tick heal roll + the 33-tick drink window: 1200 ticks
        // gives the drink overwhelming odds (1 - 0.95^1200) and room for
        // repeats all the way back to the maximum.
        float healed = 0;
        for (int tick = 0; tick < 1200 && healed == 0; tick++) {
            manager.tick(List.of(), 10_000L);
            float now = witch.health();
            if (now > 6.0f) {
                healed = now;
            }
        }
        assertTrue(healed > 6.0f, "the witch drank its instant health (health " + healed + ")");
        assertTrue(healed <= MobType.WITCH.maxHealth,
                "the heal applied within the kind's maximum");
    }

    @Test
    void theDrinkWindowSpansTheReferenceTicks() {
        // The scripted ladder (the reference's own roll order): the water
        // arm fails, the fire arm fails, the heal arm passes — the chain
        // stops there; every later roll (the aura included) reads 0.5.
        ScriptedRandom random = new ScriptedRandom(0.5f, 0.5f, 0.0f);
        MobEntity witch = new MobEntity(200, MobType.WITCH, new Position(0.5, 4.0, 0.5),
                random, worldAt(null, false));
        witch.hurt(20.0f); // below max: the heal arm qualifies

        assertFalse(witch.witchDrinking());
        witch.tick(false);
        assertTrue(witch.witchDrinking(), "the heal drink armed on the first tick");

        // The reference's own window (mobTick line 90's post-decrement):
        // the timer starts at 32 and completes when the check reads 0 —
        // the flag spans 33 full ticks, the effects land on the 34th.
        for (int tick = 2; tick <= 33; tick++) {
            witch.tick(false);
            assertTrue(witch.witchDrinking(), "still drinking at tick " + tick);
            assertEquals(0, witch.consumeWitchDrink(), "no early completion");
        }
        witch.tick(false);
        assertFalse(witch.witchDrinking(), "the drink completed on the 34th tick");
        assertEquals(MobEntity.WITCH_INSTANT_HEALTH, witch.consumeWitchDrink(),
                "the heal potion's metadata landed");
    }

    @Test
    void theDrinkingWitchStandsStill() {
        ScriptedRandom random = new ScriptedRandom(0.5f, 0.5f, 0.0f);
        MobEntity witch = new MobEntity(201, MobType.WITCH, new Position(0.5, 4.0, 0.5),
                random, worldAt(null, false));
        witch.hurt(20.0f);
        witch.tick(false);
        assertTrue(witch.witchDrinking());

        // The drinking speed penalty (the -0.25 modifier on the 0.25 base):
        // the wander walk never moves the body while the flag is up.
        for (int tick = 0; tick < 30; tick++) {
            witch.tick(false);
            assertEquals(0.5, witch.position().x(), 1e-9, "no x drift while drinking");
            assertEquals(4.0, witch.position().y(), 1e-9, "no y drift while drinking");
            assertEquals(0.5, witch.position().z(), 1e-9, "no z drift while drinking");
        }
    }

    @Test
    void theDrinkLadderGatesOnTheLiveStore() {
        // The water-breathing arm's hasStatusEffect clause (mobTick line
        // 107): a submerged witch already breathing water never re-drinks.
        ScriptedRandom random = new ScriptedRandom(0.0f);
        MobBodies bodies = new MobBodies();
        PotionSystem potions = new PotionSystem(bodies);
        MobManager manager = new MobManager(worldAt(null, true), random,
                new Loot(), 1_300_000, SURFACE);
        manager.setPotionSystem(potions);
        MobEntity witch = manager.spawnAt(MobType.WITCH, new Position(0.5, 4.0, 0.5));
        assertNotNull(witch);
        bodies.mobs.add(witch);
        potions.add(witch, new StatusEffectInstance(StatusEffects.WATER_BREATHING, 400, 0));
        assertTrue(potions.hasEffect(witch, StatusEffects.WATER_BREATHING));

        // The scripted ladder: the water arm's roll passes (0.0 < 0.15)
        // and the stub world is submerged — only the live-store gate can
        // refuse the drink. The fire/heal/speed arms and the aura all read
        // the safe 0.5 after.
        witch.tick(false);
        assertFalse(witch.witchDrinking(),
                "the live water breathing refused the redundant drink");
    }

    @Test
    void theWitchDropsSingleItemStacks() {
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(null, false), new Random(7L), loot,
                1_400_000, SURFACE);
        MobEntity witch = manager.spawnAt(MobType.WITCH, new Position(0.5, 4.0, 0.5));
        assertNotNull(witch);

        // Kill through the manager's own walk so the loot drop runs.
        manager.hurt(witch, 100.0f, 0.0);
        // The tick processes the death animation (20 ticks) then the loot.
        for (int tick = 0; tick < 40; tick++) {
            manager.tick(List.of(), 10_000L);
        }

        // The reference's walk (dropItems lines 178-192): 1-3 rows, each
        // unit dropping as its OWN single-item stack (the per-unit
        // dropItem loop) — the total stays within the 0..6 band.
        int total = 0;
        var members = List.of(
                net.zaminmc.torch.server.item.BuiltinItems.GLOWSTONE_DUST,
                net.zaminmc.torch.server.item.BuiltinItems.SUGAR,
                net.zaminmc.torch.server.item.BuiltinItems.REDSTONE,
                net.zaminmc.torch.server.item.BuiltinItems.SPIDER_EYE,
                net.zaminmc.torch.server.item.BuiltinItems.GLASS_BOTTLE,
                net.zaminmc.torch.server.item.BuiltinItems.GUNPOWDER,
                net.zaminmc.torch.server.item.BuiltinItems.STICK);
        for (ItemStack drop : loot.drops) {
            assertEquals(1, drop.count(), "each unit drops as its own stack");
            assertTrue(members.contains(drop.type()),
                    "only the witch's ingredient family drops: " + drop.type());
            total += drop.count();
        }
        assertTrue(total <= 6, "1-3 rows of 0-2 items: at most 6 (was " + total + ")");
    }
}
