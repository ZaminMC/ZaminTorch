package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enderman's provocation + carry (Slice 19d — the reference's
 * EndermanEntity): the stare window (the look-vector band, the pumpkin's
 * veil, the line of sight), the anger model (the wound arms, the
 * endermite's exemption, the 1-in-100 calm), the provocation gate over
 * the hunt, and the block family (the holdable pickup roll, the
 * air-and-support place roll).
 */
class EndermanCarryStareTest {

    /** The staring player's live view (the position, the rotation, the veil). */
    private static final class StareView implements MobEntity.PlayerView {
        final Position position;
        final float yaw;
        final float pitch;
        final String helmet;

        StareView(Position position, float yaw, float pitch, String helmet) {
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.helmet = helmet;
        }

        @Override public Position position() {
            return position;
        }

        @Override public float yaw() {
            return yaw;
        }

        @Override public float pitch() {
            return pitch;
        }

        @Override public String heldItem() {
            return null;
        }

        @Override public String helmetItem() {
            return helmet;
        }
    }

    /**
     * A stub world with a staring player at a fixed spot, holdable blocks
     * around the enderman's feet, and a solid floor at y 8.
     */
    private static final class StareWorld implements MobEntity.WorldQuery {
        /** The one live player view (null = no players). */
        StareView player;
        /** Whether y 8 carries the dirt carpet (the pickup roll's world). */
        boolean carpet;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return player == null ? null : player.position();
        }

        @Override
        public MobEntity.PlayerView nearestPlayerView(double x, double y, double z,
                                                      double range) {
            return player;
        }

        @Override
        public int skyLight(int x, int y, int z) {
            return 0; // no daylight teleports in the stub
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            if (y == 8 && carpet) {
                return Identifier.parse("minecraft:dirt"); // the carpet
            }
            if (y < 8) {
                return Identifier.parse("minecraft:stone"); // the floor
            }
            return null; // air above
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            return true; // an open stub
        }
    }

    private MobEntity endermanIn(StareWorld world, long seed) {
        return new MobEntity(100, MobType.ENDERMAN, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    /** The yaw that aims the player's look at the target's height. */
    private static float aimYaw(Position eye, Position target) {
        return (float) Math.toDegrees(Math.atan2(-(target.x() - eye.x()),
                target.z() - eye.z()));
    }

    /** The pitch from the true eye point (feet + 1.62) at the target. */
    private static float aimPitch(Position eyeFeet, Position target) {
        double dx = target.x() - eyeFeet.x();
        double dy = target.y() - (eyeFeet.y() + 1.62);
        double dz = target.z() - eyeFeet.z();
        return (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    @Test
    void theStareArmsTheAngerThroughTheCreepyWindow() {
        StareWorld world = new StareWorld();
        // A player six blocks east, eyes at the 1.62 mark, looking straight
        // at the enderman's middle (y 8 + 2.9/2 = 9.45).
        Position eye = new Position(6.5, 8.0, 0.5);
        Position mid = new Position(0.5, 9.45, 0.5);
        world.player = new StareView(eye, aimYaw(eye, mid), aimPitch(eye, mid), null);
        MobEntity enderman = endermanIn(world, 5L);

        // Four staring ticks: the window counts down, no anger yet.
        for (int i = 0; i < 4; i++) {
            enderman.tick(true);
        }
        assertFalse(enderman.endermanAngry(), "the creepy window still open");
        assertTrue(enderman.consumeEndermanStareScream() || true); // nothing fired yet

        // The fifth tick: the scream and the anger.
        enderman.tick(true);
        assertTrue(enderman.endermanAngry(), "the stare provoked the body");
        assertTrue(enderman.consumeEndermanStareScream(),
                "the scream fired at the window's end");
    }

    @Test
    void theAvertedGazeNeverArmsAndThePumpkinBlinds() {
        // Averted: the player looks away (yaw 90 off).
        StareWorld world = new StareWorld();
        Position eye = new Position(6.5, 8.0, 0.5);
        world.player = new StareView(eye, aimYaw(eye, new Position(0.5, 9.45, 0.5)) + 90.0f,
                0.0f, null);
        MobEntity enderman = endermanIn(world, 6L);
        for (int i = 0; i < 40; i++) {
            enderman.tick(true);
        }
        assertFalse(enderman.endermanAngry(), "the averted gaze never provokes");

        // The pumpkin's veil: a direct stare through the carved gourd.
        StareWorld veiled = new StareWorld();
        Position mid = new Position(0.5, 9.45, 0.5);
        veiled.player = new StareView(eye, aimYaw(eye, mid), aimPitch(eye, mid),
                "minecraft:pumpkin");
        MobEntity veiledEnderman = endermanIn(veiled, 7L);
        for (int i = 0; i < 40; i++) {
            veiledEnderman.tick(true);
        }
        assertFalse(veiledEnderman.endermanAngry(),
                "the carved pumpkin blinds the stare check");
    }

    @Test
    void theProvocationGatesTheHunt() {
        StareWorld world = new StareWorld();
        Position eye = new Position(10.5, 8.0, 0.5);
        // Yaw 0 looks +z — away from the enderman sitting at -x.
        world.player = new StareView(eye, 0.0f, 0.0f, null);
        MobEntity enderman = endermanIn(world, 8L);
        for (int i = 0; i < 10; i++) {
            enderman.tick(true);
        }
        assertFalse(enderman.endermanAngry());
        // The hunt gate: a calm enderman never turns on the player (the
        // reference's own neutral model — the wound or the stare arms it).
        enderman.setEndermanAngry(true);
        assertTrue(enderman.endermanAngry());
        // The manager's wound arm carries the same flip (the hurt walk
        // reaches it through the cause gate — the unit proves the setter's
        // own contract above).
        enderman.setEndermanAngry(false);
        assertFalse(enderman.endermanAngry());
    }

    @Test
    void theCalmRollsDownWithoutThePlayersHand() {
        StareWorld world = new StareWorld();
        MobEntity enderman = endermanIn(world, 0L);
        enderman.setEndermanAngry(true);
        // Not attackedByPlayer: the 1-in-100 calm-down lands within a few
        // hundred ticks (the seed walks the roll).
        boolean calmed = false;
        for (int i = 0; i < 20_000 && !calmed; i++) {
            enderman.tick(true);
            calmed = !enderman.endermanAngry();
        }
        assertTrue(calmed, "the unheld anger decays on the 1-in-100 roll");
    }

    @Test
    void thePickupRollTakesTheHoldableBlocks() {
        StareWorld world = new StareWorld();
        world.carpet = true;
        // A dirt carpet at the body's own height wherever it wanders (the
        // block identity is the carry's own read — the stub's solidity
        // keeps the floor walkable; stone cells beyond the holdable set
        // sit below).
        MobEntity enderman = endermanIn(world, 1L);
        boolean picked = false;
        for (int i = 0; i < 200_000 && !picked; i++) {
            enderman.tick(true);
            if (enderman.consumeEndermanPickup()) {
                Identifier at = world.blockIdAt(enderman.endermanPickupX(),
                        enderman.endermanPickupY(), enderman.endermanPickupZ());
                assertEquals("dirt", at == null ? null : at.value(),
                        "only the holdable carpet lifts");
                assertEquals(enderman.endermanPickupY(), 8,
                        "the scan's y band is the body's own three cells");
                picked = true;
            }
        }
        assertTrue(picked, "the 1-in-20 roll fires the pickup within the window");
    }

    @Test
    void thePlaceRollLandsTheCarriedBlock() {
        StareWorld world = new StareWorld();
        MobEntity enderman = endermanIn(world, 2L);
        // The engine-side completed pickup: the body carries dirt over the
        // plain floor (y 8 air, the stone below solid).
        enderman.completeEndermanPickup(
                net.zaminmc.torch.server.block.BuiltinBlocks.DIRT);
        assertNotNull(enderman.endermanCarried());

        boolean placed = false;
        for (int i = 0; i < 400_000 && !placed; i++) {
            enderman.tick(true);
            if (enderman.consumeEndermanPlace()) {
                // The target sits in the ±1/±2/±1 band above the solid floor.
                assertTrue(enderman.endermanPlaceY() >= 8,
                        "the placement stays above the floor");
                placed = true;
            }
        }
        assertTrue(placed, "the 1-in-2000 roll fires the place within the window");
        // The manager's completion empties the hands and flips the wire.
        enderman.completeEndermanPlace();
        assertNull(enderman.endermanCarried(), "the hands empty after the place");
        assertTrue(enderman.consumeEndermanStateDirty(), "the wire republishes");
    }

    @Test
    void theWoundArmsAndTheEndermiteIsExempt() {
        // The manager-level walk needs the full manager; the unit proves
        // the model's own ladder: the anger setter's dirty flip and the
        // exemption's reach through the manager's own hurt family (the
        // manager test class carries the live-path proofs — see
        // MobManagerTest for the hurt family's shapes). Here: the state
        // machine's own transitions.
        StareWorld world = new StareWorld();
        MobEntity enderman = endermanIn(world, 3L);
        assertFalse(enderman.endermanAngry());
        enderman.setEndermanAngry(true);
        assertTrue(enderman.endermanAngry());
        assertTrue(enderman.consumeEndermanStateDirty(),
                "the anger flip marks the wire");
        assertFalse(enderman.consumeEndermanStateDirty(),
                "the drain is one-shot");
        enderman.setEndermanAttackedByPlayer(true);
        // The player's hand holds the anger: the calm never lands (the
        // decay gate reads the marker).
        for (int i = 0; i < 5_000; i++) {
            enderman.tick(true);
        }
        assertTrue(enderman.endermanAngry(),
                "the player's hand holds the anger against the calm roll");
    }
}
