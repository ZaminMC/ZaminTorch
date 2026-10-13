package net.zaminmc.torch.server;

import net.zaminmc.torch.server.weather.LightningSystem;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lightning family's own law (Slice 20c — LightningBoltEntity 1:1):
 * the constructor's fire walk, the two-tick damage window, the thunder
 * sound pair, the trailing fire attempts, and the bolt's own death.
 */
class LightningSystemTest {

    /** A recording body (Entity.struckByLightning's stand-in). */
    private static final class TestBody implements LightningSystem.StruckBody {
        final Position at;
        int strikes;

        TestBody(Position at) {
            this.at = at;
        }

        @Override
        public Position position() {
            return at;
        }

        @Override
        public void strike() {
            strikes++;
        }
    }

    /** The scripted surface: the fire placements + the body set. */
    private static final class TestSurface implements LightningSystem.Surface {
        final List<String> fires = new ArrayList<>();
        final List<TestBody> bodies = new ArrayList<>();
        boolean solidGround = true;

        @Override
        public int precipitationHeight(int x, int z) {
            return 5;
        }

        @Override
        public boolean isAirAt(int x, int y, int z) {
            return y > 4; // everything above the ground plane is air
        }

        @Override
        public boolean isSolidAt(int x, int y, int z) {
            return solidGround && y == 4;
        }

        @Override
        public void placeFireAt(int x, int y, int z) {
            fires.add(x + "," + y + "," + z);
        }

        @Override
        public List<LightningSystem.StruckBody> skyVisibleBodies(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            List<LightningSystem.StruckBody> in = new ArrayList<>();
            for (TestBody body : bodies) {
                double x = body.at.x(), y = body.at.y(), z = body.at.z();
                if (x >= minX && x <= maxX && y >= minY && y <= maxY
                        && z >= minZ && z <= maxZ) {
                    in.add(body);
                }
            }
            return in;
        }
    }

    private static final class Recording implements LightningSystem.Listener {
        final List<String> spawns = new ArrayList<>();
        final List<float[]> thunders = new ArrayList<>();

        @Override
        public void onBoltStruck(int entityId, double x, double y, double z) {
            spawns.add(entityId + "@" + x + "," + y + "," + z);
        }

        @Override
        public void onThunderPair(double x, double y, double z,
                                  float thunderPitch, float explodePitch) {
            thunders.add(new float[] {thunderPitch, explodePitch});
        }
    }

    @Test
    void theStrikeSpawnsTheWireBodyAndWalksTheConstructorFire() {
        TestSurface surface = new TestSurface();
        Recording wire = new Recording();
        LightningSystem lightning = new LightningSystem(surface, new Random(1L), 1000);
        lightning.addListener(wire);

        lightning.strike(10, 5.0, 12, true);
        assertEquals(1, wire.spawns.size(), "the wire learned the bolt");
        assertEquals("1000@10.0,5.0,12.0", wire.spawns.get(0),
                "the global-entity spawn carries the id + the position");
        assertEquals(1, lightning.liveBolts());
        // The constructor's fire walk (lines 48-59): the strike cell (10,5,12)
        // — air above the solid plane — plus up to four ±1 box attempts.
        assertTrue(surface.fires.contains("10,5,12"),
                "the strike cell ignites: " + surface.fires);
        // A supportless strike never ignites (FIRE.canBePlaced).
        surface.solidGround = false;
        surface.fires.clear();
        lightning.strike(100, 5.0, 100, true);
        assertTrue(surface.fires.isEmpty(), "no support, no fire");
    }

    @Test
    void theTwoTickDamageWindowAndTheThunderPair() {
        TestSurface surface = new TestSurface();
        Recording wire = new Recording();
        LightningSystem lightning = new LightningSystem(surface, new Random(2L), 2000);
        lightning.addListener(wire);
        TestBody atTheBolt = new TestBody(new Position(10.5, 5.0, 12.5));
        TestBody besideTheBolt = new TestBody(new Position(12.5, 6.0, 12.5));
        TestBody farAway = new TestBody(new Position(40.0, 5.0, 40.0));
        surface.bodies.add(atTheBolt);
        surface.bodies.add(besideTheBolt);
        surface.bodies.add(farAway);

        lightning.strike(10, 5.0, 12, false);
        // The constructor never wounds (the damage rides the tick's
        // stage >= 0 window only).
        assertEquals(0, atTheBolt.strikes);

        // Tick 1 (stage 2 -> 1): the thunder pair plays, the box strikes.
        lightning.tick();
        assertEquals(1, wire.thunders.size(), "the sound pair on the first tick");
        float[] pair = wire.thunders.get(0);
        assertTrue(pair[0] >= 0.8f && pair[0] <= 1.0f, "the thunder pitch band");
        assertTrue(pair[1] >= 0.5f && pair[1] <= 0.7f, "the explode pitch band");
        assertEquals(1, atTheBolt.strikes, "the body at the bolt wounds");
        assertEquals(1, besideTheBolt.strikes, "the body within the 3-wide box");
        assertEquals(0, farAway.strikes, "the far body never wounds");

        // Tick 2 (stage 1 -> 0): still in the damage window (>= 0).
        lightning.tick();
        assertEquals(2, atTheBolt.strikes, "the second tick's strike");
        assertEquals(1, wire.thunders.size(), "the pair plays once");

        // Tick 3 (stage 0 -> -1): the window closes — UNLESS the fire
        // walk's re-arm fires the same tick (the reference's own quirk:
        // a lucky {@code stage < -nextInt(10)} roll resets the stage to 1
        // and the damage window RE-OPENS — lines 100-108 run before the
        // damage gate). Both outcomes are the reference's law.
        lightning.tick();
        assertTrue(atTheBolt.strikes == 2 || atTheBolt.strikes == 3,
                "the closed window or the re-arm's re-opened one: " + atTheBolt.strikes);
        assertEquals(1, wire.thunders.size(), "the pair still plays once");
        assertEquals(0, farAway.strikes, "the far body never wounds");
    }

    @Test
    void theBoltDiesWhenTheFireAttemptsDrain() {
        TestSurface surface = new TestSurface();
        LightningSystem lightning = new LightningSystem(surface, new Random(3L), 3000);
        lightning.strike(0, 5.0, 0, false);
        // The trailing walk (lines 96-112): the fireAttempts (1..3) re-arm
        // the stage over the -nextInt(10) gates; once they drain the bolt
        // leaves. A hundred ticks is far past any 1..3-attempt walk.
        for (int i = 0; i < 100; i++) {
            lightning.tick();
        }
        assertEquals(0, lightning.liveBolts(), "the bolt finished its life");
        // The wire id space advances (no bolt ever reuses an id).
        lightning.strike(0, 5.0, 0, false);
        assertEquals(1, lightning.liveBolts());
    }

    @Test
    void theRollOnlyFiresWhileBothFlagsRide() {
        TestSurface surface = new TestSurface();
        Recording wire = new Recording();
        LightningSystem lightning = new LightningSystem(surface, new Random(5L), 4000);
        lightning.addListener(wire);
        // A hundred thousand clear-sky rolls: the stream advances (the
        // reference consumes the roll first) but nothing strikes.
        for (int i = 0; i < 100_000; i++) {
            lightning.tryStrikeAtChunk(0, 0, false, false);
        }
        assertEquals(0, wire.spawns.size(), "the clear sky never strikes");
        // The raining-but-not-thundering state: the same quiet.
        for (int i = 0; i < 100_000; i++) {
            lightning.tryStrikeAtChunk(0, 0, true, false);
        }
        assertEquals(0, wire.spawns.size(), "rain alone never strikes");
    }
}
