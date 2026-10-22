package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE IN-WALL SUFFOCATION (the living family's baseTick arm —
 * LivingEntity.baseTick lines 195-197 over Entity.isInWall lines
 * 1267-1287): the eight-point eye survey, the view-blocking cell consult
 * (isCube over the movement-blocking material — a slab never holds a
 * body), the 10-tick wound cadence, and the noClip/dead exemptions.
 */
class InWallSuffocationTest {

    /**
     * A world with a switchable view-blocking band: {@code solidY} makes
     * everything below a plane solid (the boolean read); {@code wallY}
     * (when >= 0) makes ONLY that one cell view-blocking — the slab case
     * reads solid-but-not-view-blocking everywhere else.
     */
    private static class BandWorld implements MobEntity.WorldQuery {
        final double solidBelow;
        final int wallCellY;
        final int wallCellX;
        final int wallCellZ;

        BandWorld(double solidBelow, int wallCellY, int wallCellX, int wallCellZ) {
            this.solidBelow = solidBelow;
            this.wallCellY = wallCellY;
            this.wallCellX = wallCellX;
            this.wallCellZ = wallCellZ;
        }

        static BandWorld open() {
            return new BandWorld(0.0, -1, 0, 0);
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < solidBelow;
        }

        @Override
        public boolean viewBlockingAt(double x, double y, double z) {
            // Only the named cell blocks the view (the slab family reads
            // solid but never view-blocking — isCube false).
            return (int) Math.floor(y) == wallCellY
                    && (int) Math.floor(x) == wallCellX
                    && (int) Math.floor(z) == wallCellZ;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    private static MobEntity zombie(BandWorld world, long seed) {
        return new MobEntity(7, MobType.ZOMBIE, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theSurveyFindsTheWallOnlyWhenTheEyeBandHoldsIt() {
        // The zombie stands at (0.5, 8.0, 0.5): the 1.8-tall body's eye at
        // ~1.62 above the feet. A view-blocking cell at the eye (y 9.6ish
        // -> cell 9, x 0.5 -> cell 0, z 0.5 -> cell 0) holds it; a cell one
        // band off (x 2) never touches the eye survey.
        MobEntity held = zombie(new BandWorld(8.0, 9, 0, 0), 11L);
        assertTrue(held.isInWall(), "the eye-band cell holds the body");
        MobEntity missed = zombie(new BandWorld(8.0, 9, 2, 0), 11L);
        assertFalse(missed.isInWall(), "a cell outside the survey never holds");
        MobEntity low = zombie(new BandWorld(8.0, 6, 0, 0), 11L);
        assertFalse(low.isInWall(), "a cell at the feet (below the eye band) never holds");
    }

    @Test
    void aSlabCellReadsSolidButNeverHoldsTheBody() {
        // isViewBlocking = blocksMovement && isCube: the slab blocks the
        // movement (isSolid true) but isCube false — no suffocation.
        BandWorld slab = new BandWorld(0.0, -1, 0, 0) {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return true; // the slab blocks movement everywhere
            }

            @Override
            public boolean viewBlockingAt(double x, double y, double z) {
                return false; // the family is never a full cube
            }
        };
        MobEntity inSlab = zombie(slab, 13L);
        assertFalse(inSlab.isInWall(), "the partial-shape cell never holds the body");
    }

    @Test
    void theWoundLandsAtTheTenTickCadence() {
        BandWorld wall = new BandWorld(8.0, 9, 0, 0);
        MobEntity zombie = zombie(wall, 17L);
        float health = zombie.health();
        // The reference's i-frame shape: one 1.0 wound per window (the
        // engine's timer convention the lava arm rides — the first wound
        // one window deep).
        for (int i = 0; i < 9; i++) {
            zombie.tick(false);
            assertEquals(health, zombie.health(), 1e-6f, "no wound inside the first window");
        }
        zombie.tick(false);
        assertEquals(health - 1.0f, zombie.health(), 1e-6f, "the first wound lands");
        for (int i = 0; i < 9; i++) {
            zombie.tick(false);
        }
        zombie.tick(false);
        assertEquals(health - 2.0f, zombie.health(), 1e-6f, "the second wound lands");
    }

    @Test
    void leavingTheWallResetsTheClock() {
        BandWorld wall = new BandWorld(8.0, 9, 0, 0);
        // A world whose wall can be switched off mid-walk.
        class SwitchWorld extends BandWorld {
            boolean on = true;

            SwitchWorld() {
                super(8.0, 9, 0, 0);
            }

            @Override
            public boolean viewBlockingAt(double x, double y, double z) {
                return on && super.viewBlockingAt(x, y, z);
            }
        }
        SwitchWorld sw = new SwitchWorld();
        MobEntity zombie = zombie(sw, 19L);
        for (int i = 0; i < 7; i++) {
            zombie.tick(false); // the window half-spent
        }
        sw.on = false;
        for (int i = 0; i < 30; i++) {
            zombie.tick(false);
        }
        assertEquals(MobType.ZOMBIE.maxHealth, zombie.health(), 1e-6f,
                "the clock reset — no wound ever landed");
    }

    @Test
    void theDragonAndTheDeadBodyExempt() {
        BandWorld wall = new BandWorld(8.0, 9, 0, 0);
        MobEntity dragon = new MobEntity(63, MobType.ENDER_DRAGON,
                new Position(0.5, 8.0, 0.5), new Random(23L), wall);
        assertFalse(dragon.isInWall(), "the noClip dragon never registers the wall");
        for (int i = 0; i < 40; i++) {
            dragon.tick(false);
        }
        assertEquals(MobType.ENDER_DRAGON.maxHealth, dragon.health(), 1e-6f,
                "the dragon never wounds");
        MobEntity body = zombie(wall, 29L);
        body.hurt(1000.0f);
        assertTrue(body.dead());
        // The reference's gate: isAlive wraps the whole arm — a dead body
        // inside a wall still ANSWERS the survey (isInWall has no gate) but
        // never wounds.
        for (int i = 0; i < 30; i++) {
            body.tick(false);
        }
        assertEquals(0.0f, body.health(), 1e-6f, "the dead body never wounds");
    }
}
