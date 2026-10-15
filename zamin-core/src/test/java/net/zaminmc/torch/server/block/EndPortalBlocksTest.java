package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end portal family (Slice 54 — EndPortalBlock/EnderEyeItem 1:1): the
 * unbreakable film block and the frame-complete fill walk (the reference's
 * own geometry over the vanilla stronghold ring).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndPortalBlocksTest {

    private static BlockRegistryBuilder.FrozenBlockRegistry registry;
    private static net.zaminmc.torch.server.world.EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new net.zaminmc.torch.server.world.EngineWorld("test", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
    }

    /** The frame world adapter over the live world. */
    private EndPortalBlocks.FrameWorld frameWorld() {
        return new EndPortalBlocks.FrameWorld() {
            @Override
            public net.zaminmc.torch.block.BlockType getBlock(BlockPosition at) {
                return world.getBlock(at);
            }

            @Override
            public void setBlock(BlockPosition at, net.zaminmc.torch.block.BlockType type) {
                world.setBlock(at, type);
            }
        };
    }

    private void frame(int x, int z, String facing, boolean eye) {
        world.setBlock(new BlockPosition(x, 6, z), registry.lookup(Identifier.parse(
                "minecraft:end_portal_frame" + (eye ? "_eye_" : "_") + facing)).orElseThrow());
    }

    @Test
    void thePortalBlockCarriesTheReferenceRows() {
        // The unbreakable row: hardness -1, never diggable, no drops.
        BlockBehavior behavior = BlockBehaviorTable.of(EndPortalBlocks.END_PORTAL).orElseThrow();
        assertEquals(-1.0, behavior.hardness());
        assertFalse(behavior.diggable());
        assertTrue(behavior.drops().isEmpty());
        // The light rows: the setLight(1.0F) emission 15, the CUTOUT film
        // passes whole.
        assertEquals(15, BlockLightTable.lookup(EndPortalBlocks.END_PORTAL).orElseThrow().emission());
        assertEquals(0, BlockLightTable.lookup(EndPortalBlocks.END_PORTAL).orElseThrow().filter());
        // Walk-through: the film never blocks a body.
        assertFalse(net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                registry.require(EndPortalBlocks.END_PORTAL)));
        assertTrue(!net.zaminmc.torch.server.block.CollisionShapes.contains(
                registry.require(EndPortalBlocks.END_PORTAL), 0.5, 0.5, 0.5,
                net.zaminmc.torch.server.block.CollisionShapes.ISOLATED),
                "the portal film never blocks a body");
        // The frame reads: empty vs eyed, the facing preserved.
        assertTrue(EndPortalBlocks.isEmptyFrame(
                Identifier.parse("minecraft:end_portal_frame_west")));
        assertTrue(EndPortalBlocks.isEyedFrame(
                Identifier.parse("minecraft:end_portal_frame_eye_west")));
        assertEquals(1, EndPortalBlocks.frameFacingOf(
                Identifier.parse("minecraft:end_portal_frame_eye_west")));
        assertEquals("minecraft:end_portal_frame_eye_east", EndPortalBlocks
                .eyedFrameOf(Identifier.parse("minecraft:end_portal_frame_east")).orElseThrow()
                .toString());
    }

    @Test
    void theFrameCompleteWalkFillsTheVanillaRing() {
        // The vanilla ring: the 3x3 interior at (1..3, z 1..3), the twelve
        // frames around it — the near row at z=0 (facing south, into the
        // interior), the far row at z=4 (facing north), the side columns at
        // x=0 (facing east) and x=4 (facing west). Eleven frames carry
        // their eyes; the twelfth (the clicked one at (2,0)) waits.
        world.getOrGenerate(new ChunkPosition(0, 0));
        for (int x = 1; x <= 3; x++) {
            frame(x, 0, "south", true);
            frame(x, 4, "north", true);
        }
        for (int z = 1; z <= 3; z++) {
            frame(0, z, "east", true);
            frame(4, z, "west", true);
        }
        BlockPosition clicked = new BlockPosition(2, 6, 0);
        world.setBlock(clicked, registry.lookup(
                Identifier.parse("minecraft:end_portal_frame_south")).orElseThrow());

        // An incomplete ring never fills.
        assertFalse(EndPortalBlocks.tryFillPortal(frameWorld(), clicked, 0));

        // The last eye: the clicked frame joins the ring, the walk fills
        // the 3x3 interior.
        world.setBlock(clicked, registry.lookup(
                Identifier.parse("minecraft:end_portal_frame_eye_south")).orElseThrow());
        assertTrue(EndPortalBlocks.tryFillPortal(frameWorld(), clicked, 0));
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                assertEquals(EndPortalBlocks.END_PORTAL,
                        world.getBlock(new BlockPosition(x, 6, z)).identifier(),
                        "the interior filled at (" + x + ",6," + z + ")");
            }
        }
        // The frames themselves are untouched by the fill.
        assertEquals(Identifier.parse("minecraft:end_portal_frame_eye_north"),
                world.getBlock(new BlockPosition(2, 6, 4)).identifier());
    }

    @Test
    void anEmptyFrameInTheRingRefusesTheFill() {
        // The same ring with ONE far-row eye missing: the walk gives up
        // even when the clicked frame is eyed.
        world.getOrGenerate(new ChunkPosition(0, 0));
        for (int x = 9; x <= 11; x++) {
            frame(x, 8, "south", true);
            frame(x, 12, "north", x != 11); // (11,12) stays empty
        }
        for (int z = 9; z <= 11; z++) {
            frame(8, z, "east", true);
            frame(12, z, "west", true);
        }
        BlockPosition clicked = new BlockPosition(10, 6, 8);
        world.setBlock(clicked, registry.lookup(
                Identifier.parse("minecraft:end_portal_frame_eye_south")).orElseThrow());
        assertFalse(EndPortalBlocks.tryFillPortal(frameWorld(), clicked, 0));
        assertEquals(world.airType(), world.getBlock(new BlockPosition(10, 6, 10)),
                "nothing filled");
    }
}
