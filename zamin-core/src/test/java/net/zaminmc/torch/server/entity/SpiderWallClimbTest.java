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
 * THE SPIDER FAMILY'S WALL CLIMB (SpiderEntity.tick line 67 +
 * LivingEntity.travel lines 1165-1184 over SpiderEntity.isClimbing's
 * override): the post-move horizontal clip flags the wall (the wire's
 * byte-16 bit 0), the flag's next-tick read clamps the horizontal input
 * to ±0.15 and the sink to -0.15, and a climbing body still pressing
 * the wall pushes up at {@code velocityY = 0.2}.
 *
 * <p>The scene: a floor at y 63 (top 64) and the wall column at x=1
 * rising to y 72 — the spider (1.4 wide, 0.9 tall) stands at x 0.25 (box
 * -0.45..0.95), facing +X (yaw -90) so the velocity press drives the
 * clip at the wall's face x=1.0.
 */
class SpiderWallClimbTest {

    /** The scene's stub: a cells map over stone, the solver's surface. */
    private static final class WallWorld implements MobEntity.WorldQuery {
        final Map<Long, BlockType> cells = new HashMap<>();

        void stone(int x, int y, int z) {
            cells.put(((long) (x & 0xFFF) << 44) | ((long) (z & 0xFFF) << 32)
                    | (y & 0xFFFFFFFFL),
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
        }

        BlockType read(int x, int y, int z) {
            if (y == 0) {
                return net.zaminmc.torch.server.block.BuiltinBlocks.BEDROCK;
            }
            BlockType type = cells.get(
                    ((long) (x & 0xFFF) << 44) | ((long) (z & 0xFFF) << 32)
                            | (y & 0xFFFFFFFFL));
            return type != null ? type : net.zaminmc.torch.server.block.BuiltinBlocks.AIR;
        }

        /** The floor + the tall wall at x=1 (the climb never reaches its top). */
        void baseScene() {
            for (int z = -1; z <= 1; z++) {
                stone(0, 63, z); // the floor's cells (top 64.0)
                for (int y = 64; y <= 72; y++) {
                    stone(1, y, z); // the wall: the face at x=1.0
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
            return false; // the dry scene: the climb is not the pool-exit hop
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public BlockType blockAt(int x, int y, int z) {
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

    private static MobEntity spider(WallWorld world, double x) {
        MobEntity body = new MobEntity(93, MobType.SPIDER, new Position(x, 64.0, 0.5),
                new Random(7), world);
        body.setBodyYaw(-90.0f); // facing +X: the press drives the clip
        return body;
    }

    @Test
    void theSpiderFlagsTheWallThenPushesUp() {
        WallWorld world = new WallWorld();
        world.baseScene();
        MobEntity body = spider(world, 0.25);
        body.setVelocity(0.1, 0.0, 0.0);

        // The first press: the move clips the wall's face — the flag turns
        // on at the tick's end (SpiderEntity.tick line 67's own law), and
        // the push-up LAGS a tick (the reference's travel reads the flag
        // the previous tick set).
        body.tick(false);
        assertTrue(body.spiderClimbingWall(), "the first press flags the wall");
        assertFalse(body.velocityY() > 0.19f,
                "the push-up never fires on the flag's own tick");

        // The second press: the flag's read arms the climb — the body
        // still pressing the wall pushes up at 0.2 (travel line 1184).
        body.setVelocity(0.1, body.velocityY(), 0.0);
        body.tick(false);
        assertEquals(0.2f, (float) body.velocityY(), 1e-6f,
                "the second press pushes up at 0.2");
    }

    @Test
    void theClimbCarriesTheBodyUpTheWall() {
        WallWorld world = new WallWorld();
        world.baseScene();
        MobEntity body = spider(world, 0.25);
        body.setVelocity(0.1, 0.0, 0.0);
        body.tick(false); // the flag turns on
        double baseY = body.position().y();
        int pushes = 0;
        for (int i = 0; i < 6; i++) {
            body.setVelocity(0.1, body.velocityY(), 0.0);
            body.tick(false);
            if (body.velocityY() > 0.19f) {
                pushes++;
            }
        }
        assertTrue(pushes >= 5, "the push-up re-armed on every pressing tick");
        assertTrue(body.position().y() > baseY + 0.4,
                "the body climbed the wall: " + body.position().y());
    }

    @Test
    void theWallFlagNeverArmsOffTheWall() {
        WallWorld world = new WallWorld();
        world.baseScene();
        // Far from the wall (x 0.25 is the press seat; the bare stand at
        // x -2 never reaches the face): no press, no clip, no flag.
        MobEntity body = spider(world, -2.0);
        body.setVelocity(0.0, 0.0, 0.0);
        body.tick(false);
        assertFalse(body.spiderClimbingWall(), "no clip, no flag");
        assertFalse(body.velocityY() > 0.19f, "no climb without the wall");
    }

    @Test
    void theNonSpiderNeverClimbsTheWall() {
        WallWorld world = new WallWorld();
        world.baseScene();
        // The cow (0.9 wide) flush at the face — the same press, but the
        // wall flag is the spider family's own tick law.
        MobEntity body = new MobEntity(92, MobType.COW, new Position(0.55, 64.0, 0.5),
                new Random(7), world);
        body.setBodyYaw(-90.0f);
        for (int i = 0; i < 3; i++) {
            body.setVelocity(0.1, 0.0, 0.0);
            body.tick(false);
        }
        assertFalse(body.velocityY() > 0.19f,
                "the cow presses the wall and never climbs");
    }
}
