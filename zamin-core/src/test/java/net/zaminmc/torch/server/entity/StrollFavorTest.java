package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stroll family's favor lane (Slice TF-6 — the ML-100 favor family's
 * landing): the per-family favor dispatch (the monster dark preference,
 * the animal grass floor, the giant's famous inverted law, the
 * silverfish's stone floor, the guardian's water arm, the base flat),
 * the TargetFinder best-of-10 pick (the dark half wins for monsters, the
 * grass half for animals, the BRIGHT half for the giant — the inverted
 * quirk), the cadence (the interval roll, the despawn gate), and the
 * walk's own life (the navigation runs the stroll to its end).
 */
class StrollFavorTest {

    /**
     * The stub world: a flat floor below y=8, per-cell brightness (the
     * bright/dark halves the favor dispatch reads), and per-cell block
     * identities below the candidate (the grass/stone floors).
     */
    private static class FavorWorld implements MobEntity.WorldQuery {
        /** The brightness by cell key "x,y,z"; the default is full bright. */
        final Map<String, Float> brightness = new HashMap<>();
        /** The block identity by cell key (the below-floor reads). */
        final Map<String, String> blocks = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public float brightness(int x, int y, int z) {
            Float value = brightness.get(x + "," + y + "," + z);
            return value != null ? value : 1.0f;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            String id = blocks.get(x + "," + y + "," + z);
            return Identifier.parse(id != null ? id : "minecraft:air");
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return Math.floor(y) >= 8.0; // the plane above the floor is water
        }
    }

    /** The dark half world: x > 0 answers brightness 0, x <= 0 full bright. */
    private static FavorWorld darkEastWorld() {
        FavorWorld world = new FavorWorld();
        for (int x = -12; x <= 12; x++) {
            for (int y = 0; y <= 16; y++) {
                for (int z = -12; z <= 12; z++) {
                    world.brightness.put(x + "," + y + "," + z, x > 0 ? 0.0f : 1.0f);
                }
            }
        }
        return world;
    }

