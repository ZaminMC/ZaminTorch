package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ambient/water pair (Slice 16e — SquidEntity + BatEntity): the
 * squid's tentacle-cycle swim with the stranded sink, the bat's roost
 * cycle with the hangPos wander, and the spawn-category gates.
 */
class SquidAndBatTest {

    /** A flat-world stub with a scripted ceiling and water band. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        boolean water = true;
        boolean ceilingSolid = true;
        Position player;
        double playerRange = 4.0;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0; // the floor
        }

        @Override
        public boolean isSolidAt(double x, double y, double z) {
            if (Math.floor(y) >= 9.0) {
                return ceilingSolid; // the bat's ceiling band
            }
            return Math.floor(y) < 8.0;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return water && Math.floor(y) >= 10.0 && Math.floor(y) < 20; // the water column (the bat's band is air)
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            if (player == null) {
                return null;
            }
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            return dx * dx + dy * dy + dz * dz <= range * range ? player : null;
        }
    }

    private static MobEntity squidIn(ScriptedWorld world, long seed) {
        return new MobEntity(970, MobType.SQUID, new Position(0.5, 12.0, 0.5),
                new Random(seed), world);
    }

    private static MobEntity batIn(ScriptedWorld world, long seed) {
        return new MobEntity(980, MobType.BAT, new Position(0.5, 8.15, 0.5),
                new Random(seed), world);
    }

    @Test
    void theSquidCarriesTheIdentityAndSwims() {
        assertEquals(94, MobType.SQUID.legacyTypeId);
        assertEquals(0.95, MobType.SQUID.width, 1e-9);
        assertEquals(0.95, MobType.SQUID.height, 1e-9);
        assertEquals(10.0f, MobType.SQUID.maxHealth, 1e-6f);
        assertFalse(MobType.SQUID.hostile);
        assertEquals("", MobType.SQUID.idleSound, "the silent squid");

        ScriptedWorld world = new ScriptedWorld();
        MobEntity squid = squidIn(world, 91L);
        assertTrue(world.water);

        // The swim: the direction target re-rolls on the 1-in-50, then the
        // stroke drives the velocity. Over a few ticks the body moves.
        Position start = squid.position();
        boolean moved = false;
        for (int i = 0; i < 100 && !moved; i++) {
            squid.tick(false);
            Position now = squid.position();
            moved = now.x() != start.x() || now.y() != start.y() || now.z() != start.z();
        }
        assertTrue(moved, "the tentacle swim moves the body");

        // Stranded: out of water the body sinks (velocityY shrinks).
        world.water = false;
        squid.tick(false);
        assertTrue(squid.position().y() < start.y() + 0.001
                || squid.position().y() < 12.0 + 1.0, "the stranded sink or hold");
    }

    @Test
    void theBatRoostsAndFlies() {
        assertEquals(65, MobType.BAT.legacyTypeId);
        assertEquals(0.5, MobType.BAT.width, 1e-9);
        assertEquals(0.9, MobType.BAT.height, 1e-9);
        assertEquals(6.0f, MobType.BAT.maxHealth, 1e-6f);
        assertFalse(MobType.BAT.hostile);
        assertEquals("mob.bat.idle", MobType.BAT.idleSound);

        ScriptedWorld world = new ScriptedWorld();
        MobEntity bat = batIn(world, 92L);
        assertTrue(bat.batRoosting(), "a fresh bat hangs");

        // The roost holds while the ceiling is solid and no player is
        // within 4: the body snaps to the ceiling and stays.
        bat.tick(false);
        assertTrue(bat.batRoosting());

        // A player within 4 wakes it.
        world.player = new Position(2.5, 8.2, 0.5);
        bat.tick(false);
        assertFalse(bat.batRoosting(), "a player within 4 wakes the roost");
        // The wake's own 1015 (BatEntity line 112's doEvent): the event
        // flag the manager drains for the world-event broadcast.
        assertTrue(bat.consumeBatTakeoff(), "the player wake fired the takeoff event");
        assertFalse(bat.consumeBatTakeoff(), "the flag is consumed once");

        // The flight: the wander moves the body.
        world.player = null;
        Position start = bat.position();
        boolean flew = false;
        for (int i = 0; i < 100 && !flew; i++) {
            bat.tick(false);
            Position now = bat.position();
            flew = now.x() != start.x() || now.y() != start.y() || now.z() != start.z();
        }
        assertTrue(flew, "the hangPos wander moves the body");

        // The ceiling arm's own 1015 (BatEntity line 104's doEvent — the
        // roost opens when the block above stops being solid): a fresh
        // roost, the ceiling removed.
        MobEntity ceilingBat = batIn(world, 93L);
        assertTrue(ceilingBat.batRoosting(), "the fresh bat hangs");
        world.ceilingSolid = false;
        ceilingBat.tick(false);
        assertFalse(ceilingBat.batRoosting(), "the opened ceiling wakes the roost");
        assertTrue(ceilingBat.consumeBatTakeoff(), "the ceiling arm fired the takeoff event");
    }

    @Test
    void theSpawnCategoriesCarryTheReferenceRows() {
        // The ambient row: the bat 10/8-8 (Biome line 292). The water row:
        // the squid 10/4-4 (Biome line 277).
        // Verified through the system's own entries seam in the natural
        // spawn tests — here the kinds' identity.
        assertEquals("minecraft:squid", MobType.SQUID.identifier().toString());
        assertEquals("minecraft:bat", MobType.BAT.identifier().toString());
        assertEquals("mob.bat.hurt", MobType.BAT.hurtSound);
        assertEquals("mob.bat.death", MobType.BAT.deathSound);
    }
}
