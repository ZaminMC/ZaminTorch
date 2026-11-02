package net.zaminmc.torch.server.skull;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wither's soul-sand summon (Slice 60 — SkullBlock.trySpawn lines
 * 167-223): the 3x3 pattern (three wither-skull tiles on a soul-sand T)
 * found in all four horizontal planes with the placed skull at any of
 * the three top cells, the gates (the type, the y floor, the peaceful
 * refusal), the clear walk (the whole build leaves, tiles with it), and
 * the birth (the top-center cell +0.5/+0.55/+0.5, the axis yaw).
 */
class SkullSummonTest {

    /** The map-backed world (the summon walk's own consults). */
    private static final class FlatWorld implements SkullManager.Host {
        final Map<BlockPosition, BlockType> blocks = new HashMap<>();
        Position spawnedAt;
        float spawnedYaw = Float.MIN_VALUE;
        int spawnedDimension = Integer.MIN_VALUE;
        int difficulty = 2;

        void set(BlockPosition at, BlockType type) {
            blocks.put(at, type);
        }

        @Override
        public BlockType blockAt(BlockPosition position) {
            return blocks.getOrDefault(position, BuiltinBlocks.AIR);
        }

        @Override
        public void setBlock(BlockPosition position, BlockType type) {
            blocks.put(position, type);
        }

        @Override
        public BlockType airType() {
            return BuiltinBlocks.AIR;
        }

        @Override
        public int difficulty() {
            return difficulty;
        }

        @Override
        public void summonWither(Position at, float bodyYawDegrees, int dimension) {
            spawnedAt = at;
            spawnedYaw = bodyYawDegrees;
            spawnedDimension = dimension;
        }
    }

    /** Builds the soul-sand T with three wither skulls. */
    private static void buildPattern(FlatWorld world, SkullManager skulls,
                                     int baseX, int baseY, int baseZ, int dx, int dz) {
        // The sand row.
        for (int i = -1; i <= 1; i++) {
            world.set(new BlockPosition(baseX + i * dx, baseY + 1, baseZ + i * dz),
                    BuiltinBlocks.SOUL_SAND);
        }
        // The base row (the air flanks come free in the empty world).
        world.set(new BlockPosition(baseX, baseY, baseZ), BuiltinBlocks.SOUL_SAND);
        // The skulls.
        for (int i = -1; i <= 1; i++) {
            BlockPosition at = new BlockPosition(baseX + i * dx, baseY + 2, baseZ + i * dz);
            world.set(at, BuiltinBlocks.SKULL);
            skulls.place(at, 1, 0);
        }
    }

    @Test
    void theWidthAlongXPlaneSummons() {
        FlatWorld world = new FlatWorld();
        SkullManager skulls = new SkullManager(world);
        // The T runs along X (dx=1, dz=0), the base at (10, 8, 5): the
        // skulls at y 10, the sand at y 9, the base at y 8.
        buildPattern(world, skulls, 10, 8, 5, 1, 0);
        BlockPosition placed = new BlockPosition(10, 10, 5); // the center skull
        assertTrue(skulls.trySummonWither(placed, 0), "the summon fired");
        // The birth: the top-center cell +0.5/+0.55/+0.5.
        assertEquals(new Position(10.5, 10.55, 5.5), world.spawnedAt,
                "the wither births at the top-center cell");
        assertEquals(0.0f, world.spawnedYaw, 1e-6f,
                "the X-run plane's forward axis reads yaw 0 (line 191)");
        assertEquals(0, world.spawnedDimension,
                "the wither stamps the acting dimension (SkullBlock line 185's "
                        + "new WitherEntity(world) — the skull's own world)");
        // The clear walk: all nine cells left.
        for (int i = -1; i <= 1; i++) {
            assertEquals(BuiltinBlocks.AIR, world.blockAt(
                    new BlockPosition(10 + i, 10, 5)), "the skull row cleared");
            assertEquals(BuiltinBlocks.AIR, world.blockAt(
                    new BlockPosition(10 + i, 9, 5)), "the sand row cleared");
        }
        assertEquals(BuiltinBlocks.AIR, world.blockAt(new BlockPosition(10, 8, 5)),
                "the base cleared");
        // Every tile left the store.
        assertNull(skulls.tileAt(new BlockPosition(10, 10, 5)), "the tiles left");
        assertNull(skulls.tileAt(new BlockPosition(9, 10, 5)), "the flank tiles left");
    }

    @Test
    void theWidthAlongZPlaneSummonsFromAnySlot() {
        // The T runs along Z (dz=1): the placed skull at the FAR end still
        // summons (the pattern search tries every slot).
        FlatWorld world = new FlatWorld();
        SkullManager skulls = new SkullManager(world);
        buildPattern(world, skulls, 20, 8, 20, 0, 1);
        BlockPosition placed = new BlockPosition(20, 10, 21); // the +Z end skull
        assertTrue(skulls.trySummonWither(placed, 0), "the far-end placement summons");
        assertEquals(new Position(20.5, 10.55, 20.5), world.spawnedAt,
                "the center is the base's center column");
        assertEquals(90.0f, world.spawnedYaw, 1e-6f,
                "the Z-run plane's forward axis reads yaw 90 (line 191)");
    }

