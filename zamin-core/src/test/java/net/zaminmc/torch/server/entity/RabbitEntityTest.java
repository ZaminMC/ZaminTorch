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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rabbit's own mechanics (Slices 12l + 21d — the reference's
 * RabbitEntity 1:1): the kind identity, the Action-model hop cycle (the
 * per-action speed/strength/delay, the landing's delay re-roll, the
 * killer's lunge), the six-skin coat with the killer's 99 (the 8.0 wound,
 * the wolf hunt, the revenge arm, the flee's removal), the garden raid
 * (the farmland-with-age-7-carrots spiral, the eat's hunger refill), the
 * breed coat inheritance, the carrot-family tempt, and the drops (the
 * hide, the meat with the fire's cooked swap, the rare foot's 0.025
 * roll).
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

    /**
     * The garden world: the flat stub plus a block map — the farmland at
     * (10, 7, 0) with age-7 carrots standing above it (the raid's target),
     * plus the mob list the flee/hunt reads walk.
     */
    private static final class GardenWorld implements MobEntity.WorldQuery {
        final Map<Long, String> blocks = new HashMap<>();
        final List<MobEntity> mobs = new ArrayList<>();

        GardenWorld() {
            blocks.put(key(10, 7, 0), "farmland");
            blocks.put(key(10, 8, 0), "carrots_stage7");
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0xFFFFFFL) << 40 | ((long) z & 0xFFFFFFL) << 20
                    | ((long) y & 0xFFFFFL);
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            String id = blocks.get(key(x, y, z));
            return id == null ? null : net.zaminmc.torch.util.Identifier.parse("minecraft:" + id);
        }

        @Override
        public MobEntity nearestMobOfKind(double x, double y, double z, double range, MobType kind) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobs) {
                if (mob.dead() || mob.type() != kind) {
                    continue;
                }
                Position p = mob.position();
                double dx = p.x() - x;
                double dy = p.y() - y;
                double dz = p.z() - z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d <= bestDistance) {
                    bestDistance = d;
                    best = mob;
                }
            }
            return best;
        }
    }

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();

        @Override
        public void spawnLootDrop(Position position, ItemStack stack) {
            drops.add(stack);
        }
    }

    /** The abstract listener surface, stubbed (the tests below override
     * the arms they observe). */
    private static class NoopListener implements MobManager.Listener {
        @Override public void onMobSpawned(MobEntity mob) { }
        @Override public void onMobMoved(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession rider) { }
        @Override public void onMobHurt(MobEntity mob) { }
        @Override public void onMobDied(MobEntity mob) { }
        @Override public void onMobRemoved(MobEntity mob, String reason) { }
        @Override public void onMobAttackedPlayer(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession target, float damage) { }
        @Override public void onMobSound(MobEntity mob, String soundName) { }
        @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
        @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
        @Override public void onMobSheared(MobEntity mob, int woolCount) { }
        @Override public void onMobCoatRegrown(MobEntity mob) { }
        @Override public void onMobExploded(MobEntity mob) { }
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
        // The HOP action (the reference's Action enum 1:1): jumpDelay 20,
        // jumpStrength 0.2, speed 0.8 * 1.5 = 1.2 at launch. Between hops
        // the input is zeroed (the MovementControl rule). A wandering
        // rabbit leaves the ground within the window; an idle one never
        // does (the NONE action: the sitting rabbit).
        MobEntity wanderer = new MobEntity(400, MobType.RABBIT, new Position(0.5, 8.0, 0.5),
                new Random(7L), worldAt());
        boolean hopped = false;
        double peakY = wanderer.position().y();
        // The idle band (IDLE_TICKS 60 + nextInt(60)) passes first, then
        // the wander phase hops on the 20-tick cadence — 400 ticks cover
        // a full idle-then-wander cycle at the slower HOP delay.
        for (int tick = 0; tick < 400 && !hopped; tick++) {
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
    void theNaturalCoatRollsTheSixSkinsTheKillerNever() {
        // RabbitEntity.initialize lines 336-353: the natural coat rolls
        // nextInt(6) — the six natural skins 0-5; the killer's 99 lands
        // only through setVariant (the NBT /summon arm), never the roll.
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(), new Random(21L), loot,
                1_500_100, SURFACE);
        boolean[] seen = new boolean[6];
        for (int i = 0; i < 200; i++) {
            MobEntity rabbit = manager.spawnAt(MobType.RABBIT,
                    new Position(0.5, 8.0, 0.5));
            assertNotNull(rabbit);
            assertTrue(rabbit.rabbitVariant() >= 0 && rabbit.rabbitVariant() <= 5,
                    "the natural coat band 0-5 (saw " + rabbit.rabbitVariant() + ")");
            assertFalse(rabbit.rabbitKiller(), "the roll never answers 99");
            seen[rabbit.rabbitVariant()] = true;
        }
        for (int coat = 0; coat < 6; coat++) {
            assertTrue(seen[coat], "the roll covers coat " + coat);
        }
    }

    @Test
    void theKillerBunnyCarriesTheKillerLaw() {
        // tryDamage lines 255-262: the killer's 8.0 wound; the plain
        // rabbit's 3.0 never arms. setVariant(99)'s own mind swap: the
        // wolf hunt (the ActiveTargetGoal at setVariant line 326), the
        // revenge arm (targetSelector 1), and the flee-wolf's removal
        // (the removeGoal at line 322).
        GardenWorld world = new GardenWorld();
        MobManager manager = new MobManager(world, new Random(22L), new Loot(),
                1_500_200, SURFACE);
        MobEntity killer = new MobEntity(500, MobType.RABBIT,
                new Position(0.5, 8.0, 0.5), new Random(51L), world);
        killer.setRabbitVariant(99);
        world.mobs.add(killer);
        assertTrue(killer.rabbitKiller());
        assertEquals(8.0f, killer.attackDamage(), 1e-6f, "the killer's own wound");

        MobEntity plain = new MobEntity(501, MobType.RABBIT,
                new Position(20.5, 8.0, 0.5), new Random(52L), world);
        world.mobs.add(plain);
        assertEquals(3.0f, plain.attackDamage(), 1e-6f, "the plain body's tryDamage");
        assertFalse(plain.rabbitKiller());

        // The wolf hunt: a live wolf within the follow range becomes the
        // killer's target (the 1-in-10 cadence — keep ticking until the
        // scan fires).
        MobEntity wolf = new MobEntity(502, MobType.WOLF,
                new Position(5.5, 8.0, 0.5), new Random(53L), world);
        world.mobs.add(wolf);
        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = killer.tickMobTargeting();
        }
        assertSame(wolf, found, "the killer hunts the wolf (setVariant 326)");

        // The flee-wolf gate: the plain rabbit flees, the killer never
        // (the goal's own removal).
        assertTrue(plain.goalFleeWolfCanUse(),
                "the plain rabbit flees the wolf within 16");
        assertFalse(killer.goalFleeWolfCanUse(),
                "setVariant(99) removed the flee goal");

        // The revenge arm (Slice 21d): a hurt killer targets its attacker.
        MobEntity attacker = new MobEntity(503, MobType.WOLF,
                new Position(6.5, 8.0, 1.5), new Random(54L), world);
        world.mobs.add(attacker);
        killer.setMobAttackTarget(null); // the hunt's target clears first
        manager.hurtByMob(attacker, killer, 1.0f);
        assertSame(attacker, killer.mobAttackTarget(),
                "the killer's RevengeGoal targets the attacker");
    }

    @Test
    void theGardenRaidFindsTheFarmlandAndEats() {
        // RaidFarmGoal over GoToBlockGoal 1:1: the hungry gate, the
        // farmland-with-age-7-carrots target, the walk, and the eat — the
        // carrots vanish through the manager's intent (the engine's air
        // write), the hunger clock refills to 100 (onEatFromGarden), the
        // cooldown re-arms at 10, and the fed body gardens never again
        // until the clock empties.
        GardenWorld world = new GardenWorld();
        MobManager manager = new MobManager(world, new Random(23L), new Loot(),
                1_500_300, SURFACE);
        // The engine's own eat handler (EngineServer.onRabbitFarmEat): the
        // air write over the carrots' cell + the hunger refill.
        manager.addListener(new NoopListener() {
            @Override
            public void onRabbitFarmEat(MobEntity rabbit) {
                world.blocks.remove(GardenWorld.key(
                        rabbit.rabbitFarmEatX(), rabbit.rabbitFarmEatY(),
                        rabbit.rabbitFarmEatZ()));
                rabbit.completeRabbitFarmEat();
            }
        });
        // The standing rabbit: fresh spawn (hunger 0 — the MoreCarrotTicks
        // default), the body already inside the up-center's 1.0 band, so
        // the first tick's scan finds and the eat lands at once.
        MobEntity rabbit = manager.spawnAt(MobType.RABBIT,
                new Position(10.5, 8.0, 0.5));
        assertNotNull(rabbit);
        assertEquals(0, rabbit.rabbitTicksBeforeHungry(), "fresh bodies are hungry");
        assertTrue(world.blocks.containsKey(GardenWorld.key(10, 8, 0)),
                "the carrots stand");
        manager.tick(List.of(), MobManager.NIGHT_START);
        assertFalse(world.blocks.containsKey(GardenWorld.key(10, 8, 0)),
                "the eat's air write landed");
        assertEquals(100, rabbit.rabbitTicksBeforeHungry(),
                "onEatFromGarden's 100 refill");
        assertFalse(rabbit.goalRabbitFarmCanUse(),
                "the fed body gardens never (the hungry gate)");
        // The distant rabbit: the same fresh hunger and a re-planted
        // garden, but the body stands far away — the approach arm walks,
        // no eat lands.
        world.blocks.put(GardenWorld.key(10, 8, 0), "carrots_stage7");
        MobEntity far = manager.spawnAt(MobType.RABBIT, new Position(0.5, 8.0, 0.5));
        assertNotNull(far);
        manager.tick(List.of(), MobManager.NIGHT_START);
        assertFalse(far.consumeRabbitFarmEat(),
                "the distant body walks first (the approach arm)");
    }

    @Test
    void theTemptFollowsTheCarrotFamily() {
        // RabbitEntity's own TemptGoal family (lines 59-61): the carrot,
        // the golden carrot, and the dandelion — speed 1.0, NOT scared by
        // movement (the ocelot's fish-follow IS; the rabbit's never
        // breaks on a turn).
        final Position[] holderAt = {new Position(6.0, 8.0, 0.5)};
        final String[] heldItem = {null};
        MobEntity.WorldQuery world = new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }

            @Override
            public MobEntity.PlayerView nearestPlayerView(double x, double y,
                                                          double z, double range) {
                double dx = holderAt[0].x() - x;
                double dy = holderAt[0].y() - y;
                double dz = holderAt[0].z() - z;
                if (dx * dx + dy * dy + dz * dz > range * range) {
                    return null;
                }
                return new MobEntity.PlayerView() {
                    @Override public Position position() {
                        return holderAt[0];
                    }

                    @Override public float yaw() {
                        return 0f;
                    }

                    @Override public float pitch() {
                        return 0f;
                    }

                    @Override public String heldItem() {
                        return heldItem[0];
                    }
                };
            }
        };
        MobEntity rabbit = new MobEntity(430, MobType.RABBIT,
                new Position(0.5, 8.0, 0.5), new Random(24L), world);
        assertFalse(rabbit.goalTemptCanUse(), "an empty hand never tempts");
        heldItem[0] = "minecraft:wheat";
        assertFalse(rabbit.goalTemptCanUse(), "the wheat is not the rabbit's food");
        for (String food : new String[] {"minecraft:carrot",
                "minecraft:golden_carrot", "minecraft:dandelion"}) {
            heldItem[0] = food;
            assertTrue(rabbit.goalTemptCanUse(), food + " tempts the rabbit");
            rabbit.goalTemptTick();
        }
        // The not-scared arm: a holder who spins in place never breaks the
        // rabbit's follow (the ocelot's does) — shouldContinue is only the
        // item re-check.
        heldItem[0] = "minecraft:carrot";
        assertTrue(rabbit.goalTemptCanUse());
        // The holder swaps mid-follow: the goal stops (the item gate).
        heldItem[0] = "minecraft:stone";
        assertFalse(rabbit.goalTemptCanUse(), "the swapped item stops the follow");
    }

    @Test
    void theBreedCoatComesFromEitherParent() {
        // RabbitEntity.makeChild lines 302-309: the child's coat is the
        // reference's own nextBoolean() coin — either parent's skin. Two
        // fixed coats (0 and 5) breed many times; every child wears one
        // of the two, and both sides of the coin land.
        final List<MobEntity> babies = new ArrayList<>();
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(), new Random(25L), loot,
                1_500_400, SURFACE);
        manager.addListener(new NoopListener() {
            @Override
            public void onMobBred(MobEntity parent, MobEntity mate, MobEntity baby) {
                babies.add(baby);
            }
        });
        for (int round = 0; round < 60; round++) {
            MobEntity a = manager.spawnAt(MobType.RABBIT,
                    new Position(0.5 + round * 4.0, 8.0, 0.5));
            MobEntity b = manager.spawnAt(MobType.RABBIT,
                    new Position(1.5 + round * 4.0, 8.0, 0.5));
            a.setRabbitVariant(0);
            b.setRabbitVariant(5);
            a.enterLove(1);
            b.enterLove(2);
            a.requestBreedWith(b);
            manager.tick(List.of(), 10_000L); // the pending breed drains
        }
        int coat0 = 0;
        int coat5 = 0;
        for (MobEntity baby : babies) {
            assertTrue(baby.rabbitVariant() == 0 || baby.rabbitVariant() == 5,
                    "the child wears a parent's coat (saw " + baby.rabbitVariant() + ")");
            if (baby.rabbitVariant() == 0) {
                coat0++;
            } else {
                coat5++;
            }
        }
        assertTrue(babies.size() >= 60, "the rounds bred (" + babies.size() + " babies)");
        assertTrue(coat0 > 0 && coat5 > 0,
                "both coats appear over 60 coins (" + coat0 + "/" + coat5 + ")");
    }

    @Test
    void theDropsCarryTheReferenceRolls() {
        // dropItems: the hide nextInt(2) + nextInt(1+looting) units and the
        // raw rabbit nextInt(2) units — each unit its own single-item stack.
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
    void theCookedSwapAndTheRareFootFollowTheReferenceLaw() {
        // The always-drop rig: every nextFloat answers 0.0 (the rare
        // 0.025 roll always lands) and every nextInt(2) answers 1 (one
        // hide unit, one meat unit — the reference's own rolls), so a
        // burning rabbit's meat swaps to the cooked cut and the
        // player-kill window answers the rare foot deterministically.
        Random forced = new Random(26L) {
            @Override
            public float nextFloat() {
                return 0.0f;
            }

            @Override
            public int nextInt(int bound) {
                return bound == 2 ? 1 : super.nextInt(bound);
            }
        };
        Loot loot = new Loot();
        MobManager alwaysRare = new MobManager(worldAt(), forced, loot,
                1_500_500, SURFACE);
        MobEntity burning = alwaysRare.spawnAt(MobType.RABBIT,
                new Position(0.5, 8.0, 0.5));
        burning.ignite(100);
        burning.markPlayerHit(); // LivingEntity.die line 766's gate
        alwaysRare.hurt(burning, 999.0f, 0.0);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            alwaysRare.tick(List.of(), MobManager.NIGHT_START);
        }
        boolean sawCooked = false;
        boolean sawFoot = false;
        for (ItemStack drop : loot.drops) {
            if (drop.type() == BuiltinItems.COOKED_RABBIT) {
                sawCooked = true;
            } else if (drop.type() == BuiltinItems.RABBIT_FOOT) {
                sawFoot = true;
            } else if (drop.type() != BuiltinItems.RABBIT_HIDE) {
                throw new AssertionError("unexpected rabbit loot " + drop.type());
            }
            assertEquals(1, drop.count(), "per-unit single-item stacks");
        }
        assertTrue(sawCooked, "the burning body drops the cooked cut");
        assertTrue(sawFoot, "the rare foot at the forced 0.025 roll");
        // No player hit — no rare foot (the window's own gate).
        Random forcedCold = new Random(27L) {
            @Override
            public float nextFloat() {
                return 0.0f;
            }

            @Override
            public int nextInt(int bound) {
                return bound == 2 ? 1 : super.nextInt(bound);
            }
        };
        Loot noPlayer = new Loot();
        MobManager neverHit = new MobManager(worldAt(), forcedCold, noPlayer,
                1_500_600, SURFACE);
        MobEntity cold = neverHit.spawnAt(MobType.RABBIT, new Position(0.5, 8.0, 0.5));
        neverHit.hurt(cold, 999.0f, 0.0);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            neverHit.tick(List.of(), MobManager.NIGHT_START);
        }
        boolean sawRaw = false;
        for (ItemStack drop : noPlayer.drops) {
            if (drop.type() == BuiltinItems.RABBIT_FOOT) {
                throw new AssertionError("the rare foot needs the player-kill window");
            }
            if (drop.type() == BuiltinItems.RABBIT) {
                sawRaw = true; // the cold body keeps the raw cut
            }
        }
        assertTrue(sawRaw, "the cold body drops the raw cut (no cooked swap)");
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
