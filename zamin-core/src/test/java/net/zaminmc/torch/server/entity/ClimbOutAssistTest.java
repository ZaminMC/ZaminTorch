package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE CLIMB-OUT ASSIST (LivingEntity.travel lines 1210/1238 — the water and
 * lava arms' shared pool-exit law, the step model's own slice): a body in
 * fluid pushing against a wall (the move's collidingHorizontally) that can
 * occupy the dry ledge 0.6 above its pre-move position hops out with
 * {@code velocityY = 0.3F}.
 *
 * <p>The scene: a 1-wide pool at x=0 — the water spans [64, 66) (two
 * cells), the pool's bottom stone at (0, 63) — the rim stone at x=1 rising
 * to cell 66 (the face at x=1.0, the surface 67 — one block above the
 * water's surface 66, too tall for the 0.5 step retry, so the push clips).
 * The cow (0.9 wide, 1.3 tall) floats at y 65.5: the mid-band 65.7 in the
 * water, the head 66.8 below the overhang band.
 */
class ClimbOutAssistTest {

    /** The scene's stub: a cells map, a water span, the solver's surface. */
    private static final class PoolWorld implements MobEntity.WorldQuery {
        final Map<Long, BlockType> cells = new HashMap<>();
        /** The water's upper bound (exclusive): [64, waterTop). */
        double waterTop = 66.0;
        boolean overhangAtExit;

        void stone(int x, int y, int z) {
            cells.put(((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL),
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
        }

        BlockType read(int x, int y, int z) {
            if (y == 0) {
                return net.zaminmc.torch.server.block.BuiltinBlocks.BEDROCK;
            }
            BlockType type = cells.get(
                    ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL));
            return type != null ? type : net.zaminmc.torch.server.block.BuiltinBlocks.AIR;
        }

        /** The base scene: the pool bottom, the rim column, the water. */
        void baseScene() {
            stone(0, 63, 0); // the pool's bottom (top 64.0)
            for (int z = -1; z <= 1; z++) {
                for (int y = 64; y <= 66; y++) {
                    stone(1, y, z); // the rim: the face at x=1.0, top 67.0
                }
            }
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return net.zaminmc.torch.server.block.WorldSolidity.isSolid(read(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return y >= 64.0 && y < waterTop; // the pool's column at x=0
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public BlockType blockAt(int x, int y, int z) {
                        // The overhang blocks the exit band but never the
                        // body's own box (cell 67 sits above the head 66.8).
                        if (overhangAtExit && x == 0 && y == 67 && z == 0) {
                            return net.zaminmc.torch.server.block.BuiltinBlocks.STONE;
                        }
                        return read(x, y, z);
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

    private static MobEntity cow(PoolWorld world, double x) {
        // The flush placement: the 0.9-wide body at x has its box's max at
        // x + 0.45; at x 0.55 the box is 0.1..1.0 — flush against the rim's
        // face at 1.0. Facing +X (yaw -90: -sin(-90) = +1) so the walk's
        // push drives the clip; velocityX stays 0 like the reference's
        // post-clip zeroed terms.
        MobEntity body = new MobEntity(92, MobType.COW, new Position(x, 65.5, 0.5),
                new Random(7), world);
        body.setBodyYaw(-90.0f);
        // The reference's swimmer pushes into the wall — the reference's
        // move ZEROES the velocity on the clip (Entity.move's horizontal
        // arm), so the climb-out consult sees the applied motion (dx 0 at
        // the face). The engine drives the same push through the velocity
        // field (its walk adds nothing without a path); the consult reads
        // the APPLIED dx/dz (0 at the clip) like the reference's zeroed
        // terms.
        body.setVelocity(0.1, 0.0, 0.0);
        return body;
    }

    @Test
    void theWalledSwimmerAtTheSurfaceHopsOut() throws Exception {
        PoolWorld world = new PoolWorld();
        world.baseScene();
        MobEntity body = cow(world, 0.55);
        assertTrue(world.inFluid(0.55, 65.7, 0.5), "the body floats in the water");
        body.tick(false);
        // The push clipped at the rim's face (collidingHorizontally) and the
        // dry ledge above the water (cells 66-67 at the body's column) is
        // free: the pool-exit hop arms.
        assertEquals(0.3f, (float) body.velocityY(), 1e-6f,
                "the climb-out sets velocityY = 0.3");
    }

    @Test
    void anOverhangAboveTheExitBandRefusesTheHop() throws Exception {
        PoolWorld world = new PoolWorld();
        world.baseScene();
        world.overhangAtExit = true; // a stone at (0, 67): the exit band blocked
        MobEntity body = cow(world, 0.55);
        body.tick(false);
        assertFalse(body.velocityY() > 0.29f,
                "the blocked ledge never hops (the gather finds the overhang)");
    }

    @Test
    void aLiquidExitColumnRefusesTheHop() throws Exception {
        PoolWorld world = new PoolWorld();
        world.baseScene();
        world.waterTop = 68.0; // the water raised through the exit band
        MobEntity body = cow(world, 0.55);
        assertTrue(world.inFluid(0.55, 65.7, 0.5), "still in the water");
        body.tick(false);
        assertFalse(body.velocityY() > 0.29f,
                "containsLiquid refuses the exit (the ledge must be dry)");
    }

    @Test
    void aBodyAwayFromTheWallNeverClips() throws Exception {
        PoolWorld world = new PoolWorld();
        world.baseScene();
        // 0.35 from the face: no velocity push, no walk (no path) — the
        // move never reaches the rim's face, no clip, no hop.
        MobEntity body = cow(world, 0.2);
        body.setVelocity(0.0, 0.0, 0.0);
        body.tick(false);
        assertFalse(body.velocityY() > 0.29f,
                "no clip, no hop (the law needs the horizontal collision)");
    }

    @Test
    void theHopCarriesTheBodyUpThroughTheWater() throws Exception {
        PoolWorld world = new PoolWorld();
        world.baseScene();
        // The start inside the hop's window (the mid-band submerged at
        // 65.65 < 66 AND the exit box clearing the water: preY 65.45 + 0.6
        // > 66): the hop re-fires every tick while the body pushes, each
        // hop carrying ~0.25 up (the water arm's own model: the hop's 0.3
        // rewrite + the FLOAT input's +0.04, then the arm's drag
        // (0.3 + 0.04) * 0.8 - 0.02 = 0.252 per tick — the retired
        // composite crawled at 0.032/tick, the clamp's own pace); once
        // the mid-band clears the surface (y > 65.8) the engine's POINT
        // fluid consult ends the arm (the reference's box-level water
        // read would carry one more hop — the named difference this slice
        // records). Three hops carry the body from 65.45 past the surface
        // band; the fourth tick's consult has flipped (mid-band 66.17 >
        // 66), the land arm takes over, and the climb-out window closes.
        MobEntity body = new MobEntity(92, MobType.COW,
                new Position(0.55, 65.45, 0.5), new Random(7), world);
        body.setBodyYaw(-90.0f);
        body.setVelocity(0.1, 0.0, 0.0);
        // The honest choreography pin: the hop re-fires every tick while
        // the window holds (velocityY 0.3 at every in-window tick's end)
        // and the body rises through it at the water arm's own pace. The
        // RISE'S PACE is the reference's water-arm form now (the F1 order
        // note still phases it one tick — the drag lands before the move,
        // the ledger's own standing note).
        double firstY = body.position().y();
        int hopArmed = 0;
        for (int i = 0; i < 6; i++) {
            body.setVelocity(0.1, body.velocityY(), 0.0);
            body.tick(false);
            if (body.velocityY() > 0.29f) {
                hopArmed++;
            }
        }
        assertEquals(3, hopArmed,
                "the hop re-armed every in-window tick; the POINT consult ends it at the surface band");
        assertTrue(body.position().y() > firstY + 0.1,
                "the body rose through the window: " + body.position().y());
        assertTrue(body.position().y() > 65.6,
                "the body climbed the surface band: " + body.position().y());
    }

}
