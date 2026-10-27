package net.zaminmc.torch.server.fishing;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fishing system's own law (Slice 67 — the reference port of
 * FishingBobberEntity + FishingRodItem): the cast's launch, the water
 * float, the catchable cycle's approach/splash/bite-window walk, the
 * retrieve's results, and the loot tables' weights.
 */
class FishingSystemTest {

    /** The all-water world (a flat ocean). */
    private static final class WaterWorld implements FishingSystem.WorldQuery {
        @Override public Position blockHit(Position from, Position to) {
            return null; // water never grounds the bobber
        }

        @Override public double waterFraction(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
            return 1.0; // fully submerged
        }

        @Override public boolean hasSkyAccess(int x, int y, int z) {
            return true;
        }

        @Override public boolean raining() {
            return false;
        }
    }

    /** The flat dry world (a grounded cast). */
    private static final class DryWorld implements FishingSystem.WorldQuery {
        @Override public Position blockHit(Position from, Position to) {
            // The first segment end below y 40 grounds the bobber.
            if (to.y() < 40.0) {
                return new Position(to.x(), 40.0, to.z());
            }
            return null;
        }

        @Override public double waterFraction(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
            return 0.0;
        }

        @Override public boolean hasSkyAccess(int x, int y, int z) {
            return true;
        }

        @Override public boolean raining() {
            return false;
        }
    }

    /** The recording sinks. */
    private static final class Recording implements FishingSystem.HookResolver,
            FishingSystem.CatchSink {
        final List<String> catches = new ArrayList<>();
        final List<Integer> xps = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        final List<double[]> pulls = new ArrayList<>();
        Object hookBody;
        boolean hookAlive = true;

        @Override public Object hookAlong(Position from, Position to, int throwerId,
                                          int airTicks) {
            return hookBody;
        }

        @Override public boolean hookedAlive(Object hooked) {
            return hookAlive;
        }

        @Override public Position hookedAnchor(Object hooked) {
            return new Position(50.0, 64.0, 50.0);
        }

        @Override public void pullToward(Object hooked, double dx, double dy, double dz) {
            pulls.add(new double[]{dx, dy, dz});
        }

        @Override public void dropCatch(Position at, net.zaminmc.torch.item.ItemStack stack,
                                        double anglerX, double anglerY, double anglerZ,
                                        int dimension) {
            catches.add(stack.type().identifier() + ":" + stack.damage());
        }

        @Override public void dropXp(Position at, int amount) {
            xps.add(amount);
        }

        @Override public void sound(Position at, String key, float volume, float pitch) {
            sounds.add(key);
        }
    }

    @Test
    void theCastLaunchesFromTheEyePulledBackAlongTheLook() {
        // The ctor + thrown lines 107-155: the 0.4-power direction scaled
        // 1.5 — the speed lands at 1.5 (the gaussian scatter aside).
        FishingSystem fishing = new FishingSystem(new WaterWorld(), new Recording(),
                new Recording(), new Random(101L), 10_000);
        FishingSystem.Bobber bobber = fishing.cast(7, new Position(0.0, 65.62, 0.0),
                0.0f, -45.0f); // looking north-down
        assertNotNull(bobber);
        assertEquals(7, bobber.throwerId());
        double speed = Math.sqrt(bobber.velocityX() * bobber.velocityX()
                + bobber.velocityY() * bobber.velocityY()
                + bobber.velocityZ() * bobber.velocityZ());
        assertTrue(speed > 1.4 && speed < 1.6, "the 1.5-scaled launch: " + speed);
        assertTrue(bobber.velocityZ() > 0.0, "yaw 0 faces south (+Z — Minecraft's own convention)");
        assertTrue(bobber.velocityY() > 0.0, "pitch -45 looks up (+Y - Minecraft pitches negative-up)");
        // One bobber per angler.
        assertEquals(bobber, fishing.cast(7, new Position(0.0, 65.62, 0.0), 0.0f, 0.0f));
    }

