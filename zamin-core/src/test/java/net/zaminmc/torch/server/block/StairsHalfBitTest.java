package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stairs' HALF bit (Slice L1 — StairsBlock's upside-down forms): the
 * reference's own metadata writer folds every stair state as
 * {@code (HALF == TOP ? 4 : 0) | (5 - FACING.getId())} — the E=0 W=1 S=2
 * N=3 facing band with the HALF bit at 4. The placement law
 * (StairsBlock.getPlacementState lines 493-500): the underside click
 * hangs the upside-down form, the top face lays the plain, a side face
 * splits on the cursor's upper half — the slab law's own shape.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StairsHalfBitTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "stairs", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void hold(PlayerSession player, ItemType type, int count) {
        player.inventory().setSlot(0, ItemStack.of(type, count));
        player.inventory().selectHotbarSlot(0);
    }

    /** One right-click use with the cursor fractions, awaited. */
    private void useOn(PlayerSession player, BlockPosition at, int face,
                       float cursorY) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                0.5f, cursorY, 0.5f, windowId -> { });
        Thread.sleep(120);
    }

    @Test
    void theTopStatesCarryTheHalfBitNibble() {
        // The hand-declared families: the metadata field is the reference's
        // own fold — facing band | 4.
        assertEquals(4, BuiltinBlocks.OAK_STAIRS_EAST_TOP.legacyMetadata());
        assertEquals(5, BuiltinBlocks.OAK_STAIRS_WEST_TOP.legacyMetadata());
        assertEquals(6, BuiltinBlocks.OAK_STAIRS_SOUTH_TOP.legacyMetadata());
        assertEquals(7, BuiltinBlocks.OAK_STAIRS_NORTH_TOP.legacyMetadata());
        assertEquals(4, BuiltinBlocks.COBBLESTONE_STAIRS_EAST_TOP.legacyMetadata());
        assertEquals(7, BuiltinBlocks.NETHER_BRICK_STAIRS_NORTH_TOP.legacyMetadata());
        assertEquals(4, BuiltinBlocks.SANDSTONE_STAIRS_EAST_TOP.legacyMetadata());
        assertEquals(5, RedSandstoneBlocks.stairsTopState(1).legacyMetadata());
        // The eight loop families.
        for (String family : WoodShapeBlocks.STAIRS.keySet()) {
            for (int s = 0; s < 4; s++) {
                assertEquals(s | 4, WoodShapeBlocks.stairsTopStates(family).get(s)
                        .legacyMetadata(), family + " top state " + s);
            }
        }
        // The consult recognizes the upside-down forms.
        assertTrue(WoodShapeBlocks.isStairsState("minecraft:spruce_stairs_east_top"));
        assertTrue(WoodShapeBlocks.isStairsTopState("minecraft:quartz_stairs_north_top"));
        assertTrue(RedSandstoneBlocks.isStairs(
                Identifier.parse("minecraft:red_sandstone_stairs_west_top")));
    }

    @Test
    void theTopStatesCarryTheFamilysTables() {
        // The behavior rows: the top states drop the family item exactly
        // like the bottom forms (the vanilla per-block-id scheme).
        var oakTop = BlockBehaviorTable.of(
                Identifier.parse("minecraft:oak_stairs_east_top")).orElseThrow();
        assertEquals(2.0, oakTop.hardness());
        assertEquals("wood", oakTop.material());
        assertTrue(!oakTop.requiresTool(), "the wood stairs never gate their drops");
        assertEquals(Identifier.parse("minecraft:oak_stairs"), oakTop.drops().get(0).item());
        var spruceTop = BlockBehaviorTable.of(
                Identifier.parse("minecraft:spruce_stairs_south_top")).orElseThrow();
        assertEquals(Identifier.parse("minecraft:spruce_stairs"), spruceTop.drops().get(0).item());
        var redTop = BlockBehaviorTable.of(
                Identifier.parse("minecraft:red_sandstone_stairs_north_top")).orElseThrow();
        assertEquals(Identifier.parse("minecraft:red_sandstone_stairs"),
                redTop.drops().get(0).item());
        // The light rows: the opaque-partial band.
        assertEquals(15, BlockLightTable.lookup(
                Identifier.parse("minecraft:oak_stairs_west_top")).orElseThrow().filter());
        assertEquals(15, BlockLightTable.lookup(
                Identifier.parse("minecraft:brick_stairs_east_top")).orElseThrow().filter());
        // The collision law (setBaseShapeForCollisions lines 74-81): the
        // top form fills the cell's UPPER half — the base half box alone,
        // the 1.8.8 law (the tall half's body carries no collision).
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_STAIRS_EAST_TOP,
                0.5, 0.75, 0.5, CollisionShapes.ISOLATED),
                "the upside-down stair's upper half collides");
        assertTrue(!CollisionShapes.contains(BuiltinBlocks.OAK_STAIRS_EAST_TOP,
                0.5, 0.25, 0.5, CollisionShapes.ISOLATED),
                "the underside stays open below the top stair");
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_STAIRS_EAST,
                0.5, 0.25, 0.5, CollisionShapes.ISOLATED),
                "the plain stair's lower half keeps its box");
        // The dig sound rule: the woods dig wood, the masonry the stone
        // default.
        assertEquals(java.util.Optional.of("dig.wood"),
                net.zaminmc.torch.server.fx.BlockSoundMap.digSound(
                        Identifier.parse("minecraft:birch_stairs_north_top")));
        assertEquals(java.util.Optional.of("dig.wood"),
                net.zaminmc.torch.server.fx.BlockSoundMap.digSound(
                        Identifier.parse("minecraft:dark_oak_stairs_east")));
        assertTrue(net.zaminmc.torch.server.fx.BlockSoundMap.digSound(
                Identifier.parse("minecraft:stone_brick_stairs_east_top")).isEmpty(),
                "the masonry set carries no wood row");
    }

    @Test
    void thePlacementLawHangsTheUndersideAndSplitsTheSides() throws Exception {
        server = boot();
        PlayerSession builder = join("StairBuilder");
        var world = server.world();
        ItemType oakStairs = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:oak_stairs")).orElseThrow();

        // A floor column with a ceiling two above: the underside click
        // targets the cell between (face 0 against the ceiling).
        BlockPosition floor = new BlockPosition(
                (int) Math.floor(builder.position().x()) + 2,
                (int) Math.floor(builder.position().y()) - 1,
                (int) Math.floor(builder.position().z()));
        BlockPosition ceil = floor.offset(0, 3, 0);
        server.ticker().submit(() -> {
            world.setBlock(floor, BuiltinBlocks.STONE);
            world.setBlock(ceil, BuiltinBlocks.STONE);
        });
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE)
                && world.getBlock(ceil).equals(BuiltinBlocks.STONE), "frame seeded");
        BlockPosition lowCell = floor.offset(0, 1, 0);
        BlockPosition highCell = floor.offset(0, 2, 0);

        // The TOP face lays the plain form (getPlacementState's UP arm).
        hold(builder, oakStairs, 4);
        useOn(builder, floor, 1, 0.5f);
        assertTrue(world.getBlock(lowCell).identifier().value().startsWith("oak_stairs_"),
                "the stair commits");
        assertTrue(!WoodShapeBlocks.isStairsTopState(
                world.getBlock(lowCell).identifier().value()),
                "the top face lays the plain form");

        // The UNDER SIDE click (face 0 against the ceiling) hangs the
        // upside-down form.
        useOn(builder, ceil, 0, 0.5f);
        assertEquals("minecraft:oak_stairs_" + facingOf(world.getBlock(lowCell)) + "_top",
                world.getBlock(highCell).identifier().toString(),
                "the underside click hangs the upside-down stair");

        // The SIDE face split: the cursor's upper half hangs the top form,
        // the lower half lays the plain. The floor's own EAST face (the
        // target one column over, well inside the 4.5 reach).
        BlockPosition sideCell = floor.offset(1, 0, 0);
        server.ticker().submit(() -> {
            world.setBlock(lowCell, BuiltinBlocks.AIR);
            world.setBlock(highCell, BuiltinBlocks.AIR);
            world.setBlock(sideCell, BuiltinBlocks.AIR);
        });
        await(() -> world.getBlock(lowCell).equals(BuiltinBlocks.AIR)
                && world.getBlock(sideCell).equals(BuiltinBlocks.AIR), "the cells cleared");
        useOn(builder, floor, 5, 0.75f);
        assertTrue(WoodShapeBlocks.isStairsTopState(
                world.getBlock(sideCell).identifier().value()),
                "the upper-half side click hangs the top form");
        server.ticker().submit(() -> world.setBlock(sideCell, BuiltinBlocks.AIR));
        await(() -> world.getBlock(sideCell).equals(BuiltinBlocks.AIR), "the side cell cleared");
        useOn(builder, floor, 5, 0.25f);
        assertTrue(world.getBlock(sideCell).identifier().value().startsWith("oak_stairs_")
                        && !WoodShapeBlocks.isStairsTopState(
                                world.getBlock(sideCell).identifier().value()),
                "the lower-half side click lays the plain form");
        server.shutdown(null);
    }

    /** The facing suffix of a committed bottom stair state. */
    private static String facingOf(net.zaminmc.torch.block.BlockType state) {
        String value = state.identifier().value();
        return value.substring("oak_stairs_".length());
    }
}
