package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The orb's retarget clock + fizz frame (the Wave LF review — the
 * translation defects the parallel-lane collision surfaced, each read
 * against the reference before its fix):
 *
 * <p>THE RETARGET CLOCK (ExperienceOrbEntity.tick line 80): the window
 * {@code lastTargetUpdateTick < renderTicks - 20 + networkId % 100}
 * rides the body's OWN {@code renderTicks} counter — line 12's public
 * field, incremented at the tick's TAIL (line 123), never persisted
 * (the NBT round trip writes the Age tag alone). The despawn
 * {@code age} incremented beside it IS persisted — a RELOADED orb
 * restarts renderTicks at 0 while its age keeps the stored value, so
 * the two clocks diverge exactly where a fused {@code age - 1} frame
 * would misplace the first-acquire window: a reloaded orb waits out
 * its id-staggered window exactly like a fresh one (an orb with
 * {@code id % 100 <= 20} holds nothing for its first ~21 ticks), while
 * the pre-fix age frame acquired on the FIRST tick back.</p>
 *
 * <p>THE FIZZ SOUND'S FRAME (ExperienceOrbEntity.tick line 75): the
 * reference's {@code playSound} plays at the entity — x/z the box
 * center, y the box BOTTOM (the 1.8.8 position IS the feet). The
 * pre-fix emission carried the center's y a half-height high.</p>
 */
class OrbRetargetClockParityTest {

    /** The recorded sound (the sink the seats drive). */
    private record Heard(Position at, String name, float volume, float pitch, int dimension) {
    }

    /** The recorder sink: collects every emission for the assertions. */
    private static final class RecordingSounds implements ExperienceOrbEntity.Sounds {
        final List<Heard> heard = new ArrayList<>();

        @Override
        public void sound(Position at, String name, float volume, float pitch, int dimension) {
            heard.add(new Heard(at, name, volume, pitch, dimension));
        }
    }

    /** The single-player consult the attraction seats drive. */
    private static final class SinglePlayer implements ExperienceOrbEntity.Players {
        int entityId = 5;
        double x = 3.0;
        double y = 64.0;
        double z = 0.0;
        boolean spectator;
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
            return new ExperienceOrbEntity.Players.Target(
                    entityId, x, y, y + 1.62, z, spectator);
        }
    }

    /** The orb's air ground: nothing solid, no lava. */
    private static final class OrbAirGround implements ExperienceOrbEntity.Ground {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return false;
        }

        @Override
        public boolean lavaAtCell(double centerX, double feetY, double centerZ) {
            return false;
        }
    }

    /** The orb's lava ground: nothing solid, lava at every cell. */
    private static final class OrbLavaGround implements ExperienceOrbEntity.Ground {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return false;
        }

        @Override
        public boolean lavaAtCell(double centerX, double feetY, double centerZ) {
            return true;
        }
    }

    /**
     * The first tick each body's retarget consult opened (0 never):
     * the seat resets the geometry every tick (the teleport +
     * setVelocity discipline the attraction seats ride) so the throttle
     * state alone decides.
     */
    private static int firstConsultTick(ExperienceOrbEntity orb,
                                        SinglePlayer players,
                                        Random random, RecordingSounds sounds,
                                        ExperienceOrbEntity.Ground ground,
                                        int ticks) {
        for (int tick = 1; tick <= ticks; tick++) {
            orb.teleport(new Position(0.0, 64.125, 0.0));
            orb.setVelocity(0.0, 0.0, 0.0);
            orb.tick(ground, random, sounds, players);
            if (players.nearestCalls > 0) {
                return tick;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // THE RETARGET CLOCK — the reloaded orb waits like a fresh one
    // ------------------------------------------------------------------

    @Test
    void theSeededAgeOrbWaitsOutItsWindowLikeAFreshOne() {
        // The persistence path's own shape: an orb whose age was
        // restored (the NBT Age tag — AnvilWorldImport line 1356, the
        // store round trip, ExperienceOrbManager line 150) spawns
        // beside a player well inside the 8-block radius. The
        // reference's window rides the renderTicks counter — restarted
        // at 0 by the load — so the restored-age orb holds NOTHING for
        // exactly as long as a fresh one: id 300 (stagger 0) opens its
        // first consult at tick 22 (renderTicks 21 > 20). The pre-fix
        // engine played the window on the age frame — renderTicks 2999
        // at tick 1 — and the restored orb acquired (and pulled) on
        // its FIRST tick back.
        Random random = new Random(21);
        ExperienceOrbEntity restored = new ExperienceOrbEntity(300,
                new Position(0.0, 64.125, 0.0), 7, 0);
        restored.setAge(3000);
        SinglePlayer restoredPlayers = new SinglePlayer();
        int restoredTick = firstConsultTick(restored, restoredPlayers, random,
                new RecordingSounds(), new OrbAirGround(), 25);

        ExperienceOrbEntity fresh = new ExperienceOrbEntity(300,
                new Position(0.0, 64.125, 0.0), 7, 0);
        SinglePlayer freshPlayers = new SinglePlayer();
        int freshTick = firstConsultTick(fresh, freshPlayers, random,
                new RecordingSounds(), new OrbAirGround(), 25);

        assertEquals(freshTick, restoredTick,
                "the restored-age orb waits out its window exactly like a fresh one");
        assertEquals(22, restoredTick,
                "id 300 (stagger 0) opens its first consult at tick 22"
                        + " (renderTicks 21 > 20), never at tick 1");
        // The pull arrives WITH the consult and stays: the sticky hold
        // reads the live player every tick after, so the next armed
        // tick carries the attraction's horizontal arm (the player
        // stands at +x).
        restored.teleport(new Position(0.0, 64.125, 0.0));
        restored.setVelocity(0.0, 0.0, 0.0);
        restored.tick(new OrbAirGround(), random, new RecordingSounds(), restoredPlayers);
        assertTrue(restored.velocityX() > 0.0,
                "the acquired hold pulls toward the player every armed tick");
    }

    // ------------------------------------------------------------------
    // THE FIZZ SOUND'S FRAME — the reference plays at the box bottom
    // ------------------------------------------------------------------

    @Test
    void theOrbFizzSoundPlaysAtTheFeetFrame() {
        // The reference's playSound (ExperienceOrbEntity.tick line 75)
        // plays at the entity BEFORE the move: x/z the box center, y
        // the box BOTTOM (the 1.8.8 position IS the feet — the same
        // frame the lava consult reads). The pre-fix emission carried
        // the center a half-height high.
        OrbLavaGround ground = new OrbLavaGround();
        RecordingSounds sounds = new RecordingSounds();
        ExperienceOrbEntity orb = new ExperienceOrbEntity(301,
                new Position(0.0, 64.125, 0.0), 7, 0);
        orb.tick(ground, new Random(22), sounds, null);
        assertEquals(1, sounds.heard.size(), "the unconditional fizz fired");
        Position at = sounds.heard.get(0).at();
        assertEquals(0.0, at.x(), 1.0e-9, "the sound's x is the pre-move center x");
        assertEquals(64.125 - ExperienceOrbEntity.HALF_HEIGHT, at.y(), 1.0e-9,
                "the sound's y is the box BOTTOM (the reference's own y)");
        assertEquals(0.0, at.z(), 1.0e-9);
    }
}
