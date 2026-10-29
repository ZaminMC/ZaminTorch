package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.experience.ExperienceOrbEntity;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item/orb families' lava envelope + the orb's igniter attraction
 * (Wave LF — the §22 named remainder's envelope tails, each arm read
 * against the reference before its fix):
 *
 * <p>THE ITEM'S FIZZ (ItemEntity.tick lines 78-87): the gate opens when
 * the move crossed a BLOCK BOUNDARY — {@code (int)lastX != (int)x ...},
 * the Java int CAST (truncation toward zero, NOT floor: -0.4 to +0.4
 * crosses cells -1 to 0 by floor but 0 to 0 by cast, the gate stays
 * DARK — the reference's own quirk) — or every 25th tick
 * ({@code ticks % 25 == 0}); open, the feet cell's lava POPS the body
 * ({@code velocityY = 0.2}, the horizontals re-rolled at
 * {@code (rand - rand) * 0.2}) and plays {@code random.fizz} at
 * 0.4 volume, pitch {@code 2.0 + rand * 0.4} — the pop lands BETWEEN
 * the move and the drag, so the dragged remainder is 0.196 vertical.</p>
 *
 * <p>THE ORB'S FIZZ (ExperienceOrbEntity.tick lines 69-74): UNLIKE the
 * item there is NO gate — every tick the feet cell's lava pops the body
 * BEFORE the move ({@code velocityY = 0.2} feeds THIS tick's rise), the
 * same pop law and the same sound.</p>
 *
 * <p>THE ORB'S ATTRACTION (ExperienceOrbEntity.tick lines 76-100): the
 * 8-block pull toward the nearest non-spectator player, the retarget
 * THROTTLED to every ~20 ticks staggered by {@code networkId % 100}
 * (a fresh orb holds NOTHING for its first ~21 ticks — the reference's
 * own {@code lastTargetUpdateTick = 0} start), the held igniter read
 * live every tick (a spectator RELEASES the hold), the pull itself
 * {@code velocity += dir/|d| * (1 - |d|)² * 0.1} aimed at the player's
 * EYE (1.62 standing, 1.54 sneaking — PlayerEntity.getEyeHeight lines
 * 1648-1655), nothing at or beyond 8 blocks.</p>
 */
class ItemOrbLavaEnvelopeParityTest {

    /** The recorded sound (the sink the seats drive). */
    private record Heard(Position at, String name, float volume, float pitch, int dimension) {
    }

    /** The recorder sink: collects every emission for the assertions. */
    private static final class RecordingSounds implements ItemEntity.Sounds,
            ExperienceOrbEntity.Sounds {
        final List<Heard> heard = new ArrayList<>();

        @Override
        public void sound(Position at, String name, float volume, float pitch, int dimension) {
            heard.add(new Heard(at, name, volume, pitch, dimension));
        }
    }

    /**
     * The item's lava-column ground: air cells everywhere (the body
     * falls freely), the lava consult answered by the y band — the
     * column the bobbing seat never leaves.
     */
    private static final class LavaColumnGround implements ItemEntity.Ground {
        final int lavaBottomY;
        final int lavaTopY;

        LavaColumnGround(int lavaBottomY, int lavaTopY) {
            this.lavaBottomY = lavaBottomY;
            this.lavaTopY = lavaTopY;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return false; // the air: the column never blocks
        }

        // No @Override (the stash-run discipline: a plain stub method on
        // the pre-wave tree).
        public boolean lavaAtCell(double centerX, double feetY, double centerZ) {
            int cellY = (int) Math.floor(feetY);
            return cellY >= lavaBottomY && cellY <= lavaTopY;
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public BlockType blockAt(int x, int y, int z) {
                        return net.zaminmc.torch.server.block.BuiltinBlocks.AIR;
                    }

                    @Override
                    public boolean chunkLoaded(int x, int z) {
                        return true;
                    }
                };

        @Override
        public net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    /** The orb's twin of the column ground (the fizz + attraction seats). */
    private static final class OrbAirGround implements ExperienceOrbEntity.Ground {
        final boolean lava;

        OrbAirGround(boolean lava) {
            this.lava = lava;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return false;
        }

        // No @Override (the stash-run discipline).
        public boolean lavaAtCell(double centerX, double feetY, double centerZ) {
            return lava;
        }
    }

    /** The single-player consult the attraction seats drive. */
    private static final class SinglePlayer implements ExperienceOrbEntity.Players {
        int entityId = 5;
        double x = 3.0;
        double y = 64.0;
        double z = 0.0;
        boolean spectator;
        boolean sneaking;
        int nearestCalls;

        @Override
        public ExperienceOrbEntity.Players.Target nearest(double x, double y, double z,
                                                          int dimension, double range) {
            nearestCalls++;
            return spectator ? null : view();
        }

        @Override
        public ExperienceOrbEntity.Players.Target byId(int entityId, int dimension) {
            return entityId == this.entityId ? view() : null;
        }

        private ExperienceOrbEntity.Players.Target view() {
            double eye = sneaking ? 1.54 : 1.62;
            return new ExperienceOrbEntity.Players.Target(
                    entityId, x, y, y + eye, z, spectator);
        }
    }

    // ------------------------------------------------------------------
    // The item's fizz (the gate's two arms + the int-cast quirk)
    // ------------------------------------------------------------------

    @Test
    void theItemFizzPopsOnTheBoundaryCrossing() {
        // The crossing arm alone opens the gate: an item crossing x=-1.4
        // to x=-0.6 (int cells -1 to 0) pops on the crossing tick — age 1,
        // NOT a 25th tick — the pop re-rolls the horizontals and the
        // vertical at 0.2, the sound at 0.4 volume.
        LavaColumnGround ground = new LavaColumnGround(55, 75);
        RecordingSounds sounds = new RecordingSounds();
        ItemEntity item = new ItemEntity(1, new Position(-1.4, 62.5, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(0.8, 0.0, 0.0);
        item.tick(ground, new Random(4), sounds);

        assertEquals(1, sounds.heard.size(), "the crossing tick pops");
        Heard heard = sounds.heard.get(0);
        assertEquals("random.fizz", heard.name());
        assertEquals(0.4f, heard.volume(), 1.0e-6f);
        assertTrue(heard.pitch() >= 2.0f && heard.pitch() <= 2.4f,
                "the pitch band 2.0 + rand * 0.4");
        assertEquals(0, heard.dimension());
        assertEquals(-0.6, item.position().x(), 1.0e-9, "the move itself");
        // The pop lands between the move and the drag: the dragged
        // vertical is 0.2 * 0.98 (the air band, lava never solid).
        assertEquals(0.2 * 0.98, item.velocityY(), 1.0e-9);
        assertTrue(Math.abs(item.velocityX()) <= 0.2,
                "the horizontal re-roll is bounded by (rand - rand) * 0.2");
    }

    @Test
    void theItemFizzIntCastQuirkKeepsTheZeroCrossingDark() {
        // The reference's own quirk (line 79's (int) CAST, truncation
        // toward zero — NOT floor): an item crossing x=-0.4 to x=+0.4
        // crosses CELLS -1 to 0 by floor but (int) reads 0 to 0 — the
        // flag stays dark and a non-25th tick never pops.
        LavaColumnGround ground = new LavaColumnGround(55, 75);
        RecordingSounds sounds = new RecordingSounds();
        ItemEntity item = new ItemEntity(1, new Position(-0.4, 62.5, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(0.8, 0.0, 0.0);
        item.tick(ground, new Random(4), sounds);

        assertEquals(0.4, item.position().x(), 1.0e-9, "the move itself ran");
        assertEquals(0, sounds.heard.size(),
                "the int-cast flag misses the -1 to 0 cell crossing");
        // The control: the same tick crossing (int) -1 to 0 DOES pop —
        // the pair pins the cast against the floor.
        ItemEntity control = new ItemEntity(2, new Position(-1.4, 62.5, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        control.setVelocity(0.8, 0.0, 0.0);
        control.tick(ground, new Random(4), sounds);
        assertEquals(1, sounds.heard.size(), "the control crossing pops");
    }

    @Test
    void theItemFizzTwentyFiveTickGateLaw() {
        // The gate's whole law over a 60-tick bob in the lava column:
        // the sound fires EXACTLY when the move crossed an int-cell
        // boundary OR the age hit a 25th tick AND the feet cell reads
        // lava — every tick asserted, both arms live (the bob's own
        // crossings at unaligned ages, the 25th/50th ticks' age arm).
        LavaColumnGround ground = new LavaColumnGround(55, 75);
        RecordingSounds sounds = new RecordingSounds();
        ItemEntity item = new ItemEntity(3, new Position(0.5, 62.5, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(0.0, 0.0, 0.0);
        Random random = new Random(9);
        int boundaryPops = 0;
        int agePops = 0;
        for (int tick = 1; tick <= 60; tick++) {
            double preX = item.position().x();
            double preY = item.position().y();
            double preZ = item.position().z();
            int before = sounds.heard.size();
            item.tick(ground, random, sounds);
            // The crossing flag's own frame (the clock-frame repair's
            // re-pin): the reference's line 75 reads (int) of the entity's
            // y — the 1.8.8 position IS the box BOTTOM, the engine's
            // center translates as y - HALF_HEIGHT (the same convention
            // the lava consult reads below). The pre-fix pin read the
            // raw center.
            boolean crossed = (int) preX != (int) item.position().x()
                    || (int) (preY - ItemEntity.HALF_HEIGHT)
                    != (int) (item.position().y() - ItemEntity.HALF_HEIGHT)
                    || (int) preZ != (int) item.position().z();
            boolean ageArm = tick % 25 == 0;
            boolean inLava = ground.lavaAtCell(item.position().x(),
                    item.position().y() - ItemEntity.HALF_HEIGHT, item.position().z());
            boolean expected = inLava && (crossed || ageArm);
            assertEquals(expected, sounds.heard.size() > before,
                    "tick " + tick + ": the gate law (crossed=" + crossed
                            + " ageArm=" + ageArm + " lava=" + inLava + ")");
            if (sounds.heard.size() > before) {
                if (ageArm && !crossed) {
                    agePops++;
                } else {
                    boundaryPops++;
                }
            }
        }
        assertTrue(boundaryPops >= 2, "the bob's own crossings fired");
        assertTrue(agePops >= 1, "the 25th/50th ticks' age arm fired");
    }

    // ------------------------------------------------------------------
    // The orb's fizz (the unconditional band)
    // ------------------------------------------------------------------

    @Test
    void theOrbFizzPopsEveryTickUnconditionally() {
        // The orb has NO gate (ExperienceOrbEntity.tick lines 69-74): the
        // feet cell's lava pops EVERY tick BEFORE the move — the pop
        // feeds THIS tick's rise (the position climbs 0.2/tick), the
        // dragged remainder is 0.196, the sound fires each tick.
        OrbAirGround ground = new OrbAirGround(true);
        RecordingSounds sounds = new RecordingSounds();
        ExperienceOrbEntity orb = new ExperienceOrbEntity(100,
                new Position(0.0, 64.125, 0.0), 7, 0);
        Random random = new Random(5);
        for (int tick = 1; tick <= 3; tick++) {
            double preY = orb.position().y();
            orb.tick(ground, random, sounds, null);
            assertEquals(preY + 0.2, orb.position().y(), 1.0e-9,
                    "tick " + tick + ": the pop fed the move");
            assertEquals(0.2 * 0.98, orb.velocityY(), 1.0e-9,
                    "tick " + tick + ": the dragged pop");
            assertEquals(tick, sounds.heard.size(),
                    "tick " + tick + ": the sound fired");
            assertEquals("random.fizz", sounds.heard.get(tick - 1).name());
            assertEquals(0.4f, sounds.heard.get(tick - 1).volume(), 1.0e-6f);
        }
        // The control: the same orb over dry air never sounds.
        OrbAirGround dry = new OrbAirGround(false);
        RecordingSounds drySounds = new RecordingSounds();
        ExperienceOrbEntity dryOrb = new ExperienceOrbEntity(101,
                new Position(0.0, 64.125, 0.0), 7, 0);
        dryOrb.tick(dry, random, drySounds, null);
        assertEquals(0, drySounds.heard.size());
    }

    // ------------------------------------------------------------------
    // The orb's attraction (the staggered arm, the pull, the release)
    // ------------------------------------------------------------------

    /** The standing pull at the seat geometry: dir/|d| * (1 - |d|)² * 0.1. */
    private static final double STANDING_DV_X = 0.028972371;
    private static final double STANDING_DV_Y = 0.015645081;
    /** The sneaking eye's own vertical aim (1.54, not 1.62). */
    private static final double SNEAKING_DV_X = 0.029770310;
    private static final double SNEAKING_DV_Y = 0.015282092;

    @Test
    void theOrbAttractionWaitsForTheStaggeredArmThenPulls() {
        // The throttle's own start (lines 82-89): lastTargetUpdateTick
        // starts 0, so a fresh orb (id 100, stagger 0) holds NOTHING
        // until renderTicks 21 — tick 22 — and only then pulls. The
        // pull itself (lines 91-100): d = (player - orb) / 8 over the
        // EYE aim, dv = dir/|d| * (1 - |d|)² * 0.1 — at the seat
        // geometry (orb feet (0, 64, 0), player (3, 64, 0) standing)
        // dvX = 0.028972371, dvY = 0.015645081 (pinned literals). The
        // seat resets the body to its start every tick (the orb's own
        // teleport + setVelocity): the throttle state (age, the stamp,
        // the hold) persists, the free fall does not — the pull's dv
        // reads positions only, so the reset isolates the law.
        OrbAirGround ground = new OrbAirGround(false);
        RecordingSounds sounds = new RecordingSounds();
        SinglePlayer players = new SinglePlayer();
        ExperienceOrbEntity orb = new ExperienceOrbEntity(100,
                new Position(0.0, 64.125, 0.0), 7, 0);
        Random random = new Random(6);
        for (int tick = 1; tick <= 21; tick++) {
            orb.teleport(new Position(0.0, 64.125, 0.0));
            orb.setVelocity(0.0, 0.0, 0.0);
            orb.tick(ground, random, sounds, players);
            assertEquals(0.0, orb.position().x(), 1.0e-12,
                    "tick " + tick + ": no pull before the staggered arm");
            assertEquals(0, players.nearestCalls,
                    "tick " + tick + ": the retarget consult stays closed");
        }
        // Tick 22: the arm opens, the nearest consult runs once, the
        // pull lands before the move (the position moves at dv) and the
        // drag multiplies after (velocity = dv * 0.98).
        orb.teleport(new Position(0.0, 64.125, 0.0));
        orb.setVelocity(0.0, 0.0, 0.0);
        orb.tick(ground, random, sounds, players);
        assertEquals(1, players.nearestCalls, "the retarget consult ran");
        assertEquals(STANDING_DV_X, orb.position().x(), 1.0e-8,
                "the pull moved the orb at the pinned dv");
        assertEquals(STANDING_DV_X * 0.98, orb.velocityX(), 1.0e-8,
                "the drag applied after the pull");
        // The vertical: gravity -0.03 + dvY moved the body pre-drag.
        assertEquals(64.125 - 0.03 + STANDING_DV_Y, orb.position().y(), 1.0e-8);
        // The cadence + the sticky hold (lines 82-89): the player walks
        // FAR — the held pull reads a live position every tick, so the
        // beyond-radius ticks add nothing (d4 >= 1 leaves d5 <= 0), and
        // the ~20-tick re-arm at tick 43 finds the hold LOST (beyond
        // 64 squared) and runs the nearest consult again — the player
        // back in range at tick 44, the same hold pulls at the pinned
        // dv (the hold is a live read, never a snapshot).
        players.x = 50.0; // far beyond the 8-block radius
        for (int tick = 23; tick <= 42; tick++) {
            orb.teleport(new Position(0.0, 64.125, 0.0));
            orb.setVelocity(0.0, 0.0, 0.0);
            orb.tick(ground, random, sounds, players);
            assertEquals(0.0, orb.position().x(), 1.0e-12,
                    "tick " + tick + ": no pull at or beyond the radius");
            assertEquals(1, players.nearestCalls,
                    "tick " + tick + ": the sticky hold never re-searches");
        }
        orb.teleport(new Position(0.0, 64.125, 0.0));
        orb.setVelocity(0.0, 0.0, 0.0);
        orb.tick(ground, random, sounds, players); // tick 43: the re-arm
        assertEquals(2, players.nearestCalls,
                "the re-arm re-searches a hold lost beyond the radius");
        players.x = 3.0; // back in range — the same live hold
        orb.teleport(new Position(0.0, 64.125, 0.0));
        orb.setVelocity(0.0, 0.0, 0.0);
        orb.tick(ground, random, sounds, players); // tick 44
        assertEquals(STANDING_DV_X, orb.position().x(), 1.0e-8,
                "the live hold pulls again the tick the player returns");
    }

    @Test
    void theOrbAttractionReleasesTheSpectator() {
        // The live hold's own law (lines 90-92): a held igniter turning
        // spectator RELEASES the hold that tick — the pull stops, the
        // velocity only drags. And the never-held arm: a spectator from
        // the start is never targeted (the nearest consult's own
        // NOT_SPECTATOR filter).
        OrbAirGround ground = new OrbAirGround(false);
        RecordingSounds sounds = new RecordingSounds();
        SinglePlayer players = new SinglePlayer();
        ExperienceOrbEntity orb = new ExperienceOrbEntity(100,
                new Position(0.0, 64.125, 0.0), 7, 0);
        Random random = new Random(6);
        for (int tick = 1; tick <= 22; tick++) {
            orb.teleport(new Position(0.0, 64.125, 0.0));
            orb.setVelocity(0.0, 0.0, 0.0);
            orb.tick(ground, random, sounds, players); // tick 22 pulls
        }
        double pulledVelocityX = orb.velocityX();
        assertEquals(STANDING_DV_X * 0.98, pulledVelocityX, 1.0e-8,
                "the pull landed at tick 22");
        players.spectator = true;
        orb.teleport(new Position(0.0, 64.125, 0.0));
        orb.setVelocity(0.0, 0.0, 0.0);
        orb.tick(ground, random, sounds, players); // tick 23: the release
        assertEquals(0.0, orb.position().x(), 1.0e-12,
                "the released hold never moves the body");
        assertEquals(0.0, orb.velocityX(), 1.0e-12,
                "the released hold adds no pull — the reset velocity stays zero");
        // The never-held arm: the spectator is never targeted at all.
        SinglePlayer watching = new SinglePlayer();
        watching.spectator = true;
        ExperienceOrbEntity fresh = new ExperienceOrbEntity(100,
                new Position(0.0, 64.125, 0.0), 7, 0);
        for (int tick = 1; tick <= 30; tick++) {
            fresh.tick(ground, random, sounds, watching);
            assertEquals(0.0, fresh.position().x(), 1.0e-12,
                    "tick " + tick + ": a spectator never pulls");
        }
    }

    @Test
    void theOrbAttractionAimsAtTheSneakEye() {
        // The pull's aim point is the player's EYE — 1.54 sneaking, not
        // 1.62 standing (PlayerEntity.getEyeHeight lines 1648-1655's
        // sneak arm): the same geometry with a sneaking player pulls at
        // the sneak eye's own vector (pinned literals).
        OrbAirGround ground = new OrbAirGround(false);
        RecordingSounds sounds = new RecordingSounds();
        SinglePlayer players = new SinglePlayer();
        players.sneaking = true;
        ExperienceOrbEntity orb = new ExperienceOrbEntity(100,
                new Position(0.0, 64.125, 0.0), 7, 0);
        Random random = new Random(6);
        for (int tick = 1; tick <= 21; tick++) {
            orb.teleport(new Position(0.0, 64.125, 0.0));
            orb.setVelocity(0.0, 0.0, 0.0);
            orb.tick(ground, random, sounds, players);
        }
        orb.teleport(new Position(0.0, 64.125, 0.0));
        orb.setVelocity(0.0, 0.0, 0.0);
        orb.tick(ground, random, sounds, players); // tick 22: the sneak pull
        assertEquals(SNEAKING_DV_X, orb.position().x(), 1.0e-8,
                "the sneak eye's own horizontal aim");
        assertEquals(64.125 - 0.03 + SNEAKING_DV_Y, orb.position().y(), 1.0e-8,
                "the sneak eye's own vertical aim");
    }

    // ------------------------------------------------------------------
    // The manager wiring (the sink rides the tick walk)
    // ------------------------------------------------------------------

    @Test
    void theManagerDrivesTheItemFizz() {
        // The wiring seat: the manager's own tick walk drives the fizz —
        // the 4-arg construction hands the sink and the random through,
        // a spawned drop bobbing in the lava column sounds repeatedly.
        LavaColumnGround ground = new LavaColumnGround(55, 75);
        RecordingSounds sounds = new RecordingSounds();
        ItemEntityManager manager = new ItemEntityManager(
                ground, new Random(7), 1, sounds);
        manager.spawnDropAtBlock(new Position(0, 62, 0),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        for (int tick = 0; tick < 60; tick++) {
            manager.tick(List.of());
        }
        assertFalse(sounds.heard.isEmpty(), "the bobbing drop sounds");
        assertTrue(sounds.heard.size() >= 2, "the pops repeat through the bob");
        for (Heard heard : sounds.heard) {
            assertEquals("random.fizz", heard.name());
            assertEquals(0.4f, heard.volume(), 1.0e-6f);
        }
    }
}