    @Test
    void theCycleArmsApproachesSplashesAndOpensTheBiteWindow() {
        // tickCatchCycle lines 322-430: the arming (100..900), the fish
        // approach (20..80), the splash (the sound + the vy bob + the
        // 10..30 bite window).
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(102L), 10_100);
        fishing.cast(8, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f); // straight down
        FishingSystem.Bobber bobber = fishing.bobberOf(8);
        // Force the arming to the shortest approach: the package-private
        // catchTimer is the cycle's own state.
        bobber.catchTimer = 1;
        int splashAt = -1;
        for (int i = 0; i < 200 && splashAt < 0; i++) {
            fishing.tick();
            if (bobber.selfHitTimer > 0) {
                splashAt = i;
            }
        }
        assertTrue(splashAt >= 0, "the bite window opened (the approach + the splash)");
        assertTrue(sink.sounds.stream().anyMatch("random.splash"::equals),
                "the splash sound fired");
        assertTrue(bobber.selfHitTimer >= FishingSystem.BITE_WINDOW_MIN
                        && bobber.selfHitTimer <= FishingSystem.BITE_WINDOW_MAX,
                "the bite window's 10..30 band: " + bobber.selfHitTimer);
        // The retrieve inside the window: a catch (result 1) + the XP.
        int result = fishing.retrieve(8, new Position(0.0, 66.0, 0.0), 0);
        assertEquals(1, result);
        assertEquals(1, sink.catches.size(), "the loot dropped");
        assertEquals(1, sink.xps.size(), "the XP orb dropped");
        assertTrue(sink.xps.get(0) >= 1 && sink.xps.get(0) <= 6,
                "the nextInt(6)+1 band: " + sink.xps.get(0));
        assertNull(fishing.bobberOf(8), "the retrieve removed the bobber");
    }

    @Test
    void theRetrieveOutsideTheBiteWindowCatchesNothing() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(103L), 10_200);
        fishing.cast(9, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f);
        FishingSystem.Bobber bobber = fishing.bobberOf(9);
        bobber.catchTimer = 10_000; // the approach never comes
        fishing.tick();
        assertEquals(0, fishing.retrieve(9, new Position(0.0, 66.0, 0.0), 0),
                "the bare water retrieve: result 0, no wear");
        assertTrue(sink.catches.isEmpty(), "nothing dropped");
        assertTrue(sink.xps.isEmpty());
    }

    @Test
    void theGroundedBobberAnswersTwoAndTheHookedBodyDrags() {
        Recording sink = new Recording();
        // The hooked arm: the hook body holds a live mob.
        sink.hookBody = new Object();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(104L), 10_300);
        fishing.cast(10, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f);
        FishingSystem.Bobber bobber = fishing.bobberOf(10);
        bobber.hooked = sink.hookBody;
        fishing.tick();
        assertEquals(new Position(50.0, 64.0, 50.0), bobber.position(),
                "the hooked follow rides the body's anchor");
        assertEquals(3, fishing.retrieve(10, new Position(51.0, 65.0, 50.0), 0),
                "the hooked drag: result 3");
        assertEquals(1, sink.pulls.size(), "the pull fired");
        // The grounded arm (a fresh recording: the hooked body above must
        // not hook the dry cast).
        Recording drySink = new Recording();
        FishingSystem dry = new FishingSystem(new DryWorld(), drySink, drySink,
                new Random(105L), 10_400);
        dry.cast(11, new Position(0.0, 45.0, 0.0), 0.0f, -90.0f);
        FishingSystem.Bobber grounded = dry.bobberOf(11);
        for (int i = 0; i < 100 && !grounded.grounded(); i++) {
            dry.tick();
        }
        assertTrue(grounded.grounded(), "the dry world grounded the bobber");
        assertEquals(2, dry.retrieve(11, new Position(0.0, 45.0, 0.0), 0),
                "the grounded answer: result 2");
    }

    @Test
    void theAnglerStateGateEndsTheCast() {
        // tick lines 181-189: the angler gone, the rod un-held, or the
        // leash broken removes the bobber.
        FishingSystem fishing = new FishingSystem(new WaterWorld(), new Recording(),
                new Recording(), new Random(106L), 10_500);
        fishing.cast(12, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f);
        fishing.noteAnglerState(12, true, false, new Position(0.0, 65.0, 0.0));
        assertNull(fishing.bobberOf(12), "the un-held rod removed the bobber");

        fishing.cast(13, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f);
        fishing.noteAnglerState(13, true, true, new Position(100.0, 65.0, 0.0));
        assertNull(fishing.bobberOf(13), "the 32-block leash removed the bobber");
    }

    @Test
    void theLootTablesRollTheReferenceWeights() {
        // The statistical shape: with zero enchant levels, junk ~10%,
        // treasure ~5%, fish ~85% — the fish table's own cod/salmon/
        // pufferfish/clownfish split 60/25/13/2.
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(107L), 10_600);
        int fish = 0;
        int cod = 0;
        int salmon = 0;
        int puffer = 0;
        int clown = 0;
        int junk = 0;
        int treasure = 0;
        int leather = 0;
        int bowls = 0;
        for (int i = 0; i < 4000; i++) {
            fishing.cast(20, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f);
            FishingSystem.Bobber bobber = fishing.bobberOf(20);
            bobber.selfHitTimer = 5; // force the bite window
            fishing.retrieve(20, new Position(0.0, 66.0, 0.0), 0);
        }
        for (String entry : sink.catches) {
            // "minecraft:fish:0" — the damage rides the LAST colon (the
            // namespaced identifier carries its own).
            String name = entry.substring(0, entry.lastIndexOf(':'));
            int damage = Integer.parseInt(entry.substring(entry.lastIndexOf(':') + 1));
            switch (name) {
                case "minecraft:fish" -> {
                    fish++;
                    switch (damage) {
                        case 0 -> cod++;
                        case 1 -> salmon++;
                        case 2 -> clown++;
                        case 3 -> puffer++;
                        default -> throw new AssertionError("bad fish damage: " + damage);
                    }
                }
                case "minecraft:leather", "minecraft:bone", "minecraft:potion",
                     "minecraft:string", "minecraft:bowl", "minecraft:stick",
                     "minecraft:dye", "minecraft:tripwire_hook",
                     "minecraft:rotten_flesh", "minecraft:leather_boots",
                     "minecraft:fishing_rod" -> junk++;
                default -> treasure++;
            }
            if (name.equals("minecraft:leather")) {
                leather++;
            }
            if (name.equals("minecraft:bowl")) {
                bowls++;
            }
        }
        assertEquals(4000, sink.catches.size());
        // The bands (±3% tolerance for the 4000-sample roll).
        assertTrue(fish > 0.82 * 4000 && fish < 0.88 * 4000,
                "the fish share ~85%: " + fish);
        assertTrue(junk > 0.07 * 4000 && junk < 0.13 * 4000,
                "the junk share ~10%: " + junk);
        assertTrue(treasure > 0.02 * 4000 && treasure < 0.08 * 4000,
                "the treasure share ~5%: " + treasure);
        // The fish table's own split: 60/25/2/13 of the fish rolls.
        assertTrue(cod > 0.55 * fish && cod < 0.65 * fish, "cod ~60%: " + cod);
        assertTrue(salmon > 0.20 * fish && salmon < 0.30 * fish, "salmon ~25%: " + salmon);
        assertTrue(puffer > 0.08 * fish && puffer < 0.18 * fish, "pufferfish ~13%: " + puffer);
        assertTrue(clown <= 0.06 * fish, "clownfish ~2%: " + clown);
        // The junk table's own weights: leather 10/83 vs bowl 10/83 — the
        // equal-weight pair lands within the tolerance.
        assertTrue(Math.abs(leather - bowls) < 0.15 * (leather + bowls + 1),
                "the equal-weight junk pair (both 10/83): " + leather + " vs " + bowls);
    }

    @Test
    void theLureEnchantCutsTheApproach() {
        // The arming (lines 424-430): 100..900 minus Lure * 100.
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(108L), 10_700);
        fishing.setEnchantReads(id -> 0, id -> 3); // Lure III
        fishing.cast(21, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f);
        FishingSystem.Bobber bobber = fishing.bobberOf(21);
        bobber.catchTimer = 0; // force the arming
        fishing.tick();
        assertTrue(bobber.catchTimer >= 0,
                "the Lure III cut keeps the timer non-negative: " + bobber.catchTimer);
        assertTrue(bobber.catchTimer <= FishingSystem.CATCH_ARM_MAX - 300,
                "the cut landed: " + bobber.catchTimer);
    }
}
