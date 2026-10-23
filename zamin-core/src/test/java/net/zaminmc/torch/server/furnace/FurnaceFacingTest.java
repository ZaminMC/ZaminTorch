package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
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
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The furnace pair's horizontal FACING states (Slice L3 — FurnaceBlock
 * 1:1): the placement's look-opposite walk over the Direction-id band
 * 2-5, the custom-name carry (onPlaced lines 141-147 — the held stack's
 * display name riding the fresh tile), and the lit swap keeping the
 * facing whole (AbstractFurnaceBlock.updateState's own write).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FurnaceFacingTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "furnaces", "it", 20, 4, 20,
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

    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 120 wall settle)");
    }

    @Test
    void theFacingStatesResolveTheirBand() {
        assertEquals(2, FurnaceFacingBlocks.plainOf(2).legacyMetadata());
        assertEquals(5, FurnaceFacingBlocks.litOf(5).legacyMetadata());
        assertEquals("minecraft:furnace_north", FurnaceFacingBlocks.plainOf(2).identifier().toString());
        assertEquals("minecraft:furnace_lit_east",
                FurnaceFacingBlocks.litOf(5).identifier().toString());
        // The family consults.
        assertTrue(FurnaceFacingBlocks.isPlainFamily(FurnaceFacingBlocks.plainOf(3)));
        assertTrue(FurnaceFacingBlocks.isLitFamily(FurnaceFacingBlocks.litOf(4)));
        // The family's own trap: the lit stem shares the furnace_ prefix,
        // so the plain consult must exclude it.
        assertTrue(!FurnaceFacingBlocks.isPlainFamily(FurnaceFacingBlocks.litOf(4)));
        assertTrue(!FurnaceFacingBlocks.isPlainFamily(BuiltinBlocks.FURNACE_LIT));
        assertTrue(FurnaceFacingBlocks.isLitFamily(FurnaceFacingBlocks.litOf(4)));
        assertTrue(!FurnaceFacingBlocks.isLitFamily(FurnaceFacingBlocks.plainOf(4)));
        assertTrue(net.zaminmc.torch.server.block.WorldSolidity.isFurnaceBlock(
                FurnaceFacingBlocks.plainOf(2).identifier()));
        assertTrue(net.zaminmc.torch.server.block.WorldSolidity.isFurnaceBlock(
                FurnaceFacingBlocks.litOf(3).identifier()));
        // The lit swap's consult: the partner keeps the facing whole.
        assertEquals("minecraft:furnace_lit_west",
                FurnaceFacingBlocks.partnerOf(FurnaceFacingBlocks.plainOf(4), true)
                        .identifier().toString());
        assertEquals("minecraft:furnace_south",
                FurnaceFacingBlocks.partnerOf(FurnaceFacingBlocks.litOf(3), false)
                        .identifier().toString());
        // The same-form guard: a state already in the target form writes
        // nothing (the old bare-form guard's own semantics).
        assertEquals(null, FurnaceFacingBlocks.partnerOf(FurnaceFacingBlocks.litOf(3), true));
        assertEquals(null, FurnaceFacingBlocks.partnerOf(FurnaceFacingBlocks.plainOf(5), false));
        // The bare forms' own swap.
        assertEquals(BuiltinBlocks.FURNACE_LIT,
                FurnaceFacingBlocks.partnerOf(BuiltinBlocks.FURNACE, true));
        assertEquals(null, FurnaceFacingBlocks.partnerOf(BuiltinBlocks.FURNACE, false));
        // The look consult.
        assertEquals(2, FurnaceFacingBlocks.lookOppositeOf(0));
        assertEquals(4, FurnaceFacingBlocks.lookOppositeOf(3));
    }

    @Test
    void thePlacementWritesTheLookOppositeAndTheNameCarries() throws Exception {
        server = boot();
        PlayerSession builder = join("FurnaceBuilder");
        var world = server.world();
        ItemType furnaceItem = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:furnace")).orElseThrow();

        BlockPosition floor = new BlockPosition(
                (int) Math.floor(builder.position().x()) + 2,
                (int) Math.floor(builder.position().y()) - 1,
                (int) Math.floor(builder.position().z()));
        BlockPosition atop = floor.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "the floor seeded");
        // A NAMED furnace stack (the anvil's own hover name): the placement
        // carries it into the fresh tile.
        builder.inventory().setSlot(0,
                new ItemStack(furnaceItem, 1, 0).withName("Forge of Ages"));
        builder.inventory().selectHotbarSlot(0);
        useOn(builder, floor, 1);
        assertEquals("minecraft:furnace_north", world.getBlock(atop).identifier().toString(),
                "the default south look's opposite writes the north front");
        net.zaminmc.torch.server.furnace.FurnaceBlockEntity furnace =
                server.furnaceManager().getOrCreate(atop);
        assertEquals("Forge of Ages", furnace.customName(),
                "onPlaced's hasCustomHoverName carry rides the fresh tile");

        // The lit swap keeps the facing whole: seed the smelt (input +
        // fuel) and let the tick walk ignite the furnace itself — the
        // burn TRANSITION is what drives the block swap (the manager's
        // wasBurning consult, AbstractFurnaceBlock.updateState).
        ItemType coal = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:coal")).orElseThrow();
        ItemType ironOre = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:iron_ore")).orElseThrow();
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.furnace.FurnaceBlockEntity tile =
                    server.furnaceManager().getOrCreate(atop);
            tile.setSlot(net.zaminmc.torch.server.furnace.FurnaceBlockEntity.SLOT_INPUT,
                    new ItemStack(ironOre, 1, 0));
            tile.setSlot(net.zaminmc.torch.server.furnace.FurnaceBlockEntity.SLOT_FUEL,
                    new ItemStack(coal, 1, 0));
        });
        await(() -> world.getBlock(atop).identifier().value().startsWith("furnace_lit"),
                "the burn lights the block");
        assertEquals("minecraft:furnace_lit_north", world.getBlock(atop).identifier().toString(),
                "the lit swap keeps the FACING whole");
        server.shutdown(null);
    }
}
