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
        /** Every TARGET-walk consult's own box (Wave PE-4's band arm). */
        final List<String> targetConsults = new ArrayList<>();
        /** Every STRIKE-walk consult's own box (Wave PE-4's band arm). */
        final List<String> strikeConsults = new ArrayList<>();
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
        public int worldHeight() {
            return 256; // the 1.8 build band (the scripted world's own)
        }

        @Override
        public void placeFireAt(int x, int y, int z) {
            fires.add(x + "," + y + "," + z);
        }

        @Override
        public List<LightningSystem.StruckBody> skyVisibleBodies(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            // Wave PE-4's band arm: the consult's own box recorded —
            // the system's query is itself the parity surface (the
            // scripted bodies carry no boxes; the ENGINE's own walk is
            // where the strict own-box law lives, the full-boot seat's).
            targetConsults.add(minX + "," + minY + "," + minZ + ","
                    + maxX + "," + maxY + "," + maxZ);
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

        @Override
        public List<LightningSystem.StruckBody> bodiesInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            // The scripted strike walk — the same center-band form as the
            // scripted target walk (the scripted bodies carry no boxes;
            // the ENGINE's own surface is where the strict own-box law
            // lives, exercised by the full-boot parity seats).
            strikeConsults.add(minX + "," + minY + "," + minZ + ","
                    + maxX + "," + maxY + "," + maxZ);
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

    /**
     * The forced roll (Wave PE-4's band arms): the storm's own
     * 1-in-100000 gate answering zero every time — the target walk
     * consulted deterministically, every other draw the seeded stream's
     * own.
     */
    private static final class ForcedRollRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return bound == 100000 ? 0 : super.nextInt(bound);
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

    /**
     * Wave PE-4 — THE TARGET BOX'S OWN BAND (findLightningTarget
     * line 372: {@code Box(blockpos, BlockPos(x, getHeight(),
     * z)).grown(3.0, 3.0, 3.0)} — the degenerate column line grown ±3
     * on every axis, the ceiling at the WORLD'S OWN HEIGHT + 3, never
     * the surface + 10): the consult's recorded box is exactly
     * [cell-3, cell+3] x [surface-3, worldHeight+3] x [cell-3,
     * cell+3]. Pre-fix the east/south face rode the CELL'S OWN +1 (a
     * half-block phantom east of vanilla's box) and the ceiling the
     * surface's +10: RED pre-fix on both columns.
     */
    @Test
    void theTargetSelectionBoxIsTheColumnGrownThreeToTheWorldHeight() {
        TestSurface surface = new TestSurface();
        Recording wire = new Recording();
        LightningSystem lightning = new LightningSystem(surface, new ForcedRollRandom(), 5000);
        lightning.addListener(wire);

        lightning.tryStrikeAtChunk(8, 8, true, true);
        // The LCG's own cell derivation (the same arithmetic the
        // system walks — the shared stream's deterministic pick).
        int pick = (0 * 3 + 1013904223) >> 2;
        int cellX = 8 * 16 + (pick & 15);
        int cellZ = 8 * 16 + (pick >> 8 & 15);
        assertEquals(1, surface.targetConsults.size(),
                "the target walk consulted exactly once");
        assertEquals((cellX - 3.0) + "," + (5 - 3.0) + "," + (cellZ - 3.0) + ","
                        + (cellX + 3.0) + "," + (256 + 3.0) + "," + (cellZ + 3.0),
                surface.targetConsults.get(0),
                "the recorded box: the column line grown ±3, the ceiling the "
                        + "world's own height + 3");
        // No bodies in the band: the bolt at the precipitation cell
        // itself (the scripted surface's own height 5).
        assertEquals(1, wire.spawns.size());
        assertTrue(wire.spawns.get(0).endsWith("@" + cellX + ".0,5.0," + cellZ + ".0"),
                "the empty census lands the bolt at the precipitation cell: "
                        + wire.spawns.get(0));
    }

    /**
     * Wave PE-4 — THE PHANTOM-EAST BODY never the target: a body at
     * cellX + 3.6 — inside the pre-fix engine's phantom [cell-3,
     * cell+4] band but OUTSIDE the reference's own ±3 — is never a
     * candidate; a body a hair inside the real band (cellX + 2.9) is
     * the pick, the bolt landing at ITS position (the chosen
     * candidate's own cell — getCommandSourceBlockPos's floor). RED
     * pre-fix (the phantom band picked the 3.6 body whole).
     */
    @Test
    void thePhantomEastBodyIsNeverTheTargetAndTheInBandBodyIs() {
        TestSurface surface = new TestSurface();
        Recording wire = new Recording();
        LightningSystem lightning = new LightningSystem(surface, new ForcedRollRandom(), 6000);
        lightning.addListener(wire);

        // The phantom-east body alone: never picked — the bolt stays
        // at the precipitation cell (the first fired strike's LCG
        // step derives from the initial state).
        surface.bodies.add(new TestBody(new Position(cellOf(0, 8, true) + 3.6, 5.0,
                cellOf(0, 8, false) + 0.5)));
        lightning.tryStrikeAtChunk(8, 8, true, true);
        assertEquals(1, wire.spawns.size());
        assertTrue(wire.spawns.get(0).endsWith(
                        "@" + cellOf(0, 8, true) + ".0,5.0," + cellOf(0, 8, false) + ".0"),
                "the phantom-east body (cellX+3.6, past the reference's ±3) "
                        + "never the target: " + wire.spawns.get(0));

        // The in-band body: the pick — the bolt at ITS position (the
        // second fired strike's LCG step — the shared stream's own
        // walk; the chosen candidate's own cell, floor(chosen.x)).
        surface.bodies.clear();
        int secondLcg = 0 * 3 + 1013904223;
        surface.bodies.add(new TestBody(new Position(cellOf(secondLcg, 8, true) + 2.9, 5.0,
                cellOf(secondLcg, 8, false) + 0.5)));
        lightning.tryStrikeAtChunk(8, 8, true, true);
        assertEquals(2, wire.spawns.size());
        assertTrue(wire.spawns.get(1).endsWith(
                        "@" + (cellOf(secondLcg, 8, true) + 2) + ".0,5.0,"
                                + cellOf(secondLcg, 8, false) + ".0"),
                "the in-band body (cellX+2.9) is the pick, the bolt at its "
                        + "own cell: " + wire.spawns.get(1));
    }

    /** The LCG's own in-chunk cell derivation (the test's own mirror). */
    private static int cellOf(int lcgBefore, int chunk, boolean xAxis) {
        int pick = (lcgBefore * 3 + 1013904223) >> 2;
        return chunk * 16 + (xAxis ? (pick & 15) : (pick >> 8 & 15));
    }

    /**
     * Wave PE-4 — THE TALL BODY stays a candidate: the reference's
     * ceiling is the WORLD'S OWN HEIGHT + 3 (a body on a tall tower
     * beside the strike cell — here y 250 against the surface 5 —
     * remains exactly the candidate vanilla keeps), never the
     * surface's +10. The bolt lands at the tall body's own position.
     * RED pre-fix (the surface+10 cap at 15 dropped the 250 body).
     */
    @Test
    void theTallBodyBesideTheCellStaysTheTarget() {
        TestSurface surface = new TestSurface();
        Recording wire = new Recording();
        LightningSystem lightning = new LightningSystem(surface, new ForcedRollRandom(), 7000);
        lightning.addListener(wire);
        int cellX = cellOf(0, 8, true);
        int cellZ = cellOf(0, 8, false);

        surface.bodies.add(new TestBody(new Position(cellX + 0.5, 250.0, cellZ + 0.5)));
        lightning.tryStrikeAtChunk(8, 8, true, true);
        assertEquals(1, wire.spawns.size());
        assertTrue(wire.spawns.get(0).endsWith("@" + cellX + ".0,250.0," + cellZ + ".0"),
                "the tall body (y 250, inside the world-height + 3 ceiling) is "
                        + "the pick: " + wire.spawns.get(0));
    }

    /**
     * Wave PE-4 — THE STRIKE WALK'S OWN BOX (LightningBoltEntity.tick
     * lines 84-90: {@code Box(x-3, y-3, z-3, x+3, y+6+3, z+3)}): the
     * consult's recorded box is the bolt's own ±3 with the +6+3
     * ceiling — its own surface consult, never the target walk's
     * (the split's own scripted pin; the sky gate and the census live
     * in the full-boot seat). RED pre-fix (the strike walk shared the
     * target walk's consult — the strike list empty whole).
     */
    @Test
    void theStrikeWalkConsultsItsOwnBox() {
        TestSurface surface = new TestSurface();
        LightningSystem lightning = new LightningSystem(surface, new Random(11L), 8000);

        lightning.strike(10, 5.0, 12, false);
        assertTrue(surface.strikeConsults.isEmpty(),
                "the constructor never consults the strike walk");
        lightning.tick();
        assertEquals(1, surface.strikeConsults.size(),
                "the first damage tick consults once");
        assertEquals("7.0,2.0,9.0,13.0,14.0,15.0",
                surface.strikeConsults.get(0),
                "the bolt's own box: [bolt-3, bolt+3] x [boltY-3, boltY+6+3]");
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
