package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The world-spawn resolution's laws (T-SPAWN — the terrain family audit's
 * second fix): the reference's own three-walk shape resolved against the
 * GENERATED blocks — the grass-column walk (ServerWorld.initSpawnPoint
 * lines 660-680 over Dimension.isValidSpawnPoint's grass gate), the
 * surface consult (World.getSurfaceHeight lines 1132-1148), and the
 * unstuck walk (ServerPlayerEntity's constructor lines 158-160).
 */
class WorldSpawnResolverParityTest {

    private static final BlockType GRASS =
            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK;
    private static final BlockType LEAVES =
            net.zaminmc.torch.server.block.BuiltinBlocks.OAK_LEAVES;
    private static final BlockType STONE =
            net.zaminmc.torch.server.block.BuiltinBlocks.STONE;
    private static final BlockType WATER = new net.zaminmc.torch.server.block.EngineBlockType(
            Identifier.parse("minecraft:water"), "Water");

    /** A stub world: the refused columns answer a non-grass block. */
    private static final class StubGround implements WorldSpawnResolver.Ground {
        /** The refused column set ("x,z" keys) with their surface blocks. */
        final java.util.Map<String, BlockType> refused = new java.util.HashMap<>();
        /** The surface-height answers (the first free cell). */
        int surfaceHeight = 65;
        /** The solid cells ("x,y,z" keys) — the canopy of the unstuck walk. */
        final java.util.Set<String> solid = new java.util.HashSet<>();

        private static String key(int x, int z) {
            return x + "," + z;
        }

        @Override
        public BlockType surfaceBlock(int x, int z) {
            BlockType refusedAnswer = refused.get(key(x, z));
            return refusedAnswer != null ? refusedAnswer : GRASS;
        }

        @Override
        public int surfaceHeight(int x, int z) {
            return surfaceHeight;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return solid.contains(x + "," + y + "," + z);
        }
    }

    /**
     * The stand: a grass column answers the scan — no drift, the surface
     * consult's cell is the spawn Y (initSpawnPoint's while never runs; the
     * ctor's getSurfaceHeight answers).
     */
    @Test
    void theGrassColumnStands() {
        StubGround ground = new StubGround();
        ground.surfaceHeight = 65;
        Position scan = new Position(10.5, 66.0, 10.5);
        Position resolved = WorldSpawnResolver.resolve(ground, 42L, scan);
        assertEquals(10.5, resolved.x());
        assertEquals(10.5, resolved.z());
        assertEquals(65.0, resolved.y(), "the free cell above the grass");
    }

    /**
     * The grass walk (initSpawnPoint lines 671-680): a refused column (a
     * tree — the leaves answer) never hosts the spawn; the ±64 drift finds
     * a grass column.
     */
    @Test
    void theTreeColumnIsRefusedAndTheWalkFindsGrass() {
        StubGround ground = new StubGround();
        ground.refused.put("10,10", LEAVES); // the scan column carries a tree
        Position scan = new Position(10.5, 66.0, 10.5);
        Position resolved = WorldSpawnResolver.resolve(ground, 42L, scan);
        assertNotEquals(10, (int) Math.floor(resolved.x()) + (int) Math.floor(resolved.z()),
                "the walk moved off the tree column");
        assertEquals(65.0, resolved.y());
        // The settled column's surface is the grass (the refusal law).
        BlockType settled = (BlockType) ground.surfaceBlock(
                (int) Math.floor(resolved.x()), (int) Math.floor(resolved.z()));
        assertEquals(GRASS, settled, "the spawn settles on a grass column");
    }

    /**
     * The ocean column is refused the same way (Dimension.isValidSpawnPoint
     * line 72: only GRASS answers) — the pre-fix fallback sites (the
     * probe's ocean spawns) can no longer host the world spawn.
     */
    @Test
    void theOceanColumnIsRefused() {
        StubGround ground = new StubGround();
        ground.refused.put("0,0", WATER); // the probe's fallback column, over water
        Position scan = new Position(0.5, 64.0, 0.5);
        Position resolved = WorldSpawnResolver.resolve(ground, 42L, scan);
        BlockType settled = (BlockType) ground.surfaceBlock(
                (int) Math.floor(resolved.x()), (int) Math.floor(resolved.z()));
        assertEquals(GRASS, settled, "the spawn never stands over open water");
        assertFalse((int) Math.floor(resolved.x()) == 0
                && (int) Math.floor(resolved.z()) == 0,
                "the drifted column left the water behind");
    }

    /**
     * The unstuck walk (the fresh-body placement's own escape,
     * ServerPlayerEntity lines 158-160): a canopy over the landing cell
     * pushes the spawn up until the body's cells are free.
     */
    @Test
    void theCanopyAboveTheLandingIsWalkedUp() {
        StubGround ground = new StubGround();
        ground.surfaceHeight = 65; // the leaves-skipping consult: the grass cell
        ground.solid.add("10,65,10"); // the canopy's lower ring
        ground.solid.add("10,66,10");
        Position scan = new Position(10.5, 66.0, 10.5);
        Position resolved = WorldSpawnResolver.resolve(ground, 42L, scan);
        assertEquals(67.0, resolved.y(),
                "the walk steps up through the solid cells (the feet cell 65,"
                        + " the head cell 66 — free at 67)");
    }

    /**
     * The reference's own 1000-attempt cap (initSpawnPoint line 676): a
     * world without a single grass column still terminates, keeping the
     * last drifted column's surface cell.
     */
    @Test
    void theCapTerminatesTheWalk() {
        // A never-grass world: every consult answers stone. The walk must
        // still terminate at the reference's own cap — the exact consult
        // count is the deterministic seat (the initial check + the 1000
        // drifts, the reference's break at line 676).
        int[] consults = {0};
        WorldSpawnResolver.Ground stony = new WorldSpawnResolver.Ground() {
            @Override
            public BlockType surfaceBlock(int x, int z) {
                consults[0]++;
                return STONE;
            }

            @Override
            public int surfaceHeight(int x, int z) {
                return 70;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return false;
            }
        };
        Position scan = new Position(10.5, 66.0, 10.5);
        Position resolved = WorldSpawnResolver.resolve(stony, 42L, scan);
        assertEquals(70.0, resolved.y(), "the surface consult answers the cap's column");
        assertEquals(1000, consults[0],
                "the reference's own 1000-attempt cap (one consult per walk step)");
    }
}