    @Test
    void theGatesRefuseTheWrongSkullTheShallowFloorAndThePeacefulWorld() {
        FlatWorld world = new FlatWorld();
        SkullManager skulls = new SkullManager(world);
        buildPattern(world, skulls, 0, 8, 0, 1, 0);
        // A skeleton tile on the center skull (type 0): the whole consult
        // refuses before the pattern walk.
        BlockPosition skeletonSkull = new BlockPosition(0, 10, 0);
        world.set(skeletonSkull, BuiltinBlocks.SKULL);
        skulls.place(skeletonSkull, 0, 0);
        assertFalse(skulls.trySummonWither(skeletonSkull, 0), "the skeleton head never summons");
        assertNull(world.spawnedAt, "nothing spawned");
        // The y gate: a wither skull at y 1 (the pattern would need the
        // base at y -1 — below the world's floor band anyway).
        FlatWorld shallow = new FlatWorld();
        SkullManager shallowSkulls = new SkullManager(shallow);
        BlockPosition shallowSkull = new BlockPosition(5, 1, 5);
        shallow.set(shallowSkull, BuiltinBlocks.SKULL);
        shallowSkulls.place(shallowSkull, 1, 0);
        assertFalse(shallowSkulls.trySummonWither(shallowSkull, 0),
                "the shallow floor never summons");
        // The peaceful gate.
        FlatWorld peaceful = new FlatWorld();
        peaceful.difficulty = 0;
        SkullManager peacefulSkulls = new SkullManager(peaceful);
        buildPattern(peaceful, peacefulSkulls, 30, 8, 30, 1, 0);
        assertFalse(peacefulSkulls.trySummonWither(new BlockPosition(30, 10, 30), 0),
                "the peaceful world never summons");
        assertNull(peaceful.spawnedAt, "nothing spawned");
    }

    @Test
    void theIncompletePatternNeverSummons() {
        FlatWorld world = new FlatWorld();
        SkullManager skulls = new SkullManager(world);
        buildPattern(world, skulls, 40, 8, 40, 1, 0);
        // A flank skull replaced by air: no match.
        world.set(new BlockPosition(41, 10, 40), BuiltinBlocks.AIR);
        assertFalse(skulls.trySummonWither(new BlockPosition(40, 10, 40), 0),
                "the missing flank skull breaks the pattern");
        // A base flank filled with stone: the air gate fails.
        FlatWorld filled = new FlatWorld();
        SkullManager filledSkulls = new SkullManager(filled);
        buildPattern(filled, filledSkulls, 50, 8, 50, 1, 0);
        filled.set(new BlockPosition(51, 8, 50), BuiltinBlocks.STONE);
        assertFalse(filledSkulls.trySummonWither(new BlockPosition(50, 10, 50), 0),
                "the blocked base flank breaks the pattern");
        // A sand row with a netherrack hole: no match.
        FlatWorld holed = new FlatWorld();
        SkullManager holedSkulls = new SkullManager(holed);
        buildPattern(holed, holedSkulls, 60, 8, 60, 1, 0);
        holed.set(new BlockPosition(59, 9, 60), BuiltinBlocks.NETHERRACK);
        assertFalse(holedSkulls.trySummonWither(new BlockPosition(60, 10, 60), 0),
                "the wrong sand breaks the pattern");
        // A wither-skull BLOCK with a skeleton tile: the predicate reads
        // the TILE, not the block.
        FlatWorld typed = new FlatWorld();
        SkullManager typedSkulls = new SkullManager(typed);
        buildPattern(typed, typedSkulls, 70, 8, 70, 1, 0);
        typedSkulls.place(new BlockPosition(71, 10, 70), 0, 0);
        assertFalse(typedSkulls.trySummonWither(new BlockPosition(70, 10, 70), 0),
                "the mistyped flank tile breaks the pattern");
    }

    @Test
    void theTilesCarryTheTypeAndTheRotation() {
        FlatWorld world = new FlatWorld();
        SkullManager skulls = new SkullManager(world);
        BlockPosition at = new BlockPosition(3, 4, 3);
        skulls.place(at, 1, 7);
        SkullManager.SkullTile tile = skulls.tileAt(at);
        assertNotNull(tile, "the tile landed");
        assertEquals(1, tile.type(), "the wither type");
        assertEquals(7, tile.rotation(), "the floor rotation");
        assertEquals(1, skulls.tilesIn(at.chunkPosition().packed()).size(),
                "the chunk walk finds it");
        SkullManager.SkullTile removed = skulls.remove(at);
        assertEquals(1, removed.type(), "the removal returns the tile");
        assertNull(skulls.tileAt(at), "the store is empty");
    }
}
