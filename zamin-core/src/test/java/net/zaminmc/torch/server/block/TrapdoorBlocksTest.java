package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trapdoor pair (Slice 39 — TrapdoorBlock 1:1): the wall-mount
 * placement (the horizontal face rule, the facing band, the cursor's half,
 * the canBePlacedOn gate), the wood's manual swing and the iron's material
 * refusal, the signal arm's open/close edge (the iron's only opening
 * path), the mount-depart pop, and the family tables (behavior, light,
 * solidity, crafting).
 */
class TrapdoorBlocksTest {

    @TempDir
    Path dataDir;

    @Test
    void thePlacementMountsOnTheNamedWallWithTheCursorHalf() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            // A stone wall on the west side of the target cell.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, y, z + 4), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x - 1, y, z), "the wall placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the site");
            hold(player, "minecraft:trapdoor");
            // Clicking the wall's EAST face (face 5): the trapdoor mounts
            // facing east (the panel against the west wall), bottom half
            // (cursor 0).
            server.useItemOnBlock(player, new BlockPosition(x - 1, y, z), 5,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> TrapdoorBlocks.isTrapdoor(typeAt(server, x, y, z)), "the trapdoor mounted");
            BlockType placed = typeAt(server, x, y, z);
            assertEquals("trapdoor", TrapdoorBlocks.familyOf(placed));
            assertEquals(3, TrapdoorBlocks.facingOf(placed), "the east facing (meta 3)");
            assertFalse(TrapdoorBlocks.isOpen(placed), "a trapdoor places closed");
            assertFalse(TrapdoorBlocks.isTop(placed), "the cursor's lower half");
            assertEquals("minecraft:trapdoor_east", placed.identifier().toString());

