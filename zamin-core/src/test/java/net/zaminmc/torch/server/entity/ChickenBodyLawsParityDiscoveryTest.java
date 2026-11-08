package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave CE — THE CHICKEN'S OWN BODY LAWS (ChickenEntity.mobTick 1:1):
 *
 * <p>THE EGG LAYER (lines 76-81): the adult non-jockey chicken's clock
 * counts down every living tick — the reference's own short-circuit shape
 * ({@code !isClient && !isBaby() && !hasJockey() && --layEggCooldown <= 0}
 * — a baby's or a jockey's clock FROZEN, never decremented); the lay pays
 * the plop ("mob.chicken.plop", 1.0F, the two-draw pitch jitter
 * {@code (nextFloat - nextFloat) * 0.2 + 1.0}) and drops ONE egg at the
 * body's feet (+0.5, the reference's own dropItem anchor), the clock
 * re-rolling its 6000-12000 band. THE NBT PAIR: EggLayTime reads only
 * when present (an absent row keeps the ctor's roll), IsChickenJockey
 * reads ungated (absent is false).</p>
 *
 * <p>THE SLOW FALL (lines 73-75): an airborne falling chicken dampens its
 * descent 60% per tick before the gravity arm — the famous feather fall
 * (the steady state ~0.095/tick against the ordinary body's ~1.96), and
 * takeFallDamage's EMPTY override carries the landing's immunity whole
 * (the empty-override family the engine already lands).</p>
 */
class ChickenBodyLawsParityDiscoveryTest {

    /** The captured drop pair (the item and its spawn position). */
    private record Dropped(Position position, String item, int count, int dimension) {
    }

    /** The captured sound triple. */
    private record Sounded(String name, float volume, float pitch) {
    }

    /** A flat-world stub with the loot/sound capture. */
    private static final class FlatWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            // Solid below y=64 only: a long fall window for the slow-fall
            // seats, a flat ground for the egg seats.
            return Math.floor(y) < 64.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null; // no players in these seats
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return false;
        }

        @Override
        public List<MobEntity> hostileMobsInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            return List.of();
        }
    }

    private static final class Capture {
        final ConcurrentLinkedQueue<Dropped> drops = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Sounded> sounds = new ConcurrentLinkedQueue<>();
    }

    private static MobManager managerIn(FlatWorld world, Capture capture, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) {
                capture.drops.add(new Dropped(position,
                        stack.type().identifier().toString(), stack.count(),
                        dimension));
            }
        }
        // The no-op base over every abstract arm (the seat captures the
        // sound family alone).
        class SoundListener extends net.zaminmc.torch.server.entity.NoOpMobListener {
            @Override public void onMobSoundAt(MobEntity mob, String name,
                    float volume, float pitch) {
                capture.sounds.add(new Sounded(name, volume, pitch));
            }
        }
        MobManager manager = new MobManager(world, new Random(seed), new NoLoot(),
                996_500, (x, z) -> 64);
        manager.addListener(new SoundListener());
        return manager;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the lay itself: the plop + ONE egg + the re-roll's floor
    // ------------------------------------------------------------------

    /**
     * THE LAY: the clock stamped to 1, the next living tick pays the plop
     * (1.0 volume, the two-draw jitter band [0.8, 1.2]) and ONE egg at
     * the body's feet (+0.5) — and the re-roll's 6000 floor keeps the
     * next 200 ticks egg-less (the band's minimum stands five minutes
     * out).
     */
    @Test
    void theLayPaysThePlopAndOneEggAtTheFeet() {
        FlatWorld world = new FlatWorld();
        Capture capture = new Capture();
        MobManager manager = managerIn(world, capture, 60L);
        MobEntity chicken = manager.spawnAt(MobType.CHICKEN,
                new Position(5.5, 64.0, 5.5));
        assert chicken != null : "the spawn never cancels here";
        world.mobs.add(chicken);
        chicken.setEggLayTime(1);

        manager.tick(List.of(), 6000L);
        assertEquals(1, capture.drops.size(), "ONE egg per lay");
        Dropped egg = capture.drops.poll();
        assertEquals("minecraft:egg", egg.item());
        assertEquals(1, egg.count());
        assertEquals(5.5, egg.position().x(), 1e-9, "the egg at the body's x");
        assertEquals(64.5, egg.position().y(), 1e-9,
                "the egg at the body's feet +0.5 (the dropItem anchor)");
        assertEquals(5.5, egg.position().z(), 1e-9);
        assertEquals(0, egg.dimension());
        assertEquals(1, capture.sounds.size(), "the plop beside the drop");
        Sounded plop = capture.sounds.poll();
        assertEquals("mob.chicken.plop", plop.name());
        assertEquals(1.0f, plop.volume(), 1e-9);
        assertTrue(plop.pitch() >= 0.8f && plop.pitch() <= 1.2f,
                "the two-draw jitter band [0.8, 1.2]: " + plop.pitch());

        // The re-roll's floor: the 6000-minimum band keeps 200 ticks
        // egg-less (whatever the roll drew, its floor stands far out).
        // The ambient clucks keep their own cadence (the manager's
        // ambient family — a legitimate sound beside the plop), so the
        // window pins the PLOP alone.
        capture.drops.clear();
        capture.sounds.clear();
        for (int i = 0; i < 200; i++) {
            manager.tick(List.of(), 6000L);
        }
        assertTrue(capture.drops.isEmpty(),
                "the re-rolled clock's 6000 floor (no second lay inside"
                        + " 200 ticks)");
        assertTrue(capture.sounds.stream().noneMatch(
                        sound -> sound.name().equals("mob.chicken.plop")),
                "no second plop inside the window (the ambient clucks"
                        + " alone may sound)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the gates: the baby's and the jockey's frozen clock
    // ------------------------------------------------------------------

    /**
     * THE GATES' OWN SHAPE (the reference's short-circuit: the decrement
     * runs ONLY past the gates — a refused body's clock FROZEN, never
     * spent): a BABY's clock never counts (and the clock it froze at 1
     * lays the very tick it grows up), and a JOCKEY's clock never counts
     * either (the rider's mount never lays).
     */
    @Test
    void theBabyAndTheJockeyFreezeTheClock() {
        // The baby: the clock frozen at 1, no lay over 20 ticks; the
        // grown-up tick lays at once.
        FlatWorld babyWorld = new FlatWorld();
        Capture babyCapture = new Capture();
        MobManager babyManager = managerIn(babyWorld, babyCapture, 61L);
        MobEntity baby = babyManager.spawnAt(MobType.CHICKEN,
                new Position(5.5, 64.0, 5.5));
        assert baby != null : "the spawn never cancels here";
        babyWorld.mobs.add(baby);
        baby.setBreedingAge(-24000);
        baby.setEggLayTime(1);
        for (int i = 0; i < 20; i++) {
            babyManager.tick(List.of(), 6000L);
        }
        assertTrue(babyCapture.drops.isEmpty(),
                "the baby's clock never counts (the short-circuit)");
        baby.setBreedingAge(0); // grown
        babyManager.tick(List.of(), 6000L);
        assertEquals(1, babyCapture.drops.size(),
                "the frozen clock spends its very first adult tick");

        // The jockey: the clock frozen whole.
        FlatWorld jockeyWorld = new FlatWorld();
        Capture jockeyCapture = new Capture();
        MobManager jockeyManager = managerIn(jockeyWorld, jockeyCapture, 62L);
        MobEntity jockey = jockeyManager.spawnAt(MobType.CHICKEN,
                new Position(5.5, 64.0, 5.5));
        assert jockey != null : "the spawn never cancels here";
        jockeyWorld.mobs.add(jockey);
        jockey.setChickenJockey(true);
        jockey.setEggLayTime(1);
        for (int i = 0; i < 20; i++) {
            jockeyManager.tick(List.of(), 6000L);
        }
        assertTrue(jockeyCapture.drops.isEmpty(),
                "the jockey's clock never counts");
        assertTrue(jockey.hasChickenJockey(), "the mark stands (the NBT row)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the slow fall (the 60% descent dampening)
    // ------------------------------------------------------------------

    /**
     * THE SLOW FALL (lines 73-75): an airborne falling chicken dampens
     * its descent 60% per tick before the gravity arm — the measured
     * steady state ~0.095/tick against the cow's ~1.96 — and the landing
     * from a 20-block drop never wounds the body (takeFallDamage's empty
     * override, the empty-override family's own member).
     */
    @Test
    void theFallingChickenDampensItsDescentAndLandsUnwounded() {
        FlatWorld world = new FlatWorld();
        Capture capture = new Capture();
        MobManager manager = managerIn(world, capture, 63L);
        MobEntity chicken = manager.spawnAt(MobType.CHICKEN,
                new Position(5.5, 84.0, 5.5));
        MobEntity cow = manager.spawnAt(MobType.COW,
                new Position(8.5, 84.0, 5.5));
        assert chicken != null && cow != null : "the spawns never cancel here";
        world.mobs.add(chicken);
        world.mobs.add(cow);
        float chickenHealth = chicken.health();

        double chickenDescent = 0.0;
        double cowDescent = 0.0;
        int measured = 0;
        for (int i = 0; i < 40; i++) {
            manager.tick(List.of(), 6000L);
            if (i >= 10) { // past the spin-up, the steady states stand
                chickenDescent = 84.0 - chicken.position().y();
                cowDescent = 84.0 - cow.position().y();
                measured++;
            }
        }
        assertTrue(measured > 0, "the window measured");
        // The chicken's steady descent < 0.2/tick (the dampened band);
        // the cow's > 1.0/tick (the ordinary gravity).
        assertTrue(chickenDescent / measured < 0.2,
                "the chicken's dampened descent (measured "
                        + chickenDescent / measured + "/tick)");
        assertTrue(cowDescent / measured > 0.4,
                "the cow's ordinary descent (measured "
                        + cowDescent / measured + "/tick)");
        assertTrue(cowDescent > 3.0 * chickenDescent,
                "the ordinary body falls at least triple the dampened"
                        + " chicken's rate (the 60% arm's own signature)");

        // The landing's immunity: run the chicken to the ground from 20
        // blocks (its dampened descent makes the debt tiny anyway) and
        // pin the unwounded landing.
        for (int i = 0; i < 400 && !chicken.onGround(); i++) {
            manager.tick(List.of(), 6000L);
        }
        assertTrue(chicken.onGround(), "the chicken landed");
        assertEquals(chickenHealth, chicken.health(), 1e-9,
                "the landing never wounds (takeFallDamage's empty override)");
    }
}