    /** The grass-east world: every cell below x > 0's candidates is grass. */
    private static FavorWorld grassEastWorld() {
        FavorWorld world = new FavorWorld();
        for (int x = -12; x <= 12; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = -12; z <= 12; z++) {
                    world.blocks.put(x + "," + y + "," + z,
                            x > 0 ? "minecraft:grass_block" : "minecraft:stone");
                }
            }
        }
        return world;
    }

    private static MobEntity mob(FavorWorld world, MobType kind, long seed) {
        return new MobEntity(700, kind, new Position(0.5, 8, 0.5), new Random(seed), world);
    }

    @Test
    void theFavorDispatchCarriesEveryFamilysOwnLaw() {
        FavorWorld world = new FavorWorld();
        world.brightness.put("5,8,5", 0.1f); // a dark cell
        world.brightness.put("6,8,6", 1.0f); // a bright cell

        // MonsterEntity lines 105-109: the dark preference.
        MobEntity zombie = mob(world, MobType.ZOMBIE, 1L);
        assertEquals(0.4f, zombie.getPathfindingFavor(5, 8, 5), 0.0001f,
                "the dark cell: 0.5 - 0.1");
        assertEquals(-0.5f, zombie.getPathfindingFavor(6, 8, 6), 0.0001f,
                "the bright cell: 0.5 - 1.0");

        // GiantEntity lines 27-29: the family's famous INVERTED law.
        MobEntity giant = mob(world, MobType.GIANT, 1L);
        assertEquals(-0.4f, giant.getPathfindingFavor(5, 8, 5), 0.0001f,
                "the giant's dark cell: 0.1 - 0.5 (inverted)");
        assertEquals(0.5f, giant.getPathfindingFavor(6, 8, 6), 0.0001f,
                "the giant's bright cell: 1.0 - 0.5 (inverted)");

        // PathFinderMobEntity lines 29-31: the base flat.
        MobEntity villager = mob(world, MobType.VILLAGER, 1L);
        assertEquals(0.0f, villager.getPathfindingFavor(5, 8, 5), 0.0001f,
                "the villager's flat 0.0");
        assertEquals(0.0f, villager.getPathfindingFavor(6, 8, 6), 0.0001f,
                "the flat everywhere");
    }

    @Test
    void theAnimalPrefersTheGrassFloorAndTheSilverfishTheStone() {
        FavorWorld world = new FavorWorld();
        world.blocks.put("3,7,3", "minecraft:grass_block");
        world.blocks.put("4,7,4", "minecraft:stone");
        world.blocks.put("5,7,5", "minecraft:dirt");

        // AnimalEntity lines 72-75: the grass floor scores 10.0, the rest
        // brightness - 0.5 (full bright: 0.5).
        MobEntity pig = mob(world, MobType.PIG, 1L);
        assertEquals(10.0f, pig.getPathfindingFavor(3, 8, 3), 0.0001f,
                "the grass-below cell scores the floor");
        assertEquals(0.5f, pig.getPathfindingFavor(5, 8, 5), 0.0001f,
                "the dirt-below bright cell: brightness - 0.5");

        // SilverfishEntity lines 105-108: the stone floor scores 10.0,
        // every other cell the monster law (full bright: 0.5 - 1.0).
        MobEntity silverfish = mob(world, MobType.SILVERFISH, 1L);
        assertEquals(10.0f, silverfish.getPathfindingFavor(4, 8, 4), 0.0001f,
                "the stone-below cell scores the floor");
        assertEquals(-0.5f, silverfish.getPathfindingFavor(5, 8, 5), 0.0001f,
                "the dirt-below bright cell: the monster law");
    }

    @Test
    void theGuardiansWaterArmScoresAboveItsLandArm() {
        FavorWorld world = new FavorWorld();
        MobEntity guardian = mob(world, MobType.GUARDIAN, 1L);
        // GuardianEntity lines 238-243: the water cell 10.0 + brightness -
        // 0.5 (full bright: 10.5), the dry cell the monster law (-0.5).
        assertEquals(10.5f, guardian.getPathfindingFavor(0, 8, 0), 0.0001f,
                "the water cell's own score");
        FavorWorld dry = new FavorWorld() {
            @Override
            public boolean inFluid(double x, double y, double z) {
                return false; // the dry plane
            }
        };
        MobEntity beached = mob(dry, MobType.GUARDIAN, 1L);
        assertEquals(-0.5f, beached.getPathfindingFavor(0, 8, 0), 0.0001f,
                "the dry cell falls to the monster law");
    }

    @Test
    void theStrollPickPrefersTheFavorHalfOfTheWorld() {
        // The monster's pick over the dark-east world: a single dark
        // candidate among the ten beats every bright one (0.5 over -0.5),
        // so the overwhelming mass of picks must land east.
        int dark = 0;
        for (int seed = 1; seed <= 300; seed++) {
            MobEntity zombie = mob(darkEastWorld(), MobType.ZOMBIE, seed);
            assertTrue(zombie.pickStrollTargetForTest(), "a pick always wins on the plane");
            if (zombie.strollTargetForTest().x() > 0) {
                dark++;
            }
        }
        assertTrue(dark > 270, "the dark half wins the mass of picks (got " + dark + "/300)");

        // The giant's pick over the same world: the INVERTED law — the
        // bright half (x <= 0) wins.
        int bright = 0;
        for (int seed = 1; seed <= 300; seed++) {
            MobEntity giant = mob(darkEastWorld(), MobType.GIANT, seed);
            assertTrue(giant.pickStrollTargetForTest());
            if (giant.strollTargetForTest().x() <= 0) {
                bright++;
            }
        }
        assertTrue(bright > 270, "the giant's inverted law wins the bright half (got "
                + bright + "/300)");

        // The animal's pick over the grass-east world: the grass floor's
        // 10.0 beats the stone half's 0.5.
        int grass = 0;
        for (int seed = 1; seed <= 300; seed++) {
            MobEntity pig = mob(grassEastWorld(), MobType.PIG, seed);
            assertTrue(pig.pickStrollTargetForTest());
            if (pig.strollTargetForTest().x() > 0) {
                grass++;
            }
        }
        assertTrue(grass > 270, "the grass half wins the mass of picks (got " + grass + "/300)");
    }

    @Test
    void theStrollStartsOnTheIntervalRollAndNeverForTheDespawningBody() {
        FavorWorld world = darkEastWorld();
        MobEntity zombie = mob(world, MobType.ZOMBIE, 42L);
        // The interval roll (120): the stroll starts inside a generous
        // window of plain ticks (no despawn bookkeeping on the test body).
        int ticks = 0;
        while (zombie.mode() != MobEntity.Mode.WANDER && ticks < 2000) {
            zombie.tick();
            ticks++;
        }
        assertEquals(MobEntity.Mode.WANDER, zombie.mode(),
                "the interval roll started the stroll within the window");

        // The despawn gate (WanderAroundGoal.canStart's >= 100): a body
        // 100+ ticks without a player nearby never strolls again.
        FavorWorld gated = darkEastWorld();
        MobEntity leaving = mob(gated, MobType.ZOMBIE, 42L);
        for (int i = 0; i < 150; i++) {
            leaving.tickDespawnWindow(false, null); // no player: the window counts up
        }
        assertTrue(leaving.farFromPlayerTicks() >= 100, "the despawn window is armed");
        for (int i = 0; i < 2000; i++) {
            leaving.tick();
            assertFalse(leaving.mode() == MobEntity.Mode.WANDER && leaving.isStrollActive(),
                    "the despawning body never starts a stroll");
        }
    }

    @Test
    void theStrollWalkRunsToThePathsOwnEnd() {
        FavorWorld world = darkEastWorld();
        MobEntity zombie = mob(world, MobType.ZOMBIE, 7L);
        int ticks = 0;
        while (zombie.mode() != MobEntity.Mode.WANDER && ticks < 2000) {
            zombie.tick();
            ticks++;
        }
        assertTrue(zombie.isStrollActive(), "the stroll owns the WANDER band");
        // shouldContinue: the walk's life is the navigation's own — the
        // body walks the picked target to its end, then stands down.
        int walked = 0;
        while (zombie.mode() == MobEntity.Mode.WANDER && walked < 4000) {
            zombie.tick();
            walked++;
        }
        assertFalse(zombie.isStrollActive(), "the stroll ended at the path's end");
        assertEquals(MobEntity.Mode.IDLE, zombie.mode(),
                "the body stood back down (the stroll's own shouldContinue)");
    }
}