            // The manual swing: the wood flips OPEN, the facing and half kept.
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 5,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> TrapdoorBlocks.isOpen(typeAt(server, x, y, z)), "the wood swung open");
            assertEquals(3, TrapdoorBlocks.facingOf(typeAt(server, x, y, z)),
                    "the swing kept the facing");
            assertEquals("minecraft:trapdoor_open_east",
                    typeAt(server, x, y, z).identifier().toString(),
                    "the open east form is the reference's meta-7 shape");
            // The close arm.
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 5,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> !TrapdoorBlocks.isOpen(typeAt(server, x, y, z)), "the wood swung closed");

            // The iron's material refusal: the use is consumed, no swing.
            hold(player, "minecraft:iron_trapdoor");
            server.useItemOnBlock(player, new BlockPosition(x - 1, y, z + 4), 5,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> TrapdoorBlocks.isTrapdoor(typeAt(server, x, y, z + 4)),
                    "the iron trapdoor mounted");
            server.useItemOnBlock(player, new BlockPosition(x, y, z + 4), 5,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> true, "the iron click settles");
            assertFalse(TrapdoorBlocks.isOpen(typeAt(server, x, y, z + 4)),
                    "the iron trapdoor refuses the swing");

            // A vertical face never mounts (the UP face refusal).
            hold(player, "minecraft:trapdoor");
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x + 4, y - 1, z), BuiltinBlocks.STONE));
            await(() -> solidAt(server, x + 4, y - 1, z), "the floor placed");
            server.useItemOnBlock(player, new BlockPosition(x + 4, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> true, "the vertical click settles");
            assertFalse(TrapdoorBlocks.isTrapdoor(typeAt(server, x + 4, y, z)),
                    "the vertical face refused the mount");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSignalArmSwingsBothFamiliesAndTheMountDepartPops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 14, y = 40;
            server.ticker().submit(() -> {
                // the west wall with a feedable cell beside the mount
                server.world().setBlock(new BlockPosition(x - 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, y, z + 4), BuiltinBlocks.STONE);
                // the torch's own floor under the feed cell
                server.world().setBlock(new BlockPosition(x, y - 2, z + 4), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x - 1, y, z + 4), "the walls placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the site");
            // The iron trapdoor mounts on the second wall (facing east).
            hold(player, "minecraft:iron_trapdoor");
            server.useItemOnBlock(player, new BlockPosition(x - 1, y, z + 4), 5,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> TrapdoorBlocks.isTrapdoor(typeAt(server, x, y, z + 4)),
                    "the iron trapdoor mounted");

            // The torch lands under the trapdoor's cell: the signal edge
            // opens the iron pair (the only opening path).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y - 1, z + 4), RedstoneBlocks.torchStandingLit()));
            await(() -> TrapdoorBlocks.isOpen(typeAt(server, x, y, z + 4)),
                    "the torch-fed iron trapdoor swung open");
            // The feed departs: the edge closes it.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y - 1, z + 4), BuiltinBlocks.AIR));
            await(() -> !TrapdoorBlocks.isOpen(typeAt(server, x, y, z + 4)),
                    "the iron trapdoor closed when the feed departed");

            // The mount departs: the trapdoor pops with its item
            // (neighborChanged lines 120-125).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z + 4), BuiltinBlocks.AIR));
            await(() -> !TrapdoorBlocks.isTrapdoor(typeAt(server, x, y, z + 4)),
                    "the departed mount popped the iron trapdoor");
            AtomicReference<Integer> drops = new AtomicReference<>();
            await(() -> {
                server.ticker().submit(() -> {
                    int found = 0;
                    for (var entity : server.itemEntities().all()) {
                        if (entity.stack() != null && entity.stack().type().identifier()
                                .toString().equals("minecraft:iron_trapdoor")) {
                            found++;
                        }
                    }
                    drops.set(found);
                });
                return drops.get() != null && drops.get() >= 1;
            }, "the popped trapdoor dropped its item");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFamilyTablesCoverEveryStateAndTheCraftsAnswer() {
        for (String family : TrapdoorBlocks.TRAPDOORS.keySet()) {
            boolean iron = TrapdoorBlocks.isIron(family);
            for (int facing = 0; facing < 4; facing++) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean top : new boolean[] {false, true}) {
                        EngineBlockType state = TrapdoorBlocks.state(family, facing, open, top);
                        int meta = facing | (open ? 4 : 0) | (top ? 8 : 0);
                        assertEquals(meta, state.legacyMetadata(),
                                family + " " + state.identifier().value());
                        BlockBehavior behavior = BlockBehaviorTable.of(state.identifier())
                                .orElseThrow();
                        assertEquals(TrapdoorBlocks.trapdoorItem(family),
                                behavior.drops().get(0).item());
                        assertEquals(iron ? 5.0 : 3.0, behavior.hardness());
                        assertEquals(iron, behavior.requiresTool(),
                "the iron pair is pickaxe-gated like the iron door");
                        assertEquals(15, BlockLightTable.lookup(state.identifier())
                                .orElseThrow().filter(), "the CUTOUT family passes light whole");
                        assertEquals(family, TrapdoorBlocks.familyOf(state));
                        assertEquals(facing, TrapdoorBlocks.facingOf(state));
                        assertEquals(open, TrapdoorBlocks.isOpen(state));
                        assertEquals(top, TrapdoorBlocks.isTop(state));
                        assertTrue(WorldSolidity.isSolid(state),
                                "both panels carry collision — the flat-solid stand-in");
                    }
                }
            }
        }
        // The wire ids: the reference's own rows.
        assertEquals(96, TrapdoorBlocks.TRAPDOORS.get("trapdoor"));
        assertEquals(167, TrapdoorBlocks.TRAPDOORS.get("iron_trapdoor"));
        // The crafts: six planks (any variant) -> 2, four iron -> 1.
        CraftingService service = CraftingService.builtin();
        ItemStack planks = ItemStack.of(BuiltinItems.lookup(
                Identifier.parse("minecraft:planks")).orElseThrow(), 1);
        var wood = service.resultOf3x3(new ItemStack[]{
                planks, planks, planks,
                planks, planks, planks,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY});
        assertTrue(wood.isPresent(), "the trapdoor craft");
        assertEquals("minecraft:trapdoor", wood.orElseThrow().type().identifier().toString());
        assertEquals(2, wood.orElseThrow().count());
        ItemStack iron = ItemStack.of(BuiltinItems.lookup(
                Identifier.parse("minecraft:iron_ingot")).orElseThrow(), 1);
        var ironCraft = service.resultOf3x3(new ItemStack[]{
                iron, iron, ItemStack.EMPTY,
                iron, iron, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY});
        assertTrue(ironCraft.isPresent(), "the iron trapdoor craft");
        assertEquals("minecraft:iron_trapdoor",
                ironCraft.orElseThrow().type().identifier().toString());
        assertEquals(1, ironCraft.orElseThrow().count());
    }

    // ------------------------------------------------------------- helpers

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "hatches" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession joined(EngineServer server) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        }, "Hatchwright", UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, "the player playing");
        return session;
    }

    private void hold(PlayerSession player, String id) {
        player.inventory().setSlot(0, ItemStack.of(
                BuiltinItems.lookup(Identifier.parse(id)).orElseThrow(), 1));
        player.inventory().selectHotbarSlot(0);
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        return waitFor(result);
    }

    private static boolean solidAt(EngineServer server, int x, int y, int z) {
        AtomicReference<Boolean> solid = new AtomicReference<>();
        server.ticker().submit(() -> solid.set(
                WorldSolidity.isSolid(server.world().getBlock(new BlockPosition(x, y, z)))));
        return waitFor(solid);
    }

    private static <T> T waitFor(AtomicReference<T> ref) {
        long deadline = System.currentTimeMillis() + 10_000;
        T value = ref.get();
        while (value == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            value = ref.get();
        }
        return value;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }
}
